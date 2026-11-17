"""Run and verify the M0 development chain, stopping only children created here."""

import contextlib
import json
import os
import shutil
import socket
import subprocess
import sys
import time
import urllib.error
import urllib.request
from pathlib import Path


def main() -> None:
    root = Path(__file__).resolve().parents[1]
    logs = root / ".runtime" / "smoke-local"
    logs.mkdir(parents=True, exist_ok=True)
    ports: set[int] = set()
    while len(ports) < 3:
        with socket.socket() as listener:
            listener.bind(("127.0.0.1", 0))
            ports.add(listener.getsockname()[1])
    server_port, ai_port, web_port = sorted(ports)
    server_url = f"http://127.0.0.1:{server_port}"
    ai_url = f"http://127.0.0.1:{ai_port}"
    web_url = f"http://127.0.0.1:{web_port}"

    children: list[subprocess.Popen[bytes]] = []
    # Loopback requests must not be sent through an inherited HTTP proxy.
    client = urllib.request.build_opener(urllib.request.ProxyHandler({}))

    def wait(url: str, process: subprocess.Popen[bytes]) -> bytes:
        for _ in range(45):
            if process.poll() is not None:
                raise RuntimeError(f"Process exited before readiness: {url}; see {logs}")
            try:
                with client.open(url, timeout=2) as response:
                    if response.status == 200:
                        payload = response.read()
                        if not isinstance(payload, bytes):
                            raise TypeError("HTTP response must contain bytes")
                        return payload
            except (OSError, urllib.error.URLError):
                pass
            time.sleep(1)
        raise TimeoutError(f"Readiness timed out: {url}; see {logs}")

    with contextlib.ExitStack() as stack:

        def start(
            name: str,
            command: list[str],
            directory: Path,
            environment: dict[str, str] | None = None,
        ) -> subprocess.Popen[bytes]:
            creation_flags = 0
            if sys.platform == "win32":
                creation_flags = subprocess.CREATE_NO_WINDOW
            process = subprocess.Popen(
                command,
                cwd=directory,
                env=environment,
                stdout=stack.enter_context((logs / f"{name}.out.log").open("wb")),
                stderr=stack.enter_context((logs / f"{name}.err.log").open("wb")),
                creationflags=creation_flags,
            )
            children.append(process)
            return process

        try:
            java_bin = shutil.which("java")
            node_bin = shutil.which("node")
            if not java_bin or not node_bin:
                raise RuntimeError("Activate scripts/env.ps1 before running smoke checks")
            java = start(
                "server",
                [
                    java_bin,
                    "-jar",
                    str(root / "apps/server/target/testops-server-0.1.0-SNAPSHOT.jar"),
                    f"--server.port={server_port}",
                ],
                root,
            )
            ai = start(
                "ai",
                [
                    sys.executable,
                    "-m",
                    "uvicorn",
                    "testops_ai.main:app",
                    "--host",
                    "127.0.0.1",
                    "--port",
                    str(ai_port),
                ],
                root,
            )
            web = start(
                "web",
                [
                    node_bin,
                    str(root / "node_modules/vite/bin/vite.js"),
                    "--host",
                    "127.0.0.1",
                    "--port",
                    str(web_port),
                ],
                root / "apps/web",
                {**os.environ, "TESTOPS_SERVER_URL": server_url},
            )
            server_health = json.loads(wait(f"{server_url}/actuator/health", java))
            assert server_health.get("status") == "UP" and "components" not in server_health
            for path in ("live", "ready"):
                assert json.loads(wait(f"{ai_url}/health/{path}", ai)) == {"status": "UP"}
            assert b"/src/app/main.ts" in wait(f"{web_url}/", web)
            assert json.loads(wait(f"{web_url}/actuator/health", web)).get("status") == "UP"
            try:
                client.open(f"{server_url}/api/v1/users/me", timeout=3)
            except urllib.error.HTTPError as error:
                assert error.code == 401
            else:
                raise AssertionError("Business surface must remain closed")
            print("Local smoke passed: Java security, AI health, Web entrypoint and Java proxy.")
        finally:
            for process in reversed(children):
                if process.poll() is None:
                    process.terminate()
                    try:
                        process.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        process.kill()
                        process.wait(timeout=10)


if __name__ == "__main__":
    main()
