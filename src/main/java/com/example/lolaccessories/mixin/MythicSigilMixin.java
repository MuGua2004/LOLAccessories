package com.example.lolaccessories.mixin;

import com.example.lolaccessories.client.MythicSigilRenderer;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * 神话装备（tier4）物品图标下方的「魔法阵」叠加层。
 *
 * <p>注入 {@code GuiGraphics.renderItem} 的 private 6 参实现——所有公开重载（背包/快捷栏/
 * 鼠标拿起的漂浮物品）最终都 delegate 到这里，因此法阵在<b>任何显示场景</b>都常驻；
 * 在 HEAD 处绘制 → 位于物品图标<b>下层</b>（后画的物品盖在法阵之上），符合
 * 「法阵放在装备贴图下面」的要求。</p>
 *
 * <p>手持物品走 ItemInHandRenderer，不经过 GuiGraphics，故<b>拿在手上没有法阵
 * ——与原版附魔光效/其它 GUI 叠加层的表现一致。</p>
 */
@Mixin(GuiGraphics.class)
public abstract class MythicSigilMixin {

    @Inject(method = "renderItem(Lnet/minecraft/world/entity/LivingEntity;"
            + "Lnet/minecraft/world/level/Level;"
            + "Lnet/minecraft/world/item/ItemStack;III)V",
            at = @At("HEAD"))
    private void lolaccessories$mythicSigilUnderItem(LivingEntity entity, Level level, ItemStack stack,
                                                     int x, int y, int seed, CallbackInfo ci) {
        if (stack == null || stack.isEmpty()) {
            return;
        }
        MythicSigilRenderer.drawUnderItem((GuiGraphics) (Object) this, stack, x, y);
    }
}
