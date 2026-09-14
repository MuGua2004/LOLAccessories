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
 * 冷却起始即“收到该包”。</p>
 *
 * <p>{@code debug} 为调试通道标记（{@code /lolaccessories cdtest} 触发）：置真时客户端
 * 无视该技能的常规可见条件（如“必须佩戴对应装备”），强制显示冷却条，便于纯观感调试。</p>
 */
public record SkillCooldownPacket(String skillId, int cooldownTicks, boolean debug) {

    public static void encode(SkillCooldownPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.skillId());
        buf.writeVarInt(msg.cooldownTicks());
        buf.writeBoolean(msg.debug());
    }

    public static SkillCooldownPacket decode(FriendlyByteBuf buf) {
        return new SkillCooldownPacket(buf.readUtf(32), buf.readVarInt(), buf.readBoolean());
    }

    public static void handle(SkillCooldownPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() ->
                DistExecutor.safeRunWhenOn(Dist.CLIENT, () -> () ->
                        SkillCooldownHud.markTriggered(msg.skillId(), msg.cooldownTicks(), msg.debug())));
        ctx.get().setPacketHandled(true);
    }
}
