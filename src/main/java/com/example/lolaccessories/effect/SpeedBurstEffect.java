package com.example.lolaccessories.effect;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 「热烈」效果：以药水效果形式为佩戴者提供移动速度加成。
 *
 * <p>数值（加成比例）每次施加时从所属装备配置读取，时长由施加方事件控制。</p>
 */
public class SpeedBurstEffect extends MobEffect {

    private static final UUID MODIFIER_UUID = UUID.fromString("2c0b3d7e-9f42-4e1a-8c77-a5f8b1e9c3d4");

    private final String gearId;

    public SpeedBurstEffect(String gearId) {
        super(MobEffectCategory.BENEFICIAL, 0xffb400);
        this.gearId = gearId;
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MODIFIER_UUID.toString(), 0.20D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        GearConfig config = GearConfigManager.get(gearId);
        double bonus = config.findEffect("cleaver_rush")
                .map(effect -> effect.amount)
                .orElse(0.20D);
        return bonus * (amplifier + 1);
    }
}
