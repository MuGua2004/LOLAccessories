package com.example.lolaccessories.util;

import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;

/**
 * 装备技能急速换算：按英雄联盟官方公式 {@code 冷却缩减 = 急速 / (100 + 急速)}。
 * 只作用于本模组装备的主动技能冷却（时间停止、各装备主动技、回声被动等），
 * 与铁魔法冷却缩减属性的减免乘区相互独立。
 */
public final class HasteMath {

    private HasteMath() {
    }

    /**
     * 目标身上的<b>装备技能急速</b>乘区因子：{@code 1 − 急速/(100+急速)}；
     * 无急速时返回 1（不缩放）。
     */
    public static double gearHasteFactor(LivingEntity entity) {
        if (entity == null) {
            return 1.0D;
        }
        AttributeInstance instance = entity.getAttribute(ModAttributes.LOL_GEAR_HASTE.get());
        double haste = instance == null ? 0.0D : instance.getValue();
        if (haste <= 0.0D) {
            return 1.0D;
        }
        return 1.0D - haste / (100.0D + haste);
    }
}
