#!/usr/bin/env python3
# -*- coding: utf-8 -*-
"""Generates every graphic and sound asset of GALAXY.

    pip install pillow
    python3 tools/make_assets.py

Outputs:
  app/src/main/assets/sprites/*.png   32x32 game sprites
  app/src/main/assets/logo.png        title logo
  app/src/main/res/mipmap-*/          launcher icons
  app/src/main/res/raw/*.wav          8-bit style sound effects
  docs/sprites_preview.png            enlarged sprite sheet for README
"""
import math
import os
import random
import struct
import wave

from PIL import Image, ImageDraw

import sprites_art as art

ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SPRITES = os.path.join(ROOT, 'app/src/main/assets/sprites')
ASSETS = os.path.join(ROOT, 'app/src/main/assets')
RES = os.path.join(ROOT, 'app/src/main/res')
DOCS = os.path.join(ROOT, 'docs')
S = 32

for d in (SPRITES, ASSETS, DOCS, os.path.join(RES, 'raw')):
    os.makedirs(d, exist_ok=True)


# ------------------------------------------------------------------ helpers
def mirror(half):
    return [row + row[::-1] for row in half]


def from_rows(rows, pal=art.PALETTE, center=True):
    h, w = len(rows), len(rows[0])
    img = Image.new('RGBA', (w, h), (0, 0, 0, 0))
    px = img.load()
    for y, row in enumerate(rows):
        assert len(row) == w, (y, row, len(row))
        for x, ch in enumerate(row):
            c = pal[ch]
            if c is not None:
                px[x, y] = c + (255,)
    if center:
        img = center_vert(img)
    return img


def center_vert(img):
    bbox = img.getbbox()
    if not bbox:
        return img
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    content = img.crop((0, bbox[1], img.width, bbox[3]))
    top = (img.height - content.height) // 2
    out.paste(content, (0, top))
    return out


def add_outline(img, color=(0, 0, 0, 255)):
    """1px dark outline around opaque pixels -> reads well on the starfield."""
    w, h = img.size
    src = img.load()
    out = Image.new('RGBA', img.size, (0, 0, 0, 0))
    o = out.load()
    for y in range(h):
        for x in range(w):
            if src[x, y][3] == 0:
                for dx, dy in ((1, 0), (-1, 0), (0, 1), (0, -1)):
                    nx, ny = x + dx, y + dy
                    if 0 <= nx < w and 0 <= ny < h and src[nx, ny][3]:
                        o[x, y] = color
                        break
            else:
                o[x, y] = src[x, y]
    return out


def save(img, name):
    assert img.size == (S, S), (name, img.size)
    img.save(os.path.join(SPRITES, name + '.png'))
    return img


def flip_v(img):
    return img.transpose(Image.FLIP_TOP_BOTTOM)


# ------------------------------------------------------------------ sprites
made = {}

made['player'] = save(add_outline(from_rows(mirror(art.PLAYER))), 'player')

ENEMY_COLORS = {
    #          body  dark  wing wing_dark
    'blue':   ('P', 'p', 'C', 'B'),
    'purple': ('P', 'p', 'M', 'P'),
    'red':    ('R', 'r', 'C', 'B'),
}
for name, (body, dark, wing, wdark) in ENEMY_COLORS.items():
    up, down = art.drone(body, dark, wing, wdark)
    if name == 'purple':
        # purple aliens: magenta wings, yellow body
        up, down = art.drone('M', 'p', 'P', 'p', eye='W')
    if name == 'red':
        up, down = art.drone('R', 'r', 'Y', 'O', eye='W')
    if name == 'blue':
        up, down = art.drone('B', 'b', 'C', 'c', eye='Y')
    # enemies look DOWN at the player -> flip vertically
    made['enemy_%s_1' % name] = save(add_outline(flip_v(from_rows(mirror(up)))), 'enemy_%s_1' % name)
    made['enemy_%s_2' % name] = save(add_outline(flip_v(from_rows(mirror(down)))), 'enemy_%s_2' % name)

made['enemy_flagship_1'] = save(add_outline(flip_v(from_rows(mirror(art.FLAG_1)))), 'enemy_flagship_1')
made['enemy_flagship_2'] = save(add_outline(flip_v(from_rows(mirror(art.FLAG_2)))), 'enemy_flagship_2')


# bullets ------------------------------------------------------------------
def bullet(colors):
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    px = img.load()
    n = len(colors)
    top = (S - n) // 2
    for i, c in enumerate(colors):
        if c:
            px[15, top + i] = c + (255,)
            px[16, top + i] = c + (255,)
    return img


P = art.PALETTE
made['bullet_player'] = save(bullet([P['W'], P['y'], P['Y'], P['Y'], P['Y'], P['Y'], P['O'], P['O'], P['R'], P['r']]), 'bullet_player')
eb = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(eb)
d.rectangle([15, 12, 16, 19], fill=P['W'] + (255,))
d.rectangle([15, 18, 16, 20], fill=P['Y'] + (255,))
d.point([(14, 14), (17, 14), (14, 17), (17, 17)], fill=P['R'] + (255,))
made['bullet_enemy'] = save(eb, 'bullet_enemy')


# explosions ---------------------------------------------------------------
def explosion(frame, colors, seed, big=False):
    rnd = random.Random(seed * 100 + frame)
    img = Image.new('RGBA', (S, S), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    cx = cy = 15.5
    radius = [4, 8, 11, 14][frame]
    count = [26, 46, 56, 40][frame]
    if frame == 0:
        d.ellipse([cx - 4, cy - 4, cx + 4, cy + 4], fill=colors[0] + (255,))
    if frame == 1:
        d.ellipse([cx - 5, cy - 5, cx + 5, cy + 5], fill=colors[1] + (255,))
        d.ellipse([cx - 3, cy - 3, cx + 3, cy + 3], fill=colors[0] + (255,))
    for i in range(count):
        a = rnd.random() * math.tau
        r = radius * (0.35 + 0.65 * rnd.random())
        x = int(round(cx + math.cos(a) * r))
        y = int(round(cy + math.sin(a) * r))
        col = colors[min(len(colors) - 1, int(rnd.random() * len(colors) * (0.4 + frame * 0.2)))]
        sz = 2 if (frame < 2 and rnd.random() < 0.5) or (big and rnd.random() < 0.35) else 1
        d.rectangle([x, y, x + sz - 1, y + sz - 1], fill=col + (255,))
    # rays
    for k in range(8):
        a = k * math.tau / 8 + 0.2 * frame
        r0, r1 = radius * 0.6, radius
        for t in range(4):
            r = r0 + (r1 - r0) * t / 3
            x = int(round(cx + math.cos(a) * r))
            y = int(round(cy + math.sin(a) * r))
            if 0 <= x < S and 0 <= y < S and frame in (1, 2):
                img.putpixel((x, y), colors[0] + (255,))
    return img


ENEMY_BOOM = [P['W'], P['Y'], P['O'], P['R'], P['r']]
PLAYER_BOOM = [P['W'], P['c'], P['C'], P['R'], P['B'], P['Y']]
for f in range(4):
    made['explosion_%d' % (f + 1)] = save(explosion(f, ENEMY_BOOM, 7), 'explosion_%d' % (f + 1))
    made['explosion_player_%d' % (f + 1)] = save(explosion(f, PLAYER_BOOM, 3, big=True), 'explosion_player_%d' % (f + 1))


# HUD icons ----------------------------------------------------------------
flag = from_rows([
    "................................",
    "................................",
    "................................",
    "................................",
    "........RRRRRRRRRRRRRR..........",
    "........RRRRRRRRRRRRRRRR........",
    "........RRRRRRRRRRRRRRRRRR......",
    "........RRRRRRRRRRRRRRRRRRRR....",
    "........RRRRRRRRRRRRRRRRRRRRRR..",
    "........RRRRRRRRRRRRRRRRRRRR....",
    "........RRRRRRRRRRRRRRRRRR......",
    "........RRRRRRRRRRRRRRRR........",
    "........RRRRRRRRRRRRRR..........",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "........YY......................",
    "......YYYYYY....................",
    "....YYYYYYYYYY..................",
    "................................",
    "................................",
], center=False)
made['flag'] = save(add_outline(flag), 'flag')


def button(kind, pressed):
    big = 4
    W = S * big
    img = Image.new('RGBA', (W, W), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if kind == 'fire':
        rim, face, hi, sym = ((120, 0, 0), (232, 40, 24), (255, 140, 110), (255, 255, 255))
        if pressed:
            rim, face, hi, sym = ((160, 30, 0), (255, 120, 40), (255, 220, 120), (255, 255, 160))
    else:
        rim, face, hi, sym = ((30, 40, 90), (60, 80, 170), (130, 160, 240), (255, 255, 255))
        if pressed:
            rim, face, hi, sym = ((40, 90, 180), (90, 170, 255), (190, 230, 255), (255, 255, 160))
    m = 1 * big
    d.ellipse([m, m, W - m - 1, W - m - 1], fill=(10, 10, 20, 255))
    m2 = 2 * big
    d.ellipse([m2, m2, W - m2 - 1, W - m2 - 1], fill=rim + (255,))
    off = big if pressed else 0
    m3 = 4 * big
    d.ellipse([m3, m3 - big + off, W - m3 - 1, W - m3 - 1 - big + off], fill=face + (255,))
    # highlight arc
    d.arc([m3 + big, m3 + off, W - m3 - big - 1, W - m3 - big - 1 + off], 200, 290, fill=hi + (255,), width=big * 2)
    c = W / 2
    cy = c - big + off
    if kind == 'left':
        d.polygon([(c - 7 * big, cy), (c + 4 * big, cy - 7 * big), (c + 4 * big, cy + 7 * big)], fill=sym + (255,))
    elif kind == 'right':
        d.polygon([(c + 7 * big, cy), (c - 4 * big, cy - 7 * big), (c - 4 * big, cy + 7 * big)], fill=sym + (255,))
    elif kind == 'fire':
        # little missile icon
        d.rectangle([c - 1.5 * big, cy - 7 * big, c + 1.5 * big, cy + 4 * big], fill=sym + (255,))
        d.polygon([(c - 1.5 * big, cy - 7 * big), (c, cy - 9 * big), (c + 1.5 * big, cy - 7 * big)], fill=sym + (255,))
        d.polygon([(c - 4 * big, cy + 6 * big), (c - 1.5 * big, cy + 1 * big), (c - 1.5 * big, cy + 5 * big)], fill=sym + (255,))
        d.polygon([(c + 4 * big, cy + 6 * big), (c + 1.5 * big, cy + 1 * big), (c + 1.5 * big, cy + 5 * big)], fill=sym + (255,))
    small = img.resize((S, S), Image.NEAREST)
    return small


for k in ('left', 'right', 'fire'):
    made['btn_%s' % k] = save(button(k, False), 'btn_%s' % k)
    made['btn_%s_on' % k] = save(button(k, True), 'btn_%s_on' % k)

pause = Image.new('RGBA', (S, S), (0, 0, 0, 0))
d = ImageDraw.Draw(pause)
d.rounded_rectangle([2, 2, 29, 29], radius=6, fill=(20, 24, 60, 200), outline=(90, 110, 220, 255), width=2)
d.rectangle([10, 9, 13, 22], fill=(255, 255, 255, 255))
d.rectangle([18, 9, 21, 22], fill=(255, 255, 255, 255))
made['btn_pause'] = save(pause, 'btn_pause')


# --------------------------------------------------------------------- logo
FONT_BIG = {
    'G': [".XXXXX.", "XX...XX", "XX.....", "XX.XXXX", "XX...XX", "XX...XX", ".XXXXX."],
    'A': ["..XXX..", ".XX.XX.", "XX...XX", "XX...XX", "XXXXXXX", "XX...XX", "XX...XX"],
    'L': ["XX.....", "XX.....", "XX.....", "XX.....", "XX.....", "XX.....", "XXXXXXX"],
    'X': ["XX...XX", ".XX.XX.", "..XXX..", "..XXX..", "..XXX..", ".XX.XX.", "XX...XX"],
    'Y': ["XX...XX", "XX...XX", ".XX.XX.", "..XXX..", "...X...", "...X...", "...X..."],
}
FONT_BIG['Y'] = ["XX...XX", "XX...XX", ".XX.XX.", "..XXX..", "..XX...", "..XX...", "..XX..."]
FONT_BIG['Y'] = ["XX...XX", "XX...XX", ".XX.XX.", "..XXX..", "..XXX..", "..XXX..", "..XXX.."]


def make_logo():
    word = "GALAXY"
    block = 6
    lw, gap = 7 * block, block
    W, H = 320, 84
    img = Image.new('RGBA', (W, H), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    total = len(word) * lw + (len(word) - 1) * gap
    x0 = (W - total) // 2
    y0 = 12
    grad = [(255, 248, 140), (255, 220, 40), (255, 180, 0), (255, 130, 0), (240, 80, 0), (220, 40, 0), (170, 10, 0)]
    # shadow / 3D extrude
    for depth in range(4, 0, -1):
        col = (40, 30, 160) if depth > 2 else (90, 70, 230)
        for i, ch in enumerate(word):
            gx = x0 + i * (lw + gap)
            for r, row in enumerate(FONT_BIG[ch]):
                for c, v in enumerate(row):
                    if v == 'X':
                        x = gx + c * block + depth
                        y = y0 + r * block + depth
                        d.rectangle([x, y, x + block - 1, y + block - 1], fill=col + (255,))
    for i, ch in enumerate(word):
        gx = x0 + i * (lw + gap)
        for r, row in enumerate(FONT_BIG[ch]):
            for c, v in enumerate(row):
                if v == 'X':
                    x = gx + c * block
                    y = y0 + r * block
                    d.rectangle([x, y, x + block - 1, y + block - 1], fill=grad[r] + (255,))
                    # tiny highlight pixel per block
                    d.point([(x + 1, y + 1)], fill=(255, 255, 230, 255))
    # stripes under the title
    sy = y0 + 7 * block + 10
    for k, col in enumerate([(255, 60, 0), (255, 170, 0), (60, 188, 252)]):
        d.rectangle([x0, sy + k * 4, x0 + total, sy + k * 4 + 1], fill=col + (255,))
    # little sprites at both ends of the stripes
    ship = made['player']
    flag = made['enemy_flagship_1']
    img.alpha_composite(flag, (x0 - 34 if x0 > 34 else 0, sy - 12))
    img.alpha_composite(ship, (min(W - 32, x0 + total + 2), sy - 12))
    # sparkle stars
    rnd = random.Random(42)
    for _ in range(14):
        x, y = rnd.randrange(W), rnd.randrange(0, 10)
        d.point([(x, y)], fill=rnd.choice([(255, 255, 255), (60, 188, 252), (248, 216, 0)]) + (255,))
    return img


logo = make_logo()
logo.save(os.path.join(ASSETS, 'logo.png'))


# ------------------------------------------------------------ launcher icon
def make_icon(size, adaptive=False):
    base = 108 if adaptive else 48
    img = Image.new('RGBA', (base * 4, base * 4), (0, 0, 0, 0))
    d = ImageDraw.Draw(img)
    if not adaptive:
        d.rounded_rectangle([4, 4, base * 4 - 5, base * 4 - 5], radius=40, fill=(8, 10, 40, 255))
        rnd = random.Random(5)
        for _ in range(40):
            x, y = rnd.randrange(12, base * 4 - 12), rnd.randrange(12, base * 4 - 12)
            d.rectangle([x, y, x + 3, y + 3], fill=rnd.choice([(255, 255, 255), (60, 188, 252), (248, 216, 0), (248, 120, 248)]) + (255,))
    # sprites, scaled up with nearest neighbour
    content = 72 if adaptive else 44
    unit = content * 4 // 2  # two sprites stacked
    fs = made['enemy_flagship_1'].resize((unit, unit), Image.NEAREST)
    ps = made['player'].resize((unit, unit), Image.NEAREST)
    cx = base * 4 // 2
    top = (base * 4 - content * 4) // 2
    img.alpha_composite(fs, (cx - unit // 2, top))
    img.alpha_composite(ps, (cx - unit // 2, top + unit))
    return img.resize((size, size), Image.NEAREST)


DENS = {'mdpi': 1, 'hdpi': 1.5, 'xhdpi': 2, 'xxhdpi': 3, 'xxxhdpi': 4}
for name, k in DENS.items():
    dd = os.path.join(RES, 'mipmap-' + name)
    os.makedirs(dd, exist_ok=True)
    make_icon(int(48 * k)).save(os.path.join(dd, 'ic_launcher.png'))
    make_icon(int(108 * k), adaptive=True).save(os.path.join(dd, 'ic_launcher_foreground.png'))
make_icon(512).save(os.path.join(DOCS, 'icon.png'))


# ------------------------------------------------------------------- sounds
RATE = 22050


def write_wav(name, samples):
    path = os.path.join(RES, 'raw', name + '.wav')
    with wave.open(path, 'wb') as w:
        w.setnchannels(1)
        w.setsampwidth(2)
        w.setframerate(RATE)
        w.writeframes(b''.join(struct.pack('<h', int(max(-1, min(1, s)) * 30000)) for s in samples))


def square(freq_fn, dur, vol_fn, duty=0.5):
    out, phase = [], 0.0
    n = int(RATE * dur)
    for i in range(n):
        t = i / RATE
        phase = (phase + freq_fn(t) / RATE) % 1.0
        out.append((1 if phase < duty else -1) * vol_fn(t))
    return out


def noise(dur, vol_fn, step_fn=lambda t: 4):
    rnd = random.Random(1)
    out, v, cnt = [], 0.0, 0
    for i in range(int(RATE * dur)):
        t = i / RATE
        if cnt <= 0:
            v = rnd.choice((-1, 1))
            cnt = step_fn(t)
        cnt -= 1
        out.append(v * vol_fn(t))
    return out


def mix(*tracks):
    n = max(len(t) for t in tracks)
    return [sum(t[i] for t in tracks if i < len(t)) for i in range(n)]


write_wav('sfx_shoot', square(lambda t: 1400 - 5500 * t, 0.16, lambda t: 0.45 * (1 - t / 0.16), duty=0.25))
write_wav('sfx_hit', noise(0.32, lambda t: 0.55 * (1 - t / 0.32) ** 2, lambda t: 2 + int(t * 40)))
write_wav('sfx_die', mix(noise(1.1, lambda t: 0.6 * (1 - t / 1.1) ** 1.5, lambda t: 3 + int(t * 30)),
                         square(lambda t: 600 - 400 * t, 1.0, lambda t: 0.18 * (1 - t), duty=0.5)))
write_wav('sfx_dive', square(lambda t: 1800 - 1300 * (t / 0.7) + 60 * math.sin(t * 60), 0.7,
                             lambda t: 0.22 * min(1, t * 20) * (1 - t / 0.7), duty=0.5))


def melody(notes, bpm_step=0.11, duty=0.5, vol=0.3):
    out = []
    for f, steps in notes:
        dur = steps * bpm_step
        if f == 0:
            out += [0.0] * int(RATE * dur)
        else:
            out += square(lambda t, f=f: f, dur, lambda t, dur=dur: vol * (1 - 0.6 * t / dur), duty)
    return out


N = {'C5': 523, 'D5': 587, 'E5': 659, 'F5': 698, 'G5': 784, 'A5': 880, 'B5': 988, 'C6': 1047, 'E6': 1319, 'G6': 1568,
     'G4': 392, 'C4': 262, 'E4': 330, 'A4': 440}
start = [(N['C5'], 1), (N['E5'], 1), (N['G5'], 1), (N['C6'], 2), (0, 1), (N['G5'], 1), (N['C6'], 1), (N['E6'], 3), (0, 1),
         (N['C6'], 1), (N['E6'], 1), (N['G6'], 4)]
bass = [(N['C4'], 4), (N['G4'], 4), (N['C4'], 4), (N['E4'], 2), (N['G4'], 4)]
write_wav('sfx_start', mix(melody(start, vol=0.25, duty=0.25), melody(bass, vol=0.15, duty=0.5)))
write_wav('sfx_extra', melody([(N['C6'], 1), (N['E6'], 1), (N['G6'], 1), (N['E6'], 1), (N['G6'], 3)], 0.07, 0.25, 0.25))
write_wav('sfx_clear', melody([(N['G5'], 1), (N['A5'], 1), (N['B5'], 1), (N['C6'], 1), (N['E6'], 1), (N['G6'], 4)], 0.09, 0.5, 0.22))


# ------------------------------------------------------------ preview sheet
names = sorted(made)
cols = 8
scale = 4
pad = 8
rows = (len(names) + cols - 1) // cols
sheet = Image.new('RGBA', (cols * (S * scale + pad) + pad, rows * (S * scale + pad) + pad), (12, 12, 28, 255))
for i, n in enumerate(names):
    x = pad + (i % cols) * (S * scale + pad)
    y = pad + (i // cols) * (S * scale + pad)
    ImageDraw.Draw(sheet).rectangle([x, y, x + S * scale - 1, y + S * scale - 1], fill=(0, 0, 0, 255))
    sheet.alpha_composite(made[n].resize((S * scale, S * scale), Image.NEAREST), (x, y))
sheet.save(os.path.join(DOCS, 'sprites_preview.png'))
logo_big = Image.new('RGBA', (logo.width * 3, logo.height * 3), (0, 0, 0, 255))
logo_big.alpha_composite(logo.resize((logo.width * 3, logo.height * 3), Image.NEAREST))
logo_big.save(os.path.join(DOCS, 'logo_preview.png'))

print('sprites:', len(made))
for n in names:
    print('  ', n)
