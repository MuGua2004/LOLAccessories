package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.ChatScreen;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 「时间停止」按键的客户端转发（仅客户端）。
 *
 * <p>按键被按下且不在聊天输入框时，向服务端发送触发请求；是否可用由服务端判定并回执。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class TimeStopKeyHandler {

    private TimeStopKeyHandler() {
    }

    @SubscribeEvent
    public static void onClientTick(TickEvent.ClientTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null || mc.level == null) {
            return;
        }
        // 聊天/输入框打开时忽略按键，避免打字误触
        if (mc.screen instanceof ChatScreen) {
            return;
        }
        if (ModKeyBindings.isTimeStopPressed()) {
            LOLNetworking.sendTimeStopTrigger();
        }
    }
}
