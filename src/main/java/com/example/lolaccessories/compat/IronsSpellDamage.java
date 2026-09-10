package com.example.lolaccessories.compat;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageType;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;

/**
 * 铁魔法「学派伤害」：本模组里<b>装备技能造成的魔法伤害</b>统一走铁魔法（Iron's Spellbooks）
 * 的学派伤害类型，而不是原版 {@code magic()} / {@code indirectMagic()}。
 *
 * <p><b>为什么要这么做：</b>铁魔法在本模组中代表英雄联盟口径的「魔法伤害」——它虽按原版
 * 物理口径结算（吃护甲减免），但语义上是魔法。原版 {@code DamageTypes.MAGIC} 不携带学派
 * 信息、也不触发铁魔法的学派抗性/穿透与法术事件，因此装备技能的魔法伤害必须改成铁魔法的
 * 学派伤害源，才能与「所有装备造成的魔法伤害 = 铁魔法伤害」这条规则一致。</p>
 *
 * <p><b>学派 DamageType 来源：</b>铁魔法在 {@code data/irons_spellbooks/damage_type/} 下注册了
 * {@code fire_magic / ice_magic / lightning_magic / holy_magic / ender_magic / blood_magic /
 * evocation_magic / nature_magic / eldritch_magic}。这里按资源名取用，用原版
 * {@code DamageSources#source(DamageType, Entity)} 构造伤害源，避免直接依赖铁魔法的
 * 方法签名（铁魔法为 {@code compileOnly}，缺席时本类也不参与加载）。</p>
 *
 * <p><b>铁魔法缺席时</b>：回退到原版间接魔法伤害，保证未装铁魔法也能正常造成伤害。</p>
 */
public final class IronsSpellDamage {

    /** 火焰学派（黑焰火炬、引燃、献祭、日炎等）。 */
    public static final String FIRE = "irons_spellbooks:fire_magic";
    /** 冰霜学派（基克的聚合风暴等）。 */
    public static final String ICE = "irons_spellbooks:ice_magic";
    /** 闪电学派（海克斯科技系等）。 */
    public static final String LIGHTNING = "irons_spellbooks:lightning_magic";
    /** 神圣学派（护盾/治疗向技能等）。 */
    public static final String HOLY = "irons_spellbooks:holy_magic";
    /** 末影学派（卢登的回声等——原末影法强口径，技能学派保持不变）。 */
    public static final String ENDER = "irons_spellbooks:ender_magic";
    /** 血魔法学派（魔王之心、吸血向等）。 */
    public static final String BLOOD = "irons_spellbooks:blood_magic";
    /** 召唤学派（澄空之愿、弹射物向等）。 */
    public static final String EVOCATION = "irons_spellbooks:evocation_magic";
    /** 自然学派。 */
    public static final String NATURE = "irons_spellbooks:nature_magic";
    /** 邪术学派。 */
    public static final String ELDRITCH = "irons_spellbooks:eldritch_magic";

    private IronsSpellDamage() {
    }

    /**
     * 把配置里的学派名（如 {@code fire}）解析成铁魔法的 DamageType 资源名。
     * 留空或无法识别时退回 {@link #ENDER}（末影，本模组默认学派）。
     */
    public static String resolve(String school) {
        if (school == null || school.isEmpty()) {
            return ENDER;
        }
        String s = school.trim().toLowerCase(java.util.Locale.ROOT);
        if (s.contains(":")) {
            return s; // 已是完整资源名（如 irons_spellbooks:fire_magic），直接使用
        }
        return switch (s) {
            case "fire" -> FIRE;
            case "ice" -> ICE;
            case "lightning" -> LIGHTNING;
            case "holy" -> HOLY;
            case "ender" -> ENDER;
            case "blood" -> BLOOD;
            case "evocation" -> EVOCATION;
            case "nature" -> NATURE;
            case "eldritch" -> ELDRITCH;
            default -> MOD_ID_SPELL + s + "_magic";
        };
    }

    private static final String MOD_ID_SPELL = "irons_spellbooks:";

    /**
     * 装备默认学派映射（{@code gear_id/效果id -> 学派短名}）。
     *
     * <p>配置里写明 {@code school} 时配置优先；旧配置合并后可能没有该字段，
     * 此时用这张表兜底，保证每件装备的魔法伤害仍有自己的学派。</p>
     */
    private static final java.util.Map<String, String> DEFAULT_SCHOOLS = java.util.Map.ofEntries(
            java.util.Map.entry("ludens_echo/echo", "ender"),
            java.util.Map.entry("fated_ashes/inflame", "fire"),
            java.util.Map.entry("bamis_cinder/immolate", "fire"),
            java.util.Map.entry("sunfire_aegis/sunfire", "fire"),
            java.util.Map.entry("blackfire_torch/baleful_blaze", "fire"),
            java.util.Map.entry("dusk_and_dawn/spellblade", "holy"),
            java.util.Map.entry("sheen/spellblade", "holy"),
            java.util.Map.entry("zekes_convergence/zeal", "ice"),
            java.util.Map.entry("hextech_alternator/revved", "lightning"),
            java.util.Map.entry("scouts_slingshot/bullseye", "lightning"),
            java.util.Map.entry("unending_despair/anguish", "eldritch"));

    /** 配置未写 school 时的默认学派（无匹配返回 null，由调用方退回 ENDER）。 */
    public static String defaultSchoolFor(String gearId, String effectId) {
        return DEFAULT_SCHOOLS.get(gearId + '/' + effectId);
    }

    /**
     * 以指定学派造成魔法伤害。
     *
     * @param attacker     伤害来源（可为 null，表示无来源）
     * @param target       目标
     * @param amount       伤害量
     * @param damageTypeId 学派 DamageType 资源名（用本类常量，如 {@link #ENDER}）
     * @return 是否成功造成伤害
     */
    public static boolean apply(Entity attacker, LivingEntity target, float amount, String damageTypeId) {
        if (target == null || amount <= 0.0F) {
            return false;
        }
        if (!IronsCompat.isLoaded()) {
            // 铁魔法缺席：退回原版间接魔法伤害
            return target.hurt(target.damageSources().indirectMagic(attacker, attacker), amount);
        }
        try {
            ResourceKey<DamageType> key = ResourceKey.create(Registries.DAMAGE_TYPE,
                    new ResourceLocation(damageTypeId));
            Registry<DamageType> registry = target.level().registryAccess()
                    .registryOrThrow(Registries.DAMAGE_TYPE);
            if (!registry.containsKey(key)) {
                // 该学派类型不存在（铁魔法版本差异）：退回原版
                return target.hurt(target.damageSources().indirectMagic(attacker, attacker), amount);
            }
            // 1.20.1 的 DamageSources#source(ResourceKey|Holder, Entity) 均为 private，
            // 这里直接用公开的 DamageSource(Holder<DamageType>, Entity) 构造学派伤害源
            Holder<DamageType> holder = registry.getHolderOrThrow(key);
            DamageSource source = new DamageSource(holder, attacker);
            return target.hurt(source, amount);
        } catch (RuntimeException e) {
            LOLAccessories.LOGGER.warn("[LOLAccessories] 学派伤害 {} 结算失败，退回原版魔法伤害：{}",
                    damageTypeId, e.getMessage());
            return target.hurt(target.damageSources().indirectMagic(attacker, attacker), amount);
        }
    }
}