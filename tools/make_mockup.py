#!/usr/bin/env python3
"""Composes docs/screenshot_mock.png (a static preview of the game screen) from the real sprites."""
import os, re, random
from PIL import Image, ImageDraw
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SP = os.path.join(ROOT, 'app/src/main/assets/sprites')
src = open(os.path.join(ROOT, 'app/src/main/java/com/vetos26/galaxy/PixelFont.kt'), encoding='utf-8').read()
FONT = {}
for m in re.finditer(r"'(.|\\')' to arrayOf\(([^)]*)\)", src):
    ch = m.group(1).replace("\\'", "'")
    FONT[ch] = re.findall(r'"([.X]{5})"', m.group(2))
K = 3  # physical pixels per virtual pixel
VW, VH, CTRL = 360, 700, 128
img = Image.new('RGBA', (VW * K, VH * K), (0, 0, 0, 255))
d = ImageDraw.Draw(img)
def spr(name, x, y, size=32):
    s = Image.open(os.path.join(SP, name + '.png')).resize((int(size * K), int(size * K)), Image.NEAREST)
    img.alpha_composite(s, (int((x - size / 2) * K), int((y - size / 2) * K)))
def text(t, x, y, size, col, align='l'):
    w = (len(t) * 6 - 1) * size
    if align == 'c': x -= w / 2
    if align == 'r': x -= w
    for ch in t:
        g = FONT.get(ch)
        if g:
            for r, row in enumerate(g):
                for c, v in enumerate(row):
                    if v == 'X':
                        d.rectangle([(x + c * size) * K, (y + r * size) * K, (x + (c + 1) * size) * K - 1, (y + (r + 1) * size) * K - 1], fill=col)
        x += 6 * size
rnd = random.Random(3)
for _ in range(90):
    x, y = rnd.uniform(0, VW), rnd.uniform(0, VH - CTRL)
    d.rectangle([x * K, y * K, x * K + 4, y * K + 4], fill=rnd.choice([(255,255,255),(60,188,252),(248,216,0),(248,120,248),(120,232,96)]))
RED, WHITE = (248, 56, 0), (255, 255, 255)
text('1UP', 14, 6, 1.8, RED); text('004350', 14, 22, 1.8, WHITE)
text('HI-SCORE', 180, 6, 1.8, RED, 'c'); text('005000', 180, 22, 1.8, WHITE, 'c')
spr('btn_pause', 340, 44, 28)
form = [('enemy_flagship', 0, [2, 5]), ('enemy_red', 1, range(1, 7)), ('enemy_purple', 2, range(8)), ('enemy_blue', 3, range(8)), ('enemy_blue', 4, range(8))]
gone = {(4, 2), (3, 6), (2, 0), (4, 7), (1, 6)}
for name, row, cols in form:
    for c in cols:
        if (row, c) in gone: continue
        spr('%s_%d' % (name, 1 + (c % 2)), 180 + (c - 3.5) * 36 + 10, 62 + row * 30)
pb = VH - CTRL
img2 = Image.open(os.path.join(SP, 'enemy_blue_2.png')).resize((32 * K, 32 * K), Image.NEAREST).rotate(35)
img.alpha_composite(img2, (int(250 * K), int(330 * K)))
img2 = Image.open(os.path.join(SP, 'enemy_flagship_1.png')).resize((32 * K, 32 * K), Image.NEAREST).rotate(-20)
img.alpha_composite(img2, (int(90 * K), int(250 * K)))
spr('bullet_enemy', 275, 420); spr('bullet_enemy', 112, 330)
spr('explosion_2', 210, 150)
spr('player', 160, pb - 34); spr('bullet_player', 160, 380)
text('100', 210, 120, 1.6, (60, 188, 252), 'c')
for i in range(2): spr('player', 12 + i * 18, pb - 8, 16)
for i in range(3): spr('flag', 348 - i * 14, pb - 8, 16)
d.rectangle([0, pb * K, VW * K, VH * K], fill=(10, 10, 26))
d.rectangle([0, pb * K, VW * K, (pb + 2) * K], fill=(48, 64, 160))
cy = pb + CTRL / 2 + 2
spr('btn_left_on', 54, cy, 84); spr('btn_right', 150, cy, 84); spr('btn_fire_on', 290, cy, 96)
text('FIRE', 290, cy + 46, 1.2, (140, 140, 170), 'c')
img.convert('RGB').resize((VW * 2, VH * 2), Image.NEAREST).save(os.path.join(ROOT, 'docs/screenshot_mock.png'))
print('ok')
