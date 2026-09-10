package com.example.lolaccessories.client.tooltip;

import net.minecraft.world.inventory.tooltip.TooltipComponent;

/**
 * 神话装备流光标题在物品 tooltip 中的数据载荷。
 *
 * <p>与 {@link PaidCostTooltip} 同一套 rich-tooltip 通道：
 * tooltip 聚合事件把第一行（装备名）替换为本组件，客户端通过
 * {@code RegisterClientTooltipComponentFactoriesEvent} 注册的
 * {@link ClientMythicNameTooltip} 以神话流光样式渲染。</p>
 */
public class MythicNameTooltip implements TooltipComponent {

    private final String text;
    /** 贴图主色色相（0~1）与饱和度（流光渲染按贴图色调，见 MythicHueCache）。 */
    private final float hue;
    private final float sat;

    public MythicNameTooltip(String text, float hue, float sat) {
        this.text = text == null ? "" : text;
        this.hue = hue;
        this.sat = sat;
    }

    /** 标题纯文本（已本地化）。 */
    public String text() {
        return text;
    }

    public float hue() {
        return hue;
    }

    public float sat() {
        return sat;
    }
}
