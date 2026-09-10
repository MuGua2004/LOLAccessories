package com.example.lolaccessories.mixin;

import net.minecraft.world.effect.MobEffectInstance;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

/**
 * {@link MobEffectInstance} 的时长写入 accessor。
 *
 * <p>原版 {@code duration} 字段无公开 setter，韧性结算需要把「正在被施加的负面效果实例」的
 * 剩余时长改短后再放行原版 {@code addEffect} 逻辑（让后续合并/刷新逻辑都基于已减免的时长），
 * 因此用 mixin accessor 暴露唯一写入点。</p>
 */
@Mixin(MobEffectInstance.class)
public interface MobEffectInstanceAccessor {

    @Accessor("duration")
    void lolaccessories$setDuration(int duration);
}
