"""Source-located plain text extraction, with no rendering, OCR or remote resource loading."""

import hashlib
import logging
import re
from collections.abc import Iterable
from io import BytesIO
from uuid import NAMESPACE_URL, uuid5
from xml.etree.ElementTree import ParseError
from zipfile import BadZipFile, ZipFile

from defusedxml.common import DefusedXmlException
from defusedxml.ElementTree import fromstring
from pypdf import Configuration, PdfReader, apply_configuration
from pypdf.errors import LimitReachedError, PdfReadError
from pypdf.generic import ArrayObject, StreamObject

from testops_ai.features.robot_parser.errors import ParserError

from .models import PARSER_VERSION, WORKSPACE_PARSER_VERSION, Chunk, ExtractedSource, SourceBlock


def document_format(path: str, size: int, parser_version: str = PARSER_VERSION) -> str:
    suffix = path.rsplit(".", 1)[-1].lower()
    allowed = {"md", "markdown", "pdf"}
    if parser_version == WORKSPACE_PARSER_VERSION:
        allowed.add("docx")
    if suffix not in allowed:
        raise ParserError(415, "UNSUPPORTED_FORMAT", "Unsupported knowledge document format")
    maximum = (20 if suffix in {"pdf", "docx"} else 2) * 1024 * 1024
    if size > maximum:
        raise ParserError(413, "CONTENT_TOO_LARGE", "Document exceeds its format limit")
    return "markdown" if suffix in {"md", "markdown"} else suffix


def chunk_text(
    text: str,
    content_hash: str,
    page: int | None = None,
    *,
    paragraph: int | None = None,
    parser_version: str = PARSER_VERSION,
) -> list[Chunk]:
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
            boundary = text.rfind("\n\n", start + 500, end)
            if boundary >= 0:
                end = boundary + 2
        excerpt = text[start:end]
        heading_text = next((title for pos, title in reversed(headings) if pos <= start), "")
        if excerpt.strip():
            text_hash = hashlib.sha256(excerpt.encode()).hexdigest()
            identity = f"{parser_version}/{content_hash}/{page}/{start}/{text_hash}"
            if paragraph is not None:
                identity += f"/paragraph/{paragraph}"
            chunks.append(
                Chunk(
                    id=str(uuid5(NAMESPACE_URL, identity)),
                    text=excerpt,
                    heading=heading_text,
                    page=page,
                    start_line=None if page else text.count("\n", 0, start) + 1,
                    end_line=None if page else text.count("\n", 0, max(start, end - 1)) + 1,
                    offset=start,
                    paragraph=paragraph,
                )
            )
        if end == len(text):
            break
        start = max(start + 1, end - 100)
    return chunks


def parse_document(content: bytes, path: str, content_hash: str) -> list[Chunk]:
    return parse_source(content, path, content_hash, PARSER_VERSION).chunks


def source_blocks(label: str, number: int, text: str) -> list[SourceBlock]:
    if not text.strip():
        return []
    base = f"{label} {number}"
    return [
        SourceBlock(
            locator=base if offset == 0 else f"{base} part {offset // 4000 + 1}",
            text=text[offset : offset + 4000],
        )
        for offset in range(0, len(text), 4000)
        if text[offset : offset + 4000].strip()
    ]


def parse_source(
    content: bytes, path: str, content_hash: str, parser_version: str
) -> ExtractedSource:
    kind = document_format(path, len(content), parser_version)
    if hashlib.sha256(content).hexdigest() != content_hash:
        raise ParserError(422, "CONTENT_HASH_MISMATCH", "Document digest does not match")
    workspace = parser_version == WORKSPACE_PARSER_VERSION
    chunks: list[Chunk] = []
    blocks: list[SourceBlock] = []
    try:
        if kind == "markdown":
            text = content.decode("utf-8-sig")
            if "\x00" in text:
                raise ValueError("Binary content")
            if workspace:
                text = text.replace("\r\n", "\n").replace("\r", "\n")
                for number, line in enumerate(text.split("\n"), 1):
                    blocks.extend(source_blocks("line", number, line))
                    if len(blocks) > 50000:
                        raise ParserError(413, "CONTENT_TOO_LARGE", "Too many original locations")
            chunks = chunk_text(text, content_hash, parser_version=parser_version)
        elif kind == "pdf":
            if not content.startswith(b"%PDF-"):
                raise ValueError("Invalid PDF signature")
            logging.getLogger("pypdf").disabled = True
            for number, text in pdf_pages(content, require_text=not workspace):
                chunks.extend(chunk_text(text, content_hash, number, parser_version=parser_version))
                if workspace:
                    blocks.extend(source_blocks("page", number, text))
                if len(chunks) > 10000:
                    raise ParserError(413, "CONTENT_TOO_LARGE", "Document exceeds 10000 chunks")
        else:
            for number, text in docx_paragraphs(content):
                chunks.extend(
                    chunk_text(text, content_hash, paragraph=number, parser_version=parser_version)
                )
                blocks.extend(source_blocks("paragraph", number, text))
                if len(chunks) > 10000 or len(blocks) > 50000:
                    raise ParserError(413, "CONTENT_TOO_LARGE", "Too many document locations")
        if not chunks:
            raise ParserError(422, "NO_TEXT", "Document contains no indexable text")
        if len(chunks) > 10000:
            raise ParserError(413, "CONTENT_TOO_LARGE", "Document exceeds 10000 chunks")
        return ExtractedSource(chunks=chunks, blocks=blocks)
    except LimitReachedError as exc:
        raise ParserError(413, "CONTENT_TOO_LARGE", "PDF resource budget exceeded") from exc
    except (
        UnicodeError,
        ValueError,
        PdfReadError,
        RecursionError,
        BadZipFile,
        DefusedXmlException,
        ParseError,
        KeyError,
        RuntimeError,
    ) as exc:
        raise ParserError(422, "DOCUMENT_PARSE_FAILED", "Document cannot be parsed") from exc


def docx_paragraphs(content: bytes) -> Iterable[tuple[int, str]]:
    n = "{http://schemas.openxmlformats.org/wordprocessingml/2006/main}"
    xml_budget = 16 * 1024 * 1024
    with ZipFile(BytesIO(content)) as archive:
        entries = archive.infolist()
        if len(entries) > 2048 or sum(e.file_size for e in entries) > 64 * 1024 * 1024:
            raise ParserError(413, "CONTENT_TOO_LARGE", "DOCX expansion budget exceeded")
        names = [e.filename for e in entries]
        if names.count("word/document.xml") != 1 or names.count("[Content_Types].xml") != 1:
            raise ValueError("Not a Word document")
        if archive.getinfo("word/document.xml").file_size > xml_budget:
            raise ParserError(413, "CONTENT_TOO_LARGE", "DOCX document part exceeds its limit")
        with archive.open("[Content_Types].xml") as part:
            types = fromstring(
                part.read(65537), forbid_dtd=True, forbid_entities=True, forbid_external=True
            )
        if not any(
            node.get("PartName") == "/word/document.xml"
            and node.get("ContentType")
            == "application/vnd.openxmlformats-officedocument.wordprocessingml.document.main+xml"
            for node in types
        ):
            raise ValueError("Unsupported Word content type")
        with archive.open("word/document.xml") as part:
            xml = part.read(xml_budget + 1)
        if len(xml) > xml_budget:
            raise ParserError(413, "CONTENT_TOO_LARGE", "DOCX document part exceeds its limit")
        root = fromstring(xml, forbid_dtd=True, forbid_entities=True, forbid_external=True)
    body = root.find(f"{n}body")
    if root.tag != f"{n}document" or body is None:
        raise ValueError("Invalid Word document body")
    total = 0
    for number, paragraph in enumerate(body.iter(f"{n}p"), 1):
        text = "".join(
            (element.text or "")
            if element.tag == f"{n}t"
            else "\t"
            if element.tag == f"{n}tab"
            else "\n"
            for element in paragraph.iter()
            if element.tag in {f"{n}t", f"{n}tab", f"{n}br"}
        )
        total += len(text.encode("utf-8"))
        if total > 4 * 1024 * 1024 or number > 50000:
            raise ParserError(413, "CONTENT_TOO_LARGE", "Word content exceeds extraction budget")
        yield number, text


def pdf_pages(content: bytes, require_text: bool) -> Iterable[tuple[int, str]]:
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
            if require_text and not text.strip():
                raise ParserError(422, "OCR_REQUIRED", "A PDF page has no extractable text")
            yield page_number, text
