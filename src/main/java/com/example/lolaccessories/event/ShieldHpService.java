package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.DamageTypeTags;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 真护盾服务（服务端）——取代旧「原版黄心（吸收）」方案。
 *
 * <p>每名玩家三个独立护盾池（白/魔/物），各池记录来源、余额与到期时刻；护盾值不再写
 * 原版吸收（黄心），由 ShieldBarHud 在快捷栏左侧绘制真正的护盾条。</p>
 *
 * <p>抵消规则（LivingHurtEvent，LOWEST 优先级，等所有伤害修改定稿后结算）：
 * 白盾抵消除虚空（真实伤害语义）外的所有伤害；紫盾只抵铁魔法学派与原版魔法；
 * 橙盾只抵近战与弹射物。受击时对应类型盾优先，不足由白盾兜底；HUD 显示数额最多者。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class ShieldHpService {

    public static final String SOURCE_ROOKERN = "rookern";
    public static final String SOURCE_PROTOPLASM = "protoplasm";
    public static final String SOURCE_LIFELINE = "lifeline";
    public static final String SOURCE_STERAK = "sterak";
    public static final String SOURCE_BLOODTHIRSTER = "bloodthirster";
    public static final String SOURCE_MAW = "maw";

    private static final class Pool {
        String source = "";
        float amount;
        long untilMs;

        boolean hasRecord() {
            return untilMs > 0L;
        }
    }

    private static final class PlayerState {
        final Pool white = new Pool();
        final Pool magic = new Pool();
        final Pool physical = new Pool();

        Pool pool(ShieldType type) {
            return switch (type) {
                case WHITE -> white;
                case MAGIC -> magic;
                case PHYSICAL -> physical;
            };
        }
    }

    private static final Map<UUID, PlayerState> ACTIVE = new HashMap<>();
    private static final Map<UUID, float[]> LAST_SYNC = new HashMap<>();

    private ShieldHpService() {
    }

    /** 施加白盾（旧签名兼容）。后到覆盖制，同来源为刷新。 */
    public static void apply(ServerPlayer player, String source, float amount, long durationMs) {
        apply(player, source, amount, durationMs, ShieldType.WHITE);
    }

    /** 施加指定类型护盾；护盾量吃「受到治疗与护盾提升」（healing_received）统一乘区。 */
    public static void apply(ServerPlayer player, String source, float amount, long durationMs,
                             ShieldType type) {
        if (player == null || player.isRemoved() || type == null) {
            return;
        }
        if (amount <= 0.0F) {
            clearPool(player, type);
            return;
        }
        double incoming = incomingHealBonus(player);
        if (incoming > 0.0D) {
            amount *= (float) (1.0D + incoming);
        }
        PlayerState state = ACTIVE.computeIfAbsent(player.getUUID(), k -> new PlayerState());
        Pool pool = state.pool(type);
        pool.source = source == null ? "" : source;
        pool.amount = amount;
        pool.untilMs = System.currentTimeMillis() + Math.max(1L, durationMs);
        sync(player);
    }

    private static double incomingHealBonus(ServerPlayer player) {
        ResourceLocation id = ResourceLocation.tryParse("attributeslib:healing_received");
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

    /** 是否存在某来源的护盾记录（任一池匹配；盾被打空但时长未到仍算存在）。 */
    public static boolean isSource(UUID uuid, String source) {
        PlayerState state = ACTIVE.get(uuid);
        if (state == null || source == null) {
            return false;
        }
        return (state.white.hasRecord() && source.equals(state.white.source))
                || (state.magic.hasRecord() && source.equals(state.magic.source))
                || (state.physical.hasRecord() && source.equals(state.physical.source));
    }

    /** 是否存在任意护盾记录。 */
    public static boolean hasActive(UUID uuid) {
        PlayerState state = ACTIVE.get(uuid);
        return state != null && (state.white.hasRecord() || state.magic.hasRecord()
                || state.physical.hasRecord());
    }

    /**
     * 目标身上是否有任何可用护盾（本模组三池任一余额 > 0，或玩家身上的原版吸收黄心）。
     * 巨蛇之牙「掠盾者」用：对有护盾的目标伤害提高。
     */
    public static boolean hasAnyShield(LivingEntity target) {
        if (target instanceof ServerPlayer player) {
            PlayerState state = ACTIVE.get(player.getUUID());
            if (state != null && (state.white.amount > 0.0F || state.magic.amount > 0.0F
                    || state.physical.amount > 0.0F)) {
                return true;
            }
            return player.getAbsorptionAmount() > 0.0F;
        }
        return false;
    }

    /** 清空全部护盾并移除到期记录。 */
    public static void clear(ServerPlayer player) {
        if (player == null) {
            return;
        }
        if (ACTIVE.remove(player.getUUID()) != null) {
            LAST_SYNC.remove(player.getUUID());
            sync(player);
        }
    }

    /** 仅清空来源匹配的护盾池（装备卸载/破盾；避免误清其他来源的新盾）。 */
    public static void clearIfSource(ServerPlayer player, String source) {
        if (player == null || source == null) {
            return;
        }
        PlayerState state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        boolean changed = false;
        for (Pool pool : new Pool[]{state.white, state.magic, state.physical}) {
            if (pool.hasRecord() && source.equals(pool.source)) {
                pool.source = "";
                pool.amount = 0.0F;
                pool.untilMs = 0L;
                changed = true;
            }
        }
        if (changed) {
            sync(player);
        }
    }

    private static void clearPool(ServerPlayer player, ShieldType type) {
        PlayerState state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        Pool pool = state.pool(type);
        if (pool.hasRecord()) {
            pool.source = "";
            pool.amount = 0.0F;
            pool.untilMs = 0L;
            sync(player);
        }
    }

    private static float[] amounts(UUID uuid) {
        PlayerState state = ACTIVE.get(uuid);
        if (state == null) {
            return new float[3];
        }
        return new float[]{state.white.amount, state.magic.amount, state.physical.amount};
    }

    private static void sync(ServerPlayer player) {
        float[] values = amounts(player.getUUID());
        LAST_SYNC.put(player.getUUID(), values.clone());
        LOLNetworking.sendShieldSync(player, values[0], values[1], values[2]);
    }

    /** 真护盾抵消（LOWEST）：对应类型盾优先、白盾兜底；虚空伤害任何盾都不抵。 */
    @SubscribeEvent(priority = EventPriority.LOWEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        PlayerState state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        DamageSource source = event.getSource();
        if (isVoidDamage(source)) {
            return;
        }
        float amount = event.getAmount();
        if (amount <= 0.0F) {
            return;
        }
        if (isMagicDamage(source)) {
            amount = absorb(state.magic, amount);
            amount = absorb(state.white, amount);
        } else if (isPhysicalDamage(source)) {
            amount = absorb(state.physical, amount);
            amount = absorb(state.white, amount);
        } else {
            amount = absorb(state.white, amount);
        }
        event.setAmount(Math.max(0.0F, amount));
        sync(player);
    }

    private static float absorb(Pool pool, float amount) {
        if (amount <= 0.0F || pool.amount <= 0.0F) {
            return amount;
        }
        float absorbed = Math.min(pool.amount, amount);
        pool.amount -= absorbed;
        if (pool.amount <= 1.0E-4F) {
            pool.amount = 0.0F;
        }
        return amount - absorbed;
    }

    /** 虚空伤害（含本模组的真实伤害语义 fellOutOfWorld）：不可被护盾抵挡。 */
    private static boolean isVoidDamage(DamageSource source) {
        return source.is(DamageTypes.FELL_OUT_OF_WORLD) || source.typeHolder().unwrapKey()
                .map(key -> key.location().getPath().equals("out_of_world"))
                .orElse(false);
    }

    /** 魔法伤害：铁魔法学派（本模组的 LoL 魔法口径）+ 原版魔法/间接魔法。 */
    private static boolean isMagicDamage(DamageSource source) {
        boolean irons = source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals("irons_spellbooks"))
                .orElse(false);
        return irons || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    /** 物理伤害：弹射物（箭/三叉戟/烟花等）与近战（带来源实体的直接攻击）。 */
    private static boolean isPhysicalDamage(DamageSource source) {
        if (source.is(DamageTypeTags.IS_PROJECTILE) || source.getDirectEntity() instanceof Projectile) {
            return true;
        }
        return source.getEntity() instanceof net.minecraft.world.entity.LivingEntity;
    }

    /** 每 tick：到期池清零；余额变化即时同步，另有每 2 秒强制对账兜底。 */
    private static void tick(ServerPlayer player) {
        UUID uuid = player.getUUID();
        PlayerState state = ACTIVE.get(uuid);
        long now = System.currentTimeMillis();
        boolean changed = false;
        if (state != null) {
            for (Pool pool : new Pool[]{state.white, state.magic, state.physical}) {
                if (pool.hasRecord() && now >= pool.untilMs) {
                    pool.source = "";
                    pool.amount = 0.0F;
                    pool.untilMs = 0L;
                    changed = true;
                }
            }
        }
        float[] values = amounts(uuid);
        float[] last = LAST_SYNC.get(uuid);
        if (changed || last == null || last[0] != values[0] || last[1] != values[1]
                || last[2] != values[2] || player.tickCount % 40 == 0) {
            LAST_SYNC.put(uuid, values.clone());
            LOLNetworking.sendShieldSync(player, values[0], values[1], values[2]);
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (event.player instanceof ServerPlayer serverPlayer) {
            tick(serverPlayer);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        ACTIVE.remove(event.getEntity().getUUID());
        LAST_SYNC.remove(event.getEntity().getUUID());
    }

    @SubscribeEvent
    public static void onPlayerClone(PlayerEvent.Clone event) {
        if (event.isWasDeath()) {
            ACTIVE.remove(event.getEntity().getUUID());
            LAST_SYNC.remove(event.getEntity().getUUID());
        }
    }
}
