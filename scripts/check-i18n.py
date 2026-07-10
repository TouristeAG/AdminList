#!/usr/bin/env python3
"""Verify that every locale strings.xml has the same keys as the English reference.

Exits with code 1 if any locale is missing keys (or has extras optional warn).
"""
from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parents[1]

SETS = [
    {
        "name": "Compose Resources",
        "base": ROOT / "shared/src/commonMain/composeResources",
        "folders": {
            "en": "values",
            "fr": "values-fr",
            "es": "values-es",
            "zh-TW": "values-zh-rTW",
            "zh-CN": "values-zh-rCN",
            "la": "values-la",
            "hi": "values-hi",
        },
    },
    {
        "name": "Android Resources",
        "base": ROOT / "app/src/main/res",
        "folders": {
            "en": "values",
            "fr": "values-fr",
            "es": "values-es",
            "zh-TW": "values-zh-rTW",
            "zh-CN": "values-zh-rCN",
            "la": "values-la",
            "hi": "values-hi",
        },
    },
]

STRING_RE = re.compile(r'<string\s+name="([^"]+)"')


def load_keys(path: Path) -> set[str]:
    if not path.exists():
        raise FileNotFoundError(path)
    return set(STRING_RE.findall(path.read_text(encoding="utf-8")))


def check_set(entry: dict, warn_extra: bool) -> list[str]:
    errors: list[str] = []
    base: Path = entry["base"]
    folders: dict[str, str] = entry["folders"]
    en_path = base / folders["en"] / "strings.xml"
    en_keys = load_keys(en_path)
    print(f"\n[{entry['name']}] EN keys: {len(en_keys)} ({en_path.relative_to(ROOT)})")

    for lang, folder in folders.items():
        if lang == "en":
            continue
        path = base / folder / "strings.xml"
        keys = load_keys(path)
        missing = sorted(en_keys - keys)
        extra = sorted(keys - en_keys)
        status = "OK" if not missing else f"MISSING {len(missing)}"
        print(f"  {lang:6} {len(keys):5} keys  {status}")
        if missing:
            errors.append(f"{entry['name']} / {lang}: missing {len(missing)} keys")
            for key in missing[:40]:
                print(f"    - {key}")
            if len(missing) > 40:
                print(f"    … and {len(missing) - 40} more")
        if warn_extra and extra:
            print(f"    (warning: {len(extra)} extra keys not in EN)")
    return errors


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--warn-extra",
        action="store_true",
        help="Warn about keys present in a locale but not in English",
    )
    args = parser.parse_args()

    all_errors: list[str] = []
    for entry in SETS:
        all_errors.extend(check_set(entry, warn_extra=args.warn_extra))

    print()
    if all_errors:
        print("i18n check FAILED:")
        for err in all_errors:
            print(f"  • {err}")
        return 1

    print("i18n check PASSED: all locales match English key sets.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
