package com.example.lolaccessories.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 盈能能量同步（服务端 → 客户端）：驱动客户端 HUD 盈能条
 * （饥饿条上方的闪电条，见 {@code client.EnergyHudOverlay}）。
 */
public record EnergySyncPacket(float energy, float max) {

    public static void encode(EnergySyncPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.energy());
        buf.writeFloat(msg.max());
    }

    public static EnergySyncPacket decode(FriendlyByteBuf buf) {
        return new EnergySyncPacket(buf.readFloat(), buf.readFloat());
    }

    public static void handle(EnergySyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.lolaccessories.client.ClientEnergyState.set(msg.energy(), msg.max())));
        ctx.get().setPacketHandled(true);
    }
}
