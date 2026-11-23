"""Command line interface: ``python -m sonnenregen.cli [CITY]``."""

from __future__ import annotations

import argparse
import json

from .core import DEFAULT_CITY, __version__, analyze


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--version", action="version", version=f"%(prog)s {__version__}")
    parser.add_argument("city", nargs="?", default=DEFAULT_CITY, help="WetterOnline city slug")
    parser.add_argument("--human", action="store_true", help="print one line per question")
    args = parser.parse_args(argv)

    answer = analyze(args.city)
    if args.human:
        print(answer.text())
    else:
        print(json.dumps(answer.as_dict(), ensure_ascii=False, indent=2))
    return 0


if __name__ == "__main__":  # pragma: no cover
    raise SystemExit(main())
