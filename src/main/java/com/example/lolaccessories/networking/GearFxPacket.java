package com.example.lolaccessories.networking;

import com.example.lolaccessories.client.ActiveGearFx;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.UUID;
import java.util.function.Supplier;

/**
 * 装备视觉特效包（服务端 → 客户端）。
 *
 * <p>载荷：{@link FxKind} + 目标实体 UUID + 开/关 + 持续刻数。客户端在
 * {@link ActiveGearFx} 里维护“每实体每类特效”的持续窗口：窗口类特效按刻自然消退，
 * 护罩/增幅被同 type 再次点亮时刷新；服务端如需提前解除（破盾/窗口中断）发一条
 * {@code active=false} 即可。</p>
 */
public record GearFxPacket(FxKind kind, UUID target, boolean active, int ticks) {

    public static void encode(GearFxPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.kind());
        buf.writeUUID(msg.target());
        buf.writeBoolean(msg.active());
        buf.writeVarInt(msg.ticks());
    }

    public static GearFxPacket decode(FriendlyByteBuf buf) {
        return new GearFxPacket(buf.readEnum(FxKind.class), buf.readUUID(),
                buf.readBoolean(), buf.readVarInt());
    }

    public static void handle(GearFxPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> () -> ActiveGearFx.apply(msg.kind(), msg.target(), msg.active(), msg.ticks())));
        ctx.get().setPacketHandled(true);
    }
}
