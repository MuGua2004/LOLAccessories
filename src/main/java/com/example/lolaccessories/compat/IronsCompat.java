package com.example.lolaccessories.compat;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraftforge.fml.ModList;

/**
 * 铁魔法（Iron's Spells 'n Spellbooks，modid irons_spellbooks）联动入口。
 *
 * <p>铁魔法已正式列为本模组前置（见 mods.toml）。代码层仍采用运行时探测（字符串类名、铁魔法
 * 自行注册到 {@code BuiltInRegistries.ATTRIBUTE} 的属性）引用其定义，避免产生编译期依赖，
 * 从而保持构建无需额外下载铁魔法构件；{@code isLoaded()} 的判断保留用于开发环境未装铁魔法时
 * 安全跳过联动逻辑。</p>
 */
public final class IronsCompat {

    /** 铁魔法 modid。 */
    public static final String MOD_ID = "irons_spellbooks";

    /** 法术吟唱（蓄力）时间缩减（MagicRangedAttribute，1.0 = 无加成基准，ADDITION +0.1 = -10% 吟唱时间）。 */
    public static final String CAST_TIME_REDUCTION = MOD_ID + ":cast_time_reduction";

    /** 铁魔法造成法术伤害时使用的伤害源实现类（仅在安装了铁魔法时才会出现）。 */
    private static final String SPELL_DAMAGE_SOURCE_CLASS =
            "io.redspace.ironsspellbooks.damage.SpellDamageSource";

    /** 铁魔法注册到原版属性注册表里的资源名（数值语义沿用其 MagicPercentAttribute：1.0 = 无加成）。 */
    public static final String ENDER_SPELL_POWER = MOD_ID + ":ender_spell_power";
    public static final String MAX_MANA = MOD_ID + ":max_mana";
    public static final String COOLDOWN_REDUCTION = MOD_ID + ":cooldown_reduction";
    /** 通用法术强度：任何学派的法术都吃到该加成（铁魔法 1.20.1 有该属性，资源名 spell_power）。 */
    public static final String SPELL_POWER = MOD_ID + ":spell_power";
    /** 法力恢复倍率（MagicPercentAttribute，1.0 = 无加成）。 */
    public static final String MANA_REGEN = MOD_ID + ":mana_regen";

    private IronsCompat() {
    }

    /** 是否已安装铁魔法。 */
    public static boolean isLoaded() {
        return ModList.get().isLoaded(MOD_ID);
    }

    /** 该伤害源是否来自铁魔法的法术伤害（通过类名识别，避免对铁魔法产生编译期依赖）。 */
    public static boolean isIronSpellDamage(DamageSource source) {
        return source != null
                && isLoaded()
                && SPELL_DAMAGE_SOURCE_CLASS.equals(source.getClass().getName());
    }

    /**
     * 注册「直接引用铁魔法事件 API」的 Forge 订阅器（如 {@code IronsSpellSchoolPen}）。
     *
     * <p>这类订阅器的方法签名里出现铁魔法事件类型，若在铁魔法缺席时被 JVM 加载会抛
     * {@code NoClassDefFoundError}。因此主类只调用本方法（不引用那个类），此处先探测
     * {@link #isLoaded()} 再用反射加载/注册，把类加载推迟到铁魔法确实存在之后。</p>
     */
    public static void registerDynamicSubscribers() {
        if (!isLoaded()) {
            return;
        }
        try {
            Class.forName("com.example.lolaccessories.compat.IronsSpellSchoolPen")
                    .getMethod("register")
                    .invoke(null);
            LOLAccessories.LOGGER.info("[LOLAccessories] 铁魔法法术穿透联动（IronsSpellSchoolPen）已注册");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOLAccessories.LOGGER.error("[LOLAccessories] 注册铁魔法法术穿透联动失败", e);
        }
        try {
            Class.forName("com.example.lolaccessories.compat.IronsLegendCastingEvents")
                    .getMethod("register")
                    .invoke(null);
            LOLAccessories.LOGGER.info("[LOLAccessories] 2026 传说施法联动（IronsLegendCastingEvents）已注册");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOLAccessories.LOGGER.error("[LOLAccessories] 注册 2026 传说施法联动失败", e);
        }
        try {
            Class.forName("com.example.lolaccessories.compat.IronsUltimateHasteEvents")
                    .getMethod("register")
                    .invoke(null);
            LOLAccessories.LOGGER.info("[LOLAccessories] 终极技能冷却缩减联动（IronsUltimateHasteEvents）已注册");
        } catch (ReflectiveOperationException | LinkageError e) {
            LOLAccessories.LOGGER.error("[LOLAccessories] 注册终极技能冷却缩减联动失败", e);
        }
    }
}
