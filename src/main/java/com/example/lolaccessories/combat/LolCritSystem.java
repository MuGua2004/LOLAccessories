package com.example.lolaccessories.combat;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.entity.EchoOrbEntity;
import com.example.lolaccessories.event.Lol2026LegendPassiveEvents;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.item.GearItem;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * LOL 暴击系统。
 *
 * <p>独立于其它模组的暴击规则：佩戴者身上只要带有「LOL 暴击率」（>0）就参与判定，数值来自
 * Curios 装备通过原版属性修正器加到佩戴者身上的 {@link ModAttributes#LOL_CRIT_CHANCE} 与
 * {@link ModAttributes#LOL_CRIT_DAMAGE}。</p>
 *
 * <p>可暴击的伤害范围：</p>
 * <ul>
 *   <li><b>近战攻击</b>（伤害源直接实体就是佩戴者本人）；</li>
 *   <li><b>玩家射出的物理弹射物</b>（弓 / 弩 / 三叉戟等 {@link Projectile}）；</li>
 *   <li><b>法术伤害默认不暴击</b>，只有佩戴了配置里声明 {@code spell_crit: true} 的
 *       「特殊装备」时才允许法术（铁魔法法术、本模组卢登回声光球）暴击。</li>
 * </ul>
 *
 * <p>暴击伤害是<b>独立乘区</b>：命中暴击时把 {@link LivingHurtEvent} 的最终伤害直接乘以
 * {@code LOL 暴击伤害} 倍率（基础默认 150%，即 ×1.5）。</p>
 *
 * <p>暴击率封顶 100%：属性本身被 {@link ModAttributes#LOL_CRIT_CHANCE} 的上限压到 ≤100%。
 * 当多件装备的裸加成之和超过 100% 时，超出部分<b>隐藏生效</b>——每多 10% 转化为 +1%
 * 暴击伤害（只在实际结算时叠加，不改变属性面板显示的暴击率）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolCritSystem {

    /** 溢出暴击率换算：每 10% 溢出 → +1% 暴击伤害。 */
    private static final double OVERFLOW_PER_CONVERSION = 0.10D;
    private static final double CONVERSION_PER_STEP = 0.01D;

    private LolCritSystem() {
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        DamageSource source = event.getSource();
        if (source == null) {
            return;
        }
        Entity rawAttacker = source.getEntity();
        if (!(rawAttacker instanceof Player attacker) || attacker == event.getEntity()) {
            return;
        }
        if (!attacker.isAlive()) {
            return;
        }

        // 读取生效的 LOL 暴击率（即使在齐射里也用于判定“自然会心”分支）
        double chance = readCritChance(attacker);

        // 猎魔人弩箭·开战弹幕：玩家射出的远程物理弹道命中时，若弹幕有充能则接管本击结算。
        // 必须放在“0% 暴击率放行”之前——齐射即使 0% 暴击率也要按 config 比例强制结算。
        boolean magicSource = IronsCompat.isIronSpellDamage(source)
                || source.getDirectEntity() instanceof EchoOrbEntity;
        if (!magicSource && source.getDirectEntity() instanceof Projectile barrageProjectile) {
            Entity barrageOwner = barrageProjectile.getOwner();
            if (barrageOwner != null
                    && barrageOwner.getUUID().equals(attacker.getUUID())
                    && Lol2026LegendPassiveEvents.tryFiendhunterBarrage(
                    attacker, event, readCritMultiplier(attacker))) {
                return; // 已被弹幕消费充能并结算必定会心，跳过常规暴击
            }
        }

        // 没有 LOL 暴击率（等于没带暴击属性）就直接放行
        if (chance <= 0.0D) {
            return;
        }

        // 判定这次伤害是否允许暴击（法术需要特殊装备许可）
        if (!isCritable(source, attacker)) {
            return;
        }

        if (attacker.getRandom().nextDouble() >= chance) {
            return;
        }

        double multiplier = readCritMultiplier(attacker);
        if (multiplier <= 1.0D) {
            return;
        }

        // 独立乘区：最终伤害 × 暴击伤害倍率
        event.setAmount((float) (event.getAmount() * multiplier));

        LivingEntity victim = event.getEntity();
        if (victim.level() instanceof ServerLevel level) {
            boolean magic = IronsCompat.isIronSpellDamage(source)
                    || source.getDirectEntity() instanceof EchoOrbEntity;
            Vec3 p = victim.position().add(0.0D, victim.getBbHeight() * 0.5D, 0.0D);
            level.sendParticles(magic ? ParticleTypes.ENCHANTED_HIT : ParticleTypes.CRIT,
                    p.x, p.y, p.z, magic ? 10 : 14, 0.3D, 0.3D, 0.3D, 0.12D);
        }
        LOLAccessories.LOGGER.debug("[LOL暴击] {} 暴击了 {}：原始 {} 点 → {} 点（倍率 {}，暴击率 {}）",
                attacker.getName().getString(), victim.getName().getString(),
                Math.round(event.getAmount() / multiplier), Math.round(event.getAmount()),
                String.format(java.util.Locale.ROOT, "%.2f", multiplier),
                String.format(java.util.Locale.ROOT, "%.1f%%", chance * 100.0D));
    }

    /**
     * 该伤害来源是否允许暴击：近战与玩家射出的物理弹射物默认允许；
     * 法术（铁魔法 / 卢登回声）只有佩戴了 {@code spell_crit} 装备时才允许。
     */
    private static boolean isCritable(DamageSource source, Player attacker) {
        Entity direct = source.getDirectEntity();

        // 法术类伤害：默认不暴击，除非佩戴者带“允许法术暴击”的装备
        boolean magic = IronsCompat.isIronSpellDamage(source)
                || direct instanceof EchoOrbEntity;
        if (magic) {
            return allowsSpellCrit(attacker);
        }

        // 近战：伤害直接来源就是佩戴者本人（玩家的近战/拳击等）
        if (direct == attacker) {
            return true;
        }
        // 弹射物：弓/弩/三叉戟等由玩家射出且归属该玩家的物理弹道
        if (direct instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner != null && owner.getUUID().equals(attacker.getUUID());
        }
        return false;
    }

    /** 是否有任意一件已装备的饰品在其配置中声明了「允许法术暴击」。 */
    private static boolean allowsSpellCrit(Player player) {
        var slots = CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(handler -> handler.findCurios(stack -> {
                    if (!(stack.getItem() instanceof GearItem gear)) {
                        return false;
                    }
                    GearConfig config = GearConfigManager.get(gear.getGearId());
                    return config != null && config.spell_crit;
                }))
                .orElse(List.of());
        return !slots.isEmpty();
    }

    /** 读取生效的 LOL 暴击率（属性上限 1.0 已由 RangedAttribute 封顶，永远 ≤ 100%）。 */
    private static double readCritChance(LivingEntity entity) {
        AttributeInstance instance = entity.getAttribute(ModAttributes.LOL_CRIT_CHANCE.get());
        return instance == null ? 0.0D : instance.getValue();
    }

    /**
     * 读取本次暴击的最终倍率 = LOL 暴击伤害属性值（默认 150%） + 溢出暴击率换算。
     *
     * <p>裸加成（本模组装备统一以 ADDITION 修正器贡献暴击率，属性默认值为 0）超过 100% 的
     * 部分，每满 10% 额外 +1% 暴击伤害。换算对属性面板隐藏——面板上的暴击率始终显示 ≤100%。</p>
     */
    private static double readCritMultiplier(LivingEntity entity) {
        AttributeInstance damageInstance = entity.getAttribute(ModAttributes.LOL_CRIT_DAMAGE.get());
        double multiplier = damageInstance == null
                ? ModAttributes.DEFAULT_CRIT_DAMAGE
                : Math.max(1.0D, damageInstance.getValue());

        AttributeInstance chanceInstance = entity.getAttribute(ModAttributes.LOL_CRIT_CHANCE.get());
        if (chanceInstance != null) {
            double rawBonus = 0.0D;
            for (AttributeModifier modifier : chanceInstance.getModifiers()) {
                if (modifier.getOperation() == AttributeModifier.Operation.ADDITION) {
                    rawBonus += modifier.getAmount();
                }
            }
            double overflow = rawBonus - 1.0D;
            if (overflow > 0.0D) {
                double converted = Math.floor(overflow / OVERFLOW_PER_CONVERSION) * CONVERSION_PER_STEP;
                multiplier += converted;
            }
        }
        return Math.max(1.0D, multiplier);
    }
}
