package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.client.tooltip.ClientMythicNameTooltip;
import com.example.lolaccessories.client.tooltip.ClientPaidCostTooltip;
import com.example.lolaccessories.client.tooltip.MythicNameTooltip;
import com.example.lolaccessories.client.tooltip.PaidCostTooltip;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RegisterClientTooltipComponentFactoriesEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 注册 {@link PaidCostTooltip} 对应的客户端渲染组件 {@link ClientPaidCostTooltip}。
 *
 * <p>{@code RegisterClientTooltipComponentFactoriesEvent} 走 MOD 事件总线且只存在于客户端，
 * 因此整个类仅在客户端装载（其余时刻不会被加载）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID,
        bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
public final class ClientPaidCostTooltipRegistration {

    private ClientPaidCostTooltipRegistration() {
    }

    @SubscribeEvent
    public static void registerFactories(RegisterClientTooltipComponentFactoriesEvent event) {
        event.register(PaidCostTooltip.class, ClientPaidCostTooltip::new);
        // 神话流光标题（澄空之愿等 tier4 神话装备）
        event.register(MythicNameTooltip.class, ClientMythicNameTooltip::new);
    }
}
