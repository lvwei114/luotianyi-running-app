"""Collect actual Compose screenshots and make a compact streak preview sheet."""
from pathlib import Path
from html import escape
import shutil
from PIL import Image, ImageDraw, ImageFont

project = Path(__file__).resolve().parents[1]
output = project / "docs/images/streak-states"
output.mkdir(parents=True, exist_ok=True)
states = [
    (0, "首次未跑 · icon0"),
    (1, "连续 1–3 天 · icon1（示例 2 天）"),
    (2, "连续 4–7 天 · icon2（示例 6 天）"),
    (3, "连续 8 天及以上 · icon3（示例 9 天）"),
    (4, "连续记录中断 · icon4"),
    (5, "每连续 5 天的祝贺弹窗 · icon5"),
]
font = ImageFont.truetype("C:/Windows/Fonts/msyh.ttc", 17)
sheet = Image.new("RGB", (810, 522), "#10111A")
draw = ImageDraw.Draw(sheet)
for index, title in states:
    name = f"streak-icon{index}.png"
    shutil.copyfile(project / f"app/build/qa/native-{name}", output / name)
    if index < 5:
        x = 10 + (index % 2) * 400
        y = 12 + (index // 2) * 174
        draw.text((x, y), title, font=font, fill="#66CCFF")
        screenshot = Image.open(output / name).convert("RGB")
        sheet.paste(screenshot.crop((0, 0, 390, 130)), (x, y + 30))
draw.text((410, 390), "同一天多次跑步，只算一天。", font=font, fill="#A6AFBF")
draw.text((410, 424), "漏掉一个完整自然日后显示 icon4。", font=font, fill="#A6AFBF")
draw.text((410, 458), "重新完成跑步后，从第 1 天开始。", font=font, fill="#A6AFBF")
sheet.save(output / "streak-overview.png")
cards = "\n".join(
    f'<figure><figcaption>{escape(title)}</figcaption><img src="streak-icon{index}.png" alt="{escape(title)}"></figure>'
    for index, title in states
)
(output / "index.html").write_text(
    '<!doctype html><html lang="zh-CN"><meta charset="utf-8"><meta name="viewport" content="width=device-width,initial-scale=1">'
    '<title>连续跑步状态预览</title><style>body{background:#10111a;color:#fff6ec;font-family:system-ui;margin:24px}'
    'main{display:grid;grid-template-columns:repeat(auto-fit,minmax(300px,1fr));gap:24px;max-width:1240px}'
    'figure{margin:0;background:#1d202d;padding:16px;border-radius:18px}figcaption{color:#66ccff;margin-bottom:14px}'
    'img{display:block;max-width:100%;height:auto;margin:auto}</style><h1>连续跑步状态预览</h1>'
    '<p>由实际 Compose 界面绘制；预览中的记录为示例数据。</p><main>' + cards + '</main></html>',
    encoding="utf-8",
)
print(output)
