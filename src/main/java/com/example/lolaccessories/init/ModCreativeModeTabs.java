package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.IronsCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.RegistryObject;

/**
 * 创造模式物品栏注册。
 */
public final class ModCreativeModeTabs {

    /** 物品栏注册器。 */
    public static final DeferredRegister<CreativeModeTab> CREATIVE_MODE_TABS =
            DeferredRegister.create(Registries.CREATIVE_MODE_TAB, LOLAccessories.MOD_ID);

    /** LOLAccessories 专属物品栏，所有本模组的物品都挂到这里。 */
    public static final RegistryObject<CreativeModeTab> LOLACCESSORIES_TAB =
            CREATIVE_MODE_TABS.register("lolaccessories_tab", () -> CreativeModeTab.builder()
                    .icon(() -> new ItemStack(ModItems.BLACK_CLEAVER.get()))
                    .title(Component.translatable("itemGroup." + LOLAccessories.MOD_ID))
                    // 这里把需要在物品栏中出现的物品逐个放进去
                    .displayItems((parameters, output) -> {
                        // 装备按品阶（1级普通 → 2级史诗 → 3级传说 → 4级神话）依次展示；
                        // 顺序取自各品阶标签（与 datagen 添加顺序一致），新增装备自动归位。
                        java.util.List<net.minecraft.tags.TagKey<net.minecraft.world.item.Item>> tiers =
                                java.util.List.of(ModItemTags.TIER1, ModItemTags.TIER2,
                                        ModItemTags.TIER3, ModItemTags.TIER4);
                        for (net.minecraft.tags.TagKey<net.minecraft.world.item.Item> tier : tiers) {
                            net.minecraft.core.registries.BuiltInRegistries.ITEM.getTag(tier)
                                    .ifPresent(named -> named.stream().forEach(holder -> {
                                        Item item = holder.value();
                                        // 卢登的回声依赖铁魔法属性与技能，未安装铁魔法时不可见、不可用
                                        if (item == ModItems.LUDENS_ECHO.get() && !IronsCompat.isLoaded()) {
                                            return;
                                        }
                                        output.accept(new ItemStack(item));
                                    }));
                        }
                        // 非装备物品放最后：货币与材料
                        output.accept(ModItems.GOLD_COIN.get());
                        output.accept(ModItems.GLOWING_MOTE.get());
                        // 测试饰品（调试用，后续移除）
                        // 第九批 4 件传说（冰霜之心 / 纳什之牙 / 瑞莱 / 残疫）
                        output.accept(ModItems.FROZEN_HEART.get());
                        output.accept(ModItems.NASHORS_TOOTH.get());
                        output.accept(ModItems.RYLAIS_CRYSTAL_SCEPTER.get());
                        output.accept(ModItems.MALIGNANCE.get());
                        output.accept(ModItems.GUINSOOS_RAGEBLADE.get());
                        output.accept(ModItems.VOID_STAFF.get());
                        output.accept(ModItems.CRYPTBLOOM.get());
                        output.accept(ModItems.MERCURIAL_SCIMITAR.get());
                        output.accept(ModItems.YOUMUUS_GHOSTBLADE.get());
                        // 第十一批 5 件传说/史诗（已在品阶标签里，此处冗余备援）
                        output.accept(ModItems.RANDUINS_OMEN.get());
                        output.accept(ModItems.HEXTECH_GUNBLADE.get());
                        output.accept(ModItems.HEXTECH_ROCKETBELT.get());
                        output.accept(ModItems.RUINED_KING.get());
                        output.accept(ModItems.MAW_OF_MALMORTIUS.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
