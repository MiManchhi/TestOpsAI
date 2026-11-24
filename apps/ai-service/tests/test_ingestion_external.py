"""Opt-in remote checks. Always use an isolated Qdrant collection, never production data."""

import asyncio
import os
from uuid import uuid4

import httpx
import pytest
from pydantic import SecretStr
from qdrant_client import AsyncQdrantClient

from testops_ai.core.settings import EmbeddingProfile
from testops_ai.features.ingestion.documents import chunk_text
from testops_ai.features.ingestion.embedding import HttpEmbedding
from testops_ai.features.ingestion.models import Manifest
from testops_ai.features.ingestion.vectors import VectorIndex
from testops_ai.features.robot_parser.errors import ParserError


def test_real_qdrant_http_isolated_collection():
    url = os.environ.get("TESTOPS_AI_VERIFY_QDRANT_URL")
    if not url:
        pytest.skip("Explicit TESTOPS_AI_VERIFY_QDRANT_URL is required")
    collection = "testops_ai_verify_" + uuid4().hex

    async def run():
        client = AsyncQdrantClient(
            url=url,
            api_key=os.environ.get("TESTOPS_AI_VERIFY_QDRANT_API_KEY"),
            timeout=15,
            check_compatibility=False,
        )
        vectors = VectorIndex(client)
        try:
            profile = EmbeddingProfile(
                url="http://unused.test/embeddings", model="isolated-test", dimensions=3
            )
            await vectors.ensure(collection, profile)
            manifest = Manifest(
                project_id="1",
                knowledge_base_id="2",
                document_id="3",
                content_hash="a" * 64,
                parser_version="knowledge-v1",
                embedding_profile="isolated-test",
                generation=str(uuid4()),
                collection=collection,
            )
            chunks = chunk_text("Self-made meter protocol verification.", "a" * 64)
            await vectors.write(manifest, chunks, [[1.0, 0.0, 0.0]])
            assert len(await vectors.search(manifest, [1.0, 0.0, 0.0], 10)) == 1
            outside = manifest.model_copy(update={"project_id": "99"})
            assert await vectors.search(outside, [1.0, 0.0, 0.0], 10) == []
            await vectors.delete(manifest)
            assert await vectors.search(manifest, [1.0, 0.0, 0.0], 10) == []
        finally:
            try:
                if await client.collection_exists(collection):
                    await client.delete_collection(collection, timeout=15)
            finally:
                await client.close()

    asyncio.run(run())


def test_real_embedding_http():
    url = os.environ.get("TESTOPS_AI_VERIFY_EMBEDDING_URL")
    model = os.environ.get("TESTOPS_AI_VERIFY_EMBEDDING_MODEL")
    dimensions = os.environ.get("TESTOPS_AI_VERIFY_EMBEDDING_DIMENSIONS")
    if not url or not model or not dimensions:
        pytest.skip("Explicit embedding verification URL/model/dimensions are required")

    async def run():
        async with httpx.AsyncClient(trust_env=False, follow_redirects=False) as client:
            vectors = await HttpEmbedding(client).embed(
                [
                    "Self-made meter reading check.",
                    "Self-made protocol timeout check.",
                ],
                EmbeddingProfile(
                    url=url,
                    model=model,
                    dimensions=int(dimensions),
                    api_key=SecretStr(os.environ.get("TESTOPS_AI_VERIFY_EMBEDDING_API_KEY", "")),
                ),
            )
            assert len(vectors) == 2

    asyncio.run(run())


def test_embedding_provider_auth_failure_is_explicit():
    async def run():
        async def reject(_):
            return httpx.Response(
                401,
                json={
                    "error": {
                        "code": "invalid_api_key",
                        "message": "Incorrect API key provided",
                    }
                },
            )

        async with httpx.AsyncClient(
            transport=httpx.MockTransport(reject), trust_env=False, follow_redirects=False
        ) as client:
            with pytest.raises(ParserError, match="Embedding provider rejected"):
                await HttpEmbedding(client).embed(
                    ["连接测试"],
                    EmbeddingProfile(
                        url="http://provider.test/embeddings",
                        model="text-embedding-v4",
                        dimensions=3,
                        api_key=SecretStr("test"),
                    ),
                )

    asyncio.run(run())
