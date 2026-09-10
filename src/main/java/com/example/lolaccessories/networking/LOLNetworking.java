package com.example.lolaccessories.networking;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.network.NetworkRegistry;
import net.minecraftforge.network.PacketDistributor;
import net.minecraftforge.network.simple.SimpleChannel;

/**
 * LOLAccessories 网络通道。
 *
 * <ul>
 *   <li>服务端 → 客户端：{@link EchoCooldownPacket}（回声被动冷却 HUD）、
 *       {@link SkillCooldownPacket}（主动技能冷却 HUD：时间停止/净化/新月）。</li>
 *   <li>客户端 → 服务端：{@link TimeStopTriggerPacket}（时间停止）、
 *       {@link ActiveSkillTriggerPacket}（净化/新月等新主动技，载荷为技能 id）。</li>
 * </ul>
 */
public final class LOLNetworking {

    private static final String PROTOCOL_VERSION = "1";

    private static final SimpleChannel CHANNEL = NetworkRegistry.newSimpleChannel(
            new ResourceLocation(LOLAccessories.MOD_ID + ":main"),
            () -> PROTOCOL_VERSION,
            PROTOCOL_VERSION::equals,
            PROTOCOL_VERSION::equals);

    private static int nextId = 0;

    private LOLNetworking() {
    }

    /** 在模组构造阶段调用：注册所有消息类型。 */
    public static void register() {
        CHANNEL.registerMessage(nextId++, EchoCooldownPacket.class,
                EchoCooldownPacket::encode,
                EchoCooldownPacket::decode,
                EchoCooldownPacket::handle);
        CHANNEL.registerMessage(nextId++, SkillCooldownPacket.class,
                SkillCooldownPacket::encode,
                SkillCooldownPacket::decode,
                SkillCooldownPacket::handle);
        CHANNEL.registerMessage(nextId++, TimeStopTriggerPacket.class,
                TimeStopTriggerPacket::encode,
                TimeStopTriggerPacket::decode,
                TimeStopTriggerPacket::handle);
        CHANNEL.registerMessage(nextId++, ActiveSkillTriggerPacket.class,
                ActiveSkillTriggerPacket::encode,
                ActiveSkillTriggerPacket::decode,
                ActiveSkillTriggerPacket::handle);
        CHANNEL.registerMessage(nextId++, GearFxPacket.class,
                GearFxPacket::encode,
                GearFxPacket::decode,
                GearFxPacket::handle);
    }

    /** 服务端通知指定玩家：回声被动已触发，其冷却为 cooldownTicks 游戏刻。 */
    public static void sendEchoCooldown(ServerPlayer player, int cooldownTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EchoCooldownPacket(cooldownTicks));
    }

    /** 服务端通知指定玩家：主动技能（skillId）已触发，其冷却为 cooldownTicks 游戏刻。 */
    public static void sendSkillCooldown(ServerPlayer player, String skillId, int cooldownTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new SkillCooldownPacket(skillId, cooldownTicks));
    }

    /** 客户端按下「时间停止」按键时调用：请求服务端触发。 */
    public static void sendTimeStopTrigger() {
        CHANNEL.sendToServer(new TimeStopTriggerPacket());
    }

    /** 客户端按下新主动技按键（净化/新月）时调用：请求服务端按技能 id 触发。 */
    public static void sendActiveSkillTrigger(String skillId) {
        CHANNEL.sendToServer(new ActiveSkillTriggerPacket(skillId));
    }

    /**
     * 服务端向所有正在追踪 {@code target} 的客户端发送装备视觉特效开关。
     *
     * <p>使用 {@code TRACKING_ENTITY_AND_SELF} 分发：特效大多锚定在玩家自己身上
     * （战歌增幅、魔盾、救主灵刃等），若用不含自身的 {@code TRACKING_ENTITY}，目标玩家
     * 本人会收不到包，单人测试里特效就永远不显示。AND_SELF 保证本人与旁观者都可见。</p>
     */
    public static void sendGearFx(net.minecraft.world.entity.Entity target, FxKind kind, boolean active, int ticks) {
        CHANNEL.send(PacketDistributor.TRACKING_ENTITY_AND_SELF.with(() -> target),
                new GearFxPacket(kind, target.getUUID(), active, ticks));
    }
}
