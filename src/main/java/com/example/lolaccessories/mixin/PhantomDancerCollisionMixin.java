package com.example.lolaccessories.mixin;

import com.example.lolaccessories.client.PhantomDancerState;
import com.example.lolaccessories.init.ModMobEffects;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 幻影之舞（Phantom Dancer）·「幽影华尔兹」——无视单位碰撞体积。
 *
 * <p>英雄联盟里幻影之舞让佩戴者可以穿过单位（无单位碰撞）。我的世界实体间同样有推挤
 * 碰撞（{@code LivingEntity#isPushable}）：本 Mixin 让<b>佩戴幻影之舞的玩家</b>不再被视为
 * 可推挤实体——由于推挤是双向判定（A 推 B 时既查 B 的 isPushable，B 的推挤遍历也查 A 的
 * isPushable），单一注入点即可实现双向「穿过」：别人穿不透佩戴者、佩戴者也能穿过别人。</p>
 *
 * <p>佩戴判定 {@link PhantomDancerState#isWearing} 自带逐 tick 缓存，实体对推挤检测的
 * 高频调用不会带来可观的 Curios 遍历开销。 spectators / 无敌帧等原版语义不受影响。</p>
 */
@Mixin(LivingEntity.class)
public abstract class PhantomDancerCollisionMixin {

    @Inject(method = "isPushable", at = @At("HEAD"), cancellable = true)
    private void lolaccessories$phantomDancerNoCollision(CallbackInfoReturnable<Boolean> cir) {
        // 快速路径：只有玩家才可能佩戴饰品，其它实体原样返回
        if (!((Object) this instanceof LivingEntity living)) {
            return;
        }
        if (living instanceof net.minecraft.world.entity.player.Player player) {
            if (PhantomDancerState.isWearing(player)
                    || player.hasEffect(com.example.lolaccessories.init.ModMobEffects.WRAITH_STEP.get())) {
                cir.setReturnValue(false);
            }
        }
    }
}
