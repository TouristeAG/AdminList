#!/usr/bin/env python3
"""Regenerate Latin (full) and fix broken Hindi strings using segment-safe translation."""
from __future__ import annotations

import hashlib
import json
import re
import time
from pathlib import Path

from deep_translator import GoogleTranslator

ROOT = Path(__file__).resolve().parents[1]
CACHE_PATH = ROOT / ".i18n_segment_cache.json"

STRING_RE = re.compile(
    r'(?P<pre>\s*)<string\s+name="(?P<name>[^"]+)"(?P<attrs>[^>]*)>(?P<body>.*?)</string>',
    re.DOTALL,
)

PH_RE = re.compile(
    r'(%\d+\$[sdif]|%[sdif]|\\n|\\u[0-9a-fA-F]{4}|&amp;|&lt;|&gt;|&#\d+;|\\\'|\\")'
)

CORRUPT_RE = re.compile(r'⟦|⟧|<\d+>|&lt;\d+&gt;|__PH')

KEEP_AS_IS = {
    "app_name", "app_version",
    "language_english", "language_french", "language_spanish",
    "language_chinese", "language_chinese_simplified",
    "language_latin", "language_hindi",
    "venue_groove", "venue_le_terreau",
    "uid_label", "nfc_uid_label", "pos_title",
    "email", "total", "notes", "actions", "admin_mode", "admin_label",
    "nav_shifts", "shifts_title", "description_label", "qr_code",
    "resolution_normal", "sales_category_bar", "sales_category_merch",
    "sales_emoji_label", "temp_guest_chip_label",
    "easter_egg_hextris", "easter_egg_scroll", "easter_egg_pizza_undelivery",
    "easter_egg_wendol_village", "easter_egg_catculus",
    "arcade_credit_hextris", "arcade_credit_scroll", "arcade_credit_pizza_undelivery",
    "arcade_credit_wendol_village", "arcade_credit_catculus",
    "ble_reader_row_subtitle", "email_placeholder", "guest_email_placeholder",
    "setup_spreadsheet_title", "date_format_dd_mm_yyyy", "date_format_mm_dd_yyyy",
    "date_of_birth_placeholder", "time_format_hh_mm", "time_format_hh_mm_ss",
    "time_format_hh_mm_ss_sss", "date_format_yyyy_mm_dd",
    "benefits_help_section_profité_title", "benefits_help_section_orion_title",
    "benefits_help_section_galaxie_title", "benefits_help_section_veteran_title",
    "benefits_help_dialog_subtitle", "email_association_name_hint", "email_signature_default",
    "date_change_offset_time", "minutes_short",
}

TECH_TOKENS = {
    "POS", "NFC", "QR", "UID", "ID", "OK", "CHF", "EUR", "USD", "GBP", "API", "JSON",
    "Email", "Temp", "BLE", "PC/SC", "NanoID", "GitHub", "Gmail", "Google", "Android",
    "Windows", "macOS", "Linux", "USB", "Bluetooth", "ACS", "ACR1255U-J1", "ACR122U",
}

# Hindi strings to always retranslate (truncated / corrupted in prior MT pass)
HI_FORCE_KEYS = {
    "biometric_warning_message",
    "benefits_help_section_profité_body",
    "sync_error_device_time_solution",
    "guest_email",
    "volunteer_qr_code",
}

HI_MANUAL = {
    "guest_email": "ईमेल",
    "volunteer_qr_code": "स्वयंसेवक QR कोड",
    "guest_phone_placeholder": "+91 98765 43210",
}


def load_cache() -> dict[str, str]:
    if CACHE_PATH.exists():
        return json.loads(CACHE_PATH.read_text(encoding="utf-8"))
    return {}


def save_cache(cache: dict[str, str]) -> None:
    CACHE_PATH.write_text(json.dumps(cache, ensure_ascii=False), encoding="utf-8")


def placeholders(s: str) -> list[str]:
    return PH_RE.findall(s)


def xml_escape_android(s: str) -> str:
    out: list[str] = []
    i = 0
    while i < len(s):
        ch = s[i]
        if ch == "\\" and i + 1 < len(s):
            out.append(s[i : i + 2])
            i += 2
            continue
        if ch == "&" and not s[i:].startswith(("&amp;", "&lt;", "&gt;", "&#")):
            out.append("&amp;")
        elif ch == "'":
            out.append("\\'")
        elif ch == '"':
            out.append('\\"')
        elif ch == "<":
            out.append("&lt;")
        elif ch == ">":
            out.append("&gt;")
        else:
            out.append(ch)
        i += 1
    return "".join(out)


def translate_segments(text: str, target: str, cache: dict[str, str]) -> str:
    if not text:
        return text
    parts = PH_RE.split(text)
    translator = GoogleTranslator(source="en", target=target)
    out: list[str] = []
    for part in parts:
        if not part:
            continue
        if PH_RE.fullmatch(part):
            out.append(part)
            continue
        stripped = part.strip()
        if not stripped:
            out.append(part)
            continue
        if stripped in TECH_TOKENS:
            out.append(part)
            continue
        key = hashlib.sha1(f"{target}|{part}".encode()).hexdigest()
        if key in cache:
            translated = cache[key]
        else:
            try:
                translated = translator.translate(part) or part
            except Exception:
                translated = part
            cache[key] = translated
            time.sleep(0.03)
        # Preserve leading/trailing whitespace from original segment
        if part != stripped:
            prefix = part[: len(part) - len(part.lstrip())]
            suffix = part[len(part.rstrip()) :]
            out.append(prefix + translated + suffix)
        else:
            out.append(translated)
    return "".join(out)


def parse_strings(path: Path) -> tuple[str, list[re.Match]]:
    text = path.read_text(encoding="utf-8")
    return text, list(STRING_RE.finditer(text))


def write_locale(src_path: Path, dest_path: Path, bodies: dict[str, str]) -> None:
    text, matches = parse_strings(src_path)
    out: list[str] = []
    last = 0
    for m in matches:
        out.append(text[last : m.start()])
        name = m.group("name")
        body = bodies.get(name, m.group("body"))
        escaped = xml_escape_android(body).replace("&amp;amp;", "&amp;")
        out.append(f'{m.group("pre")}<string name="{name}"{m.group("attrs")}>{escaped}</string>')
        last = m.end()
    out.append(text[last:])
    dest_path.parent.mkdir(parents=True, exist_ok=True)
    dest_path.write_text("".join(out), encoding="utf-8")


def needs_la_fix(name: str, en_body: str, la_body: str) -> bool:
    if name in KEEP_AS_IS:
        return False
    if CORRUPT_RE.search(la_body):
        return True
    if la_body == en_body and len(en_body) > 3:
        return True
    if placeholders(en_body) != placeholders(la_body):
        return True
    return False


def needs_hi_fix(name: str, en_body: str, hi_body: str) -> bool:
    if name in HI_FORCE_KEYS or name in HI_MANUAL:
        return True
    if CORRUPT_RE.search(hi_body):
        return True
    if placeholders(en_body) != placeholders(hi_body):
        return True
    if len(en_body) > 100 and len(hi_body) < len(en_body) * 0.55:
        return True
    return False


def regenerate_la(en_path: Path, la_path: Path, cache: dict[str, str]) -> int:
    _, matches = parse_strings(en_path)
    en_bodies = {m.group("name"): m.group("body") for m in matches}
    _, la_matches = parse_strings(la_path)
    la_bodies = {m.group("name"): m.group("body") for m in la_matches}
    updated = 0
    total = len(en_bodies)
    for i, (name, en_body) in enumerate(en_bodies.items(), 1):
        current = la_bodies.get(name, en_body)
        if name in KEEP_AS_IS:
            la_bodies[name] = en_body if name in KEEP_AS_IS else current
            continue
        if not needs_la_fix(name, en_body, current):
            continue
        la_bodies[name] = translate_segments(en_body, "la", cache)
        updated += 1
        if updated % 25 == 0:
            save_cache(cache)
            print(f"    la {la_path.name}: fixed {updated} ({i}/{total})")
    write_locale(en_path, la_path, la_bodies)
    return updated


def regenerate_hi(en_path: Path, hi_path: Path, cache: dict[str, str]) -> int:
    _, matches = parse_strings(en_path)
    en_bodies = {m.group("name"): m.group("body") for m in matches}
    _, hi_matches = parse_strings(hi_path)
    hi_bodies = {m.group("name"): m.group("body") for m in hi_matches}
    updated = 0
    for name, en_body in en_bodies.items():
        current = hi_bodies.get(name, en_body)
        if name in HI_MANUAL:
            hi_bodies[name] = HI_MANUAL[name]
            updated += 1
            continue
        if not needs_hi_fix(name, en_body, current):
            continue
        hi_bodies[name] = translate_segments(en_body, "hi", cache)
        updated += 1
    write_locale(en_path, hi_path, hi_bodies)
    return updated


def audit(path: Path, en_path: Path) -> tuple[int, int]:
    en = {m.group("name"): m.group("body") for m in parse_strings(en_path)[1]}
    tr = {m.group("name"): m.group("body") for m in parse_strings(path)[1]}
    corrupt = sum(1 for k, v in tr.items() if CORRUPT_RE.search(v))
    ph_bad = sum(
        1 for k, v in tr.items()
        if k in en and placeholders(en[k]) != placeholders(v)
    )
    return corrupt, ph_bad


def main() -> None:
    cache = load_cache()
    jobs = [
        ("compose-la", ROOT / "shared/src/commonMain/composeResources/values/strings.xml",
         ROOT / "shared/src/commonMain/composeResources/values-la/strings.xml", "la"),
        ("android-la", ROOT / "app/src/main/res/values/strings.xml",
         ROOT / "app/src/main/res/values-la/strings.xml", "la"),
        ("compose-hi", ROOT / "shared/src/commonMain/composeResources/values/strings.xml",
         ROOT / "shared/src/commonMain/composeResources/values-hi/strings.xml", "hi"),
        ("android-hi", ROOT / "app/src/main/res/values/strings.xml",
         ROOT / "app/src/main/res/values-hi/strings.xml", "hi"),
    ]
    for label, en_path, dest_path, lang in jobs:
        print(f"\n=== {label} ===")
        if lang == "la":
            n = regenerate_la(en_path, dest_path, cache)
        else:
            n = regenerate_hi(en_path, dest_path, cache)
        save_cache(cache)
        c, p = audit(dest_path, en_path)
        print(f"  updated={n}, corrupt={c}, placeholder_mismatch={p}")

    save_cache(cache)
    CACHE_PATH.unlink(missing_ok=True)
    print("\nDone.")


if __name__ == "__main__":
    main()
