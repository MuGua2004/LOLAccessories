package com.example.lolaccessories.effect;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 幽梦之灵·鬼步（Wraith Step）主动窗口：以药水效果形式在 6 秒内提供 +20% 移动速度，
 * 同时作为「无视单位碰撞」的标记——{@code PhantomDancerCollisionMixin} 会读取本效果，
 * 让处于鬼步窗口的玩家穿过单位（复用幻影之舞的穿人机制）。效果本身由
 * {@code LolNewActiveSkillEvents} 在主动触发时施加，时长/移速比例由 youmuus_ghostblade.json
 * 的 wraith_step 条目控制。
 */
public class WraithStepEffect extends MobEffect {

    private static final UUID MODIFIER_UUID = UUID.fromString("7d2a1c55-3b4e-4f18-9c6a-2b7e0d9f1a83");

    private static final String GEAR_ID = "youmuus_ghostblade";

    public WraithStepEffect() {
        super(MobEffectCategory.BENEFICIAL, 0x66ccff);
        this.addAttributeModifier(Attributes.MOVEMENT_SPEED, MODIFIER_UUID.toString(), 0.20D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        GearConfig config = GearConfigManager.get(GEAR_ID);
        double bonus = config == null ? 0.20D
                : config.findEffect("wraith_step")
                .map(effect -> effect.amount > 0 ? effect.amount : 0.20D)
                .orElse(0.20D);
        return bonus * (amplifier + 1);
    }
}
