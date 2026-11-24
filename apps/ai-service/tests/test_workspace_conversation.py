"""Regression coverage for continuing an authorized conversation with a selected run."""

import json
import os
import time
from uuid import uuid4

import httpx
import jwt
import pytest
from conftest import headers
from fastapi.testclient import TestClient
from pydantic import SecretStr

from testops_ai.app import create_app
from testops_ai.core.settings import Settings
from testops_ai.core.workspace_identity import AnswerScope
from testops_ai.features.answers.generation import task_for
from testops_ai.features.answers.models import AnswerContext

DRAFT = (
    "*** Test Cases ***\n检查拒绝原因\n    [Documentation]    被拒绝时检查审计原因\n"
    "    Skip    TODO 接入实际关键字"
)


def context():
    scope = AnswerScope(
        project_id="1",
        turn_id=str(uuid4()),
        auth_revision=1,
        version_ids=[],
        run_ids=["7"],
        attachment_ids=[],
    )
    return AnswerContext.model_validate(
        {
            "scope": scope.model_dump(by_alias=True),
            "text": "把刚才的复测写成 Robot 回归用例。",
            "currentMessageId": str(uuid4()),
            "history": [
                {"author": "USER", "text": "上次拒绝操作发生了什么？"},
                {"author": "ASSISTANT", "answer": {"summary": "拒绝原因未记录。"}},
            ],
            "basisAtRun": None,
            "basisCurrent": None,
            "files": [],
            "versions": [],
            "runs": [
                {
                    "runId": "7",
                    "name": "拒绝操作复测",
                    "executedAt": "2026-11-19T18:00:00Z",
                    "results": [
                        {
                            "resultId": "1",
                            "caseName": "拒绝操作",
                            "suite": "抄表",
                            "robotStatus": "FAIL",
                            "artifactId": "2",
                        }
                    ],
                    "facts": [
                        {
                            "source": {
                                "resourceKind": "RUN_LOG",
                                "resourceId": "3",
                                "version": "V1",
                                "locator": "line 4",
                            },
                            "excerpt": "DENIED reason=(empty)",
                        }
                    ],
                }
            ],
        }
    )


def signed(keys, ctx):
    scope = ctx.scope
    auth = headers(keys, scope.turn_id, [])
    now = int(time.time())
    grant = {
        "iss": "testops-server",
        "aud": "testops-server-tools",
        "iat": now,
        "exp": now + 60,
        "jti": str(uuid4()),
        "operationId": scope.turn_id,
        "actorId": "2",
        "projectId": "1",
        "authorizationVersion": 1,
        "allowActions": ["composeAnswer", "resolveEvidence"],
        "scopeDigest": scope.digest(),
    }
    auth["X-Delegation-Token"] = jwt.encode(grant, keys[0][0], algorithm="RS256")
    return auth


@pytest.mark.parametrize(
    ("text", "expected"),
    [
        ("复测达标吗？", "COMPARE"),
        ("运行 #7", "COMPARE"),
        ("为什么不达标？", "EXPLAIN"),
        ("生成一份失败原因总结", "EXPLAIN"),
        ("帮我评审这条 Robot 用例，覆盖了哪些条款？", "REVIEW"),
        ("帮我把漏掉的补上", "DRAFT"),
        ("不要生成用例，告诉我为什么", "EXPLAIN"),
        ("给一份可复制的用例", "DRAFT"),
    ],
)
def test_task_changes_with_question(text, expected):
    assert task_for(context().model_copy(update={"text": text})) == expected


def test_followup_draft_preserves_history_run_evidence_and_requires_valid_robot(configured, keys):
    ctx = context()
    configured.chat_model = "meter-chat"
    configured.chat_base_url = "http://provider.test/v1"
    requests = []

    def provider(req):
        assert req.url.path == "/v1/chat/completions"
        assert "authorization" not in req.headers
        body = json.loads(req.content)
        payload = json.loads(body["messages"][1]["content"])
        requests.append(payload)
        response = {
            "summary": "操作被拒绝后应检查审计记录。",
            "pending": ["设备审计关键字待确认"],
            "robotDraft": DRAFT if len(requests) > 1 else None,
            "sources": [1],
        }
        return httpx.Response(
            200, json={"choices": [{"message": {"content": json.dumps(response)}}]}
        )

    with TestClient(create_app(configured, httpx.MockTransport(provider))) as client:
        response = client.post(
            "/internal/v2/ai/answer", json=ctx.model_dump(by_alias=True), headers=signed(keys, ctx)
        )
    assert response.status_code == 200, response.text
    assert len(requests) == 2
    assert requests[0]["history"][-1]["text"] == "拒绝原因未记录。"
    assert requests[0]["run"]["results"][0]["status"] == "FAIL"
    assert requests[0]["evidence"][0]["text"] == "DENIED reason=(empty)"
    assert response.json()["robotDraft"] == DRAFT
    assert response.json()["findings"] == []
    assert response.json()["robotStatus"] == "FAIL"
    assert response.json()["sources"] == [ctx.runs[0].facts[0].source.model_dump(by_alias=True)]


def test_invalid_sources_or_code_do_not_reach_users(configured, keys):
    ctx = context()
    configured.chat_model = "meter-chat"
    configured.chat_base_url = "http://provider.test/v1"

    def bad(_):
        draft = {"summary": "test", "robotDraft": "plain prose", "pending": [], "sources": [999]}
        return httpx.Response(200, json={"choices": [{"message": {"content": json.dumps(draft)}}]})

    with TestClient(create_app(configured, httpx.MockTransport(bad))) as client:
        res = client.post(
            "/internal/v2/ai/answer", json=ctx.model_dump(by_alias=True), headers=signed(keys, ctx)
        )
        assert res.status_code == 503 and res.json()["code"] == "ANSWER_MODEL_FAILED"


def test_provider_auth_failure_is_explicit(configured, keys):
    ctx = context()
    configured.chat_model = "meter-chat"
    configured.chat_base_url = "http://provider.test/v1"

    def reject(_):
        return httpx.Response(
            401,
            json={"error": {"code": "invalid_api_key", "message": "Incorrect API key provided"}},
        )

    with TestClient(create_app(configured, httpx.MockTransport(reject))) as client:
        res = client.post(
            "/internal/v2/ai/answer", json=ctx.model_dump(by_alias=True), headers=signed(keys, ctx)
        )
    assert res.status_code == 503
    assert res.json()["code"] == "ANSWER_PROVIDER_AUTH_FAILED"


def test_missing_configuration_is_visible(configured, keys, monkeypatch):
    ctx = context()
    with TestClient(create_app(configured)) as client:
        assert client.get("/health/configuration").json() == {
            "chatModelConfigured": False,
            "embeddingConfigured": False,
        }
        res = client.post(
            "/internal/v2/ai/answer", json=ctx.model_dump(by_alias=True), headers=signed(keys, ctx)
        )
        assert res.status_code == 503 and res.json()["code"] == "MODEL_NOT_CONFIGURED"
        assert client.post("/internal/v1/parsers/robot").status_code == 404
    for name, value in {
        "URL": "https://embed.test/v1/embeddings",
        "MODEL": "test",
        "DIMENSIONS": "384",
    }.items():
        monkeypatch.setenv("TESTOPS_AI_EMBEDDING_" + name, value)
    settings = Settings()
    assert settings.embedding_configured
    assert settings.embedding_profiles["default"].dimensions == 384
    monkeypatch.delenv("TESTOPS_AI_EMBEDDING_MODEL")
    assert not Settings().embedding_configured


@pytest.mark.parametrize("dimensions", ["0", ""])
def test_blank_and_zero_dimension_mean_provider_not_configured(monkeypatch, dimensions):
    monkeypatch.setenv("TESTOPS_AI_EMBEDDING_DIMENSIONS", dimensions)
    monkeypatch.setenv("TESTOPS_AI_EMBEDDING_URL", "http://embed.test/v1/embeddings")
    monkeypatch.setenv("TESTOPS_AI_EMBEDDING_MODEL", "pending")
    config = Settings()
    with TestClient(create_app(config)) as client:
        assert client.get("/health/ready").status_code == 200
        assert client.get("/health/configuration").json()["embeddingConfigured"] is False


def test_opt_in_real_model_produces_grounded_robot(configured, keys):
    """Send only this synthetic scenario to the explicitly selected deployment."""
    url = os.environ.get("TESTOPS_AI_VERIFY_CHAT_BASE_URL")
    model = os.environ.get("TESTOPS_AI_VERIFY_CHAT_MODEL")
    if not url or not model:
        pytest.skip("Explicit chat verification URL and model are required")
    ctx = context()
    config = configured.model_copy(
        update={
            "chat_base_url": url,
            "chat_model": model,
            "chat_api_key": SecretStr(os.environ.get("TESTOPS_AI_VERIFY_CHAT_API_KEY", "")),
            "ingestion_state_dir": None,
        }
    )
    ctx.text = "根据上轮讨论及这次运行的日志，补写一条记录拒绝原因的 Robot 用例并附日志出处。"
    with TestClient(create_app(config)) as client:
        answer = client.post(
            "/internal/v2/ai/answer", json=ctx.model_dump(by_alias=True), headers=signed(keys, ctx)
        )
    assert answer.status_code == 200, answer.text[:150]
    data = answer.json()
    assert data["robotStatus"] == "FAIL"
    assert data["robotDraft"] and "*** Test Cases ***" in data["robotDraft"]
    assert ctx.runs[0].facts[0].source.model_dump(by_alias=True) in data["sources"]
