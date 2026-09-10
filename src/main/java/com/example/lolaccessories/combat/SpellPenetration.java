package com.example.lolaccessories.combat;

import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.world.entity.LivingEntity;

/**
 * 法术穿透（lolaccessories:magic_pen / magic_pen_percent）的读取与折算工具。
 *
 * <p>两件法穿属性语义（只对铁魔法法术伤害生效，攻击方携带）：</p>
 * <ul>
 *   <li>{@link ModAttributes#LOL_MAGIC_PEN}：固定点数。结算时目标的「原版护甲」与
 *       「传说魔法抗性」各等额扣减（最少 0）。</li>
 *   <li>{@link ModAttributes#LOL_MAGIC_PEN_PERCENT}：百分比。结算时同时按比例无视目标的
 *       「原版护甲、护甲韧性、传说魔法抗性、铁魔法学派魔抗」。</li>
 * </ul>
 *
 * <p>两者同时存在时顺序固定为：先百分比后固定（与现行英雄联盟一致），即
 * {@code 有效值 = max(原值 × (1 − pct) − flat, 0)}，见 {@link #effective}。</p>
 */
public final class SpellPenetration {

    private SpellPenetration() {
    }

    /** 读取攻击方的固定法术穿透点数；无属性实例返回 0。 */
    public static double getFlatPenetration(LivingEntity attacker) {
        if (attacker == null) {
            return 0.0D;
        }
        var attr = attacker.getAttribute(ModAttributes.LOL_MAGIC_PEN.get());
        return attr == null ? 0.0D : Math.max(0.0D, attr.getValue());
    }

    /** 读取攻击方的百分比法术穿透（0~1）；无属性实例返回 0。 */
    public static double getPercentPenetration(LivingEntity attacker) {
        if (attacker == null) {
            return 0.0D;
        }
        var attr = attacker.getAttribute(ModAttributes.LOL_MAGIC_PEN_PERCENT.get());
        if (attr == null) {
            return 0.0D;
        }
        double value = attr.getValue();
        if (value <= 0.0D) {
            return 0.0D;
        }
        return Math.min(1.0D, value);
    }

    /** 是否携带任一法穿属性（固定或百分比）。 */
    public static boolean hasAny(LivingEntity attacker) {
        return attacker != null
                && (getFlatPenetration(attacker) > 0.0D || getPercentPenetration(attacker) > 0.0D);
    }

    /**
     * 穿透后的有效防御值：先按百分比无视，再扣除固定点数，最少为 0。
     *
     * @param base 原始防御值（护甲 / 护甲韧性 / 传说魔抗等）
     * @param flat 固定法穿点数（只参与点数型防御的折算）
     * @param pct  百分比法穿（0~1）
     */
    public static double effective(double base, double flat, double pct) {
        return Math.max(0.0D, base * (1.0D - pct) - flat);
    }
}
