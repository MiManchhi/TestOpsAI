import asyncio
import hashlib
import re
from uuid import NAMESPACE_URL, uuid5

from qdrant_client import AsyncQdrantClient, models

from testops_ai.core.settings import EmbeddingProfile
from testops_ai.features.robot_parser.errors import ParserError

from .models import Chunk, Manifest


def collection_name(prefix: str, profile: str) -> str:
    if not re.fullmatch(r"[A-Za-z][A-Za-z0-9_-]{0,63}", prefix):
        raise ValueError("Invalid collection prefix")
    return prefix + "_" + hashlib.sha256(profile.encode()).hexdigest()[:16]


def exact(**values: str) -> list[models.Condition]:
    return [
        models.FieldCondition(key=key, match=models.MatchValue(value=value))
        for key, value in values.items()
    ]


class VectorIndex:
    def __init__(self, client: AsyncQdrantClient, *, local_mode: bool = False) -> None:
        self.client = client
        self.local_mode = local_mode
        self.setup_lock = asyncio.Lock()

    async def ensure(self, collection: str, profile: EmbeddingProfile) -> None:
        async with self.setup_lock:
            if not await self.client.collection_exists(collection):
                await self.client.create_collection(
                    collection,
                    vectors_config=models.VectorParams(
                        size=profile.dimensions, distance=models.Distance.COSINE
                    ),
                    timeout=15,
                )
            info = await self.client.get_collection(collection)
            vector = info.config.params.vectors
            if not isinstance(vector, models.VectorParams) or (
                vector.size != profile.dimensions or vector.distance != models.Distance.COSINE
            ):
                raise ParserError(
                    503, "VECTOR_SCHEMA_MISMATCH", "Vector collection schema mismatch"
                )
            # Retry missing payload indexes after a crash during collection initialization.
            if not self.local_mode:
                for field in (
                    "projectId",
                    "knowledgeBaseId",
                    "documentId",
                    "contentHash",
                    "generation",
                    "parserVersion",
                    "embeddingProfile",
                ):
                    if field not in info.payload_schema:
                        await self.client.create_payload_index(
                            collection,
                            field,
                            models.PayloadSchemaType.KEYWORD,
                            wait=True,
                            timeout=15,
                        )

    async def write(
        self, manifest: Manifest, chunks: list[Chunk], vectors: list[list[float]]
    ) -> None:
        points = [
            models.PointStruct(
                id=str(uuid5(NAMESPACE_URL, manifest.generation + "/" + chunk.id)),
                vector=vector,
                payload={
                    "projectId": manifest.project_id,
                    "knowledgeBaseId": manifest.knowledge_base_id,
                    "documentId": manifest.document_id,
                    "contentHash": manifest.content_hash,
                    "parserVersion": manifest.parser_version,
                    "embeddingProfile": manifest.embedding_profile,
                    "generation": manifest.generation,
                    "chunk": chunk.model_dump(by_alias=True),
                },
            )
            for chunk, vector in zip(chunks, vectors, strict=True)
        ]
        result = await self.client.upsert(manifest.collection, points, wait=True, timeout=15)
        if result.status != models.UpdateStatus.COMPLETED:
            raise ParserError(503, "VECTOR_WRITE_PENDING", "Vector write was not confirmed")

    async def delete(self, manifest: Manifest) -> None:
        await self.delete_version(
            manifest.collection, manifest.project_id, manifest.document_id, manifest.content_hash
        )

    async def delete_version(
        self, collection: str, project_id: str, document_id: str, content_hash: str
    ) -> None:
        if await self.client.collection_exists(collection):
            result = await self.client.delete(
                collection,
                models.FilterSelector(
                    filter=models.Filter(
                        must=exact(
                            projectId=project_id,
                            documentId=document_id,
                            contentHash=content_hash,
                        )
                    )
                ),
                wait=True,
                timeout=15,
            )
            if result.status != models.UpdateStatus.COMPLETED:
                raise ParserError(503, "VECTOR_DELETE_PENDING", "Vector deletion was not confirmed")

    async def search(
        self, manifest: Manifest, vector: list[float], limit: int
    ) -> list[models.ScoredPoint]:
        return await self.search_group([manifest], vector, limit)

    async def search_group(
        self, manifests: list[Manifest], vector: list[float], limit: int
    ) -> list[models.ScoredPoint]:
        if not manifests:
            return []
        collection = manifests[0].collection
        if len(manifests) > 1000 or any(m.collection != collection for m in manifests):
            raise ParserError(
                413, "SEARCH_SCOPE_TOO_LARGE", "Search scope requires a smaller batch"
            )
        filters: list[models.Condition] = [
            models.Filter(
                must=exact(
                    projectId=manifest.project_id,
                    knowledgeBaseId=manifest.knowledge_base_id,
                    documentId=manifest.document_id,
                    contentHash=manifest.content_hash,
                    generation=manifest.generation,
                    parserVersion=manifest.parser_version,
                    embeddingProfile=manifest.embedding_profile,
                )
            )
            for manifest in manifests
        ]
        result = await self.client.query_points(
            collection,
            query=vector,
            query_filter=models.Filter(should=filters),
            limit=limit,
            with_payload=True,
            with_vectors=False,
            timeout=15,
        )
        return result.points
