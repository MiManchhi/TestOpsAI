"""RS256 internal identity. Java remains the authority for live resource permissions."""

import time
from datetime import datetime
from uuid import uuid4

import jwt
from cryptography.hazmat.primitives.asymmetric.rsa import RSAPrivateKey, RSAPublicKey
from cryptography.hazmat.primitives.serialization import load_pem_private_key, load_pem_public_key
from pydantic import ValidationError

from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.models import DelegationClaims, ServiceClaims

from .settings import Settings
from .workspace_identity import AnswerScope, WorkspaceAction, WorkspaceDelegation


class ServiceTokens:
    def __init__(self, settings: Settings) -> None:
        self.public_key: RSAPublicKey | None = None
        self.private_key: RSAPrivateKey | None = None
        try:
            public = load_pem_public_key(settings.server_public_key.get_secret_value().encode())
            private = load_pem_private_key(settings.private_key.get_secret_value().encode(), None)
            if (
                isinstance(public, RSAPublicKey)
                and public.key_size >= 2048
                and isinstance(private, RSAPrivateKey)
                and private.key_size >= 2048
            ):
                self.public_key, self.private_key = public, private
        except (ValueError, TypeError):
            # Health remains available without deployment configuration; routes fail closed.
            pass

    def require_configured(self) -> None:
        if self.public_key is None or self.private_key is None:
            raise ParserError(503, "SERVICE_NOT_CONFIGURED", "Parser service is not configured")

    def verify_service(self, authorization: str) -> ServiceClaims:
        self.require_configured()
        if not authorization.startswith("Bearer "):
            raise self.unauthorized()
        raw = self.decode(authorization[7:], "testops-ai")
        try:
            claims = ServiceClaims.model_validate(raw, by_alias=True, by_name=False)
            self.check_lifetime(claims.iat, claims.exp, 300)
            return claims
        except (ValueError, ValidationError) as exc:
            raise self.unauthorized() from exc

    def verify_delegation(self, token: str, operation_id: str) -> DelegationClaims:
        raw = self.decode(token, "testops-server-tools")
        try:
            claims = DelegationClaims.model_validate(raw, by_alias=True, by_name=False)
            self.check_lifetime(claims.iat, claims.exp, 120)
            expiry = datetime.fromisoformat(claims.scope.expires_at.replace("Z", "+00:00"))
            if expiry.tzinfo is None or expiry.timestamp() <= time.time():
                raise ValueError("Scope is expired")
        except (ValueError, ValidationError) as exc:
            raise self.unauthorized() from exc
        if (
            claims.operation_id != operation_id
            or claims.project_id != claims.scope.project_id
            or claims.actor_id != claims.scope.actor_id
        ):
            raise ParserError(403, "FORBIDDEN", "Delegation does not match the operation scope")
        return claims

    def verify_workspace_delegation(
        self,
        authorization: str,
        token: str,
        operation_id: str,
        action: WorkspaceAction,
        *,
        scope: AnswerScope | None = None,
        index_source: tuple[str, str, str] | None = None,
    ) -> WorkspaceDelegation:
        identity = self.verify_service(authorization)
        raw = self.decode(token, "testops-server-tools")
        try:
            claims = WorkspaceDelegation.model_validate(raw, by_alias=True, by_name=False)
            self.check_lifetime(claims.iat, claims.exp, 120)
        except (ValueError, ValidationError) as exc:
            raise self.unauthorized() from exc

        matches = identity.operation_id == operation_id == claims.operation_id
        if matches and action in claims.allow_actions:
            if (
                action == "composeAnswer" and scope is not None and claims.matches_answer(scope)
            ) or (
                action == "exportIndexBlocks"
                and index_source == (claims.project_id, claims.version_id, claims.content_hash)
            ):
                return claims
        raise ParserError(403, "FORBIDDEN", "Delegation does not match the workspace operation")

    def decode(self, token: str, audience: str) -> dict[str, object]:
        if not token or len(token) > 12000 or self.public_key is None:
            raise self.unauthorized()
        try:
            result: dict[str, object] = jwt.decode(
                token,
                self.public_key,
                algorithms=["RS256"],
                issuer="testops-server",
                audience=audience,
                options={"require": ["iss", "aud", "iat", "exp", "jti", "operationId"]},
            )
            return result
        except jwt.PyJWTError as exc:
            raise self.unauthorized() from exc

    @staticmethod
    def check_lifetime(iat: int, exp: int, maximum: int) -> None:
        now = time.time()
        if not iat <= now < exp or not 0 < exp - iat <= maximum:
            raise ValueError("Invalid token lifetime")

    def callback_token(self, operation_id: str) -> str:
        self.require_configured()
        assert self.private_key is not None
        now = int(time.time())
        claims = ServiceClaims(
            iss="testops-ai",
            aud="testops-server-tools",
            iat=now,
            exp=now + 60,
            jti=str(uuid4()),
            operation_id=operation_id,
        )
        return jwt.encode(claims.model_dump(by_alias=True), self.private_key, algorithm="RS256")

    @staticmethod
    def unauthorized() -> ParserError:
        return ParserError(401, "UNAUTHORIZED", "Valid internal credentials are required")
