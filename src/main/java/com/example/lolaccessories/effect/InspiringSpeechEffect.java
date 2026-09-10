package com.example.lolaccessories.effect;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 「鼓舞」效果（舒瑞娅的战歌主动）：以药水效果形式为佩戴者及其附近友方玩家提供移速加成。
 *
 * <p>数值（加成比例）每次施加时从 shurelyas_battlesong.json 的 inspiring_speech 条目读取
 * （默认 amount = 0.30 → +30%），时长（默认 4 秒）由主动技能事件按配置施加。</p>
 */
public class InspiringSpeechEffect extends MobEffect {

    private static final UUID MODIFIER_UUID = UUID.fromString("6e4a2c9d-8b51-4f0a-b3c7-d9e2f0a1c4b8");

    private final String gearId;
    private final String effectId;

    public InspiringSpeechEffect(String gearId, String effectId) {
        super(MobEffectCategory.BENEFICIAL, 0x9be6ff);
        this.gearId = gearId;
        this.effectId = effectId;
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MODIFIER_UUID.toString(), 0.30D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        GearConfig config = GearConfigManager.get(gearId);
        double bonus = config.findEffect(effectId)
                .map(effect -> effect.amount)
                .orElse(0.30D);
        return bonus * (amplifier + 1);
    }
}
