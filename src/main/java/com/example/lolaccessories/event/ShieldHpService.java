package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 黄心护盾服务（服务端）。
 *
 * <p>按英雄联盟“护盾 = 伤害吸收”的语义，用原版“吸收”UI（金黄色心）呈现护盾值，
 * 但完全不经由药水效果：直接写 {@link ServerPlayer#setAbsorptionAmount(float)}，
 * 该值会经实体 DataWatcher 自动同步到客户端显示黄心。因此：</p>
 * <ul>
 *   <li>没有 buff 图标、不会被牛奶/清除药水效果抹掉；</li>
 *   <li>同一时刻只保留一个活跃护盾来源（后到覆盖制），避免共享黄心池相互叠加/覆盖错乱。</li>
 * </ul>
 *
 * <p>本服务在每玩家每 tick 检查到期，到期即把黄心清零；具体装备（败魔 / 原生质护带 /
 * 生命残片等）只负责在合适的时机调用 {@link #apply} / {@link #clear}。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShieldHpService {

    /** 败魔（魔盾）来源标识。 */
    public static final String SOURCE_ROOKERN = "rookern";
    /** 原生质护带（救主灵刃）来源标识。 */
    public static final String SOURCE_PROTOPLASM = "protoplasm";
    /** 生命残片（魔法护盾）来源标识。 */
    public static final String SOURCE_LIFELINE = "lifeline";
    /** 斯特拉克的挑战护手（救主灵刃）来源标识。 */
    public static final String SOURCE_STERAK = "sterak";
    /** 饮血剑（余烬溢血护盾）来源标识。 */
    public static final String SOURCE_BLOODTHIRSTER = "bloodthirster";

    /** 单名玩家当前活跃护盾来源与到期时刻。 */
    private static final class State {
        final String source;
        final long untilMs;

        State(String source, long untilMs) {
            this.source = source;
            this.untilMs = untilMs;
        }
    }

    private static final Map<UUID, State> ACTIVE = new HashMap<>();

    private ShieldHpService() {
    }

    /**
     * 施加黄心护盾：把玩家当前吸收值置为 {@code amount}（黄心显示），并记录到期时间。
     *
     * <p>若玩家已有其他来源的黄心护盾会被本来源直接顶替（覆盖制）。同来源重复施加为刷新。</p>
     *
     * @param player     目标玩家
     * @param source     来源标识（{@link #SOURCE_ROOKERN} 等），用于区分/到期管理
     * @param amount     黄心护盾量（生命点数，4 点 = 1 颗黄心）
     * @param durationMs 持续毫秒，到期后黄心清零
     */
    public static void apply(ServerPlayer player, String source, float amount, long durationMs) {
        if (player == null || player.isRemoved()) {
            return;
        }
        if (amount <= 0.0F) {
            clear(player);
            return;
        }
        // 「受到治疗与护盾提升」（振奋盔甲·无匹活力语义）：护盾不吃 Apothic 的治疗结算，
        // 在此读取 healing_received 净加成统一乘区，与受治疗语义保持一致。
        double incoming = incomingHealBonus(player);
        if (incoming > 0.0D) {
            amount *= (float) (1.0D + incoming);
        }
        UUID uuid = player.getUUID();
        // 吸收池按“单来源”管理：新来源到来直接顶替旧的（旧的到期记录一并清除，防止误扣新盾）。
        ACTIVE.put(uuid, new State(source, System.currentTimeMillis() + Math.max(1L, durationMs)));
        player.setAbsorptionAmount(amount);
    }

    /** 读取 Apothic「受到治疗提升」（attributeslib:healing_received）净加成；未安装/未配置返回 0。 */
    private static double incomingHealBonus(ServerPlayer player) {
        net.minecraft.resources.ResourceLocation id =
                net.minecraft.resources.ResourceLocation.tryParse("attributeslib:healing_received");
        if (id == null) {
            return 0.0D;
        }
        net.minecraft.world.entity.ai.attributes.Attribute attribute =
                net.minecraft.core.registries.BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return 0.0D;
        }
        net.minecraft.world.entity.ai.attributes.AttributeInstance instance =
                player.getAttribute(attribute);
        if (instance == null) {
            return 0.0D;
        }
        return Math.max(0.0D, instance.getValue() - attribute.getDefaultValue());
    }

    /** 该玩家当前是否有某来源的黄心护盾记录（即便黄心已被伤害打空、时长未到仍算“存在”）。 */
    public static boolean isSource(UUID uuid, String source) {
        State state = ACTIVE.get(uuid);
        return state != null && state.source.equals(source);
    }

    /** 该玩家当前是否存在任意黄心护盾记录。 */
    public static boolean hasActive(UUID uuid) {
        return ACTIVE.containsKey(uuid);
    }

    /** 清空某玩家的黄心护盾（黄心归零并移除到期记录）。仅当存在记录时执行。 */
    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (ACTIVE.remove(player.getUUID()) != null) {
            player.setAbsorptionAmount(0.0F);
        }
    }

    /** 仅当护盾来源匹配时才清空（用于装备卸载/破盾，避免误清其他来源的新盾）。 */
    public static void clearIfSource(ServerPlayer player, String source) {
        if (player == null) {
            return;
        }
        if (isSource(player.getUUID(), source)) {
            clear(player);
        }
    }

    /** 服务端每 tick 检查到期：黄心护盾到时间后清零。 */
    private static void tick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        State state = ACTIVE.get(uuid);
        if (state == null) {
            return;
        }
        if (System.currentTimeMillis() >= state.untilMs) {
            ACTIVE.remove(uuid);
            player.setAbsorptionAmount(0.0F);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (event.player.level().isClientSide) {
            return;
        }
        if (event.player instanceof ServerPlayer serverPlayer) {
            tick(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        // 死亡重生：新实体吸收值归零，旧状态直接丢弃
        if (event.isWasDeath()) {
            ACTIVE.remove(event.getEntity().getUUID());
        }
    }
}
