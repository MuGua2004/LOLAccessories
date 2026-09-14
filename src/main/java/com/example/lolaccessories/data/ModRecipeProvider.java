package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.data.recipes.FinishedRecipe;
import net.minecraft.data.recipes.RecipeCategory;
import net.minecraft.data.recipes.RecipeProvider;
import net.minecraft.data.recipes.ShapedRecipeBuilder;
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
        // 5 个金粒摆成十字形 → 1 枚金币
        ShapedRecipeBuilder.shaped(RecipeCategory.MISC, ModItems.GOLD_COIN.get(), 1)
                .pattern(" G ")
                .pattern("GGG")
                .pattern(" G ")
                .define('G', Items.GOLD_NUGGET)
                .unlockedBy("has_gold_nugget", has(Items.GOLD_NUGGET))
                .save(writer, ResourceLocation.fromNamespaceAndPath(LOLAccessories.MOD_ID, "gold_coin_from_gold_nuggets"));
    }
}
