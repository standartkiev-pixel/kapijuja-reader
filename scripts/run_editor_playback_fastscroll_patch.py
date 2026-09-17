#!/usr/bin/env python3
from pathlib import Path

# The guarded patch intentionally uses exact source matches. One historical
# source line differs only by a space before `).trim()`. Normalize the patch
# program itself in memory, then execute it; this keeps all other guards strict.
patch_path = Path("scripts/apply_editor_playback_fastscroll_patch.py")
program = patch_path.read_text(encoding="utf-8")
program = program.replace(
    'segments[i].spoken.replace(Regex(\\\"\\\\\\\\s+\\\"), \\\" \\\" ).trim()',
    'segments[i].spoken.replace(Regex(\\\"\\\\\\\\s+\\\"), \\\" \\\").trim()'
)
program = program.replace(
    'raw.replace(Regex(\\\"\\\\\\\\s+\\\"), \\\" \\\" ).trim()',
    'raw.replace(Regex(\\\"\\\\\\\\s+\\\"), \\\" \\\").trim()'
)
exec(compile(program, str(patch_path), "exec"), {"__name__": "__main__"})
