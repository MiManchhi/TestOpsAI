import asyncio
import math
from typing import Annotated
from urllib.parse import urlsplit

import httpx
from pydantic import BaseModel, ConfigDict, Field, ValidationError

from testops_ai.core.settings import EmbeddingProfile
from testops_ai.features.robot_parser.errors import ParserError


class EmbeddingDatum(BaseModel):
    model_config = ConfigDict(strict=True, extra="ignore")
    index: Annotated[int, Field(ge=0)]
    embedding: list[float]


class EmbeddingResponse(BaseModel):
    model_config = ConfigDict(strict=True, extra="ignore")
    data: list[EmbeddingDatum]


class HttpEmbedding:
    def __init__(self, client: httpx.AsyncClient) -> None:
        self.client = client

    async def embed(self, texts: list[str], profile: EmbeddingProfile) -> list[list[float]]:
        address = urlsplit(profile.url)
        if (
            address.scheme not in {"http", "https"}
            or not address.hostname
            or address.username
            or address.password
            or address.query
            or address.fragment
        ):
            raise ParserError(
                503, "EMBEDDING_NOT_CONFIGURED", "Invalid embedding endpoint configuration"
            )
        if not texts or len(texts) > profile.batch_size:
            raise ValueError("Invalid embedding batch")
        headers = {"Accept-Encoding": "identity"}
        if profile.api_key.get_secret_value():
            headers["Authorization"] = "Bearer " + profile.api_key.get_secret_value()
        for attempt in range(3):
            try:
                async with asyncio.timeout(15):
                    async with self.client.stream(
                        "POST",
                        profile.url,
                        headers=headers,
                        json={"model": profile.model, "input": texts, "encoding_format": "float"},
                        timeout=15,
                        follow_redirects=False,
                    ) as response:
                        if response.status_code in {429, 500, 502, 503, 504}:
                            raise httpx.ReadError("Transient embedding failure")
                        if response.status_code != 200:
                            break
                        body = bytearray()
                        async for chunk in response.aiter_bytes(65536):
                            if len(body) + len(chunk) > 16 * 1024 * 1024:
                                raise ValueError("Embedding response too large")
                            body.extend(chunk)
                        data = EmbeddingResponse.model_validate_json(bytes(body)).data
                        if len(data) != len(texts) or sorted(item.index for item in data) != list(
                            range(len(texts))
                        ):
                            raise ValueError("Embedding count/index mismatch")
                        vectors = [
                            item.embedding for item in sorted(data, key=lambda item: item.index)
                        ]
                        for vector in vectors:
                            if (
                                len(vector) != profile.dimensions
                                or not all(math.isfinite(value) for value in vector)
                                or not any(value != 0 for value in vector)
                            ):
                                raise ValueError("Invalid embedding vector")
                        return vectors
            except (httpx.HTTPError, TimeoutError):
                if attempt < 2:
                    await asyncio.sleep(0.1 * (attempt + 1))
                    continue
            except (ValidationError, ValueError):
                break
        raise ParserError(
            503, "EMBEDDING_UNAVAILABLE", "Embedding service returned no valid vectors"
        )
