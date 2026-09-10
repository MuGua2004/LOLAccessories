package com.example.lolaccessories.effect;

import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;

/**
 * 「重伤」（Grievous Wounds）效果：使受害者受到的治疗降低。
 *
 * <p>不携带任何属性修正器——它是纯“窗口标记”。治疗削减量由事件层按效果的
 * {@code amplifier} 结算：{@code 削减 = (amplifier + 1) × 10%}。施加重伤时，事件层把装备配置
 * 里 amount（默认 0.4 = 40%）换算成对应的等级（{@code amplifier = clamp(amount/0.1 - 1)}），
 * 因此玩家修改配置里的 40% 为其它值时治疗削减仍能跟随（按 10% 一档就近取整）。</p>
 */
public class WoundsEffect extends MobEffect {

    public WoundsEffect() {
        super(MobEffectCategory.HARMFUL, 0x7f8c8d);
    }
}
