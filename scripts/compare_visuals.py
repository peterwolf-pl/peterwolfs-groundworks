#!/usr/bin/env python3
"""Compare deterministic Groundworks screenshots.

Requires Pillow (`python3 -m pip install Pillow`). The script is development-only;
its dependency is not packaged with the mod.
"""

from __future__ import annotations

import argparse
import json
from pathlib import Path

from PIL import Image, ImageChops, ImageEnhance


def compare(baseline_path: Path, current_path: Path, diff_dir: Path) -> dict[str, object]:
    baseline = Image.open(baseline_path).convert("RGB")
    current = Image.open(current_path).convert("RGB")
    original_current_size = current.size
    normalized = current.size != baseline.size
    if normalized:
        current = current.resize(baseline.size, Image.Resampling.LANCZOS)

    difference = ImageChops.difference(baseline, current)
    histogram = difference.histogram()
    channel_pixels = baseline.width * baseline.height * 3
    absolute_sum = sum((index % 256) * count for index, count in enumerate(histogram))
    mean_absolute_difference = absolute_sum / channel_pixels / 255.0

    threshold = 12
    changed = 0
    pixels = difference.load()
    for y in range(difference.height):
        for x in range(difference.width):
            if max(pixels[x, y]) > threshold:
                changed += 1

    diff_dir.mkdir(parents=True, exist_ok=True)
    heatmap_path = diff_dir / baseline_path.name
    ImageEnhance.Contrast(difference).enhance(3.0).save(heatmap_path)

    return {
        "name": baseline_path.name,
        "baseline_size": list(baseline.size),
        "current_size": list(original_current_size),
        "size_normalized": normalized,
        "mean_absolute_rgb_difference": round(mean_absolute_difference, 6),
        "changed_pixel_percentage": round(changed * 100.0 / (baseline.width * baseline.height), 3),
        "changed_pixel_threshold": threshold,
        "diff_image": str(heatmap_path),
    }


def main() -> int:
    parser = argparse.ArgumentParser()
    parser.add_argument("--baseline", type=Path, default=Path("visual-tests/baseline"))
    parser.add_argument("--current", type=Path, default=Path("visual-tests/current"))
    parser.add_argument("--output", type=Path, default=Path("visual-tests/comparison.json"))
    args = parser.parse_args()

    names = sorted(
        path.name
        for path in args.baseline.glob("*.png")
        if (args.current / path.name).is_file() and path.name != "contact-sheet.png"
    )
    if not names:
        raise SystemExit("No matching PNG filenames found in baseline and current directories")

    diff_dir = args.output.parent / "diff"
    results = [
        compare(args.baseline / name, args.current / name, diff_dir)
        for name in names
    ]
    payload = {
        "warning": "Pixel difference detects change; it does not determine visual quality.",
        "comparisons": results,
    }
    args.output.parent.mkdir(parents=True, exist_ok=True)
    args.output.write_text(json.dumps(payload, indent=2) + "\n", encoding="utf-8")
    print(json.dumps(payload, indent=2))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
