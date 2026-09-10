package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.item.GearItem;
import com.example.lolaccessories.item.GoldCoinItem;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 物品注册。新增装备时，按下方「注册 → 配置文件 → 配方 → 模型/语言」流程走：
 * <ol>
 *   <li>在 {@code init/ModItems} 追加一个 {@code ITEMS.register(...)} 条目；</li>
 *   <li>在 {@code config/lolaccessories/gear/} 准备同名 JSON 配置（首次运行会自动从模板复制）；</li>
 *   <li>合成一律写成数据包配方 JSON（工作台用原版 crafting_shaped/crafting_shapeless，
 *       升级链用自定义 {@code lolaccessories:paid_smithing}），放在
 *       {@code data/lolaccessories/recipes/<装备id>.json}，<b>禁止在 Java 代码里动态注册配方</b>——
 *       这样整合包作者才能用同名文件覆盖、用 Forge conditions 禁用（后续新增装备同样遵守）；</li>
 *   <li>数据生成自动产出模型与语言文件（纹理放在 textures/item/ 下）；</li>
 *   <li>在创造模式物品栏与 Curios 栏位标签中加入该物品。</li>
 * </ol>
 */
public final class ModItems {

    /** 物品注册器。 */
    public static final DeferredRegister<Item> ITEMS =
            DeferredRegister.create(ForgeRegistries.ITEMS, LOLAccessories.MOD_ID);

    /**
     * 黑色切割者（The Black Cleaver）。
     * 栏位：手饰（Curios hands）；数值全部来自配置文件 black_cleaver.json。
     */
    public static final RegistryObject<GearItem> BLACK_CLEAVER =
            ITEMS.register("black_cleaver", () -> {
                GearConfig config = GearConfigManager.get("black_cleaver");
                int maxStack = Math.max(1, config.max_stack_size);
                return new GearItem("black_cleaver",
                        new Item.Properties().stacksTo(maxStack));
            });

    /**
     * 卢登的回声（Luden's Echo）。
     * 栏位：手饰（Curios hands）；数值与「回声」技能全部来自配置文件 ludens_echo.json。
     * 物品始终注册以保证 Curios 标签/存档稳定，但仅在安装了铁魔法时才会出现在创造栏
     * （见 ModCreativeModeTabs），其铁魔法属性与技能在未装铁魔法时不会解析、不会生效。
     */
    public static final RegistryObject<GearItem> LUDENS_ECHO =
            ITEMS.register("ludens_echo", () -> {
                GearConfig config = GearConfigManager.get("ludens_echo");
                int maxStack = Math.max(1, config.max_stack_size);
                return new GearItem("ludens_echo",
                        new Item.Properties().stacksTo(maxStack));
            });

    /**
     * 无尽之刃（Infinity Edge）。
     * 栏位：护符（Curios charm）；数值全部来自配置文件 infinity_edge.json。
     * 传说级暴击装备：攻击力 + 传说暴击率 + 传说暴击伤害，不与同名牌（任意 LOLAccessories
     * 装备）重复佩戴（唯一限制由 GearItem 统一实现）。
     */
    public static final RegistryObject<GearItem> INFINITY_EDGE =
            ITEMS.register("infinity_edge", () -> {
                GearConfig config = GearConfigManager.get("infinity_edge");
                int maxStack = Math.max(1, config.max_stack_size);
                return new GearItem("infinity_edge",
                        new Item.Properties().stacksTo(maxStack));
            });

    /**
     * 舒瑞娅的战歌（Shurelya's Battlesong）。槽位：手饰 hands。
     * +50% 通用法术强度 / +15% 冷却缩减 / +4% 移动速度 / +125% 法力恢复。
     * 主动「鼓舞（Inspiring Speech）」：对佩戴者与附近友方玩家施加短时移速加成（仅作用于玩家，
     * 不对生物生效）。数值来自 shurelyas_battlesong.json。
     */
    public static final RegistryObject<GearItem> SHURELYAS_BATTLESONG =
            ITEMS.register("shurelyas_battlesong",
                    () -> new GearItem("shurelyas_battlesong", gearProperties("shurelyas_battlesong")));

    /**
     * 霸王血铠（Overlord's Bloodmail）。槽位：胸饰 body。
     * +30 攻击力 / +550 最大生命值。
     * 被动「暴政 / 报应（Tyranny / Retribution）」：攻击伤害加成 = 最大生命 × 2.5%，
     * 生命越低至多再提升（当前总攻击伤害 − 报应自身）× 12%；实时挂到佩戴者攻击伤害属性上，
     * AttributesLib 面板动态显示。数值来自 overlords_bloodmail.json。
     */
    public static final RegistryObject<GearItem> OVERLORDS_BLOODMAIL =
            ITEMS.register("overlords_bloodmail",
                    () -> new GearItem("overlords_bloodmail", gearProperties("overlords_bloodmail")));

    /**
     * 无终恨意（Unending Despair）。槽位：腰带 belt。
     * +400 最大生命值 / +50 护甲 / +15% 冷却缩减。
     * 被动「苦楚（Anguish）」：战斗中每 4 秒对周围目标造成最大生命 3% 的魔法伤害，
     * 并按伤害的 250% 回复自身。数值来自 unending_despair.json。
     */
    public static final RegistryObject<GearItem> UNENDING_DESPAIR =
            ITEMS.register("unending_despair",
                    () -> new GearItem("unending_despair", gearProperties("unending_despair")));

    /**
     * 黯炎火炬（Blackfire Torch）。槽位：护符 charm。
     * +80% 通用法术强度 / +600 最大法力 / +20% 冷却缩减。
     * 被动「不祥灼烧（Baleful Blaze）」：佩戴者的法术（魔法）伤害会灼烧目标 3 秒，
     * 每跳 10 + 1% 法强的魔法伤害；每名被灼烧的敌人使佩戴者的魔法伤害提高 4%。
     * 数值来自 blackfire_torch.json。
     */
    public static final RegistryObject<GearItem> BLACKFIRE_TORCH =
            ITEMS.register("blackfire_torch",
                    () -> new GearItem("blackfire_torch", gearProperties("blackfire_torch")));

    /**
     * 败魔（Kaenic Rookern）。槽位：头饰 head。
     * +400 最大生命值 / +80 传说魔法抗性 / +100% 自然生命恢复。
     * 被动「法师之祸（Magebane）」：15 秒未受到魔法伤害时获得最大生命 15% 的护盾
     * （生命提升形态），受到魔法伤害即破除并重计时。数值来自 kaenic_rookern.json。
     */
    public static final RegistryObject<GearItem> KAENIC_ROOKERN =
            ITEMS.register("kaenic_rookern",
                    () -> new GearItem("kaenic_rookern", gearProperties("kaenic_rookern")));

    /**
     * 黄昏与黎明（Dusk and Dawn）。槽位：手饰 hands。
     * +300 最大生命值 / +60% 通用法术强度 / +20% 冷却缩减 / +20% 攻速。
     * 被动「咒刃（Spellblade）」：施放铁魔法法术后，下次普攻命中附加
     * 0.75×攻击力 + 0.10×法强的魔法伤害并按 0.10×法强 + 0.03×总生命治疗，
     * 随后 0.2 秒对目标再结算一次攻击命中特效（不结算基础物伤）。数值来自 dusk_and_dawn.json。
     */
    public static final RegistryObject<GearItem> DUSK_AND_DAWN =
            ITEMS.register("dusk_and_dawn",
                    () -> new GearItem("dusk_and_dawn", gearProperties("dusk_and_dawn")));

    /**
     * 猎魔人弩箭（Fiendhunter Bolts）。槽位：背饰 back。
     * +45% 攻速 / +25% 暴击率 / +4% 移动速度 / +30% 终极技能冷却缩减（lolaccessories:ultimate_cdr，
     * 对应原作终极技能急速 30、不按比例缩减直接生效，仅缩短终极技能冷却）。
     * 被动「开战弹幕（Barrage）」（仅远程普攻）：释放终极技能（基础法力消耗 > 200）后 8 秒内
     * 接下来 3 次远程攻击：本应暴击则改为附加 15% 真实伤害，否则强制按 80% 常规暴击伤害暴击。
     * 数值来自 fiendhunter_bolts.json。
     */
    public static final RegistryObject<GearItem> FIENDHUNTER_BOLTS =
            ITEMS.register("fiendhunter_bolts",
                    () -> new GearItem("fiendhunter_bolts", gearProperties("fiendhunter_bolts")));

    /**
     * 无穷饥渴（Endless Hunger）。槽位：手饰 hands。
     * +65 攻击力 / +5% 全能吸血 / +20% 传说韧性。
     * 被动「饥馑（Famine）」：额外获得 5 + 13%×攻击力（近战口径，不区分近战/远程）的冷却缩减；
     * 被动「盛宴（Feast）」：3 秒内被你伤害过的目标死亡时获得 15% 全能吸血，持续 8 秒。
     * 数值来自 endless_hunger.json。
     */
    public static final RegistryObject<GearItem> ENDLESS_HUNGER =
            ITEMS.register("endless_hunger",
                    () -> new GearItem("endless_hunger", gearProperties("endless_hunger")));

    /**
     * 破垒者（Bastionbreaker）。槽位：手饰 hands。
     * +55 攻击力 / +22 穿甲 / +15% 冷却缩减。
     * 被动「成型炸药（Shaped Charge）」（近战普攻）：对非友善目标命中时附加
     * 50 + 1.5×穿甲的真实伤害（内置 20 秒冷却）。数值来自 bastionbreaker.json。
     */
    public static final RegistryObject<GearItem> BASTIONBREAKER =
            ITEMS.register("bastionbreaker",
                    () -> new GearItem("bastionbreaker", gearProperties("bastionbreaker")));

    /**
     * 实现器（Actualizer）。槽位：护符 charm。
     * +90% 通用法术强度 / +300 最大法力 / +10% 冷却缩减。
     * 主动「法力成真（Realize）」：开启后 5 秒内所有铁魔法法术冷却近乎立即完成，
     * 期间施法魔力消耗翻倍；冷却 180 秒且不受冷却缩减影响。数值来自 actualizer.json。
     */
    public static final RegistryObject<GearItem> ACTUALIZER =
            ITEMS.register("actualizer",
                    () -> new GearItem("actualizer", gearProperties("actualizer")));

    /**
     * 海克斯镜片 C44（Hexoptics C44）。槽位：背饰 back。
     * +55% 弹射物伤害 / +25% 暴击率。
     * 被动「高倍望远镜（Long Shot）」（远程普攻）：命中距离每接近 50 格增伤至多 10%。
     * 数值来自 hexoptics_c44.json。
     */
    public static final RegistryObject<GearItem> HEXOPTICS_C44 =
            ITEMS.register("hexoptics_c44",
                    () -> new GearItem("hexoptics_c44", gearProperties("hexoptics_c44")));

    /**
     * 班德尔音管（Bandlepipes）。槽位：腰带 belt。
     * +200 最大生命值 / +15% 冷却缩减 / +20 护甲 / +20 传说魔法抗性。
     * 被动「嘹亮旋律（Fanfare）」：对目标施加负面药水效果后获得 +20% 移速，
     * 并让自身与附近友军获得 +30% 攻速，持续 8 秒（内置 30 秒冷却）。
     * 数值来自 bandlepipes.json。
     */
    public static final RegistryObject<GearItem> BANDLEPIPES =
            ITEMS.register("bandlepipes",
                    () -> new GearItem("bandlepipes", gearProperties("bandlepipes")));

    /**
     * 澄空之愿（Clear Sky's Wish）。槽位：背饰 back。首件 4 级（神话）装备。
     * +100% 弹射物伤害 / +100% 蓄力速度（attributeslib:arrow_damage / draw_speed）。
     * 唯一被动「澄澈天空（Clear Sky）」：造成的所有弹射物伤害有 50% 的概率
     * 转化为虚空伤害（minecraft:out_of_world，无视护甲与魔抗减免）。
     * 数值来自 clear_skys_wish.json。
     */
    public static final RegistryObject<GearItem> CLEAR_SKYS_WISH =
            ITEMS.register("clear_skys_wish",
                    () -> new GearItem("clear_skys_wish", gearProperties("clear_skys_wish")));

    /**
     * 魔王之心（Demon Heart）。第 2 件神话装备（tier4）。
     * +666 生命/攻击力/护甲/魔抗/最大法力/穿甲/法穿、+666% 弹射物伤害与法强。
     * 唯一被动「我是大魔王（Demon King）」：每个诅咒附魔或负面药水效果使全部数值 +10%，
     * 每个正面附魔或正面药水效果使全部数值 -15%（加算相抵，见 LolDemonHeartEvents）。
     * 获取：最大生命 ≥ 100,000 且收集传说装备 ≥ 50 种（可魔改，见 mythic/demon_heart.json）。
     */
    public static final RegistryObject<GearItem> DEMON_HEART =
            ITEMS.register("demon_heart",
                    () -> new GearItem("demon_heart", gearProperties("demon_heart")));

    /**
     * 原生质护带（Protoplasm Harness）。槽位：腰带 belt。
     * +600 最大生命值 / +20% 冷却缩减。
     * 被动「救主灵刃（Protoplasm Lifeline）」：承受将生命打至 30% 以下的伤害时，
     * 获得 100~300 临时最大生命 5 秒并同步治疗 100~400（按等级），期间 +10% 移速、
     * +25% 韧性；冷却 90 秒。数值来自 protoplasm_harness.json。
     */
    public static final RegistryObject<GearItem> PROTOPLASM_HARNESS =
            ITEMS.register("protoplasm_harness",
                    () -> new GearItem("protoplasm_harness", gearProperties("protoplasm_harness")));

    // ===================== 女神泪系列（法力流，8 件传说，4 对蜕变） =====================

    /**
     * 魔宗（Manamune）。槽位：手饰 hands。+35 攻击力 / +15 技能急速 / +500 最大法力。
     * 被动「敬畏（Awe）」：额外攻击力 = 2% 额外法力；「法力流」叠满 360 额外法力
     * 自动蜕变 → 魔切。数值来自 manamune.json。
     */
    public static final RegistryObject<GearItem> MANAMUNE =
            ITEMS.register("manamune",
                    () -> new GearItem("manamune", gearProperties("manamune")));

    /**
     * 魔切（Muramana）。魔宗的蜕变形态（独立物品，仅满层自动蜕变获得）。
     * +35 攻击力 / +15 技能急速 / +1000 最大法力。
     * 被动「敬畏」+「冲击（Shock）」：普攻命中附加 1.2% 最大法力的物理伤害；
     * 技能命中附加 4%（近战）/3%（远程）最大法力（同目标 6.5 秒一次）。
     */
    public static final RegistryObject<GearItem> MURAMANA =
            ITEMS.register("muramana",
                    () -> new GearItem("muramana", gearProperties("muramana")));

    /**
     * 大天使之杖（Archangel's Staff）。槽位：护符 charm。
     * +70 法术强度 / +25 技能急速 / +600 最大法力。
     * 被动「敬畏」：法术强度 = 1% 额外法力；「法力流」叠满 360 自动蜕变 → 炽天使之拥。
     */
    public static final RegistryObject<GearItem> ARCHANGELS_STAFF =
            ITEMS.register("archangels_staff",
                    () -> new GearItem("archangels_staff", gearProperties("archangels_staff")));

    /**
     * 炽天使之拥（Seraph's Embrace）。大天使之杖的蜕变形态。
     * +70 法术强度 / +25 技能急速 / +1000 最大法力。
     * 被动「敬畏」+「应急护盾（Lifeline）」：受到将使生命低于 30% 的伤害时，
     * 获得 18% 最大法力的护盾 3 秒（90 秒冷却）。
     */
    public static final RegistryObject<GearItem> SERAPHS_EMBRACE =
            ITEMS.register("seraphs_embrace",
                    () -> new GearItem("seraphs_embrace", gearProperties("seraphs_embrace")));

    /**
     * 凛冬之临（Winter's Approach）。槽位：胸饰 body。
     * +550 生命值 / +500 最大法力 / +15 技能急速。
     * 被动「敬畏」：额外生命 = 15% 额外法力；「法力流」叠满 360 自动蜕变 → 冬之誓。
     */
    public static final RegistryObject<GearItem> WINTERS_APPROACH =
            ITEMS.register("winters_approach",
                    () -> new GearItem("winters_approach", gearProperties("winters_approach")));

    /**
     * 冬之誓（Fimbulwinter）。凛冬之临的蜕变形态。
     * +550 生命值 / +1000 最大法力 / +15 技能急速。
     * 被动「敬畏」+「永恒（Everlasting）」：对敌人施加移动减速时，获得
     * 100 + 4.5% 最大法力的护盾 3 秒（8 秒冷却；附近多名敌人时提升 80%）。
     */
    public static final RegistryObject<GearItem> FIMBULWINTER =
            ITEMS.register("fimbulwinter",
                    () -> new GearItem("fimbulwinter", gearProperties("fimbulwinter")));

    /**
     * 耳语头环（Whispering Circlet）。槽位：戒指 ring。
     * +200 生命值 / +300 最大法力 / +75% 基础法力回复 / +8% 治疗与护盾强度。
     * 被动「和谐（Harmony）」：治疗与护盾强度 = 0.5% 额外法力；「法力流」叠满 360
     * 自动蜕变 → 歌之权冠。
     */
    public static final RegistryObject<GearItem> WHISPERING_CIRCLET =
            ITEMS.register("whispering_circlet",
                    () -> new GearItem("whispering_circlet", gearProperties("whispering_circlet")));

    /**
     * 歌之权冠（Diadem of Songs）。耳语头环的蜕变形态。
     * +200 生命值 / +1000 最大法力 / +100% 基础法力回复 / +8% 治疗与护盾强度。
     * 被动「和谐」+「共鸣（Consonance）」：每秒治疗范围内血量百分比最低的友方玩家
     * 0.8% 最大法力（附近存在可交战目标时）。
     */
    public static final RegistryObject<GearItem> DIADEM_OF_SONGS =
            ITEMS.register("diadem_of_songs",
                    () -> new GearItem("diadem_of_songs", gearProperties("diadem_of_songs")));

    // ===================== AD 物理系传说（守护天使 / 荒野箭 / 凡性 / 多米尼克） =====================

    /**
     * 守护天使（Guardian Angel）。槽位：胸饰 body。
     * +55 攻击力 / +45 护甲。
     * 唯一被动「重生」：受到致命伤害时像不死图腾一样直接免死一次（无凝滞），
     * 回复 50% 基础生命值并恢复 100% 最大法力；冷却 60 秒（用户指定）。
     */
    public static final RegistryObject<GearItem> GUARDIAN_ANGEL =
            ITEMS.register("guardian_angel",
                    () -> new GearItem("guardian_angel", gearProperties("guardian_angel")));

    /**
     * 育恩塔尔荒野箭（Yun Tal Wildarrows）。槽位：背饰 back。
     * +50% 弹射物伤害（50 攻击力按 10AD=10% 换算）/ +45% 蓄力速度（攻速→蓄力速度）。
     * 被动「熟能生巧」：弹射物命中永久叠加暴击率（近战口径 0.4%/层，至多 63 层 = 25%）；
     * 被动「疾风连射」：弹射物命中玩家时 +30% 蓄力速度 6 秒（30 秒冷却，弹射物命中减时）。
     */
    public static final RegistryObject<GearItem> YUN_TAL_WILDARROWS =
            ITEMS.register("yun_tal_wildarrows",
                    () -> new GearItem("yun_tal_wildarrows", gearProperties("yun_tal_wildarrows")));

    /**
     * 凡性的提醒（Mortal Reminder）。槽位：背饰 back。
     * +35 攻击力 / +30% 护甲穿透 / +25% 暴击率。
     * 唯一被动「重伤」：物理伤害（近战/弹射物/铁魔法法术——铁魔法实际吃护甲减免）
     * 命中敌人后施加 40% 重伤 3 秒。
     */
    public static final RegistryObject<GearItem> MORTAL_REMINDER =
            ITEMS.register("mortal_reminder",
                    () -> new GearItem("mortal_reminder", gearProperties("mortal_reminder")));

    /**
     * 多米尼克领主的致意（Lord Dominik's Regards）。槽位：背饰 back。
     * +35 攻击力 / +35% 护甲穿透 / +25% 暴击率。
     * 唯一被动「巨人杀手」：对敌方玩家造成的伤害提升 0%~15%
     * （基于目标额外生命值，15000 额外生命时封顶——用户指定）。
     */
    public static final RegistryObject<GearItem> LORD_DOMINIKS_REGARDS =
            ITEMS.register("lord_dominiks_regards",
                    () -> new GearItem("lord_dominiks_regards", gearProperties("lord_dominiks_regards")));

    /**
     * 鞋子（Boots）：英雄联盟中的基础移动速度装备。
     * 槽位：足部 feet；+25% 移动速度。数值来自配置文件 boots.json。
     */
    public static final RegistryObject<GearItem> BOOTS =
            ITEMS.register("boots", () -> {
                GearConfig config = GearConfigManager.get("boots");
                return new GearItem("boots",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 仙女护符（Faerie Charm）。槽位：护符 charm。
     * +50% 法力恢复（铁魔法 mana_regen）。数值来自 faerie_charm.json。
     */
    public static final RegistryObject<GearItem> FAERIE_CHARM =
            ITEMS.register("faerie_charm", () -> {
                GearConfig config = GearConfigManager.get("faerie_charm");
                return new GearItem("faerie_charm",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 治疗宝珠（Rejuvenation Bead）。槽位：手镯 bracelet。
     * +100% 自然生命恢复（本模组自建属性 natural_regen，事件层实现回血翻倍）。
     * 数值来自 rejuvenation_bead.json。
     */
    public static final RegistryObject<GearItem> REJUVENATION_BEAD =
            ITEMS.register("rejuvenation_bead", () -> {
                GearConfig config = GearConfigManager.get("rejuvenation_bead");
                return new GearItem("rejuvenation_bead",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 灵巧披风（Cloak of Agility）。槽位：背饰 back。
     * +15% 传说暴击率。数值来自 cloak_of_agility.json。
     */
    public static final RegistryObject<GearItem> CLOAK_OF_AGILITY =
            ITEMS.register("cloak_of_agility", () -> {
                GearConfig config = GearConfigManager.get("cloak_of_agility");
                return new GearItem("cloak_of_agility",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 爆裂魔杖（Blasting Wand）。槽位：手饰 hands。
     * +45% 通用法术强度（铁魔法 spell_power）。数值来自 blasting_wand.json。
     */
    public static final RegistryObject<GearItem> BLASTING_WAND =
            ITEMS.register("blasting_wand", () -> {
                GearConfig config = GearConfigManager.get("blasting_wand");
                return new GearItem("blasting_wand",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 蓝水晶（Sapphire Crystal）。槽位：护符 charm。
     * +300 最大法力值（铁魔法 max_mana）。数值来自 sapphire_crystal.json。
     */
    public static final RegistryObject<GearItem> SAPPHIRE_CRYSTAL =
            ITEMS.register("sapphire_crystal", () -> {
                GearConfig config = GearConfigManager.get("sapphire_crystal");
                return new GearItem("sapphire_crystal",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 红水晶（Ruby Crystal）。槽位：护符 charm。
     * +150 生命值（原版最大生命值）。数值来自 ruby_crystal.json。
     */
    public static final RegistryObject<GearItem> RUBY_CRYSTAL =
            ITEMS.register("ruby_crystal", () -> {
                GearConfig config = GearConfigManager.get("ruby_crystal");
                return new GearItem("ruby_crystal",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 布甲（Cloth Armor）。槽位：胸饰 body。
     * +15 护甲值。只走工作台合成。数值来自 cloth_armor.json。
     */
    public static final RegistryObject<GearItem> CLOTH_ARMOR =
            ITEMS.register("cloth_armor", () -> {
                GearConfig config = GearConfigManager.get("cloth_armor");
                return new GearItem("cloth_armor",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 抗魔斗篷（Null-Magic Mantle）。槽位：腰带 belt。
     * +20 传说魔法抗性（lolaccessories:magic_resist，LoL 法抗公式减伤）。
     * 数值来自 null_magic_mantle.json。
     * 注：该 1 级装备已按 LoL 官方名正名（旧注册名 negatron_cloak 让位给 2 级「负极斗篷
     * Negatron Cloak」，见下）。
     */
    public static final RegistryObject<GearItem> NULL_MAGIC_MANTLE =
            ITEMS.register("null_magic_mantle", () -> {
                GearConfig config = GearConfigManager.get("null_magic_mantle");
                return new GearItem("null_magic_mantle",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 长剑（Long Sword）。槽位：手饰 hands。
     * +10 攻击力。只走工作台合成。数值来自 long_sword.json。
     */
    public static final RegistryObject<GearItem> LONG_SWORD =
            ITEMS.register("long_sword", () -> {
                GearConfig config = GearConfigManager.get("long_sword");
                return new GearItem("long_sword",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 十字镐（Pickaxe）。槽位：背饰 back。
     * +25 攻击力。数值来自 pickaxe.json。
     */
    public static final RegistryObject<GearItem> PICKAXE =
            ITEMS.register("pickaxe", () -> {
                GearConfig config = GearConfigManager.get("pickaxe");
                return new GearItem("pickaxe",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 暴风之剑（B.F. Sword）。槽位：手饰 hands。
     * +40 攻击力。数值来自 bf_sword.json。
     */
    public static final RegistryObject<GearItem> BF_SWORD =
            ITEMS.register("bf_sword", () -> {
                GearConfig config = GearConfigManager.get("bf_sword");
                return new GearItem("bf_sword",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 短剑（Dagger）。槽位：护符 charm。
     * +10% 攻击速度（近战）与等额 +10% 蓄力速度（attributeslib:draw_speed）——英雄联盟攻速在
     * Minecraft 中等效为「近战攻速 + 远程武器蓄力速度」两项。蓄力速度依赖 Apothic Attributes
     * （自带原生效应），未安装时该加成行不生效。数值来自 dagger.json。
     */
    public static final RegistryObject<GearItem> DAGGER =
            ITEMS.register("dagger", () -> {
                GearConfig config = GearConfigManager.get("dagger");
                return new GearItem("dagger",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /**
     * 增幅典籍（Amplifying Tome）。槽位：护符 charm。
     * +20% 通用法术强度（铁魔法 spell_power）。数值来自 amplifying_tome.json。
     */
    public static final RegistryObject<GearItem> AMPLIFYING_TOME =
            ITEMS.register("amplifying_tome", () -> {
                GearConfig config = GearConfigManager.get("amplifying_tome");
                return new GearItem("amplifying_tome",
                        new Item.Properties().stacksTo(Math.max(1, config.max_stack_size)));
            });

    /** 多兰之盾（Doran's Shield）。槽位：手饰 hands。数值来自 doran_shield.json。 */
    public static final RegistryObject<GearItem> DORAN_SHIELD =
            ITEMS.register("doran_shield", () -> new GearItem("doran_shield", gearProperties("doran_shield")));

    /** 多兰之剑（Doran's Blade）。槽位：背饰 back。数值来自 doran_blade.json。 */
    public static final RegistryObject<GearItem> DORAN_BLADE =
            ITEMS.register("doran_blade", () -> new GearItem("doran_blade", gearProperties("doran_blade")));

    /** 多兰之戒（Doran's Ring）。槽位：戒指 ring。数值来自 doran_ring.json。 */
    public static final RegistryObject<GearItem> DORAN_RING =
            ITEMS.register("doran_ring", () -> new GearItem("doran_ring", gearProperties("doran_ring")));

    /** 无用大棒（Needlessly Large Rod）。槽位：护符 charm。数值来自 needlessly_large_rod.json。 */
    public static final RegistryObject<GearItem> NEEDLESSLY_LARGE_ROD =
            ITEMS.register("needlessly_large_rod",
                    () -> new GearItem("needlessly_large_rod", gearProperties("needlessly_large_rod")));

    /** 黑暗封印（Dark Seal）。槽位：戒指 ring。数值来自 dark_seal.json。 */
    public static final RegistryObject<GearItem> DARK_SEAL =
            ITEMS.register("dark_seal", () -> new GearItem("dark_seal", gearProperties("dark_seal")));

    /**
     * 梅贾的窃魂卷（Mejai's Soulstealer）。槽位：戒指 ring。与黑暗封印同属 seal_family
     * 系列互斥、共享荣耀层数池（超出黑暗封印上限的层数只在佩戴窃魂卷时生效）：
     * 击杀 +4 层 / 阵亡 -10 层 / 每层 +5% 通用法术强度，10 层以上 +10% 移速。
     * 数值来自 mejais_soulstealer.json。
     */
    public static final RegistryObject<GearItem> MEJAIS_SOULSTEALER =
            ITEMS.register("mejais_soulstealer",
                    () -> new GearItem("mejais_soulstealer", gearProperties("mejais_soulstealer")));

    /**
     * 幻影之舞（Phantom Dancer）。槽位：护符 charm。
     * +45% 攻击速度（近战）与等额 +45% 蓄力速度（涉及攻速必须成对，MC 远近战不共享攻速）。
     * 唯一被动「幽影华尔兹（Waltz）」：无视单位碰撞体积（Mixin isPushable）。
     * 数值来自 phantom_dancer.json。
     */
    public static final RegistryObject<GearItem> PHANTOM_DANCER =
            ITEMS.register("phantom_dancer",
                    () -> new GearItem("phantom_dancer", gearProperties("phantom_dancer")));

    /**
     * 基克的聚合（Zeke's Convergence）。槽位：护符 charm。
     * 唯一被动「聚合风暴（Zeal）」：攻击命中积累充能，满 8 次释放冰火风暴——周身敌人
     * 受魔法伤害并被大幅减速，内置冷却 10 秒。数值来自 zekes_convergence.json。
     */
    public static final RegistryObject<GearItem> ZEKES_CONVERGENCE =
            ITEMS.register("zekes_convergence",
                    () -> new GearItem("zekes_convergence", gearProperties("zekes_convergence")));

    /**
     * 斯特拉克的挑战护手（Sterak's Gage）。槽位：手饰 hands。
     * 唯一被动「救主灵刃（Lifeline）」：生命值将低于 30% 时触发——金色护盾 +
     * 8 秒 +50 攻击力怒火（MC 基础攻击只有 1 点，按用户要求改为固定攻击力），冷却 90 秒。
     * 数值来自 steraks_gage.json。
     */
    public static final RegistryObject<GearItem> STERAKS_GAGE =
            ITEMS.register("steraks_gage",
                    () -> new GearItem("steraks_gage", gearProperties("steraks_gage")));

    /**
     * 振奋盔甲（Spirit Visage）。槽位：头饰 head。
     * 唯一被动「无匹活力（Vigor）」：受到的治疗与护盾提升 25%——治疗由 Apothic
     * healing_received 属性原生结算，护盾由 ShieldHpService 读取同一属性乘区。
     * 数值来自 spirit_visage.json。
     */
    public static final RegistryObject<GearItem> SPIRIT_VISAGE =
            ITEMS.register("spirit_visage",
                    () -> new GearItem("spirit_visage", gearProperties("spirit_visage")));

    /**
     * 日炎圣盾（Sunfire Aegis）。槽位：胸饰 body。
     * 唯一被动「献祭（Sunfire）」：佩戴期间每秒点燃周围敌人（常驻版 immolate），
     * 自身带常驻贴体火焰特效。数值来自 sunfire_aegis.json。
     */
    public static final RegistryObject<GearItem> SUNFIRE_AEGIS =
            ITEMS.register("sunfire_aegis",
                    () -> new GearItem("sunfire_aegis", gearProperties("sunfire_aegis")));

    /**
     * 饮血剑（Bloodthirster）。槽位：手饰 hands。
     * +18% 生命偷取（attributeslib:life_steal）。唯一被动「余烬（Remnant）」：
     * 满血时的溢出治疗转化为血色护盾（上限配置），持续到期清空。
     * 数值来自 bloodthirster.json。
     */
    public static final RegistryObject<GearItem> BLOODTHIRSTER =
            ITEMS.register("bloodthirster",
                    () -> new GearItem("bloodthirster", gearProperties("bloodthirster")));

    /**
     * 海克斯注力刚壁（Experimental Hexplate）。槽位：手饰 hands。
     * +30% 终极技能冷却缩减（lolaccessories:ultimate_cdr，即 +30 终极技能急速）。
     * 唯一被动「超速驱动（Overdrive）」：施放终极技能后 8 秒内近战数值 +50% 攻速
     * （含等额蓄力速度）与 +20% 移速，内部冷却 30 秒。数值来自 experimental_hexplate.json。
     */
    public static final RegistryObject<GearItem> EXPERIMENTAL_HEXPLATE =
            ITEMS.register("experimental_hexplate",
                    () -> new GearItem("experimental_hexplate", gearProperties("experimental_hexplate")));

    /** 多兰之弓（Doran's Bow）。槽位：背饰 back。
     *  +8% 远程弹射伤害（引用 Apothic Attributes 的 arrow_damage 属性，未安装 Apothic 时该加成行不生效）、
     *  +1.5% 全能吸血（lolaccessories:omnivamp，对所有类型伤害回血）、
     *  +15% 攻击速度（近战）与等额 +15% 蓄力速度（attributeslib:draw_speed，同样依赖 Apothic）。
     *  数值来自 doran_bow.json。 */
    public static final RegistryObject<GearItem> DORAN_BOW =
            ITEMS.register("doran_bow", () -> new GearItem("doran_bow", gearProperties("doran_bow")));

    /** 多兰之盔（Doran's Helmet）。槽位：头饰 head。数值来自 doran_helmet.json。 */
    public static final RegistryObject<GearItem> DORAN_HELMET =
            ITEMS.register("doran_helmet", () -> new GearItem("doran_helmet", gearProperties("doran_helmet")));

    /** 荧尘（Glowing Mote）。槽位：护符 charm。数值来自 glowing_mote.json。 */
    public static final RegistryObject<GearItem> GLOWING_MOTE =
            ITEMS.register("glowing_mote", () -> new GearItem("glowing_mote", gearProperties("glowing_mote")));

    /** 女神之泪（Tear of the Goddess）。槽位：护符 charm。数值来自 tear_of_goddess.json。 */
    public static final RegistryObject<GearItem> TEAR_OF_GODDESS =
            ITEMS.register("tear_of_goddess", () -> new GearItem("tear_of_goddess", gearProperties("tear_of_goddess")));

    /**
     * 巨人腰带（Giant's Belt）。槽位：腰带 belt。
     * +350 最大生命值。红水晶在锻造台付费升级获得（见 giant_belt.json 付费锻造配方）。
     * 数值来自 giant_belt.json。
     */
    public static final RegistryObject<GearItem> GIANT_BELT =
            ITEMS.register("giant_belt", () -> new GearItem("giant_belt", gearProperties("giant_belt")));

    /**
     * 锁子甲（Chain Vest）。槽位：胸饰 body。
     * +40 护甲值。布甲在锻造台付费升级获得（见 chain_vest.json 付费锻造配方）。
     * 数值来自 chain_vest.json。
     */
    public static final RegistryObject<GearItem> CHAIN_VEST =
            ITEMS.register("chain_vest", () -> new GearItem("chain_vest", gearProperties("chain_vest")));

    /**
     * 反曲之弓（Recurve Bow）。槽位：背饰 back。
     * +25% 攻击速度（近战）与等额 +25% 蓄力速度（attributeslib:draw_speed，依赖 Apothic）。
     * 短剑在锻造台付费升级获得（见 recurve_bow.json 付费锻造配方）。数值来自 recurve_bow.json。
     */
    public static final RegistryObject<GearItem> RECURVE_BOW =
            ITEMS.register("recurve_bow", () -> new GearItem("recurve_bow", gearProperties("recurve_bow")));

    /**
     * 吸血鬼节杖（Vampiric Scepter）。槽位：手饰 hands。
     * +15 攻击力 / +7% 生命偷取（attributeslib:life_steal，数值对齐英雄联盟现版本官方；依赖 Apothic，
     * 仅近战直击由 Apothic 原生事件层结算，符合英雄联盟本体「生命偷取只对普通攻击生效」口径）。
     * 长剑在锻造台付费升级获得（见 vampiric_scepter.json 付费锻造配方）。
     * 数值来自 vampiric_scepter.json。
     */
    public static final RegistryObject<GearItem> VAMPIRIC_SCEPTER =
            ITEMS.register("vampiric_scepter", () -> new GearItem("vampiric_scepter", gearProperties("vampiric_scepter")));

    /**
     * 【测试用】拉弓速度测试器（Draw Speed Test）。槽位：腰带 belt。
     * 蓄力速度 +100%（attributeslib:draw_speed 默认 1.0，加成后倍率 = 2.0），用于在装有
     * Apothic Attributes 的环境里直观验证远程武器蓄力速度是否生效：佩戴后弓只需蓄力约 10 tick
     * （半秒）即可达到满力。属性自带原生效应（弓/弩/三叉戟每 tick 消耗双倍蓄力进度），无需本模组
     * 事件层。仅调试用途，后续版本会移除。数值来自 draw_speed_test.json。
     */
    public static final RegistryObject<GearItem> DRAW_SPEED_TEST =
            ITEMS.register("draw_speed_test",
                    () -> new GearItem("draw_speed_test", gearProperties("draw_speed_test")));

    /**
     * 【测试用】固定法穿测试器（Flat Magic Penetration Tester）。槽位：腰带 belt。
     * +100 法术穿透（lolaccessories:magic_pen，固定点数）。佩戴后对铁魔法法术目标结算时
     * 无视等额「原版护甲 + 传说魔法抗性」，用于拿冰霜蜘蛛等自带护甲的怪验证穿透是否生效。
     * 仅调试用途，后续版本会移除。数值来自 flat_pen_test.json。
     */
    public static final RegistryObject<GearItem> FLAT_PEN_TEST =
            ITEMS.register("flat_pen_test",
                    () -> new GearItem("flat_pen_test", gearProperties("flat_pen_test")));

    /**
     * 【测试用】百分比法穿测试器（Percent Magic Penetration Tester）。槽位：腰带 belt。
     * +90% 法术穿透（lolaccessories:magic_pen_percent，0~1 百分比语义）。佩戴后对铁魔法
     * 法术目标结算时按 90% 无视「原版护甲、护甲韧性、传说魔法抗性与铁魔法学派魔抗」，
     * 用于拿冰霜蜘蛛等怪验证百分比穿透是否生效。仅调试用途，后续版本会移除。
     * 数值来自 pct_pen_test.json。
     */
    public static final RegistryObject<GearItem> PCT_PEN_TEST =
            ITEMS.register("pct_pen_test",
                    () -> new GearItem("pct_pen_test", gearProperties("pct_pen_test")));

    /**
     * 负极斗篷（Negatron Cloak）。槽位：腰带 belt。
     * +45 传说魔法抗性（lolaccessories:magic_resist）。抗魔斗篷（Null-Magic Mantle）在
     * 锻造台付费升级获得（见 negatron_cloak.json 付费锻造配方）。数值来自 negatron_cloak.json。
     */
    public static final RegistryObject<GearItem> NEGATRON_CLOAK =
            ITEMS.register("negatron_cloak", () -> new GearItem("negatron_cloak", gearProperties("negatron_cloak")));

    /**
     * 钢铁印章（Steel Sigil）。槽位：护符 charm。
     * +15 攻击力 / +30 护甲。两个布甲 + 一把长剑在锻造台付费合成（见 steel_sigil.json）。
     * 数值来自 steel_sigil.json。
     */
    public static final RegistryObject<GearItem> STEEL_SIGIL =
            ITEMS.register("steel_sigil", () -> new GearItem("steel_sigil", gearProperties("steel_sigil")));

    /**
     * 残暴之力（The Brutalizer）。槽位：手饰 hands。
     * +25 攻击力 / +10% 冷却缩减（引用铁魔法 irons_spellbooks:cooldown_reduction，即此前约定
     * 的「技能急速」语义） / +5 护甲穿透（引用 Apothic attributeslib:armor_pierce，点数穿甲）。
     * 十字镐 + 荧尘在锻造台付费合成（见 brutalizer.json）。数值来自 brutalizer.json。
     */
    public static final RegistryObject<GearItem> BRUTALIZER =
            ITEMS.register("brutalizer", () -> new GearItem("brutalizer", gearProperties("brutalizer")));

    /**
     * 掘道钻头（Tunneler）。槽位：护符 charm。
     * +15 攻击力 / +250 最大生命值。红水晶 + 长剑在锻造台付费合成（见 tunneler.json）。
     * 数值来自 tunneler.json。
     */
    public static final RegistryObject<GearItem> TUNNELER =
            ITEMS.register("tunneler", () -> new GearItem("tunneler", gearProperties("tunneler")));

    /**
     * 探索者的护臂（Seeker's Armguard）。槽位：手镯 bracelet。
     * +40% 法术强度（铁魔法 spell_power）/ +25 护甲。
     * 主动技能「时间停止（凝滞）」：由按键触发（按键默认不绑定，在按键设置的
     * 「LOLAccessories」分类中自行分配），冷却 300 秒（享受冷却缩减，计算见
     * {@code TimeStopSkill}），激活后凝滞 2.5 秒（无敌并禁止移动/攻击/交互，可开背包）。
     * 一个布甲 + 两个增幅典籍在锻造台付费合成（见 seekers_armguard.json）。
     * 数值与冷却/时长等来自 seekers_armguard.json 的 on_hit_effects[time_stop]。
     */
    public static final RegistryObject<GearItem> SEEKERS_ARMGUARD =
            ITEMS.register("seekers_armguard", () -> new GearItem("seekers_armguard", gearProperties("seekers_armguard")));

    /** 以太精魂（Aether Wisp）。槽位：护符 charm。+30% 通用法术强度 / +4% 移动速度。数值来自 aether_wisp.json。 */
    public static final RegistryObject<GearItem> AETHER_WISP =
            ITEMS.register("aether_wisp", () -> new GearItem("aether_wisp", gearProperties("aether_wisp")));

    /** 斑比的熔渣（Bami's Cinder）。槽位：腰带 belt。+150 最大生命 / +10% 冷却缩减；被动「引燃（Immolate）」：受伤或造成伤害时灼烧周围敌对生物 3 秒。数值来自 bamis_cinder.json。 */
    public static final RegistryObject<GearItem> BAMIS_CINDER =
            ITEMS.register("bamis_cinder", () -> new GearItem("bamis_cinder", gearProperties("bamis_cinder")));

    /** 班德尔玻璃镜（Bandleglass Mirror）。槽位：护符 charm。+20% 通用法术强度 / +100% 法力恢复 / +10% 冷却缩减。数值来自 bandleglass_mirror.json。 */
    public static final RegistryObject<GearItem> BANDLEGLASS_MIRROR =
            ITEMS.register("bandleglass_mirror", () -> new GearItem("bandleglass_mirror", gearProperties("bandleglass_mirror")));

    /** 枯萎珠宝（Blighting Jewel）。槽位：护符 charm。+25% 通用法术强度 / +13% 传说魔法穿透。数值来自 blighting_jewel.json。 */
    public static final RegistryObject<GearItem> BLIGHTING_JEWEL =
            ITEMS.register("blighting_jewel", () -> new GearItem("blighting_jewel", gearProperties("blighting_jewel")));

    /** 棘刺背心（Bramble Vest）。槽位：胸饰 body。+30 护甲；被动「尖刺（Thorns）」：被近战/弹射物攻击命中时反弹伤害并施加 40% 重伤。数值来自 bramble_vest.json。 */
    public static final RegistryObject<GearItem> BRAMBLE_VEST =
            ITEMS.register("bramble_vest", () -> new GearItem("bramble_vest", gearProperties("bramble_vest")));

    /** 万世催化石（Catalyst of Aeons）。槽位：腰带 belt。+300 最大生命 / +375 最大法力；被动「永恒（Eternity）」：受伤回复法力、施法消耗回复生命。数值来自 catalyst_of_aeons.json。 */
    public static final RegistryObject<GearItem> CATALYST_OF_AEONS =
            ITEMS.register("catalyst_of_aeons", () -> new GearItem("catalyst_of_aeons", gearProperties("catalyst_of_aeons")));

    /** 考尔菲德的战锤（Caulfield's Warhammer）。槽位：手饰 hands。+20 攻击力 / +10% 冷却缩减。数值来自 caulfields_warhammer.json。 */
    public static final RegistryObject<GearItem> CAULFIELDS_WARHAMMER =
            ITEMS.register("caulfields_warhammer", () -> new GearItem("caulfields_warhammer", gearProperties("caulfields_warhammer")));

    /** 晶体护腕（Crystalline Bracer）。槽位：手镯 bracelet。+200 最大生命 / +100% 自然生命恢复。数值来自 crystalline_bracer.json。 */
    public static final RegistryObject<GearItem> CRYSTALLINE_BRACER =
            ITEMS.register("crystalline_bracer", () -> new GearItem("crystalline_bracer", gearProperties("crystalline_bracer")));

    /** 死刑宣告（Executioner's Calling）。槽位：手饰 hands。+15 攻击力；被动「死刑宣告（Grievous Wounds）」：物理伤害命中施加 40% 重伤 3 秒。数值来自 executioners_calling.json。 */
    public static final RegistryObject<GearItem> EXECUTIONERS_CALLING =
            ITEMS.register("executioners_calling", () -> new GearItem("executioners_calling", gearProperties("executioners_calling")));

    /** 命定灰烬（Fated Ashes）。槽位：护符 charm。+30% 通用法术强度；被动「引燃（Inflame）」：造成魔法伤害的目标被灼烧 3 秒。数值来自 fated_ashes.json。 */
    public static final RegistryObject<GearItem> FATED_ASHES =
            ITEMS.register("fated_ashes", () -> new GearItem("fated_ashes", gearProperties("fated_ashes")));

    /** 恶魔法典（Fiendish Codex）。槽位：护符 charm。+25% 通用法术强度 / +10% 冷却缩减。数值来自 fiendish_codex.json。 */
    public static final RegistryObject<GearItem> FIENDISH_CODEX =
            ITEMS.register("fiendish_codex", () -> new GearItem("fiendish_codex", gearProperties("fiendish_codex")));

    /** 禁忌雕像（Forbidden Idol）。槽位：护符 charm。+50% 法力恢复 / +8% 治疗与护盾强度（本模组自建 heal_power 属性）。数值来自 forbidden_idol.json。 */
    public static final RegistryObject<GearItem> FORBIDDEN_IDOL =
            ITEMS.register("forbidden_idol", () -> new GearItem("forbidden_idol", gearProperties("forbidden_idol")));

    /** 冰川圆盾（Glacial Buckler）。槽位：腰带 belt。+25 护甲 / +300 最大法力 / +10% 冷却缩减。数值来自 glacial_buckler.json。 */
    public static final RegistryObject<GearItem> GLACIAL_BUCKLER =
            ITEMS.register("glacial_buckler", () -> new GearItem("glacial_buckler", gearProperties("glacial_buckler")));

    /** 幽魂面具（Haunting Guise）。槽位：腰带 belt。+30% 通用法术强度 / +200 最大生命；被动「疯狂（Madness）」：与敌方战斗每秒增伤至多 6%。数值来自 haunting_guise.json。 */
    public static final RegistryObject<GearItem> HAUNTING_GUISE =
            ITEMS.register("haunting_guise", () -> new GearItem("haunting_guise", gearProperties("haunting_guise")));

    /** 缚炉之斧（Hearthbound Axe）。槽位：背饰 back。+20 攻击力 / +20% 攻击速度。数值来自 hearthbound_axe.json。 */
    public static final RegistryObject<GearItem> HEARTHBOUND_AXE =
            ITEMS.register("hearthbound_axe", () -> new GearItem("hearthbound_axe", gearProperties("hearthbound_axe")));

    /** 海克斯饮魔刀（Hexdrinker）。槽位：手饰 hands。+25 攻击力 / +25 传说魔法抗性；被动「生命残片（Lifeline）」：受到魔法伤害使生命低于 30% 时获得魔法伤害护盾。数值来自 hexdrinker.json。 */
    public static final RegistryObject<GearItem> HEXDRINKER =
            ITEMS.register("hexdrinker", () -> new GearItem("hexdrinker", gearProperties("hexdrinker")));

    /** 海克斯科技发电机（Hextech Alternator）。槽位：护符 charm。+45% 通用法术强度；被动「充能（Revved）」：周期性附加额外魔法伤害。数值来自 hextech_alternator.json。 */
    public static final RegistryObject<GearItem> HEXTECH_ALTERNATOR =
            ITEMS.register("hextech_alternator", () -> new GearItem("hextech_alternator", gearProperties("hextech_alternator")));

    /** 燃烧宝石（Kindlegem）。槽位：腰带 belt。+200 最大生命 / +10% 冷却缩减。数值来自 kindlegem.json。 */
    public static final RegistryObject<GearItem> KINDLEGEM =
            ITEMS.register("kindlegem", () -> new GearItem("kindlegem", gearProperties("kindlegem")));

    /** 最后的轻语（Last Whisper）。槽位：背饰 back。+20 攻击力 / +18% 护甲穿透（attributeslib:armor_shred 百分比）。数值来自 last_whisper.json。 */
    public static final RegistryObject<GearItem> LAST_WHISPER =
            ITEMS.register("last_whisper", () -> new GearItem("last_whisper", gearProperties("last_whisper")));

    /** 遗失的章节（Lost Chapter）。槽位：护符 charm。+40% 通用法术强度 / +300 最大法力 / +10% 冷却缩减；被动「启迪（Enlighten）」：升级回复 20% 最大法力。数值来自 lost_chapter.json。 */
    public static final RegistryObject<GearItem> LOST_CHAPTER =
            ITEMS.register("lost_chapter", () -> new GearItem("lost_chapter", gearProperties("lost_chapter")));

    /** 正午箭袋（Noonquiver）。槽位：背饰 back。+15 攻击力 / +20% 传说暴击率。数值来自 noonquiver.json。 */
    public static final RegistryObject<GearItem> NOONQUIVER =
            ITEMS.register("noonquiver", () -> new GearItem("noonquiver", gearProperties("noonquiver")));

    /** 湮灭宝珠（Oblivion Orb）。槽位：护符 charm。+25% 通用法术强度；被动「湮灭（Grievous Wounds）」：魔法伤害命中施加 40% 重伤 3 秒。数值来自 oblivion_orb.json。 */
    public static final RegistryObject<GearItem> OBLIVION_ORB =
            ITEMS.register("oblivion_orb", () -> new GearItem("oblivion_orb", gearProperties("oblivion_orb")));

    /** 净蚀（Phage）。槽位：手饰 hands。+15 攻击力 / +200 最大生命；被动「狂怒（Rage）」：攻击获得 2 秒加速。数值来自 phage.json。 */
    public static final RegistryObject<GearItem> PHAGE =
            ITEMS.register("phage", () -> new GearItem("phage", gearProperties("phage")));

    /** 水银饰带（Quicksilver Sash）。槽位：腰带 belt。+30 传说魔法抗性；主动「净化（Quicksilver）」：解除自身所有控制类减益。数值来自 quicksilver_sash.json。 */
    public static final RegistryObject<GearItem> QUICKSILVER_SASH =
            ITEMS.register("quicksilver_sash", () -> new GearItem("quicksilver_sash", gearProperties("quicksilver_sash")));

    /** 剑翎（Rectrix）。槽位：手饰 hands。+15 攻击力 / +4% 移动速度。数值来自 rectrix.json。 */
    public static final RegistryObject<GearItem> RECTRIX =
            ITEMS.register("rectrix", () -> new GearItem("rectrix", gearProperties("rectrix")));

    /** 斥候弹弓（Scout's Slingshot）。槽位：背饰 back。+20% 攻击速度；被动「牛眼（Bullseye）」：周期性附加额外魔法伤害，攻击加快触发。数值来自 scouts_slingshot.json。 */
    public static final RegistryObject<GearItem> SCOUTS_SLINGSHOT =
            ITEMS.register("scouts_slingshot", () -> new GearItem("scouts_slingshot", gearProperties("scouts_slingshot")));

    /** 锯齿短匕（Serrated Dirk）。槽位：手饰 hands。+20 攻击力 / +10 护甲穿透（attributeslib:armor_pierce 固定穿甲）。数值来自 serrated_dirk.json。 */
    public static final RegistryObject<GearItem> SERRATED_DIRK =
            ITEMS.register("serrated_dirk", () -> new GearItem("serrated_dirk", gearProperties("serrated_dirk")));

    /** 耀光（Sheen）。槽位：手饰 hands。+10% 冷却缩减；被动「咒刃（Spellblade）」：施法后下一次攻击附加额外物理伤害。数值来自 sheen.json。 */
    public static final RegistryObject<GearItem> SHEEN =
            ITEMS.register("sheen", () -> new GearItem("sheen", gearProperties("sheen")));

    /** 幽魂斗篷（Spectre's Cowl）。槽位：头饰 head。+200 最大生命 / +35 传说魔法抗性 / +100% 自然生命恢复。数值来自 spectres_cowl.json。 */
    public static final RegistryObject<GearItem> SPECTRES_COWL =
            ITEMS.register("spectres_cowl", () -> new GearItem("spectres_cowl", gearProperties("spectres_cowl")));

    /** 提亚马特（Tiamat）。槽位：手饰 hands。+25 攻击力；被动「顺劈/新月（Cleave/Crescent）」：攻击对附近敌人造成物理伤害、主动对周围敌人造成物理伤害。数值来自 tiamat.json。 */
    public static final RegistryObject<GearItem> TIAMAT =
            ITEMS.register("tiamat", () -> new GearItem("tiamat", gearProperties("tiamat")));

    /** 翠绿屏障（Verdant Barrier）。槽位：手镯 bracelet。+40% 通用法术强度 / +25 传说魔法抗性；被动「法盾（Annul）」：挡下一次敌方法术。数值来自 verdant_barrier.json。 */
    public static final RegistryObject<GearItem> VERDANT_BARRIER =
            ITEMS.register("verdant_barrier", () -> new GearItem("verdant_barrier", gearProperties("verdant_barrier")));

    /** 守望者铠甲（Warden's Mail）。槽位：胸饰 body。+40 护甲；被动「磐石（Rock Solid）」：每次被攻击命中减免伤害（最高减免 20% 且不超过 15 点）。数值来自 wardens_mail.json。 */
    public static final RegistryObject<GearItem> WARDENS_MAIL =
            ITEMS.register("wardens_mail", () -> new GearItem("wardens_mail", gearProperties("wardens_mail")));

    /** 带翼的月板甲（Winged Moonplate）。槽位：足部 feet。+200 最大生命 / +4% 移动速度。数值来自 winged_moonplate.json。 */
    public static final RegistryObject<GearItem> WINGED_MOONPLATE =
            ITEMS.register("winged_moonplate", () -> new GearItem("winged_moonplate", gearProperties("winged_moonplate")));

    /** 狂热（Zeal）。槽位：背饰 back。+15% 攻击速度 / +15% 传说暴击率 / +4% 移动速度。数值来自 zeal.json。 */
    public static final RegistryObject<GearItem> ZEAL =
            ITEMS.register("zeal", () -> new GearItem("zeal", gearProperties("zeal")));

    /**
     * 金币（Gold Coin）。通用货币，非饰品，堆叠上限 64（原版默认值）。
     * 击杀生物按目标血量分档掉落；可用 1 个金块无序合成。
     */
    public static final RegistryObject<Item> GOLD_COIN =
            ITEMS.register("gold_coin", () -> new GoldCoinItem(
                    new Item.Properties().stacksTo(64)));

    /** 依据同名配置的堆叠上限构造物品属性。 */
    private static Item.Properties gearProperties(String gearId) {
        GearConfig config = GearConfigManager.get(gearId);
        return new Item.Properties().stacksTo(Math.max(1, config.max_stack_size));
    }

    private ModItems() {
    }
}
