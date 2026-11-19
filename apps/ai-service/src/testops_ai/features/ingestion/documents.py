"""Source-located plain text extraction, with no rendering, OCR or remote resource loading."""

import hashlib
import logging
import re
from io import BytesIO
from uuid import NAMESPACE_URL, uuid5

from pypdf import Configuration, PdfReader, apply_configuration
from pypdf.errors import LimitReachedError, PdfReadError
from pypdf.generic import ArrayObject, StreamObject

from testops_ai.features.robot_parser.errors import ParserError

from .models import PARSER_VERSION, Chunk


def document_format(path: str, size: int) -> str:
    suffix = path.rsplit(".", 1)[-1].lower()
    if suffix not in {"md", "markdown", "pdf"}:
        raise ParserError(415, "UNSUPPORTED_FORMAT", "Expected Markdown or PDF")
    maximum = (20 if suffix == "pdf" else 2) * 1024 * 1024
    if size > maximum:
        raise ParserError(413, "CONTENT_TOO_LARGE", "Document exceeds its format limit")
    return "pdf" if suffix == "pdf" else "markdown"


def chunk_text(text: str, content_hash: str, page: int | None = None) -> list[Chunk]:
    if not text.strip():
        return []
    headings: list[tuple[int, str]] = []
    offset = 0
    fence: str | None = None
    for line in text.splitlines(keepends=True):
        marker = re.match(r"^\s*(`{3,}|~{3,})", line)
        if marker:
            token = marker[1][0]
            fence = None if fence == token else token if fence is None else fence
        if fence is None:
            heading = re.match(r"^ {0,3}#{1,6}\s+(.+?)\s*#*\s*$", line)
            if heading:
                if len(heading[1]) > 256:
                    raise ParserError(
                        422, "HEADING_TOO_LONG", "Document heading exceeds 256 characters"
                    )
                headings.append((offset, heading[1]))
        offset += len(line)
    chunks: list[Chunk] = []
    start = 0
    while start < len(text):
        end = min(start + 1000, len(text))
        if end < len(text):
            paragraph = text.rfind("\n\n", start + 500, end)
            if paragraph >= 0:
                end = paragraph + 2
        excerpt = text[start:end]
        heading_text = next((title for pos, title in reversed(headings) if pos <= start), "")
        if excerpt.strip():
            text_hash = hashlib.sha256(excerpt.encode()).hexdigest()
            identity = f"{PARSER_VERSION}/{content_hash}/{page}/{start}/{text_hash}"
            chunks.append(
                Chunk(
                    id=str(uuid5(NAMESPACE_URL, identity)),
                    text=excerpt,
                    heading=heading_text,
                    page=page,
                    start_line=None if page else text.count("\n", 0, start) + 1,
                    end_line=None if page else text.count("\n", 0, max(start, end - 1)) + 1,
                    offset=start,
                )
            )
        if end == len(text):
            break
        start = max(start + 1, end - 100)
    return chunks


def parse_document(content: bytes, path: str, content_hash: str) -> list[Chunk]:
    kind = document_format(path, len(content))
    if hashlib.sha256(content).hexdigest() != content_hash:
        raise ParserError(422, "CONTENT_HASH_MISMATCH", "Document digest does not match")
    try:
        if kind == "markdown":
            text = content.decode("utf-8-sig")
            if "\x00" in text:
                raise ValueError("Binary content")
            chunks = chunk_text(text, content_hash)
        else:
            if not content.startswith(b"%PDF-"):
                raise ValueError("Invalid PDF signature")
            logging.getLogger("pypdf").disabled = True
            chunks = pdf_chunks(content, content_hash)
        if not chunks:
            raise ParserError(422, "NO_TEXT", "Document contains no indexable text")
        if len(chunks) > 10000:
            raise ParserError(413, "CONTENT_TOO_LARGE", "Document exceeds 10000 chunks")
        return chunks
    except LimitReachedError as exc:
        raise ParserError(413, "CONTENT_TOO_LARGE", "PDF resource budget exceeded") from exc
    except (UnicodeError, ValueError, PdfReadError, RecursionError) as exc:
        raise ParserError(422, "DOCUMENT_PARSE_FAILED", "Document cannot be parsed") from exc


def pdf_chunks(content: bytes, content_hash: str) -> list[Chunk]:
    stream_limit = 8 * 1024 * 1024
    configuration = Configuration(
        maximum_declared_stream_length=20 * 1024 * 1024,
        array_based_stream_maximum_output_length=stream_limit,
        array_maximum_element_count=10000,
        dictionary_maximum_element_count=10000,
        zlib_maximum_output_length=stream_limit,
        lzw_maximum_output_length=stream_limit,
        run_length_maximum_output_length=stream_limit,
        jbig2_maximum_output_length=stream_limit,
        brotli_maximum_output_length=stream_limit,
        image_maximum_buffer_size=stream_limit,
        stream_decoding_work_maximum_length=16 * 1024 * 1024,
        stream_parsing_work_maximum_count=500000,
        page_tree_maximum_entries=10000,
        page_tree_maximum_depth=64,
        jbig2dec_binary=None,
    )
    with apply_configuration(configuration):
        reader = PdfReader(BytesIO(content), strict=True)
        if reader.is_encrypted:
            raise ParserError(422, "ENCRYPTED_PDF", "Encrypted PDF is not supported")
        if len(reader.pages) > 500:
            raise ParserError(413, "CONTENT_TOO_LARGE", "PDF exceeds 500 pages")
        chunks: list[Chunk] = []
        total_text = 0
        total_stream = 0
        for page_number, page in enumerate(reader.pages, 1):
            raw = page.get("/Contents")
            if raw is not None:
                resolved = raw.get_object()
                parts = resolved if isinstance(resolved, ArrayObject) else [resolved]
                # Check before ContentStream joins an array of repeated/decompressed streams.
                for part in parts:
                    stream = part.get_object()
                    if not isinstance(stream, StreamObject):
                        raise ValueError("Invalid content stream")
                    total_stream += len(stream.get_data())
                    if total_stream > 16 * 1024 * 1024:
                        raise ParserError(
                            413, "CONTENT_TOO_LARGE", "PDF content stream budget exceeded"
                        )
            text = page.extract_text() or ""
            total_text += len(text.encode("utf-8"))
            if total_text > 4 * 1024 * 1024:
                raise ParserError(413, "CONTENT_TOO_LARGE", "Extracted text exceeds 4 MiB")
            if not text.strip():
                raise ParserError(422, "OCR_REQUIRED", "A PDF page has no extractable text")
            chunks.extend(chunk_text(text, content_hash, page_number))
            if len(chunks) > 10000:
                raise ParserError(413, "CONTENT_TOO_LARGE", "Document exceeds 10000 chunks")
        return chunks
