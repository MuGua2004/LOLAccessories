package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.client.tooltip.PaidCostTooltip;
import com.example.lolaccessories.crafting.PaidSmithingRecipe;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.screens.inventory.SmithingScreen;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.level.Level;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 客户端（Forge 事件总线）：物品 tooltip 聚合时，仅在「真实锻造台界面」悬停付费锻造的
 * 成品时，在 tooltip 末尾追加一行金色标题与费用图标行（图标+数量）。
 *
 * <p>费用展示被刻意限定在锻造场景（锻造台 GUI 与 JEI 锻造配方页），不作为成品物品的
 * 常驻 tooltip —— 因此创造/生存物品栏等普通悬停一律不显示。JEI 一侧由
 * {@code PaidSmithingJeiExtension} 直接向输出槽 tooltip 追加图标行；本类只负责真实锻造台，
 * 并检测到 tooltip 里已有费用行（JEI 覆盖在锻造台上方时）则跳过，避免叠加。</p>
 *
 * <p>成品 = 任一 {@code PaidSmithingRecipe} 的 result。费用随配方数据驱动：在客户端配方
 * 列表（与服务器同步）里按产出物品反查即可，未来新增付费锻造成品无需再改代码。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT)
public final class ClientPaidCostTooltipEvents {

    private ClientPaidCostTooltipEvents() {
    }

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        // 只服务于真实锻造台界面；其他任何界面（创造/生存物品栏等）悬停成品不显示费用。
        if (!(Minecraft.getInstance().screen instanceof SmithingScreen)) {
            return;
        }
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        Level level = Minecraft.getInstance().level;
        if (level == null) {
            return;
        }
        PaidSmithingRecipe recipe = findRecipe(level, stack);
        if (recipe == null) {
            return;
        }
        List<ItemStack> costs = recipe.getCosts();
        if (costs.isEmpty()) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        // JEI 锻造配方页已通过 rich tooltip 自带费用行（含 PaidCostTooltip），
        // 当 JEI 覆盖在锻造台上方悬停输出槽时不再重复追加。
        for (Either<FormattedText, TooltipComponent> line : elements) {
            if (line.right().map(component -> component instanceof PaidCostTooltip).orElse(false)) {
                return;
            }
        }
        elements.add(Either.left(Component.translatable("tooltip.lolaccessories.paid_smithing.title")
                .withStyle(ChatFormatting.GOLD)));
        elements.add(Either.right(new PaidCostTooltip(costs)));
    }

    /** 在客户端配方列表中查找产出该物品的付费锻造配方（找不到返回 null）。 */
    private static PaidSmithingRecipe findRecipe(Level level, ItemStack stack) {
        for (SmithingRecipe smithing : level.getRecipeManager().getAllRecipesFor(RecipeType.SMITHING)) {
            if (smithing instanceof PaidSmithingRecipe paid
                    && paid.getResultItem(RegistryAccess.EMPTY).getItem() == stack.getItem()) {
                return paid;
            }
        }
        return null;
    }
}
