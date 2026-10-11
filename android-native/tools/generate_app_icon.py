"""Generate Android launcher resources from the supplied artwork (Pillow)."""
from pathlib import Path
import sys
from io import BytesIO
from PIL import Image, ImageCms, ImageDraw, ImageOps

project = Path(__file__).resolve().parents[1]
source = Path(sys.argv[1])
with Image.open(source) as original:
    image = ImageOps.exif_transpose(original)
    profile = original.info.get("icc_profile")
    if profile:
        image = ImageCms.profileToProfile(image, ImageCms.ImageCmsProfile(BytesIO(profile)), ImageCms.createProfile("sRGB"), outputMode="RGB")
    else:
        image = image.convert("RGB")
    art = ImageOps.fit(image, (512, 512), method=Image.Resampling.LANCZOS)
res = project / "app/src/main/res"
background = "#" + "".join(f"{channel:02X}" for channel in art.getpixel((0, 0)))
(res / "values/icon_colors.xml").write_text(
    f'<?xml version="1.0" encoding="utf-8"?>\n<resources>\n    <color name="app_icon_background">{background}</color>\n</resources>\n',
    encoding="utf-8",
)
for density, size in [("mdpi", 48), ("hdpi", 72), ("xhdpi", 96), ("xxhdpi", 144), ("xxxhdpi", 192)]:
    folder = res / f"mipmap-{density}"
    folder.mkdir(parents=True, exist_ok=True)
    art.resize((size, size), Image.Resampling.LANCZOS).save(folder / "app_icon.png", optimize=True)

# 108dp adaptive layer with artwork in the central 72dp; keep the face in the safe area.
foreground = Image.new("RGBA", (432, 432), (0, 0, 0, 0))
foreground.paste(art.resize((288, 288), Image.Resampling.LANCZOS), (72, 72))
(res / "drawable-nodpi").mkdir(parents=True, exist_ok=True)
foreground.save(res / "drawable-nodpi/app_icon.png", optimize=True)

preview = Image.new("RGB", (560, 280), "#10111A")
for index, shape in enumerate(["round", "square"]):
    mask = Image.new("L", (192, 192), 0)
    draw = ImageDraw.Draw(mask)
    if shape == "round":
        draw.ellipse((0, 0, 191, 191), fill=255)
    else:
        draw.rounded_rectangle((0, 0, 191, 191), radius=42, fill=255)
    # Preview a common 72dp launcher mask, excluding the adaptive layer's outer inset.
    picture = foreground.crop((72, 72, 360, 360)).resize((192, 192), Image.Resampling.LANCZOS)
    preview.paste(picture, (48 + index * 272, 44), mask)
output = project / "docs/images/app-icon-preview.png"
output.parent.mkdir(parents=True, exist_ok=True)
preview.save(output, optimize=True)
print("Generated 5 launcher sizes, adaptive foreground, and preview.")
