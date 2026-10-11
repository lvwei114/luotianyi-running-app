"""Build density-specific streak artwork from the untouched original PNGs."""
from pathlib import Path
from PIL import Image, ImageFilter

project = Path(__file__).resolve().parents[1]
source = project / "artwork/streak-icons"
resources = project / "app/src/main/res"
densities = [("mdpi", 1), ("hdpi", 1.5), ("xhdpi", 2), ("xxhdpi", 3), ("xxxhdpi", 4)]

for index in range(6):
    with Image.open(source / f"icon{index}.png") as image:
        original = image.convert("RGBA")
    logical_size = 200 if index == 5 else 42
    for density, scale in densities:
        size = round(logical_size * scale)
        # Pillow resizes RGBA in premultiplied alpha to keep transparent edges clean.
        resized = original.resize((size, size), Image.Resampling.LANCZOS)
        alpha = resized.getchannel("A")
        colors = resized.convert("RGB")
        sharpened = colors.filter(ImageFilter.UnsharpMask(radius=0.6, percent=45, threshold=3))
        # Sharpen only solid artwork; preserve translucent outlines and the alpha channel.
        solid = alpha.point(lambda value: 255 if value >= 240 else 0)
        colors.paste(sharpened, mask=solid)
        result = colors.convert("RGBA")
        result.putalpha(alpha)
        folder = resources / f"drawable-{density}"
        folder.mkdir(parents=True, exist_ok=True)
        result.save(folder / f"streak_icon{index}.png", optimize=True)

print("Generated 30 PNGs: homepage 42dp, milestone 200dp, mdpi through xxxhdpi.")
