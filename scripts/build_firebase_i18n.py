#!/usr/bin/env python3
"""Validate Firebase i18n JSON files against the English reference."""

from __future__ import annotations

import json
import sys
from pathlib import Path

SCRIPTS = Path(__file__).resolve().parent
ROOT = SCRIPTS.parent
EN_STRINGS = (
    ROOT / "shared/src/commonMain/composeResources/values/strings.xml"
)

LOCALE_FILES = {
    "es": SCRIPTS / "firebase_i18n_es.json",
    "hi": SCRIPTS / "firebase_i18n_hi.json",
    "la": SCRIPTS / "firebase_i18n_la.json",
    "zh_cn": SCRIPTS / "firebase_i18n_zh_cn.json",
    "zh_tw": SCRIPTS / "firebase_i18n_zh_tw.json",
}


def load_json(path: Path) -> dict[str, str]:
    with path.open(encoding="utf-8") as handle:
        return json.load(handle)


def load_en_reference() -> dict[str, str]:
    import re

    text = EN_STRINGS.read_text(encoding="utf-8")
    pattern = re.compile(r'<string name="([^"]+)">(.*?)</string>', re.DOTALL)
    prefix = (
        "firebase_",
        "sheets_mirror_",
        "sheets_migrate",
        "migration_",
        "follow_",
        "backend_mismatch",
        "setup_choose_backend",
        "setup_backend",
        "setup_firebase",
    )
    out: dict[str, str] = {}
    for match in pattern.finditer(text):
        name = match.group(1)
        if not name.startswith(prefix):
            continue
        value = match.group(2)
        value = (
            value.replace("\\'", "'")
            .replace("\\n", "\n")
            .replace("&amp;", "&")
            .replace("&lt;", "<")
            .replace("&gt;", ">")
            .replace("&quot;", '"')
        )
        out[name] = value
    return out


def validate() -> int:
    en = load_en_reference()
    en_keys = set(en)
    expected_count = len(en_keys)
    errors: list[str] = []

    if expected_count != 184:
        errors.append(f"EN reference has {expected_count} keys, expected 184")

    for locale, path in LOCALE_FILES.items():
        if not path.is_file():
            errors.append(f"{locale}: missing file {path.name}")
            continue

        data = load_json(path)
        data_keys = set(data)

        if len(data_keys) != expected_count:
            errors.append(
                f"{locale}: expected {expected_count} keys, found {len(data_keys)}"
            )

        missing = en_keys - data_keys
        extra = data_keys - en_keys
        if missing:
            errors.append(f"{locale}: missing keys: {sorted(missing)}")
        if extra:
            errors.append(f"{locale}: extra keys: {sorted(extra)}")

        for key in en_keys & data_keys:
            value = data[key]
            if not isinstance(value, str):
                errors.append(f"{locale}:{key}: value is not a string")
                continue
            if not value.strip():
                errors.append(f"{locale}:{key}: empty translation")
            if value == en[key]:
                errors.append(f"{locale}:{key}: identical to English (likely untranslated)")

    if errors:
        print("Firebase i18n validation FAILED:", file=sys.stderr)
        for err in errors:
            print(f"  - {err}", file=sys.stderr)
        return 1

    print(
        f"OK: all {len(LOCALE_FILES)} locale files contain exactly "
        f"{expected_count} keys matching {EN_STRINGS.name}"
    )
    return 0


def main() -> None:
    raise SystemExit(validate())


if __name__ == "__main__":
    main()
