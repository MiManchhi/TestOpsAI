import asyncio
import json
import multiprocessing
from pathlib import Path
from tempfile import TemporaryDirectory

from pydantic import TypeAdapter

from testops_ai.features.robot_parser.errors import ParserError

from .documents import parse_document
from .models import Chunk


def _extract(source: str, logical_path: str, content_hash: str, destination: str) -> None:
    try:
        chunks = parse_document(Path(source).read_bytes(), logical_path, content_hash)
        payload: dict[str, object] = {
            "chunks": [chunk.model_dump(by_alias=True) for chunk in chunks]
        }
    except ParserError as exc:
        payload = {"error": {"status": exc.status, "code": exc.code, "message": exc.message}}
    except Exception:
        payload = {
            "error": {
                "status": 422,
                "code": "DOCUMENT_PARSE_FAILED",
                "message": "Document cannot be parsed",
            }
        }
    Path(destination).write_text(json.dumps(payload, ensure_ascii=False), encoding="utf-8")


async def extract_document(
    source: Path, logical_path: str, content_hash: str, timeout: float
) -> list[Chunk]:
    with TemporaryDirectory(prefix="testops-document-") as directory:
        output = Path(directory) / "chunks.json"
        process = multiprocessing.get_context("spawn").Process(
            target=_extract,
            args=(str(source), logical_path, content_hash, str(output)),
            daemon=True,
        )
        started = False
        try:
            process.start()
            started = True
            async with asyncio.timeout(timeout):
                while process.is_alive():
                    await asyncio.sleep(0.02)
            if process.exitcode != 0 or not output.exists():
                raise ParserError(422, "DOCUMENT_PARSE_FAILED", "Document worker failed")
            if output.stat().st_size > 32 * 1024 * 1024:
                raise ParserError(413, "CONTENT_TOO_LARGE", "Parsed document exceeds output limit")
            envelope = json.loads(output.read_text(encoding="utf-8"))
            if "error" in envelope:
                error = envelope["error"]
                raise ParserError(error["status"], error["code"], error["message"])
            return TypeAdapter(list[Chunk]).validate_python(envelope["chunks"])
        except TimeoutError as exc:
            raise ParserError(
                422, "DOCUMENT_TIMEOUT", "Document parsing exceeded time limit"
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
