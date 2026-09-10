package com.example.lolaccessories.mixin;

import com.example.lolaccessories.client.gearfx.MythicTextRenderer;
import com.example.lolaccessories.init.ModItemTags;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipPositioner;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import org.joml.Vector2ic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 传说 / 神话装备的 tooltip 精致边框。
 *
 * <p>注入 {@code GuiGraphics.renderTooltipInternal} 的收尾点（popPose 调用处），此时
 * tooltip 的背景与内容已全部画完。矩形位置不依赖局部变量捕获（那样对映射版本很脆弱），
 * 而是与原版逻辑完全一致地重算：</p>
 * <ul>
 *   <li>宽 = max(各组件 getWidth)；高 = Σ(各组件 getHeight) + (单行时 -2)——与原版一致；</li>
 *   <li>左上角 = positioner.positionTooltip(...) 再求一次（positioner 均为无状态纯函数，
 *       二次调用与首次结果一致）。</li>
 * </ul>
 *
 * <p>绘制走原版 {@code GuiGraphics#fill}（纯色矩形），抬升 z=600 保证覆盖 tooltip 背景层：
 * </p>
 * <ul>
 *   <li>传说（tier3）：金色双线框 + 四角亮点；</li>
 *   <li>神话（tier4）：四边流光渐变框（复用 {@link MythicTextRenderer#colorAt} 色环，
 *       随悬停时间与位置流动）+ 内圈暗紫细线 + 四角白光点。</li>
 * </ul>
 */
@Mixin(GuiGraphics.class)
public abstract class GuiTooltipBorderMixin {

    /** Forge 补丁在 GuiGraphics 上维护的「当前 tooltip 悬停物品」字段（启示录同款 shadow；
     *  补丁字段不在官方混淆映射内，需 remap = false）。 */
    @Shadow(remap = false)
    @Nullable
    private ItemStack tooltipStack;

    @Inject(
            method = "renderTooltipInternal",
            at = @At(value = "INVOKE", target = "Lcom/mojang/blaze3d/vertex/PoseStack;popPose()V")
    )
    private void lolaccessories$tierBorder(Font font, List<ClientTooltipComponent> components,
                                           int mouseX, int mouseY, ClientTooltipPositioner positioner,
                                           CallbackInfo ci) {
        if (components == null || components.isEmpty() || tooltipStack == null || tooltipStack.isEmpty()) {
            return;
        }
        boolean mythic = tooltipStack.is(ModItemTags.TIER4);
        boolean legend = !mythic && tooltipStack.is(ModItemTags.TIER3);
        if (!mythic && !legend) {
            return;
        }

        GuiGraphics self = (GuiGraphics) (Object) this;
        // 与原版完全一致的矩形重算（见 GuiGraphics.renderTooltipInternal 的 i/j 计算）
        int width = 0;
        int height = components.size() == 1 ? -2 : 0;
        for (ClientTooltipComponent component : components) {
            int w = component.getWidth(font);
            if (w > width) {
                width = w;
            }
            height += component.getHeight();
        }
        Vector2ic pos = positioner.positionTooltip(self.guiWidth(), self.guiHeight(),
                mouseX, mouseY, width, height);
        int x = pos.x();
        int y = pos.y();
        // 原版背景绘制区域是 x-3..x+width+3 / y-4..y+height+4，边框整体外扩 1px 包住背景
        int x0 = x - 4;
        int y0 = y - 5;
        int x1 = x + width + 4;
        int y1 = y + height + 5;

        self.pose().pushPose();
        self.pose().translate(0.0F, 0.0F, 600.0F);
        try {
            if (mythic) {
                drawMythicBorder(self, x0, y0, x1, y1, mouseX);
            } else {
                drawLegendBorder(self, x0, y0, x1, y1);
            }
        } finally {
            self.pose().popPose();
        }
    }

    /** 传说：金色双线框（外 2px 金、内 1px 暗金衬线）+ 四角亮点。 */
    private static void drawLegendBorder(GuiGraphics gfx, int x0, int y0, int x1, int y1) {
        final int GOLD = 0xFFFFC63B;
        final int GOLD_DARK = 0xFF8A5A12;
        final int CORNER = 0xFFFFE9A8;
        // 外圈金线（1px）
        fillRect(gfx, x0 - 1, y0 - 1, x1 + 1, y1 + 1, GOLD);
        // 内衬暗金线（框住原版背景外缘）
        fillRect(gfx, x1, y0 + 1, x1 + 1, y1, GOLD_DARK);
        fillRect(gfx, x0, y1, x1, y1 + 1, GOLD_DARK);
        // 四角亮点（2x2）
        fillRect(gfx, x0 - 1, y0 - 1, x0 + 1, y0 + 1, CORNER);
        fillRect(gfx, x1 - 1, y0 - 1, x1 + 1, y0 + 1, CORNER);
        fillRect(gfx, x0 - 1, y1 - 1, x0 + 1, y1 + 1, CORNER);
        fillRect(gfx, x1 - 1, y1 - 1, x1 + 1, y1 + 1, CORNER);
    }

    /**
     * 神话：四边流光渐变框（颜色沿边随时间流动，复用神话流光字色环）+
     * 内圈暗紫细线 + 四角白光点。
     */
    private static void drawMythicBorder(GuiGraphics gfx, int x0, int y0, int x1, int y1, int mouseX) {
        long time = System.currentTimeMillis();
        // 顶边与底边：颜色随横向位置流动
        for (int px = x0; px < x1; px++) {
            int top = MythicTextRenderer.colorAt(px - mouseX, time, 0.0F, 0.95F);
            int bottom = MythicTextRenderer.colorAt(px - mouseX, time, 0.35F, 0.95F);
            gfx.fill(px, y0 - 1, px + 1, y0, top);
            gfx.fill(px, y1, px + 1, y1 + 1, bottom);
        }
        // 左右边：颜色随纵向位置流动
        for (int py = y0; py < y1; py++) {
            int left = MythicTextRenderer.colorAt(py * 2 - mouseX, time, 0.15F, 0.95F);
            int right = MythicTextRenderer.colorAt(py * 2 - mouseX, time, 0.55F, 0.95F);
            gfx.fill(x0 - 1, py, x0, py + 1, left);
            gfx.fill(x1, py, x1 + 1, py + 1, right);
        }
        // 内圈暗紫细线（紧贴原版背景外缘）
        final int INNER = 0xFF2A0F45;
        gfx.fill(x0 + 1, y0 + 1, x1 - 1, y0 + 2, INNER);
        gfx.fill(x0 + 1, y1 - 2, x1 - 1, y1 - 1, INNER);
        gfx.fill(x0 + 1, y0 + 1, x0 + 2, y1 - 1, INNER);
        gfx.fill(x1 - 2, y0 + 1, x1 - 1, y1 - 1, INNER);
        // 四角白光点（2x2）
        final int CORNER = 0xFFFFFFFF;
        gfx.fill(x0 - 1, y0 - 1, x0 + 1, y0 + 1, CORNER);
        gfx.fill(x1 - 1, y0 - 1, x1 + 1, y0 + 1, CORNER);
        gfx.fill(x0 - 1, y1 - 1, x0 + 1, y1 + 1, CORNER);
        gfx.fill(x1 - 1, y1 - 1, x1 + 1, y1 + 1, CORNER);
    }

    /** 描边矩形（四条 1px 边，非填充）。 */
    private static void fillRect(GuiGraphics gfx, int x0, int y0, int x1, int y1, int color) {
        gfx.fill(x0, y0, x1, y0 + 1, color);
        gfx.fill(x0, y1 - 1, x1, y1, color);
        gfx.fill(x0, y0, x0 + 1, y1, color);
        gfx.fill(x1 - 1, y0, x1, y1, color);
    }
}
