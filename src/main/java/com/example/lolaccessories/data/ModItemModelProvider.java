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
        basicItem(ModItems.RAVENOUS_HYDRA.get());
        basicItem(ModItems.THORNMAIL.get());
        basicItem(ModItems.TRINITY_FORCE.get());
        basicItem(ModItems.WARMOGS_ARMOR.get());
        basicItem(ModItems.HEARTSTEEL.get());
        basicItem(ModItems.FATE_DIE.get());
        basicItem(ModItems.EMERALD_CITY.get());
        basicItem(ModItems.RUNAAN_HURRICANE.get());
        basicItem(ModItems.STATIKK_SHIV.get());
        basicItem(ModItems.RABADONS_DEATHCAP.get());
        basicItem(ModItems.WITS_END.get());
        basicItem(ModItems.RAPID_FIRECANNON.get());
        basicItem(ModItems.STORMRAZOR.get());
        basicItem(ModItems.LICH_BANE.get());
        basicItem(ModItems.BANSHEES_VEIL.get());
        basicItem(ModItems.REDEMPTION.get());
        basicItem(ModItems.KNIGHTS_VOW.get());
        basicItem(ModItems.FROZEN_HEART.get());
        basicItem(ModItems.NASHORS_TOOTH.get());
        basicItem(ModItems.RYLAIS_CRYSTAL_SCEPTER.get());
        basicItem(ModItems.MALIGNANCE.get());
        basicItem(ModItems.GUINSOOS_RAGEBLADE.get());
        basicItem(ModItems.VOID_STAFF.get());
        basicItem(ModItems.CRYPTBLOOM.get());
        basicItem(ModItems.MERCURIAL_SCIMITAR.get());
        basicItem(ModItems.YOUMUUS_GHOSTBLADE.get());
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
        basicItem(ModItems.AXIOM_ARC.get());
        basicItem(ModItems.HUBRIS.get());
        basicItem(ModItems.PROFANE_HYDRA.get());
        basicItem(ModItems.VOLTAIC_CYCLOSWORD.get());
        basicItem(ModItems.BLOODLETTERS_CURSE.get());
        basicItem(ModItems.ABYSSAL_MASK.get());
        basicItem(ModItems.BERSERKER_GREAVES.get());
        basicItem(ModItems.SYMBIOTE_SOLES.get());
        basicItem(ModItems.SWIFT_BOOTS.get());
        basicItem(ModItems.SORCERERS_SHOES.get());
        basicItem(ModItems.PLATED_STEELCAPS.get());
        basicItem(ModItems.MERCURYS_TREADS.get());
        basicItem(ModItems.IONIAN_BOOTS.get());
        basicItem(ModItems.IMMORTAL_PATH.get());
        basicItem(ModItems.SWIFTMARCH.get());
        basicItem(ModItems.GUNMETAL_GREAVES.get());
        basicItem(ModItems.CRIMSON_LUCIDITY.get());
        basicItem(ModItems.CHAINLACED_CRUSHERS.get());
        basicItem(ModItems.ARMORED_ADVANCE.get());
        basicItem(ModItems.SPELLSLINGERS_SHOES.get());
        // 澄空之愿（神话）：光效由 MythicItemGlowMixin 的物品栏独立图层扇形光芒提供，模型单层
        // 神话装备（澄空之愿 / 魔王之心）：icon 本体单层
        // （图标光效不再用 layer1 帧动画；神话装备的「魔法阵」由客户端在物品图标下方叠加绘制）
        basicItem(ModItems.CLEAR_SKYS_WISH.get());
        basicItem(ModItems.DEMON_HEART.get());
        basicItem(ModItems.EMPERORS_NEW_CLOTHES.get());
        basicItem(ModItems.HEAVENLY_EMPEROR.get());
        basicItem(ModItems.SOULS_LAMENT.get());
        basicItem(ModItems.POEM_FOR_TOMORROW.get());
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
        // 第十一批 5 件装备
        basicItem(ModItems.RANDUINS_OMEN.get());
        basicItem(ModItems.HEXTECH_GUNBLADE.get());
        basicItem(ModItems.HEXTECH_ROCKETBELT.get());
        basicItem(ModItems.RUINED_KING.get());
        basicItem(ModItems.MAW_OF_MALMORTIUS.get());
        // 第十二批 5 件传说装备
        basicItem(ModItems.ZHONYAS_HOURGLASS.get());
        basicItem(ModItems.SPEAR_OF_SHOJIN.get());
        basicItem(ModItems.MORELLONOMICON.get());
        basicItem(ModItems.UMBRAL_GLAIVE.get());
        basicItem(ModItems.HULLBREAKER.get());
        // 第十三批 5 件传说装备
        basicItem(ModItems.TERMINUS.get());
        basicItem(ModItems.ESSENCE_REAVER.get());
        basicItem(ModItems.DEAD_MANS_PLATE.get());
        basicItem(ModItems.TITANIC_HYDRA.get());
        basicItem(ModItems.EDGE_OF_NIGHT.get());
        // 第十四批 5 件传说装备
        basicItem(ModItems.FORCE_OF_NATURE.get());
        basicItem(ModItems.HORIZON_FOCUS.get());
        basicItem(ModItems.RIFTMAKER.get());
        basicItem(ModItems.SHADOWFLAME.get());
        basicItem(ModItems.STORMSURGE.get());
        // 第十五批 5 件传说装备
        basicItem(ModItems.DEATHS_DANCE.get());
        basicItem(ModItems.CHEMPUNK_CHAINSWORD.get());
        basicItem(ModItems.SUNDERED_SKY.get());
        basicItem(ModItems.STRIDEBREAKER.get());
        basicItem(ModItems.LIANDRYS_TORMENT.get());
        // 第六批 5 件传说装备
        basicItem(ModItems.ROD_OF_AGES.get());
        basicItem(ModItems.ICEBORN_GAUNTLET.get());
        basicItem(ModItems.JAKSHO.get());
        basicItem(ModItems.KRAKEN_SLAYER.get());
        basicItem(ModItems.IMMORTAL_SHIELDBOW.get());
        // 第七批 5 件传说装备
        basicItem(ModItems.NAAVORI_FLICKERBLADE.get());
        basicItem(ModItems.THE_COLLECTOR.get());
        basicItem(ModItems.ECLIPSE.get());
        basicItem(ModItems.SERYLDAS.get());
        basicItem(ModItems.SERPENTS_FANG.get());
    }
}
