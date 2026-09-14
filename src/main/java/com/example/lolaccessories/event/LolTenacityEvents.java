package com.example.lolaccessories.event;

import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.effect.MobEffectInstance;

/**
 * 传说韧性（{@code lolaccessories:tenacity}）时长减免的共用入口。
 *
 * <p>负面药水效果在通过 {@code LivingEntity#addEffect(...)} 施加给玩家时，由
 * {@code mixin.LivingEntityTenacityMixin} 调用本类的 {@link #reduceDuration} 计算减免后的时长，
 * 再写回正在被施加的效果实例上。逻辑上即「任何给玩家挂负面药水效果的来源，其时长 × (1 − 净韧性)」，
 * 一次施加只削减一次，后续来自同一效果的逐 tick 延长（如再次命中刷新）会重新按当前净韧性计算。</p>
 *
 * <p>净韧性 = 玩家身上该属性的净值（装备静态加成 + 药水/其它修正器都会被计入，例如
 * 原生质挂具的瞬时韧性加成）。韧性 1.0（100%）时任何负面效果只剩 1 tick。</p>
 */
public final class LolTenacityEvents {

    private LolTenacityEvents() {
    }

    /**
     * 按玩家净韧性折算负面效果的时长，返回折算后的 tick 数（至少 1 tick）。
     *
     * @param player   被施加效果的玩家
     * @param instance 正在被施加的效果实例（null 视为无可减免内容）
     * @return 减免后的 tick 数；玩家无韧性或效果时返回原时长
     */
    public static int reduceDuration(Player player, MobEffectInstance instance) {
        if (instance == null) {
            return -1;
        }
        int original = instance.getDuration();
        if (original <= 1) {
            return original;
        }
        // 迅捷步（轻灵之靴）：受到的减速效果效能降低 25%（时长 ×0.75）
        if (instance.getEffect() == net.minecraft.world.effect.MobEffects.MOVEMENT_SLOWDOWN
                && CuriosGearWear.isWearing(player, "swift_boots")) {
            original = Math.max(1, Math.round(original * 0.75F));
        }
        AttributeInstance attribute = player.getAttribute(ModAttributes.LOL_TENACITY.get());
        if (attribute == null) {
            return original;
        }
        double tenacity = attribute.getValue();
        if (tenacity <= 0.0D) {
            return original;
        }
        if (tenacity > 1.0D) {
            tenacity = 1.0D;
        }
        return Math.max(1, (int) Math.floor(original * (1.0D - tenacity)));
    }
}
