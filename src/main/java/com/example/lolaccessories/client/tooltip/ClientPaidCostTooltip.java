package com.example.lolaccessories.client.tooltip;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 在物品 tooltip 中逐行绘制付费锻造费用：每行 18px，由一个 16px 物品图标和
 * 「×N 物品名」文字组成（N 为取件时需支付的数量）。
 *
 * <p>图标直接以 {@link GuiGraphics#renderItem} 绘制——与原版 Bundle 的
 * {@code ClientBundleTooltip} 相同的手法，不额外推拉矩阵；文字用带阴影的
 * {@code drawString} 画在图标右侧，行内垂直居中。</p>
 */
public class ClientPaidCostTooltip implements ClientTooltipComponent {

    private static final int ROW_HEIGHT = 18;
    private static final int TEXT_X_OFFSET = 20;

    private final List<ItemStack> costs;

    public ClientPaidCostTooltip(PaidCostTooltip data) {
        this.costs = data.costs();
    }

    @Override
    public int getHeight() {
        return costs.size() * ROW_HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        int width = TEXT_X_OFFSET;
        for (ItemStack cost : costs) {
            width = Math.max(width, TEXT_X_OFFSET + font.width(rowText(cost)));
        }
        return width;
    }

    @Override
    public void renderImage(Font font, int x, int y, GuiGraphics guiGraphics) {
        for (int i = 0; i < costs.size(); i++) {
            ItemStack cost = costs.get(i);
            int rowY = y + i * ROW_HEIGHT;
            guiGraphics.renderItem(cost, x, rowY);
            guiGraphics.drawString(font, rowText(cost), x + TEXT_X_OFFSET,
                    rowY + (ROW_HEIGHT - font.lineHeight) / 2, 0xFFFFFFFF, true);
        }
    }

    /** 该行文字：物品名 ×数量（如「金币 ×50」）。 */
    private static Component rowText(ItemStack cost) {
        return Component.literal(cost.getHoverName().getString())
                .append(Component.literal(" ×" + cost.getCount()));
    }
}
