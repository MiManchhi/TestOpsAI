import asyncio
import logging
from contextlib import suppress
from datetime import datetime
from typing import Literal
from uuid import NAMESPACE_URL, uuid5

from testops_ai.core.service_tokens import ServiceTokens
from testops_ai.core.settings import Settings
from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.models import AuthorizedScope, DelegationClaims
from testops_ai.features.robot_parser.objects import ObjectReader

from .documents import document_format
from .embedding import HttpEmbedding
from .models import (
    PARSER_VERSION,
    Candidate,
    Chunk,
    IndexDelete,
    IndexRequest,
    JobError,
    JobRecord,
    KnowledgeSearchTool,
    Manifest,
    ReadyVersion,
)
from .parser import extract_document
from .store import JobStore, fingerprint
from .vectors import VectorIndex, collection_name


class IngestionService:
    def __init__(
        self,
        settings: Settings,
        store: JobStore,
        tokens: ServiceTokens,
        reader: ObjectReader,
        embedding: HttpEmbedding,
        vectors: VectorIndex | None,
    ) -> None:
        self.settings, self.store, self.tokens = settings, store, tokens
        self.reader, self.embedding, self.vectors = reader, embedding, vectors
        self.admission = asyncio.Semaphore(settings.ingestion_concurrency)
        self.version_locks: dict[tuple[str, str, str], asyncio.Lock] = {}
        self.running: dict[str, tuple[JobRecord, asyncio.Task[None]]] = {}
        self.dispatcher: asyncio.Task[None] | None = None
        self.stopping = False

    def start(self) -> None:
        if self.vectors is not None:
            self.dispatcher = asyncio.create_task(self.dispatch())

    async def close(self) -> None:
        self.stopping = True
        if self.dispatcher:
            self.dispatcher.cancel()
            with suppress(asyncio.CancelledError):
                await self.dispatcher
        tasks = [task for _, task in self.running.values()]
        for task in tasks:
            task.cancel()
        await asyncio.gather(*tasks, return_exceptions=True)
        self.store.close()

    def profile(self, name: str) -> None:
        profile = self.settings.embedding_profiles.get(name)
        if profile is None or not profile.url or self.vectors is None:
            raise ParserError(503, "INDEX_NOT_CONFIGURED", "Index dependencies are not configured")
        self.store.bind_profile(
            name,
            fingerprint(
                {
                    "url": profile.url,
                    "model": profile.model,
                    "dimensions": profile.dimensions,
                    "collectionPrefix": self.settings.qdrant_collection_prefix,
                }
            ),
        )

    @staticmethod
    def authorize(body: IndexRequest | IndexDelete, claims: DelegationClaims) -> None:
        if body.document_id not in claims.scope.document_ids:
            raise ParserError(403, "FORBIDDEN", "Document is outside the delegated scope")
        if isinstance(body, IndexRequest) and (
            body.knowledge_base_id not in claims.scope.knowledge_base_ids
            or body.object.object_id not in claims.object_ids
        ):
            raise ParserError(
                403, "FORBIDDEN", "Knowledge base or object is outside delegated scope"
            )

    async def submit(
        self,
        body: IndexRequest | IndexDelete,
        claims: DelegationClaims,
        key: str,
        delegation: str,
        request_id: str,
    ) -> JobRecord:
        self.authorize(body, claims)
        kind = "index" if isinstance(body, IndexRequest) else "delete"
        scope = fingerprint([claims.project_id, claims.actor_id, kind, key])
        digest = fingerprint(body.model_dump(by_alias=True))
        replay = self.store.replay(scope, digest)
        if replay:
            return replay
        if isinstance(body, IndexRequest):
            if body.parser_version != PARSER_VERSION:
                raise ParserError(
                    422, "UNSUPPORTED_PARSER_VERSION", "Parser version is not supported"
                )
            self.profile(body.embedding_profile)
            if body.content_hash != body.object.sha256:
                raise ParserError(422, "CONTENT_HASH_MISMATCH", "Document and object hashes differ")
            document_format(body.object.logical_path, body.object.size_bytes)
        elif self.vectors is None:
            raise ParserError(503, "INDEX_NOT_CONFIGURED", "Vector store is not configured")
        async with self.admission:
            # A simultaneous retry may have finished downloading while this request queued.
            replay = self.store.replay(scope, digest)
            if replay:
                return replay
            content = None
            if isinstance(body, IndexRequest):
                content = await self.reader.read(
                    body.object, body.operation_id, delegation, request_id, 20 * 1024 * 1024
                )
                self.tokens.verify_delegation(delegation, body.operation_id)
            record = self.store.enqueue(
                scope=scope,
                digest=digest,
                operation_id=body.operation_id,
                project=claims.project_id,
                actor=claims.actor_id,
                document=body.document_id,
                content_hash=body.content_hash,
                request_json=body.model_dump_json(),
                request_id=request_id,
                content=content,
            )
            if kind == "delete":
                for active, task in list(self.running.values()):
                    if active.kind == "index" and self.version(active) == self.version(record):
                        task.cancel()
            return record

    @staticmethod
    def version(record: JobRecord) -> tuple[str, str, str]:
        return record.project_id, record.document_id, record.content_hash

    def deleted(self, record: JobRecord) -> bool:
        return self.store.deleted(*self.version(record))

    def stage(
        self, record: JobRecord, stage: Literal["PARSING", "CHUNKING", "EMBEDDING", "CLEANUP"]
    ) -> None:
        record.public.stage = stage
        self.store.save(record)

    async def dispatch(self) -> None:
        while True:
            for job_id, (_, task) in list(self.running.items()):
                if task.done():
                    with suppress(asyncio.CancelledError):
                        task.result()
                    self.running.pop(job_id)
            available = self.settings.ingestion_concurrency - len(self.running)
            for record in self.store.pending()[:available]:
                record.public.status = "PROCESSING"
                self.store.save(record)
                self.running[record.public.id] = (record, asyncio.create_task(self.execute(record)))
            await asyncio.sleep(0.05)

    async def execute(self, record: JobRecord) -> None:
        lock = self.version_locks.setdefault(self.version(record), asyncio.Lock())
        try:
            async with lock:
                if record.kind == "index":
                    await self.index(record)
                else:
                    await self.delete(record)
                record.public.status = "SUCCEEDED"
                record.public.stage = "FINISHED"
                self.finish_input(record)
        except asyncio.CancelledError:
            if record.kind == "index" and self.deleted(record):
                record.public.status = "CANCELLED"
                self.finish_input(record)
            else:
                record.public.status = "QUEUED"
                self.store.save(record)
            raise
        except Exception as error:
            if isinstance(error, ParserError):
                code, message = error.code, error.message
            else:
                code, message = "INDEX_FAILED", "Index dependency or processing failure"
            record.public.status = (
                "CANCELLED" if record.kind == "index" and self.deleted(record) else "FAILED"
            )
            record.public.error = JobError(code=code, message=message, request_id=record.request_id)
            self.finish_input(record)

    def finish_input(self, record: JobRecord) -> None:
        # Publishing succeeded independently of unlinking staging bytes. Keep their quota
        # reservation until actual cleanup; startup retries terminal input cleanup.
        self.store.save(record)
        try:
            self.store.release_input(record)
        except OSError:
            logging.getLogger(__name__).error(
                "Input cleanup deferred requestId=%s", record.request_id
            )

    async def index(self, record: JobRecord) -> None:
        assert self.vectors is not None
        body = IndexRequest.model_validate_json(record.request_json)
        self.profile(body.embedding_profile)
        if self.deleted(record):
            raise ParserError(409, "VERSION_DELETED", "Document version was deleted")
        profile = self.settings.embedding_profiles[body.embedding_profile]
        generation = str(
            uuid5(
                NAMESPACE_URL,
                fingerprint(
                    [
                        record.project_id,
                        body.knowledge_base_id,
                        body.document_id,
                        body.content_hash,
                        body.parser_version,
                        body.embedding_profile,
                    ]
                ),
            )
        )
        existing = next(
            (m for m in self.store.manifests(*self.version(record)) if m.generation == generation),
            None,
        )
        if existing and existing.active:
            record.public.chunk_count = existing.chunk_count
            return
        self.stage(record, "PARSING")
        chunks = await extract_document(
            self.store.input_path(record),
            body.object.logical_path,
            body.content_hash,
            self.settings.document_timeout_seconds,
        )
        self.stage(record, "CHUNKING")
        manifest = Manifest(
            project_id=record.project_id,
            knowledge_base_id=body.knowledge_base_id,
            document_id=body.document_id,
            content_hash=body.content_hash,
            parser_version=body.parser_version,
            embedding_profile=body.embedding_profile,
            generation=generation,
            collection=collection_name(
                self.settings.qdrant_collection_prefix, body.embedding_profile
            ),
            chunk_count=len(chunks),
        )
        await self.vectors.ensure(manifest.collection, profile)
        if existing:
            await self.vectors.delete(existing)
        self.store.put_manifest(manifest)
        self.stage(record, "EMBEDDING")
        for start in range(0, len(chunks), profile.batch_size):
            if self.deleted(record):
                raise ParserError(409, "VERSION_DELETED", "Document version was deleted")
            batch = chunks[start : start + profile.batch_size]
            values = await self.embedding.embed([chunk.text for chunk in batch], profile)
            await self.write_batch(manifest, batch, values)
        with self.store.transaction():
            if self.deleted(record):
                raise ParserError(409, "VERSION_DELETED", "Document version was deleted")
            manifest.active = True
            self.store.put_manifest(manifest)
            record.public.chunk_count = len(chunks)
            record.public.status = "SUCCEEDED"
            record.public.stage = "FINISHED"
            self.store.save(record)

    async def write_batch(
        self, manifest: Manifest, chunks: list[Chunk], values: list[list[float]]
    ) -> None:
        assert self.vectors is not None

        async def confirmed_write() -> None:
            async with asyncio.timeout(15):
                assert self.vectors is not None
                await self.vectors.write(manifest, chunks, values)

        # Cancelling an HTTP client does not retract a write already sent to Qdrant.
        # Keep the version lock until that request settles before deletion can run.
        writing = asyncio.create_task(confirmed_write())
        cancelled = False
        while not writing.done():
            try:
                await asyncio.shield(writing)
            except asyncio.CancelledError:
                cancelled = True
            except Exception:
                break
        if cancelled:
            with suppress(Exception, asyncio.CancelledError):
                writing.result()
            raise asyncio.CancelledError
        writing.result()

    async def delete(self, record: JobRecord) -> None:
        assert self.vectors is not None
        self.stage(record, "CLEANUP")
        manifests = self.store.manifests(*self.version(record))
        # Profile names survive manifest cleanup, allowing a new deletion operation
        # to remove even a late remote write whose original HTTP result was unknown.
        collections = {manifest.collection for manifest in manifests} | {
            collection_name(self.settings.qdrant_collection_prefix, name)
            for name in self.store.profile_names()
        }
        for collection in sorted(collections):
            for attempt in range(4):
                try:
                    await self.vectors.delete_version(collection, *self.version(record))
                    break
                except asyncio.CancelledError:
                    raise
                except Exception:
                    if attempt == 3:
                        raise
                    await asyncio.sleep(0.1 * (attempt + 1))
        with self.store.transaction():
            for manifest in manifests:
                self.store.forget_manifest(manifest.generation)

    async def search(
        self,
        query: KnowledgeSearchTool,
        scope: AuthorizedScope,
        ready_versions: list[ReadyVersion] | None,
        profile_name: str,
    ) -> list[Candidate]:
        try:
            async with asyncio.timeout(15):
                return await self._search(query, scope, ready_versions, profile_name)
        except TimeoutError as exc:
            raise ParserError(
                503, "SEARCH_TIMEOUT", "Knowledge search exceeded its time limit"
            ) from exc

    async def _search(
        self,
        query: KnowledgeSearchTool,
        scope: AuthorizedScope,
        ready_versions: list[ReadyVersion] | None,
        profile_name: str,
    ) -> list[Candidate]:
        try:
            expiry = datetime.fromisoformat(scope.expires_at.replace("Z", "+00:00"))
            live = expiry.tzinfo is not None and expiry.timestamp() > datetime.now().timestamp()
        except ValueError:
            live = False
        if "search_knowledge" not in scope.allowed_tools or not live:
            raise ParserError(403, "FORBIDDEN", "Knowledge search scope is not valid")
        knowledge_bases = set(query.knowledge_base_ids) & set(scope.knowledge_base_ids)
        documents = set(query.document_ids)
        if scope.document_ids:
            documents = (
                documents & set(scope.document_ids) if documents else set(scope.document_ids)
            )
        if not knowledge_bases or (query.document_ids and not documents):
            raise ParserError(403, "FORBIDDEN", "Requested knowledge scope has empty intersection")
        if ready_versions is None:
            raise ParserError(503, "READY_VERSIONS_REQUIRED", "Trusted READY versions are required")
        self.profile(profile_name)
        assert self.vectors is not None
        ready = {
            (v.project_id, v.knowledge_base_id, v.document_id, v.content_hash)
            for v in ready_versions
        }
        manifests = [
            m
            for m in self.store.manifests(scope.project_id, active=True)
            if m.embedding_profile == profile_name
            and m.knowledge_base_id in knowledge_bases
            and (not documents or m.document_id in documents)
            and (m.project_id, m.knowledge_base_id, m.document_id, m.content_hash) in ready
            and not self.store.deleted(m.project_id, m.document_id, m.content_hash)
        ]
        if not manifests:
            return []
        if len(manifests) > 1000:
            raise ParserError(
                413, "SEARCH_SCOPE_TOO_LARGE", "Search scope requires a smaller batch"
            )
        vector = (
            await self.embedding.embed(
                [query.query], self.settings.embedding_profiles[profile_name]
            )
        )[0]
        candidates: list[Candidate] = []
        by_generation = {manifest.generation: manifest for manifest in manifests}
        for hit in await self.vectors.search_group(manifests, vector, query.limit):
            payload = hit.payload or {}
            manifest = by_generation.get(str(payload.get("generation")))
            if manifest is None or any(
                payload.get(key) != value
                for key, value in {
                    "projectId": manifest.project_id,
                    "knowledgeBaseId": manifest.knowledge_base_id,
                    "documentId": manifest.document_id,
                    "contentHash": manifest.content_hash,
                    "parserVersion": manifest.parser_version,
                    "embeddingProfile": manifest.embedding_profile,
                }.items()
            ):
                raise ParserError(
                    503, "INVALID_VECTOR_PAYLOAD", "Vector result failed scope validation"
                )
            chunk = Chunk.model_validate(payload.get("chunk"))
            candidates.append(
                Candidate(
                    document_id=manifest.document_id,
                    content_hash=manifest.content_hash,
                    knowledge_base_id=manifest.knowledge_base_id,
                    chunk=chunk,
                    score=hit.score,
                )
            )
        visible = [
            candidate
            for candidate in candidates
            if not self.store.deleted(
                scope.project_id, candidate.document_id, candidate.content_hash
            )
        ]
        return sorted(visible, key=lambda c: (-c.score, c.document_id, c.chunk.id))[: query.limit]
