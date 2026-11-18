"""One spawned process per parse, reaped even on timeout or client disconnect."""

import asyncio
import json
import multiprocessing
from pathlib import Path
from tempfile import TemporaryDirectory
from typing import Literal

from pydantic import ValidationError

from .catalog import SourceFile, parse_catalog
from .errors import ParserError
from .models import ResultParseResult, RobotParseResult
from .output import parse_output

Kind = Literal["robot", "output"]
ParseResult = RobotParseResult | ResultParseResult


def _child(kind: Kind, files: list[SourceFile], destination: str) -> None:
    try:
        result: ParseResult = (
            parse_catalog(files)
            if kind == "robot"
            else parse_output(files[0].content, files[0].reference.logical_path)
        )
        payload = {"result": result.model_dump(by_alias=True)}
    except ParserError as exc:
        payload = {"error": {"status": exc.status, "code": exc.code, "message": exc.message}}
    except Exception:
        payload = {
            "error": {
                "status": 422,
                "code": "UNPROCESSABLE_CONTENT",
                "message": "Content cannot be parsed",
            }
        }
    Path(destination).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


class ParseWorker:
    def __init__(self, concurrency: int, timeout_seconds: float) -> None:
        self.slots = asyncio.Semaphore(concurrency)
        self.timeout_seconds = timeout_seconds

    async def parse(self, kind: Kind, files: list[SourceFile]) -> ParseResult:
        async with self.slots:
            with TemporaryDirectory(prefix="testops-parser-") as directory:
                result_file = Path(directory) / "result.json"
                process = multiprocessing.get_context("spawn").Process(
                    target=_child,
                    args=(kind, files, str(result_file)),
                    daemon=True,
                )
                started = False
                try:
                    process.start()
                    started = True
                    async with asyncio.timeout(self.timeout_seconds):
                        while process.is_alive():
                            await asyncio.sleep(0.02)
                    if process.exitcode != 0 or not result_file.exists():
                        raise ParserError(422, "PARSER_FAILED", "Parser process did not complete")
                    if result_file.stat().st_size > 64 * 1024 * 1024:
                        raise ParserError(413, "CONTENT_TOO_LARGE", "Parsed result exceeds limit")
                    envelope = json.loads(result_file.read_text(encoding="utf-8"))
                    if "error" in envelope:
                        error = envelope["error"]
                        raise ParserError(error["status"], error["code"], error["message"])
                    model = RobotParseResult if kind == "robot" else ResultParseResult
                    return model.model_validate(envelope["result"], by_alias=True, by_name=False)
                except TimeoutError as exc:
                    raise ParserError(
                        422, "PARSER_TIMEOUT", "Parsing exceeded the time limit"
                    ) from exc
                except (OSError, ValidationError, json.JSONDecodeError) as exc:
                    raise ParserError(
                        503, "PARSER_UNAVAILABLE", "Parser process unavailable"
                    ) from exc
                finally:
                    if started:
                        if process.is_alive():
                            process.terminate()
                        process.join(timeout=1)
                        if process.is_alive():
                            process.kill()
                            process.join()
                    process.close()
