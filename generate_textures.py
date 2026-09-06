#!/usr/bin/env python3
"""Generate Minecart Mania's block textures out of vanilla's rails.

Every rail here is a vanilla rail with something changed, and the change is stated as a
rule rather than drawn by hand: the wooden rail is the iron rail with its metal turned to
plank, the copper powered rail is the gold one with its gold turned to copper, the crossing
is a rail laid over a rail, the junction a rail with one arm of another across it. Ties and
redstone are left as they are. Source pixels are read straight out of the vanilla Minecraft
jar; nothing is smoothed.

Pure stdlib PNG reader and writer (zlib + struct) so it runs without Pillow, the same
script generated art approach as the rest of the suite. Deterministic: re-running produces
identical bytes.

Usage: python3 generate_textures.py [path/to/minecraft.jar]
"""

import glob
import os
import struct
import sys
import zipfile
import zlib
from collections import Counter

HERE = os.path.dirname(os.path.abspath(__file__))
OUT = os.path.join(HERE, "src/main/resources/assets/spawner-shards-justfatlard/icon.png")

CLEAR = (0, 0, 0, 0)
_JAR = None


def minecraft_version():
    """The version this mod targets, so the sprite is cut from the same jar the
    mod is built against rather than whatever happens to be cached."""
    path = os.path.join(HERE, "gradle.properties")
    if not os.path.exists(path):
        return None
    for line in open(path):
        key, sep, value = line.partition("=")
        if sep and key.strip() == "minecraft_version":
            return value.strip()
    return None


def find_jar():
    """Loom caches the remapped Minecraft jars after a build; that is where the
    vanilla art comes from. Override with an argument or $MINECRAFT_JAR."""
    global _JAR
    if _JAR:
        return _JAR
    if len(sys.argv) > 1:
        _JAR = sys.argv[1]
        return _JAR
    if os.environ.get("MINECRAFT_JAR"):
        _JAR = os.environ["MINECRAFT_JAR"]
        return _JAR
    cache = os.path.expanduser("~/.gradle/caches/fabric-loom")
    names = ("minecraft-merged.jar", "minecraft-client.jar")
    found = []
    version = minecraft_version()
    if version:
        for name in names:
            found += glob.glob(os.path.join(cache, version, name))
    if not found:
        for name in names:
            found += glob.glob(os.path.join(cache, "*", name))
    if not found:
        sys.exit("no cached Minecraft jar found: build the mod once, "
                 "or pass a jar path as the first argument")
    _JAR = max(found, key=os.path.getmtime)
    return _JAR


def vanilla(name):
    """Read assets/minecraft/textures/<name> out of the vanilla jar."""
    with zipfile.ZipFile(find_jar()) as jar:
        return decode_png(jar.read("assets/minecraft/textures/" + name))


def decode_png(data):
    """Minimal PNG reader: no interlacing, every colour type and bit depth
    vanilla actually ships. Returns rows of RGBA tuples."""
    pos = 8
    idat = b""
    width = height = depth = ctype = None
    palette = trns = None
    while pos < len(data):
        (length,) = struct.unpack(">I", data[pos:pos + 4])
        tag = data[pos + 4:pos + 8]
        body = data[pos + 8:pos + 8 + length]
        pos += 12 + length
        if tag == b"IHDR":
            width, height, depth, ctype, _, _, interlace = struct.unpack(">IIBBBBB", body)
            assert interlace == 0, "interlaced PNG not supported"
        elif tag == b"PLTE":
            palette = body
        elif tag == b"tRNS":
            trns = body
        elif tag == b"IDAT":
            idat += body
        elif tag == b"IEND":
            break

    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}[ctype]
    stride = (width * channels * depth + 7) // 8
    step = max(1, (channels * depth) // 8)
    raw = zlib.decompress(idat)
    out = bytearray(stride * height)
    prev = bytearray(stride)
    p = 0
    for y in range(height):
        filt = raw[p]
        p += 1
        line = bytearray(raw[p:p + stride])
        p += stride
        if filt == 1:
            for i in range(step, stride):
                line[i] = (line[i] + line[i - step]) & 0xFF
        elif filt == 2:
            for i in range(stride):
                line[i] = (line[i] + prev[i]) & 0xFF
        elif filt == 3:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                line[i] = (line[i] + ((a + prev[i]) >> 1)) & 0xFF
        elif filt == 4:
            for i in range(stride):
                a = line[i - step] if i >= step else 0
                b = prev[i]
                c = prev[i - step] if i >= step else 0
                pa, pb, pc = abs(b - c), abs(a - c), abs(a + b - 2 * c)
                pr = a if (pa <= pb and pa <= pc) else (b if pb <= pc else c)
                line[i] = (line[i] + pr) & 0xFF
        out[y * stride:(y + 1) * stride] = line
        prev = line

    pixels = []
    if depth < 8:
        per = 8 // depth
        mask = (1 << depth) - 1
        for y in range(height):
            base = y * stride
            row = []
            for x in range(width):
                i = x * channels
                value = (out[base + i // per] >> (8 - depth * (i % per + 1))) & mask
                if ctype == 3:
                    r, g, b = palette[value * 3:value * 3 + 3]
                    a = trns[value] if trns and value < len(trns) else 255
                    row.append((r, g, b, a))
                else:
                    v = value * 255 // mask
                    row.append((v, v, v, 255))
            pixels.append(row)
        return pixels

    for y in range(height):
        base = y * stride
        row = []
        for x in range(width):
            i = base + x * channels
            if ctype == 6:
                row.append(tuple(out[i:i + 4]))
            elif ctype == 2:
                row.append((out[i], out[i + 1], out[i + 2], 255))
            elif ctype == 4:
                row.append((out[i], out[i], out[i], out[i + 1]))
            elif ctype == 0:
                row.append((out[i], out[i], out[i], 255))
            else:
                r, g, b = palette[out[i] * 3:out[i] * 3 + 3]
                a = trns[out[i]] if trns and out[i] < len(trns) else 255
                row.append((r, g, b, a))
        pixels.append(row)
    return pixels


def write_png(path, pixels):
    """pixels: rows of RGBA tuples."""
    height = len(pixels)
    width = len(pixels[0])
    raw = b"".join(b"\x00" + b"".join(bytes(px) for px in row) for row in pixels)

    def chunk(tag, body):
        c = tag + body
        return struct.pack(">I", len(body)) + c + struct.pack(">I", zlib.crc32(c))

    ihdr = struct.pack(">IIBBBBB", width, height, 8, 6, 0, 0, 0)
    png = (b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", ihdr)
           + chunk(b"IDAT", zlib.compress(raw, 9)) + chunk(b"IEND", b""))
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "wb") as f:
        f.write(png)
    print("wrote %s (%dx%d)" % (path, width, height))


OUT_BLOCK = os.path.join(HERE, "src/main/resources/assets/minecart-mania-justfatlard/textures/block")
OUT_ITEM = os.path.join(HERE, "src/main/resources/assets/minecart-mania-justfatlard/textures/item")


def block_cart(cart, top, front, side):
    """A block sitting in a cart, drawn the way vanilla draws its furnace cart: the cart from
    the plain cart icon, and over it a block in the icon's own three-quarter view - top face a
    diamond across the upper rows, the front face down the left, the side down the right - each
    face cut from the block's own texture and shaded as the game shades a block. The first
    working cart icons pasted a flat, pixel-skipped front over the chest cart's chest, which was
    a grey smear with a hole in it."""
    out = [list(row) for row in cart]
    tx, ty, half_w, half_h, drop = 7.5, 3.0, 5.5, 2.5, 6.0   # the top diamond and the face height
    OUTLINE = (40, 30, 11, 255)   # the dark rim vanilla draws round the block in its carts

    def shade(px, k):
        return (int(px[0] * k), int(px[1] * k), int(px[2] * k), px[3])

    def sample(tex, u, v):
        return tex[min(15, max(0, int(v * 16)))][min(15, max(0, int(u * 16)))]

    block = {}
    for y in range(16):
        for x in range(16):
            cx, cy = x + 0.5, y + 0.5
            s, t = (cx - tx) / half_w, (cy - ty) / half_h
            if abs(s) + abs(t) <= 1.0:
                block[(x, y)] = shade(sample(top, (s - t + 1) / 2, (s + t + 1) / 2), 1.0)
            elif tx - half_w <= cx < tx:
                edge = ty + (cx - (tx - half_w)) / half_w * half_h
                if edge <= cy < edge + drop:
                    block[(x, y)] = shade(sample(front, (cx - (tx - half_w)) / half_w, (cy - edge) / drop), 0.75)
            elif tx <= cx < tx + half_w:
                edge = ty + half_h - (cx - tx) / half_w * half_h
                if edge <= cy < edge + drop:
                    block[(x, y)] = shade(sample(side, (cx - tx) / half_w, (cy - edge) / drop), 0.5)
    for (x, y), px in block.items():
        rim = any((x + dx, y + dy) not in block for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)))
        out[y][x] = OUTLINE if rim else px
    return out

# The iron rail's metal, darkest to lightest, and what it becomes in wood and in copper.
RAIL_METAL = [(104, 104, 104), (114, 121, 110), (156, 156, 156), (171, 172, 171)]
WOOD = [(103, 80, 44), (126, 98, 55), (175, 143, 85), (194, 157, 98)]
GOLD = [(201, 136, 29), (220, 174, 41)]
COPPER = [(178, 98, 71), (214, 123, 91)]


def recolour(pixels, mapping):
    table = dict(zip(*mapping))
    return [[(table[px[:3]] + (px[3],)) if px[3] and px[:3] in table else px for px in row] for row in pixels]


def rotate90(pixels):
    """Clockwise, so a north-south rail becomes east-west."""
    n = len(pixels)
    return [[pixels[n - 1 - x][y] for x in range(n)] for y in range(n)]


def overlay(base, top, keep_rails):
    """Lay top over base. Rail metal in the base stays on top of the other's ties, so the two
    lines read as crossing at grade rather than one buried under the other."""
    out = []
    for y in range(len(base)):
        row = []
        for x in range(len(base[y])):
            b, t = base[y][x], top[y][x]
            if b[3] and b[:3] in keep_rails:
                row.append(b)
            elif t[3]:
                row.append(t)
            else:
                row.append(b)
        out.append(row)
    return out


def half(pixels, rows):
    """Only the given rows kept; everything else clear."""
    return [[px if y in rows else CLEAR for px in row] for y, row in enumerate(pixels)]


def metal(pixels):
    """Only the rails kept; the sleepers under them clear."""
    return [[px if px[3] and px[:3] in RAIL_METAL else CLEAR for px in row] for row in pixels]


def flip(pixels):
    """Top to bottom, so a curve joining the south face joins the north."""
    return pixels[::-1]


def build():
    rail = vanilla("block/rail.png")
    corner = vanilla("block/rail_corner.png")
    powered = vanilla("block/powered_rail.png")
    powered_on = vanilla("block/powered_rail_on.png")

    textures = {
        "wooden_rail": recolour(rail, (RAIL_METAL, WOOD)),
        "wooden_rail_corner": recolour(corner, (RAIL_METAL, WOOD)),
        "copper_powered_rail": recolour(powered, (GOLD, COPPER)),
        "copper_powered_rail_on": recolour(powered_on, (GOLD, COPPER)),
        "cross_rail": overlay(rail, rotate90(rail), set(RAIL_METAL)),
        # Main line east-west, the branch a curve in from the north swinging east, the way the
        # block is drawn: a switch, not two straights butted into a T. Rails only, as the model
        # does it - the branch shares the main line's sleepers.
        "tee_rail": overlay(rotate90(rail), flip(metal(corner)), set(RAIL_METAL)),
    }
    for name, pixels in textures.items():
        write_png(os.path.join(OUT_BLOCK, name + ".png"), pixels)
    for name in ("wooden_rail", "copper_powered_rail", "cross_rail", "tee_rail"):
        write_png(os.path.join(OUT_ITEM, name + ".png"), textures[name])

    # The working carts: the block in a cart, the way the furnace cart is drawn.
    cart = vanilla("item/minecart.png")
    for name, front in (("dropper_minecart", "block/dropper_front.png"), ("dispenser_minecart", "block/dispenser_front.png")):
        write_png(os.path.join(OUT_ITEM, name + ".png"),
                  block_cart(cart, vanilla("block/furnace_top.png"), vanilla(front), vanilla("block/furnace_side.png")))


if __name__ == "__main__":
    build()
