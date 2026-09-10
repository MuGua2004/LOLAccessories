package com.example.lolaccessories.networking;

import com.example.lolaccessories.client.SkillCooldownHud;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 主动技能冷却同步包（服务端 → 客户端，通用技能标识）。
 *
 * <p>载荷为 skillId + cooldownTicks（本次冷却总刻数）。客户端在收到包的那一刻开始计时，
 * 冷却起始即“收到该包”。当前唯一的 skillId 为 {@code time_stop}（探索者的护臂），
 * 后续新增主动技能时客户端按 skillId 分发到各自的 HUD。</p>
 */
public record SkillCooldownPacket(String skillId, int cooldownTicks) {

    public static void encode(SkillCooldownPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.skillId());
        buf.writeVarInt(msg.cooldownTicks());
    }

    public static SkillCooldownPacket decode(FriendlyByteBuf buf) {
        return new SkillCooldownPacket(buf.readUtf(32), buf.readVarInt());
    }

    public static void handle(SkillCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () -> SkillCooldownHud.markTriggered(msg.skillId(), msg.cooldownTicks())));
        ctx.get().setPacketHandled(true);
    }
}
