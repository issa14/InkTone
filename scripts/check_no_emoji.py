#!/usr/bin/env python3
"""Échoue si un emoji décoratif apparaît dans app/src/main/**/*.kt.

Évite la réintroduction d'emojis dans le code applicatif (voir
PLAN_ICONES_INKTONE.md, étape 6) — les zones de test/debug ne sont pas
concernées, tout comme les commentaires contenant des flèches typographiques.
"""
import re
import sys
from pathlib import Path

EMOJI_PATTERN = re.compile(
    "[\U0001F300-\U0001FAFF\U00002600-\U000027BF\U00002B00-\U00002BFF\U0000FE0F]"
)

ROOT = Path(__file__).resolve().parent.parent
TARGET_DIR = ROOT / "app" / "src" / "main"


def main() -> int:
    violations = []
    for path in TARGET_DIR.rglob("*.kt"):
        text = path.read_text(encoding="utf-8")
        for lineno, line in enumerate(text.splitlines(), start=1):
            if EMOJI_PATTERN.search(line):
                violations.append(f"{path.relative_to(ROOT)}:{lineno}: {line.strip()}")

    if violations:
        print("Emoji détecté dans app/src/main — utiliser Icon() / AppIcons à la place :\n")
        print("\n".join(violations))
        return 1

    print("OK — aucun emoji dans app/src/main/**/*.kt")
    return 0


if __name__ == "__main__":
    sys.exit(main())
