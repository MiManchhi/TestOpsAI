import asyncio
import multiprocessing

import pytest
from conftest import source

from testops_ai.features.robot_parser.errors import ParserError
from testops_ai.features.robot_parser.worker import ParseWorker


def test_timeout_reaps_process_and_releases_slot():
    async def run():
        before = {p.pid for p in multiprocessing.active_children()}
        worker = ParseWorker(1, 0.0001)
        with pytest.raises(ParserError) as error:
            await worker.parse("robot", [source()])
        assert error.value.code == "PARSER_TIMEOUT"
        assert {p.pid for p in multiprocessing.active_children()} == before
        worker.timeout_seconds = 10
        result = await worker.parse("robot", [source()])
        assert result.cases[0].name == "Example"

    asyncio.run(run())


def test_cancellation_reaps_process():
    async def run():
        before = {p.pid for p in multiprocessing.active_children()}
        worker = ParseWorker(1, 10)
        task = asyncio.create_task(worker.parse("robot", [source()]))
        await asyncio.sleep(0.02)
        task.cancel()
        with pytest.raises(asyncio.CancelledError):
            await task
        assert {p.pid for p in multiprocessing.active_children()} == before

    asyncio.run(run())
