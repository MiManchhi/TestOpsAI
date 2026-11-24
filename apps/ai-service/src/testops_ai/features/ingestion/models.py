from typing import Annotated, Literal

from pydantic import Field, field_validator

from testops_ai.features.robot_parser.models import (
    ContractModel,
    Id,
    ObjectRef,
    Sha256,
    Uuid,
    check_uuid,
)

PARSER_VERSION = "knowledge-v1"
WORKSPACE_PARSER_VERSION = "knowledge-v2"
Status = Literal["QUEUED", "PROCESSING", "SUCCEEDED", "FAILED", "CANCELLED"]
Stage = Literal["PARSING", "CHUNKING", "EMBEDDING", "CLEANUP", "FINISHED"]


class IndexRequest(ContractModel):
    operation_id: Uuid
    document_id: Id
    knowledge_base_id: Id
    content_hash: Sha256
    object: ObjectRef
    parser_version: Annotated[str, Field(max_length=64)]
    embedding_profile: Annotated[str, Field(max_length=64)]

    _uuid = field_validator("operation_id")(check_uuid)


class IndexDelete(ContractModel):
    operation_id: Uuid
    document_id: Id
    content_hash: Sha256

    _uuid = field_validator("operation_id")(check_uuid)


class JobError(ContractModel):
    code: str
    message: str
    request_id: str


class IndexJob(ContractModel):
    id: Uuid
    operation_id: Uuid
    status: Status
    stage: Stage
    chunk_count: Annotated[int, Field(ge=0)]
    error: JobError | None


class Chunk(ContractModel):
    """Private index representation, not a public Citation or DocumentContent DTO."""

    id: Uuid
    text: Annotated[str, Field(min_length=1, max_length=1000)]
    heading: Annotated[str, Field(max_length=256)]
    page: Annotated[int, Field(ge=1)] | None
    start_line: Annotated[int, Field(ge=1)] | None
    end_line: Annotated[int, Field(ge=1)] | None
    offset: Annotated[int, Field(ge=0)]
    paragraph: Annotated[int, Field(ge=1)] | None = None

    _uuid = field_validator("id")(check_uuid)


class JobRecord(ContractModel):
    public: IndexJob
    project_id: str
    actor_id: str
    document_id: str
    content_hash: str
    kind: Literal["index", "delete"]
    request_json: str
    request_id: str
    input_name: str | None
    input_size: int
    created_at: float


class Manifest(ContractModel):
    project_id: str
    knowledge_base_id: str
    document_id: str
    content_hash: str
    parser_version: str
    embedding_profile: str
    generation: str
    collection: str
    chunk_count: int = 0
    active: bool = False


class KnowledgeSearchTool(ContractModel):
    query: Annotated[str, Field(max_length=2000)]
    knowledge_base_ids: Annotated[list[Id], Field(min_length=1, max_length=5)]
    document_ids: Annotated[list[Id], Field(max_length=20)]
    limit: Annotated[int, Field(ge=1, le=10)]

    @field_validator("knowledge_base_ids", "document_ids")
    @classmethod
    def unique_ids(cls, value: list[str]) -> list[str]:
        if len(value) != len(set(value)):
            raise ValueError("Duplicate scope identifiers")
        return value


class ReadyVersion(ContractModel):
    """Trusted caller supplies verified READY versions, never request/model input."""

    project_id: Id
    knowledge_base_id: Id
    document_id: Id
    content_hash: Sha256


class Candidate(ContractModel):
    document_id: str
    content_hash: str
    knowledge_base_id: str
    chunk: Chunk
    score: Annotated[float, Field(allow_inf_nan=False)]


class SourceBlock(ContractModel):
    locator: Annotated[str, Field(min_length=1, max_length=96)]
    text: Annotated[str, Field(min_length=1, max_length=4000)]


class ExtractedSource(ContractModel):
    chunks: list[Chunk]
    blocks: list[SourceBlock]


class IndexBlockPage(ContractModel):
    job_id: Uuid
    version_id: Id
    content_hash: Sha256
    parser_version: str
    blocks: list[SourceBlock]
    next_cursor: str | None
