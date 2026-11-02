import math
import os

from PIL import Image, ImageChops, ImageDraw, ImageFilter

W, H = 1440, 3200
OUT = "/var/minis/workspace/timewallpaper/testwall"
os.makedirs(OUT, exist_ok=True)


def lerp(a, b, t):
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def vertical_gradient(top, bottom, mid=None):
    img = Image.new("RGB", (W, H))
    px = img.load()
    for y in range(H):
        t = y / (H - 1)
        if mid is None:
            c = lerp(top, bottom, t)
        else:
            if t < 0.5:
                c = lerp(top, mid, t / 0.5)
            else:
                c = lerp(mid, bottom, (t - 0.5) / 0.5)
        for x in range(W):
            px[x, y] = c
    return img


def add_glow(img, cx, cy, radius, color, strength=180):
    layer = Image.new("RGB", (W, H), (0, 0, 0))
    d = ImageDraw.Draw(layer)
    steps = 60
    for i in range(steps, 0, -1):
        r = radius * i / steps
        a = int(strength * (1 - i / steps) ** 2 * 0.35)
        d.ellipse([cx - r, cy - r, cx + r, cy + r], fill=tuple(int(c * a / 255) for c in color))
    layer = layer.filter(ImageFilter.GaussianBlur(40))
    return ImageChops.add(img, layer)


def add_mountains(img, horizon, color, jag=0.0, seed=1):
    d = ImageDraw.Draw(img)
    import random
    random.seed(seed)
    points = [(0, H), (0, horizon)]
    x = 0
    while x < W:
        step = random.randint(120, 260)
        x = min(W, x + step)
        y = horizon - random.randint(0, int(260 * (1 + jag)))
        points.append((x, y))
    points += [(W, H)]
    layer = Image.new("RGBA", (W, H), (0, 0, 0, 0))
    dl = ImageDraw.Draw(layer)
    dl.polygon(points, fill=color)
    return Image.alpha_composite(img.convert("RGBA"), layer).convert("RGB")


def finish(img, name):
    img = img.filter(ImageFilter.GaussianBlur(1.2))
    img.save(os.path.join(OUT, name), quality=94)
    print(name)


# 1. dawn
img = vertical_gradient((28, 24, 62), (255, 176, 120), (120, 70, 120))
img = add_glow(img, W // 2, int(H * 0.55), 560, (255, 210, 150), 220)
img = add_mountains(img, int(H * 0.62), (60, 38, 62, 255), 0.4, 3)
img = add_mountains(img, int(H * 0.72), (30, 20, 38, 255), 0.8, 7)
finish(img, "dawn.jpg")

# 2. day
img = vertical_gradient((110, 170, 235), (225, 240, 250), (170, 215, 245))
img = add_glow(img, int(W * 0.72), int(H * 0.22), 420, (255, 255, 240), 160)
img = add_mountains(img, int(H * 0.58), (120, 160, 175, 255), 0.6, 11)
img = add_mountains(img, int(H * 0.68), (80, 120, 130, 255), 0.9, 5)
finish(img, "day.jpg")

# 3. dusk
img = vertical_gradient((40, 26, 70), (255, 130, 90), (150, 70, 120))
img = add_glow(img, int(W * 0.45), int(H * 0.66), 520, (255, 150, 110), 230)
img = add_mountains(img, int(H * 0.74), (55, 30, 55, 255), 0.7, 21)
img = add_mountains(img, int(H * 0.82), (25, 15, 30, 255), 1.0, 13)
finish(img, "dusk.jpg")

# 4. night
img = vertical_gradient((8, 10, 30), (28, 34, 70), (14, 18, 46))
d = ImageDraw.Draw(img)
import random
random.seed(9)
for _ in range(700):
    x = random.randint(0, W - 1)
    y = random.randint(0, int(H * 0.62))
    r = random.choice([1, 1, 1, 2, 2, 3])
    b = random.randint(120, 255)
    d.ellipse([x, y, x + r, y + r], fill=(b, b, min(255, b + 20)))
img = add_glow(img, int(W * 0.7), int(H * 0.18), 260, (220, 230, 255), 200)
img = add_mountains(img, int(H * 0.7), (18, 22, 48, 255), 0.8, 31)
img = add_mountains(img, int(H * 0.8), (8, 10, 24, 255), 1.0, 17)
finish(img, "night.jpg")
print("done", OUT)
