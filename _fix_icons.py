"""把 AI 生成的神话装备原图抠图后撑满 32x32 画布（触及边框）。"""
from PIL import Image
import numpy as np
import glob
import os

BASE = r'c:\Users\ASUS\CodeBuddy\20260905194031'
ITEM = os.path.join(BASE, 'src', 'main', 'resources', 'assets',
                    'lolaccessories', 'textures', 'item')
CA = os.path.join(BASE, 'curseforge_assets')
SIZE = 32


def chroma_key(path):
    """去绿幕 + 去右下角水印，返回 RGBA。"""
    im = Image.open(path).convert('RGB')
    w, h = im.size
    a = np.array(im).astype(np.int16)
    a[h - 150:, w - 240:] = [0, 255, 0]          # 水印区填绿
    r, g, b = a[:, :, 0], a[:, :, 1], a[:, :, 2]
    green = (g > 110) & (g > r + 50) & (g > b + 50)
    out = np.zeros((h, w, 4), dtype=np.uint8)
    out[:, :, :3] = np.clip(a, 0, 255)
    out[:, :, 3] = np.where(green, 0, 255)
    return Image.fromarray(out, 'RGBA')


def fill_canvas(img, n=SIZE):
    """取内容 bbox 等比放大到贴边，让图案触及四边。"""
    bb = img.getbbox()
    if bb is None:
        return img.resize((n, n), Image.NEAREST)
    crop = img.crop(bb)
    w, h = crop.size
    scale = min(n / w, n / h)
    nw, nh = max(1, round(w * scale)), max(1, round(h * scale))
    out = Image.new('RGBA', (n, n), (0, 0, 0, 0))
    out.alpha_composite(crop.resize((nw, nh), Image.LANCZOS),
                        ((n - nw) // 2, (n - nh) // 2))
    arr = np.array(out)
    al = arr[:, :, 3]
    al[:] = np.where(al > 110, 255, 0)            # alpha 二值化，保像素感
    return Image.fromarray(arr, 'RGBA')


def dominant_hue(img):
    """判断主色调：返回 'cyan' 偏青 或 'red' 偏红。"""
    arr = np.array(img).astype(int)
    mask = arr[:, :, 3] > 0
    if not mask.any():
        return 'unknown'
    r = arr[:, :, 0][mask].mean()
    g = arr[:, :, 1][mask].mean()
    b = arr[:, :, 2][mask].mean()
    return 'red' if r > b else 'cyan'


def measure(img):
    px = img.load()
    xs, ys, n = [], [], 0
    for y in range(SIZE):
        for x in range(SIZE):
            if px[x, y][3] > 0:
                xs.append(x)
                ys.append(y)
                n += 1
    if not xs:
        return 'empty'
    return ('content %dx%d  x%d-%d y%d-%d  px%d'
            % (max(xs) - min(xs) + 1, max(ys) - min(ys) + 1,
               min(xs), max(xs), min(ys), max(ys), n))


def main():
    srcs = sorted(glob.glob(os.path.join(CA, 'A_League_of_Legends_style_epic_*.png')))
    print('found %d source images' % len(srcs))
    results = []
    for s in srcs:
        img = chroma_key(s)
        hue = dominant_hue(img)
        # 澄空之愿偏青，魔王之心偏红
        name = 'clear_skys_wish' if hue == 'cyan' else 'demon_heart'
        icon = fill_canvas(img)
        icon.save(os.path.join(ITEM, name + '.png'))
        # 放大预览
        S = 8
        bg = Image.new('RGBA', (SIZE * S, SIZE * S))
        for y in range(SIZE * S):
            for x in range(SIZE * S):
                bg.putpixel((x, y),
                            (58, 58, 58, 255) if ((x // S) + (y // S)) % 2 == 0
                            else (38, 38, 38, 255))
        bg.alpha_composite(icon.resize((SIZE * S, SIZE * S), Image.NEAREST))
        bg.convert('RGB').save(os.path.join(CA, 'preview_' + name + '.png'))
        results.append('%-18s hue=%-5s %s' % (name, hue, measure(icon)))
        print('  %s <- %s' % (name, os.path.basename(s)))
    print()
    for r in results:
        print(' ', r)


if __name__ == '__main__':
    main()
