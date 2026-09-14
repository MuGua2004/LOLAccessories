package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.mojang.blaze3d.systems.RenderSystem;
import com.mojang.blaze3d.vertex.DefaultVertexFormat;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.Tesselator;
import com.mojang.blaze3d.vertex.VertexFormat;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.renderer.GameRenderer;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterGuiOverlaysEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 盈能条 HUD（客户端）：饥饿值上方的一条横向能量条 + 闪电图标。
 *
 * <p>位置与饥饿条右端对齐（{@code x = 屏宽/2 + 91}），向上错开一行，不会遮挡
 * 饥饿/护甲条。能量值由服务端 {@link com.example.lolaccessories.networking.EnergySyncPacket}
 * 同步到 {@link ClientEnergyState}。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class EnergyHudOverlay {

    /** 盈能条闪电图标（16×16 手绘像素闪电，渲染为 9×9）。 */
    private static final ResourceLocation BOLT_TEXTURE =
            new ResourceLocation(LOLAccessories.MOD_ID, "textures/gui/energy_bolt.png");

    /** 条总宽（与原版饥饿条同宽：9 格 × 9px）。 */
    private static final int BAR_WIDTH = 81;
    /** 条高。 */
    private static final int BAR_HEIGHT = 6;

    private EnergyHudOverlay() {
    }

    @SubscribeEvent
    public static void onRegisterOverlays(RegisterGuiOverlaysEvent event) {
        event.registerAbove(VanillaGuiOverlay.FOOD_LEVEL.id(), "energy_bar",
                (gui, guiGraphics, partialTick, width, height) -> render(guiGraphics, width, height));
    }

    private static void render(GuiGraphics guiGraphics, int width, int height) {
        float energy = ClientEnergyState.energy();
        float max = ClientEnergyState.max();
        // 不常驻：没有能量或已经攒满时隐藏（攒满后下一次普攻触发盈能攻击即清零）
        if (energy <= 0.01F || energy >= max - 0.01F) {
            return;
        }
        float frac = Mth.clamp(energy / max, 0.0F, 1.0F);

        int xRight = width / 2 + 91;
        int xLeft = xRight - BAR_WIDTH;
        // 饥饿条所在行向上错开一行（饥饿条 y≈height-39+3）
        int y = height - 39 - 10;

        PoseStack poseStack = guiGraphics.pose();

        // 背景：暗槽
        guiGraphics.fill(xLeft, y, xRight, y + BAR_HEIGHT, 0x88000000 | 0x101418);
        // 填充：满时亮青金，随比例变亮
        int filled = (int) (BAR_WIDTH * frac);
        if (filled > 0) {
            float glow = 0.55F + 0.45F * frac;
            int r = (int) (0x9B * glow);
            int g = (int) (0xE8 * glow);
            int b = (int) (0xFF * glow);
            guiGraphics.fill(xLeft, y, xLeft + filled, y + BAR_HEIGHT,
                    (0xFF << 24) | (Mth.clamp(r, 0, 255) << 16) | (Mth.clamp(g, 0, 255) << 8) | Mth.clamp(b, 0, 255));
        }
        // 顶端细描边
        guiGraphics.fill(xLeft, y, xRight, y + 1, 0x66000000);

        // 闪电图标（条左端外侧）
        RenderSystem.enableBlend();
        RenderSystem.setShader(GameRenderer::getPositionTexShader);
        RenderSystem.setShaderColor(1.0F, 1.0F, 1.0F, 1.0F);
        guiGraphics.blit(BOLT_TEXTURE, xLeft - 11, y - 2, 0.0F, 0.0F, 9, 9, 16, 16);
        RenderSystem.disableBlend();
    }
}
