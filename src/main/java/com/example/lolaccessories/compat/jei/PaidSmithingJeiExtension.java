package com.example.lolaccessories.compat.jei;

import com.example.lolaccessories.client.tooltip.PaidCostTooltip;
import com.example.lolaccessories.crafting.PaidSmithingRecipe;
import mezz.jei.api.gui.builder.IIngredientAcceptor;
import mezz.jei.api.gui.builder.IRecipeSlotBuilder;
import mezz.jei.api.recipe.category.extensions.vanilla.smithing.ISmithingCategoryExtension;
import net.minecraft.ChatFormatting;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.ItemStack;

import java.util.List;

/**
 * 让 JEI 显示 {@link PaidSmithingRecipe} 的「付费锻造」配方。
 *
 * <p>JEI 自带的 smithing 类目只给原版 {@code SmithingTransformRecipe}/{@code SmithingTrimRecipe}
 * 注册了显示扩展；自定义的 SmithingRecipe（本模组的付费锻造）不在其中会被直接丢弃，
 * 因此这里通过 {@code ISmithingCategoryExtension} 补上槽位布局：
 * <ul>
 *     <li>组件 0 → 基底槽；</li>
 *     <li>组件 1 → 附加槽（两/三组件时）；</li>
 *     <li>组件 2 → 模板槽（三组件时）。</li>
 * </ul>
 * 与 {@code PaidSmithingRecipe#matches} 的槽位约定一致。</p>
 *
 * <p>花费物品（金币、下界之星等）不进锻造台，费用只在锻造台界面与 JEI 展示，不属于成品
 * 物品自身 tooltip（创造/生存物品栏悬停成品看不到）。此处通过 rich tooltip 在输出槽追加与
 * 锻造台一致的「金色标题 + 图标行」（复用 {@link PaidCostTooltip}，由 Forge 注册的
 * {@code ClientTooltipComponent} 工厂渲染）。</p>
 */
public class PaidSmithingJeiExtension implements ISmithingCategoryExtension<PaidSmithingRecipe> {

    @Override
    public <T extends IIngredientAcceptor<T>> void setTemplate(PaidSmithingRecipe recipe, T acceptor) {
        if (recipe.getComponentCount() >= 3) {
            acceptor.addIngredients(recipe.getComponent(2));
        }
    }

    @Override
    public <T extends IIngredientAcceptor<T>> void setBase(PaidSmithingRecipe recipe, T acceptor) {
        if (recipe.getComponentCount() >= 1) {
            acceptor.addIngredients(recipe.getComponent(0));
        }
    }

    @Override
    public <T extends IIngredientAcceptor<T>> void setAddition(PaidSmithingRecipe recipe, T acceptor) {
        if (recipe.getComponentCount() >= 2) {
            acceptor.addIngredients(recipe.getComponent(1));
        }
    }

    @Override
    public <T extends IIngredientAcceptor<T>> void setOutput(PaidSmithingRecipe recipe, T acceptor) {
        acceptor.addItemStack(recipe.getResultItem(RegistryAccess.EMPTY));
        // 锻造台放不下花费物品，用输出槽 tooltip 展示费用：金色标题 + 图标行，
        // 与真实锻造台悬停成品时的展示一致（费用不常驻于物品 tooltip）。
        if (acceptor instanceof IRecipeSlotBuilder slotBuilder) {
            slotBuilder.addRichTooltipCallback((view, tooltip) -> {
                List<ItemStack> costs = recipe.getCosts();
                if (costs.isEmpty()) {
                    return;
                }
                tooltip.add(Component.translatable("tooltip.lolaccessories.paid_smithing.title")
                        .withStyle(ChatFormatting.GOLD));
                tooltip.add(new PaidCostTooltip(costs));
            });
        }
    }
}
