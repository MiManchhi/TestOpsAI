import asyncio
import json
import threading
from uuid import uuid4

import pytest
from ingestion_helpers import (
    MARKDOWN,
    client_for,
    embedding_reply,
    finish,
    index_auth,
    index_body,
    scope,
)
from test_ingestion_api import search

from testops_ai.features.ingestion.documents import chunk_text
from testops_ai.features.ingestion.store import JobStore
from testops_ai.features.robot_parser.errors import ParserError


def test_partial_write_is_not_published_and_delete_cleans_it(
    configured, keys, tmp_path, monkeypatch
):
    body = index_body()
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path) as client:
        service = client.app.state.ingestion
        original = service.vectors.write

        async def fail_after_write(*args):
            await original(*args)
            raise RuntimeError("private upstream failure")

        monkeypatch.setattr(service.vectors, "write", fail_after_write)
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        job = finish(client, response.json()["id"], auth)
        assert job["status"] == "FAILED"
        assert "private" not in job["error"]["message"]
        assert search(client, body) == []
        manifest = service.store.manifests("1")[0]
        assert not manifest.active
        assert client.portal.call(service.vectors.client.count, manifest.collection).count == 1
        deletion = {
            "operationId": str(uuid4()),
            "documentId": "10",
            "contentHash": body["contentHash"],
        }
        auth = index_auth(keys, deletion)
        response = client.post("/internal/v1/index-deletions", json=deletion, headers=auth)
        assert finish(client, response.json()["id"], auth)["status"] == "SUCCEEDED"
        assert client.portal.call(service.vectors.client.count, manifest.collection).count == 0


def test_delete_failure_stays_invisible_and_new_operation_retries(
    configured, keys, tmp_path, monkeypatch
):
    body = index_body()
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        assert finish(client, response.json()["id"], auth)["status"] == "SUCCEEDED"
        service = client.app.state.ingestion
        original = service.vectors.delete_version
        calls = []

        async def fail_delete(*target):
            calls.append(target)
            raise RuntimeError("dependency unavailable")

        monkeypatch.setattr(service.vectors, "delete_version", fail_delete)
        deletion = {
            "operationId": str(uuid4()),
            "documentId": "10",
            "contentHash": body["contentHash"],
        }
        auth = index_auth(keys, deletion)
        response = client.post("/internal/v1/index-deletions", json=deletion, headers=auth)
        assert finish(client, response.json()["id"], auth)["status"] == "FAILED"
        assert len(calls) == 4
        assert search(client, body) == []
        monkeypatch.setattr(service.vectors, "delete_version", original)
        deletion["operationId"] = str(uuid4())
        auth = index_auth(keys, deletion)
        response = client.post("/internal/v1/index-deletions", json=deletion, headers=auth)
        assert finish(client, response.json()["id"], auth)["status"] == "SUCCEEDED"


def test_delete_cancels_inflight_index_and_dispatcher_continues(configured, keys, tmp_path):
    entered = threading.Event()

    async def blocked_embedding(_):
        entered.set()
        await asyncio.sleep(120)

    body = index_body()
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path, embedding_callback=blocked_embedding) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        index_id = response.json()["id"]
        assert entered.wait(15)
        deletion = {
            "operationId": str(uuid4()),
            "documentId": "10",
            "contentHash": body["contentHash"],
        }
        deletion_auth = index_auth(keys, deletion)
        response = client.post("/internal/v1/index-deletions", json=deletion, headers=deletion_auth)
        assert response.status_code == 202
        assert finish(client, response.json()["id"], deletion_auth)["status"] == "SUCCEEDED"
        assert finish(client, index_id, auth)["status"] == "CANCELLED"
        assert client.app.state.ingestion.store.manifests("1") == []


def test_processing_job_recovers_from_staged_input_without_token(configured, keys, tmp_path):
    body = index_body()
    store = JobStore(tmp_path / "state")
    job = store.enqueue(
        scope="recovery",
        digest="digest",
        operation_id=body["operationId"],
        project="1",
        actor="2",
        document="10",
        content_hash=body["contentHash"],
        request_json=json.dumps(body),
        request_id=str(uuid4()),
        content=MARKDOWN,
    )
    job.public.status = "PROCESSING"
    job.public.stage = "EMBEDDING"
    store.save(job)
    store.close()

    def no_download(_):
        pytest.fail("Recovered jobs use verified staged input, not expired delegation tokens")

    with client_for(configured, tmp_path, object_callback=no_download) as client:
        assert finish(client, job.public.id, index_auth(keys, body))["status"] == "SUCCEEDED"
        assert len(search(client, body)) == 1


def test_delete_waits_for_inflight_vector_write_before_cleanup(
    configured, keys, tmp_path, monkeypatch
):
    entered, release, written = threading.Event(), threading.Event(), threading.Event()
    body = index_body()
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path) as client:
        service = client.app.state.ingestion
        original = service.vectors.write

        async def delayed_write(*args):
            entered.set()
            while not release.is_set():
                await asyncio.sleep(0.01)
            await original(*args)
            written.set()

        monkeypatch.setattr(service.vectors, "write", delayed_write)
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        index_id = response.json()["id"]
        assert entered.wait(15)
        manifest = service.store.manifests("1")[0]
        deletion = {
            "operationId": str(uuid4()),
            "documentId": "10",
            "contentHash": body["contentHash"],
        }
        deletion_auth = index_auth(keys, deletion)
        response = client.post("/internal/v1/index-deletions", json=deletion, headers=deletion_auth)
        delete_id = response.json()["id"]
        assert response.status_code == 202
        assert search(client, body) == []
        assert client.get("/internal/v1/index-jobs/" + delete_id, headers=deletion_auth).json()[
            "status"
        ] in {"QUEUED", "PROCESSING"}
        release.set()
        assert finish(client, delete_id, deletion_auth)["status"] == "SUCCEEDED"
        assert finish(client, index_id, auth)["status"] == "CANCELLED"
        assert written.is_set()
        assert client.portal.call(service.vectors.client.count, manifest.collection).count == 0
        assert service.store.manifests("1") == []
        # Simulate a delayed remote write after an ambiguous network timeout.
        chunks = chunk_text(MARKDOWN.decode(), body["contentHash"])
        client.portal.call(original, manifest, chunks, [[1.0, 0.2, 0.1]])
        assert search(client, body) == []
        deletion["operationId"] = str(uuid4())
        deletion_auth = index_auth(keys, deletion)
        response = client.post("/internal/v1/index-deletions", json=deletion, headers=deletion_auth)
        assert finish(client, response.json()["id"], deletion_auth)["status"] == "SUCCEEDED"
        assert client.portal.call(service.vectors.client.count, manifest.collection).count == 0


def test_restart_after_partial_vector_write_is_deduplicated(configured, keys, tmp_path):
    entered = threading.Event()
    calls = []

    async def partial_embedding(request):
        calls.append(request)
        if len(calls) == 1:
            return embedding_reply(request)
        entered.set()
        await asyncio.sleep(120)

    content = b"Self-made meter protocol. " * 150
    body = index_body(content)
    auth = index_auth(keys, body)
    vector_path = tmp_path / "qdrant"
    with client_for(
        configured,
        tmp_path,
        content,
        vector_path=vector_path,
        embedding_callback=partial_embedding,
        batch_size=1,
    ) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        job_id = response.json()["id"]
        assert entered.wait(15)
        service = client.app.state.ingestion
        manifest = service.store.manifests("1")[0]
        assert not manifest.active
        assert client.portal.call(service.vectors.client.count, manifest.collection).count == 1
    with client_for(configured, tmp_path, content, vector_path=vector_path, batch_size=1) as client:
        job = finish(client, job_id, auth)
        assert job["status"] == "SUCCEEDED", job
        manifest = client.app.state.ingestion.store.manifests("1")[0]
        count = client.portal.call(
            client.app.state.ingestion.vectors.client.count, manifest.collection
        ).count
        assert count == job["chunkCount"] > 1


def test_immutable_profiles_and_ready_version_filter(configured, keys, tmp_path):
    body = index_body()
    auth = index_auth(keys, body)
    with client_for(configured, tmp_path) as client:
        response = client.post("/internal/v1/index-jobs", json=body, headers=auth)
        assert finish(client, response.json()["id"], auth)["status"] == "SUCCEEDED"
        assert search(client, {**body, "contentHash": "0" * 64}) == []
        with pytest.raises(ParserError):
            search(client, body, signed_scope={**scope(), "expiresAt": "2020-01-01T00:00:00Z"})
        with pytest.raises(ParserError):
            search(client, body, signed_scope={**scope(), "expiresAt": "2030-01-01T00:00:00"})
        service = client.app.state.ingestion
        service.settings.embedding_profiles["demo"].model = "different-model"
        body["operationId"] = str(uuid4())
        response = client.post("/internal/v1/index-jobs", json=body, headers=index_auth(keys, body))
        assert response.status_code == 503
        assert response.json()["code"] == "PROFILE_CHANGED"
