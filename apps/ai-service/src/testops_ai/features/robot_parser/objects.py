import asyncio
import hashlib
from urllib.parse import urlsplit

import httpx

from testops_ai.core.service_tokens import ServiceTokens
from testops_ai.core.settings import Settings

from .errors import ParserError, invalid_content
from .models import ObjectRef


class ObjectReader:
    def __init__(
        self, settings: Settings, tokens: ServiceTokens, client: httpx.AsyncClient
    ) -> None:
        self.settings, self.tokens, self.client = settings, tokens, client
        base = urlsplit(settings.server_base_url)
        self.configured = (
            base.scheme in {"http", "https"}
            and bool(base.hostname)
            and not base.username
            and not base.password
            and base.path in {"", "/"}
            and not base.query
            and not base.fragment
        )

    async def read(
        self, ref: ObjectRef, operation_id: str, delegation: str, request_id: str, maximum: int
    ) -> bytes:
        if not self.configured:
            raise ParserError(503, "SERVICE_NOT_CONFIGURED", "Object service is not configured")
        if ref.size_bytes > maximum:
            raise ParserError(413, "CONTENT_TOO_LARGE", "Object exceeds the permitted size")
        headers = {
            "Authorization": "Bearer " + self.tokens.callback_token(operation_id),
            "X-Delegation-Token": delegation,
            "X-Request-Id": request_id,
            "Accept-Encoding": "identity",
        }
        url = self.settings.server_base_url.rstrip("/") + (
            f"/internal/v1/objects/{ref.object_id}/content"
        )
        try:
            async with asyncio.timeout(self.settings.object_timeout_seconds):
                async with self.client.stream(
                    "GET",
                    url,
                    headers=headers,
                    follow_redirects=False,
                    timeout=self.settings.object_timeout_seconds,
                ) as response:
                    if response.status_code in {401, 403, 404}:
                        raise ParserError(
                            response.status_code,
                            "OBJECT_ACCESS_DENIED",
                            "Authorized object is unavailable",
                        )
                    if response.status_code != 200:
                        raise ParserError(
                            503, "OBJECT_SERVICE_UNAVAILABLE", "Object service is unavailable"
                        )
                    if response.headers.get("content-encoding", "identity") != "identity":
                        raise invalid_content("Compressed object responses are not accepted")
                    digest = hashlib.sha256()
                    content = bytearray()
                    async for chunk in response.aiter_bytes(65536):
                        if len(content) + len(chunk) > min(ref.size_bytes, maximum):
                            raise ParserError(
                                413, "CONTENT_TOO_LARGE", "Object stream exceeds limit"
                            )
                        content.extend(chunk)
                        digest.update(chunk)
                    if len(content) != ref.size_bytes or digest.hexdigest() != ref.sha256:
                        raise invalid_content("Object size or digest mismatch")
                    return bytes(content)
        except (httpx.HTTPError, TimeoutError) as exc:
            raise ParserError(
                503, "OBJECT_SERVICE_UNAVAILABLE", "Object service is unavailable"
            ) from exc
