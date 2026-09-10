package com.example.lolaccessories.mixin;

import com.example.lolaccessories.event.LolTenacityEvents;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 传说韧性——负面药水效果时长减免的施加入口。
 *
 * <p>任何负面药水效果（原版标记为 non-beneficial）在被加给玩家时，都统一经由
 * {@code LivingEntity#addEffect(MobEffectInstance, Entity)} 落地（此两参重载被无实体参数重载
 * 委托调用，覆盖所有施加来源：攻击效果、药水、区域效果云、信标等）。在方法头根据玩家净韧性
 * 折算时长（见 {@link LolTenacityEvents#reduceDuration}），写回效果实例后再放行原版逻辑，
 * 保证之后的一切合并/刷新都基于已减免后的时长。</p>
 */
@Mixin(LivingEntity.class)
public abstract class LivingEntityTenacityMixin {

    @Inject(method = "addEffect(Lnet/minecraft/world/effect/MobEffectInstance;Lnet/minecraft/world/entity/Entity;)Z",
            at = @At("HEAD"))
    private void lolaccessories$applyTenacity(MobEffectInstance instance, Entity source,
                                              CallbackInfoReturnable<Boolean> cir) {
        Object self = this;
        if (!(self instanceof Player player)) {
            return;
        }
        if (instance == null || player.level().isClientSide) {
            return;
        }
        // 只减免负面效果；生命提升/伤害吸收等自定义护盾、增益一律不动
        if (instance.getEffect().isBeneficial()) {
            return;
        }
        int reduced = LolTenacityEvents.reduceDuration(player, instance);
        if (reduced >= 0 && reduced < instance.getDuration()) {
            ((MobEffectInstanceAccessor) (Object) instance).lolaccessories$setDuration(reduced);
        }
    }
}
