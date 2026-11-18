import asyncio
from contextlib import suppress
from dataclasses import dataclass
from typing import cast
from uuid import uuid4

from fastapi import APIRouter, Request
from pydantic import ValidationError

from testops_ai.core.service_tokens import ServiceTokens
from testops_ai.core.settings import Settings

from .catalog import SourceFile
from .errors import ParserError
from .models import ResultParseRequest, RobotParseRequest
from .objects import ObjectReader
from .worker import Kind, ParseResult, ParseWorker

router = APIRouter(prefix="/internal/v1/parsers")


@dataclass
class ParserRuntime:
    settings: Settings
    tokens: ServiceTokens
    reader: ObjectReader
    worker: ParseWorker
    slots: asyncio.Semaphore


async def disconnected(request: Request) -> None:
    while not await request.is_disconnected():
        await asyncio.sleep(0.05)
    raise ParserError(503, "REQUEST_CANCELLED", "Request was cancelled")


async def parse_request(request: Request, kind: Kind) -> ParseResult:
    runtime = cast(ParserRuntime, request.app.state.parser)
    claims = runtime.tokens.verify_service(request.headers.get("authorization", ""))
    if request.headers.get("content-type", "").split(";", 1)[0].strip() != "application/json":
        raise ParserError(415, "UNSUPPORTED_FORMAT", "Expected application/json")
    try:
        async with asyncio.timeout(runtime.settings.request_timeout_seconds):
            content = bytearray()
            async for chunk in request.stream():
                if len(content) + len(chunk) > 512 * 1024:
                    raise ParserError(413, "CONTENT_TOO_LARGE", "Request body exceeds limit")
                content.extend(chunk)
            try:
                model = RobotParseRequest if kind == "robot" else ResultParseRequest
                body = model.model_validate_json(bytes(content), by_alias=True, by_name=False)
            except ValidationError as exc:
                raise ParserError(400, "VALIDATION_ERROR", "Invalid parser request") from exc
            if claims.operation_id != body.operation_id:
                raise ParserError(403, "FORBIDDEN", "Service token does not match operation")
            delegation = request.headers.get("x-delegation-token", "")
            scope = runtime.tokens.verify_delegation(delegation, body.operation_id)
            refs = body.files if isinstance(body, RobotParseRequest) else [body.object]
            if not {ref.object_id for ref in refs}.issubset(scope.object_ids):
                raise ParserError(403, "FORBIDDEN", "Objects are outside the delegated scope")
            maximum = 2 * 1024 * 1024 if kind == "robot" else 50 * 1024 * 1024
            if any(ref.size_bytes > maximum for ref in refs) or (
                kind == "robot" and sum(ref.size_bytes for ref in refs) > 20 * 1024 * 1024
            ):
                raise ParserError(413, "CONTENT_TOO_LARGE", "Input exceeds parser limits")
            if (
                any(not ref.logical_path.endswith((".robot", ".resource")) for ref in refs)
                if kind == "robot"
                else refs[0].logical_path.rsplit("/", 1)[-1] != "output.xml"
            ):
                raise ParserError(415, "UNSUPPORTED_FORMAT", "Unsupported parser file format")

            async def download_and_parse() -> ParseResult:
                files: list[SourceFile] = []
                request_id = cast(str, request.scope.get("parser_request_id", str(uuid4())))
                for ref in refs:
                    payload = await runtime.reader.read(
                        ref, body.operation_id, delegation, request_id, maximum
                    )
                    files.append(SourceFile(ref, payload))
                return await runtime.worker.parse(kind, files)

            async def work() -> ParseResult:
                # Bound downloads as well as CPU workers; queueing shares the request timeout.
                async with runtime.slots:
                    return await download_and_parse()

            task = asyncio.create_task(work())
            monitor = asyncio.create_task(disconnected(request))
            try:
                done, _ = await asyncio.wait({task, monitor}, return_when=asyncio.FIRST_COMPLETED)
                if monitor in done:
                    await monitor
                return await task
            finally:
                for pending in (task, monitor):
                    pending.cancel()
                for pending in (task, monitor):
                    with suppress(asyncio.CancelledError, ParserError):
                        await pending
    except TimeoutError as exc:
        raise ParserError(503, "REQUEST_TIMEOUT", "Parser request exceeded its time limit") from exc


@router.post("/robot", operation_id="parseRobotCatalog")
async def robot(request: Request) -> dict[str, object]:
    return (await parse_request(request, "robot")).model_dump(by_alias=True)


@router.post("/robot-output", operation_id="parseRobotOutput")
async def output(request: Request) -> dict[str, object]:
    return (await parse_request(request, "output")).model_dump(by_alias=True)
