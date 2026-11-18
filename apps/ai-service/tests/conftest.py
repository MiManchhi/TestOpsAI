import hashlib
import json
import time
from datetime import UTC, datetime
from pathlib import Path
from uuid import uuid4

import jwt
import pytest
from cryptography.hazmat.primitives import serialization
from cryptography.hazmat.primitives.asymmetric import rsa
from jsonschema import Draft7Validator, FormatChecker
from pydantic import SecretStr

from testops_ai.core.settings import Settings
from testops_ai.features.robot_parser.catalog import SourceFile
from testops_ai.features.robot_parser.models import ObjectRef

ROBOT = b"*** Settings ***\nTest Tags    smoke\n*** Test Cases ***\nExample\n    Log    hello\n"
OUTPUT = b"""<?xml version="1.0"?>
<robot generator="Robot 7.5" schemaversion="5">
<suite name="Demo" source="suites/demo.robot">
<test name="Example"><kw name="Check"><status status="FAIL"/></kw>
<status status="FAIL" elapsed="0.125">expected mismatch</status></test>
<test name="Skipped"><status status="SKIP" elapsed="0"/></test>
<test name="Passed"><status status="PASS" elapsed="0.001"/></test>
<status status="FAIL" start="2026-11-18T01:00:00Z" end="2026-11-18T01:00:01Z"/>
</suite><statistics><total><stat pass="1" fail="1" skip="1">All Tests</stat></total>
</statistics></robot>"""


def source(content: bytes = ROBOT, path: str = "suites/demo.robot") -> SourceFile:
    return SourceFile(
        ObjectRef(
            object_id=str(uuid4()),
            sha256=hashlib.sha256(content).hexdigest(),
            size_bytes=len(content),
            logical_path=path,
        ),
        content,
    )


def validate_contract(name: str, value: object) -> None:
    root = Path(__file__).resolve().parents[3]
    schema = json.loads((root / "docs/contracts/schemas.json").read_text(encoding="utf-8"))
    Draft7Validator(
        {**schema, "$ref": f"#/definitions/{name}"}, format_checker=FormatChecker()
    ).validate(value)


@pytest.fixture(scope="session")
def keys():
    def pair():
        key = rsa.generate_private_key(public_exponent=65537, key_size=2048)
        private = key.private_bytes(
            serialization.Encoding.PEM,
            serialization.PrivateFormat.PKCS8,
            serialization.NoEncryption(),
        ).decode()
        public = (
            key.public_key()
            .public_bytes(
                serialization.Encoding.PEM, serialization.PublicFormat.SubjectPublicKeyInfo
            )
            .decode()
        )
        return private, public

    return pair(), pair()


@pytest.fixture
def configured(keys):
    return Settings(
        server_base_url="http://java.test",
        server_public_key=SecretStr(keys[0][1]),
        private_key=SecretStr(keys[1][0]),
    )


def headers(keys, operation_id, object_ids, *, service_changes=None, delegation_changes=None):
    now = int(time.time())
    service = {
        "iss": "testops-server",
        "aud": "testops-ai",
        "iat": now,
        "exp": now + 120,
        "jti": str(uuid4()),
        "operationId": operation_id,
    }
    scope = {
        "projectId": "1",
        "actorId": "2",
        "knowledgeBaseIds": [],
        "documentIds": [],
        "caseIds": [],
        "runIds": [],
        "allowedTools": [],
        "caseScope": "PROJECT",
        "expiresAt": datetime.fromtimestamp(now + 120, UTC).isoformat(),
    }
    delegation = {
        "iss": "testops-server",
        "aud": "testops-server-tools",
        "iat": now,
        "exp": now + 120,
        "jti": str(uuid4()),
        "operationId": operation_id,
        "actorId": "2",
        "projectId": "1",
        "scope": scope,
        "objectIds": object_ids,
        "authorizationVersion": 1,
    }
    service.update(service_changes or {})
    delegation.update(delegation_changes or {})
    return {
        "Authorization": "Bearer " + jwt.encode(service, keys[0][0], algorithm="RS256"),
        "X-Delegation-Token": jwt.encode(delegation, keys[0][0], algorithm="RS256"),
        "X-Request-Id": str(uuid4()),
    }
