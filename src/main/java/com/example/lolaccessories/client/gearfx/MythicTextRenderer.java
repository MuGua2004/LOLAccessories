package com.example.lolaccessories.client.gearfx;

import net.minecraft.ChatFormatting;
import net.minecraft.client.gui.Font;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * 神话流光字渲染核心：
 *
 * <p>首件神话装备「澄空之愿」采用类似 Goety: Revelation「教主长袍」的双层红色艺术字风格
 * （外层深红渐变厚描边 + 内层亮红→粉白渐变 + 高光带扫过），两层都施加随位置与时间
 * 流动的渐变；以后新增神话装备如需不同色系再行扩展。</p>
 *
 * <p>性能：纯 drawInBatch 批量文字调用，无 shader、无贴图采样；
 * 一行 12 字符约 156 次调用，对帧率无可测影响（性能优先守则）。</p>
 */
public final class MythicTextRenderer {

    private MythicTextRenderer() {
    }

    /** 深红外层描边的色相基线（0.97 ≈ 深红/红紫）。 */
    private static final float OUTLINE_HUE_BASE = 0.97f;
    /** 亮红内层主字的色相基线（0.02 ≈ 红偏橙/粉）。 */
    private static final float MAIN_HUE_BASE = 0.02f;
    /** 描边饱和度：满饱和。 */
    private static final float OUTLINE_SAT = 1.0f;
    /** 主字饱和度：略降防刺眼。 */
    private static final float MAIN_SAT = 0.95f;

    /**
     * 计算外层（描边/外发光）某字符在某时刻的 ARGB 颜色，hue 围绕 hueCenter 小幅流动。
     *
     * @param hueCenter 贴图主色色相（0~1）；描边在主色基础上偏暗一档
     * @param sat       基准饱和度（贴图偏白时较低 → 白光流光）
     */
    public static int outlineColor(int charX, long timeMs, float hueCenter, float sat) {
        float wave = (float) Math.sin((timeMs * 0.0020D) + charX * 0.018D);
        float hue = hueCenter - 0.05f + 0.04f * wave;
        float bright = 0.58f + 0.05f * (float) Math.sin(timeMs * Math.PI / 600.0D);
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, Math.max(0.0F, sat), bright);
    }

    /**
     * 计算内层（亮色主字）某字符在某时刻的 ARGB 颜色，hue 围绕贴图主色小幅流动，
     * 并叠加一道「亮高光带」随时间从左向右扫过。
     */
    public static int mainColor(int charX, long timeMs, float hueCenter, float sat) {
        float hueWave = (float) Math.sin((timeMs * 0.0024D) + charX * 0.022D);
        float hue = hueCenter + 0.05f * hueWave;
        // 高光带：从左到右扫过 charX~80px 范围的亮峰
        float band = (float) Math.sin((timeMs * 0.0035D) - charX * 0.055D);
        float bright = 0.88f + 0.12f * band;
        return 0xFF000000 | java.awt.Color.HSBtoRGB(hue, Math.max(0.0F, sat), bright);
    }

    /**
     * 以「双层艺术字」渲染文字（色调随装备贴图主色）：
     * <ol>
     *   <li>外发光层：更暗、4 向 ±2 偏移，模拟艺术字厚边光晕；</li>
     *   <li>外描边层：主色加深、8 向 ±1 偏移；</li>
     *   <li>内主字层：主色亮渐变 + 高光带扫过。</li>
     * </ol>
     *
     * @param hueCenter 贴图主色色相（0~1，见 MythicHueCache）
     * @param sat       基准饱和度（0.10 = 白光流光 / 0.9+ = 高饱和彩色流光）
     */
    public static void drawFlowingText(Font font, String text, float x, int y,
                                       Matrix4f matrix, MultiBufferSource.BufferSource buffers, long timeMs) {
        drawFlowingText(font, text, x, y, matrix, buffers, timeMs, OUTLINE_HUE_BASE, OUTLINE_SAT);
    }

    /** 带 hue/sat 的完整渲染（供按贴图色调的神话装备调用）。 */
    public static void drawFlowingText(Font font, String text, float x, int y,
                                       Matrix4f matrix, MultiBufferSource.BufferSource buffers,
                                       long timeMs, float hueCenter, float sat) {
        float cx = x;
        for (int i = 0; i < text.length(); i++) {
            String ch = String.valueOf(text.charAt(i));
            int px = Math.round(cx);
            int outline = outlineColor(px, timeMs, hueCenter, sat);
            int glow = outline & 0x00FFFFFF;  // 去掉 alpha，下面手动设半透明
            int glowArgb = (0xB0 << 24) | glow;
            // 外发光（4 向 ±2，更暗更深）
            font.drawInBatch(ch, cx - 2.0F, y, glowArgb, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx + 2.0F, y, glowArgb, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx, y - 2.0F, glowArgb, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx, y + 2.0F, glowArgb, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            // 8 向描边（深红渐变）
            font.drawInBatch(ch, cx - 1.0F, y, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx + 1.0F, y, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx, y - 1.0F, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx, y + 1.0F, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx - 1.0F, y - 1.0F, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx + 1.0F, y - 1.0F, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx - 1.0F, y + 1.0F, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            font.drawInBatch(ch, cx + 1.0F, y + 1.0F, outline, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            // 内层主字：主色亮渐变 + 高光带
            int main = mainColor(px, timeMs, hueCenter, sat);
            font.drawInBatch(ch, cx, y, main, false, matrix, buffers, Font.DisplayMode.NORMAL, 0, 0xF000F0);
            cx += font.width(ch);
        }
    }

    /** 估算流光文字的总宽度（供 tooltip 宽度计算，与普通渲染一致）。 */
    public static int flowingTextWidth(Font font, String text) {
        return font.width(text);
    }

    /**
     * 通用 HSL 流光颜色（供 tooltip 边框等场景使用，非澄空艺术字）：
     * hue = charX * PHASE + time * SPEED + hueOffset，按需取不同 hue/亮度。
     */
    public static int colorAt(int charX, long timeMs, float hueOffset, float bright) {
        float PHASE = 0.0075f;
        float SPEED = 1.0f / 2800.0f;
        float phase = charX * PHASE + timeMs * SPEED + hueOffset;
        float hue = phase - (float) Math.floor(phase);
        float b = bright * (0.9f + 0.1f * (float) Math.sin((timeMs + charX * 37.0D) * Math.PI / 500.0D));
        int rgb = java.awt.Color.HSBtoRGB(hue, 0.95f, b);
        return 0xFF000000 | rgb;
    }

    /**
     * 技能描述的关键词着色工具：在传入的任意 Component 中，给所有「整数 / 浮点数 / 百分号数」
     * 套上金黄 §6、把「秒/格/级/层」等单位套上青绿 §b，把「真实/虚空/护甲/魔抗」等核心机制词套上淡紫 §d。
     * 适用于被动/主动描述的快速着色（在 GearItem appendHoverText 中转调）。
     */
    public static String colorizeDescKeywords(String raw) {
        if (raw == null || raw.isEmpty()) {
            return raw;
        }
        // 数字（含 %）
        String out = raw.replaceAll("(?<![0-9.])(\\d+(?:\\.\\d+)?%)", "\u00A76$1\u00A77");
        // 数字（纯整数/浮点，前面非数字/小数点，且不能是颜色码 § 后的数字——
        // 否则会把上一步插入的 §7 里的 7 当成独立数字再染一次，拼出 10%67/15%67 这类错乱）
        out = out.replaceAll("(?<![0-9.\u00A7])(\\d+(?:\\.\\d+)?)(?![%0-9.])", "\u00A76$1\u00A77");
        // 单位关键词：此时数字已染成「§6数字§7」，单位紧随其后——
        // 直接匹配「已染色数字 + 单位」整体，把单位染青；
        // 绝不能再裸匹配 \d+（会把上一段色码 §7 里的数字 7 当数字吃掉，
        // 破坏格式序列并拼出 3067/18067 这类错乱数字）。
        out = out.replaceAll("(\u00A76\\d+(?:\\.\\d+)?\u00A77)\\s*(秒|格|级|层|次|点|米)",
                "$1\u00A7b$2\u00A77");
        // 防御层：未被染色的裸数字+单位（前一个字符是 § 开头的色码则跳过）
        out = out.replaceAll("(?<!\u00A7)(\\d+\\s*)(秒|格|级|层|次|点|米)", "\u00A76$1\u00A7b$2\u00A77");
        // 机制词
        out = out.replace("真实伤害", "\u00A7d真实伤害\u00A77");
        out = out.replace("虚空伤害", "\u00A79虚空伤害\u00A77");
        out = out.replace("护甲", "\u00A7e护甲\u00A77");
        out = out.replace("魔抗", "\u00A7d魔抗\u00A77");
        out = out.replace("最大生命", "\u00A7c最大生命\u00A77");
        out = out.replace("攻击力", "\u00A7c攻击力\u00A77");
        out = out.replace("法术强度", "\u00A7d法术强度\u00A77");
        out = out.replace("冷却缩减", "\u00A7b冷却缩减\u00A77");
        out = out.replace("暴击率", "\u00A76暴击率\u00A77");
        out = out.replace("移动速度", "\u00A7a移动速度\u00A77");
        return out;
    }
}
