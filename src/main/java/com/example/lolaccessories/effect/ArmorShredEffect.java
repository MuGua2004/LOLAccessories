package com.example.lolaccessories.effect;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;

import java.util.UUID;

/**
 * 「切割」效果：以药水效果形式削减目标护甲。
 *
 * <p>通过原版属性效果机制实现：向 {@link Attributes#ARMOR} 挂一个总量乘法修正器，
 * 放大器 n 对应「护甲削减 (n+1) 层」。数值不再写死在类里，而是每次施加时从
 * {@link GearConfigManager} 读取所属装备的配置。</p>
 */
public class ArmorShredEffect extends MobEffect {

    private static final UUID MODIFIER_UUID = UUID.fromString("1d8f1c4b-8c61-4fae-8d13-7c54a3f2b901");

    private final String gearId;

    public ArmorShredEffect(String gearId) {
        super(MobEffectCategory.HARMFUL, 0x6b7280);
        this.gearId = gearId;
        // 基础量仅用于让原版机制认识该修正器（应用/移除按 UUID 配对），真实强度由下方覆写计算
        this.addAttributeModifier(Attributes.ARMOR, MODIFIER_UUID.toString(), -0.06D,
                AttributeModifier.Operation.MULTIPLY_TOTAL);
    }

    @Override
    public double getAttributeModifierValue(int amplifier, AttributeModifier modifier) {
        GearConfig config = GearConfigManager.get(gearId);
        double perStack = config.findEffect("cleaver_shred")
                .map(effect -> effect.per_stack)
                .orElse(0.06D);
        double maxTotal = config.findEffect("cleaver_shred")
                .map(effect -> effect.max_total)
                .orElse(0.30D);
        double stacks = amplifier + 1;
        // 物理层数永远不会超过「每层削减 x 最多层数 = 总上限」，这里再兜底一次防止越界
        return -Math.min(perStack * stacks, Math.max(maxTotal, perStack));
    }
}
