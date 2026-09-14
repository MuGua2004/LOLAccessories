package com.example.lolaccessories.client;

import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.common.Mod;

import com.example.lolaccessories.LOLAccessories;

/**
 * 客户端盈能能量缓存（由 {@link com.example.lolaccessories.networking.EnergySyncPacket}
 * 从服务端同步而来），供 {@link EnergyHudOverlay} 绘制盈能条。
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ClientEnergyState {

    private static volatile float energy = 0.0F;
    private static volatile float max = 100.0F;

    private ClientEnergyState() {
    }

    public static void set(float e, float m) {
        energy = Math.max(0.0F, e);
        max = Math.max(1.0F, m);
    }

    public static float energy() {
        return energy;
    }

    public static float max() {
        return max;
    }
}
