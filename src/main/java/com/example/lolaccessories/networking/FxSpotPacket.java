package com.example.lolaccessories.networking;

import net.minecraft.network.FriendlyByteBuf;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.fml.DistExecutor;
import net.minecraftforge.network.NetworkEvent;

import java.util.function.Supplier;

/**
 * 地点锚定的装备特效开关（服务端 → 客户端）。
 *
 * <p>与 {@link GearFxPacket}（锚定实体）互补：救赎「降临」的施法点法阵 /
 * 圣光光柱、残疫的憎恨之雾法阵圈等不跟随任何实体，而是锚定在施法/触发的
 * 世界坐标上，由 {@code client.ActiveSpotFx} 维护其生命周期并在世界渲染阶段绘制。
 * {@code radius} 为特效的作用半径（法阵圈大小），由服务端按配置传入。</p>
 */
public record FxSpotPacket(FxKind kind, double x, double y, double z, int ticks, float radius) {

    public static void encode(FxSpotPacket msg, FriendlyByteBuf buf) {
        buf.writeEnum(msg.kind());
        buf.writeDouble(msg.x());
        buf.writeDouble(msg.y());
        buf.writeDouble(msg.z());
        buf.writeVarInt(msg.ticks());
        buf.writeFloat(msg.radius());
    }

    public static FxSpotPacket decode(FriendlyByteBuf buf) {
        return new FxSpotPacket(buf.readEnum(FxKind.class), buf.readDouble(),
                buf.readDouble(), buf.readDouble(), buf.readVarInt(), buf.readFloat());
    }

    public static void handle(FxSpotPacket msg, Supplier<NetworkEvent.Context> ctx) {
        ctx.get().enqueueWork(() -> DistExecutor.safeRunWhenOn(Dist.CLIENT,
                () -> () -> com.example.lolaccessories.client.ActiveSpotFx.apply(
                        msg.kind(), msg.x(), msg.y(), msg.z(), msg.ticks(), msg.radius())));
        ctx.get().setPacketHandled(true);
    }
}
