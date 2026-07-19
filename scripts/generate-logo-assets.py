"""Generate JNote raster icons and a visual QA sheet from the logo geometry."""

from __future__ import annotations

from pathlib import Path

from PIL import Image, ImageDraw, ImageFont


ROOT = Path(__file__).resolve().parents[1]
RESOURCE_DIR = ROOT / "src" / "main" / "resources" / "com" / "jnote" / "images"
DESIGN_DIR = ROOT / "design"
PACKAGING_DIR = ROOT / "src" / "main" / "packaging"

PAPER = "#718A99"
FOLD = "#AEBBAF"
INK = "#F5F1E8"
CANVAS = "#ECE9E2"
TEXT = "#34434B"
MUTED_TEXT = "#69767B"


def cubic(
    p0: tuple[float, float],
    p1: tuple[float, float],
    p2: tuple[float, float],
    p3: tuple[float, float],
    steps: int = 32,
) -> list[tuple[float, float]]:
    points = []
    for index in range(1, steps + 1):
        t = index / steps
        u = 1 - t
        points.append(
            (
                u**3 * p0[0]
                + 3 * u**2 * t * p1[0]
                + 3 * u * t**2 * p2[0]
                + t**3 * p3[0],
                u**3 * p0[1]
                + 3 * u**2 * t * p1[1]
                + 3 * u * t**2 * p2[1]
                + t**3 * p3[1],
            )
        )
    return points


def scale_points(
    points: list[tuple[float, float]], scale: float
) -> list[tuple[int, int]]:
    return [(round(x * scale), round(y * scale)) for x, y in points]


def page_points() -> list[tuple[float, float]]:
    points = [(129, 52)]
    points += cubic((129, 52), (89, 54), (61, 84), (59, 125))
    points.append((48, 382))
    points += cubic((48, 382), (46, 426), (79, 459), (123, 459))
    points.append((383, 459))
    points += cubic((383, 459), (426, 459), (457, 428), (457, 386))
    points.append((457, 174))
    points += cubic((457, 174), (457, 151), (449, 132), (433, 115))
    points.append((395, 75))
    points += cubic((395, 75), (378, 58), (357, 51), (334, 51))
    return points


def fold_points() -> list[tuple[float, float]]:
    points = [(353, 58), (440, 148), (383, 148)]
    points += cubic((383, 148), (366, 148), (353, 135), (353, 118))
    return points


def j_stroke_points() -> list[tuple[float, float]]:
    points = [(248, 231)]
    points += cubic((248, 231), (247, 274), (252, 314), (242, 348), 40)
    points += cubic((242, 348), (234, 378), (211, 399), (180, 405), 40)
    points += cubic((180, 405), (151, 410), (127, 398), (114, 376), 40)
    return points


def render_mark(size: int, oversample: int = 8) -> Image.Image:
    work_size = size * oversample
    scale = work_size / 512
    image = Image.new("RGBA", (work_size, work_size), (0, 0, 0, 0))
    draw = ImageDraw.Draw(image)

    draw.polygon(scale_points(page_points(), scale), fill=PAPER)
    draw.polygon(scale_points(fold_points(), scale), fill=FOLD)

    stroke = scale_points(j_stroke_points(), scale)
    width = round(66 * scale)
    draw.line(stroke, fill=INK, width=width, joint="curve")
    radius = width / 2
    for x, y in (stroke[0], stroke[-1]):
        draw.ellipse((x - radius, y - radius, x + radius, y + radius), fill=INK)

    dot_x, dot_y, dot_radius = 246 * scale, 169 * scale, 25 * scale
    draw.ellipse(
        (
            dot_x - dot_radius,
            dot_y - dot_radius,
            dot_x + dot_radius,
            dot_y + dot_radius,
        ),
        fill=INK,
    )

    return image.resize((size, size), Image.Resampling.LANCZOS)


def font(size: int, semibold: bool = False) -> ImageFont.FreeTypeFont | ImageFont.ImageFont:
    windows_fonts = Path("C:/Windows/Fonts")
    candidates = (
        [windows_fonts / "seguisb.ttf", windows_fonts / "segoeuib.ttf"]
        if semibold
        else [windows_fonts / "segoeui.ttf"]
    )
    for candidate in candidates:
        if candidate.exists():
            return ImageFont.truetype(str(candidate), size)
    return ImageFont.load_default()


def rounded_rectangle(
    draw: ImageDraw.ImageDraw,
    box: tuple[int, int, int, int],
    fill: str,
    radius: int,
) -> None:
    draw.rounded_rectangle(box, radius=radius, fill=fill)


def make_preview(mark: Image.Image) -> Image.Image:
    preview = Image.new("RGB", (1600, 1000), CANVAS)
    draw = ImageDraw.Draw(preview)

    rounded_rectangle(draw, (80, 70, 1520, 690), "#F8F6F1", 40)
    large_mark = mark.resize((480, 480), Image.Resampling.LANCZOS)
    preview.paste(large_mark, (170, 140), large_mark)

    draw.text((720, 230), "JNote", fill=TEXT, font=font(154, semibold=True))
    draw.text(
        (730, 420),
        "Low-saturation visual system",
        fill=MUTED_TEXT,
        font=font(38),
    )

    draw.text((100, 745), "Muted palette", fill=TEXT, font=font(34, semibold=True))
    palette = [
        (PAPER, "Mist blue", PAPER),
        (FOLD, "Soft sage", FOLD),
        (INK, "Warm ivory", INK),
    ]
    for index, (color, label, value) in enumerate(palette):
        x = 100 + index * 390
        rounded_rectangle(draw, (x, 815, x + 92, 907), color, 22)
        draw.text((x + 118, 816), label, fill=TEXT, font=font(27, semibold=True))
        draw.text((x + 118, 862), value, fill=MUTED_TEXT, font=font(24))

    draw.text((1285, 744), "Small sizes", fill=TEXT, font=font(30, semibold=True))
    for size, x in [(64, 1288), (48, 1375), (32, 1445)]:
        icon = mark.resize((size, size), Image.Resampling.LANCZOS)
        preview.paste(icon, (x, 825), icon)
        label_width = draw.textlength(f"{size}px", font=font(18))
        draw.text(
            (x + (size - label_width) / 2, 900),
            f"{size}px",
            fill=MUTED_TEXT,
            font=font(18),
        )

    return preview


def main() -> None:
    RESOURCE_DIR.mkdir(parents=True, exist_ok=True)
    DESIGN_DIR.mkdir(parents=True, exist_ok=True)
    PACKAGING_DIR.mkdir(parents=True, exist_ok=True)

    source = render_mark(1024)
    source.save(DESIGN_DIR / "jnote-app-icon-1024.png")

    sizes = [16, 24, 28, 32, 48, 64, 128, 256, 512]
    rendered = {size: source.resize((size, size), Image.Resampling.LANCZOS) for size in sizes}
    for size, image in rendered.items():
        image.save(RESOURCE_DIR / f"jnote-app-icon-{size}.png", optimize=True)
    rendered[512].save(RESOURCE_DIR / "jnote-app-icon.png", optimize=True)

    source.save(
        PACKAGING_DIR / "jnote.ico",
        format="ICO",
        sizes=[(size, size) for size in [16, 24, 32, 48, 64, 128, 256]],
    )

    make_preview(source).save(DESIGN_DIR / "jnote-logo-muted-preview.png", optimize=True)

    print(f"Generated {len(sizes)} PNG sizes, master icon, ICO, and preview.")


if __name__ == "__main__":
    main()
