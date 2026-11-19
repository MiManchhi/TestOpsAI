import asyncio
import logging
from collections.abc import AsyncIterator, Awaitable, Callable
from contextlib import asynccontextmanager
from uuid import UUID, uuid4

import httpx
from fastapi import FastAPI, Request
from fastapi.responses import JSONResponse
from qdrant_client import AsyncQdrantClient
from starlette.exceptions import HTTPException
from starlette.responses import Response

from testops_ai.core.service_tokens import ServiceTokens
from testops_ai.core.settings import Settings
from testops_ai.features.health.router import router as health_router
from testops_ai.features.ingestion.embedding import HttpEmbedding
from testops_ai.features.ingestion.router import router as ingestion_router
from testops_ai.features.ingestion.service import IngestionService
from testops_ai.features.ingestion.store import JobStore
from testops_ai.features.ingestion.vectors import VectorIndex
from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.objects import ObjectReader
from testops_ai.features.robot_parser.router import ParserRuntime
from testops_ai.features.robot_parser.router import router as parser_router
from testops_ai.features.robot_parser.worker import ParseWorker


def create_app(
    settings: Settings | None = None,
    object_transport: httpx.AsyncBaseTransport | None = None,
    embedding_transport: httpx.AsyncBaseTransport | None = None,
    vector_client: AsyncQdrantClient | None = None,
    *,
    vector_local_mode: bool = False,
) -> FastAPI:
    config = settings or Settings()

    @asynccontextmanager
    async def lifespan(app: FastAPI) -> AsyncIterator[None]:
        async with httpx.AsyncClient(
            transport=object_transport, trust_env=False, follow_redirects=False
        ) as client:
            tokens = ServiceTokens(config)
            app.state.parser = ParserRuntime(
                config,
                tokens,
                ObjectReader(config, tokens, client),
                ParseWorker(config.parser_concurrency, config.parser_timeout_seconds),
                asyncio.Semaphore(config.parser_concurrency),
            )
            app.state.ingestion = None
            vectors = vector_client
            store: JobStore | None = None
            service: IngestionService | None = None
            async with httpx.AsyncClient(
                transport=embedding_transport, trust_env=False, follow_redirects=False
            ) as embedding_client:
                try:
                    if config.ingestion_state_dir is not None:
                        store = JobStore(
                            config.ingestion_state_dir,
                            config.ingestion_queue_limit,
                            config.ingestion_input_quota,
                        )
                        if vectors is None and config.qdrant_url:
                            vectors = AsyncQdrantClient(
                                url=config.qdrant_url,
                                api_key=config.qdrant_api_key.get_secret_value() or None,
                                timeout=15,
                                check_compatibility=False,
                            )
                        service = IngestionService(
                            config,
                            store,
                            tokens,
                            app.state.parser.reader,
                            HttpEmbedding(embedding_client),
                            VectorIndex(vectors, local_mode=vector_local_mode) if vectors else None,
                        )
                        app.state.ingestion = service
                        service.start()
                    yield
                finally:
                    if service:
                        await service.close()
                    elif store:
                        store.close()
                    if vectors:
                        await vectors.close()

    app = FastAPI(
        title=config.app_name, docs_url=None, redoc_url=None, openapi_url=None, lifespan=lifespan
    )

    @app.middleware("http")
    async def request_identity(
        request: Request, call_next: Callable[[Request], Awaitable[Response]]
    ) -> Response:
        try:
            request_id = str(UUID(request.headers.get("x-request-id", "")))
        except ValueError:
            request_id = str(uuid4())
        request.scope["parser_request_id"] = request_id
        try:
            response = await call_next(request)
        except Exception as error:
            logging.getLogger(__name__).error(
                "Unhandled request error requestId=%s type=%s", request_id, type(error).__name__
            )
            response = JSONResponse(
                status_code=500,
                content={
                    "code": "INTERNAL_ERROR",
                    "message": "Internal service error",
                    "requestId": request_id,
                },
            )
        response.headers["X-Request-Id"] = request_id
        return response

    @app.exception_handler(ParserError)
    async def parser_error(request: Request, error: ParserError) -> JSONResponse:
        return JSONResponse(
            status_code=error.status,
            content={
                "code": error.code,
                "message": error.message,
                "requestId": request.scope["parser_request_id"],
            },
        )

    @app.exception_handler(HTTPException)
    async def http_error(request: Request, error: HTTPException) -> JSONResponse:
        return JSONResponse(
            status_code=error.status_code,
            content={
                "code": "NOT_FOUND" if error.status_code == 404 else "HTTP_ERROR",
                "message": "Requested endpoint is unavailable",
                "requestId": request.scope["parser_request_id"],
            },
            headers=error.headers,
        )

    app.include_router(health_router)
    app.include_router(parser_router)
    app.include_router(ingestion_router)
    return app
