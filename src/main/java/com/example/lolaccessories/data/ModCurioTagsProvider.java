package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.core.HolderLookup;
import net.minecraft.core.registries.Registries;
import net.minecraft.data.PackOutput;
import net.minecraft.data.tags.TagsProvider;
import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;
import net.minecraftforge.common.data.ExistingFileHelper;
import net.minecraftforge.registries.RegistryObject;
import org.jetbrains.annotations.Nullable;

import java.util.concurrent.CompletableFuture;

/**
 * 生成 Curios 栏位物品标签（data/curios/tags/items/hands.json 等）。
 *
 * <p>Curios 的 hands（手饰）栏位校验器是 curios:tag，物品只有加入
 * {@code curios:hands} 标签后才能被放入手饰栏。装备在配置文件里声明 slot 字段，
 * 本 Provider 按注册物品实际使用的栏位生成对应标签。</p>
 *
 * <p>注意：标签只决定「哪些物品能进该栏位」。要让栏位真的出现在玩家身上，还必须
 * 把槽位类型分配给玩家实体——见 {@code src/main/resources/data/lolaccessories/curios/entities/default_player_slots.json}。
 * Curios 不会把任何预设槽位（ring/hands 等）自动分配给玩家。</p>
 */
public class ModCurioTagsProvider extends TagsProvider<Item> {

    public ModCurioTagsProvider(PackOutput output,
                                CompletableFuture<HolderLookup.Provider> lookupProvider,
                                @Nullable ExistingFileHelper existingFileHelper) {
        super(output, Registries.ITEM, lookupProvider, LOLAccessories.MOD_ID, existingFileHelper);
    }

    @Override
    protected void addTags(HolderLookup.Provider provider) {
        // 手饰栏（hands）
        TagKey<Item> hands = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "hands"));
        this.tag(hands).add(itemKey(ModItems.BLACK_CLEAVER));
        this.tag(hands).add(itemKey(ModItems.RAVENOUS_HYDRA));
        this.tag(hands).add(itemKey(ModItems.TRINITY_FORCE));
        this.tag(hands).add(itemKey(ModItems.RUNAAN_HURRICANE));
        this.tag(hands).add(itemKey(ModItems.STATIKK_SHIV));
        this.tag(hands).add(itemKey(ModItems.WITS_END));
        this.tag(hands).add(itemKey(ModItems.RAPID_FIRECANNON));
        this.tag(hands).add(itemKey(ModItems.STORMRAZOR));
        this.tag(hands).add(itemKey(ModItems.NASHORS_TOOTH));
        this.tag(hands).add(itemKey(ModItems.GUINSOOS_RAGEBLADE));
        this.tag(hands).add(itemKey(ModItems.MERCURIAL_SCIMITAR));
        this.tag(hands).add(itemKey(ModItems.YOUMUUS_GHOSTBLADE));
        // 灵恸（神话，手饰）
        this.tag(hands).add(itemKey(ModItems.SOULS_LAMENT));
        this.tag(hands).add(itemKey(ModItems.LUDENS_ECHO));
        // 爆裂魔杖（魔杖，单手）
        this.tag(hands).add(itemKey(ModItems.BLASTING_WAND));
        // 长剑
        this.tag(hands).add(itemKey(ModItems.LONG_SWORD));
        // 暴风之剑
        this.tag(hands).add(itemKey(ModItems.BF_SWORD));
        // 多兰之盾
        this.tag(hands).add(itemKey(ModItems.DORAN_SHIELD));
        // 吸血鬼节杖
        this.tag(hands).add(itemKey(ModItems.VAMPIRIC_SCEPTER));
        // 残暴之力
        this.tag(hands).add(itemKey(ModItems.BRUTALIZER));
        // 考尔菲德的战锤
        this.tag(hands).add(itemKey(ModItems.CAULFIELDS_WARHAMMER));
        // 死刑宣告
        this.tag(hands).add(itemKey(ModItems.EXECUTIONERS_CALLING));
        // 海克斯饮魔刀
        this.tag(hands).add(itemKey(ModItems.HEXDRINKER));
        // 净蚀
        this.tag(hands).add(itemKey(ModItems.PHAGE));
        // 剑翎
        this.tag(hands).add(itemKey(ModItems.RECTRIX));
        // 锯齿短匕
        this.tag(hands).add(itemKey(ModItems.SERRATED_DIRK));
        // 耀光
        this.tag(hands).add(itemKey(ModItems.SHEEN));
        // 提亚马特
        this.tag(hands).add(itemKey(ModItems.TIAMAT));
        // 舒瑞娅的战歌（手饰，3 级传说）
        this.tag(hands).add(itemKey(ModItems.SHURELYAS_BATTLESONG));
        // 2026 海克斯赛季 8 件传说：手饰栏
        this.tag(hands).add(itemKey(ModItems.DUSK_AND_DAWN));
        this.tag(hands).add(itemKey(ModItems.ENDLESS_HUNGER));
        this.tag(hands).add(itemKey(ModItems.BASTIONBREAKER));
        // 2026 传说第 2 批：手饰栏（斯特拉克 / 饮血剑 / 海克斯注力刚壁）
        this.tag(hands).add(itemKey(ModItems.STERAKS_GAGE));
        this.tag(hands).add(itemKey(ModItems.BLOODTHIRSTER));
        this.tag(hands).add(itemKey(ModItems.EXPERIMENTAL_HEXPLATE));
        // 第十一批：手饰栏（海克斯科技枪刃 / 破败王者之刃 / 玛莫提乌斯之噬）
        this.tag(hands).add(itemKey(ModItems.HEXTECH_GUNBLADE));
        this.tag(hands).add(itemKey(ModItems.RUINED_KING));
        this.tag(hands).add(itemKey(ModItems.MAW_OF_MALMORTIUS));
        this.tag(hands).add(itemKey(ModItems.SPEAR_OF_SHOJIN));
        this.tag(hands).add(itemKey(ModItems.UMBRAL_GLAIVE));
        this.tag(hands).add(itemKey(ModItems.HULLBREAKER));

        // 护符栏（charm）
        TagKey<Item> charm = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "charm"));
        this.tag(charm).add(itemKey(ModItems.INFINITY_EDGE));
        // 仙女护符
        this.tag(charm).add(itemKey(ModItems.FAERIE_CHARM));
        // 蓝水晶
        this.tag(charm).add(itemKey(ModItems.SAPPHIRE_CRYSTAL));
        // 红水晶
        this.tag(charm).add(itemKey(ModItems.RUBY_CRYSTAL));
        this.tag(charm).add(itemKey(ModItems.FATE_DIE));
        this.tag(charm).add(itemKey(ModItems.RABADONS_DEATHCAP));
        this.tag(charm).add(itemKey(ModItems.LICH_BANE));
        this.tag(charm).add(itemKey(ModItems.BANSHEES_VEIL));
        this.tag(charm).add(itemKey(ModItems.REDEMPTION));
        // 短剑
        this.tag(charm).add(itemKey(ModItems.DAGGER));
        // 增幅典籍
        this.tag(charm).add(itemKey(ModItems.AMPLIFYING_TOME));
        // 无用大棒
        this.tag(charm).add(itemKey(ModItems.NEEDLESSLY_LARGE_ROD));
        // 荧尘
        this.tag(charm).add(itemKey(ModItems.GLOWING_MOTE));
        // 女神之泪
        this.tag(charm).add(itemKey(ModItems.TEAR_OF_GODDESS));
        // 钢铁印章（护符）
        this.tag(charm).add(itemKey(ModItems.STEEL_SIGIL));
        // 掘道钻头（护符）
        this.tag(charm).add(itemKey(ModItems.TUNNELER));
        // 以太精魂
        this.tag(charm).add(itemKey(ModItems.AETHER_WISP));
        // 班德尔玻璃镜
        this.tag(charm).add(itemKey(ModItems.BANDLEGLASS_MIRROR));
        // 枯萎珠宝
        this.tag(charm).add(itemKey(ModItems.BLIGHTING_JEWEL));
        // 命定灰烬
        this.tag(charm).add(itemKey(ModItems.FATED_ASHES));
        // 恶魔法典
        this.tag(charm).add(itemKey(ModItems.FIENDISH_CODEX));
        // 禁忌雕像
        this.tag(charm).add(itemKey(ModItems.FORBIDDEN_IDOL));
        // 海克斯科技发电机
        this.tag(charm).add(itemKey(ModItems.HEXTECH_ALTERNATOR));
        // 遗失的章节
        this.tag(charm).add(itemKey(ModItems.LOST_CHAPTER));
        // 湮灭宝珠
        this.tag(charm).add(itemKey(ModItems.OBLIVION_ORB));
        // 黯炎火炬（护符，3 级传说）
        this.tag(charm).add(itemKey(ModItems.BLACKFIRE_TORCH));
        // 实现器（护符，2026 海克斯赛季传说）
        this.tag(charm).add(itemKey(ModItems.ACTUALIZER));
        // 2026 传说第 2 批：护符栏（幻影之舞 / 基克的聚合）
        this.tag(charm).add(itemKey(ModItems.PHANTOM_DANCER));
        this.tag(charm).add(itemKey(ModItems.ZEKES_CONVERGENCE));
        this.tag(charm).add(itemKey(ModItems.MORELLONOMICON));
        // 第十四批法系传说：护符栏
        this.tag(charm).add(itemKey(ModItems.HORIZON_FOCUS));
        this.tag(charm).add(itemKey(ModItems.SHADOWFLAME));
        this.tag(charm).add(itemKey(ModItems.STORMSURGE));

        // 戒指栏（ring）
        TagKey<Item> ring = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "ring"));
        this.tag(ring).add(itemKey(ModItems.DORAN_RING));
        // 黑暗封印
        this.tag(ring).add(itemKey(ModItems.DARK_SEAL));
        // 梅贾的窃魂卷（与黑暗封印 seal_family 系列互斥、共享层数池）
        this.tag(ring).add(itemKey(ModItems.MEJAIS_SOULSTEALER));

        // 足部栏（feet）
        TagKey<Item> feet = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "feet"));
        this.tag(feet).add(itemKey(ModItems.BOOTS));
        // 带翼的月板甲（足部）
        this.tag(feet).add(itemKey(ModItems.WINGED_MOONPLATE));
        // 第十六批 7 双 2 级（史诗）鞋
        this.tag(feet).add(itemKey(ModItems.BERSERKER_GREAVES));
        this.tag(feet).add(itemKey(ModItems.SYMBIOTE_SOLES));
        this.tag(feet).add(itemKey(ModItems.SWIFT_BOOTS));
        this.tag(feet).add(itemKey(ModItems.SORCERERS_SHOES));
        this.tag(feet).add(itemKey(ModItems.PLATED_STEELCAPS));
        this.tag(feet).add(itemKey(ModItems.MERCURYS_TREADS));
        this.tag(feet).add(itemKey(ModItems.IONIAN_BOOTS));
        // 第十六批 7 双 3 级（传说）鞋
        this.tag(feet).add(itemKey(ModItems.IMMORTAL_PATH));
        this.tag(feet).add(itemKey(ModItems.SWIFTMARCH));
        this.tag(feet).add(itemKey(ModItems.GUNMETAL_GREAVES));
        this.tag(feet).add(itemKey(ModItems.CRIMSON_LUCIDITY));
        this.tag(feet).add(itemKey(ModItems.CHAINLACED_CRUSHERS));
        this.tag(feet).add(itemKey(ModItems.ARMORED_ADVANCE));
        this.tag(feet).add(itemKey(ModItems.SPELLSLINGERS_SHOES));

        // 手镯栏（bracelet）
        TagKey<Item> bracelet = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "bracelet"));
        this.tag(bracelet).add(itemKey(ModItems.REJUVENATION_BEAD));
        // 探索者的护臂（手镯）
        this.tag(bracelet).add(itemKey(ModItems.SEEKERS_ARMGUARD));
        // 晶体护腕（手镯）
        this.tag(bracelet).add(itemKey(ModItems.CRYSTALLINE_BRACER));
        // 翠绿屏障（手镯）
        this.tag(bracelet).add(itemKey(ModItems.VERDANT_BARRIER));
        // 中娅沙漏（手镯）
        this.tag(bracelet).add(itemKey(ModItems.ZHONYAS_HOURGLASS));

        // 背饰栏（back）
        TagKey<Item> back = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "back"));
        this.tag(back).add(itemKey(ModItems.CLOAK_OF_AGILITY));
        // 十字镐（背饰）
        this.tag(back).add(itemKey(ModItems.PICKAXE));
        // 多兰之剑（背饰）
        this.tag(back).add(itemKey(ModItems.DORAN_BLADE));
        // 多兰之弓（背饰）
        this.tag(back).add(itemKey(ModItems.DORAN_BOW));
        // 反曲之弓（背饰）
        this.tag(back).add(itemKey(ModItems.RECURVE_BOW));
        // 缚炉之斧（背饰）
        this.tag(back).add(itemKey(ModItems.HEARTHBOUND_AXE));
        // 最后的轻语（背饰）
        this.tag(back).add(itemKey(ModItems.LAST_WHISPER));
        // 正午箭袋（背饰）
        this.tag(back).add(itemKey(ModItems.NOONQUIVER));
        // 斥候弹弓（背饰）
        this.tag(back).add(itemKey(ModItems.SCOUTS_SLINGSHOT));
        // 狂热（背饰）
        this.tag(back).add(itemKey(ModItems.ZEAL));
        // 2026 海克斯赛季传说：背饰栏（猎魔弹仓 / 六维光学 C44）
        this.tag(back).add(itemKey(ModItems.FIENDHUNTER_BOLTS));
        this.tag(back).add(itemKey(ModItems.HEXOPTICS_C44));
        // 澄空之愿（神话，背饰）
        this.tag(back).add(itemKey(ModItems.CLEAR_SKYS_WISH));
        // 魔王之心（神话，护符）
        this.tag(charm).add(itemKey(ModItems.DEMON_HEART));
        // 翡翠城（神话，护符）
        this.tag(charm).add(itemKey(ModItems.EMERALD_CITY));

        // 头饰栏（head）
        TagKey<Item> head = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "head"));
        this.tag(head).add(itemKey(ModItems.DORAN_HELMET));
        // 幽魂斗篷（头饰）
        this.tag(head).add(itemKey(ModItems.SPECTRES_COWL));
        // 败魔（头饰，3 级传说）
        this.tag(head).add(itemKey(ModItems.KAENIC_ROOKERN));
        // 振奋盔甲（头饰，3 级传说）
        this.tag(head).add(itemKey(ModItems.SPIRIT_VISAGE));
        // 自然之力（头饰，3 级传说）
        this.tag(head).add(itemKey(ModItems.FORCE_OF_NATURE));

        // 胸饰栏（body）
        TagKey<Item> body = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "body"));
        this.tag(body).add(itemKey(ModItems.CLOTH_ARMOR));
        // 锁子甲（胸饰）
        this.tag(body).add(itemKey(ModItems.CHAIN_VEST));
        this.tag(body).add(itemKey(ModItems.THORNMAIL));
        this.tag(body).add(itemKey(ModItems.WARMOGS_ARMOR));
        this.tag(body).add(itemKey(ModItems.KNIGHTS_VOW));
        this.tag(body).add(itemKey(ModItems.FROZEN_HEART));
        // 棘刺背心（胸饰）
        this.tag(body).add(itemKey(ModItems.BRAMBLE_VEST));
        // 守望者铠甲（胸饰）
        this.tag(body).add(itemKey(ModItems.WARDENS_MAIL));
        // 霸王血铠（胸饰，3 级传说）
        this.tag(body).add(itemKey(ModItems.OVERLORDS_BLOODMAIL));
        // 日炎圣盾（胸饰，3 级传说）
        this.tag(body).add(itemKey(ModItems.SUNFIRE_AEGIS));
        // 千变者贾修（胸饰，3 级传说）
        this.tag(body).add(itemKey(ModItems.JAKSHO));
        // 第十一批：胸饰栏（兰顿之兆）
        this.tag(body).add(itemKey(ModItems.RANDUINS_OMEN));

        // 腰带栏（belt）
        TagKey<Item> belt = TagKey.create(Registries.ITEM, new ResourceLocation("curios", "belt"));
        // 抗魔斗篷（腰带）
        this.tag(belt).add(itemKey(ModItems.NULL_MAGIC_MANTLE));
        this.tag(belt).add(itemKey(ModItems.HEARTSTEEL));
        // 巨人腰带（腰带）
        this.tag(belt).add(itemKey(ModItems.GIANT_BELT));
        // 负极斗篷（腰带）
        this.tag(belt).add(itemKey(ModItems.NEGATRON_CLOAK));
        // 斑比的熔渣
        this.tag(belt).add(itemKey(ModItems.BAMIS_CINDER));
        // 万世催化石
        this.tag(belt).add(itemKey(ModItems.CATALYST_OF_AEONS));
        // 冰川圆盾
        this.tag(belt).add(itemKey(ModItems.GLACIAL_BUCKLER));
        // 幽魂面具
        this.tag(belt).add(itemKey(ModItems.HAUNTING_GUISE));
        // 燃烧宝石
        this.tag(belt).add(itemKey(ModItems.KINDLEGEM));
        // 水银饰带
        this.tag(belt).add(itemKey(ModItems.QUICKSILVER_SASH));
        // 无终恨意（腰带，3 级传说）
        this.tag(belt).add(itemKey(ModItems.UNENDING_DESPAIR));
        // 2026 海克斯赛季传说：腰带栏（班德尔风笛 / 原生质护带）
        this.tag(belt).add(itemKey(ModItems.BANDLEPIPES));
        this.tag(belt).add(itemKey(ModItems.PROTOPLASM_HARNESS));
        // 第十一批：腰带栏（海克斯科技火箭腰带）
        this.tag(belt).add(itemKey(ModItems.HEXTECH_ROCKETBELT));
        // 裂隙制造者（腰带，3 级传说）
        this.tag(belt).add(itemKey(ModItems.RIFTMAKER));
        // 第十五批：手饰栏（公理圆弧 狂妄 亵渎九头蛇 电震涡流剑 放血者的诅咒）
        this.tag(hands).add(itemKey(ModItems.AXIOM_ARC));
        this.tag(hands).add(itemKey(ModItems.HUBRIS));
        this.tag(hands).add(itemKey(ModItems.PROFANE_HYDRA));
        this.tag(hands).add(itemKey(ModItems.VOLTAIC_CYCLOSWORD));
        this.tag(hands).add(itemKey(ModItems.BLOODLETTERS_CURSE));
        // 第十五批：头饰栏（深渊面具）
        this.tag(head).add(itemKey(ModItems.ABYSSAL_MASK));
        // 挺进破坏者（腰带，3 级传说）
        this.tag(belt).add(itemKey(ModItems.STRIDEBREAKER));
        // 第十五批：手部（死亡之舞 / 炼金朋克链锯剑 / 焚天）
        this.tag(hands).add(itemKey(ModItems.DEATHS_DANCE));
        this.tag(hands).add(itemKey(ModItems.CHEMPUNK_CHAINSWORD));
        this.tag(hands).add(itemKey(ModItems.SUNDERED_SKY));
        // 第十五批：护符（兰德里的折磨）
        this.tag(charm).add(itemKey(ModItems.LIANDRYS_TORMENT));
        // 第六批：护符（时光之杖）
        this.tag(charm).add(itemKey(ModItems.ROD_OF_AGES));
        // 第六批：腰带（冰脉护手）
        this.tag(belt).add(itemKey(ModItems.ICEBORN_GAUNTLET));
        // 第六批：手部（海妖杀手 / 不朽盾弓）
        this.tag(hands).add(itemKey(ModItems.KRAKEN_SLAYER));
        this.tag(hands).add(itemKey(ModItems.IMMORTAL_SHIELDBOW));
        // 第七批：手部（纳沃利烁刃 / 收集者 / 星蚀 / 赛瑞尔达的怨恨 / 巨蛇之牙）
        this.tag(hands).add(itemKey(ModItems.NAAVORI_FLICKERBLADE));
        this.tag(hands).add(itemKey(ModItems.THE_COLLECTOR));
        this.tag(hands).add(itemKey(ModItems.ECLIPSE));
        this.tag(hands).add(itemKey(ModItems.SERYLDAS));
        this.tag(hands).add(itemKey(ModItems.SERPENTS_FANG));

        // 女神泪系列（法力流传说，4 对蜕变）
        this.tag(hands).add(itemKey(ModItems.MANAMUNE));
        this.tag(hands).add(itemKey(ModItems.MURAMANA));
        this.tag(charm).add(itemKey(ModItems.ARCHANGELS_STAFF));
        this.tag(charm).add(itemKey(ModItems.SERAPHS_EMBRACE));
        this.tag(charm).add(itemKey(ModItems.RYLAIS_CRYSTAL_SCEPTER));
        this.tag(charm).add(itemKey(ModItems.MALIGNANCE));
        this.tag(charm).add(itemKey(ModItems.VOID_STAFF));
        this.tag(charm).add(itemKey(ModItems.CRYPTBLOOM));
        this.tag(body).add(itemKey(ModItems.WINTERS_APPROACH));
        this.tag(body).add(itemKey(ModItems.FIMBULWINTER));
        this.tag(ring).add(itemKey(ModItems.WHISPERING_CIRCLET));
        this.tag(ring).add(itemKey(ModItems.DIADEM_OF_SONGS));
        // 致明日之诗（神话，戒指）
        this.tag(ring).add(itemKey(ModItems.POEM_FOR_TOMORROW));
        this.tag(body).add(itemKey(ModItems.GUARDIAN_ANGEL));
        this.tag(back).add(itemKey(ModItems.YUN_TAL_WILDARROWS));
        this.tag(back).add(itemKey(ModItems.MORTAL_REMINDER));
        this.tag(back).add(itemKey(ModItems.LORD_DOMINIKS_REGARDS));
        this.tag(hands).add(itemKey(ModItems.TERMINUS));
        this.tag(charm).add(itemKey(ModItems.ESSENCE_REAVER));
        this.tag(body).add(itemKey(ModItems.DEAD_MANS_PLATE));
        this.tag(hands).add(itemKey(ModItems.TITANIC_HYDRA));
        this.tag(charm).add(itemKey(ModItems.EDGE_OF_NIGHT));
    }

    /** RegistryObject.getKey() 泛型为 ResourceKey<GearItem>，此处向上转型为 ResourceKey<Item> 以满足标签追加 API。 */
    @SuppressWarnings({"unchecked", "rawtypes"})
    private static ResourceKey<Item> itemKey(RegistryObject<? extends Item> item) {
        return (ResourceKey) item.getKey();
    }
}
