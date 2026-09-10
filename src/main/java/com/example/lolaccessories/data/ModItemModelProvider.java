package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.data.PackOutput;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.client.model.generators.ItemModelProvider;
import net.minecraftforge.client.model.generators.ModelFile;
import net.minecraftforge.common.data.ExistingFileHelper;

/**
 * 物品模型生成。生成的是 assets/lolaccessories/models/item/*.json，
 * 引用的纹理需要在 assets/lolaccessories/textures/item/ 下放置 16x16 PNG。
 */
public class ModItemModelProvider extends ItemModelProvider {

    public ModItemModelProvider(PackOutput output, ExistingFileHelper existingFileHelper) {
        super(output, LOLAccessories.MOD_ID, existingFileHelper);
    }

    @Override
    protected void registerModels() {
        // 普通物品：仅需一张 textures/item/<注册名>.png
        basicItem(ModItems.BLACK_CLEAVER.get());
        basicItem(ModItems.LUDENS_ECHO.get());
        basicItem(ModItems.INFINITY_EDGE.get());
        basicItem(ModItems.BOOTS.get());
        basicItem(ModItems.FAERIE_CHARM.get());
        basicItem(ModItems.REJUVENATION_BEAD.get());
        basicItem(ModItems.CLOAK_OF_AGILITY.get());
        basicItem(ModItems.BLASTING_WAND.get());
        basicItem(ModItems.SAPPHIRE_CRYSTAL.get());
        basicItem(ModItems.RUBY_CRYSTAL.get());
        basicItem(ModItems.CLOTH_ARMOR.get());
        basicItem(ModItems.NULL_MAGIC_MANTLE.get());
        basicItem(ModItems.LONG_SWORD.get());
        basicItem(ModItems.PICKAXE.get());
        basicItem(ModItems.BF_SWORD.get());
        basicItem(ModItems.DAGGER.get());
        basicItem(ModItems.AMPLIFYING_TOME.get());
        // 第二批普通装备
        basicItem(ModItems.DORAN_SHIELD.get());
        basicItem(ModItems.DORAN_BLADE.get());
        basicItem(ModItems.DORAN_RING.get());
        basicItem(ModItems.NEEDLESSLY_LARGE_ROD.get());
        basicItem(ModItems.DARK_SEAL.get());
        // 2026 传说第 2 批八件
        basicItem(ModItems.MEJAIS_SOULSTEALER.get());
        basicItem(ModItems.PHANTOM_DANCER.get());
        basicItem(ModItems.ZEKES_CONVERGENCE.get());
        basicItem(ModItems.STERAKS_GAGE.get());
        basicItem(ModItems.SPIRIT_VISAGE.get());
        basicItem(ModItems.SUNFIRE_AEGIS.get());
        basicItem(ModItems.BLOODTHIRSTER.get());
        basicItem(ModItems.EXPERIMENTAL_HEXPLATE.get());
        basicItem(ModItems.DORAN_BOW.get());
        basicItem(ModItems.DORAN_HELMET.get());
        basicItem(ModItems.GLOWING_MOTE.get());
        basicItem(ModItems.TEAR_OF_GODDESS.get());
        // 第三批（升级散件）
        basicItem(ModItems.GIANT_BELT.get());
        basicItem(ModItems.CHAIN_VEST.get());
        basicItem(ModItems.RECURVE_BOW.get());
        basicItem(ModItems.VAMPIRIC_SCEPTER.get());
        // 第四批（新 2 级装备：负极斗篷 / 钢铁印章 / 残暴之力 / 掘道钻头 / 探索者的护臂）
        basicItem(ModItems.NEGATRON_CLOAK.get());
        basicItem(ModItems.STEEL_SIGIL.get());
        basicItem(ModItems.BRUTALIZER.get());
        basicItem(ModItems.TUNNELER.get());
        basicItem(ModItems.SEEKERS_ARMGUARD.get());
        // 第五批（34 件 2 级装备：新组件/史诗）
        basicItem(ModItems.AETHER_WISP.get());
        basicItem(ModItems.BAMIS_CINDER.get());
        basicItem(ModItems.BANDLEGLASS_MIRROR.get());
        basicItem(ModItems.BLIGHTING_JEWEL.get());
        basicItem(ModItems.BRAMBLE_VEST.get());
        basicItem(ModItems.CATALYST_OF_AEONS.get());
        basicItem(ModItems.CAULFIELDS_WARHAMMER.get());
        basicItem(ModItems.CRYSTALLINE_BRACER.get());
        basicItem(ModItems.EXECUTIONERS_CALLING.get());
        basicItem(ModItems.FATED_ASHES.get());
        basicItem(ModItems.FIENDISH_CODEX.get());
        basicItem(ModItems.FORBIDDEN_IDOL.get());
        basicItem(ModItems.GLACIAL_BUCKLER.get());
        basicItem(ModItems.HAUNTING_GUISE.get());
        basicItem(ModItems.HEARTHBOUND_AXE.get());
        basicItem(ModItems.HEXDRINKER.get());
        basicItem(ModItems.HEXTECH_ALTERNATOR.get());
        basicItem(ModItems.KINDLEGEM.get());
        basicItem(ModItems.LAST_WHISPER.get());
        basicItem(ModItems.LOST_CHAPTER.get());
        basicItem(ModItems.NOONQUIVER.get());
        basicItem(ModItems.OBLIVION_ORB.get());
        basicItem(ModItems.PHAGE.get());
        basicItem(ModItems.QUICKSILVER_SASH.get());
        basicItem(ModItems.RECTRIX.get());
        basicItem(ModItems.SCOUTS_SLINGSHOT.get());
        basicItem(ModItems.SERRATED_DIRK.get());
        basicItem(ModItems.SHEEN.get());
        basicItem(ModItems.SPECTRES_COWL.get());
        basicItem(ModItems.TIAMAT.get());
        basicItem(ModItems.VERDANT_BARRIER.get());
        basicItem(ModItems.WARDENS_MAIL.get());
        basicItem(ModItems.WINGED_MOONPLATE.get());
        basicItem(ModItems.ZEAL.get());
        // 第六批 5 件 3 级（传说）
        basicItem(ModItems.SHURELYAS_BATTLESONG.get());
        basicItem(ModItems.OVERLORDS_BLOODMAIL.get());
        basicItem(ModItems.UNENDING_DESPAIR.get());
        basicItem(ModItems.BLACKFIRE_TORCH.get());
        basicItem(ModItems.KAENIC_ROOKERN.get());
        // 第七批 8 件 3 级（传说，2026 海克斯赛季）
        basicItem(ModItems.BASTIONBREAKER.get());
        basicItem(ModItems.ENDLESS_HUNGER.get());
        basicItem(ModItems.HEXOPTICS_C44.get());
        basicItem(ModItems.BANDLEPIPES.get());
        basicItem(ModItems.PROTOPLASM_HARNESS.get());
        basicItem(ModItems.ACTUALIZER.get());
        basicItem(ModItems.DUSK_AND_DAWN.get());
        basicItem(ModItems.FIENDHUNTER_BOLTS.get());
        // 测试饰品
        basicItem(ModItems.DRAW_SPEED_TEST.get());
        basicItem(ModItems.FLAT_PEN_TEST.get());
        basicItem(ModItems.PCT_PEN_TEST.get());
        // 澄空之愿（神话）：光效由 MythicItemGlowMixin 的物品栏独立图层扇形光芒提供，模型单层
        // 神话装备（澄空之愿 / 魔王之心）：icon 本体单层
        // （图标光效不再用 layer1 帧动画；神话装备的「魔法阵」由客户端在物品图标下方叠加绘制）
        basicItem(ModItems.CLEAR_SKYS_WISH.get());
        basicItem(ModItems.DEMON_HEART.get());
        // 女神泪系列（8 件传说，4 对蜕变）
        basicItem(ModItems.MANAMUNE.get());
        basicItem(ModItems.MURAMANA.get());
        basicItem(ModItems.ARCHANGELS_STAFF.get());
        basicItem(ModItems.SERAPHS_EMBRACE.get());
        basicItem(ModItems.WINTERS_APPROACH.get());
        basicItem(ModItems.FIMBULWINTER.get());
        basicItem(ModItems.WHISPERING_CIRCLET.get());
        basicItem(ModItems.DIADEM_OF_SONGS.get());
        basicItem(ModItems.GUARDIAN_ANGEL.get());
        basicItem(ModItems.YUN_TAL_WILDARROWS.get());
        basicItem(ModItems.MORTAL_REMINDER.get());
        basicItem(ModItems.LORD_DOMINIKS_REGARDS.get());
        // 金币
        basicItem(ModItems.GOLD_COIN.get());
    }
}
