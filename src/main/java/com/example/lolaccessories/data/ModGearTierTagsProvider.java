package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModItemTags;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * 生成装备品阶标签（data/lolaccessories/tags/items/tier1.json 等），常量见 {@link ModItemTags}。
 *
 * <p>品阶归属与获取途径强相关，规则如下，新增装备时按此归类：
 * <ul>
 *     <li><b>1 级（普通）</b>：在工作台（crafting_shaped / crafting_shapeless）合成的装备，
 *         是锻造升级链的原料；</li>
 *     <li><b>2 级（史诗）</b>：由 1 级装备在锻造台经
 *         {@code lolaccessories:paid_smithing} 配方升级得到；</li>
 *     <li><b>3 级（传说）</b>：由 2 级装备在锻造台升级得到（含既有的
 *         黑色切割者 / 卢登的回声 / 无尽之刃）。</li>
 *     <li><b>4 级（神话）</b>：尚未实装，{@code tier4} 标签先置空预留；
 *         后续新增每件神话装备都要加入该标签。</li>
 * </ul>
 * 金币（非装备）与测试用物品不参与品阶。</p>
 */
public class ModGearTierTagsProvider extends TagsProvider<Item> {

    public ModGearTierTagsProvider(PackOutput output,
                                   CompletableFuture<HolderLookup.Provider> lookupProvider,
                                   @Nullable ExistingFileHelper existingFileHelper) {
        super(output, Registries.ITEM, lookupProvider, LOLAccessories.MOD_ID, existingFileHelper);
    }

    /**
     * 同一注册表(物品)已存在 ModCurioTagsProvider，两者若以默认名注册会被 DataGenerator
     * 判为重复（Duplicate provider）。输出路径由各 tag 自身的命名空间决定，互不影响，
     * 因此这里仅让 provider 显示名唯一化即可。
     */
    @Override
    public String getName() {
        return super.getName() + " (gear tier)";
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // ---------- 1 级（普通，工作台合成） ----------
        this.tag(ModItemTags.TIER1)
                .add(itemKey(ModItems.BOOTS))
                .add(itemKey(ModItems.FAERIE_CHARM))
                .add(itemKey(ModItems.REJUVENATION_BEAD))
                .add(itemKey(ModItems.CLOAK_OF_AGILITY))
                .add(itemKey(ModItems.BLASTING_WAND))
                .add(itemKey(ModItems.SAPPHIRE_CRYSTAL))
                .add(itemKey(ModItems.RUBY_CRYSTAL))
                .add(itemKey(ModItems.CLOTH_ARMOR))
                .add(itemKey(ModItems.NULL_MAGIC_MANTLE))
                .add(itemKey(ModItems.LONG_SWORD))
                .add(itemKey(ModItems.PICKAXE))
                .add(itemKey(ModItems.BF_SWORD))
                .add(itemKey(ModItems.DAGGER))
                .add(itemKey(ModItems.AMPLIFYING_TOME))
                .add(itemKey(ModItems.DORAN_SHIELD))
                .add(itemKey(ModItems.DORAN_BLADE))
                .add(itemKey(ModItems.DORAN_RING))
                .add(itemKey(ModItems.NEEDLESSLY_LARGE_ROD))
                .add(itemKey(ModItems.DARK_SEAL))
                .add(itemKey(ModItems.DORAN_BOW))
                .add(itemKey(ModItems.DORAN_HELMET))
                .add(itemKey(ModItems.GLOWING_MOTE))
                .add(itemKey(ModItems.TEAR_OF_GODDESS));

        // ---------- 2 级（史诗，锻造台由 1 级升级） ----------
        this.tag(ModItemTags.TIER2)
                .add(itemKey(ModItems.GIANT_BELT))
                .add(itemKey(ModItems.CHAIN_VEST))
                .add(itemKey(ModItems.RECURVE_BOW))
                .add(itemKey(ModItems.VAMPIRIC_SCEPTER))
                .add(itemKey(ModItems.NEGATRON_CLOAK))
                .add(itemKey(ModItems.STEEL_SIGIL))
                .add(itemKey(ModItems.BRUTALIZER))
                .add(itemKey(ModItems.TUNNELER))
                .add(itemKey(ModItems.SEEKERS_ARMGUARD))
                // 第五批 34 件 2 级（史诗）装备
                .add(itemKey(ModItems.AETHER_WISP))
                .add(itemKey(ModItems.BAMIS_CINDER))
                .add(itemKey(ModItems.BANDLEGLASS_MIRROR))
                .add(itemKey(ModItems.BLIGHTING_JEWEL))
                .add(itemKey(ModItems.BRAMBLE_VEST))
                .add(itemKey(ModItems.CATALYST_OF_AEONS))
                .add(itemKey(ModItems.CAULFIELDS_WARHAMMER))
                .add(itemKey(ModItems.CRYSTALLINE_BRACER))
                .add(itemKey(ModItems.EXECUTIONERS_CALLING))
                .add(itemKey(ModItems.FATED_ASHES))
                .add(itemKey(ModItems.FIENDISH_CODEX))
                .add(itemKey(ModItems.FORBIDDEN_IDOL))
                .add(itemKey(ModItems.GLACIAL_BUCKLER))
                .add(itemKey(ModItems.HAUNTING_GUISE))
                .add(itemKey(ModItems.HEARTHBOUND_AXE))
                .add(itemKey(ModItems.HEXDRINKER))
                .add(itemKey(ModItems.HEXTECH_ALTERNATOR))
                .add(itemKey(ModItems.KINDLEGEM))
                .add(itemKey(ModItems.LAST_WHISPER))
                .add(itemKey(ModItems.LOST_CHAPTER))
                .add(itemKey(ModItems.NOONQUIVER))
                .add(itemKey(ModItems.OBLIVION_ORB))
                .add(itemKey(ModItems.PHAGE))
                .add(itemKey(ModItems.QUICKSILVER_SASH))
                .add(itemKey(ModItems.RECTRIX))
                .add(itemKey(ModItems.SCOUTS_SLINGSHOT))
                .add(itemKey(ModItems.SERRATED_DIRK))
                .add(itemKey(ModItems.SHEEN))
                .add(itemKey(ModItems.SPECTRES_COWL))
                .add(itemKey(ModItems.TIAMAT))
                .add(itemKey(ModItems.VERDANT_BARRIER))
                .add(itemKey(ModItems.WARDENS_MAIL))
                .add(itemKey(ModItems.WINGED_MOONPLATE))
                .add(itemKey(ModItems.ZEAL))
                // 第十六批 7 双 2 级（史诗）鞋
                .add(itemKey(ModItems.BERSERKER_GREAVES))
                .add(itemKey(ModItems.SYMBIOTE_SOLES))
                .add(itemKey(ModItems.SWIFT_BOOTS))
                .add(itemKey(ModItems.SORCERERS_SHOES))
                .add(itemKey(ModItems.PLATED_STEELCAPS))
                .add(itemKey(ModItems.MERCURYS_TREADS))
                .add(itemKey(ModItems.IONIAN_BOOTS))
                // 第十一批：史诗级（兰顿之兆）
                .add(itemKey(ModItems.RANDUINS_OMEN));

        // ---------- 3 级（传说，锻造台由 2 级升级；含既有传说装备） ----------
        this.tag(ModItemTags.TIER3)
                .add(itemKey(ModItems.BLACK_CLEAVER))
                .add(itemKey(ModItems.LUDENS_ECHO))
                .add(itemKey(ModItems.INFINITY_EDGE))
                // 2026 传说第 2 批八件
                .add(itemKey(ModItems.MEJAIS_SOULSTEALER))
                .add(itemKey(ModItems.PHANTOM_DANCER))
                .add(itemKey(ModItems.ZEKES_CONVERGENCE))
                .add(itemKey(ModItems.STERAKS_GAGE))
                .add(itemKey(ModItems.SPIRIT_VISAGE))
                .add(itemKey(ModItems.SUNFIRE_AEGIS))
                .add(itemKey(ModItems.BLOODTHIRSTER))
                .add(itemKey(ModItems.EXPERIMENTAL_HEXPLATE))
                // 第二批五件新传说
                .add(itemKey(ModItems.SHURELYAS_BATTLESONG))
                .add(itemKey(ModItems.OVERLORDS_BLOODMAIL))
                .add(itemKey(ModItems.UNENDING_DESPAIR))
                .add(itemKey(ModItems.BLACKFIRE_TORCH))
                .add(itemKey(ModItems.KAENIC_ROOKERN))
                // 第三批 8 件传说（2026 海克斯赛季）
                .add(itemKey(ModItems.DUSK_AND_DAWN))
                .add(itemKey(ModItems.FIENDHUNTER_BOLTS))
                .add(itemKey(ModItems.ENDLESS_HUNGER))
                .add(itemKey(ModItems.BASTIONBREAKER))
                .add(itemKey(ModItems.ACTUALIZER))
                .add(itemKey(ModItems.HEXOPTICS_C44))
                .add(itemKey(ModItems.BANDLEPIPES))
                .add(itemKey(ModItems.PROTOPLASM_HARNESS))
                // 第四批 8 件传说（女神泪系列，4 对蜕变）
                .add(itemKey(ModItems.MANAMUNE))
                .add(itemKey(ModItems.MURAMANA))
                .add(itemKey(ModItems.ARCHANGELS_STAFF))
                .add(itemKey(ModItems.SERAPHS_EMBRACE))
                .add(itemKey(ModItems.WINTERS_APPROACH))
                .add(itemKey(ModItems.FIMBULWINTER))
                .add(itemKey(ModItems.WHISPERING_CIRCLET))
                .add(itemKey(ModItems.DIADEM_OF_SONGS))
                // 第五批 4 件传说（AD 物理系：守护天使 / 育恩塔尔 / 凡性 / 多米尼克）
                .add(itemKey(ModItems.GUARDIAN_ANGEL))
                .add(itemKey(ModItems.YUN_TAL_WILDARROWS))
                .add(itemKey(ModItems.MORTAL_REMINDER))
                .add(itemKey(ModItems.LORD_DOMINIKS_REGARDS))
                // 第六批 5 件传说（贪欲九头蛇 / 荆棘之甲 / 三相之力 / 狂徒铠甲 / 心之钢）
                .add(itemKey(ModItems.RAVENOUS_HYDRA))
                .add(itemKey(ModItems.THORNMAIL))
                .add(itemKey(ModItems.TRINITY_FORCE))
                .add(itemKey(ModItems.WARMOGS_ARMOR))
                .add(itemKey(ModItems.HEARTSTEEL))
                // 第七批 5 件传说（卢安娜 / 电刃 / 死亡之帽 / 智慧末刃 / 疾射火炮）
                .add(itemKey(ModItems.RUNAAN_HURRICANE))
                .add(itemKey(ModItems.STATIKK_SHIV))
                .add(itemKey(ModItems.RABADONS_DEATHCAP))
                .add(itemKey(ModItems.WITS_END))
                .add(itemKey(ModItems.RAPID_FIRECANNON))
                // 第八批 5 件传说（岚切 / 巫妖之祸 / 女妖面纱 / 救赎 / 骑士之誓）
                .add(itemKey(ModItems.STORMRAZOR))
                .add(itemKey(ModItems.LICH_BANE))
                .add(itemKey(ModItems.BANSHEES_VEIL))
                .add(itemKey(ModItems.REDEMPTION))
                .add(itemKey(ModItems.KNIGHTS_VOW))
                // 第九批 4 件传说（冰霜之心 / 纳什之牙 / 瑞莱的冰晶节杖 / 残疫）
                .add(itemKey(ModItems.FROZEN_HEART))
                .add(itemKey(ModItems.NASHORS_TOOTH))
                .add(itemKey(ModItems.RYLAIS_CRYSTAL_SCEPTER))
                .add(itemKey(ModItems.MALIGNANCE))
                // 第十批 5 件传说（鬼索的狂暴之刃 / 虚空之杖 / 蜕生 / 水银弯刀 / 幽梦之灵）
                .add(itemKey(ModItems.GUINSOOS_RAGEBLADE))
                .add(itemKey(ModItems.VOID_STAFF))
                .add(itemKey(ModItems.CRYPTBLOOM))
                .add(itemKey(ModItems.MERCURIAL_SCIMITAR))
                .add(itemKey(ModItems.YOUMUUS_GHOSTBLADE))
                // 第十一批 5 件传说（兰顿之兆为史诗，不在此列）：海克斯枪刃 / 火箭腰带 / 破败 / 玛莫提乌斯
                .add(itemKey(ModItems.HEXTECH_GUNBLADE))
                .add(itemKey(ModItems.HEXTECH_ROCKETBELT))
                .add(itemKey(ModItems.RUINED_KING))
                .add(itemKey(ModItems.MAW_OF_MALMORTIUS))
                // 第十二批 5 件传说：中娅 / 朔极 / 莫雷洛 / 黯影 / 破舰
                .add(itemKey(ModItems.ZHONYAS_HOURGLASS))
                .add(itemKey(ModItems.SPEAR_OF_SHOJIN))
                .add(itemKey(ModItems.MORELLONOMICON))
                .add(itemKey(ModItems.UMBRAL_GLAIVE))
                .add(itemKey(ModItems.HULLBREAKER))
                // 第十三批 5 件传说：界弓 / 夺萃之镰 / 亡者的板甲 / 巨型九头蛇 / 夜之锋刃
                .add(itemKey(ModItems.TERMINUS))
                .add(itemKey(ModItems.ESSENCE_REAVER))
                .add(itemKey(ModItems.DEAD_MANS_PLATE))
                .add(itemKey(ModItems.TITANIC_HYDRA))
                .add(itemKey(ModItems.EDGE_OF_NIGHT))
                // 第十四批 5 件传说：自然之力 / 视界专注 / 裂隙制造者 / 影焰 / 风暴狂涌
                .add(itemKey(ModItems.FORCE_OF_NATURE))
                .add(itemKey(ModItems.HORIZON_FOCUS))
                .add(itemKey(ModItems.RIFTMAKER))
                .add(itemKey(ModItems.SHADOWFLAME))
                .add(itemKey(ModItems.STORMSURGE))
                // 第十五批 5 件 3 级（传说）装备
                .add(itemKey(ModItems.DEATHS_DANCE))
                .add(itemKey(ModItems.CHEMPUNK_CHAINSWORD))
                .add(itemKey(ModItems.SUNDERED_SKY))
                .add(itemKey(ModItems.STRIDEBREAKER))
                .add(itemKey(ModItems.LIANDRYS_TORMENT))
                // 第六批 5 件 3 级（传说）装备
                .add(itemKey(ModItems.ROD_OF_AGES))
                .add(itemKey(ModItems.ICEBORN_GAUNTLET))
                .add(itemKey(ModItems.JAKSHO))
                .add(itemKey(ModItems.KRAKEN_SLAYER))
                .add(itemKey(ModItems.IMMORTAL_SHIELDBOW))
                // 第七批 5 件 3 级（传说）装备
                .add(itemKey(ModItems.NAAVORI_FLICKERBLADE))
                .add(itemKey(ModItems.THE_COLLECTOR))
                .add(itemKey(ModItems.ECLIPSE))
                .add(itemKey(ModItems.SERYLDAS))
                .add(itemKey(ModItems.SERPENTS_FANG))
                // 第十六批 7 双 3 级（传说）鞋
                .add(itemKey(ModItems.IMMORTAL_PATH))
                .add(itemKey(ModItems.SWIFTMARCH))
                .add(itemKey(ModItems.GUNMETAL_GREAVES))
                .add(itemKey(ModItems.CRIMSON_LUCIDITY))
                .add(itemKey(ModItems.CHAINLACED_CRUSHERS))
                .add(itemKey(ModItems.ARMORED_ADVANCE))
                .add(itemKey(ModItems.SPELLSLINGERS_SHOES));

        // ---------- 4 级（神话） ----------
        // 首件神话装备（澄空之愿）；后续神话装备像其它品阶一样 add 进来即可，
        // 「书写神话 / 不败的勇者 / 我们是冠军」等进度会自动随标签内容生效。
        this.tag(ModItemTags.TIER4)
                .add(itemKey(ModItems.CLEAR_SKYS_WISH))
                .add(itemKey(ModItems.DEMON_HEART))
                .add(itemKey(ModItems.FATE_DIE))
                .add(itemKey(ModItems.EMERALD_CITY))
                .add(itemKey(ModItems.EMPERORS_NEW_CLOTHES))
                .add(itemKey(ModItems.HEAVENLY_EMPEROR))
                .add(itemKey(ModItems.SOULS_LAMENT))
                .add(itemKey(ModItems.POEM_FOR_TOMORROW));
    }

    /** RegistryObject.getKey() 泛型为 ResourceKey<GearItem>，此处向上转型为 ResourceKey<Item> 以满足标签追加 API。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceKey<Item> itemKey(RegistryObject<? extends Item> item) {
        return (ResourceKey) item.getKey();
    }
}
