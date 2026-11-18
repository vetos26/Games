#!/usr/bin/env python3
"""Static previews of the menu screens (docs/menu_*.png), using the real sprites and pixel font."""
import os, re, random
from PIL import Image, ImageDraw
ROOT = os.path.abspath(os.path.join(os.path.dirname(__file__), '..'))
SP = os.path.join(ROOT, 'app/src/main/assets/sprites')
src = open(os.path.join(ROOT, 'app/src/main/java/com/vetos26/galaxy/PixelFont.kt'), encoding='utf-8').read()
FONT = {m.group(1).replace("\\'", "'"): re.findall(r'"([.X]{5})"', m.group(2))
        for m in re.finditer(r"'(.|\\')' to arrayOf\(([^)]*)\)", src)}
for a, b in re.findall(r"'(.)' to '(.)'", src):
    FONT[a] = FONT[b]
K, VW, VH = 3, 360, 700
YEL, RED, CY, PINK, GREY, WHITE = (248,216,0), (248,56,0), (60,188,252), (248,120,248), (140,140,170), (255,255,255)

class Screen:
    def __init__(s):
        s.img = Image.new('RGBA', (VW*K, VH*K), (0,0,0,255)); s.d = ImageDraw.Draw(s.img)
        rnd = random.Random(7)
        for _ in range(90):
            x, y = rnd.uniform(0, VW), rnd.uniform(0, VH)
            s.d.rectangle([x*K, y*K, x*K+4, y*K+4], fill=rnd.choice([WHITE,CY,YEL,PINK,(120,232,96)]))
    def spr(s, name, x, y, size=32, path=None):
        im = Image.open(path or os.path.join(SP, name + '.png'))
        if path is None: im = im.resize((int(size*K), int(size*K)), Image.NEAREST)
        else: im = im.resize((im.width*K, im.height*K), Image.NEAREST)
        s.img.alpha_composite(im, (int(x*K - im.width/2), int(y*K - im.height/2)))
    def text(s, t, x, y, size, col, align='l', shadow=False):
        if shadow: s.text(t, x+size, y+size, size, (20,20,60), align)
        w = (len(t)*6-1)*size
        x -= w/2 if align == 'c' else (w if align == 'r' else 0)
        for ch in t.upper():
            for r, row in enumerate(FONT.get(ch, [])):
                for c, v in enumerate(row):
                    if v == 'X':
                        s.d.rectangle([(x+c*size)*K, (y+r*size)*K, (x+(c+1)*size)*K-1, (y+(r+1)*size)*K-1], fill=col)
            x += 6*size
    def box(s, cy, w, h, sel):
        x0, x1 = VW/2-w/2, VW/2+w/2
        s.d.rectangle([x0*K, (cy-h/2)*K, x1*K, (cy+h/2)*K], fill=YEL if sel else (48,64,160))
        s.d.rectangle([(x0+2)*K, (cy-h/2+2)*K, (x1-2)*K, (cy+h/2-2)*K], fill=(40,38,40) if sel else (14,16,44))
    def save(s, name):
        s.img.convert('RGB').resize((VW*2, VH*2), Image.NEAREST).save(os.path.join(ROOT, 'docs', name))

top, bottom = 0, VH
# main menu
s = Screen()
s.text('HI-SCORE', 180, 6, 1.8, RED, 'c'); s.text('012340', 180, 22, 1.8, WHITE, 'c')
items = ['НОВАЯ ИГРА', 'НАСТРОЙКИ', 'ТАБЛИЦА ОЧКОВ', 'АВТОРЫ']
bt = 52 + max(0, (bottom - 30) - 52 - (84 + 46 + len(items) * 50)) * 0.4
s.spr(None, 180, bt + 42, path=os.path.join(ROOT, 'app/src/main/assets/logo.png'))
s.text('DENDY STYLE SPACE SHOOTER', 180, bt + 84 + 10, 1.4, CY, 'c', True)
it = bt + 84 + 46
for i, t in enumerate(items):
    cy = it + i*50; s.box(cy, 240, 40, i == 2)
    s.text(t, 180, cy-7, 2.2, YEL if i == 2 else WHITE, 'c')
    if i == 2:
        s.spr('enemy_flagship_1', 180-120-20, cy, 28); s.spr('enemy_flagship_1', 180+120+20, cy, 28)
s.text('(C) 2026 VETOS26   V1.0.7', 180, bottom-16, 1.2, GREY, 'c')
s.save('menu_main.png')
# score table
s = Screen()
s.text('ТАБЛИЦА ОЧКОВ', 180, 22, 3.2, YEL, 'c', True)
backTop = bottom - 40 - 20
y = 66
s.text('- ЛУЧШИЕ РЕЗУЛЬТАТЫ -', 180, y, 1.6, PINK, 'c', True); y += 18
s.text('МЕСТО', 30, y, 1.2, GREY, 'c'); s.text('ОЧКИ', 150, y, 1.2, GREY, 'r')
s.text('ЭТАП', 204, y, 1.2, GREY, 'c'); s.text('ДАТА', 330, y, 1.2, GREY, 'r'); y += 14
rowH = max(13, min(19, (backTop - y - 160) / 10)); size = 1.8 if rowH >= 16 else 1.5
data = [(12340, 4, '18.11.26'), (9870, 3, '18.11.26'), (7420, 3, '17.11.26'), (5110, 2, '17.11.26'), (2860, 1, '16.11.26')]
for i in range(10):
    e = data[i] if i < len(data) else None
    last = i == 2
    col = YEL if (i == 0 or last) else (WHITE if e else (70, 70, 100))
    if last:
        s.d.rectangle([14*K, (y-2)*K, 346*K, (y+7*size+2)*K], fill=(70, 62, 20))
    s.text('%d.' % (i+1), 44, y, size, CY if e else col, 'r')
    if e:
        s.text('%06d' % e[0], 150, y, size, col, 'r'); s.text(str(e[1]), 204, y, size, col, 'c'); s.text(e[2], 330, y, size, col, 'r')
    else:
        s.text('------', 150, y, size, col, 'r'); s.text('-', 204, y, size, col, 'c'); s.text('--.--.--', 330, y, size, col, 'r')
    y += rowH
y += 20
s.text('- ОЧКИ ЗА ПРИШЕЛЬЦЕВ -', 180, y, 1.6, PINK, 'c', True); y += 16
s.text('В СТРОЮ', 190, y, 1.4, GREY, 'c'); s.text('В АТАКЕ', 290, y, 1.4, GREY, 'c')
ar = max(24, min(36, (backTop - 10 - y - 12) / 4)); y += 12 + ar/2
for n, a_, b_ in [('flagship','60','150-800'),('red','50','100'),('purple','40','80'),('blue','30','60')]:
    s.spr('enemy_%s_1' % n, 86, y, min(32, ar)); s.text(a_, 190, y-6, 1.8, YEL, 'c'); s.text(b_, 290, y-6, 1.8, YEL, 'c'); y += ar
s.box(bottom-40, 240, 40, True); s.text('НАЗАД', 180, bottom-47, 2.2, YEL, 'c')
s.spr('enemy_flagship_1', 40, bottom-40, 28); s.spr('enemy_flagship_1', 320, bottom-40, 28)
s.save('menu_scores.png')
# settings
s = Screen()
s.text('НАСТРОЙКИ', 180, 22, 3.2, YEL, 'c', True)
rows = [('ЗВУК','ВКЛ'),('ВИБРАЦИЯ','ВКЛ'),('АВТООГОНЬ','ВЫКЛ'),('УПРАВЛЕНИЕ','КНОПКИ'),('КНОПКА ОГНЯ','СПРАВА'),
        ('РАЗМЕР КНОПОК','СРЕДНИЕ'),('СЛОЖНОСТЬ','НОРМА'),('СБРОС РЕКОРДОВ','12340'),('НАЗАД','')]
step = min(42, (bottom-64-34)/9)
for i, (l, v) in enumerate(rows):
    cy = 64 + i*step + step/2; sel = i == 3
    s.box(cy, 332, step-6, sel)
    if l == 'НАЗАД': s.text(l, 180, cy-6.3, 1.8, WHITE, 'c')
    else:
        s.text(l, 180-166+10, cy-6.3, 1.8, YEL if sel else WHITE)
        s.text(v, 180+166-10, cy-6.3, 1.8, GREY if v == 'ВЫКЛ' else CY, 'r')
s.text('НАЖМИТЕ НА ПУНКТ, ЧТОБЫ ИЗМЕНИТЬ', 180, bottom-16, 1.2, GREY, 'c')
s.save('menu_settings.png')
# credits
s = Screen()
s.text('АВТОРЫ', 180, 22, 3.2, YEL, 'c', True)
mk = re.search(r'private val credits = listOf\((.*?)\n    \)', open(os.path.join(ROOT, 'app/src/main/java/com/vetos26/galaxy/Menu.kt'), encoding='utf-8').read(), re.S).group(1)
y = 70
for t, st in re.findall(r'"([^"]*)" to (\d)', mk):
    if st == '0': y += 6; s.text(t, 180, y, 1.5, CY, 'c'); y += 14
    elif st == '1': s.text(t, 180, y, 2, WHITE, 'c', True); y += 18
    else: y += 14; s.text(t, 180, y, 2.4, YEL, 'c', True); y += 22
py = y + 34
for i, n in enumerate(['player', 'enemy_blue_1', 'enemy_purple_2', 'enemy_red_1', 'enemy_flagship_2']):
    s.spr(n, 300 - i*40, py, 28 if i == 0 else 26)
s.box(bottom-40, 240, 40, True); s.text('НАЗАД', 180, bottom-47, 2.2, YEL, 'c')
s.spr('enemy_flagship_1', 40, bottom-40, 28); s.spr('enemy_flagship_1', 320, bottom-40, 28)
s.save('menu_credits.png')
# combined preview
ims = [Image.open(os.path.join(ROOT, 'docs', n)) for n in ('menu_main.png', 'menu_settings.png', 'menu_scores.png', 'menu_credits.png')]
w = sum(i.width for i in ims) + 20 * (len(ims) - 1)
out = Image.new('RGB', (w, ims[0].height), (30, 30, 50)); x = 0
for i in ims: out.paste(i, (x, 0)); x += i.width + 20
out.resize((w//2, ims[0].height//2), Image.NEAREST).save(os.path.join(ROOT, 'docs', 'menu_preview.png'))
print('ok')
