import asyncio

import httpx
import pytest

from testops_ai.core.settings import EmbeddingProfile
from testops_ai.features.ingestion.embedding import HttpEmbedding
from testops_ai.features.robot_parser.errors import ParserError


def run_embed(callback, texts=None):
    async def run():
        async with httpx.AsyncClient(transport=httpx.MockTransport(callback)) as client:
            return await HttpEmbedding(client).embed(
                texts or ["demo"],
                EmbeddingProfile(
                    url="http://embedding.test/v1/embeddings", model="demo", dimensions=3
                ),
            )

    return asyncio.run(run())


def test_retries_and_preserves_input_order():
    calls = []

    def callback(request):
        calls.append(request)
        if len(calls) < 3:
            return httpx.Response(429)
        return httpx.Response(
            200,
            json={
                "data": [
                    {"index": 1, "embedding": [0.0, 1.0, 0.0]},
                    {"index": 0, "embedding": [1.0, 0.0, 0.0]},
                ]
            },
        )

    assert run_embed(callback, ["a", "b"]) == [[1, 0, 0], [0, 1, 0]]
    assert len(calls) == 3


@pytest.mark.parametrize(
    "data",
    [
        {"data": []},
        {"data": [{"index": 0, "embedding": [1.0]}]},
        {"data": [{"index": 1, "embedding": [1.0, 0.0, 0.0]}]},
        {"data": [{"index": 0, "embedding": [0.0, 0.0, 0.0]}]},
        {"data": [{"index": 0, "embedding": ["1", 0, 0]}]},
    ],
)
def test_invalid_embedding_response_never_reaches_vectors(data):
    with pytest.raises(ParserError):
        run_embed(lambda _: httpx.Response(200, json=data))


def test_timeout_retry_is_bounded_and_errors_are_sanitized():
    calls = []

    def timeout(request):
        calls.append(request)
        raise httpx.ReadTimeout("secret upstream details")

    with pytest.raises(ParserError) as error:
        run_embed(timeout)
    assert len(calls) == 3
    assert "secret" not in error.value.message


def test_redirect_is_not_followed():
    calls = []

    def callback(request):
        calls.append(request)
        return httpx.Response(302, headers={"location": "http://untrusted.test"})

    with pytest.raises(ParserError):
        run_embed(callback)
    assert len(calls) == 1
