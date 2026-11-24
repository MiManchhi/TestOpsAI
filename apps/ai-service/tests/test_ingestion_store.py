from uuid import uuid4

import pytest

from testops_ai.features.ingestion.store import JobStore
from testops_ai.features.robot_parser.errors import ParserError


def enqueue(store, key="one", content=b"self-made"):
    return store.enqueue(
        scope=key,
        digest="digest",
        operation_id=str(uuid4()),
        project="1",
        actor="2",
        document="10",
        content_hash="a" * 64,
        request_json="{}",
        request_id=str(uuid4()),
        content=content,
    )


def test_exclusive_state_lock_and_migration(tmp_path):
    store = JobStore(tmp_path)
    assert store.db.execute("PRAGMA user_version").fetchone()[0] == 2
    with pytest.raises(ParserError):
        JobStore(tmp_path)
    store.close()
    JobStore(tmp_path).close()


def test_replay_conflict_and_quota(tmp_path):
    store = JobStore(tmp_path, queue_limit=1, input_quota=12)
    try:
        job = enqueue(store)
        assert enqueue(store).public.id == job.public.id
        with pytest.raises(ParserError) as error:
            store.replay("one", "changed")
        assert error.value.status == 409
        with pytest.raises(ParserError) as error:
            enqueue(store, "two")
        assert error.value.status == 429
    finally:
        store.close()
    quota = JobStore(tmp_path / "small", input_quota=1)
    try:
        with pytest.raises(ParserError) as error:
            enqueue(quota)
        assert error.value.status == 413
        assert not list(quota.inputs.iterdir())
    finally:
        quota.close()


def test_restart_recovers_processing_and_removes_only_owned_orphans(tmp_path):
    store = JobStore(tmp_path)
    record = enqueue(store)
    record.public.status = "PROCESSING"
    store.save(record)
    orphan = store.inputs / str(uuid4())
    orphan.write_bytes(b"orphan")
    untouched = store.inputs / "operator-note.txt"
    untouched.write_text("keep")
    store.close()
    restored = JobStore(tmp_path)
    try:
        assert restored.get(record.public.id).public.status == "QUEUED"
        assert restored.input_path(restored.get(record.public.id)).read_bytes() == b"self-made"
        assert not orphan.exists() and untouched.exists()
    finally:
        restored.close()


def test_delete_tombstone_prevents_resurrection(tmp_path):
    store = JobStore(tmp_path)
    try:
        enqueue(store, "delete", content=None)
        assert store.deleted("1", "10", "a" * 64)
        with pytest.raises(ParserError) as error:
            enqueue(store, "new-index")
        assert error.value.code == "VERSION_DELETED"
    finally:
        store.close()
