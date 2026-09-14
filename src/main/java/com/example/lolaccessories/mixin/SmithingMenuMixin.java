package com.example.lolaccessories.mixin;

import com.example.lolaccessories.advancement.LolAdvancementService;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.crafting.PaidSmithingRecipe;
import com.example.lolaccessories.money.GoldWallet;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.inventory.SmithingMenu;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraftforge.registries.ForgeRegistries;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** 锻造台付费取件拦截：花费校验 + 鞋类互斥（足部槽装备同时只能持有一双）。 */
@Mixin(SmithingMenu.class)
public abstract class SmithingMenuMixin {

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
        ItemStack resultStack = paid.getResultItem(RegistryAccess.EMPTY);
        String resultGearId = ForgeRegistries.ITEMS.getKey(resultStack.getItem()).getPath();
        GearConfig resultConfig = GearConfigManager.get(resultGearId);
        if (resultConfig != null && "feet".equals(resultConfig.slot) && hasAnyFeetGear(player)) {
            player.displayClientMessage(
                    Component.translatable("message.lolaccessories.boots_conflict"), false);
            cir.setReturnValue(false);
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
            if (player instanceof ServerPlayer serverPlayer) {
                LolAdvancementService.onGearCrafted(serverPlayer, stack);
            }
        }
    }

    private static boolean hasAnyFeetGear(Player player) {
        for (ItemStack stack : player.getInventory().items) {
            if (!stack.isEmpty() && isFeetGear(stack)) {
                return true;
            }
        }
        boolean[] found = {false};
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            GearConfig config = GearConfigManager.get(gear.getGearId());
            if (config != null && "feet".equals(config.slot)) {
                found[0] = true;
            }
        });
        return found[0];
    }

    private static boolean isFeetGear(ItemStack stack) {
        String id = ForgeRegistries.ITEMS.getKey(stack.getItem()).getPath();
        GearConfig config = GearConfigManager.get(id);
        return config != null && "feet".equals(config.slot);
    }
}
