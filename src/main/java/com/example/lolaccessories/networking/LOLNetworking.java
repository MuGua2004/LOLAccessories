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
        CHANNEL.registerMessage(nextId++, FxSpotPacket.class,
                FxSpotPacket::encode,
                FxSpotPacket::decode,
                FxSpotPacket::handle);
        CHANNEL.registerMessage(nextId++, EnergySyncPacket.class,
                EnergySyncPacket::encode,
                EnergySyncPacket::decode,
                EnergySyncPacket::handle);
        CHANNEL.registerMessage(nextId++, ShieldSyncPacket.class,
                ShieldSyncPacket::encode,
                ShieldSyncPacket::decode,
                ShieldSyncPacket::handle);
    }

    /** 服务端通知指定玩家：回声被动已触发，其冷却为 cooldownTicks 游戏刻。 */
    public static void sendEchoCooldown(ServerPlayer player, int cooldownTicks) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EchoCooldownPacket(cooldownTicks));
    }

    /** 服务端通知指定玩家：主动技能（skillId）已触发，其冷却为 cooldownTicks 游戏刻。 */
    public static void sendSkillCooldown(ServerPlayer player, String skillId, int cooldownTicks) {
        sendSkillCooldown(player, skillId, cooldownTicks, false);
    }

    /**
     * 服务端通知指定玩家：主动技能（skillId）已触发。
     *
     * @param debug 调试通道标记（{@code /lolaccessories cdtest}）：置真时客户端无视
     *              常规可见条件强制显示冷却条
     */
    public static void sendSkillCooldown(ServerPlayer player, String skillId, int cooldownTicks, boolean debug) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new SkillCooldownPacket(skillId, cooldownTicks, debug));
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

    /**
     * 服务端向正在追踪施法点所在区块的客户端（含施法者本人）广播地点特效。
     * 救赎「降临」、残疫「憎恨之雾」等以世界坐标为锚的特效使用此通道。
     * {@code radius} 为特效作用半径（法阵圈大小）。
     */
    public static void sendSpotFx(net.minecraft.world.level.Level level, FxKind kind,
                                  double x, double y, double z, int ticks, float radius) {
        net.minecraft.world.level.ChunkPos chunkPos =
                new net.minecraft.world.level.ChunkPos(net.minecraft.core.BlockPos.containing(x, y, z));
        CHANNEL.send(PacketDistributor.TRACKING_CHUNK.with(() -> level.getChunk(chunkPos.x, chunkPos.z)),
                new FxSpotPacket(kind, x, y, z, ticks, radius));
    }

    /** 服务端向指定玩家同步盈能能量（驱动客户端盈能条 HUD）。 */
    public static void sendEnergySync(ServerPlayer player, float energy, float max) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player), new EnergySyncPacket(energy, max));
    }

    /** 服务端向指定玩家同步三池护盾余额（白/魔/物，驱动快捷栏左侧护盾条）。 */
    public static void sendShieldSync(ServerPlayer player, float white, float magic, float physical) {
        CHANNEL.send(PacketDistributor.PLAYER.with(() -> player),
                new ShieldSyncPacket(white, magic, physical));
    }
}
