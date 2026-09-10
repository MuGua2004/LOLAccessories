package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.combat.SpellPenetration;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 魔法/物理伤害的百分比减免结算（挂在 {@link LivingHurtEvent} 上做乘法折算，对护甲/其它减伤类事件互不干扰）。
 *
 * <p><b>传说魔法抗性（lolaccessories:magic_resist）</b>：本模组自建属性（见
 * {@link ModAttributes#LOL_MAGIC_RESIST}），数值语义为「抗性点数」，直接应用英雄联盟法抗公式：
 * {@code 减伤 = MR / (100 + MR)}，即受到的魔法伤害乘上 {@code 100 / (100 + MR)}。
 * 减免对象：原版魔法伤害（{@code minecraft:magic} / {@code minecraft:indirect_magic}，
 * 如瞬间伤害、喷溅药水等）以及铁魔法法术伤害（{@link IronsCompat#isIronSpellDamage(DamageSource)}）。
 * 法术穿透（先百分比后固定）只对铁魔法法术伤害生效。</p>
 *
 * <p><b>魔法伤害百分比减免（lolaccessories:magic_damage_reduction）</b>：英雄联盟口径的
 * 「魔法伤害百分比减免」独立乘区，与上方法抗减免相乘：{@code 最终伤害 = 原伤害 × 100/(100+MR) × (1 − 减伤比例)}。
 * 有装备用到该属性时无需改结算点。</p>
 *
 * <p><b>物理伤害百分比减免（lolaccessories:physical_damage_reduction）</b>：对「非魔法且非真实」的伤害
 * （近战/远程/爆炸/坠落等）按 {@code ×(1 − 减伤比例)} 独立乘区折算。真实伤害统一走原版
 * {@code minecraft:out_of_world}（虚空）语义，物理/魔法两类减免都放行。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolDefenseEvents {

    private LolDefenseEvents() {
    }

    /** 是否为魔法伤害：原版魔法/间接魔法 + 铁魔法法术伤害。 */
    private static boolean isMagicDamage(DamageSource source) {
        return source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC)
                || IronsCompat.isIronSpellDamage(source);
    }

    /** 本模组语义下的真实伤害（原版虚空伤害，无视护甲/魔抗/百分比减伤）。 */
    private static boolean isTrueDamage(DamageSource source, LivingEntity victim) {
        return source.type().equals(victim.level().damageSources().fellOutOfWorld().type());
    }

    private static double readReduction(LivingEntity entity, Attribute attribute) {
        if (entity == null || attribute == null) {
            return 0.0D;
        }
        AttributeInstance instance = entity.getAttribute(attribute);
        if (instance == null) {
            return 0.0D;
        }
        return Math.max(0.0D, Math.min(1.0D, instance.getValue()));
    }

    @SubscribeEvent
    public static void onMagicDamageTaken(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide || !victim.isAlive()
                || event.getSource() == null) {
            return;
        }
        DamageSource source = event.getSource();
        if (!isMagicDamage(source)) {
            return;
        }
        float amount = event.getAmount();

        // 1) 传说魔法抗性（仅对铁魔法法术伤害叠加施法者法穿）
        AttributeInstance mrAttr = victim.getAttribute(ModAttributes.LOL_MAGIC_RESIST.get());
        if (mrAttr != null) {
            double flat = 0.0D;
            double pct = 0.0D;
            if (IronsCompat.isIronSpellDamage(source)
                    && source.getEntity() instanceof LivingEntity attacker
                    && attacker != victim && SpellPenetration.hasAny(attacker)) {
                flat = SpellPenetration.getFlatPenetration(attacker);
                pct = SpellPenetration.getPercentPenetration(attacker);
            }
            double mr = mrAttr.getValue();
            double effectiveMr = SpellPenetration.effective(mr, flat, pct);
            if (effectiveMr > 0.0D) {
                // LoL 法抗公式：受到的伤害 = 原始伤害 × 100/(100+MR)
                amount *= (float) (100.0D / (100.0D + effectiveMr));
            }
        }

        // 2) 魔法伤害百分比减免（独立乘区，与上方法抗减免相乘）
        double magicReduction = readReduction(victim, ModAttributes.LOL_MAGIC_DAMAGE_REDUCTION.get());
        if (magicReduction > 0.0D) {
            amount *= (float) (1.0D - magicReduction);
        }

        if (amount < event.getAmount()) {
            event.setAmount(Math.max(0.0F, amount));
        }
    }

    @SubscribeEvent
    public static void onPhysicalDamageTaken(LivingHurtEvent event) {
        if (event.isCanceled()) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (victim == null || victim.level().isClientSide || !victim.isAlive()
                || event.getSource() == null) {
            return;
        }
        DamageSource source = event.getSource();
        // 魔法伤害在 onMagicDamageTaken 处理，真实伤害（虚空语义）不做任何百分比减免
        if (isMagicDamage(source) || isTrueDamage(source, victim)) {
            return;
        }
        double physicalReduction = readReduction(victim, ModAttributes.LOL_PHYSICAL_DAMAGE_REDUCTION.get());
        if (physicalReduction <= 0.0D) {
            return;
        }
        float reduced = event.getAmount() * (float) (1.0D - physicalReduction);
        event.setAmount(Math.max(0.0F, reduced));
    }
}
