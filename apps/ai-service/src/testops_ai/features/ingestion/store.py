"""AI-owned state. Append migrations; never open the Java database."""

import hashlib
import json
import os
import sqlite3
import threading
import time
from collections.abc import Iterator
from contextlib import contextmanager
from pathlib import Path
from typing import Literal
from uuid import UUID, uuid4

from filelock import FileLock, Timeout

from testops_ai.features.robot_parser.errors import ParserError

from .models import IndexJob, JobRecord, Manifest, SourceBlock

MIGRATIONS = (
    """
    CREATE TABLE jobs(id TEXT PRIMARY KEY, status TEXT NOT NULL, created REAL NOT NULL,
                      data TEXT NOT NULL);
    CREATE TABLE idempotency(scope TEXT PRIMARY KEY, fingerprint TEXT NOT NULL,
                             job_id TEXT NOT NULL REFERENCES jobs(id), expires REAL NOT NULL);
    CREATE TABLE manifests(generation TEXT PRIMARY KEY, project TEXT NOT NULL,
                           document TEXT NOT NULL,
                           hash TEXT NOT NULL, active INTEGER NOT NULL, data TEXT NOT NULL);
    CREATE TABLE tombstones(project TEXT NOT NULL, document TEXT NOT NULL, hash TEXT NOT NULL,
                            PRIMARY KEY(project, document, hash));
    CREATE TABLE profiles(name TEXT PRIMARY KEY, fingerprint TEXT NOT NULL);
    CREATE INDEX pending_jobs ON jobs(status, created);
    """,
    """
    CREATE TABLE source_blocks(
      generation TEXT NOT NULL REFERENCES manifests(generation) ON DELETE CASCADE,
      ordinal INTEGER NOT NULL, locator TEXT NOT NULL, content TEXT NOT NULL,
      PRIMARY KEY (generation, ordinal), UNIQUE(generation, locator)
    );
    """,
)


def fingerprint(value: object) -> str:
    return hashlib.sha256(
        json.dumps(value, sort_keys=True, separators=(",", ":"), ensure_ascii=False).encode()
    ).hexdigest()


class JobStore:
    def __init__(
        self, directory: Path, queue_limit: int = 64, input_quota: int = 512 * 1024 * 1024
    ) -> None:
        self.directory = directory.resolve()
        self.directory.mkdir(mode=0o700, parents=True, exist_ok=True)
        self.lock = FileLock(str(self.directory / "instance.lock"))
        try:
            self.lock.acquire(timeout=0)
        except Timeout as exc:
            raise ParserError(503, "STATE_IN_USE", "State directory already has an owner") from exc
        self.guard = threading.RLock()
        self.queue_limit, self.input_quota = queue_limit, input_quota
        try:
            self.inputs = self.directory / "inputs"
            self.inputs.mkdir(mode=0o700, exist_ok=True)
            self.db = sqlite3.connect(
                self.directory / "jobs.sqlite3",
                isolation_level=None,
                check_same_thread=False,
                timeout=5,
            )
            self.db.execute("PRAGMA journal_mode=WAL")
            self.db.execute("PRAGMA foreign_keys=ON")
            self.db.execute("PRAGMA synchronous=FULL")
            version = int(self.db.execute("PRAGMA user_version").fetchone()[0])
            if version > len(MIGRATIONS):
                raise ValueError("State schema is newer than this application")
            for index in range(version, len(MIGRATIONS)):
                self.db.executescript(
                    "BEGIN IMMEDIATE;"
                    + MIGRATIONS[index]
                    + f"PRAGMA user_version={index + 1};COMMIT;"
                )
            self.recover()
        except Exception:
            if hasattr(self, "db"):
                self.db.close()
            self.lock.release()
            raise

    @contextmanager
    def transaction(self) -> Iterator[None]:
        with self.guard:
            self.db.execute("BEGIN IMMEDIATE")
            try:
                yield
                self.db.execute("COMMIT")
            except BaseException:
                self.db.execute("ROLLBACK")
                raise

    def close(self) -> None:
        self.db.execute("PRAGMA wal_checkpoint(TRUNCATE)")
        self.db.close()
        self.lock.release()

    def get(self, job_id: str) -> JobRecord:
        row = self.db.execute("SELECT data FROM jobs WHERE id=?", (job_id,)).fetchone()
        if row is None:
            raise ParserError(404, "JOB_NOT_FOUND", "Index task is not available")
        return JobRecord.model_validate_json(row[0])

    def save(self, record: JobRecord) -> None:
        self.db.execute(
            "UPDATE jobs SET status=?,data=? WHERE id=?",
            (record.public.status, record.model_dump_json(), record.public.id),
        )

    def pending(self) -> list[JobRecord]:
        rows = self.db.execute(
            "SELECT data FROM jobs WHERE status='QUEUED' ORDER BY created,id"
        ).fetchall()
        return [JobRecord.model_validate_json(row[0]) for row in rows]

    def input_path(self, record: JobRecord) -> Path:
        if (
            record.input_name != record.public.id
            or str(UUID(record.input_name)) != record.input_name
        ):
            raise ValueError("Invalid internal input identifier")
        return self.inputs / record.input_name

    def release_input(self, record: JobRecord) -> None:
        if record.input_name:
            self.input_path(record).unlink(missing_ok=True)
            record.input_name = None
            record.input_size = 0
        self.save(record)

    def recover(self) -> None:
        with self.transaction():
            records = [
                JobRecord.model_validate_json(row[0])
                for row in self.db.execute("SELECT data FROM jobs").fetchall()
            ]
            for record in records:
                if record.public.status == "PROCESSING":
                    record.public.status = "QUEUED"
                    self.save(record)
                if record.public.status not in {"QUEUED", "PROCESSING"}:
                    self.release_input(record)
            expected = {r.input_name for r in records if r.input_name}
            for path in self.inputs.iterdir():
                # Only application-owned UUID files are eligible for orphan cleanup.
                try:
                    canonical = str(UUID(path.name)) == path.name
                except ValueError:
                    canonical = False
                if canonical and path.is_file() and path.name not in expected:
                    path.unlink()

    def bind_profile(self, name: str, definition: str) -> None:
        row = self.db.execute("SELECT fingerprint FROM profiles WHERE name=?", (name,)).fetchone()
        if row and row[0] != definition:
            raise ParserError(503, "PROFILE_CHANGED", "An existing embedding profile was changed")
        self.db.execute("INSERT OR IGNORE INTO profiles VALUES(?,?)", (name, definition))

    def replay(self, scope: str, digest: str) -> JobRecord | None:
        row = self.db.execute(
            "SELECT fingerprint,job_id FROM idempotency WHERE scope=? AND expires>?",
            (scope, time.time()),
        ).fetchone()
        if row:
            if row[0] != digest:
                raise ParserError(
                    409, "IDEMPOTENCY_CONFLICT", "Key was used with different content"
                )
            return self.get(row[1])
        return None

    def profile_names(self) -> list[str]:
        return [row[0] for row in self.db.execute("SELECT name FROM profiles").fetchall()]

    def enqueue(
        self,
        *,
        scope: str,
        digest: str,
        operation_id: str,
        project: str,
        actor: str,
        document: str,
        content_hash: str,
        request_json: str,
        request_id: str,
        content: bytes | None,
    ) -> JobRecord:
        file_path: Path | None = None
        try:
            with self.transaction():
                replay = self.replay(scope, digest)
                if replay:
                    return replay
                pending = self.db.execute(
                    "SELECT data FROM jobs WHERE status IN ('QUEUED','PROCESSING')"
                ).fetchall()
                if len(pending) >= self.queue_limit:
                    raise ParserError(429, "QUEUE_FULL", "Index task queue is full")
                used = sum(
                    JobRecord.model_validate_json(row[0]).input_size
                    for row in self.db.execute("SELECT data FROM jobs").fetchall()
                )
                if used + len(content or b"") > self.input_quota:
                    raise ParserError(413, "INPUT_QUOTA", "Input staging quota is exhausted")
                kind: Literal["index", "delete"] = "index" if content is not None else "delete"
                if kind == "index" and self.deleted(project, document, content_hash):
                    raise ParserError(409, "VERSION_DELETED", "Document version is being deleted")
                job_id = str(uuid4())
                record = JobRecord(
                    public=IndexJob(
                        id=job_id,
                        operation_id=operation_id,
                        status="QUEUED",
                        stage="PARSING" if content is not None else "CLEANUP",
                        chunk_count=0,
                        error=None,
                    ),
                    project_id=project,
                    actor_id=actor,
                    document_id=document,
                    content_hash=content_hash,
                    kind=kind,
                    request_json=request_json,
                    request_id=request_id,
                    input_name=job_id if content is not None else None,
                    input_size=len(content or b""),
                    created_at=time.time(),
                )
                if content is not None:
                    file_path = self.inputs / job_id
                    descriptor = os.open(file_path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
                    with os.fdopen(descriptor, "wb") as output:
                        output.write(content)
                        output.flush()

                        os.fsync(output.fileno())
                else:
                    self.db.execute(
                        "INSERT OR IGNORE INTO tombstones VALUES(?,?,?)",
                        (project, document, content_hash),
                    )
                    self.db.execute(
                        "UPDATE manifests SET active=0 WHERE project=? AND document=? AND hash=?",
                        (project, document, content_hash),
                    )
                self.db.execute(
                    "INSERT INTO jobs VALUES(?,?,?,?)",
                    (job_id, record.public.status, record.created_at, record.model_dump_json()),
                )
                self.db.execute(
                    "INSERT OR REPLACE INTO idempotency VALUES(?,?,?,?)",
                    (scope, digest, job_id, time.time() + 86400),
                )
                return record
        except BaseException:
            if file_path:
                file_path.unlink(missing_ok=True)
            raise

    def deleted(self, project: str, document: str, content_hash: str) -> bool:
        return (
            self.db.execute(
                "SELECT 1 FROM tombstones WHERE project=? AND document=? AND hash=?",
                (project, document, content_hash),
            ).fetchone()
            is not None
        )

    def manifests(
        self,
        project: str,
        document: str | None = None,
        content_hash: str | None = None,
        *,
        active: bool = False,
    ) -> list[Manifest]:
        sql = "SELECT data,active FROM manifests WHERE project=?"
        params: list[object] = [project]
        for column, value in (("document", document), ("hash", content_hash)):
            if value is not None:
                sql += f" AND {column}=?"
                params.append(value)
        if active:
            sql += " AND active=1"
        result: list[Manifest] = []
        for row in self.db.execute(sql, params).fetchall():
            manifest = Manifest.model_validate_json(row[0])
            manifest.active = bool(row[1])
            result.append(manifest)
        return result

    def put_manifest(self, manifest: Manifest) -> None:
        self.db.execute(
            "INSERT INTO manifests VALUES(?,?,?,?,?,?) ON CONFLICT(generation) "
            "DO UPDATE SET active=excluded.active,data=excluded.data",
            (
                manifest.generation,
                manifest.project_id,
                manifest.document_id,
                manifest.content_hash,
                int(manifest.active),
                manifest.model_dump_json(),
            ),
        )

    def save_source_blocks(self, generation: str, blocks: list[SourceBlock]) -> None:
        self.db.execute("DELETE FROM source_blocks WHERE generation=?", (generation,))
        self.db.executemany(
            "INSERT INTO source_blocks (generation,ordinal,locator,content) VALUES (?,?,?,?)",
            ((generation, i, block.locator, block.text) for i, block in enumerate(blocks)),
        )

    def source_blocks(self, generation: str, offset: int, limit: int) -> list[SourceBlock]:
        rows = self.db.execute(
            """
            SELECT locator,content FROM source_blocks WHERE generation=? AND ordinal>=?
            ORDER BY ordinal LIMIT ?
            """,
            (generation, offset, limit),
        )
        return [SourceBlock(locator=locator, text=content) for locator, content in rows]

    def forget_manifest(self, generation: str) -> None:
        self.db.execute("DELETE FROM manifests WHERE generation=?", (generation,))
