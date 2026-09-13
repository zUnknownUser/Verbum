"""One explicit command per offline stage; no automatic approval or publication."""

import argparse
import json
import os
from pathlib import Path

import psycopg
from pydantic import ValidationError

from . import extract, normalize, publish, review, scripture
from .files import digest, read, write
from .models import Bundle, Provenance, Source


def main(argv=None) -> int:
    parser = argparse.ArgumentParser(prog="verbum-pipeline")
    commands = parser.add_subparsers(dest="command", required=True)
    for command in ("import", "normalize"):
        sub = commands.add_parser(command)
        sub.add_argument("source", type=Path)
        sub.add_argument("--output", type=Path, required=True)
        if command == "import":
            sub.add_argument("--fixtures", action="store_true")
    sub = commands.add_parser("review")
    sub.add_argument("source", type=Path)
    sub.add_argument("--queue", type=Path, required=True)
    sub.add_argument("--decisions", type=Path, required=True)
    sub = commands.add_parser("extract")
    sub.add_argument("text", type=Path)
    sub.add_argument("--source", type=Path, required=True, help="JSON {source, provenance}")
    sub.add_argument("--output", type=Path, required=True)
    sub.add_argument(
        "--model", default=os.environ.get("VERBUM_OPENAI_MODEL", extract.DEFAULT_MODEL)
    )
    sub = commands.add_parser("embed-scripture")
    sub.add_argument("tsv", type=Path)
    sub.add_argument("--translation", default="WEB")
    sub.add_argument(
        "--model",
        default=os.environ.get("VERBUM_OPENAI_EMBEDDING_MODEL", scripture.DEFAULT_MODEL),
    )
    sub.add_argument("--dimensions", type=int, default=scripture.DEFAULT_DIMENSIONS)
    sub.add_argument("--batch-size", type=int, default=200)
    sub.add_argument("--limit", type=int, default=None, help="embed only the first N verses")
    for command in ("validate", "publish"):
        sub = commands.add_parser(command)
        sub.add_argument("source", type=Path)
        sub.add_argument("--decisions", type=Path, required=command == "publish")
        if command == "publish":
            sub.add_argument("--allow-fixtures", action="store_true")
    args = parser.parse_args(argv)
    try:
        if args.command == "import":
            normalize.import_source(args.source, args.output, fixtures=args.fixtures)
        elif args.command == "normalize":
            normalize.normalize(args.source, args.output)
        elif args.command == "review":
            review.prepare(args.source, args.queue, args.decisions)
        elif args.command == "extract":
            payload = read(args.source)
            bundle = extract.propose(
                args.text.read_text(encoding="utf-8"),
                Source.model_validate(payload["source"]),
                Provenance.model_validate(payload["provenance"]),
                client=extract.default_client(),
                model=args.model,
            )
            write(args.output, bundle.model_dump())
        elif args.command == "embed-scripture":
            url = os.environ.get("VERBUM_DATABASE_URL")
            if not url:
                raise ValueError("VERBUM_DATABASE_URL is required")
            print(
                json.dumps(
                    scripture.load(
                        args.tsv,
                        url,
                        embedder=scripture.default_embedder(),
                        model=args.model,
                        dimensions=args.dimensions,
                        translation=args.translation,
                        batch_size=args.batch_size,
                        limit=args.limit,
                    )
                )
            )
        elif args.command == "validate":
            bundle = Bundle.model_validate(read(args.source))
            if args.decisions:
                review.require_approval(bundle, args.decisions)
            print(
                json.dumps(
                    {
                        "kind": bundle.kind,
                        "bundleHash": digest(bundle.model_dump()),
                        "counts": {k: len(v) for k, v in bundle.content.model_dump().items()},
                        "approved": args.decisions is not None,
                    }
                )
            )
        elif args.command == "publish":
            url = os.environ.get("VERBUM_DATABASE_URL")
            if not url:
                raise ValueError("VERBUM_DATABASE_URL is required")
            print(
                json.dumps(
                    publish.publish(
                        args.source, args.decisions, url, allow_fixtures=args.allow_fixtures
                    )
                )
            )
    except ValidationError as exc:
        # Do not print source bodies or input_value fields in logs.
        parser.exit(1, f"Invalid content: {exc.error_count()} validation error(s)\n")
    except psycopg.Error:
        parser.exit(
            1, "Database operation failed; transaction rolled back. Check connection/schema.\n"
        )
    except (ValueError, OSError) as exc:
        parser.exit(1, f"{exc}\n")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
