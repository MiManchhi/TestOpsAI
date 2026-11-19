import asyncio
import hashlib
import multiprocessing
from io import BytesIO

import pytest
from ingestion_helpers import MARKDOWN
from pypdf import PdfWriter
from pypdf.generic import ArrayObject, DecodedStreamObject, DictionaryObject, NameObject

from testops_ai.features.ingestion.documents import chunk_text, parse_document
from testops_ai.features.ingestion.parser import extract_document
from testops_ai.features.robot_parser.errors import ParserError


def pdf_bytes(*, text=True, encrypted=False, pages=2, text_content=b"Self-made meter test text"):
    writer = PdfWriter()
    for _ in range(pages):
        page = writer.add_blank_page(300, 300)
        if text:
            font = DictionaryObject(
                {
                    NameObject("/Type"): NameObject("/Font"),
                    NameObject("/Subtype"): NameObject("/Type1"),
                    NameObject("/BaseFont"): NameObject("/Helvetica"),
                }
            )
            page[NameObject("/Resources")] = DictionaryObject(
                {NameObject("/Font"): DictionaryObject({NameObject("/F1"): font})}
            )
            stream = DecodedStreamObject()
            stream.set_data(b"BT /F1 12 Tf 10 250 Td (" + text_content + b") Tj ET")
            page[NameObject("/Contents")] = stream
    if encrypted:
        writer.encrypt("self-made-password")
    output = BytesIO()
    writer.write(output)
    return output.getvalue()


def parse(content, path="manual.md"):
    return parse_document(content, path, hashlib.sha256(content).hexdigest())


def test_markdown_exact_source_lines_code_and_stable_ids():
    chunks = parse(MARKDOWN)
    assert chunks[0].text == MARKDOWN.decode()
    assert chunks[0].start_line == 1
    assert chunks[0].heading == "自造计量规范"
    assert "Log    demo only" in chunks[0].text
    assert chunks == parse(MARKDOWN)


def test_overlap_long_paragraph_and_no_cross_page():
    text = "a" * 2500
    chunks = chunk_text(text, "a" * 64)
    assert [len(c.text) for c in chunks] == [1000, 1000, 700]
    assert chunks[0].text[-100:] == chunks[1].text[:100]
    pdf = parse(pdf_bytes(), "manual.pdf")
    assert {c.page for c in pdf} == {1, 2}
    assert all(c.start_line is None for c in pdf)


@pytest.mark.parametrize(
    "content,path,code",
    [
        (b"\xff", "a.md", "DOCUMENT_PARSE_FAILED"),
        (b"\x00", "a.md", "DOCUMENT_PARSE_FAILED"),
        (b"  \n", "a.md", "NO_TEXT"),
        (b"not PDF", "a.pdf", "DOCUMENT_PARSE_FAILED"),
        (b"%PDF-1.7\nbad", "a.pdf", "DOCUMENT_PARSE_FAILED"),
        (b"#" * 2, "a.html", "UNSUPPORTED_FORMAT"),
        (b"x" * (2 * 1024 * 1024 + 1), "a.md", "CONTENT_TOO_LARGE"),
        (b"x" * (20 * 1024 * 1024 + 1), "a.pdf", "CONTENT_TOO_LARGE"),
    ],
)
def test_invalid_documents_fail(content, path, code):
    with pytest.raises(ParserError) as error:
        parse(content, path)
    assert error.value.code == code


def test_scanned_encrypted_and_page_limit():
    for content, code in [
        (pdf_bytes(text=False), "OCR_REQUIRED"),
        (pdf_bytes(encrypted=True), "ENCRYPTED_PDF"),
        (pdf_bytes(pages=501), "CONTENT_TOO_LARGE"),
    ]:
        with pytest.raises(ParserError) as error:
            parse(content, "a.pdf")
        assert error.value.code == code


def test_source_digest_and_heading_limit():
    with pytest.raises(ParserError):
        parse_document(MARKDOWN, "a.md", "0" * 64)
    with pytest.raises(ParserError):
        parse(b"# " + b"x" * 257)


def test_pdf_extracted_text_budget():
    content = pdf_bytes(pages=5, text_content=b"a" * 900000)
    with pytest.raises(ParserError) as error:
        parse(content, "text-budget.pdf")
    assert error.value.status == 413
    assert error.value.message == "Extracted text exceeds 4 MiB"


@pytest.mark.parametrize("stream_size,repetitions", [(8 * 1024 * 1024 + 1, 1), (1024 * 1024, 17)])
def test_compressed_stream_and_aggregate_budget(stream_size, repetitions):
    writer = PdfWriter()
    page = writer.add_blank_page(300, 300)
    stream = DecodedStreamObject()
    stream.set_data(b" " * stream_size)
    reference = writer._add_object(stream.flate_encode())
    page[NameObject("/Contents")] = ArrayObject([reference] * repetitions)
    output = BytesIO()
    writer.write(output)
    with pytest.raises(ParserError) as error:
        parse(output.getvalue(), "limited.pdf")
    assert error.value.status == 413


def test_document_process_timeout_and_cancellation_are_reaped(tmp_path):
    path = tmp_path / "source"
    path.write_bytes(MARKDOWN)

    async def run():
        initial = {p.pid for p in multiprocessing.active_children()}
        with pytest.raises(ParserError) as error:
            await extract_document(path, "a.md", hashlib.sha256(MARKDOWN).hexdigest(), 0.0001)
        assert error.value.code == "DOCUMENT_TIMEOUT"
        task = asyncio.create_task(
            extract_document(path, "a.md", hashlib.sha256(MARKDOWN).hexdigest(), 30)
        )
        await asyncio.sleep(0.02)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert {p.pid for p in multiprocessing.active_children()} == initial

    asyncio.run(run())
