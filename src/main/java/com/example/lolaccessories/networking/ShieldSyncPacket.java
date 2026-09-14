package com.example.lolaccessories.networking;

import com.example.lolaccessories.client.ShieldBarHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 护盾同步包（服务端 → 客户端）：三池护盾余额（白/魔/物），驱动快捷栏左侧护盾条。
 */
public record ShieldSyncPacket(float white, float magic, float physical) {

    public static void encode(ShieldSyncPacket msg, FriendlyByteBuf buf) {
        buf.writeFloat(msg.white());
        buf.writeFloat(msg.magic());
        buf.writeFloat(msg.physical());
    }

    public static ShieldSyncPacket decode(FriendlyByteBuf buf) {
        return new ShieldSyncPacket(buf.readFloat(), buf.readFloat(), buf.readFloat());
    }

    public static void handle(ShieldSyncPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
                        ShieldBarHud.accept(msg.white(), msg.magic(), msg.physical())));
        ctx.get().setPacketHandled(true);
    }
}
