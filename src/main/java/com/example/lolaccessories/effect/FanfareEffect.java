package com.example.lolaccessories.effect;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 「嘹亮旋律」效果（班德尔音管被动触发）：为佩戴者及附近友军玩家提供移速 + 攻速加成。
 *
 * <p>数值（加成比例）每次施加时从 bandlepipes.json 的 fanfare 条目读取：默认
 * move_speed_ratio = 0.20（+20% 移速）、attack_speed_ratio = 0.30（+30% 攻速），
 * 持续时长（默认 8 秒）由触发事件按配置施加。用独立 UUID 区分两个属性修正器。</p>
 */
public class FanfareEffect extends MobEffect {

    private static final UUID MS_MODIFIER_UUID = UUID.fromString("4f2c9d8a-6b41-4f0a-b3c7-d9e2f0a1c4b8");
    private static final UUID AS_MODIFIER_UUID = UUID.fromString("5f2c9d8a-6b41-4f0a-b3c7-d9e2f0a1c4b8");

    private final String gearId;
    private final String effectId;

    public FanfareEffect(String gearId, String effectId) {
        super(MobEffectCategory.BENEFICIAL, 0xf5d76e);
        this.gearId = gearId;
        this.effectId = effectId;
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MS_MODIFIER_UUID.toString(), 0.20D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
        this.addAttributeModifier(Attributes.ATTACK_SPEED, AS_MODIFIER_UUID.toString(), 0.30D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        GearConfig config = GearConfigManager.get(gearId);
        double bonus = config.findEffect(effectId)
                .map(effect -> modifier.getId().equals(MS_MODIFIER_UUID.toString())
                        ? effect.move_speed_ratio
                        : effect.attack_speed_ratio)
                .orElse(0.0D);
        return bonus * (amplifier + 1);
    }
}
