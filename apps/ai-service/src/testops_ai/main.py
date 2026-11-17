"""Stable ASGI entry point; application assembly lives in app.py."""

from testops_ai.app import create_app

__all__ = ["app", "create_app"]

app = create_app()
