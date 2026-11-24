import asyncio
from datetime import UTC, datetime
from itertools import zip_longest
from typing import Annotated

import httpx
from fastapi import APIRouter, Header, Request

from testops_ai.core.workspace_identity import WorkspaceDelegation
from testops_ai.features.ingestion.models import Chunk, KnowledgeSearchTool, ReadyVersion
from testops_ai.features.ingestion.service import IngestionService
from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.models import AuthorizedScope, ObjectRef
from testops_ai.features.robot_parser.router import ParserRuntime

from .assessment import compare_run, timing_assessment_requested
from .generation import generate, retrieval_query, task_for
from .models import AnswerCandidate, AnswerContext, EvidenceRef, Proof

router = APIRouter(prefix="/internal/v2/ai")
MAX_ATTACHMENT = 256 * 1024


def source_locators(part: Chunk, query: str) -> list[str]:
    if part.page or part.paragraph:
        label = f"page {part.page}" if part.page else f"paragraph {part.paragraph}"
        first = part.offset // 4000 + 1
        last = (part.offset + len(part.text) - 1) // 4000 + 1
        return [label if n == 1 else f"{label} part {n}" for n in range(first, last + 1)]
    if not part.start_line:
        return []
    # Rank actual nonblank lines inside a retrieved Markdown chunk. Blank/title lines
    # at the top of a chunk must not hide a matching clause further down the page.
    terms = {
        query[i : i + 2].casefold() for i in range(len(query) - 1) if query[i : i + 2].isalnum()
    }
    scores = []
    for offset, line in enumerate(part.text.split("\n")):
        if line.strip():
            overlap = sum(term in line.casefold() for term in terms)
            scores.append((overlap, not line.lstrip().startswith("#"), offset))
    scores.sort(key=lambda row: (-row[0], -row[1], row[2]))
    return [f"line {part.start_line + offset}" for _, _, offset in scores[:5]]


async def search(
    context: AnswerContext, grant: WorkspaceDelegation, service: IngestionService, query_text: str
) -> list[EvidenceRef]:
    by_id = {v.version_id: v for v in context.versions}

    async def chunk(ids: list[str]) -> list[tuple[float, EvidenceRef]]:
        bases = sorted({by_id[i].knowledge_base_id for i in ids})
        scope = AuthorizedScope(
            project_id=context.scope.project_id,
            actor_id=grant.actor_id,
            knowledge_base_ids=bases,
            document_ids=ids,
            case_ids=[],
            run_ids=[],
            allowed_tools=["search_knowledge"],
            expires_at=datetime.fromtimestamp(grant.exp, UTC).isoformat(),
            case_scope="PROJECT",
        )
        ready = [
            ReadyVersion(
                project_id=scope.project_id,
                document_id=i,
                knowledge_base_id=by_id[i].knowledge_base_id,
                content_hash=by_id[i].content_hash,
            )
            for i in ids
        ]
        query = KnowledgeSearchTool(
            query=query_text,
            knowledge_base_ids=bases,
            document_ids=ids,
            limit=7,
        )
        hits = await service.search(query, scope, ready, service.settings.answer_embedding_profile)
        refs = []
        for hit in hits:
            part = hit.chunk
            for locator in source_locators(part, query_text):
                refs.append(
                    (
                        hit.score,
                        EvidenceRef(
                            resource_kind="DOCUMENT",
                            resource_id=hit.document_id,
                            version=by_id[hit.document_id].version_label,
                            locator=locator,
                        ),
                    )
                )
        return refs

    groups = []
    remaining = dict(by_id)
    for basis in (context.basis_current, context.basis_at_run):
        if basis:
            group = [i for i in basis.visible_version_ids if i in remaining]
            if group:
                groups.append(group)
            for i in group:
                del remaining[i]
    if remaining:
        groups.append(list(remaining))
    ranked = []
    for group in groups:
        results = []
        for offset in range(0, len(group), 20):
            results.extend(await chunk(group[offset : offset + 20]))
        # Stable sorting retains the relevance order of lines inside the same chunk.
        results.sort(key=lambda item: -item[0])
        ranked.append([ref for _, ref in results])
    return [ref for round_ in zip_longest(*ranked) for ref in round_ if ref is not None]


async def resolve(
    context: AnswerContext,
    source: EvidenceRef,
    runtime: ParserRuntime,
    delegation: str,
    request_id: str,
) -> Proof | None:
    if not runtime.reader.configured:
        raise ParserError(503, "EVIDENCE_UNAVAILABLE", "Evidence service is not configured")
    headers = {
        "Authorization": "Bearer " + runtime.tokens.callback_token(context.scope.turn_id),
        "X-Delegation-Token": delegation,
        "X-Request-Id": request_id,
    }
    try:
        response = await runtime.reader.client.post(
            runtime.settings.server_base_url.rstrip("/") + "/internal/v2/evidence/resolve",
            json={"turnId": context.scope.turn_id, "source": source.model_dump(by_alias=True)},
            headers=headers,
            timeout=5,
        )
        if response.status_code == 404:
            return None
        response.raise_for_status()
        proof = Proof.model_validate_json(response.content)
        if proof.source != source:
            raise ValueError("Mismatched evidence source")
        return proof
    except (httpx.HTTPError, ValueError) as exc:
        raise ParserError(503, "EVIDENCE_UNAVAILABLE", "Evidence is unavailable") from exc


@router.post("/answer", response_model=AnswerCandidate)
async def compose_answer(
    context: AnswerContext,
    request: Request,
    authorization: Annotated[str, Header()] = "",
    x_delegation_token: Annotated[str, Header()] = "",
    x_object_delegation_token: Annotated[str, Header()] = "",
) -> AnswerCandidate:
    runtime: ParserRuntime = request.app.state.parser
    grant = runtime.tokens.verify_workspace_delegation(
        authorization,
        x_delegation_token,
        context.scope.turn_id,
        "composeAnswer",
        scope=context.scope,
    )
    request_id = str(request.scope["parser_request_id"])
    ingestion: IngestionService | None = request.app.state.ingestion
    if context.versions and not runtime.settings.embedding_configured:
        raise ParserError(503, "EMBEDDING_NOT_CONFIGURED", "Embedding model is not configured")
    if context.versions and ingestion is None:
        raise ParserError(503, "KNOWLEDGE_UNAVAILABLE", "Knowledge search is unavailable")
    try:
        async with asyncio.timeout(110):
            attachments = []
            for file in context.files:
                obj = ObjectRef(
                    object_id=file.object_id,
                    sha256=file.sha256,
                    size_bytes=file.size_bytes,
                    logical_path=file.logical_path,
                )
                raw = await runtime.reader.read(
                    obj,
                    context.scope.turn_id,
                    x_object_delegation_token,
                    request_id,
                    MAX_ATTACHMENT,
                )
                try:
                    data = raw.decode("utf-8-sig")
                except UnicodeError as exc:
                    raise ParserError(
                        422, "ATTACHMENT_ENCODING", "Use a UTF-8 text attachment"
                    ) from exc
                attachments.append(
                    {
                        "name": file.logical_path,
                        "text": data[:12000],
                        "coverage": "完整" if len(data) <= 12000 else "仅前12000字符",
                    }
                )
            query = retrieval_query(context, attachments)
            refs = (
                await search(context, grant, ingestion, query)
                if context.versions and ingestion
                else []
            )
            proofs = []
            seen = set()
            for ref in refs[:25]:
                key = (ref.resource_id, ref.locator)
                if key in seen:
                    continue
                seen.add(key)
                proof = await resolve(context, ref, runtime, x_delegation_token, request_id)
                if proof:
                    proofs.append(proof)
                if len(proofs) >= 10:
                    break
            task = task_for(context)
            comparison = compare_run(context, proofs) if context.runs else None
            if (
                comparison is not None
                and task == "COMPARE"
                and timing_assessment_requested(context, proofs)
            ):
                return comparison
            if context.runs:
                proofs.extend(context.runs[0].facts[:30])
            draft = await generate(
                context,
                proofs,
                attachments,
                runtime,
                task,
                comparison.findings if comparison else [],
            )
            return AnswerCandidate(
                summary=draft.summary,
                robot_status=comparison.robot_status if comparison else "NOT_RUN",
                findings=[],
                robot_draft=draft.robot_draft,
                pending=draft.pending,
                sources=[proofs[i - 1].source for i in dict.fromkeys(draft.sources)],
            )
    except TimeoutError as exc:
        raise ParserError(503, "ANSWER_TIMEOUT", "Answer exceeded its time limit") from exc
