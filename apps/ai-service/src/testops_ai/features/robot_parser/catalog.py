"""Read Robot ASTs only. Never construct an executable suite or resolve libraries."""

import ast
import hashlib
from dataclasses import dataclass
from fnmatch import fnmatchcase
from io import StringIO
from pathlib import PurePosixPath

from robot.api.parsing import get_init_model, get_model, get_resource_model

# Robot 7 exposes this AST API but does not ship a py.typed marker for its model modules.
from robot.parsing.model.blocks import TestCase  # type: ignore[import-untyped]
from robot.parsing.model.statements import Documentation, Statement  # type: ignore[import-untyped]

from .errors import ParserError, invalid_content
from .models import (
    ImportIssue,
    ObjectRef,
    ParsedCase,
    ParsedSuite,
    RobotParseResult,
    SourceRef,
    Step,
)


@dataclass(frozen=True)
class SourceFile:
    reference: ObjectRef
    content: bytes


def issue(code: str, path: str, line: int | None, message: str) -> ImportIssue:
    return ImportIssue(code=code, source_path=path, line=line, message=message)


def dynamic(value: str) -> bool:
    return any(marker in value for marker in ("${", "@{", "&{", "%{"))


def parse_catalog(files: list[SourceFile]) -> RobotParseResult:
    try:
        return _parse_catalog(files)
    except (ValueError, RecursionError) as exc:
        raise invalid_content(
            "Robot content exceeds contract limits or has invalid encoding"
        ) from exc


def _parse_catalog(files: list[SourceFile]) -> RobotParseResult:
    if not 1 <= len(files) <= 200:
        raise ParserError(413, "CONTENT_TOO_LARGE", "Too many source files")
    if sum(len(f.content) for f in files) > 20 * 1024 * 1024:
        raise ParserError(413, "CONTENT_TOO_LARGE", "Catalog exceeds 20 MiB")
    paths = {f.reference.logical_path for f in files}
    if len(paths) != len(files):
        raise invalid_content("Duplicate source paths")
    suites: dict[str, ParsedSuite] = {}
    cases: list[ParsedCase] = []
    issues: list[ImportIssue] = []
    inherited_tags: dict[str, list[str]] = {}
    for source in sorted(
        files,
        key=lambda f: (
            PurePosixPath(f.reference.logical_path).name != "__init__.robot",
            f.reference.logical_path,
        ),
    ):
        path = source.reference.logical_path
        suffix = PurePosixPath(path).suffix
        if suffix not in {".robot", ".resource"}:
            raise ParserError(415, "UNSUPPORTED_FORMAT", "Expected .robot or .resource")
        if len(source.content) > 2 * 1024 * 1024:
            raise ParserError(413, "CONTENT_TOO_LARGE", "Robot source exceeds 2 MiB")
        if (
            len(source.content) != source.reference.size_bytes
            or hashlib.sha256(source.content).hexdigest() != source.reference.sha256
        ):
            raise invalid_content("Source size or digest mismatch")
        text = source.content.decode("utf-8-sig")
        parser = get_resource_model if suffix == ".resource" else get_model
        if PurePosixPath(path).name == "__init__.robot":
            parser = get_init_model
        model = parser(StringIO(text))
        model.validate_model()
        for node in ast.walk(model):
            if getattr(node, "errors", ()) or type(node).__name__ == "InvalidSection":
                raise invalid_content("Robot syntax error")
            if isinstance(node, Statement):
                kind = type(node).__name__
                arguments = node.get_values("ARGUMENT")
                if kind == "ResourceImport":
                    target = node.get_value("NAME", "")
                    if dynamic(target):
                        issues.append(
                            issue(
                                "DYNAMIC_RESOURCE",
                                path,
                                node.lineno,
                                "Resource reference cannot be resolved statically",
                            )
                        )
                    else:
                        ObjectRef.safe_path(target)
                        resolved = str(PurePosixPath(path).parent / target)
                        if resolved not in paths:
                            raise invalid_content("Resource is outside the uploaded snapshot")
                elif kind in {"LibraryImport", "VariablesImport"}:
                    issues.append(
                        issue(
                            "IMPORT_NOT_EXECUTED",
                            path,
                            node.lineno,
                            "Library and variable imports are not executed",
                        )
                    )
                elif any(dynamic(v) for v in arguments):
                    issues.append(
                        issue(
                            "DYNAMIC_VALUE",
                            path,
                            node.lineno,
                            "Variable expressions are preserved without evaluation",
                        )
                    )
        # Resource files contain reusable definitions, not test suites/cases.
        if suffix == ".resource":
            continue
        suite_path = str(PurePosixPath(path).parent) if path.endswith("/__init__.robot") else path
        if path == "__init__.robot":
            suite_path = "."
        parents = list(PurePosixPath(suite_path).parents)
        for parent in reversed(parents):
            parent_path = str(parent)
            if parent_path != ".":
                grandparent = str(parent.parent)
                suites.setdefault(
                    parent_path,
                    ParsedSuite(
                        path=parent_path,
                        name=parent.name,
                        parent_path=None if grandparent == "." else grandparent,
                    ),
                )
        suite_parent = str(PurePosixPath(suite_path).parent)
        suites[suite_path] = ParsedSuite(
            path=suite_path,
            name=PurePosixPath(suite_path).stem or "root",
            parent_path=None if suite_parent == "." else suite_parent,
        )
        defaults: list[str] = []
        forced: list[str] = []
        for section in model.sections:
            if type(section).__name__ == "SettingSection":
                for statement in section.body:
                    if isinstance(statement, Statement):
                        kind = type(statement).__name__
                        if kind in {"TestTags", "ForceTags"}:
                            forced.extend(statement.get_values("ARGUMENT"))
                        elif kind == "DefaultTags":
                            defaults.extend(statement.get_values("ARGUMENT"))
        if PurePosixPath(path).name == "__init__.robot":
            inherited_tags[suite_path] = forced
            continue
        ancestors = [str(p) for p in reversed(PurePosixPath(path).parents)]
        forced = [
            tag for ancestor in ancestors for tag in inherited_tags.get(ancestor, [])
        ] + forced
        seen: set[str] = set()
        for node in ast.walk(model):
            if not isinstance(node, TestCase):
                continue
            if node.name in seen:
                raise invalid_content("Duplicate case name within a suite")
            seen.add(node.name)
            tags: list[str] | None = None
            doc = ""
            steps: list[Step] = []
            for statement in node.body:
                kind = type(statement).__name__
                if isinstance(statement, Documentation):
                    doc = statement.value
                elif isinstance(statement, Statement) and kind == "Tags":
                    tags = list(statement.get_values("ARGUMENT"))
                elif isinstance(statement, Statement) and kind == "KeywordCall":
                    steps.append(
                        Step(
                            keyword=statement.get_value("KEYWORD", ""),
                            arguments=list(statement.get_values("ARGUMENT")),
                            line=statement.lineno,
                        )
                    )
                elif kind not in {"EmptyLine", "Comment"}:
                    issues.append(
                        issue(
                            "STATIC_STRUCTURE",
                            path,
                            statement.lineno,
                            "Control flow or fixture is not expanded into executable steps",
                        )
                    )
            raw_tags = forced + (defaults if tags is None else tags)
            static_tags = list(
                dict.fromkeys(t.strip() for t in raw_tags if t.strip() and not dynamic(t))
            )
            removals = [tag[1:] for tag in static_tags if tag.startswith("-")]
            static_tags = [
                tag
                for tag in static_tags
                if not tag.startswith("-")
                and not any(fnmatchcase(tag, pattern) for pattern in removals)
            ]
            cases.append(
                ParsedCase(
                    suite_path=suite_path,
                    name=node.name,
                    documentation=doc,
                    tags=static_tags,
                    source=SourceRef(
                        path=path, line=node.header.lineno, revision=source.reference.sha256
                    ),
                    steps=steps,
                )
            )
    return RobotParseResult(suites=list(suites.values()), cases=cases, issues=issues)
