package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.money.GoldWallet;
import io.redspace.ironsspellbooks.api.registry.SpellRegistry;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import io.redspace.ironsspellbooks.api.magic.MagicData;
import io.redspace.ironsspellbooks.capabilities.magic.CooldownInstance;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 第七批传说装备被动：纳沃利烁刃 / 收集者 / 星蚀 / 巨蛇之牙。
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolSeventhBatchPassiveEvents {

    public static final String GEAR_NAAVORI = "naavori_flickerblade";
    public static final String GEAR_COLLECTOR = "the_collector";
    public static final String GEAR_ECLIPSE = "eclipse";
    public static final String GEAR_SERPENTS_FANG = "serpents_fang";

    private static final Map<UUID, long[]> ECLIPSE_STACKS = new HashMap<>();
    private static final Map<UUID, Long> ECLIPSE_CD = new HashMap<>();

    private LolSeventhBatchPassiveEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGH)
    public static void onLivingHurtAttacker(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker) || attacker == event.getEntity()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        long now = System.currentTimeMillis();
        if (!LolNewEpicPassiveEvents.isEnemyOf(attacker, victim)) {
            return;
        }
        float amount = event.getAmount();

        if (CuriosGearWear.isWearing(attacker, GEAR_NAAVORI) && isBasicAttack(source, attacker)) {
            GearConfig config = GearConfigManager.get(GEAR_NAAVORI);
            GearConfig.OnHitEffect effect = config == null
                    ? null : config.findEffect("naavori_flicker").orElse(null);
            if (effect != null && effect.enabled) {
                reduceBasicCooldowns(attacker, effect.amount > 0 ? effect.amount : 0.15D);
            }
        }
        if (CuriosGearWear.isWearing(attacker, GEAR_COLLECTOR)) {
            GearConfig config = GearConfigManager.get(GEAR_COLLECTOR);
            GearConfig.OnHitEffect effect = config == null
                    ? null : config.findEffect("collector_execute").orElse(null);
            if (effect != null && effect.enabled) {
                double threshold = (effect.amount > 0 ? effect.amount : 0.05D) * victim.getMaxHealth();
                double after = victim.getHealth() - amount;
                if (after > 0.0D && after <= threshold) {
                    event.setAmount(victim.getHealth() + 1.0F);
                }
            }
        }
        if (CuriosGearWear.isWearing(attacker, GEAR_SERPENTS_FANG)) {
            GearConfig config = GearConfigManager.get(GEAR_SERPENTS_FANG);
            GearConfig.OnHitEffect effect = config == null
                    ? null : config.findEffect("shield_reaver").orElse(null);
            if (effect != null && effect.enabled && ShieldHpService.hasAnyShield(victim)) {
                event.setAmount(amount * (float) (1.0D + (effect.amount > 0 ? effect.amount : 0.05D)));
            }
        }
        if (CuriosGearWear.isWearing(attacker, GEAR_ECLIPSE)) {
            Long cd = ECLIPSE_CD.get(victim.getUUID());
            if (cd == null || now >= cd) {
                GearConfig config = GearConfigManager.get(GEAR_ECLIPSE);
                GearConfig.OnHitEffect effect = config == null
                        ? null : config.findEffect("ever_rising_moon").orElse(null);
                if (effect != null && effect.enabled) {
                    long window = Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D) * 1000.0D);
                    long[] stack = ECLIPSE_STACKS.computeIfAbsent(victim.getUUID(), k -> new long[2]);
                    if (now > stack[1]) {
                        stack[0] = 0;
                    }
                    stack[1] = now + window;
                    stack[0]++;
                    if (stack[0] >= 2) {
                        stack[0] = 0;
                        ECLIPSE_CD.put(victim.getUUID(), now + Math.round(
                                (effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 6.0D) * 1000.0D));
                        AttributeInstance ad = attacker.getAttribute(Attributes.ATTACK_DAMAGE);
                        double extraAd = ad == null ? 0.0D : Math.max(0.0D, ad.getValue() - ad.getBaseValue());
                        boolean ranged = isRangedWeapon(attacker);
                        double baseShield = ranged
                                ? (effect.base_damage > 0 ? effect.base_damage : 75.0D)
                                : (effect.amount > 0 ? effect.amount : 150.0D);
                        double adRatio = ranged
                                ? (effect.power_ratio > 0 ? effect.power_ratio : 0.20D)
                                : (effect.bonus_pct > 0 ? effect.bonus_pct : 0.40D);
                        ShieldHpService.apply(attacker, "eclipse",
                                (float) (baseShield + adRatio * extraAd),
                                Math.round((effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D) * 1000.0D),
                                ShieldType.WHITE);
                        float proc = (float) ((ranged
                                ? (effect.melee_ratio > 0 ? effect.melee_ratio : 0.05D)
                                : (effect.max_health_pct > 0 ? effect.max_health_pct : 0.08D))
                                * victim.getMaxHealth());
                        event.setAmount(event.getAmount() + proc);
                    }
                }
            }
        }
    }

    /** 收集者·税：击杀敌方目标提供 25 金币。 */
    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer killer)) {
            return;
        }
        if (!CuriosGearWear.isWearing(killer, GEAR_COLLECTOR)
                || !LolNewEpicPassiveEvents.isEnemyOf(killer, event.getEntity())) {
            return;
        }
        GearConfig config = GearConfigManager.get(GEAR_COLLECTOR);
        GearConfig.OnHitEffect effect = config == null
                ? null : config.findEffect("collector_toll").orElse(null);
        if (effect != null && effect.enabled) {
            GoldWallet.giveCoins(killer, (int) (effect.amount > 0 ? effect.amount : 25.0D));
        }
    }

    private static int reduceBasicCooldowns(ServerPlayer player, double factor) {
        try {
            return IronsCooldownHelper.reduce(player, factor);
        } catch (LinkageError e) {
            return 0;
        } catch (Exception e) {
            LOLAccessories.LOGGER.warn("[LOLAccessories] naavori cooldown reduce failed: {}", e.toString());
            return 0;
        }
    }

    private static final class IronsCooldownHelper {
        static int reduce(ServerPlayer player, double factor) {
            var magicData = MagicData.getPlayerMagicData(player);
            if (magicData == null) {
                return 0;
            }
            var cooldowns = magicData.getPlayerCooldowns();
            if (cooldowns == null || cooldowns.getSpellCooldowns().isEmpty()) {
                return 0;
            }
            int affected = 0;
            var entries = new ArrayList<>(cooldowns.getSpellCooldowns().entrySet());
            for (Map.Entry<String, CooldownInstance> entry : entries) {
                CooldownInstance ci = entry.getValue();
                if (ci == null || ci.getCooldownRemaining() <= 0) {
                    continue;
                }
                AbstractSpell spell = SpellRegistry.getSpell(entry.getKey());
                if (spell == null || spell.getManaCost(1) >= 200) {
                    continue;
                }
                int remain = ci.getCooldownRemaining();
                int newRemain = (int) Math.floor(remain * (1.0D - factor));
                if (newRemain < remain) {
                    cooldowns.addCooldown(entry.getKey(), ci.getSpellCooldown(), newRemain);
                    affected++;
                }
            }
            if (affected > 0) {
                cooldowns.syncToPlayer(player);
            }
            return affected;
        }
    }

    private static boolean isBasicAttack(DamageSource source, ServerPlayer attacker) {
        if (source.typeHolder().unwrapKey()
                .map(key -> key.location().getNamespace().equals("irons_spellbooks"))
                .orElse(false)
                || source.is(DamageTypes.MAGIC) || source.is(DamageTypes.INDIRECT_MAGIC)) {
            return false;
        }
        if (source.getDirectEntity() == attacker) {
            return true;
        }
        return source.getDirectEntity() instanceof Projectile projectile
                && projectile.getOwner() != null
                && projectile.getOwner().getUUID().equals(attacker.getUUID());
    }

    private static boolean isRangedWeapon(ServerPlayer player) {
        return player.getMainHandItem().getItem() instanceof net.minecraft.world.item.ProjectileWeaponItem;
    }
}
