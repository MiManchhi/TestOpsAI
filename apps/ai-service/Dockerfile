FROM ghcr.io/astral-sh/uv:0.13.8 AS uv
FROM python:3.12-slim-bookworm
COPY --from=uv /uv /usr/local/bin/uv
WORKDIR /app
COPY pyproject.toml uv.lock ./
COPY src ./src
RUN uv sync --locked --no-dev && groupadd --system app && useradd --system --gid app app
USER app
EXPOSE 8000
CMD [".venv/bin/uvicorn", "testops_ai.main:app", "--host", "0.0.0.0", "--port", "8000"]
