package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.config.ClientFxConfig;
import com.example.lolaccessories.event.ShieldType;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 护盾条 HUD（客户端）：绘制在快捷栏左侧、与快捷栏上沿平齐。
 *
 * <p>三种护盾各一种颜色：白盾（普通）、紫盾（魔法）、橙盾（物理）。同时持有多种时
 * 只显示数额最多的那种；条长按护盾值映射——最短 2 颗心宽（4 点生命），最长 10 颗心宽
 * （20 点生命），线性插值。条右侧显示护盾数值。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShieldBarHud {

    /** 条高度（金属分段条，与冷却条一致）。 */
    private static final int BAR_HEIGHT = 12;
    /** 条最短宽度：相当于原版生命值 2 颗心的宽度（1 心图标 9px）。 */
    private static final int MIN_WIDTH = 18;
    /** 条最长宽度：相当于原版生命值 10 颗心的宽度。 */
    private static final int MAX_WIDTH = 90;
    /** 护盾值→条长的映射下限（4 点 = 2 心）。 */
    private static final float MIN_SHIELD = 4.0F;
    /** 护盾值→条长的映射上限（20 点 = 10 心）。 */
    private static final float MAX_SHIELD = 20.0F;
    /** 条组右缘与快捷栏左缘的横向空隙。 */
    private static final int HOTBAR_LEFT_GAP = 8;
    /** 条底距屏幕底部的距离：快捷栏（高 22px）左上角上方 3px，紧贴快捷栏。 */
    private static final int HOTBAR_TOP_BASE = 25;
    /** 条上数字与条右端的内边距。 */
    private static final int TEXT_PAD = 2;
    /** 左侧护盾形图标尺寸（16x16 画布）。 */
    private static final int ICON_SIZE = 16;
    /** 图标与条身的空隙。 */
    private static final int ICON_GAP = 3;

    /** 三色护盾（白盾：亮白蓝；紫盾：魔法紫；橙盾：物理橙）。 */
    private static final int COLOR_WHITE = 0xFFE8F0FF;
    private static final int COLOR_MAGIC = 0xFFB26BFF;
    private static final int COLOR_PHYSICAL = 0xFFFFA24D;

    /** 护盾形图标（银框盾 + 类型色宝石）。 */
    private static final ResourceLocation ICON_WHITE = new ResourceLocation(
            LOLAccessories.MOD_ID, "textures/gui/hud/shield_white.png");
    private static final ResourceLocation ICON_MAGIC = new ResourceLocation(
            LOLAccessories.MOD_ID, "textures/gui/hud/shield_magic.png");
    private static final ResourceLocation ICON_PHYSICAL = new ResourceLocation(
            LOLAccessories.MOD_ID, "textures/gui/hud/shield_physical.png");

    /** 条上数字颜色：白盾条底色偏亮，用深色数字；其余盾用白字。 */
    private static final int TEXT_ON_LIGHT = 0xFF16161E;
    private static final int TEXT_ON_DARK = 0xFFFFFFFF;

    /** 客户端本地玩家三池余额（ShieldSyncPacket 更新）。 */
    private static volatile float white;
    private static volatile float magic;
    private static volatile float physical;

    private ShieldBarHud() {
    }

    /** 由护盾同步包在客户端主线程调用：更新三池余额。 */
    public static void accept(float white, float magic, float physical) {
        ShieldBarHud.white = white;
        ShieldBarHud.magic = magic;
        ShieldBarHud.physical = physical;
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.AIR_LEVEL.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null || mc.player == null) {
            return;
        }
        ShieldType primary = primaryType();
        if (primary == null) {
            return;
        }
        float value = switch (primary) {
            case WHITE -> white;
            case MAGIC -> magic;
            case PHYSICAL -> physical;
        };
        int color = switch (primary) {
            case WHITE -> COLOR_WHITE;
            case MAGIC -> COLOR_MAGIC;
            case PHYSICAL -> COLOR_PHYSICAL;
        };
        renderShieldBar(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(),
                event.getWindow().getGuiScaledHeight(), value, color, primary);
    }

    /** 数额最多的那种护盾；全部为空返回 null。 */
    private static ShieldType primaryType() {
        if (white <= 0.0F && magic <= 0.0F && physical <= 0.0F) {
            return null;
        }
        ShieldType primary = ShieldType.WHITE;
        float best = white;
        if (magic > best) {
            primary = ShieldType.MAGIC;
            best = magic;
        }
        if (physical > best) {
            primary = ShieldType.PHYSICAL;
        }
        return primary;
    }

    private static void renderShieldBar(GuiGraphics graphics, int screenWidth, int screenHeight,
                                        float value, int color, ShieldType primary) {
        // 位置写死贴快捷栏左上角，不读 xOffset/yOffset（避免历史配置把条顶到红心行高度）
        int xRight = screenWidth / 2 - 91 - HOTBAR_LEFT_GAP;
        int y1 = screenHeight - HOTBAR_TOP_BASE;
        int y0 = y1 - BAR_HEIGHT;

        // 条长：2 心（4 点）最短 → 10 心（20 点）最长，线性插值
        float t = Mth.clamp((value - MIN_SHIELD) / (MAX_SHIELD - MIN_SHIELD), 0.0F, 1.0F);
        int width = Math.round(Mth.lerp(t, MIN_WIDTH, MAX_WIDTH));
        int barX1 = xRight;
        int barX0 = barX1 - width;

        // 左侧护盾形图标（盾底与条底平齐，略高出条顶）
        ResourceLocation icon = switch (primary) {
            case WHITE -> ICON_WHITE;
            case MAGIC -> ICON_MAGIC;
            case PHYSICAL -> ICON_PHYSICAL;
        };
        graphics.blit(icon, barX0 - ICON_SIZE - ICON_GAP, y1 - ICON_SIZE + 1,
                0, 0, ICON_SIZE, ICON_SIZE, ICON_SIZE, ICON_SIZE);

        // RPG 斜面发光条（填充色随盾类型，满条显示）
        HudBars.drawBar(graphics, barX0, barX1, y0, y1, 1.0F, color);

        // 数值直接写在条上（条内右端，垂直居中；白盾条底色亮用深字，其余白字）
        Font font = Minecraft.getInstance().font;
        String text = String.valueOf(Math.max(1, Math.round(value)));
        int textX = barX1 - TEXT_PAD - font.width(text);
        int textY = y0 + (BAR_HEIGHT - font.lineHeight) / 2;
        graphics.drawString(font, text, textX, textY,
                color == COLOR_WHITE ? TEXT_ON_LIGHT : TEXT_ON_DARK, false);
    }
}
