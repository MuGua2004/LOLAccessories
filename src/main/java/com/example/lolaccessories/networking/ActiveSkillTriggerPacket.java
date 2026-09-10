package com.example.lolaccessories.networking;

import com.example.lolaccessories.event.LolNewActiveSkillEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 34 件新 2 级装备主动技能触发包（客户端 → 服务端，载荷为技能 id）。
 *
 * <p>客户端不本地判定冷却 / 是否佩戴，只把“玩家按下了哪个主动技按键”这一事实转发到服务端，
 * 由服务端按技能 id 做穿戴校验、冷却判定并结算（净化 / 新月），防止作弊跳过冷却。</p>
 */
public record ActiveSkillTriggerPacket(String skillId) {

    public static void encode(ActiveSkillTriggerPacket msg, FriendlyByteBuf buf) {
        buf.writeUtf(msg.skillId(), 32);
    }

    public static ActiveSkillTriggerPacket decode(FriendlyByteBuf buf) {
        return new ActiveSkillTriggerPacket(buf.readUtf(32));
    }

    public static void handle(ActiveSkillTriggerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                LolNewActiveSkillEvents.tryTrigger(sender, msg.skillId());
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
