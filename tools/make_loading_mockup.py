#!/usr/bin/env python3
"""Static preview of the loading screen (docs/loading_mock.png) for a tall and a short screen."""
import os, re, random
from PIL import Image, ImageDraw
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SRC = os.path.join(ROOT, 'app/src/main/java/com/vetos26/galaxy')
font_src = open(os.path.join(SRC, 'PixelFont.kt'), encoding='utf-8').read()
FONT = {m.group(1).replace("\\'", "'"): re.findall(r'"([.X]{5})"', m.group(2))
        for m in re.finditer(r"'(.|\\')' to arrayOf\(([^)]*)\)", font_src)}
for a, b in re.findall(r"'(.)' to '(.)'", font_src):
    FONT[a] = FONT[b]
CREDITS = re.findall(r'"([^"]*)" to Style\.(\w+)', open(os.path.join(SRC, 'Credits.kt'), encoding='utf-8').read())
K, VW = 3, 360
YEL, RED, CY, GREY, WHITE, ORANGE = (248,216,0), (248,56,0), (60,188,252), (140,140,170), (255,255,255), (248,152,40)

def render(vh, progress=0.64):
    img = Image.new('RGBA', (VW*K, int(vh*K)), (0,0,0,255)); d = ImageDraw.Draw(img)
    rnd = random.Random(4)
    for _ in range(90):
        x, y = rnd.uniform(0, VW), rnd.uniform(0, vh)
        d.rectangle([x*K, y*K, x*K+4, y*K+4], fill=rnd.choice([WHITE,CY,YEL,(248,120,248)]))
    def text(t, x, y, size, col, align='l', shadow=False):
        if shadow: text(t, x+size, y+size, size, (20,20,60), align)
        w = (len(t)*6-1)*size
        x -= w/2 if align == 'c' else (w if align == 'r' else 0)
        for ch in t:
            for r, row in enumerate(FONT.get(ch) or FONT.get(ch.upper(), [])):
                for c, v in enumerate(row):
                    if v == 'X':
                        d.rectangle([round((x+c*size)*K), round((y+r*size)*K), round((x+(c+1)*size)*K)-1, round((y+(r+1)*size)*K)-1], fill=col)
            x += 6*size
    def wrap(t, size, maxw):
        out, cur = [], ''
        for w in t.split(' '):
            cand = w if not cur else cur + ' ' + w
            if cur and (len(cand)*6-1)*size > maxw: out.append(cur); cur = w
            else: cur = cand
        return out + ([cur] if cur else [])
    def credits(top, scale, draw=True):
        y, maxw = top, VW - 28
        for t, st in CREDITS:
            if st == 'TITLE':
                sz = 2.6*scale
                if draw: text(t, 180, y, sz, ORANGE, 'c', True)
                y += 7*sz + 12*scale
            elif st == 'LINE':
                sz = 1.6*scale
                for line in wrap(t, sz, maxw):
                    if draw: text(line, 180, y, sz, WHITE, 'c', True)
                    y += 8*sz + 3*scale
                y += 6*scale
            else:
                y += 4*scale; sz = min(2*scale, maxw/(len(t)*6-1))
                if draw: text(t, 180, y, sz, YEL, 'c', True)
                y += 8*sz
        return y - top
    logo = Image.open(os.path.join(ROOT, 'app/src/main/assets/logo.png'))
    cy = vh*0.36
    img.alpha_composite(logo.resize((logo.width*K, logo.height*K), Image.NEAREST), (int((180-logo.width/2)*K), int((cy-logo.height/2)*K)))
    text('DENDY STYLE SPACE SHOOTER', 180, cy+logo.height/2+14, 1.4, CY, 'c', True)
    bw, bx, by = 220, 70, vh*0.62
    d.rectangle([(bx-3)*K, (by-3)*K, (bx+bw+3)*K, (by+13)*K], fill=(60,70,160))
    d.rectangle([(bx-1)*K, (by-1)*K, (bx+bw+1)*K, (by+11)*K], fill=(0,0,0))
    for i in range(int(progress*22)):
        col = YEL if i < 11 else ((248,120,24) if i < 18 else RED); x = bx + i*bw/22
        d.rectangle([(x+1)*K, (by+1)*K, (x+bw/22-1)*K, (by+9)*K], fill=col)
    ship = Image.open(os.path.join(ROOT, 'app/src/main/assets/sprites/player.png')).resize((32*K, 32*K), Image.NEAREST)
    img.alpha_composite(ship, (int((bx+bw*progress-16)*K), int((by-22-16)*K)))
    text('ЗАГРУЗКА...', bx, by+22, 2, WHITE, shadow=True); text('%d%%' % (progress*100), bx+bw, by+22, 2, YEL, 'r', True)
    area_top, area_bottom = by + 46, vh - 50
    area = area_bottom - area_top
    s = max(0.55, min(1, area / credits(0, 1, False)))
    h = credits(0, s, False)
    if h > area:
        s = max(0.5, s*area/h); h = credits(0, s, False)
    credits(area_top + max(0, (area-h)/2), s)
    text('(C) 2026 HomeStudio', 180, vh-40, 1.5, GREY, 'c'); text('MADE WITH KOTLIN', 180, vh-24, 1.5, GREY, 'c')
    return img.convert('RGB').resize((VW*2, int(vh*2)), Image.NEAREST)

tall, short = render(800), render(560)
out = Image.new('RGB', (tall.width + short.width + 20, tall.height), (30,30,50))
out.paste(tall, (0, 0)); out.paste(short, (tall.width + 20, 0))
out.save(os.path.join(ROOT, 'docs/loading_mock.png'))
print('ok')
