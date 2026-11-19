import hashlib
import time
from datetime import UTC, datetime
from uuid import uuid4

import httpx
from conftest import headers
from fastapi.testclient import TestClient
from pydantic import SecretStr
from qdrant_client import AsyncQdrantClient

from testops_ai.app import create_app
from testops_ai.core.settings import EmbeddingProfile

MARKDOWN = "# 自造计量规范\n\n校验电能读数。\n\n```robot\nLog    demo only\n```\n".encode()


def index_body(content=MARKDOWN, *, document="10", knowledge_base="20", path="manual.md"):
    digest = hashlib.sha256(content).hexdigest()
    return {
        "operationId": str(uuid4()),
        "documentId": document,
        "knowledgeBaseId": knowledge_base,
        "contentHash": digest,
        "object": {
            "objectId": str(uuid4()),
            "sha256": digest,
            "sizeBytes": len(content),
            "logicalPath": path,
        },
        "parserVersion": "knowledge-v1",
        "embeddingProfile": "demo",
    }


def scope(*, project="1", documents=None, knowledge_bases=None):
    return {
        "projectId": project,
        "actorId": "2",
        "knowledgeBaseIds": knowledge_bases or ["20"],
        "documentIds": documents if documents is not None else ["10"],
        "caseIds": [],
        "runIds": [],
        "allowedTools": ["search_knowledge"],
        "caseScope": "PROJECT",
        "expiresAt": datetime.fromtimestamp(time.time() + 120, UTC).isoformat(),
    }


def index_auth(keys, body, *, scope_changes=None, **kwargs):
    signed_scope = scope()
    signed_scope.update(scope_changes or {})
    auth = headers(
        keys,
        body["operationId"],
        [body["object"]["objectId"]] if "object" in body else [],
        delegation_changes={
            "scope": signed_scope,
            "projectId": signed_scope["projectId"],
            **kwargs,
        },
    )
    return {**auth, "Idempotency-Key": str(uuid4())}


def embedding_reply(request):
    import json

    data = json.loads(request.content)
    return httpx.Response(
        200,
        json={
            "data": [
                {"index": i, "embedding": [1.0, 0.2, 0.1]} for i, _ in enumerate(data["input"])
            ]
        },
    )


def client_for(
    configured,
    tmp_path,
    content=MARKDOWN,
    *,
    object_callback=None,
    embedding_callback=embedding_reply,
    vector_path=None,
    batch_size=16,
):
    config = configured.model_copy(
        update={
            "ingestion_state_dir": tmp_path / "state",
            "embedding_profiles": {
                "demo": EmbeddingProfile(
                    url="http://embedding.test/v1/embeddings",
                    model="self-made-test",
                    dimensions=3,
                    api_key=SecretStr("temporary-test-key"),
                    batch_size=batch_size,
                )
            },
        }
    )
    vectors = (
        AsyncQdrantClient(path=str(vector_path))
        if vector_path
        else AsyncQdrantClient(location=":memory:")
    )
    return TestClient(
        create_app(
            config,
            object_transport=httpx.MockTransport(
                object_callback or (lambda _: httpx.Response(200, content=content))
            ),
            embedding_transport=httpx.MockTransport(embedding_callback),
            vector_client=vectors,
            vector_local_mode=True,
        )
    )


def finish(client, job_id, auth):
    deadline = time.monotonic() + 20
    while time.monotonic() < deadline:
        response = client.get("/internal/v1/index-jobs/" + job_id, headers=auth)
        assert response.status_code == 200, response.text
        job = response.json()
        if job["status"] in {"SUCCEEDED", "FAILED", "CANCELLED"}:
            return job
        time.sleep(0.03)
    raise AssertionError("Job did not finish")
