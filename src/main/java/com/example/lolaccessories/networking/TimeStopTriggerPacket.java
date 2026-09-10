package com.example.lolaccessories.networking;

import com.example.lolaccessories.event.LolTimeStopEvents;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 「时间停止」按键触发包（客户端 → 服务端，空载荷）。
 *
 * <p>客户端不本地判定冷却 / 是否佩戴，只负责把“玩家按下了主动技按键”这一事实转发到
 * 服务端，由服务端统一做穿戴校验、冷却判定并结算状态，防止作弊跳过冷却。</p>
 */
public record TimeStopTriggerPacket() {

    public static void encode(TimeStopTriggerPacket msg, FriendlyByteBuf buf) {
        // 空载荷
    }

    public static TimeStopTriggerPacket decode(FriendlyByteBuf buf) {
        return new TimeStopTriggerPacket();
    }

    public static void handle(TimeStopTriggerPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> {
            ServerPlayer sender = ctx.get().getSender();
            if (sender != null) {
                LolTimeStopEvents.tryTrigger(sender);
            }
        });
        ctx.get().setPacketHandled(true);
    }
}
