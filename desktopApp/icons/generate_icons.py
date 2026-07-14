"""Generate platform-specific desktop icons from the shared PNG."""
from __future__ import annotations

import io
import struct
from pathlib import Path

from PIL import Image

ROOT = Path(__file__).resolve().parent
SRC = ROOT / "icon.png"


def write_ico(img: Image.Image, dest: Path) -> None:
    # Upscale first so large ICO entries stay sharp from a 192px Android mipmap.
    base = img.resize((256, 256), Image.Resampling.LANCZOS)
    sizes = [(16, 16), (24, 24), (32, 32), (48, 48), (64, 64), (128, 128), (256, 256)]
    base.save(dest, format="ICO", sizes=sizes)
    print(f"wrote {dest} ({dest.stat().st_size} bytes)")


def write_icns(img: Image.Image, dest: Path) -> None:
    # PNG-compressed ICNS icons (modern macOS).
    # See https://en.wikipedia.org/wiki/Apple_Icon_Image_format
    type_for_size = {
        16: b"icp4",
        32: b"icp5",
        64: b"icp6",
        128: b"ic07",
        256: b"ic08",
        512: b"ic09",
        1024: b"ic10",
    }
    chunks: list[bytes] = []
    for size, ostype in type_for_size.items():
        png_buf = io.BytesIO()
        img.resize((size, size), Image.Resampling.LANCZOS).save(png_buf, format="PNG")
        png_data = png_buf.getvalue()
        chunks.append(ostype + struct.pack(">I", 8 + len(png_data)) + png_data)
    body = b"".join(chunks)
    dest.write_bytes(b"icns" + struct.pack(">I", 8 + len(body)) + body)
    print(f"wrote {dest} ({dest.stat().st_size} bytes)")


def main() -> None:
    if not SRC.is_file():
        raise SystemExit(f"Missing source icon: {SRC}")
    img = Image.open(SRC).convert("RGBA")
    print(f"source size: {img.size}")
    write_ico(img, ROOT / "icon.ico")
    write_icns(img, ROOT / "icon.icns")


if __name__ == "__main__":
    main()
