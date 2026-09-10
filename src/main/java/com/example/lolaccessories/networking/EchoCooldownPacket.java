package com.example.lolaccessories.networking;

import com.example.lolaccessories.client.SkillCooldownHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 回声冷却同步包（服务端 → 客户端）。
 *
 * <p>载荷只有 cooldownTicks（本次冷却总刻数）。客户端在收到包的那一刻开始计时，
 * 冷却起始即“收到该包”，无需额外传时间戳。</p>
 */
public record EchoCooldownPacket(int cooldownTicks) {

    public static void encode(EchoCooldownPacket msg, FriendlyByteBuf buf) {
        buf.writeVarInt(msg.cooldownTicks());
    }

    public static EchoCooldownPacket decode(FriendlyByteBuf buf) {
        return new EchoCooldownPacket(buf.readVarInt());
    }

    public static void handle(EchoCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> SkillCooldownHud.markTriggered("echo", msg.cooldownTicks())));
        ctx.get().setPacketHandled(true);
    }
}
