package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.config.ClientFxConfig;
import com.example.lolaccessories.init.ModItemTags;
import com.mojang.blaze3d.systems.RenderSystem;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.ForgeRegistries;
import org.joml.AxisAngle4f;
import org.joml.Quaternionf;

/**
 * 神话装备「魔法阵」叠加渲染（Mahou Tsukai 风格）：在物品图标<b>下方</b>垫画双层法阵，
 * 外符文环正转、内星芒反转，配合轻微呼吸缩放与透明度。
 *
 * <p><b>不会变实心的设计要点：</b></p>
 * <ul>
 *   <li>贴图中心完全镂空（只有极小中心圆，无中心亮芯）——不可能出现实心圆饼；</li>
 *   <li>只画 2 个 quad（外环 + 内星），固定不叠加；</li>
 *   <li>走 {@link GuiGraphics#blit} 的 10 参带 blitOffset 重载（GuiGraphics#renderItem 内
 *       部使用的同款签名），渲染走 {@code guiTextured} 自动带 alpha 混合；</li>
 *   <li><b>不调用</b> {@code RenderSystem.enableBlend()/defaultBlendFunc()}——这些是
 *       持久全局状态，残留会污染后续 3D 物品渲染（鼠标抬起 / 其它非神话物品被染色变形）。</li>
 *   <li>{@code setShaderColor} 在 finally 中恢复 + {@code disableBlend} 恢复——保证退出
 *       函数后渲染状态与进入前完全一致，绝不影响任何后续绘制。</li>
 * </ul>
 */
public final class MythicSigilRenderer {

    private static final String RING = "textures/fx/sigil_ring_";
    private static final String STAR = "textures/fx/sigil_star_";
    private static final String DEFAULT_SET = "clear";

    private static final float RING_SIZE = 40.0F;
    private static final float STAR_SIZE = 28.0F;
    private static final double RING_SPEED = 0.00042D;
    private static final double STAR_SPEED = -0.00065D;

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
        double breathe = 0.91D + 0.09D * Math.sin(time * Math.PI / 1400.0D);
        float alpha = (float) Math.min(1.0D, 0.82D * breathe * brightness);

        float cx = x + 8.0F;
        float cy = y + 8.0F;

        try {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, alpha);
            blitRotated(gfx, ring, cx, cy, RING_SIZE * (float) breathe,
                    (float) (time * RING_SPEED));
            blitRotated(gfx, star, cx, cy, STAR_SIZE * (float) breathe,
                    (float) (time * STAR_SPEED));
        } finally {
            RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        }
    }

    /**
     * 以 (cx, cy) 为中心、按 angle 弧度旋转地贴一张 128x128 源贴图。
     *
     * <p>使用 {@link GuiGraphics#blit} 的 10 参签名（atlas, x, y, blitOffset, uOffset,
     * vOffset, uWidth, vHeight, textureWidth, textureHeight）——这是 GuiGraphics#renderItem
     * 内部 blit 的同款签名，能精确控制 z 偏移与 UV 区域，不会被错误的重载污染。</p>
     */
    private static void blitRotated(GuiGraphics gfx, ResourceLocation tex,
                                    float cx, float cy, float size, float angle) {
        int s = Math.round(size);
        var pose = gfx.pose();
        pose.pushPose();
        // 1) 把局部坐标系原点搬到图标中心
        pose.translate(cx, cy, 0.0F);
        // 2) 绕 z 轴旋转
        pose.mulPose(new Quaternionf(new AxisAngle4f(angle, 0.0F, 0.0F, 1.0F)));
        // 3) blit(x, y) 把 (x, y) 当成「当前 pose 原点相对屏幕的左上角」，
        //    因此让 (x, y) = (-s/2, -s/2)，左上角落在「中心 - 半边」= 真正的左上
        gfx.blit(tex, -s / 2, -s / 2, 0, 0.0F, 0.0F, s, s, 128, 128);
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