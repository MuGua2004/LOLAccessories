package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraft.resources.ResourceLocation;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/** 第十五批被动：涌动/盛名/恶劣衰朽/损毁/通电·苍穹。数值来自 gear JSON。 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolNewGearPassiveEvents {

    private static final String GEAR_AXIOM = "axiom_arc";
    private static final String GEAR_HUBRIS = "hubris";
    private static final String GEAR_BLOODLETTERS = "bloodletters_curse";
    private static final String GEAR_ABYSSAL = "abyssal_mask";
    private static final String GEAR_VOLTAIC = "voltaic_cyclosword";
    private static final String GEAR_IMMORTAL = "immortal_path";
    private static final String GEAR_SWIFTMARCH = "swiftmarch";
    private static final String GEAR_CRIMSON = "crimson_lucidity";
    private static final String GEAR_CHAINLACED = "chainlaced_crushers";
    private static final String GEAR_ARMORED = "armored_advance";
    private static final String GEAR_SYMBIOTE = "symbiote_soles";
    /** 暴食：全能吸血修饰器 UUID（0.6%×层数，永久）。 */
    private static final UUID BLOODFEAST_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000095");
    /** 暴食：佩戴者 → 层数。 */
    private static final Map<UUID, Integer> BLOODFEAST_STACKS = new HashMap<>();

    private static final UUID FLUX_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000091");
    private static final UUID NOTORIETY_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000092");
    private static final UUID BLIGHT_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000093");
    private static final UUID PIERCE_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000094");
    private static final String RUIN_TAG = "lolaccessories_ruin_till";
    private static final UUID IMMORTAL_FEAST_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000096");
    private static final UUID FERVOR_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000097");
    private static final UUID NOXIAN_HASTE_UUID = UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000098");

    /** 受害者 UUID → {佩戴者 UUID, 毫秒}（涌动/盛名 3 秒归因）。 */
    private static final Map<UUID, Object[]> RECENT = new HashMap<>();
    private static final Map<UUID, Integer> HUBRIS_KILLS = new HashMap<>();
    /** 佩戴者 UUID → {层数, 到期毫秒}。 */
    private static final Map<UUID, Object[]> BLIGHT = new HashMap<>();
    private static final Map<UUID, Integer> CHARGE = new HashMap<>();
    /** 佩戴者 UUID → {上帧X, 上帧Z, 累计距离, 穿甲到期毫秒}。 */
    private static final Map<UUID, double[]> VOLTAIC = new HashMap<>();
    private static final Map<UUID, Integer> IMMORTAL_STACKS = new HashMap<>();
    private static final Map<UUID, Long> NOXIAN_PERSIST_CD = new HashMap<>();
    private static final Map<UUID, Long> NOXIAN_ENDURE_CD = new HashMap<>();

    private LolNewGearPassiveEvents() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (source.getEntity() instanceof ServerPlayer attacker && attacker != victim) {
            RECENT.put(victim.getUUID(), new Object[]{attacker.getUUID(), System.currentTimeMillis()});
            if (LolNewEpicPassiveEvents.isPassiveFriendly(victim)) {
                return;
            }
            if (isMagicDamage(source) && CuriosGearWear.isWearing(attacker, GEAR_BLOODLETTERS)) {
                refreshBlight(attacker);
            }
            // 不朽之路·现在到永远：一半生命以上 → 造成 4% 额外伤害
            if (CuriosGearWear.isWearing(attacker, GEAR_IMMORTAL)
 && attacker.getHealth() > attacker.getMaxHealth() * 0.5D) {
                event.setAmount(event.getAmount() * 1.04F);
            }
            // 猩红明朗·诺克萨斯的急速：魔法伤害命中英雄 → 移速 10%/8%，4 秒
            if (CuriosGearWear.isWearing(attacker, GEAR_CRIMSON) && isMagicDamage(source)
                    && isChampionLike(victim)) {
                applyNoxianHaste(attacker);
            }
            // 带链碾碎者·不懈 / 装甲战靴·耐久：受英雄魔法/物理伤害 → 对应护盾
            if (source.getEntity() instanceof LivingEntity damager && isChampionLike(damager)
                    && victim instanceof ServerPlayer defender) {
                if (isMagicDamage(source) && CuriosGearWear.isWearing(defender, GEAR_CHAINLACED)) {
                    applyNoxianShield(defender, NOXIAN_PERSIST_CD, true);
                }
                if (!isMagicDamage(source) && CuriosGearWear.isWearing(defender, GEAR_ARMORED)) {
                    applyNoxianShield(defender, NOXIAN_ENDURE_CD, false);
                }
            }

            if (CuriosGearWear.isWearing(attacker, GEAR_VOLTAIC)) {
                int charge = Math.min(100, CHARGE.getOrDefault(attacker.getUUID(), 0) + 6);
                CHARGE.put(attacker.getUUID(), charge);
                if (charge >= 100) {
                    consumeVoltaic(attacker, victim, event);
                }
            }
        }
        // 深渊损毁：目标 12 格内有佩戴者 → 魔法伤害 +12%（不对友善或被动生物生效）
        if (isMagicDamage(source) && !LolNewEpicPassiveEvents.isPassiveFriendly(victim)) {
            for (Player p : victim.level().getEntitiesOfClass(Player.class,
                    victim.getBoundingBox().inflate(12.0D))) {
                if (p instanceof ServerPlayer wearer && p != victim
                        && wearer.distanceToSqr(victim) <= 144.0D
                        && CuriosGearWear.isWearing(wearer, GEAR_ABYSSAL)) {
                    event.setAmount(event.getAmount() * 1.12F);
                    break;
                }
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity dead = event.getEntity();
        Object[] rec = RECENT.remove(dead.getUUID());
        if (rec == null || System.currentTimeMillis() - (long) rec[1] > 3000L
                || dead.level().getServer() == null) {
            return;
        }
        ServerPlayer killer = dead.level().getServer().getPlayerList().getPlayer((UUID) rec[0]);
        if (killer == null) {
            return;
        }
        // 涌动：+500 终极技能急速，2 秒
        if (CuriosGearWear.isWearing(killer, GEAR_AXIOM) && isChampionLike(dead)) {
            AttributeInstance attr = killer.getAttribute(ModAttributes.LOL_ULTIMATE_HASTE.get());
            if (attr != null) {
                attr.removeModifier(FLUX_UUID);
                attr.addTransientModifier(new AttributeModifier(FLUX_UUID,
                        "lolaccessories_flux", 500.0D, Operation.ADDITION));
            }
            killer.addEffect(new MobEffectInstance(MobEffects.GLOWING, 40, 0, false, false, false));
        }
        // 涌动目标限定英雄类（玩家/敌对生物）由下方 isChampionLike 判定处理
        if (CuriosGearWear.isWearing(killer, GEAR_AXIOM) && !isChampionLike(dead)) {
            return;
        }
        // 暴食：参与击杀英雄 → +0.6% 全能吸血（至多 10 层，永久）
        if (CuriosGearWear.isWearing(killer, GEAR_SYMBIOTE) && isChampionLike(dead)) {
            int stacks = Math.min(10, BLOODFEAST_STACKS.merge(killer.getUUID(), 1, Integer::sum));
            AttributeInstance omni = killer.getAttribute(ModAttributes.LOL_OMNIVAMP.get());
            if (omni != null) {
                omni.removeModifier(BLOODFEAST_UUID);
                omni.addTransientModifier(new AttributeModifier(BLOODFEAST_UUID, "lolaccessories_bloodfeast", 0.006D * stacks, Operation.ADDITION));
            }
        }
        // 不朽之路·杀戮：参与击杀英雄 → +0.6% 全能吸血（至多 10 层，永久）
        if (CuriosGearWear.isWearing(killer, GEAR_IMMORTAL) && isChampionLike(dead)) {
            int stacks = Math.min(10, IMMORTAL_STACKS.merge(killer.getUUID(), 1, Integer::sum));
            AttributeInstance omni = killer.getAttribute(ModAttributes.LOL_OMNIVAMP.get());
            if (omni != null) {
                omni.removeModifier(IMMORTAL_FEAST_UUID);
                omni.addTransientModifier(new AttributeModifier(IMMORTAL_FEAST_UUID,
                        "lolaccessories_immortal_feast", 0.006D * stacks, Operation.ADDITION));
            }
        }
        // 盛名：击杀任意非友善或被动生物（任意 Mob）即可触发；击杀数仅击杀玩家累计
        if (CuriosGearWear.isWearing(killer, GEAR_HUBRIS)
                && (dead instanceof Mob || dead instanceof Player)) {
            if (dead instanceof Player) {
                HUBRIS_KILLS.merge(killer.getUUID(), 1, Integer::sum);
            }
            int kills = HUBRIS_KILLS.getOrDefault(killer.getUUID(), 0);
            AttributeInstance attr = killer.getAttribute(Attributes.ATTACK_DAMAGE);
            if (attr != null) {
                attr.removeModifier(NOTORIETY_UUID);
                attr.addTransientModifier(new AttributeModifier(NOTORIETY_UUID,
                        "lolaccessories_notoriety", 12.0D + 3.0D * kills, Operation.ADDITION));
            }
            killer.addEffect(new MobEffectInstance(MobEffects.GLOWING, 60, 0, false, false, false));
        }
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        RECENT.entrySet().removeIf(e -> now - (long) e.getValue()[1] > 3500L);
        // 恶劣衰朽：过期或摘装备清层
        Object[] blight = BLIGHT.get(uuid);
        boolean blightGone = blight != null && ((long) blight[1] <= now
                || !CuriosGearWear.isWearing(player, GEAR_BLOODLETTERS));
        if (blightGone) {
            BLIGHT.remove(uuid);
            AttributeInstance attr = player.getAttribute(ModAttributes.LOL_MAGIC_PEN_PERCENT.get());
            if (attr != null) {
                attr.removeModifier(BLIGHT_UUID);
            }
        }
        // 迅捷行军·诺克萨斯的狂热：适应之力 = 移速加成百分比 × 30
        if (CuriosGearWear.isWearing(player, GEAR_SWIFTMARCH)) {
            AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
            if (ms != null) {
                double bonusPct = Math.max(0.0D, ms.getValue() / 0.1D - 1.0D);
                AttributeInstance af = player.getAttribute(ModAttributes.LOL_ADAPTIVE_FORCE.get());
                if (af != null) {
                    af.removeModifier(FERVOR_UUID);
                    if (bonusPct > 0.0D) {
                        af.addTransientModifier(new AttributeModifier(FERVOR_UUID,
                                "lolaccessories_noxian_fervor", bonusPct * 30.0D, Operation.ADDITION));
                    }
                }
            }
        } else {
            AttributeInstance af = player.getAttribute(ModAttributes.LOL_ADAPTIVE_FORCE.get());
            if (af != null && af.getModifier(FERVOR_UUID) != null) {
                af.removeModifier(FERVOR_UUID);
            }
        }
        // 不朽之路·现在到永远（治疗/回复侧）：一半生命以下 → +12% 治疗强度与自然回复
        boolean immortalLow = CuriosGearWear.isWearing(player, GEAR_IMMORTAL)
 && player.getHealth() < player.getMaxHealth() * 0.5D;
        AttributeInstance heal = player.getAttribute(ModAttributes.LOL_HEAL_POWER.get());
        AttributeInstance regen = player.getAttribute(ModAttributes.LOL_NATURAL_REGEN.get());
        if (immortalLow) {
            if (heal != null && heal.getModifier(NOTORIETY_UUID) == null) {
                heal.addTransientModifier(new AttributeModifier(NOTORIETY_UUID,
                        "lolaccessories_immortal_heal", 0.12D, Operation.ADDITION));
            }
            if (regen != null && regen.getModifier(NOTORIETY_UUID) == null) {
                regen.addTransientModifier(new AttributeModifier(NOTORIETY_UUID,
                        "lolaccessories_immortal_regen", 0.12D, Operation.ADDITION));
            }
        } else {
            if (heal != null) {
                heal.removeModifier(NOTORIETY_UUID);
            }
            if (regen != null) {
                regen.removeModifier(NOTORIETY_UUID);
            }
        }
        // 不朽之路：摘装备清杀戮层数与全能吸血
        if (!CuriosGearWear.isWearing(player, GEAR_IMMORTAL)) {
            Integer ist = IMMORTAL_STACKS.remove(uuid);
            if (ist != null) {
                AttributeInstance omni = player.getAttribute(ModAttributes.LOL_OMNIVAMP.get());
                if (omni != null) {
                    omni.removeModifier(IMMORTAL_FEAST_UUID);
                }
            }
        }
        // 电震：移动充能（每 2 格 +1）与穿甲过期
        double[] vt = VOLTAIC.get(uuid);
        boolean wearing = CuriosGearWear.isWearing(player, GEAR_VOLTAIC);
        if (wearing) {
            if (vt == null) {
                vt = new double[]{player.getX(), player.getZ(), 0.0D, 0.0D};
                VOLTAIC.put(uuid, vt);
            }
            double dist = Math.sqrt(Math.pow(player.getX() - vt[0], 2) + Math.pow(player.getZ() - vt[1], 2));
            vt[0] = player.getX();
            vt[1] = player.getZ();
            vt[2] += dist;
            while (vt[2] >= 2.0D) {
                vt[2] -= 2.0D;
                CHARGE.put(uuid, Math.min(100, CHARGE.getOrDefault(uuid, 0) + 1));
            }
            if (vt[3] > 0 && vt[3] <= now) {
                vt[3] = 0.0D;
                AttributeInstance attr = player.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(
                        new ResourceLocation("attributeslib", "armor_pierce")));
                if (attr != null) {
                    attr.removeModifier(PIERCE_UUID);
                }
            }
        } else if (vt != null) {
            VOLTAIC.remove(uuid);
            CHARGE.remove(uuid);
            AttributeInstance attr = player.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(
                    new ResourceLocation("attributeslib", "armor_pierce")));
            if (attr != null) {
                attr.removeModifier(PIERCE_UUID);
            }
        }
    }

    @SubscribeEvent
    public static void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        RECENT.entrySet().removeIf(e -> e.getKey().equals(uuid) || (e.getValue()[0]).equals(uuid));
        HUBRIS_KILLS.remove(uuid);
        IMMORTAL_STACKS.remove(uuid);
        NOXIAN_PERSIST_CD.remove(uuid);
        NOXIAN_ENDURE_CD.remove(uuid);
        Integer bf = BLOODFEAST_STACKS.remove(uuid);
        if (bf != null) {
            AttributeInstance omni = event.getEntity().getAttribute(ModAttributes.LOL_OMNIVAMP.get());
            if (omni != null) {
                omni.removeModifier(BLOODFEAST_UUID);
            }
        }
        BLIGHT.remove(uuid);
        CHARGE.remove(uuid);
        VOLTAIC.remove(uuid);
    }

    /** 猩红明朗·诺克萨斯的急速：移速 10%（近战）/8%（远程），4 秒。 */
    private static void applyNoxianHaste(ServerPlayer player) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        double[] vt = VOLTAIC.get(uuid);
        if (vt != null && vt.length > 4 && vt[4] > now) {
            return;
        }
        boolean ranged = player.getMainHandItem().getItem()
                instanceof net.minecraft.world.item.ProjectileWeaponItem;
        double pct = ranged ? 0.08D : 0.10D;
        AttributeInstance ms = player.getAttribute(Attributes.MOVEMENT_SPEED);
        if (ms != null) {
            ms.removeModifier(NOXIAN_HASTE_UUID);
            ms.addTransientModifier(new AttributeModifier(NOXIAN_HASTE_UUID,
                    "lolaccessories_noxian_haste", pct, Operation.MULTIPLY_TOTAL));
        }
        if (vt == null) {
            vt = new double[]{player.getX(), player.getZ(), 0.0D, 0.0D, now + 4000L};
            VOLTAIC.put(uuid, vt);
        } else {
            vt[4] = now + 4000L;
        }
    }

    /** 不懈/耐久：魔法或物理护盾（100-200 随等级 + 8% 额外生命，5 秒，独立冷却 15 秒）。 */
    private static void applyNoxianShield(ServerPlayer player, Map<UUID, Long> cdMap, boolean magic) {
        UUID uuid = player.getUUID();
        long now = System.currentTimeMillis();
        Long cd = cdMap.get(uuid);
        if (cd != null && now < cd) {
            return;
        }
        cdMap.put(uuid, now + 15000L);
        int level = Math.max(1, player.experienceLevel);
        double base = 100.0D + Math.min(1.0D, (level - 1) / 17.0D) * 100.0D;
        double bonusHealth = Math.max(0.0D, player.getMaxHealth() - 20.0D) * 0.08D;
        float shield = (float) (base + bonusHealth);
        ShieldHpService.apply(player, "noxian_shoes", shield, 5000L,
                magic ? ShieldType.MAGIC : ShieldType.PHYSICAL);
    }

    /** 猩红明朗：铁魔法施法触发诺克萨斯的急速（由 ISS 联动层调用）。 */
    public static void onIronSpellCast(ServerPlayer player) {
        if (CuriosGearWear.isWearing(player, GEAR_CRIMSON)) {
            applyNoxianHaste(player);
        }
    }

    /** 恶劣衰朽：叠层（至多 4）并刷新 6 秒，同步百分比法穿。 */
    private static void refreshBlight(ServerPlayer attacker) {
        UUID uuid = attacker.getUUID();
        Object[] state = BLIGHT.get(uuid);
        int stacks = state == null ? 0 : Math.min(4, (int) state[0] + 1);
        BLIGHT.put(uuid, new Object[]{stacks, System.currentTimeMillis() + 6000L});
        AttributeInstance attr = attacker.getAttribute(ModAttributes.LOL_MAGIC_PEN_PERCENT.get());
        if (attr != null) {
            attr.removeModifier(BLIGHT_UUID);
            attr.addTransientModifier(new AttributeModifier(BLIGHT_UUID, "lolaccessories_blight",
                    0.075D * stacks, Operation.ADDITION));
        }
    }

    /** 电震触发：清空充能、附加当前生命百分比物理伤害、给 4 秒临时穿甲。 */
    private static void consumeVoltaic(ServerPlayer attacker, LivingEntity victim, LivingHurtEvent event) {
        CHARGE.put(attacker.getUUID(), 0);
        boolean ranged = attacker.getMainHandItem().getItem()
                instanceof net.minecraft.world.item.ProjectileWeaponItem;
        double pct = ranged ? 0.07D : 0.09D;
        double extra = victim.getHealth() * pct;
        if (!isChampionLike(victim)) {
            extra = Math.min(extra, 200.0D);
        }
        event.setAmount(event.getAmount() + (float) Math.max(0.0D, extra));
        double[] vt = VOLTAIC.computeIfAbsent(attacker.getUUID(),
                k -> new double[]{attacker.getX(), attacker.getZ(), 0.0D, 0.0D});
        vt[3] = System.currentTimeMillis() + 4000L;
        AttributeInstance attr = attacker.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("attributeslib", "armor_pierce")));
        if (attr != null) {
            attr.removeModifier(PIERCE_UUID);
            attr.addTransientModifier(new AttributeModifier(PIERCE_UUID, "lolaccessories_voltaic",
                    ranged ? 12.0D : 15.0D, Operation.ADDITION));
        }
    }

    private static boolean isChampionLike(LivingEntity entity) {
        return entity instanceof Player || entity instanceof Monster;
    }

    private static boolean isMagicDamage(DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)
                || IronsCompat.isIronSpellDamage(source);
    }
}
