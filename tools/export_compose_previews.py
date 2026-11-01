"""Export real Robolectric Compose renders into reviewable image boards and a GIF.

Run after ComposeDashboardTest: python tools/export_compose_previews.py
Requires Pillow. Only scales screenshots; labels are drawn outside app pixels.
"""

import argparse
from pathlib import Path
import shutil

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / "module-ui/build/outputs/compose-preview"
OUTPUT = ROOT / "assets/screenshots/compose"


def font_path(explicit):
    candidates = [explicit] if explicit else []
    candidates += [
        "C:/Windows/Fonts/msyh.ttc",
        "/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc",
        "/System/Library/Fonts/PingFang.ttc",
    ]
    for candidate in candidates:
        if candidate and Path(candidate).is_file():
            return candidate
    raise SystemExit("Supply --font with a Chinese-capable .ttf or .ttc font.")


def board(filename, heading, subtitle, entries, font):
    padding, gap, width = 40, 24, 336
    screenshots = []
    for label, relative in entries:
        with Image.open(SOURCE / relative) as source:
            size = (width, round(source.height * width / source.width))
            screenshots.append((label, source.convert("RGB").resize(size, Image.Resampling.LANCZOS)))
    height = max(shot.height for _, shot in screenshots)
    canvas = Image.new("RGB", (padding * 2 + width * len(entries) + gap * (len(entries) - 1), height + 210), "#F1F4F9")
    draw = ImageDraw.Draw(canvas)
    title_font = ImageFont.truetype(font, 28)
    label_font = ImageFont.truetype(font, 19)
    small_font = ImageFont.truetype(font, 16)
    draw.text((padding, 23), heading, font=title_font, fill="#202124")
    draw.text((padding, 68), subtitle, font=small_font, fill="#5F6368")
    for index, (label, shot) in enumerate(screenshots):
        x, y = padding + index * (width + gap), 135
        draw.text((x, 104), label, font=label_font, fill="#174275")
        canvas.paste(shot, (x, y))
        draw.rectangle((x - 1, y - 1, x + width, y + shot.height), outline="#DADCE0")
    draw.text((padding, height + 161), "实际 Compose 树离线渲染 · 非真机截图 · 未模拟激活或交接成功", font=small_font, fill="#5F6368")
    canvas.save(OUTPUT / filename, optimize=True)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--font", help="Chinese-capable font path")
    args = parser.parse_args()
    font = font_path(args.font)
    pages = ["home", "diagnostics", "regression", "settings"]
    styles = ["material-light", "material-dark", "miuix-light", "miuix-dark"]
    raw = [f"{style}/{page}.png" for style in styles for page in pages]
    raw += ["appearance-sheet-before.png", "appearance-sheet.png", "destination-sheet.png", "about-sheet.png"]
    motion = ["motion/home.png"] + [f"motion/frame-{n:02}.png" for n in range(24)] + ["motion/diagnostics.png"]
    for relative in raw + motion:
        if not (SOURCE / relative).is_file():
            raise SystemExit(f"Missing {relative}; run ComposeDashboardTest first.")
    OUTPUT.mkdir(parents=True, exist_ok=True)
    for relative in raw:
        target = OUTPUT / relative
        target.parent.mkdir(parents=True, exist_ok=True)
        shutil.copy2(SOURCE / relative, target)
    board("pages.png", "Handoff / Compose Material 3", "Google 蓝与中性纯色 · 标准组件 · 清晰的信息层级", [
        ("01  概览", "material-light/home.png"),
        ("02  诊断", "material-light/diagnostics.png"),
        ("03  回归", "material-light/regression.png"),
        ("04  设置", "material-light/settings.png"),
    ], font)
    board("appearance.png", "Handoff / 外观与上下文面板", "浅深色跟随系统 · 局部背景玻璃 · 软件渲染可能降级为底色", [
        ("浅色概览", "material-light/home.png"),
        ("深色概览", "material-dark/home.png"),
        ("外观选择面板", "appearance-sheet-before.png"),
        ("目的地详情面板", "destination-sheet.png"),
    ], font)
    board("styles.png", "Handoff / 两套独立界面", "Material：Suki 液态浮岛（软件渲染为降级效果）   /   Miuix：分组列表、平铺底栏", [
        ("Material · 概览", "material-light/home.png"),
        ("Miuix · 概览", "miuix-light/home.png"),
        ("Material · 设置", "material-light/settings.png"),
        ("Miuix · 设置", "miuix-light/settings.png"),
    ], font)
    frames = []
    for relative in motion:
        with Image.open(SOURCE / relative) as source:
            frames.append(source.convert("RGB").resize((393, 851), Image.Resampling.LANCZOS))
    frames[0].save(OUTPUT / "motion.gif", save_all=True, append_images=frames[1:],
                   duration=[800] + [20] * 24 + [1200], loop=0, disposal=2)
    print(f"Exported {len(raw)} screenshots, 3 boards and 1 GIF to {OUTPUT}")


if __name__ == "__main__":
    main()
