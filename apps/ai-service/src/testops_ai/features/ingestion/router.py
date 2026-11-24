import asyncio
from typing import cast

from fastapi import APIRouter, Request
from pydantic import ValidationError

from testops_ai.core.service_tokens import ServiceTokens
from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.models import check_uuid

from .models import WORKSPACE_PARSER_VERSION, IndexBlockPage, IndexDelete, IndexJob, IndexRequest
from .service import IngestionService

router = APIRouter(prefix="/internal/v1")
workspace_router = APIRouter(prefix="/internal/v2")


def runtime(request: Request) -> tuple[ServiceTokens, IngestionService]:
    tokens = cast(ServiceTokens, request.app.state.parser.tokens)
    service = cast(IngestionService | None, request.app.state.ingestion)
    if service is None:
        raise ParserError(503, "INDEX_NOT_CONFIGURED", "Index state directory is not configured")
    return tokens, service


async def submit(request: Request, deletion: bool = False) -> IndexJob:
    tokens, service = runtime(request)
    identity = tokens.verify_service(request.headers.get("authorization", ""))
    if request.headers.get("content-type", "").split(";", 1)[0].strip() != "application/json":
        raise ParserError(415, "UNSUPPORTED_FORMAT", "Expected application/json")
    try:
        async with asyncio.timeout(120):
            content = bytearray()
            async for chunk in request.stream():
                if len(content) + len(chunk) > 64 * 1024:
                    raise ParserError(413, "CONTENT_TOO_LARGE", "Index request is too large")
                content.extend(chunk)
            try:
                model = IndexDelete if deletion else IndexRequest
                body = model.model_validate_json(bytes(content), by_alias=True, by_name=False)
                key = check_uuid(request.headers.get("idempotency-key", ""))
                if len(key) != 36:
                    raise ValueError("Expected UUID idempotency key")
            except (ValidationError, ValueError) as exc:
                raise ParserError(
                    400, "VALIDATION_ERROR", "Invalid index request or idempotency key"
                ) from exc
            if identity.operation_id != body.operation_id:
                raise ParserError(403, "FORBIDDEN", "Service token does not match operation")
            delegation = request.headers.get("x-delegation-token", "")
            claims = tokens.verify_delegation(delegation, body.operation_id)
            record = await service.submit(
                body, claims, key, delegation, cast(str, request.scope["parser_request_id"])
            )
            return record.public
    except TimeoutError as exc:
        raise ParserError(
            503, "REQUEST_TIMEOUT", "Index admission exceeded its time limit"
        ) from exc


@router.post("/index-jobs", operation_id="indexDocument", status_code=202)
async def index_document(request: Request) -> dict[str, object]:
    return (await submit(request)).model_dump(by_alias=True)


@router.post("/index-deletions", operation_id="deleteIndex", status_code=202)
async def delete_index(request: Request) -> dict[str, object]:
    return (await submit(request, deletion=True)).model_dump(by_alias=True)


@router.get("/index-jobs/{job_id}", operation_id="getIndexJob")
async def get_job(request: Request, job_id: str) -> dict[str, object]:
    tokens, service = runtime(request)
    identity = tokens.verify_service(request.headers.get("authorization", ""))
    try:
        check_uuid(job_id)
    except ValueError as exc:
        raise ParserError(400, "VALIDATION_ERROR", "Invalid index job identifier") from exc
    record = service.store.get(job_id)
    if identity.operation_id != record.public.operation_id:
        raise ParserError(404, "JOB_NOT_FOUND", "Index task is not available")
    return record.public.model_dump(by_alias=True)


@workspace_router.get("/index-jobs/{job_id}/blocks", operation_id="exportIndexBlocks")
async def export_index_blocks(
    request: Request, job_id: str, cursor: str | None = None
) -> dict[str, object]:
    tokens, service = runtime(request)
    authorization = request.headers.get("authorization", "")
    tokens.verify_service(authorization)
    try:
        if len(job_id) != 36:
            raise ValueError("Expected UUID")
        check_uuid(job_id)
    except ValueError as exc:
        raise ParserError(400, "VALIDATION_ERROR", "Invalid index job identifier") from exc
    record = service.store.get(job_id)
    tokens.verify_workspace_delegation(
        authorization,
        request.headers.get("x-delegation-token", ""),
        job_id,
        "exportIndexBlocks",
        index_source=(record.project_id, record.document_id, record.content_hash),
    )
    if record.kind != "index" or record.public.status != "SUCCEEDED" or service.deleted(record):
        raise ParserError(404, "JOB_NOT_FOUND", "Export is not available")
    body = IndexRequest.model_validate_json(record.request_json)
    if body.parser_version != WORKSPACE_PARSER_VERSION:
        raise ParserError(404, "JOB_NOT_FOUND", "Export is not available")
    offset = 0
    if cursor is not None:
        prefix, separator, ordinal = cursor.partition(".")
        if (
            prefix != job_id
            or not separator
            or len(ordinal) > 5
            or not ordinal.isascii()
            or not ordinal.isdecimal()
        ):
            raise ParserError(400, "VALIDATION_ERROR", "Invalid index block cursor")
        offset = int(ordinal)
    blocks = service.store.source_blocks(service.generation(record, body), offset, 101)
    if not blocks:
        raise ParserError(404, "JOB_NOT_FOUND", "Index source blocks are not available")
    return IndexBlockPage(
        job_id=job_id,
        version_id=record.document_id,
        content_hash=record.content_hash,
        parser_version=body.parser_version,
        blocks=blocks[:100],
        next_cursor=f"{job_id}.{offset + 100}" if len(blocks) > 100 else None,
    ).model_dump(by_alias=True)
