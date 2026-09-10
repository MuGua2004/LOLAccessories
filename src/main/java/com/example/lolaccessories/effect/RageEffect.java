package com.example.lolaccessories.effect;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 「狂暴」效果（净蚀 Phage 的 Rage）：以药水效果形式为佩戴者提供移动速度加成。
 *
 * <p>数值（加成比例）每次施加时从 phage.json 的 rage 条目读取（默认 amount = 0.2 → +20%），
 * 时长（默认 2 秒）由触发事件按配置施加。与原版「热烈」同款实现，只是数据源换成了净蚀。</p>
 */
public class RageEffect extends MobEffect {

    private static final UUID MODIFIER_UUID = UUID.fromString("3d1e9f5a-6b8c-4d0f-a2e7-c9f0b3d4e1a2");

    private final String gearId;

    public RageEffect(String gearId) {
        super(MobEffectCategory.BENEFICIAL, 0xffd24d);
        this.gearId = gearId;
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MODIFIER_UUID.toString(), 0.20D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        GearConfig config = GearConfigManager.get(gearId);
        double bonus = config.findEffect("rage")
                .map(effect -> effect.amount)
                .orElse(0.20D);
        return bonus * (amplifier + 1);
    }
}
