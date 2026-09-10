package com.example.lolaccessories.compat.jei;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.crafting.PaidSmithingRecipe;
import mezz.jei.api.IModPlugin;
import mezz.jei.api.JeiPlugin;
import mezz.jei.api.registration.IVanillaCategoryExtensionRegistration;
import net.minecraft.resources.ResourceLocation;

/**
 * JEI 集成入口。整个类只被 JEI 扫描并加载（{@code @JeiPlugin}），
 * 未装 JEI 的环境中不会触碰任何 mezz 类，可安全发布。
 */
@JeiPlugin
public class LolJeiPlugin implements IModPlugin {

    private static final ResourceLocation UID = new ResourceLocation(LOLAccessories.MOD_ID, "jei_plugin");

    @Override
    public ResourceLocation getPluginUid() {
        return UID;
    }

    @Override
    public void registerVanillaCategoryExtensions(IVanillaCategoryExtensionRegistration registration) {
        registration.getSmithingCategory()
                .addExtension(PaidSmithingRecipe.class, new PaidSmithingJeiExtension());
    }
}
