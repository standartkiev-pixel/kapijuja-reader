#!/usr/bin/env python3
from __future__ import annotations

from pathlib import Path
import sys

ROOT = Path("app/src/main/java")
SOFT_LINES = 500
HARD_LINES = 800
HARD_BYTES = 60_000

# Temporary migration baselines for already-oversized legacy files.
# Do not increase these numbers. Reduce or remove an entry after refactoring.
LEGACY_MAX_BYTES = {
    "app/src/main/java/com/kapijuja/reader/ReaderActivity.kt": 105_730,
    "app/src/main/java/com/kapijuja/reader/SettingsActivity.kt": 47_506,
}


def count_lines(path: Path) -> int:
    with path.open("r", encoding="utf-8", errors="replace") as handle:
        return sum(1 for _ in handle)


def main() -> int:
    if not ROOT.exists():
        print(f"Architecture check skipped: {ROOT} does not exist")
        return 0

    failures: list[str] = []
    warnings: list[str] = []

    for path in sorted(ROOT.rglob("*")):
        if not path.is_file() or path.suffix not in {".kt", ".java"}:
            continue

        rel = path.as_posix()
        size = path.stat().st_size
        lines = count_lines(path)

        legacy_limit = LEGACY_MAX_BYTES.get(rel)
        if legacy_limit is not None:
            if size > legacy_limit:
                failures.append(
                    f"{rel}: legacy oversized file grew to {size} bytes "
                    f"(baseline {legacy_limit}). Extract logic instead of growing it."
                )
            else:
                warnings.append(
                    f"{rel}: legacy migration target, {lines} lines / {size} bytes. "
                    "Keep shrinking; do not increase its baseline."
                )
            continue

        if lines > HARD_LINES or size > HARD_BYTES:
            failures.append(
                f"{rel}: {lines} lines / {size} bytes exceeds normal hard limit "
                f"({HARD_LINES} lines or {HARD_BYTES} bytes). Split by responsibility."
            )
        elif lines > SOFT_LINES:
            warnings.append(
                f"{rel}: {lines} lines / {size} bytes exceeds soft target "
                f"({SOFT_LINES} lines). Review whether a cohesive extraction is due."
            )

    print("Kapijuja Reader source-size architecture check")
    for warning in warnings:
        print(f"WARNING: {warning}")

    if failures:
        for failure in failures:
            print(f"ERROR: {failure}")
        print(f"Architecture check failed with {len(failures)} violation(s).")
        return 1

    print("Architecture check passed.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
