#!/usr/bin/env python3
"""High-res (64x64) Book of Life + Life Gem item textures, pure-python PNG."""
import math
import struct
import zlib

S = 64
OUT = "/home/user/comossion2/resourcepack/assets/minecraft/textures/item"


# ----------------------------------------------------------------------- png
def write_png(path, px):
    raw = bytearray()
    for y in range(S):
        raw.append(0)
        for x in range(S):
            r, g, b, a = px[y][x]
            raw += bytes((r & 255, g & 255, b & 255, a & 255))

    def chunk(t, d):
        return struct.pack(">I", len(d)) + t + d + struct.pack(">I", zlib.crc32(t + d) & 0xffffffff)

    sig = b"\x89PNG\r\n\x1a\n"
    ihdr = struct.pack(">IIBBBBB", S, S, 8, 6, 0, 0, 0)
    with open(path, "wb") as f:
        f.write(sig + chunk(b"IHDR", ihdr) + chunk(b"IDAT", zlib.compress(bytes(raw), 9)) + chunk(b"IEND", b""))


def canvas():
    return [[(0, 0, 0, 0) for _ in range(S)] for _ in range(S)]


def put(px, x, y, c):
    if 0 <= x < S and 0 <= y < S:
        if len(c) == 3:
            c = (c[0], c[1], c[2], 255)
        px[y][x] = (max(0, min(255, c[0])), max(0, min(255, c[1])), max(0, min(255, c[2])), c[3])


def lerp(a, b, t):
    t = max(0.0, min(1.0, t))
    return tuple(int(a[i] + (b[i] - a[i]) * t) for i in range(3))


def ramp(stops, t):
    """stops: list of (pos, color). t in 0..1."""
    t = max(0.0, min(1.0, t))
    for i in range(len(stops) - 1):
        p0, c0 = stops[i]
        p1, c1 = stops[i + 1]
        if t <= p1:
            return lerp(c0, c1, (t - p0) / (p1 - p0) if p1 > p0 else 0)
    return stops[-1][1]


def rect(px, x0, y0, x1, y1, c):
    for y in range(y0, y1 + 1):
        for x in range(x0, x1 + 1):
            put(px, x, y, c)


def alpha_of(px, x, y):
    if 0 <= x < S and 0 <= y < S:
        return px[y][x][3]
    return 0


def outline(px, color, thickness=1):
    """Draw a dark outline around any opaque region."""
    for _ in range(thickness):
        edges = []
        for y in range(S):
            for x in range(S):
                if px[y][x][3] == 0:
                    if (alpha_of(px, x + 1, y) > 0 or alpha_of(px, x - 1, y) > 0
                            or alpha_of(px, x, y + 1) > 0 or alpha_of(px, x, y - 1) > 0):
                        edges.append((x, y))
        for x, y in edges:
            put(px, x, y, color)


# ---------------------------------------------------------------- heart shape
def heart_inside(x, y):
    # implicit heart curve, y up
    return (x * x + y * y - 1) ** 3 - x * x * (y ** 3) < 0


# =====================================================================  GEM
def make_gem():
    px = canvas()
    scale = 22.0
    cx, cy = 32.0, 27.0

    RUBY = [
        (0.00, (70, 6, 20)),
        (0.30, (150, 16, 40)),
        (0.55, (205, 30, 58)),
        (0.78, (240, 92, 110)),
        (1.00, (255, 170, 180)),
    ]

    def to_math(xp, yp):
        return (xp + 0.5 - cx) / scale, (cy - (yp + 0.5)) / scale

    # fill facets (levels snapped so facets read as crisp flat planes)
    for yp in range(S):
        for xp in range(S):
            x, y = to_math(xp, yp)
            if not heart_inside(x, y):
                continue
            dx, dy = x - 0.0, y - 0.20
            r = min(1.0, math.hypot(dx, dy) / 1.12)
            ang = math.atan2(dy, dx)
            sectors = 14
            s = int((ang + math.pi) / (2 * math.pi) * sectors)
            base = 0.66 - 0.34 * r
            facet = 0.12 * (1 if s % 2 == 0 else -1)
            light = 0.20 * max(0.0, math.cos(ang - 2.2))  # up-left facing
            bright = base + facet + light
            bright = round(bright * 7) / 7.0  # snap to flat facet levels
            put(px, xp, yp, ramp(RUBY, bright))

    # bevel: light edge up-left, dark edge down-right
    inside = [[px[y][x][3] > 0 for x in range(S)] for y in range(S)]
    for yp in range(S):
        for xp in range(S):
            if not inside[yp][xp]:
                continue
            up_left_open = (not inside[yp - 1][xp] if yp > 0 else True) or (not inside[yp][xp - 1] if xp > 0 else True)
            dn_right_open = (yp + 1 >= S or not inside[yp + 1][xp]) or (xp + 1 >= S or not inside[yp][xp + 1])
            if up_left_open:
                put(px, xp, yp, (255, 190, 198))
            elif dn_right_open:
                put(px, xp, yp, (95, 10, 26))

    outline(px, (34, 4, 14), 1)

    # clean central "table" facet: a bright flat diamond high on the crown
    for yp in range(16, 30):
        for xp in range(25, 40):
            x, y = to_math(xp, yp)
            if heart_inside(x, y) and abs(xp - 32) / 6.5 + abs(yp - 22) / 5.5 <= 1.0:
                cur = px[yp][xp]
                put(px, xp, yp, lerp(cur, (255, 150, 165), 0.55))

    # two short, symmetric facet seams down from the top notch (subtle)
    seam = (128, 16, 38)
    for t in range(0, 15):
        yy = 22 + t
        if alpha_of(px, 30 - t // 3, yy) > 0:
            put(px, 30 - t // 3, yy, seam)
        if alpha_of(px, 34 + t // 3, yy) > 0:
            put(px, 34 + t // 3, yy, seam)

    # crisp 4-point specular glint on the top-left crown facet
    gx, gy = 24, 19
    for (dx, dy, a) in [(0, 0, 255), (1, 0, 210), (-1, 0, 210), (0, 1, 210), (0, -1, 210),
                        (2, 0, 90), (-2, 0, 90), (0, 2, 90), (0, -2, 90)]:
        if alpha_of(px, gx + dx, gy + dy) > 0:
            put(px, gx + dx, gy + dy, (255, 246, 250, a))
    # tiny secondary sparkle
    if alpha_of(px, 40, 26) > 0:
        put(px, 40, 26, (255, 220, 226))
    return px


# ====================================================================  BOOK
def make_book():
    px = canvas()

    LEATHER = [(0.0, (86, 14, 20)), (0.5, (150, 30, 36)), (1.0, (190, 58, 62))]
    GOLD = [(0.0, (150, 108, 30)), (0.5, (214, 170, 66)), (1.0, (250, 224, 140))]
    PAGE = [(0.0, (196, 182, 146)), (0.5, (232, 220, 186)), (1.0, (250, 244, 220))]

    # --- page stack (behind, offset down-right) ---
    for y in range(12, 57):
        for x in range(16, 54):
            # gradient darker toward bottom-right
            t = 1.0 - ((x - 16) / 40 * 0.35 + (y - 12) / 45 * 0.35)
            put(px, x, y, ramp(PAGE, t))
    # page striations on the right edge
    for y in range(13, 56, 2):
        for x in range(50, 54):
            put(px, x, y, ramp(PAGE, 0.15))

    # --- front cover (leather) ---
    cov = (11, 8, 50, 53)  # x0,y0,x1,y1
    for y in range(cov[1], cov[3] + 1):
        for x in range(cov[0], cov[2] + 1):
            # radial-ish shading: brighter center, darker edges
            nx = (x - (cov[0] + cov[2]) / 2) / ((cov[2] - cov[0]) / 2)
            ny = (y - (cov[1] + cov[3]) / 2) / ((cov[3] - cov[1]) / 2)
            d = min(1.0, (nx * nx + ny * ny) ** 0.5)
            put(px, x, y, ramp(LEATHER, 0.85 - 0.6 * d))
    # spine (left band, darker) + gold bands
    for y in range(cov[1], cov[3] + 1):
        for x in range(cov[0], cov[0] + 5):
            put(px, x, y, ramp(LEATHER, 0.2))
    for by in (14, 24, 37, 47):
        for x in range(cov[0], cov[0] + 5):
            put(px, x, by, ramp(GOLD, 0.5))
            put(px, x, by + 1, ramp(GOLD, 0.8))

    # top-left highlight edge, bottom-right shadow edge on the cover
    for y in range(cov[1], cov[3] + 1):
        put(px, cov[0], y, ramp(LEATHER, 1.0))
        put(px, cov[2], y, ramp(LEATHER, 0.12))
    for x in range(cov[0], cov[2] + 1):
        put(px, x, cov[1], ramp(LEATHER, 1.0))
        put(px, x, cov[3], ramp(LEATHER, 0.12))

    # --- gold ornamental border inside the cover (clean double frame) ---
    bx0, by0, bx1, by1 = cov[0] + 7, cov[1] + 3, cov[2] - 3, cov[3] - 3
    # outer frame: bright top/left, darker bottom/right (bevel)
    for x in range(bx0, bx1 + 1):
        put(px, x, by0, ramp(GOLD, 0.95))
        put(px, x, by1, ramp(GOLD, 0.5))
    for y in range(by0, by1 + 1):
        put(px, bx0, y, ramp(GOLD, 0.95))
        put(px, bx1, y, ramp(GOLD, 0.5))
    # inner frame, one pixel in, mid tone
    for x in range(bx0 + 1, bx1):
        put(px, x, by0 + 1, ramp(GOLD, 0.72))
        put(px, x, by1 - 1, ramp(GOLD, 0.4))
    for y in range(by0 + 1, by1):
        put(px, bx0 + 1, y, ramp(GOLD, 0.72))
        put(px, bx1 - 1, y, ramp(GOLD, 0.4))
    # gold corner brackets (thicker)
    for (cxp, cyp) in [(bx0, by0), (bx1, by0), (bx0, by1), (bx1, by1)]:
        for d in range(4):
            put(px, cxp + (1 if cxp == bx0 else -1) * d, cyp, ramp(GOLD, 0.95))
            put(px, cxp, cyp + (1 if cyp == by0 else -1) * d, ramp(GOLD, 0.95))

    # --- clasp on the right ---
    for x in range(49, 55):
        for y in range(28, 33):
            put(px, x, y, ramp(GOLD, 0.6 + 0.3 * ((x + y) % 2)))
    put(px, 52, 30, ramp(GOLD, 1.0))

    # --- glowing heart emblem, centred on the cover face ---
    hs, hcx, hcy = 10.5, 32.5, 32.0

    def hx(xp):
        return (xp + 0.5 - hcx) / hs

    def hy(yp):
        return (hcy - (yp + 0.5)) / hs

    hearts = set()
    for yp in range(13, 51):
        for xp in range(15, 51):
            if heart_inside(hx(xp), hy(yp)):
                hearts.add((xp, yp))

    # soft warm glow halo on the leather around the heart
    for yp in range(cov[1] + 1, cov[3]):
        for xp in range(cov[0] + 5, cov[2]):
            if (xp, yp) in hearts:
                continue
            d = math.hypot(hx(xp), hy(yp) - 0.2)
            if 0.9 < d < 1.85:
                put(px, xp, yp, lerp(px[yp][xp], (255, 176, 96), (1.85 - d) * 0.5))

    # heart fill: gold with up-left light, snapped to crisp shade bands
    for (xp, yp) in hearts:
        x, y = hx(xp), hy(yp)
        r = min(1.0, math.hypot(x, y - 0.15) / 1.05)
        light = 0.18 * max(0.0, (-x + y))  # upper-left brighter
        b = round((0.62 - 0.30 * r + light) * 6) / 6.0
        put(px, xp, yp, ramp(GOLD, b))

    # bright warm core
    for (xp, yp) in hearts:
        if math.hypot(hx(xp), hy(yp) - 0.05) < 0.30:
            put(px, xp, yp, lerp(px[yp][xp], (255, 240, 186), 0.5))

    # crisp dark-gold rim
    for (xp, yp) in list(hearts):
        if ((xp + 1, yp) not in hearts or (xp - 1, yp) not in hearts
                or (xp, yp + 1) not in hearts or (xp, yp - 1) not in hearts):
            put(px, xp, yp, ramp(GOLD, 0.22))

    # a single soft specular glint on the upper-left lobe (not symmetric, so it
    # reads as a light reflection rather than a pair of eyes)
    for (dx, dy, c) in [(0, 0, (255, 255, 240)), (1, 0, (255, 251, 220)),
                        (0, 1, (255, 249, 214)), (1, 1, (255, 247, 210)), (2, 1, (255, 244, 204))]:
        p = (27 + dx, 25 + dy)
        if p in hearts:
            put(px, p[0], p[1], c)

    outline(px, (26, 8, 10), 1)
    return px


def make_mc_heart():
    """The classic Minecraft health heart: chunky pixels, black outline,
    bright red body, dark-maroon interior cross."""
    K = (0, 0, 0)
    R = (228, 26, 28)
    D = (122, 14, 18)
    H = (255, 120, 120)
    grid = [
        "...kk...kk...",
        "..krrk.krrk..",
        ".krrrrdrrrrk.",
        "krrrdddddrrrk",
        "krrrdddddrrrk",
        ".krrrrdrrrrk.",
        "..krrrdrrrk..",
        "...krrdrrk...",
        "....krdrk....",
        ".....kdk.....",
        "......k......",
    ]
    cmap = {".": None, "k": K, "r": R, "d": D, "h": H}
    px = canvas()
    scale = 4
    gw, gh = 13 * scale, len(grid) * scale
    xoff, yoff = (S - gw) // 2, (S - gh) // 2
    for gy, row in enumerate(grid):
        for gx, ch in enumerate(row):
            c = cmap[ch]
            if c is None:
                continue
            for dy in range(scale):
                for dx in range(scale):
                    put(px, xoff + gx * scale + dx, yoff + gy * scale + dy, c)
    return px


write_png(OUT + "/life_gem.png", make_mc_heart())
write_png(OUT + "/book_of_life.png", make_book())
print("wrote textures at", S, "x", S)
