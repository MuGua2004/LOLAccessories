package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.IronsCompat;
import net.minecraft.core.registries.Registries;
import net.minecraft.network.chat.Component;
import net.minecraft.world.item.CreativeModeTab;
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
                        // 已有三件
                        output.accept(ModItems.BLACK_CLEAVER.get());
                        output.accept(ModItems.INFINITY_EDGE.get());
                        // 卢登的回声依赖铁魔法属性与技能，未安装铁魔法时不可见、不可用
                        if (IronsCompat.isLoaded()) {
                            output.accept(ModItems.LUDENS_ECHO.get());
                        }
                        // 本次新增 14 件（通用属性装备，任意模组环境均可穿戴）
                        output.accept(ModItems.BOOTS.get());
                        output.accept(ModItems.FAERIE_CHARM.get());
                        output.accept(ModItems.REJUVENATION_BEAD.get());
                        output.accept(ModItems.CLOAK_OF_AGILITY.get());
                        output.accept(ModItems.BLASTING_WAND.get());
                        output.accept(ModItems.SAPPHIRE_CRYSTAL.get());
                        output.accept(ModItems.RUBY_CRYSTAL.get());
                        output.accept(ModItems.CLOTH_ARMOR.get());
                        output.accept(ModItems.NULL_MAGIC_MANTLE.get());
                        output.accept(ModItems.LONG_SWORD.get());
                        output.accept(ModItems.PICKAXE.get());
                        output.accept(ModItems.BF_SWORD.get());
                        output.accept(ModItems.DAGGER.get());
                        output.accept(ModItems.AMPLIFYING_TOME.get());
                        // 第二批 9 件（多兰系出门装与经典散件）
                        output.accept(ModItems.DORAN_SHIELD.get());
                        output.accept(ModItems.DORAN_BLADE.get());
                        output.accept(ModItems.DORAN_RING.get());
                        output.accept(ModItems.NEEDLESSLY_LARGE_ROD.get());
                        output.accept(ModItems.DARK_SEAL.get());
                        // 2026 传说第 2 批八件
                        output.accept(ModItems.MEJAIS_SOULSTEALER.get());
                        output.accept(ModItems.PHANTOM_DANCER.get());
                        output.accept(ModItems.ZEKES_CONVERGENCE.get());
                        output.accept(ModItems.STERAKS_GAGE.get());
                        output.accept(ModItems.SPIRIT_VISAGE.get());
                        output.accept(ModItems.SUNFIRE_AEGIS.get());
                        output.accept(ModItems.BLOODTHIRSTER.get());
                        output.accept(ModItems.EXPERIMENTAL_HEXPLATE.get());
                        output.accept(ModItems.DORAN_BOW.get());
                        output.accept(ModItems.DORAN_HELMET.get());
                        output.accept(ModItems.GLOWING_MOTE.get());
                        output.accept(ModItems.TEAR_OF_GODDESS.get());
                        // 第三批 4 件（升级散件：金币付费锻造获得）
                        output.accept(ModItems.GIANT_BELT.get());
                        output.accept(ModItems.CHAIN_VEST.get());
                        output.accept(ModItems.RECURVE_BOW.get());
                        output.accept(ModItems.VAMPIRIC_SCEPTER.get());
                        // 第四批 5 件 2 级装备（2024 新赛季组件与法系护臂，锻造台付费合成获得）
                        output.accept(ModItems.NEGATRON_CLOAK.get());
                        output.accept(ModItems.STEEL_SIGIL.get());
                        output.accept(ModItems.BRUTALIZER.get());
                        output.accept(ModItems.TUNNELER.get());
                        output.accept(ModItems.SEEKERS_ARMGUARD.get());
                        // 测试饰品（调试用，后续移除）
                        output.accept(ModItems.DRAW_SPEED_TEST.get());
                        output.accept(ModItems.FLAT_PEN_TEST.get());
                        output.accept(ModItems.PCT_PEN_TEST.get());
                        // 第五批 34 件 2 级（史诗）装备：2024/2026 新组件与史诗，锻造台付费合成获得
                        output.accept(ModItems.AETHER_WISP.get());
                        output.accept(ModItems.BAMIS_CINDER.get());
                        output.accept(ModItems.BANDLEGLASS_MIRROR.get());
                        output.accept(ModItems.BLIGHTING_JEWEL.get());
                        output.accept(ModItems.BRAMBLE_VEST.get());
                        output.accept(ModItems.CATALYST_OF_AEONS.get());
                        output.accept(ModItems.CAULFIELDS_WARHAMMER.get());
                        output.accept(ModItems.CRYSTALLINE_BRACER.get());
                        output.accept(ModItems.EXECUTIONERS_CALLING.get());
                        output.accept(ModItems.FATED_ASHES.get());
                        output.accept(ModItems.FIENDISH_CODEX.get());
                        output.accept(ModItems.FORBIDDEN_IDOL.get());
                        output.accept(ModItems.GLACIAL_BUCKLER.get());
                        output.accept(ModItems.HAUNTING_GUISE.get());
                        output.accept(ModItems.HEARTHBOUND_AXE.get());
                        output.accept(ModItems.HEXDRINKER.get());
                        output.accept(ModItems.HEXTECH_ALTERNATOR.get());
                        output.accept(ModItems.KINDLEGEM.get());
                        output.accept(ModItems.LAST_WHISPER.get());
                        output.accept(ModItems.LOST_CHAPTER.get());
                        output.accept(ModItems.NOONQUIVER.get());
                        output.accept(ModItems.OBLIVION_ORB.get());
                        output.accept(ModItems.PHAGE.get());
                        output.accept(ModItems.QUICKSILVER_SASH.get());
                        output.accept(ModItems.RECTRIX.get());
                        output.accept(ModItems.SCOUTS_SLINGSHOT.get());
                        output.accept(ModItems.SERRATED_DIRK.get());
                        output.accept(ModItems.SHEEN.get());
                        output.accept(ModItems.SPECTRES_COWL.get());
                        output.accept(ModItems.TIAMAT.get());
                        output.accept(ModItems.VERDANT_BARRIER.get());
                        output.accept(ModItems.WARDENS_MAIL.get());
                        output.accept(ModItems.WINGED_MOONPLATE.get());
                        output.accept(ModItems.ZEAL.get());
                        // 第六批 5 件 3 级（传说）装备：锻造台付费合成获得
                        output.accept(ModItems.SHURELYAS_BATTLESONG.get());
                        output.accept(ModItems.OVERLORDS_BLOODMAIL.get());
                        output.accept(ModItems.UNENDING_DESPAIR.get());
                        output.accept(ModItems.BLACKFIRE_TORCH.get());
                        output.accept(ModItems.KAENIC_ROOKERN.get());
                        // 第七批 8 件 3 级（传说）装备：2026 海克斯赛季，锻造台付费合成获得
                        output.accept(ModItems.DUSK_AND_DAWN.get());
                        output.accept(ModItems.FIENDHUNTER_BOLTS.get());
                        output.accept(ModItems.ENDLESS_HUNGER.get());
                        output.accept(ModItems.BASTIONBREAKER.get());
                        output.accept(ModItems.ACTUALIZER.get());
                        output.accept(ModItems.HEXOPTICS_C44.get());
                        output.accept(ModItems.BANDLEPIPES.get());
                        output.accept(ModItems.PROTOPLASM_HARNESS.get());
                        // 澄空之愿（首件 4 级神话装备）
                        output.accept(ModItems.CLEAR_SKYS_WISH.get());
                        output.accept(ModItems.DEMON_HEART.get());
                        // 女神泪系列传说（8 件全部放入创造栏；蜕变版同时保留满层自动蜕变途径）
                        output.accept(ModItems.MANAMUNE.get());
                        output.accept(ModItems.MURAMANA.get());
                        output.accept(ModItems.ARCHANGELS_STAFF.get());
                        output.accept(ModItems.SERAPHS_EMBRACE.get());
                        output.accept(ModItems.WINTERS_APPROACH.get());
                        output.accept(ModItems.FIMBULWINTER.get());
                        output.accept(ModItems.WHISPERING_CIRCLET.get());
                        output.accept(ModItems.DIADEM_OF_SONGS.get());
                        // AD 物理系传说（守护天使 / 育恩塔尔荒野箭 / 凡性的提醒 / 多米尼克领主的致意）
                        output.accept(ModItems.GUARDIAN_ANGEL.get());
                        output.accept(ModItems.YUN_TAL_WILDARROWS.get());
                        output.accept(ModItems.MORTAL_REMINDER.get());
                        output.accept(ModItems.LORD_DOMINIKS_REGARDS.get());
                        // 金币（货币材料）
                        output.accept(ModItems.GOLD_COIN.get());
                    })
                    .build());

    private ModCreativeModeTabs() {
    }
}
