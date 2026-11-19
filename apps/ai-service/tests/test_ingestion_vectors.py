import asyncio
from uuid import uuid4

import pytest
from qdrant_client import AsyncQdrantClient

from testops_ai.core.settings import EmbeddingProfile
from testops_ai.features.ingestion.documents import chunk_text
from testops_ai.features.ingestion.models import Manifest
from testops_ai.features.ingestion.vectors import VectorIndex
from testops_ai.features.robot_parser.errors import ParserError


def test_real_local_group_filter_and_schema_validation():
    async def run():
        client = AsyncQdrantClient(location=":memory:")
        vectors = VectorIndex(client, local_mode=True)
        profile = EmbeddingProfile(
            url="http://embedding.test/embeddings", model="demo", dimensions=3
        )
        try:
            await vectors.ensure("isolated_test", profile)
            chunks = chunk_text("Self-made protocol text.", "a" * 64)
            first = Manifest(
                project_id="1",
                knowledge_base_id="20",
                document_id="10",
                content_hash="a" * 64,
                parser_version="knowledge-v1",
                embedding_profile="demo",
                generation=str(uuid4()),
                collection="isolated_test",
            )
            second = first.model_copy(update={"project_id": "99", "generation": str(uuid4())})
            old_generation = first.model_copy(update={"generation": str(uuid4())})
            await vectors.write(first, chunks, [[1.0, 0.0, 0.0]])
            await vectors.write(second, chunks, [[1.0, 0.0, 0.0]])
            await vectors.write(old_generation, chunks, [[1.0, 0.0, 0.0]])
            assert (await client.count("isolated_test")).count == 3
            assert len(await vectors.search_group([first], [1.0, 0.0, 0.0], 10)) == 1
            for field, value in [
                ("project_id", "9"),
                ("knowledge_base_id", "9"),
                ("document_id", "9"),
                ("content_hash", "b" * 64),
                ("generation", str(uuid4())),
                ("parser_version", "unknown"),
                ("embedding_profile", "other"),
            ]:
                assert (
                    await vectors.search(
                        first.model_copy(update={field: value}), [1.0, 0.0, 0.0], 10
                    )
                    == []
                )
            with pytest.raises(ParserError):
                await vectors.ensure("isolated_test", profile.model_copy(update={"dimensions": 4}))
            await vectors.delete(first)
            assert (await client.count("isolated_test")).count == 1
            assert len(await vectors.search(second, [1.0, 0.0, 0.0], 10)) == 1
        finally:
            await client.close()

    asyncio.run(run())
