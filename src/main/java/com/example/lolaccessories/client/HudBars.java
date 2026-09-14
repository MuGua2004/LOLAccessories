package com.example.lolaccessories.client;

import net.minecraft.client.gui.GuiGraphics;

/**
 * HUD 条通用绘制工具：经典 RPG 风格的「斜面（bevel）发光条」——
 * 近黑外描边（四角留空成圆角）+ 上左亮/下右暗的斜面金属框 + 深蓝黑内槽 +
 * 三段式光泽填充（顶部高光带 / 本色主体 / 底部阴影）+ 细分段线 + 末端亮色光标。
 */
public final class HudBars {

    private static final int OUTLINE = 0xFF08090E;
    private static final int BEVEL_HI = 0xFF9AA4B6;
    private static final int BEVEL_MID = 0xFF5A6274;
    private static final int BEVEL_LO = 0xFF23272F;
    private static final int TRACK = 0xFF0A0F1A;
    private static final int TRACK_GLOSS = 0x22FFFFFF;
    private static final int SEGMENT_LINE = 0x30101420;

    private HudBars() {
    }

    /**
     * 画一根斜面发光条（宽度/高度自适应，描边四角各空 1px 形成圆角观感）。
     *
     * @param ratio 填充比例 0~1
     * @param color 填充主色（0xAARRGGBB）
     */
    public static void drawBar(GuiGraphics g, int x0, int x1, int y0, int y1,
                               float ratio, int color) {
        // 外描边（四边 1px 近黑，四角各空 1px 形成圆角观感）
        g.fill(x0, y0 - 1, x1, y0, OUTLINE);
        g.fill(x0, y1, x1, y1 + 1, OUTLINE);
        g.fill(x0 - 1, y0, x0, y1, OUTLINE);
        g.fill(x1, y0, x1 + 1, y1, OUTLINE);

        // 斜面金属框：上/左亮、下/右暗（经典 bevel）
        g.fillGradient(x0, y0, x1, y0 + 1, BEVEL_HI, BEVEL_MID);       // 框顶亮
        g.fill(x0, y1 - 1, x1, y1, BEVEL_LO);                           // 框底暗
        g.fill(x0, y0 + 1, x0 + 1, y1 - 1, BEVEL_MID);                  // 框左
        g.fill(x1 - 1, y0 + 1, x1, y1 - 1, BEVEL_LO);                   // 框右

        // 内槽（深蓝黑）+ 槽顶微高光
        g.fill(x0 + 1, y0 + 1, x1 - 1, y1 - 1, TRACK);
        g.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, TRACK_GLOSS);

        int innerW = x1 - x0 - 2;
        int filled = Math.round(innerW * Math.max(0.0F, Math.min(1.0F, ratio)));
        if (filled > 0) {
            int fx0 = x0 + 1;
            int fx1 = fx0 + filled;
            int fy0 = y0 + 1;
            int fy1 = y1 - 1;
            int mid = (fy0 + fy1) / 2;
            // 三段式光泽填充：顶部高光带 / 本色 / 底部阴影
            g.fill(fx0, fy0, fx1, mid, shade(color, 1.42F));
            g.fill(fx0, mid, fx1, fy1 - (fy1 - fy0) / 4, color);
            g.fill(fx0, fy1 - (fy1 - fy0) / 4, fx1, fy1, shade(color, 0.72F));
            // 左端 1px 高光（光源感）
            g.fill(fx0, fy0, fx0 + 1, fy1, shade(color, 1.7F));
            // 细分段线（每 6px 一条，若隐若现）
            for (int sx = fx0 + 6; sx < fx1; sx += 6) {
                g.fill(sx, fy0, sx + 1, fy1, SEGMENT_LINE);
            }
            // 末端亮色光标
            if (filled < innerW) {
                g.fill(fx1, fy0, fx1 + 1, fy1, shade(color, 1.75F));
            }
        }
    }

    /** 把 0xAARRGGBB 颜色按倍率提亮/压暗（alpha 不变）。 */
    public static int shade(int argb, float factor) {
        int r = Math.min(255, Math.round(((argb >>> 16) & 0xFF) * factor));
        int g = Math.min(255, Math.round(((argb >>> 8) & 0xFF) * factor));
        int b = Math.min(255, Math.round((argb & 0xFF) * factor));
        return (argb & 0xFF000000) | (r << 16) | (g << 8) | b;
    }
}
