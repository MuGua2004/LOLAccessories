package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.level.Level;
import net.minecraft.world.level.saveddata.SavedData;
import net.minecraft.world.level.storage.LevelResource;
import net.minecraft.world.phys.EntityHitResult;
import net.minecraftforge.event.entity.EntityJoinLevelEvent;
import net.minecraftforge.event.entity.ProjectileImpactEvent;
import net.minecraftforge.event.entity.player.AttackEntityEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import java.io.File;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 真理伤害：独立伤害源（点数 + 法强/攻击比例加成），不可阻隔、直削最大生命值、静滞与抹除。
 * 详见 {@link ModAttributes#LOL_TRUE_DAMAGE}。
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolTrueDamageEvents {

    private static final UUID TRUTH_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000070");
    /** 同一目标真理结算防重冷却标记（10 tick 内不重复结算，防快速连点/穿透箭刷伤害）。 */
    private static final String TRUTH_COOLDOWN_TAG = "lolaccessories_truth_cooldown";
    /** 真理削减累计值（权威值，存目标持久数据，随实体持久化）。 */
    private static final String TRUTH_LOST_TAG = "lolaccessories_truth_lost";
    /** 弹射物一次性真理标记（每支箭/三叉戟最多结算一次真理）。 */
    private static final String TRUTH_DONE_TAG = "lolaccessories_truth_done";
    /** 禁生成深层标记（EntityTypeCreateMixin 在 create 时打，入界时按此取消）。 */
    public static final String BANNED_SPAWN_TAG = "lolaccessories_banned_spawn";

    private LolTrueDamageEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onAttackEntity(AttackEntityEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        LivingEntity victim = resolveVictim(event.getTarget());
        if (victim == null || victim == attacker) {
            LOLAccessories.LOGGER.debug("[Truth] attack skipped: target {} is not a living entity",
                    event.getTarget().getType());
            return;
        }
        float truth = computeTruth(attacker);
        if (truth <= 0.0F) {
            LOLAccessories.LOGGER.debug("[Truth] attack skipped: attacker has no true damage");
            return;
        }
        applyTruthDamage(victim, truth);
    }

    /**
     * 弹射物入口：箭/三叉戟等命中实体即结算（先于 hurt 流程）——即使目标闪避
     * （原版末影人瞬移）、免疫或拦截普通伤害，真理部分也已在「接触」瞬间落地；
     * 只有完全没命中（射空）才不结算。与近战共用 10 tick 同目标防重。
     * multipart Boss（命中部位实体）会自动溯源到本体结算。
     *
     * <p>每支弹射物<b>只结算一次</b>真理（弹射物持久数据 {@code lolaccessories_truth_done}
     * 标记）：目标被真理命中后瞬移（如亚波伦/末影人）时箭不会钉住、可能继续飞行再次撞上
     * 同一目标，不做一次性标记就会双重结算。</p>
     */
    @SubscribeEvent
    public static void onProjectileImpact(ProjectileImpactEvent event) {
        if (event.getProjectile().level().isClientSide) {
            return;
        }
        if (!(event.getRayTraceResult() instanceof EntityHitResult hit)) {
            return;
        }
        LivingEntity victim = resolveVictim(hit.getEntity());
        if (victim == null) {
            LOLAccessories.LOGGER.debug("[Truth] impact skipped: hit {} is not a living entity",
                    hit.getEntity() == null ? "null" : hit.getEntity().getType());
            return;
        }
        if (!(event.getProjectile().getOwner() instanceof ServerPlayer attacker)
                || attacker == victim) {
            return;
        }
        // 一支弹射物只结算一次真理（防瞬移目标二次撞击/穿透多段重复）
        if (event.getProjectile().getPersistentData().getBoolean(TRUTH_DONE_TAG)) {
            LOLAccessories.LOGGER.debug("[Truth] impact skipped: projectile already spent its truth");
            return;
        }
        float truth = computeTruth(attacker);
        if (truth <= 0.0F) {
            LOLAccessories.LOGGER.debug("[Truth] impact skipped: owner has no true damage");
            return;
        }
        event.getProjectile().getPersistentData().putBoolean(TRUTH_DONE_TAG, true);
        applyTruthDamage(victim, truth);
    }

    /** 命中实体解析：普通 LivingEntity 直取；multipart Boss 的部位实体溯源到本体。 */
    private static LivingEntity resolveVictim(Entity entity) {
        if (entity instanceof LivingEntity living) {
            return living;
        }
        if (entity instanceof net.minecraftforge.entity.PartEntity<?> part
                && part.getParent() instanceof LivingEntity parent) {
            return parent;
        }
        return null;
    }

    /** 真理伤害总值 = 点数 + 法强加成比例 × 法术强度 + 攻击加成比例 × 攻击力。 */
    private static float computeTruth(ServerPlayer attacker) {
        return (float) (valueOf(attacker, ModAttributes.LOL_TRUE_DAMAGE.get())
                + valueOf(attacker, ModAttributes.LOL_TRUE_DAMAGE_AP_RATIO.get()) * spellPower(attacker)
                + valueOf(attacker, ModAttributes.LOL_TRUE_DAMAGE_AD_RATIO.get())
                        * valueOf(attacker, Attributes.ATTACK_DAMAGE));
    }

    private static double valueOf(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        AttributeInstance instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : instance.getValue();
    }

    private static double spellPower(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(
                net.minecraftforge.registries.ForgeRegistries.ATTRIBUTES.getValue(
                        new net.minecraft.resources.ResourceLocation("irons_spellbooks", "spell_power")));
        return instance == null ? 0.0D : instance.getValue();
    }

    /**
     * 真理伤害落地：只削减最大生命值属性，<b>不造成任何实际伤害</b>——
     * 全程不调用 {@code hurt}、不构造 {@code DamageSource}、不直接扣当前生命值。
     * 目标当前生命值仅在超过削减后的新上限时被引擎约束压回（血量百分比不变，
     * 满血目标削减后仍处于满血状态）；生物不会因真理伤害死亡，只有当最大生命值
     * 因此归零时被直接抹除。
     */
    private static void applyTruthDamage(LivingEntity victim, float amount) {
        // 同一目标 10 tick 内只结算一次（快速连点 / 穿透箭多段防刷）
        long now = victim.level().getGameTime();
        CompoundTag data = victim.getPersistentData();
        if (now - data.getLong(TRUTH_COOLDOWN_TAG) < 10L) {
            return;
        }
        data.putLong(TRUTH_COOLDOWN_TAG, now);
        if (victim instanceof ServerPlayer player) {
            applyTruthToPlayer(player, amount);
            return;
        }
        AttributeInstance maxAttr = victim.getAttribute(Attributes.MAX_HEALTH);
        if (maxAttr == null) {
            LOLAccessories.LOGGER.debug("[Truth] skipped: {} has no MAX_HEALTH attribute",
                    victim.getType());
            return;
        }
        // 判定前置：当前最大生命值 ≤ 本次真理伤害 → 直接移除（KubeJS remove 语义：
        // discard 出世界，不走死亡程序——无掉落物、无经验、无死亡事件），并列入存档禁生成名单
        if (maxAttr.getValue() <= amount) {
            LOLAccessories.LOGGER.info("[Truth] {} 最大生命 {} ≤ 真理伤害 {}，直接移除（不走死亡）",
                    ForgeRegistries.ENTITY_TYPES.getKey(victim.getType()), maxAttr.getValue(), amount);
            eraseEntity(victim);
            return;
        }
        reduceMaxHealth(victim, maxAttr, amount);
        double newMax = maxAttr.getValue();
        LOLAccessories.LOGGER.info("[Truth] {} 受到 {} 点真理伤害，最大生命 {} → {}",
                ForgeRegistries.ENTITY_TYPES.getKey(victim.getType()), amount, newMax + amount, newMax);
        if (victim.isAlive()) {
            victim.setHealth((float) Math.max(0.0D, Math.min(victim.getHealth(), newMax)));
        }
        if (victim instanceof Mob mob) {
            mob.setNoAi(true);
        }
    }
    /** 玩家特例：最大生命值至多削减到 1 点；多人中归零 → 踢出并删除玩家数据。 */
    private static void applyTruthToPlayer(ServerPlayer player, float amount) {
        AttributeInstance maxAttr = player.getAttribute(Attributes.MAX_HEALTH);
        if (maxAttr == null) {
            return;
        }
        boolean singlePlayerOwner = player.level().getServer().isSingleplayerOwner(player.getGameProfile());
        double floor = singlePlayerOwner ? 1.0D : 0.0D;
        double reduce = Math.min(amount, Math.max(0.0D, maxAttr.getValue() - floor));
        if (reduce > 0.0D) {
            reduceMaxHealth(player, maxAttr, reduce);
        }
        double newMax = maxAttr.getValue();
        if (player.isAlive()) {
            player.setHealth((float) Math.max(0.0D, Math.min(player.getHealth(), newMax)));
        }
        player.addEffect(new MobEffectInstance(MobEffects.MOVEMENT_SLOWDOWN, 100, 9, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.DIG_SLOWDOWN, 100, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.WEAKNESS, 100, 4, false, false, false));
        player.addEffect(new MobEffectInstance(MobEffects.BLINDNESS, 100, 0, false, false, false));
        if (newMax <= floor + 0.0001D && !singlePlayerOwner) {
            erasePlayer(player);
        }
    }

    /**
     * 累计削减最大生命值。权威累计值存目标持久数据（{@code lolaccessories_truth_lost}），
     * 属性修饰器只是其投影且幂等重挂——即使目标方的模组（如 Boss 限伤/属性锁定）清掉了
     * 修饰器，下次真理结算也会按累计值原样恢复，削减不会被「回滚」。
     */
    private static double reduceMaxHealth(LivingEntity victim, AttributeInstance maxAttr, double amount) {
        CompoundTag data = victim.getPersistentData();
        double alreadyLost = data.getDouble(TRUTH_LOST_TAG);
        double newLost = alreadyLost + amount;
        data.putDouble(TRUTH_LOST_TAG, newLost);
        maxAttr.removeModifier(TRUTH_UUID);
        maxAttr.addPermanentModifier(new AttributeModifier(TRUTH_UUID, "lolaccessories_truth_damage",
                -newLost, AttributeModifier.Operation.ADDITION));
        return newLost - alreadyLost;
    }

    private static void eraseEntity(LivingEntity victim) {
        var server = victim.level().getServer();
        if (server != null) {
            markBanned(server, victim.getType());
        }
        victim.discard();
    }

    private static void erasePlayer(ServerPlayer player) {
        MinecraftServer server = player.server;
        UUID uuid = player.getUUID();
        try {
            var save = net.minecraft.server.players.PlayerList.class.getDeclaredMethod(
                    "save", ServerPlayer.class);
            save.setAccessible(true);
            save.invoke(server.getPlayerList(), player);
        } catch (ReflectiveOperationException e) {
            LOLAccessories.LOGGER.warn("[Truth] cannot force-save player data: {}", e.toString());
        }
        File dir = server.getWorldPath(LevelResource.PLAYER_DATA_DIR).toFile();
        for (String suffix : new String[]{".dat", ".dat_old"}) {
            File file = new File(dir, uuid + suffix);
            if (file.exists() && !file.delete()) {
                LOLAccessories.LOGGER.warn("[Truth] cannot delete player data: {}", file.getName());
            }
        }
        player.connection.disconnect(net.minecraft.network.chat.Component.literal("你的存在已被真理抹除……"));
        LOLAccessories.LOGGER.info("[Truth] player {} erased: kicked and data deleted", uuid);
    }

    /** 存档级禁生成名单（SavedData，随世界持久化）。 */
    public static class ErasureSavedData extends SavedData {
        private final Set<String> banned = new HashSet<>();

        public static ErasureSavedData get(MinecraftServer server) {
            return server.overworld().getDataStorage()
                    .computeIfAbsent(ErasureSavedData::load, ErasureSavedData::new,
                            "lolaccessories_truth_erased");
        }

        public boolean isBanned(EntityType<?> type) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            return key != null && banned.contains(key.toString());
        }

        public void ban(EntityType<?> type) {
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key != null && banned.add(key.toString())) {
                setDirty();
            }
        }

        public static ErasureSavedData load(CompoundTag tag) {
            ErasureSavedData data = new ErasureSavedData();
            for (String id : tag.getString("banned").split("[,;]")) {
                if (!id.isBlank()) {
                    data.banned.add(id);
                }
            }
            return data;
        }

        @Override
        public CompoundTag save(CompoundTag tag) {
            tag.putString("banned", String.join(",", banned));
            return tag;
        }
    }

    private static void markBanned(MinecraftServer server, EntityType<?> type) {
        ErasureSavedData.get(server).ban(type);
        LOLAccessories.LOGGER.info("[Truth] {} erased and banned from spawning in this save",
                ForgeRegistries.ENTITY_TYPES.getKey(type));
    }

    /** 名单内的生物禁止加入世界（一切途径的生成都被拦截）。 */
    @SubscribeEvent
    public static void onEntityJoinLevel(EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        // 深层标记（EntityTypeCreateMixin 在 create 时打的）：一律禁止入界
        if (event.getEntity().getPersistentData().getBoolean(BANNED_SPAWN_TAG)) {
            event.setCanceled(true);
            return;
        }
        // 名单兜底（读档恢复的存量实体等未打标记的场景）
        if (event.getEntity() instanceof Mob mob && event.getLevel().getServer() != null
                && ErasureSavedData.get(event.getLevel().getServer()).isBanned(mob.getType())) {
            event.setCanceled(true);
        }
    }
}
