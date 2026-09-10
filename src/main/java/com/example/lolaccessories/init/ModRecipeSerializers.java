package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.crafting.PaidSmithingRecipe;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 配方序列化器注册。
 *
 * <p>目前只有「付费锻造」{@code lolaccessories:paid_smithing} 一种：配方放在
 * {@code data/lolaccessories/recipes/*.json}，为锻造台合成装备提供
 * “组件（1~3 件下级装备）+ 花费（cost 数组，支持金币/下界之星等任意物品，
 * 取件时从背包与末影箱自动扣除）”机制。</p>
 */
public final class ModRecipeSerializers {

    public static final DeferredRegister<RecipeSerializer<?>> RECIPE_SERIALIZERS =
            DeferredRegister.create(ForgeRegistries.RECIPE_SERIALIZERS, LOLAccessories.MOD_ID);

    public static final RegistryObject<RecipeSerializer<PaidSmithingRecipe>> PAID_SMITHING =
            RECIPE_SERIALIZERS.register("paid_smithing", () -> PaidSmithingRecipe.Serializer.INSTANCE);

    private ModRecipeSerializers() {
    }
}
