package com.example.lolaccessories.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 付费锻造费用在物品 tooltip 中的数据载荷。
 *
 * <p>{@link TooltipComponent} 仅是原版标记接口；客户端通过
 * {@code RegisterClientTooltipComponentFactoriesEvent} 注册 {@link ClientPaidCostTooltip}
 * 把这里携带的清单逐行渲染成「物品图标 + 名称 ×数量」。
 * 在渲染期悬停（物品 tooltip 聚合事件）按需构建本对象，因此不同成品可按各自配方显示费用。</p>
 */
public class PaidCostTooltip implements TooltipComponent {

    private final List<ItemStack> costs;

    public PaidCostTooltip(List<ItemStack> costs) {
        this.costs = List.copyOf(costs);
    }

    /** 花费清单：每项 ItemStack 的 count 为取件时需支付的数量。 */
    public List<ItemStack> costs() {
        return costs;
    }
}
