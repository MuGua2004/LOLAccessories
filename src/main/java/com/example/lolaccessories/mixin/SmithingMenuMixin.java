package com.example.lolaccessories.mixin;

import com.example.lolaccessories.advancement.LolAdvancementService;
import com.example.lolaccessories.crafting.PaidSmithingRecipe;
import com.example.lolaccessories.money.GoldWallet;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipe;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 锻造台「付费取件」拦截：
 *
 * <ul>
 *     <li><b>mayPickup</b>：取件前校验花费清单（背包 + 末影箱），任一物品不足即禁止取出结果
 *         并提示，材料不会被消耗；</li>
 *     <li><b>onTake</b>：取件成立时按当前匹配到的付费配方自动扣除全部花费物品
 *         （如金币、下界之星等）。</li>
 * </ul>
 *
 * <p>花费物品只允许来自背包与末影箱（不放锻造台），见 {@link GoldWallet}。
 * 费用展示仅存在于锻造场景：真实锻造台界面悬停成品时（含图标行，见 client 包下
 * {@code PaidCostTooltip} 系列）以及 JEI 锻造配方页；不作为成品物品的常驻 tooltip。</p>
 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {

    /** 当前锻造台匹配到的配方（1.20.1 锻造台重写后由 SmithingMenu 持有）。 */
    @Shadow
    private SmithingRecipe selectedRecipe;

    @Inject(method = "mayPickup(Lnet/minecraft/world/entity/player/Player;Z)Z",
            at = @At("HEAD"), cancellable = true)
    private void lolaccessories$enforceCostBeforeTake(Player player, boolean hasStack,
                                                      CallbackInfoReturnable<Boolean> cir) {
        if (player.level().isClientSide) {
            return;
        }
        if (!(this.selectedRecipe instanceof PaidSmithingRecipe paid)) {
            return;
        }
        for (ItemStack cost : paid.getCosts()) {
            int need = cost.getCount();
            int held = GoldWallet.countItems(player, cost.getItem());
            if (held < need) {
                player.displayClientMessage(
                        Component.translatable("message.lolaccessories.insufficient_cost",
                                need, new ItemStack(cost.getItem()).getHoverName(), held),
                        false);
                cir.setReturnValue(false);
                return;
            }
        }
    }

    @Inject(method = "onTake(Lnet/minecraft/world/entity/player/Player;Lnet/minecraft/world/item/ItemStack;)V",
            at = @At("HEAD"))
    private void lolaccessories$spendCostOnTake(Player player, ItemStack stack, CallbackInfo ci) {
        if (player.level().isClientSide) {
            return;
        }
        if (this.selectedRecipe instanceof PaidSmithingRecipe paid) {
            for (ItemStack cost : paid.getCosts()) {
                GoldWallet.trySpendItems(player, cost.getItem(), cost.getCount());
            }
            // 成就：第一次「合成装备」（锻造取件也算，工作台合出由 ItemCraftedEvent 覆盖）
            if (player instanceof ServerPlayer serverPlayer) {
                LolAdvancementService.onGearCrafted(serverPlayer, stack);
            }
        }
    }
}
