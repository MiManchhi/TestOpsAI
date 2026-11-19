from concurrent.futures import ThreadPoolExecutor
from uuid import uuid4

import jwt
import pytest
from conftest import validate_contract
from fastapi.testclient import TestClient
from ingestion_helpers import (
    MARKDOWN,
    client_for,
    finish,
    index_auth,
    index_body,
    scope,
)

from testops_ai.app import create_app
from testops_ai.features.ingestion.models import KnowledgeSearchTool, ReadyVersion
from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.models import AuthorizedScope


def search(client, body, *, signed_scope=None, ready=True):
    service = client.app.state.ingestion
    request = KnowledgeSearchTool(
        query="电能读数", knowledge_base_ids=["20"], document_ids=[], limit=10
    )
    versions = (
        [
            ReadyVersion(
                project_id="1",
                knowledge_base_id="20",
                document_id=body["documentId"],
                content_hash=body["contentHash"],
            )
        ]
        if ready
        else None
    )
    return client.portal.call(
        service.search,
        request,
        AuthorizedScope.model_validate(signed_scope or scope()),
        versions,
        "demo",
    )


def test_index_poll_search_and_delete_real_local_vectors(configured, keys, tmp_path):
    body = index_body()
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        assert response.status_code == 202, response.text
        validate_contract("IndexJob", response.json())
        job = finish(client, response.json()["id"], auth)
        assert job["status"] == "SUCCEEDED", job
        assert job["chunkCount"] == 1
        hits = search(client, body)
        assert hits[0].chunk.text == MARKDOWN.decode()
        assert not hasattr(hits[0], "citation_id")
        assert list((tmp_path / "state/inputs").iterdir()) == []
        deletion = {
            "operationId": str(uuid4()),
            "documentId": "10",
            "contentHash": body["contentHash"],
        }
        delete_auth = index_auth(keys, deletion)
        response = client.post("/internal/v1/index-deletions", json=deletion, headers=delete_auth)
        assert response.status_code == 202, response.text
        assert search(client, body) == []  # tombstone is effective before physical cleanup
        assert finish(client, response.json()["id"], delete_auth)["status"] == "SUCCEEDED"
        assert client.app.state.ingestion.store.manifests("1") == []
        response = client.post(
            "/internal/v1/index-jobs",
            json={**body, "operationId": str(uuid4())},
            headers=index_auth(keys, body),
        )
        assert response.status_code == 403  # operation binding remains enforced


def test_concurrent_retry_one_job_and_logical_index_reuse(configured, keys, tmp_path):
    body, auth = index_body(), None
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path) as client:

        def post(_):
            response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
            assert response.status_code == 202, response.text
            return response.json()["id"]

        with ThreadPoolExecutor(2) as pool:
            ids = list(pool.map(post, range(2)))
        assert ids[0] == ids[1]
        assert finish(client, ids[0], auth)["status"] == "SUCCEEDED"
        changed = {**body, "parserVersion": "changed"}
        assert client.post("/internal/v1/index-jobs", json=changed, headers=auth).status_code == 409
        changed_document = {**body, "documentId": "11"}
        document_auth = index_auth(
            keys, changed_document, scope_changes={"documentIds": ["10", "11"]}
        )
        document_auth["Idempotency-Key"] = auth["Idempotency-Key"]
        conflict = client.post(
            "/internal/v1/index-jobs", json=changed_document, headers=document_auth
        )
        assert conflict.status_code == 409
        assert conflict.json()["code"] == "IDEMPOTENCY_CONFLICT"
        second = {**body, "operationId": str(uuid4())}
        second_auth = index_auth(keys, second)
        response = client.post("/internal/v1/index-jobs", json=second, headers=second_auth)
        assert response.status_code == 202
        assert finish(client, response.json()["id"], second_auth)["status"] == "SUCCEEDED"
        assert len(client.app.state.ingestion.store.manifests("1")) == 1
        row = client.app.state.ingestion.store.db.execute("SELECT count(*) FROM jobs").fetchone()
        assert row[0] == 2


@pytest.mark.parametrize(
    "mutation,status",
    [
        ("doc", 403),
        ("knowledge", 403),
        ("object", 403),
        ("expired", 401),
        ("bad-signature", 401),
        ("operation", 403),
        ("unknown", 400),
        ("parser", 422),
        ("profile", 503),
        ("hash", 422),
        ("key", 400),
    ],
)
def test_admission_rejects_invalid_scope_before_download(
    configured, keys, tmp_path, mutation, status
):
    body = index_body()
    auth = index_auth(keys, body)
    if mutation == "doc":
        auth = index_auth(keys, body, scope_changes={"documentIds": []})
    elif mutation == "knowledge":
        auth = index_auth(keys, body, scope_changes={"knowledgeBaseIds": []})
    elif mutation == "object":
        auth = index_auth(keys, body, objectIds=[])
    elif mutation == "expired":
        auth = index_auth(keys, body, exp=1)
    elif mutation == "bad-signature":
        auth["Authorization"] = "Bearer " + jwt.encode({}, keys[1][0], algorithm="RS256")
    elif mutation == "operation":
        body["operationId"] = str(uuid4())
    elif mutation == "unknown":
        body["unknown"] = "not allowed"
    elif mutation == "parser":
        body["parserVersion"] = "unknown"
    elif mutation == "profile":
        body["embeddingProfile"] = "unknown"
    elif mutation == "hash":
        body["contentHash"] = "0" * 64
    else:
        auth["Idempotency-Key"] = "invalid"

    def no_download(_):
        pytest.fail("Invalid request must not download objects")

    with client_for(configured, tmp_path, object_callback=no_download) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        assert response.status_code == status, response.text
        validate_contract("Error", response.json())


def test_state_disabled_health_and_job_operation_isolation(configured, keys, tmp_path):
    with TestClient(create_app(configured)) as client:
        assert client.post("/internal/v1/index-jobs", json={}).status_code == 503
        assert client.get("/health/ready").status_code == 200
    body = index_body()
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        job_id = response.json()["id"]
        assert finish(client, job_id, auth)["status"] == "SUCCEEDED"
        other = index_auth(keys, {**body, "operationId": str(uuid4())})
        assert client.get("/internal/v1/index-jobs/" + job_id, headers=other).status_code == 404
        with pytest.raises(ParserError) as error:
            search(client, body, ready=False)
        assert error.value.code == "READY_VERSIONS_REQUIRED"
        with pytest.raises(ParserError):
            search(client, body, signed_scope=scope(knowledge_bases=["99"]))
        assert search(client, body, signed_scope=scope(project="999")) == []
        database_dump = "\n".join(client.app.state.ingestion.store.db.iterdump())
        assert auth["X-Delegation-Token"] not in database_dump
        assert "temporary-test-key" not in database_dump


def test_completed_job_survives_sqlite_and_vector_restart(configured, keys, tmp_path):
    body, vector_path = index_body(), tmp_path / "qdrant"
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path, vector_path=vector_path) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        job_id = response.json()["id"]
        assert finish(client, job_id, auth)["status"] == "SUCCEEDED"
    with client_for(configured, tmp_path, vector_path=vector_path) as client:
        assert finish(client, job_id, auth)["status"] == "SUCCEEDED"
        assert len(search(client, body)) == 1
