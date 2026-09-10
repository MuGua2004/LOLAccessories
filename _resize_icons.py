"""在原设计不变的前提下，把神话装备图标内容适度调大，让格子显得更饱满。"""
from PIL import Image
import numpy as np
import os

ITEM = r'c:\Users\ASUS\CodeBuddy\20260905194031\src\main\resources\assets\lolaccessories\textures\item'

# 目标内容尺寸（32x32 画布内）：
# 澄空之愿 20x30 -> 24x32（横向调大 20%，纵向顶到 32，保持宝盒+宝石+翅膀造型）
# 魔王之心 28x29 -> 31x32（轻微放大 10%）
TARGETS = {
    'clear_skys_wish': (24, 32),
    'demon_heart': (31, 32),
}


def main():
    for name, (tw, th) in TARGETS.items():
        p = os.path.join(ITEM, name + '.png')
        im = Image.open(p).convert('RGBA')
        bb = im.getbbox()
        crop = im.crop(bb)
        w, h = crop.size
        tw2, th2 = min(tw, 32), min(th, 32)
        r = crop.resize((tw2, th2), Image.LANCZOS)
        out = Image.new('RGBA', (32, 32), (0, 0, 0, 0))
        out.alpha_composite(r, ((32 - tw2) // 2, (32 - th2) // 2))
        a = np.array(out)
        al = a[:, :, 3]
        al[:] = np.where(al > 110, 255, 0)          # alpha 二值化
        fixed = Image.fromarray(a, 'RGBA')
        fixed.save(p)

        # 测量
        px = fixed.load()
        xs, ys, n = [], [], 0
        for y in range(32):
            for x in range(32):
                if px[x, y][3] > 0:
                    xs.append(x)
                    ys.append(y)
                    n += 1
        print('%-18s 原 %dx%d -> 新 %dx%d  内容 %dx%d  x%d-%d y%d-%d  px %d'
              % (name, w, h, tw2, th2,
                 max(xs) - min(xs) + 1, max(ys) - min(ys) + 1,
                 min(xs), max(xs), min(ys), max(ys), n))

        # 放大预览
        S = 8
        bg = Image.new('RGBA', (256, 256))
        for y in range(256):
            for x in range(256):
                bg.putpixel((x, y), (58, 58, 58, 255)
                            if ((x // S) + (y // S)) % 2 == 0 else (38, 38, 38, 255))
        bg.alpha_composite(fixed.resize((256, 256), Image.NEAREST))
        bg.convert('RGB').save(os.path.join(
            r'c:\Users\ASUS\CodeBuddy\20260905194031\curseforge_assets',
            'preview_' + name + '.png'))
    print('预览已更新到 curseforge_assets/preview_*.png')


if __name__ == '__main__':
    main()
