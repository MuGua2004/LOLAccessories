package com.example.lolaccessories.effect;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;

import java.util.UUID;

/**
 * 「耐久专注」效果：佩戴多兰之盾受到伤害后的 8 秒内，自然生命恢复翻倍。
 *
 * <p>通过原版属性效果机制实现：向本模组自建的 {@link ModAttributes#LOL_NATURAL_REGEN}
 * 追加一个与装备静态加成等量的 ADDITION 修正器，使窗口期内该属性值 = 静态加成 × 2
 * （多兰盾静态 +0.8/周期，窗口期内变为 +1.6/周期）。翻倍量由事件层按所属装备配置施加。</p>
 */
public class RegenBoostEffect extends MobEffect {

    private static final UUID MODIFIER_UUID = UUID.fromString("4a1b6c8e-7d2f-4f9a-b3c5-9e8d1f2a4b6c");

    private final String gearId;

    public RegenBoostEffect(String gearId) {
        super(MobEffectCategory.BENEFICIAL, 0x4cd964);
        this.gearId = gearId;
        // 基础量仅用于让原版机制认识该修正器，真实强度由下方覆写按配置读取
        this.addAttributeModifier(ModAttributes.LOL_NATURAL_REGEN.get(), MODIFIER_UUID.toString(), 0.8D,
                AttributeModifier.Operation.ADDITION);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        GearConfig config = GearConfigManager.get(gearId);
        return config.findEffect("perseverance")
                .map(effect -> effect.amount)
                .orElse(0.8D);
    }
}
