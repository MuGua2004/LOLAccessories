package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.config.ClientFxConfig;
import com.example.lolaccessories.init.ModItemTags;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;

/**
 * 神话装备「魔法阵」叠加渲染（Mahou Tsukai 风格）：在物品图标<b>下方</b>垫画双层法阵，
 * 外符文环正转、内星芒反转，配合轻微呼吸缩放与透明度。
 *
 * <p><b>角度必须先取模——这是旋转能否生效的关键：</b></p>
 * <p>{@code System.currentTimeMillis() * speed} 会得到一个约 2e9 量级的弧度数，而
 * {@code float} 只有约 7 位有效数字，在 2e9 量级的精度步长高达约 256 弧度（远超一整圈的
 * 2π≈6.28），角度实际被量化成「几乎不变的值」→ <b>看上去完全不转</b>。因此这里先对时间
 * 按<b>各自的旋转周期</b>取模，把角度压到 [0, 2π) 再乘速度，float 精度就绰绰有余了。</p>
 *
 * <p><b>坐标变换顺序：</b></p>
 * <pre>
 *   pose.translate(中心)      ← 局部原点搬到图标中心
 *   pose.mulPose(rotate)      ← 绕 z 轴旋转（影响后续所有绘制）
 *   pose.scale(s/128)         ← 把 128 贴图缩放到屏幕 s 像素
 *   blit(tex, -64, -64, ...)  ← 画完整 128×128 贴图，左上落在「贴图中心 - 半边」
 * </pre>
 *
 * <p><b>不会变实心 / 不影响其它渲染：</b></p>
 * <ul>
 *   <li>贴图中心镂空（只有极小中心圆），只画 2 个 quad 不叠加；</li>
 *   <li>UV 始终采完整张 128×128（早期版本只采左上 31% 区域，取到外圈深色像素→发黑）；</li>
 *   <li>{@code enableBlend} / {@code setShaderColor} 全部在 finally 中恢复，
 *       保证退出后渲染状态与进入前一致，绝不污染后续 3D 物品绘制。</li>
 * </ul>
 */
public final class MythicSigilRenderer {

    private static final String RING = "textures/fx/sigil_ring_";
    private static final String STAR = "textures/fx/sigil_star_";
    private static final String DEFAULT_SET = "clear";

    /** 源 PNG 尺寸 128×128。 */
    private static final float TEX_SIZE = 128.0F;

    /** 外环尺寸（呼吸的<b>最大</b>尺寸；图标仅 16px，法阵比图标大才有效果）。 */
    private static final float RING_SIZE = 34.0F;
    /** 内星尺寸（呼吸的最大尺寸）。 */
    private static final float STAR_SIZE = 24.0F;

    /** 外环角速度（弧度/毫秒，正转）：约 5.2 秒一圈。 */
    private static final double RING_SPEED = 0.0012D;
    /** 内星角速度（负=反转）：约 3.5 秒一圈，与外环差速对转。 */
    private static final double STAR_SPEED = -0.0018D;

    /** 各自转一圈所需毫秒数——用于把时间取模，避免 float 精度把角度吃掉。 */
    private static final double RING_PERIOD_MS = (2.0D * Math.PI) / RING_SPEED;
    private static final double STAR_PERIOD_MS = (2.0D * Math.PI) / Math.abs(STAR_SPEED);

    private MythicSigilRenderer() {
    }

    public static void drawUnderItem(GuiGraphics gfx, ItemStack stack, int x, int y) {
        if (stack == null || stack.isEmpty() || !stack.is(ModItemTags.TIER4)) {
            return;
        }
        if (!ClientFxConfig.ENABLED.get()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc == null || mc.player == null) {
            return;
        }
        String set = sigilSetOf(stack);
        ResourceLocation ring = new ResourceLocation(LOLAccessories.MOD_ID, RING + set + ".png");
        ResourceLocation star = new ResourceLocation(LOLAccessories.MOD_ID, STAR + set + ".png");

        long time = System.currentTimeMillis();
        double brightness = ClientFxConfig.BRIGHTNESS.get();
        // 呼吸：同时作用于尺寸与亮度（用户要求保留收缩感），幅度约 0.82~1.0
        double breathe = 0.91D + 0.09D * Math.sin(time * Math.PI / 1400.0D);
        float alpha = (float) Math.min(1.0D, 0.82D * breathe * brightness);

        // 关键：先按各自周期取模，把角度压到 [0, 2π)，float 才不会把变化量吃掉
        double ringAngle = (time % RING_PERIOD_MS) * RING_SPEED;
        double starAngle = -((time % STAR_PERIOD_MS) * Math.abs(STAR_SPEED));

        float cx = x + 8.0F;
        float cy = y + 8.0F;

        try {
            RenderSystem.enableBlend();
            RenderSystem.defaultBlendFunc();
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            blitRotated(gfx, ring, cx, cy, RING_SIZE * (float) breathe, (float) ringAngle);
            blitRotated(gfx, star, cx, cy, STAR_SIZE * (float) breathe, (float) starAngle);
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
            RenderSystem.disableBlend();
        }
    }

    /**
     * 以 (cx, cy) 为中心、按 angle 弧度旋转 + 缩放绘制 128×128 贴图。
     *
     * <p>UV 始终采完整张贴图（{@code uWidth=vHeight=128}），屏幕尺寸靠 {@code pose.scale}
     * 整体压缩——这样旋转中心恒为图标中心，不会出现「只采贴图一角 + 旋转成菱形」的旧 bug。</p>
     */
    private static void blitRotated(GuiGraphics gfx, ResourceLocation tex,
                                    float cx, float cy, float size, float angle) {
        int s = Math.round(size);
        var pose = gfx.pose();
        pose.pushPose();
        pose.translate(cx, cy, 0.0F);
        pose.mulPose(Axis.ZP.rotation(angle));
        float sc = s / TEX_SIZE;
        pose.scale(sc, sc, 1.0F);
        int half = (int) (TEX_SIZE / 2.0F);
        gfx.blit(tex, -half, -half, 0, 0.0F, 0.0F,
                (int) TEX_SIZE, (int) TEX_SIZE, (int) TEX_SIZE, (int) TEX_SIZE);
        pose.popPose();
    }

    private static String sigilSetOf(ItemStack stack) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(stack.getItem());
        if (key == null) {
            return DEFAULT_SET;
        }
        return switch (key.getPath()) {
            case "clear_skys_wish" -> "clear";
            case "demon_heart" -> "demon";
            default -> DEFAULT_SET;
        };
    }
}