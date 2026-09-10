package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapelessRecipeBuilder;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Items;
import net.minecraftforge.common.crafting.conditions.IConditionBuilder;

import java.util.function.Consumer;

/**
 * 配方生成。新增配方在 {@link #buildRecipes} 里追加即可。
 */
public class ModRecipeProvider extends RecipeProvider implements IConditionBuilder {

    public ModRecipeProvider(PackOutput output) {
        super(output);
    }

    @Override
    protected void buildRecipes(Consumer<FinishedRecipe> writer) {
        // 1 个金块 → 1 枚金币（无序合成，工作台任意摆放均可）
        ShapelessRecipeBuilder.shapeless(RecipeCategory.MISC, ModItems.GOLD_COIN.get(), 1)
                .requires(Items.GOLD_BLOCK)
                .unlockedBy("has_gold_block", has(Items.GOLD_BLOCK))
                .save(writer, ResourceLocation.fromNamespaceAndPath(LOLAccessories.MOD_ID, "gold_coin_from_gold_block"));
    }
}
