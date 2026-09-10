package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.init.ModItems;
import com.example.lolaccessories.init.ModMobEffects;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

/**
 * 语言文件生成。所有游戏内可见文本都在这里维护，不要手写 en_us.json / zh_cn.json。
 */
public class ModLanguageProvider extends LanguageProvider {

    private final String locale;
    private final boolean chinese;

    public ModLanguageProvider(PackOutput output, String locale) {
        super(output, LOLAccessories.MOD_ID, locale);
        this.locale = locale;
        this.chinese = "zh_cn".equals(locale);
    }

    @Override
    protected void addTranslations() {

        add("itemGroup." + LOLAccessories.MOD_ID, chinese ? "LOL 饰品" : "LOL Accessories");

        // ---------- 成就（原版进度系统） ----------
        // 行结构：[进度id, 中文标题, 英文标题, 中文描述, 英文描述]
        // 标题颜色/加粗在 ModAdvancementProvider 生成的 JSON 里控制，这里只维护文案。
        String[][] achievements = {
                {"root", "LOL 饰品", "LOLAccessories",
                        "把符文之地的装备传说，一条条写进你的冒险。",
                        "Write Runeterra's gear legends, one by one, into your own adventure."},
                {"heroes_journey", "英雄启程", "The Hero's Journey",
                        "获得第一件普通装备。", "Obtain your first Common accessory."},
                {"hello_world", "你好世界", "Hello, World",
                        "集齐全部普通装备。", "Collect every Common accessory."},
                {"epic_ballad", "史诗传唱", "Epic Ballad",
                        "获得第一件史诗装备。", "Obtain your first Epic accessory."},
                {"so_called_heroes", "所谓英雄", "So-Called Heroes",
                        "集齐全部史诗装备。", "Collect every Epic accessory."},
                {"legend_journey", "传说征程", "The Legend's Journey",
                        "获得第一件传说装备。", "Obtain your first Legendary accessory."},
                {"endless_night", "漫漫长夜", "The Endless Night",
                        "集齐全部传说装备。", "Collect every Legendary accessory."},
                {"myth_writing", "书写神话", "Writing Myth",
                        "获得第一件神话装备。", "Obtain your first Mythic accessory."},
                {"undefeated_brave", "不败的勇者", "The Undefeated Brave",
                        "集齐全部神话装备。", "Collect every Mythic accessory."},
                {"heaven_fall", "天堂陨落", "Heaven's Fall",
                        "完成挑战：单次弹射物伤害高于 1,000,000 点，澄空之愿将回应你的愿望。",
                        "Challenge: deal a single projectile hit of over 1,000,000 damage, "
                                + "and Clear Sky's Wish will answer your wish."},
                {"unimaginable_horror", "接下来的故事，将是超出想象的恐怖",
                        "The Story That Follows Is Beyond Imagination",
                        "完成挑战：最大生命值达到 100,000，且收集过 50 种传说装备，魔王之心将苏醒。",
                        "Challenge: reach 100,000 max health and collect 50 legendary "
                                + "accessories—the Demon Heart will awaken."},
                {"champions", "我们是冠军！", "We Are the Champions!",
                        "收集 LOLAccessories 的全部装备。", "Collect every LOLAccessories accessory."},
                {"first_gold", "发育不能落下", "Never Fall Behind",
                        "拾取你的第一枚金币。", "Pick up your first gold coin."},
                {"first_craft", "老板失业了", "Out of Business",
                        "亲手合成或锻造出第一件装备。", "Craft or smith your first accessory."},
                {"hero_power", "英雄之力", "Power of a Hero",
                        "第一次成功释放主动技能。", "Unleash an active skill for the first time."},
                {"wearing_5", "渐入佳境", "Getting There",
                        "同时佩戴 5 件 LOLAccessories 装备。", "Wear 5 LOLAccessories accessories at once."},
                {"wearing_10", "不可思议", "Unbelievable",
                        "同时佩戴 10 件。", "Wear 10 at once."},
                {"wearing_20", "教练！", "Coach!",
                        "同时佩戴 20 件。", "Wear 20 at once."},
                {"wearing_30", "没关就是开了？", "Cheats On?",
                        "同时佩戴 30 件。", "Wear 30 at once."},
                {"wearing_50", "向你致敬", "A Salute to You",
                        "同时佩戴 50 件。", "Wear 50 at once."},
                {"wearing_100", "终结一切的力量", "Power to End It All",
                        "同时佩戴 100 件。", "Wear 100 at once."},
        };
        for (String[] adv : achievements) {
            String prefix = "advancements." + LOLAccessories.MOD_ID + "." + adv[0] + ".";
            add(prefix + "title", chinese ? adv[1] : adv[2]);
            add(prefix + "description", chinese ? adv[3] : adv[4]);
        }

        // ---------- 物品 ----------
        addItem(ModItems.BLACK_CLEAVER, chinese ? "黑色切割者" : "The Black Cleaver");
        addItem(ModItems.LUDENS_ECHO, chinese ? "卢登的回声" : "Luden's Echo");
        addItem(ModItems.INFINITY_EDGE, chinese ? "无尽之刃" : "Infinity Edge");
        // 第六批 5 件 3 级（传说）装备（中文名采用官方译名风格，经 ddragon zh_CN 核验）
        addItem(ModItems.SHURELYAS_BATTLESONG, chinese ? "舒瑞娅的战歌" : "Shurelya's Battlesong");
        addItem(ModItems.OVERLORDS_BLOODMAIL, chinese ? "霸王血铠" : "Overlord's Bloodmail");
        addItem(ModItems.UNENDING_DESPAIR, chinese ? "无终恨意" : "Unending Despair");
        addItem(ModItems.BLACKFIRE_TORCH, chinese ? "黯炎火炬" : "Blackfire Torch");
        addItem(ModItems.KAENIC_ROOKERN, chinese ? "败魔" : "Kaenic Rookern");
        // 第七批 8 件 3 级（传说）装备（2026 海克斯赛季第三批）
        addItem(ModItems.BASTIONBREAKER, chinese ? "破垒者" : "Bastionbreaker");
        addItem(ModItems.ENDLESS_HUNGER, chinese ? "无穷饥渴" : "Endless Hunger");
        addItem(ModItems.HEXOPTICS_C44, chinese ? "海克斯镜片 C44" : "Hexoptics C44");
        addItem(ModItems.BANDLEPIPES, chinese ? "班德尔音管" : "Bandlepipes");
        addItem(ModItems.PROTOPLASM_HARNESS, chinese ? "原生质护带" : "Protoplasm Harness");
        addItem(ModItems.CLEAR_SKYS_WISH, chinese ? "澄空之愿" : "Clear Sky's Wish");
        addItem(ModItems.DEMON_HEART, chinese ? "魔王之心" : "Demon Heart");
        addItem(ModItems.MANAMUNE, chinese ? "魔宗" : "Manamune");
        addItem(ModItems.MURAMANA, chinese ? "魔切" : "Muramana");
        addItem(ModItems.ARCHANGELS_STAFF, chinese ? "大天使之杖" : "Archangel's Staff");
        addItem(ModItems.SERAPHS_EMBRACE, chinese ? "炽天使之拥" : "Seraph's Embrace");
        addItem(ModItems.WINTERS_APPROACH, chinese ? "凛冬之临" : "Winter's Approach");
        addItem(ModItems.FIMBULWINTER, chinese ? "冬之誓" : "Fimbulwinter");
        addItem(ModItems.WHISPERING_CIRCLET, chinese ? "耳语头环" : "Whispering Circlet");
        addItem(ModItems.DIADEM_OF_SONGS, chinese ? "歌之权冠" : "Diadem of Songs");
        addItem(ModItems.GUARDIAN_ANGEL, chinese ? "守护天使" : "Guardian Angel");
        addItem(ModItems.YUN_TAL_WILDARROWS, chinese ? "育恩塔尔荒野箭" : "Yun Tal Wildarrows");
        addItem(ModItems.MORTAL_REMINDER, chinese ? "凡性的提醒" : "Mortal Reminder");
        addItem(ModItems.LORD_DOMINIKS_REGARDS, chinese ? "多米尼克领主的致意" : "Lord Dominik's Regards");
        addItem(ModItems.ACTUALIZER, chinese ? "实现器" : "Actualizer");
        addItem(ModItems.DUSK_AND_DAWN, chinese ? "黄昏黎明" : "Dusk and Dawn");
        addItem(ModItems.FIENDHUNTER_BOLTS, chinese ? "猎魔人弩箭" : "Fiendhunter Bolts");
        addItem(ModItems.BOOTS, chinese ? "鞋子" : "Boots");
        addItem(ModItems.FAERIE_CHARM, chinese ? "仙女护符" : "Faerie Charm");
        addItem(ModItems.REJUVENATION_BEAD, chinese ? "治疗宝珠" : "Rejuvenation Bead");
        addItem(ModItems.CLOAK_OF_AGILITY, chinese ? "灵巧披风" : "Cloak of Agility");
        addItem(ModItems.BLASTING_WAND, chinese ? "爆裂魔杖" : "Blasting Wand");
        addItem(ModItems.SAPPHIRE_CRYSTAL, chinese ? "蓝水晶" : "Sapphire Crystal");
        addItem(ModItems.RUBY_CRYSTAL, chinese ? "红水晶" : "Ruby Crystal");
        addItem(ModItems.CLOTH_ARMOR, chinese ? "布甲" : "Cloth Armor");
        addItem(ModItems.NULL_MAGIC_MANTLE, chinese ? "抗魔斗篷" : "Null-Magic Mantle");
        addItem(ModItems.LONG_SWORD, chinese ? "长剑" : "Long Sword");
        addItem(ModItems.PICKAXE, chinese ? "十字镐" : "Pickaxe");
        addItem(ModItems.BF_SWORD, chinese ? "暴风之剑" : "B.F. Sword");
        addItem(ModItems.DAGGER, chinese ? "短剑" : "Dagger");
        addItem(ModItems.AMPLIFYING_TOME, chinese ? "增幅典籍" : "Amplifying Tome");
        // 第二批普通装备（多兰系出门装与经典散件）
        addItem(ModItems.DORAN_SHIELD, chinese ? "多兰之盾" : "Doran's Shield");
        addItem(ModItems.DORAN_BLADE, chinese ? "多兰之剑" : "Doran's Blade");
        addItem(ModItems.DORAN_RING, chinese ? "多兰之戒" : "Doran's Ring");
        addItem(ModItems.NEEDLESSLY_LARGE_ROD, chinese ? "无用大棒" : "Needlessly Large Rod");
        addItem(ModItems.DARK_SEAL, chinese ? "黑暗封印" : "Dark Seal");
        addItem(ModItems.MEJAIS_SOULSTEALER, chinese ? "梅贾的窃魂卷" : "Mejai's Soulstealer");
        addItem(ModItems.PHANTOM_DANCER, chinese ? "幻影之舞" : "Phantom Dancer");
        addItem(ModItems.ZEKES_CONVERGENCE, chinese ? "基克的聚合" : "Zeke's Convergence");
        addItem(ModItems.STERAKS_GAGE, chinese ? "斯特拉克的挑战护手" : "Sterak's Gage");
        addItem(ModItems.SPIRIT_VISAGE, chinese ? "振奋盔甲" : "Spirit Visage");
        addItem(ModItems.SUNFIRE_AEGIS, chinese ? "日炎圣盾" : "Sunfire Aegis");
        addItem(ModItems.BLOODTHIRSTER, chinese ? "饮血剑" : "Bloodthirster");
        addItem(ModItems.EXPERIMENTAL_HEXPLATE, chinese ? "海克斯注力刚壁" : "Experimental Hexplate");
        addItem(ModItems.DORAN_BOW, chinese ? "多兰之弓" : "Doran's Bow");
        addItem(ModItems.DORAN_HELMET, chinese ? "多兰之盔" : "Doran's Helmet");
        addItem(ModItems.GLOWING_MOTE, chinese ? "荧尘" : "Glowing Mote");
        addItem(ModItems.TEAR_OF_GODDESS, chinese ? "女神之泪" : "Tear of the Goddess");
        // 第三批（升级散件：锻造台用金币付费升级获得）
        addItem(ModItems.GIANT_BELT, chinese ? "巨人腰带" : "Giant's Belt");
        addItem(ModItems.CHAIN_VEST, chinese ? "锁子甲" : "Chain Vest");
        addItem(ModItems.RECURVE_BOW, chinese ? "反曲之弓" : "Recurve Bow");
        addItem(ModItems.VAMPIRIC_SCEPTER, chinese ? "吸血鬼节杖" : "Vampiric Scepter");
        // 第四批 2 级装备（新赛季组件与法系护臂，锻造台付费合成）
        addItem(ModItems.NEGATRON_CLOAK, chinese ? "负极斗篷" : "Negatron Cloak");
        addItem(ModItems.STEEL_SIGIL, chinese ? "钢铁印章" : "Steel Sigil");
        addItem(ModItems.BRUTALIZER, chinese ? "残暴之力" : "The Brutalizer");
        addItem(ModItems.TUNNELER, chinese ? "掘道钻头" : "Tunneler");
        addItem(ModItems.SEEKERS_ARMGUARD, chinese ? "探索者的护臂" : "Seeker's Armguard");
        // 第五批 34 件 2 级（史诗）装备（中文名采用官方译名，经 ddragon zh_CN 核验）
        addItem(ModItems.AETHER_WISP, chinese ? "以太精魂" : "Aether Wisp");
        addItem(ModItems.BAMIS_CINDER, chinese ? "斑比的熔渣" : "Bami's Cinder");
        addItem(ModItems.BANDLEGLASS_MIRROR, chinese ? "班德尔玻璃镜" : "Bandleglass Mirror");
        addItem(ModItems.BLIGHTING_JEWEL, chinese ? "枯萎珠宝" : "Blighting Jewel");
        addItem(ModItems.BRAMBLE_VEST, chinese ? "棘刺背心" : "Bramble Vest");
        addItem(ModItems.CATALYST_OF_AEONS, chinese ? "万世催化石" : "Catalyst of Aeons");
        addItem(ModItems.CAULFIELDS_WARHAMMER, chinese ? "考尔菲德的战锤" : "Caulfield's Warhammer");
        addItem(ModItems.CRYSTALLINE_BRACER, chinese ? "晶体护腕" : "Crystalline Bracer");
        addItem(ModItems.EXECUTIONERS_CALLING, chinese ? "死刑宣告" : "Executioner's Calling");
        addItem(ModItems.FATED_ASHES, chinese ? "命定灰烬" : "Fated Ashes");
        addItem(ModItems.FIENDISH_CODEX, chinese ? "恶魔法典" : "Fiendish Codex");
        addItem(ModItems.FORBIDDEN_IDOL, chinese ? "禁忌雕像" : "Forbidden Idol");
        addItem(ModItems.GLACIAL_BUCKLER, chinese ? "冰川圆盾" : "Glacial Buckler");
        addItem(ModItems.HAUNTING_GUISE, chinese ? "幽魂面具" : "Haunting Guise");
        addItem(ModItems.HEARTHBOUND_AXE, chinese ? "缚炉之斧" : "Hearthbound Axe");
        addItem(ModItems.HEXDRINKER, chinese ? "海克斯饮魔刀" : "Hexdrinker");
        addItem(ModItems.HEXTECH_ALTERNATOR, chinese ? "海克斯科技发电机" : "Hextech Alternator");
        addItem(ModItems.KINDLEGEM, chinese ? "燃烧宝石" : "Kindlegem");
        addItem(ModItems.LAST_WHISPER, chinese ? "最后的轻语" : "Last Whisper");
        addItem(ModItems.LOST_CHAPTER, chinese ? "遗失的章节" : "Lost Chapter");
        addItem(ModItems.NOONQUIVER, chinese ? "正午箭袋" : "Noonquiver");
        addItem(ModItems.OBLIVION_ORB, chinese ? "湮灭宝珠" : "Oblivion Orb");
        addItem(ModItems.PHAGE, chinese ? "净蚀" : "Phage");
        addItem(ModItems.QUICKSILVER_SASH, chinese ? "水银饰带" : "Quicksilver Sash");
        addItem(ModItems.RECTRIX, chinese ? "剑翎" : "Rectrix");
        addItem(ModItems.SCOUTS_SLINGSHOT, chinese ? "斥候弹弓" : "Scout's Slingshot");
        addItem(ModItems.SERRATED_DIRK, chinese ? "锯齿短匕" : "Serrated Dirk");
        addItem(ModItems.SHEEN, chinese ? "耀光" : "Sheen");
        addItem(ModItems.SPECTRES_COWL, chinese ? "幽魂斗篷" : "Spectre's Cowl");
        addItem(ModItems.TIAMAT, chinese ? "提亚马特" : "Tiamat");
        addItem(ModItems.VERDANT_BARRIER, chinese ? "翠绿屏障" : "Verdant Barrier");
        addItem(ModItems.WARDENS_MAIL, chinese ? "守望者铠甲" : "Warden's Mail");
        addItem(ModItems.WINGED_MOONPLATE, chinese ? "带翼的月板甲" : "Winged Moonplate");
        addItem(ModItems.ZEAL, chinese ? "狂热" : "Zeal");
        // 测试饰品（调试，后续移除）
        addItem(ModItems.DRAW_SPEED_TEST, chinese ? "拉弓速度测试器" : "Draw Speed Tester");
        addItem(ModItems.FLAT_PEN_TEST, chinese ? "固定法穿测试器" : "Flat Penetration Tester");
        addItem(ModItems.PCT_PEN_TEST, chinese ? "百分比法穿测试器" : "Percent Penetration Tester");
        // 金币
        addItem(ModItems.GOLD_COIN, chinese ? "金币" : "Gold Coin");

        // ---------- 装备风味词条 ----------
        // 描述策略：与官方 League of Legends 保持一致——仅当官方为该装备提供风味文本
        // （flavor text，见 League of Legends Wiki 各装备页）时才收录；官方没有描述的
        // 装备（黑色切割者/无终绝望/舒瑞娅的战歌及全部基础装备等）不自行添加。
        lore("infinity_edge",
                "只有最大胆无畏之人，才敢踏上前往瓦洛兰最炽热之地——恕瑞玛沙漠正中央的凶险旅程。成功抵达者可将剑刃浸入一方魔池，那是世间最纯净的水。凡浸过此池的锋刃，都将比世上任何利剑更锐利、更精准。",
                "Only the boldest of men and women dare take the risky trip to the hottest spot in Valoran, in the very center of the Shurima Desert. Those who successfully make the journey may bathe their swords in a magical pool of the purest water known to this world. Any edge dipped into this pool will stay sharper and strike truer than any other sword.");
        lore("kaenic_rookern",
                "命运由智谋、汗水与钢铁铸就。魔法，再也无法凌驾于这一原则之上。",
                "Destiny is shaped through cunning, sweat, and steel. Magic violates this principle no longer.");
        lore("overlords_bloodmail",
                "唯有在生与死之间，他才找到了清算宿怨的方式。",
                "Only between life and death did he find a way to settle the score.");
        lore("ludens_echo",
                "“法师最好的伙伴！今日即可入手！”——皮尔特沃夫商人（推定已故）",
                "\"A mage's best friend! Get yours today!\" - Piltover merchant, presumed deceased");
        lore("blackfire_torch",
                "黑雾需要一簇音调相合的火焰。",
                "The Black Mist needs a flame of equal pitch.");
        lore("tear_of_goddess",
                "有幸邂逅创世女神之泪的人寥寥无几……但凡有幸取得女神之泪的人，都能享受到法力值与法力回复的显著提升。",
                "Few are fortunate enough to come across the tears of the very goddess who created our world... but those who are lucky enough to acquire a Tear of the Goddess may take pleasure in a significant boost of mana points and mana points regeneration.");


        // ---------- 药水效果 ----------
        addEffect(ModMobEffects.CLEAVER_SHRED, chinese ? "切割" : "Cleave");
        addEffect(ModMobEffects.CLEAVER_RUSH, chinese ? "热烈" : "Rush");
        addEffect(ModMobEffects.PERSEVERANCE, chinese ? "耐久专注" : "Perseverance");
        addEffect(ModMobEffects.WOUNDS, chinese ? "重伤" : "Grievous Wounds");
        addEffect(ModMobEffects.RAGE, chinese ? "狂暴" : "Rage");
        addEffect(ModMobEffects.INSPIRING_SPEECH, chinese ? "鼓舞" : "Inspiring Speech");
        addEffect(ModMobEffects.FANFARE, chinese ? "嘹亮旋律" : "Fanfare");

        // ---------- 本模组 LOL 独立属性译名 ----------
        // 传说暴击率 / 传说暴击伤害：属性资源名（lolaccessories:crit_chance / crit_damage）保持不变；
        // 显示名与常见中文汉化（如“暴击率/暴击伤害”）错开，避免与其它模组或汉化包冲突而无法在
        // 属性显示类模组中区分。此类模组按 attribute.getDescriptionId() 查找词条，中文客户端即显示中文。
        add("attribute." + LOLAccessories.MOD_ID + ".crit_chance",
                chinese ? "传说暴击率" : "Legendary Crit Chance");
        add("attribute." + LOLAccessories.MOD_ID + ".crit_damage",
                chinese ? "传说暴击伤害" : "Legendary Crit Damage");
        add("attribute." + LOLAccessories.MOD_ID + ".magic_resist",
                chinese ? "传说魔法抗性" : "Legendary Magic Resist");
        add("attribute." + LOLAccessories.MOD_ID + ".magic_pen",
                chinese ? "传说法术穿透" : "Legendary Magic Penetration");
        add("attribute." + LOLAccessories.MOD_ID + ".magic_pen_percent",
                chinese ? "传说百分比法穿" : "Legendary Percent Penetration");
        add("attribute." + LOLAccessories.MOD_ID + ".natural_regen",
                chinese ? "自然生命恢复" : "Natural Health Regeneration");
        add("attribute." + LOLAccessories.MOD_ID + ".heal_power",
                chinese ? "治疗与护盾强度" : "Heal & Shield Power");
        add("attribute." + LOLAccessories.MOD_ID + ".tenacity",
                chinese ? "传说韧性" : "Legendary Tenacity");
        add("attribute." + LOLAccessories.MOD_ID + ".omnivamp",
                chinese ? "全能吸血" : "Omnivamp");
        add("attribute." + LOLAccessories.MOD_ID + ".ultimate_cdr",
                chinese ? "终极技能冷却缩减" : "Ultimate Cooldown Reduction");
        add("attribute." + LOLAccessories.MOD_ID + ".physical_damage_reduction",
                chinese ? "物理伤害百分比减免" : "Physical Damage Reduction");
        add("attribute." + LOLAccessories.MOD_ID + ".magic_damage_reduction",
                chinese ? "魔法伤害百分比减免" : "Magic Damage Reduction");

        // ---------- 引用的外部属性译名（兜底） ----------
        // 注：蓄力速度不再使用本模组自建属性，装备配置直接引用 Apothic Attributes 的
        // attributeslib:draw_speed（含原生效应与译名「蓄力速度」），本模组无需再提供译名词条。
        // 生命偷取同样以 Apothic 的 attributeslib:life_steal 为准；以下键在 Apothic 自身或其
        // 汉化未提供同键词条时兜底，保证属性面板/tooltip 不会出现裸键。
        add("attribute.attributeslib.life_steal",
                chinese ? "生命偷取" : "Life Steal");
        add("attribute.apothic_attributes.life_steal",
                chinese ? "生命偷取" : "Life Steal");

        // ---------- 物品被动提示（LoL 式：金色「唯一被动—名称」标题 + 灰色描述） ----------
        // 装备栏位行不在此维护：Curios 自带“可装备槽位”的 tooltip，本模组无需重复显示。
        add("passive.lolaccessories.cleaver_shred.title",
                chinese ? "唯一被动—切割" : "Unique Passive - Cleave");
        add("passive.lolaccessories.cleaver_shred.desc",
                chinese ? "对敌人造成伤害会撕裂其护甲，使其护甲降低 %1$s，持续 %2$s 秒；"
                                + "该削减至多叠加到 %3$s。"
                        : "Damaging an enemy tears into its armor, reducing it by %1$s for %2$s "
                                + "seconds, stacking up to %3$s.");
        add("passive.lolaccessories.cleaver_rush.title",
                chinese ? "唯一被动—狂怒" : "Unique Passive - Rage");
        add("passive.lolaccessories.cleaver_rush.desc",
                chinese ? "对敌人造成伤害后，获得 %1$s 的移动速度，持续 %2$s 秒。"
                        : "After damaging an enemy, gain %1$s bonus movement speed for %2$s seconds.");
        add("passive.lolaccessories.echo.title",
                chinese ? "唯一被动—回声" : "Unique Passive - Echo");
        add("passive.lolaccessories.echo.desc",
                chinese ? "技能命中敌人时触发回声：向主目标与 %6$s 格内至多 %2$s 名敌人各发射一道回声弹，每道"
                                + "造成 %3$s + %4$s×末影法术强度 的魔法伤害；附近敌人不足时，多余的弹体会"
                                + "折返主目标，每道造成 %5$s 伤害。末影法术强度每有 100%%，便额外发射 1 道。"
                                + "冷却时间：%1$s 秒。"
                        : "Hitting an enemy with a spell triggers an Echo: bolts fly at the main target "
                                + "and up to %2$s enemies within %6$s blocks, each dealing %3$s + %4$s × "
                                + "Ender spell power magic damage. When nearby enemies are scarce, surplus "
                                + "bolts instead strike the main target for %5$s damage each. Every 100%% "
                                + "of Ender spell power fires 1 extra bolt. Cooldown: %1$s s.");
        add("passive.lolaccessories.helping_hand.title",
                chinese ? "唯一被动—帮助之手" : "Unique Passive - Helping Hand");
        add("passive.lolaccessories.helping_hand.desc",
                chinese ? "对生命值低于 %1$s 的敌人造成伤害时，额外造成 %2$s 点物理伤害。"
                        : "Dealing damage to enemies below %1$s health deals %2$s bonus physical damage.");
        add("passive.lolaccessories.perseverance.title",
                chinese ? "唯一被动—耐久专注" : "Unique Passive - Perseverance");
        add("passive.lolaccessories.perseverance.desc",
                chinese ? "受到伤害后的 %1$s 秒内，你的自然生命恢复提升至两倍。"
                        : "For %1$s seconds after taking damage, your natural health regeneration is doubled.");
        add("passive.lolaccessories.mana_restore.title",
                chinese ? "唯一被动—回复力" : "Unique Passive - Mana Restore");
        add("passive.lolaccessories.mana_restore.desc",
                chinese ? "每秒回复 %1$s 点法力值；造成魔法伤害后的 %2$s 秒内，回复效果翻倍。"
                        : "Restores %1$s mana per second. After dealing magic damage, this is doubled "
                                + "for %2$s seconds.");
        add("passive.lolaccessories.mana_flow.title",
                chinese ? "唯一被动—法力流" : "Unique Passive - Mana Flow");
        add("passive.lolaccessories.mana_flow.desc",
                chinese ? "每次魔法命中都会永久提升 %1$s 点最大法力值，至多 +%2$s；"
                                + "仅在佩戴该装备时累积并生效。"
                        : "Each magic hit permanently grants +%1$s max mana, up to +%2$s. Stacks are "
                                + "only gained and applied while equipped.");
        add("passive.lolaccessories.mana_flow.progress",
                chinese ? "当前加成：+%s 最大法力（上限 +%s）"
                        : "Current: +%s max mana (cap +%s)");
        add("passive.lolaccessories.glory.title",
                chinese ? "唯一被动—荣耀" : "Unique Passive - Glory");
        add("passive.lolaccessories.glory.desc",
                chinese ? "击杀最大生命值远超你的生物时，获得 %1$s 层荣耀（至多 %4$s 层）；"
                                + "阵亡时损失 %2$s 层。每层提供 +%3$s 法术强度，仅在装备时生效。"
                        : "Killing creatures with far more max health than you grants %1$s Glory stacks "
                                + "(up to %4$s). Dying while equipped loses %2$s stacks. Each stack grants "
                                + "+%3$s spell power while equipped.");
        add("passive.lolaccessories.glory.progress",
                chinese ? "当前层数：%s / %s" : "Current stacks: %s / %s");

        // ---------- 第五批 34 件 2 级（史诗）装备新增被动的 tooltip 词条 ----------
        // 数值参数与 LolNewEpicPassiveEvents 结算口径一致，由 GearItem 按 effect.id 分支渲染。
        add("passive.lolaccessories.immolate.title",
                chinese ? "唯一被动—灼烧" : "Unique Passive - Immolate");
        add("passive.lolaccessories.immolate.desc",
                chinese ? "造成或受到伤害时，点燃周围 %2$s 格内的敌人，使其每秒受到 %1$s 点魔法伤害，"
                                + "持续 %3$s 秒。"
                        : "When you deal or take damage, ignite enemies within %2$s blocks, burning "
                                + "them for %1$s magic damage per second for %3$s seconds.");
        add("passive.lolaccessories.inflame.title",
                chinese ? "唯一被动—引燃" : "Unique Passive - Inflame");
        add("passive.lolaccessories.inflame.desc",
                chinese ? "魔法伤害命中敌人时将其点燃，使其每秒受到 %1$s 点魔法伤害，持续 %2$s 秒。"
                        : "Magic damage ignites the target, dealing %1$s magic damage per second for %2$s seconds.");
        add("passive.lolaccessories.thorns.title",
                chinese ? "唯一被动—尖刺" : "Unique Passive - Thorns");
        add("passive.lolaccessories.thorns.desc",
                chinese ? "受到普通攻击命中时，向攻击者反弹 %1$s 点荆棘伤害，并使其受到的治疗降低 %2$s，持续 %3$s 秒。"
                        : "When hit by a basic attack, reflect %1$s thorns damage to the attacker and reduce its incoming healing by %2$s for %3$s seconds.");
        add("passive.lolaccessories.eternity.title",
                chinese ? "唯一被动—永恒" : "Unique Passive - Eternity");
        add("passive.lolaccessories.eternity.desc",
                chinese ? "受到伤害时，回复所受伤害 %1$s 的法力值；以魔法伤害命中敌人时，回复 %2$s 点生命值。"
                        : "Restore %1$s of the damage taken as mana, and heal for %2$s health when your magic damage hits an enemy.");
        add("passive.lolaccessories.madness.title",
                chinese ? "唯一被动—疯狂" : "Unique Passive - Madness");
        add("passive.lolaccessories.madness.desc",
                chinese ? "对敌方造成伤害会叠加疯狂（至多 %2$s 层），每层使你造成的伤害提高 %3$s；%1$s 秒内未继续命中则层数消失。"
                        : "Dealing damage to enemies builds Madness (max %2$s stacks), granting %3$s increased damage per stack. Stacks expire after %1$s seconds without a hit.");
        add("passive.lolaccessories.revved.title",
                chinese ? "唯一被动—充能" : "Unique Passive - Revved");
        add("passive.lolaccessories.revved.desc",
                chinese ? "命中敌人时释放积蓄的充能，额外造成 %1$s 点魔法伤害；"
                                + "每 %2$s 秒至多触发一次。"
                        : "Hitting an enemy releases stored charge, dealing %1$s bonus magic damage. "
                                + "Triggers at most once every %2$s seconds.");
        add("passive.lolaccessories.bullseye.title",
                chinese ? "唯一被动—牛眼" : "Unique Passive - Bullseye");
        add("passive.lolaccessories.bullseye.desc",
                chinese ? "精准命中敌人的要害，额外造成 %1$s 点魔法伤害；"
                                + "每 %2$s 秒至多触发一次。"
                        : "A well-aimed hit finds the enemy's weak point, dealing %1$s bonus magic "
                                + "damage. Triggers at most once every %2$s seconds.");
        add("passive.lolaccessories.rage.title",
                chinese ? "唯一被动—狂怒" : "Unique Passive - Rage");
        add("passive.lolaccessories.rage.desc",
                chinese ? "普通攻击命中敌方后，获得 %1$s 移动速度，持续 %2$s 秒。"
                        : "After attacking an enemy, gain %1$s bonus movement speed for %2$s seconds.");
        add("passive.lolaccessories.spellblade.title",
                chinese ? "唯一被动—咒刃" : "Unique Passive - Spellblade");
        add("passive.lolaccessories.spellblade.desc",
                chinese ? "技能命中敌人后，你的下一次普通攻击附带相当于 %1$s 攻击力的额外物理伤害。"
                        : "After a spell hits an enemy, your next basic attack deals bonus physical "
                                + "damage equal to %1$s of your attack damage.");
        add("passive.lolaccessories.cleave.title",
                chinese ? "唯一被动—顺劈" : "Unique Passive - Cleave");
        add("passive.lolaccessories.cleave.desc",
                chinese ? "普通攻击命中时会产生顺劈，对目标 %1$s 格内的其他敌人造成相当于你 %2$s 攻击力的物理伤害。"
                        : "Basic attacks cleave, dealing %2$s of your attack damage as physical damage "
                                + "to other enemies within %1$s blocks of the target.");
        add("passive.lolaccessories.annul.title",
                chinese ? "唯一被动—法盾" : "Unique Passive - Annul");
        add("passive.lolaccessories.annul.desc",
                chinese ? "每 %1$s 秒至多抵挡一次敌人的魔法伤害，并将其完全抵消。"
                        : "Once every %1$s seconds, fully negate one instance of an enemy's magic "
                                + "damage.");
        add("passive.lolaccessories.rock_solid.title",
                chinese ? "唯一被动—磐石" : "Unique Passive - Rock Solid");
        add("passive.lolaccessories.rock_solid.desc",
                chinese ? "受到攻击时减免最终伤害：最多减免 %1$s 点，且不超过本次伤害的 %2$s。"
                        : "Reduce final damage taken from attacks by up to %1$s, capped at %2$s of each hit.");
        add("passive.lolaccessories.lifeline.title",
                chinese ? "唯一被动—生命残片" : "Unique Passive - Lifeline");
        add("passive.lolaccessories.lifeline.desc",
                chinese ? "受到魔法伤害而生命值即将跌破 %1$s 时，获得一层持续 %2$s 秒的护盾，吸收"
                                + " %3$s 点伤害。冷却时间：%4$s 秒。"
                        : "When magic damage would drop your health below %1$s, gain a shield that "
                                + "lasts %2$s seconds and absorbs %3$s damage. Cooldown: %4$s seconds.");
        add("passive.lolaccessories.enlighten.title",
                chinese ? "唯一被动—启迪" : "Unique Passive - Enlighten");
        add("passive.lolaccessories.enlighten.desc",
                chinese ? "升级时回复 %1$s 最大法力值，在 %2$s 秒内持续恢复。"
                        : "On leveling up, restore %1$s of your max mana, distributed over %2$s seconds.");
        add("passive.lolaccessories.grievous_wounds_phys.title",
                chinese ? "唯一被动—重伤" : "Unique Passive - Grievous Wounds");
        add("passive.lolaccessories.grievous_wounds_phys.desc",
                chinese ? "物理伤害命中敌人后，使其受到的治疗降低 %1$s，持续 %2$s 秒。"
                        : "Physical damage applies Grievous Wounds, reducing the target's incoming healing by %1$s for %2$s seconds.");
        add("passive.lolaccessories.grievous_wounds_magic.title",
                chinese ? "唯一被动—重伤" : "Unique Passive - Grievous Wounds");
        add("passive.lolaccessories.grievous_wounds_magic.desc",
                chinese ? "魔法伤害命中敌人后，使其受到的治疗降低 %1$s，持续 %2$s 秒。"
                        : "Magic damage applies Grievous Wounds, reducing the target's incoming healing by %1$s for %2$s seconds.");

        // ---------- 5 件新传说（3 级）装备被动 tooltip 词条 ----------
        add("passive.lolaccessories.tyranny.title",
                chinese ? "唯一被动—暴政" : "Unique Passive - Tyranny");
        add("passive.lolaccessories.tyranny.desc",
                chinese ? "你获得相当于你额外生命值 %1$s 的攻击伤害加成。"
                        : "Gain bonus attack damage equal to %1$s of your bonus health.");
        add("passive.lolaccessories.retribution.title",
                chinese ? "唯一被动—报应" : "Unique Passive - Retribution");
        add("passive.lolaccessories.retribution.desc",
                chinese ? "你的生命值越低，获得的攻击伤害加成越高，至多为当前攻击伤害的 %1$s"
                                + "（不含本被动自身的加成）。"
                        : "The lower your health, the more attack damage you gain—up to %1$s of your "
                                + "current attack damage (excluding this passive's own bonus).");
        add("passive.lolaccessories.anguish.title",
                chinese ? "唯一被动—苦楚" : "Unique Passive - Anguish");
        add("passive.lolaccessories.anguish.desc",
                chinese ? "战斗中每 %2$s 秒，对 %3$s 格内的敌人造成相当于你额外生命值 %1$s 的魔法伤害，"
                                + "并为你回复该伤害 %4$s 的生命值。"
                        : "While in combat, every %2$s seconds deal magic damage equal to %1$s of "
                                + "your maximum health to enemies within %3$s blocks, healing you for "
                                + "%4$s of the damage dealt.");
        add("passive.lolaccessories.baleful_blaze.title",
                chinese ? "唯一被动—不祥灼烧" : "Unique Passive - Baleful Blaze");
        add("passive.lolaccessories.baleful_blaze.desc",
                chinese ? "你的魔法伤害会灼烧目标，持续 %2$s 秒：每秒两跳，每跳造成 %1$s + %3$s×法术强度"
                                + " 的魔法伤害；每名正在灼烧的敌人使你造成的魔法伤害提高 %4$s。"
                        : "Your magic damage burns the target for %2$s seconds: two ticks per second, "
                                + "each dealing %1$s + %3$s × spell power magic damage. Each burning "
                                + "enemy increases your magic damage by %4$s.");
        add("passive.lolaccessories.magebane.title",
                chinese ? "唯一被动—法师之祸" : "Unique Passive - Magebane");
        add("passive.lolaccessories.magebane.desc",
                chinese ? "连续 %1$s 秒未受到魔法伤害时，获得相当于你最大生命值 %2$s 的护盾；一旦受到"
                                + "魔法伤害，护盾便会被击碎并重新计时。"
                        : "After %1$s seconds without taking magic damage, gain a shield equal to %2$s "
                                + "of your maximum health. Taking magic damage shatters the shield and "
                                + "restarts the timer.");

        // ---------- 34 件史诗装备的被动动作条提示（服务端触发回执） ----------
        add("passive.lolaccessories.annul_blocked",
                chinese ? "法盾生效！已格挡一次来自敌方的魔法伤害。"
                        : "Annul! Blocked an incoming enemy spell.");
        add("passive.lolaccessories.lifeline_proc",
                chinese ? "生命残片！护盾展开。" : "Lifeline shield engaged!");
        add("passive.lolaccessories.spellblade_proc",
                chinese ? "咒刃附伤！" : "Spellblade empowered!");
        add("passive.lolaccessories.proc_revved",
                chinese ? "充能释放！" : "Revved!");
        add("passive.lolaccessories.proc_bullseye",
                chinese ? "牛眼命中！" : "Bullseye!");
        add("passive.lolaccessories.enlighten_proc",
                chinese ? "启迪！法力正在恢复。" : "Enlightened! Mana is being restored.");

        // ---------- 铁魔法属性译名（兜底） ----------
        // 仅当铁魔法已安装且属性解析成功时，以下键才会出现在装备 tooltip 中；
        // 若铁魔法本身/其汉化包已提供相同键，会以加载优先级更高的一方为准，此处不影响其原义。
        add("attribute." + IronsCompat.ENDER_SPELL_POWER.replace(':', '.'),
                chinese ? "末影法术强度" : "Ender Spell Power");
        add("attribute." + IronsCompat.MAX_MANA.replace(':', '.'),
                chinese ? "最大法力值" : "Max Mana");
        add("attribute." + IronsCompat.COOLDOWN_REDUCTION.replace(':', '.'),
                chinese ? "冷却缩减" : "Cooldown Reduction");
        add("attribute." + IronsCompat.SPELL_POWER.replace(':', '.'),
                chinese ? "法术强度" : "Spell Power");
        add("attribute." + IronsCompat.MANA_REGEN.replace(':', '.'),
                chinese ? "法力恢复" : "Mana Regen");

        // ---------- 命令 ----------
        add("commands.lolaccessories.reload.success",
                chinese ? "已重新加载装备配置文件。" : "Reloaded gear configs.");

        // ---------- 付费锻造 ----------
        // 取件费用不足提示（1=需要数量, 2=物品名, 3=现有数量）
        add("message.lolaccessories.insufficient_cost",
                chinese ? "锻造费用不足：需要 %1$s 个「%2$s」，你现有 %3$s 个（背包与末影箱）。"
                        : "Not enough to smith: need %1$s of %2$s, but you only have %3$s "
                                + "(inventory & ender chest).");
        // 神话挑战解锁发放提示（1=装备名）
        add("message.lolaccessories.mythic_unlocked",
                chinese ? "§6✦ 挑战达成！%1$s§6 已发放至你的背包。"
                        : "§6✦ Challenge complete! %1$s§6 has been delivered to your inventory.");
        // 锻造台 / JEI 输出槽展示费用时的标题行（下方为逐项费用图标行）
        add("tooltip.lolaccessories.paid_smithing.title",
                chinese ? "锻造费用（取件时自动从背包与末影箱扣除）"
                        : "Smithing cost (auto-deducted from inventory & ender chest on pickup)");

        // ---------- 主动技能：时间停止（探索者的护臂） ----------
        // tooltip 条目（金色标题 + 灰色描述，由 GearItem 的 time_stop 分支渲染）
        add("active.lolaccessories.time_stop.title",
                chinese ? "唯一主动—时间停止（凝滞）" : "Unique Active - Time Stop");
        add("active.lolaccessories.time_stop.desc",
                chinese ? "进入凝滞状态 %1$s 秒：免疫所有伤害且无法被选中，期间无法移动、攻击或与外界"
                                + "交互，但可以打开背包。冷却时间：%2$s 秒，受冷却缩减影响。"
                        : "Enter stasis for %1$s seconds, becoming immune to all damage and "
                                + "untargetable. While in stasis you cannot move, attack, or interact "
                                + "with the world—though your inventory still opens. Cooldown: %2$s s, "
                                + "reduced by cooldown reduction.");
        // 按键设置的分类与按键名（按键默认不绑定）
        add("key.categories." + LOLAccessories.MOD_ID,
                chinese ? "LOL 饰品" : "LOL Accessories");
        add("key." + LOLAccessories.MOD_ID + ".time_stop",
                chinese ? "探索者的护臂：时间停止" : "Seeker's Armguard: Time Stop");
        // 触发反馈（动作条/聊天，服务端展示给客户端）
        add("skill.lolaccessories.time_stop.start",
                chinese ? "凝滞！%1$s 秒内免疫伤害且无法行动。" : "Stasis! Immune and frozen for %1$s seconds.");
        add("skill.lolaccessories.time_stop.cooldown",
                chinese ? "时间停止冷却中：剩余 %1$s 秒。" : "Time Stop on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.time_stop.need_item",
                chinese ? "需要佩戴「%1$s」才能使用时间停止。"
                        : "You must be wearing %1$s to use Time Stop.");

        // ---------- 主动技能：净化（水银饰带）/ 新月（提亚马特） ----------
        // tooltip 条目（金色标题 + 灰色描述，由 GearItem 渲染）
        add("active.lolaccessories.quicksilver.title",
                chinese ? "唯一主动—净化" : "Unique Active - Quicksilver");
        add("active.lolaccessories.quicksilver.desc",
                chinese ? "立即净化自身：移除身上所有的控制与减益效果，并扑灭身上的火焰。"
                                + "冷却时间：%1$s 秒，受冷却缩减影响。"
                        : "Instantly cleanse yourself, removing all crowd control and debuffs, and "
                                + "extinguish any fire on you. Cooldown: %1$s s, reduced by cooldown "
                                + "reduction.");
        add("active.lolaccessories.crescent.title",
                chinese ? "唯一主动—新月" : "Unique Active - Crescent");
        add("active.lolaccessories.crescent.desc",
                chinese ? "挥出横扫一击，对 %2$s 格内的所有敌人造成 %1$s 点物理伤害。"
                                + "冷却时间：%3$s 秒，受冷却缩减影响。"
                        : "Sweep outward, dealing %1$s physical damage to all enemies within "
                                + "%2$s blocks. Cooldown: %3$s s, reduced by cooldown reduction.");
        // 按键设置的按键名（默认不绑定）
        add("key." + LOLAccessories.MOD_ID + ".quicksilver",
                chinese ? "水银饰带：净化" : "Quicksilver Sash: Quicksilver");
        add("key." + LOLAccessories.MOD_ID + ".crescent",
                chinese ? "提亚马特：新月" : "Tiamat: Crescent");
        // 触发反馈（动作条，服务端展示给客户端；start 的 %1$s = 净化解除数/新月命中数）
        add("skill.lolaccessories.quicksilver.start",
                chinese ? "净化！已解除 %1$s 个有害状态。" : "Quicksilver! Cleansed %1$s harmful effects.");
        add("skill.lolaccessories.quicksilver.cooldown",
                chinese ? "净化冷却中：剩余 %1$s 秒。" : "Quicksilver on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.quicksilver.need_item",
                chinese ? "需要佩戴「%1$s」才能使用净化。"
                        : "You must be wearing %1$s to use Quicksilver.");
        add("skill.lolaccessories.crescent.start",
                chinese ? "新月！%1$s 个敌对生物受到物理伤害。" : "Crescent! %1$s hostiles struck.");
        add("skill.lolaccessories.crescent.cooldown",
                chinese ? "新月冷却中：剩余 %1$s 秒。" : "Crescent on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.crescent.need_item",
                chinese ? "需要佩戴「%1$s」才能使用新月。"
                        : "You must be wearing %1$s to use Crescent.");

        // ---------- 主动技能：鼓舞（舒瑞娅的战歌） ----------
        add("active.lolaccessories.inspiring_speech.title",
                chinese ? "唯一主动—鼓舞" : "Unique Active - Inspiring Speech");
        add("active.lolaccessories.inspiring_speech.desc",
                chinese ? "你与 %3$s 格内的友方玩家移动速度提升 %1$s，持续 %2$s 秒。"
                                + "冷却时间：%4$s 秒，受冷却缩减影响。"
                        : "You and friendly players within %3$s blocks gain %1$s movement speed for "
                                + "%2$s seconds. Cooldown: %4$s s, reduced by cooldown reduction.");
        add("key." + LOLAccessories.MOD_ID + ".inspiring_speech",
                chinese ? "舒瑞娅的战歌：鼓舞" : "Shurelya's Battlesong: Inspiring Speech");
        add("skill.lolaccessories.inspiring_speech.start",
                chinese ? "鼓舞！%1$s 名玩家获得了移动速度加成。"
                        : "Inspiring! %1$s players gained bonus movement speed.");
        add("skill.lolaccessories.inspiring_speech.cooldown",
                chinese ? "鼓舞冷却中：剩余 %1$s 秒。" : "Inspiring Speech on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.inspiring_speech.need_item",
                chinese ? "需要佩戴「%1$s」才能使用鼓舞。"
                        : "You must be wearing %1$s to use Inspiring Speech.");

        // ---------- 2026 赛季第三批 8 件传说装备的 tooltip / 反馈词条 ----------
        // 黄昏黎明：咒刃（铁魔法联动）
        add("passive.lolaccessories.dusk_spellblade.title",
                chinese ? "唯一被动—咒刃" : "Unique Passive - Spellblade");
        add("passive.lolaccessories.dusk_spellblade.desc",
                chinese ? "施放法术后，你的下一次攻击携带咒刃，持续 %1$s 秒：命中时额外造成"
                                + " %3$s×攻击力 + %4$s×法术强度 的魔法伤害，并为你回复 %5$s×法术强度"
                                + " + %6$s×最大生命 的生命值。咒刃触发后，需再等 %2$s 秒才能由施法重新装填。"
                        : "After casting a spell, your next attack gains Spellblade for %1$s seconds: "
                                + "it deals %3$s × attack damage + %4$s × spell power bonus magic "
                                + "damage and heals you for %5$s × spell power + %6$s × max health. "
                                + "After it triggers, it can be primed again by casting only after "
                                + "%2$s seconds.");
        // 破垒者：成型炸药
        add("passive.lolaccessories.shaped_charge.title",
                chinese ? "唯一被动—成型炸药" : "Unique Passive - Shaped Charge");
        add("passive.lolaccessories.shaped_charge.desc",
                chinese ? "近战攻击命中敌人后，额外附加 %1$s + %2$s×护甲穿透 的真实伤害。"
                                + "冷却时间：%3$s 秒。"
                        : "After a melee attack hits an enemy, deal %1$s bonus true damage plus "
                                + "%2$s × your armor penetration. Cooldown: %3$s seconds.");
        // 无穷饥渴：饥馑 / 盛宴
        add("passive.lolaccessories.famine.title",
                chinese ? "唯一被动—饥馑" : "Unique Passive - Famine");
        add("passive.lolaccessories.famine.desc",
                chinese ? "你获得 %1$s 冷却缩减；攻击力每达到 100 点，额外获得 %2$s 冷却缩减。"
                        : "Gain %1$s cooldown reduction, plus %2$s more cooldown reduction for every "
                                + "100 points of attack damage.");
        add("passive.lolaccessories.feast.title",
                chinese ? "唯一被动—盛宴" : "Unique Passive - Feast");
        add("passive.lolaccessories.feast.desc",
                chinese ? "被你伤害过的目标在 %3$s 秒内死亡时，你获得 %1$s 全能吸血，持续 %2$s 秒。"
                        : "When a target you damaged within the last %3$s seconds dies, gain "
                                + "%1$s omnivamp for %2$s seconds.");
        // 海克斯镜片 C44：高倍镜
        add("passive.lolaccessories.long_shot.title",
                chinese ? "唯一被动—高倍镜" : "Unique Passive - Long Shot");
        add("passive.lolaccessories.long_shot.desc",
                chinese ? "瞄准的目标越远，伤害越高：远程攻击最多获得 %1$s 的额外伤害，"
                                + "在 %2$s 格外达到上限。"
                        : "The farther your target, the harder it hits: ranged attacks deal up to "
                                + "%1$s bonus damage based on distance, reaching full effect at "
                                + "%2$s blocks.");
        // 班德尔音管：嘹亮旋律
        add("passive.lolaccessories.fanfare.title",
                chinese ? "唯一被动—嘹亮旋律" : "Unique Passive - Fanfare");
        add("passive.lolaccessories.fanfare.desc",
                chinese ? "对近期被你伤害过的敌人施加负面效果后，你获得 %1$s 移动速度，并使 %5$s 格内的友军"
                                + "（包括你自己）获得 %2$s 攻击速度，持续 %3$s 秒。冷却时间：%4$s 秒。"
                        : "After afflicting an enemy you recently damaged with a harmful effect, you "
                                + "gain %1$s movement speed and allies within %5$s blocks (including "
                                + "you) gain %2$s attack speed for %3$s seconds. Cooldown: %4$s seconds.");
        // 原生质护带：救主灵刃
        add("passive.lolaccessories.protoplasm.title",
                chinese ? "唯一被动—救主灵刃" : "Unique Passive - Protoplasm");
        add("passive.lolaccessories.protoplasm.desc",
                chinese ? "当一次伤害将使你的生命值降至 %1$s 以下时，获得 %2$s~%3$s 点临时最大生命值，并在"
                                + " %6$s 秒内逐步回复 %4$s~%5$s 点生命（数值随你的等级成长）；同时移动速度提升"
                                + " %8$s、韧性提升 %9$s。冷却时间：%7$s 秒。"
                        : "When damage would drop your health below %1$s, gain %2$s–%3$s temporary max "
                                + "health and recover %4$s–%5$s health over %6$s seconds (scaling with "
                                + "your level), along with %8$s movement speed and %9$s tenacity. "
                                + "Cooldown: %7$s seconds.");
        // 猎魔人弩箭：开战弹幕
        add("passive.lolaccessories.barrage.title",
                chinese ? "唯一被动—开战弹幕" : "Unique Passive - Barrage");
        add("passive.lolaccessories.barrage.desc",
                chinese ? "施放终极技能后的 %2$s 秒内，接下来 %1$s 次远程攻击化为弹幕，必定暴击，"
                                + "并额外造成相当于该次伤害 %3$s 的真实伤害。冷却时间：%4$s 秒。"
                        : "Within %2$s seconds of casting an ultimate, your next %1$s ranged attacks "
                                + "become a barrage that always critically strikes and deals %3$s of "
                                + "the hit's damage as bonus true damage. Cooldown: %4$s seconds.");
        // 猎魔人弩箭：终极迅捷（终极技能冷却缩减）——它是该装备的「唯一被动」技能，不是基础属性，
        // tooltip 以 LoL 被动样式展示（GearItem 从 attributes 的 lolaccessories:ultimate_cdr 条目取数值）
        add("passive.lolaccessories.ultimate_cdr.title",
                chinese ? "唯一被动—终极迅捷" : "Unique Passive - Ultimate Alacrity");
        add("passive.lolaccessories.hexcharged.title",
                chinese ? "唯一被动—海克斯充能" : "Unique Passive - Hexcharged");
        add("passive.lolaccessories.vigil.title",
                chinese ? "唯一被动—守夜" : "Unique Passive - Vigil");
        add("passive.lolaccessories.ultimate_cdr.desc",
                chinese ? "获得 %1$s 终极技能冷却缩减（终极技能急速）。"
                        : "Grants %1$s ultimate cooldown reduction (ultimate haste).");
        // 澄空之愿：唯一被动—澄澈天空（弹射物伤害概率转虚空/真实伤害）
        add("passive.lolaccessories.clear_sky.title",
                chinese ? "唯一被动—澄澈天空" : "Unique Passive - Clear Sky");
        add("passive.lolaccessories.clear_sky.desc",
                chinese ? "造成的所有弹射物伤害有 %1$s 的概率转化为§9虚空伤害§7，无视护甲与魔抗。"
                        : "All projectile damage you deal has a %1$s chance to become §9void damage§7, ignoring armor and magic resist.");
        // 2026 传说第 2 批
        add("passive.lolaccessories.glory.mejais.note",
                chinese ? "与黑暗封印共享层数，且两件不可同时佩戴；超出黑暗封印层数上限的层数，"
                                + "只有在佩戴窃魂卷时才会生效。"
                        : "Shares stacks with Dark Seal and cannot be equipped alongside it. Stacks "
                                + "beyond Dark Seal's cap only take effect while wearing Soulstealer.");
        add("passive.lolaccessories.waltz.title",
                chinese ? "唯一被动—幽影华尔兹" : "Unique Passive - Spectral Waltz");
        add("passive.lolaccessories.waltz.desc",
                chinese ? "无视其它生物的碰撞体积，可自由穿过人群。"
                        : "Ignore unit collision, moving freely through other beings.");
        add("passive.lolaccessories.zeal.title",
                chinese ? "唯一被动—霜火风暴" : "Unique Passive - Frostfire Tempest");
        add("passive.lolaccessories.zeal.desc",
                chinese ? "施放终极技能后，%3$s 秒内就绪一个风暴；进入战斗后召唤风暴环绕自身，"
                                + "持续 %3$s 秒，每秒对 %2$s 格内的敌人造成 %1$s 点魔法伤害并施加 30% 减速。"
                                + "冷却时间：%4$s 秒。"
                        : "After casting an ultimate, a storm is readied for %3$s seconds. Upon "
                                + "entering combat, a storm surrounds you for %3$s seconds, dealing %1$s "
                                + "magic damage per second to enemies within %2$s blocks and slowing them "
                                + "by 30%. Cooldown: %4$s seconds.");
        add("passive.lolaccessories.vigor.title",
                chinese ? "唯一被动—无拘活力" : "Unique Passive - Boundless Vitality");
        add("passive.lolaccessories.vigor.desc",
                chinese ? "受到的所有治疗与护盾效果提升 %1$s。"
                        : "All healing and shields you receive are increased by %1$s.");
        add("passive.lolaccessories.sunfire.title",
                chinese ? "唯一被动—献祭" : "Unique Passive - Sunfire");
        add("passive.lolaccessories.sunfire.desc",
                chinese ? "进入战斗后，灼烧 %3$s 格内的敌人，每秒造成 %1$s 点外加 %2$s 额外生命值的魔法伤害。"
                        : "While in combat, scorch enemies within %3$s blocks for %1$s plus %2$s bonus "
                                + "health magic damage per second.");
        add("passive.lolaccessories.remnant.title",
                chinese ? "唯一被动—灵液护盾" : "Unique Passive - Ichorshield");
        add("passive.lolaccessories.remnant.desc",
                chinese ? "生命值已满时溢出的治疗转化为血色护盾，护盾上限 %1$s 点，持续 %2$s 秒。"
                        : "Overflow healing at full health becomes a blood shield, up to %1$s, "
                                + "lasting %2$s seconds.");
        add("passive.lolaccessories.overdrive.title",
                chinese ? "唯一被动—过载" : "Unique Passive - Overdrive");
        add("passive.lolaccessories.overdrive.desc",
                chinese ? "施放终极技能后 %3$s 秒内获得 +50%% 攻击速度（含等额蓄力速度）与 +20%% 移速。"
                                + "内部冷却：%4$s 秒。"
                        : "Casting an ultimate grants +%1$s attack speed (and equal draw speed) and "
                                + "+%2$s movement speed for %3$s seconds. Internal cooldown: %4$s seconds.");
        add("passive.lolaccessories.demon_king.title",
                chinese ? "唯一被动—我是大魔王" : "Unique Passive - I Am the Demon King");
        add("passive.lolaccessories.demon_king.desc",
                chinese ? "每拥有 1 个诅咒附魔或负面药水效果，本装备全部数值提升 %1$s；每拥有 1 个"
                                        + "正面附魔或正面药水效果，全部数值降低 %2$s。"
                        : "Each curse enchantment or harmful effect increases all values of this "
                                + "accessory by %1$s; each beneficial enchantment or effect reduces "
                                + "them by %2$s.");
        add("passive.lolaccessories.steraks_lifeline.title",
                chinese ? "唯一被动—救主灵刃" : "Unique Passive - Lifeline");
        add("passive.lolaccessories.steraks_lifeline.desc",
                chinese ? "受到将使你的生命值跌到 %1$s 以下的伤害时，提供 %2$s 点护盾，在 %3$s 秒内持续衰减。"
                        : "When damage would bring you below %1$s health, gain a %2$s shield that decays "
                                + "over %3$s seconds.");
        add("passive.lolaccessories.grasping_claws.title",
                chinese ? "唯一被动—抓人双爪" : "Unique Passive - Grasping Claws");
        add("passive.lolaccessories.grasping_claws.desc",
                chinese ? "获得 %1$s 攻击力。" : "Grants %1$s attack damage.");
        // 基克的聚合：终极技能急速被动（官方口径被动名）
        add("passive.lolaccessories.icicle_burn.title",
                chinese ? "唯一被动—冰晶燃烧" : "Unique Passive - Icicle Burn");
        // 实现器：法力成真（主动，冷却不受冷却缩减影响）
        add("active.lolaccessories.realize.title",
                chinese ? "唯一主动—法力成真" : "Unique Active - Realize");
        add("active.lolaccessories.realize.desc",
                chinese ? "激活后的 %1$s 秒内，法术冷却几乎立即完成、吟唱近乎瞬发，但施放法术的魔力消耗"
                                + "提升为 %2$s 倍。冷却时间：%3$s 秒，不受冷却缩减影响。"
                        : "For %1$s seconds, spell cooldowns nearly reset instantly and casting is "
                                + "almost instant, but spell mana costs are multiplied by %2$s. "
                                + "Cooldown: %3$s s, unaffected by cooldown reduction.");
        add("key." + LOLAccessories.MOD_ID + ".realize",
                chinese ? "实现器：法力成真" : "Actualizer: Realize");
        add("skill.lolaccessories.realize.start",
                chinese ? "法力成真！法术冷却近乎归零，施法消耗翻倍。"
                        : "Realize! Spell cooldowns nearly reset; mana costs doubled.");
        add("skill.lolaccessories.realize.cooldown",
                chinese ? "法力成真冷却中：剩余 %1$s 秒。" : "Realize on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.realize.need_item",
                chinese ? "需要佩戴「%1$s」才能使用法力成真。"
                        : "You must be wearing %1$s to use Realize.");
        // 战斗内动作条回执（服务端 → 客户端）
        add("legend.lolaccessories.spellblade.armed",
                chinese ? "咒刃就绪！%1$s 秒内的下一次攻击将附加魔法伤害并回复生命。"
                        : "Spellblade primed! Your next attack within %1$s seconds deals bonus magic "
                                + "damage and heals you.");
        add("legend.lolaccessories.spellblade.proc",
                chinese ? "咒刃！" : "Spellblade!");
        add("legend.lolaccessories.barrage.armed",
                chinese ? "开战弹幕！%2$s 秒内，接下来 %1$s 次远程攻击必定暴击。"
                        : "Barrage armed! Your next %1$s ranged attacks within %2$s seconds will critically strike.");
        add("legend.lolaccessories.barrage.crit",
                chinese ? "弹幕暴击！附加真实伤害。" : "Barrage crit! Bonus true damage dealt.");
        add("legend.lolaccessories.clear_sky.proc",
                chinese ? "澄澈天空！弹射物伤害化为虚空。" : "Clear Sky! Projectile damage turned void.");
        // 女神泪系列（法力流传说，4 对蜕变）
        add("item.lolaccessories.tear_family.progress",
                chinese ? "法力层数：%s / 360" : "Mana Charge: %s / 360");
        add("item.lolaccessories.yun_tal.progress",
                chinese ? "熟能生巧：%s / %s 层（当前暴击率 %s）" : "Practice: %s / %s stacks (crit chance %s)");
        add("passive.lolaccessories.tear_stack.title",
                chinese ? "唯一被动—法力流" : "Unique Passive - Manaflow");
        add("passive.lolaccessories.tear_stack.desc",
                chinese ? "每 8 秒获得一层充能，普攻或技能命中时消耗充能获得 %1$s 点额外法力"
                                + "（命中玩家翻倍）；叠满 360 额外法力后自动蜕变。"
                        : "Gain a charge every 8 seconds; attacks and spells consume a charge to grant "
                                + "%1$s bonus mana (doubled against champions). Transforms at 360 bonus mana.");
        add("passive.lolaccessories.tear_shock.title",
                chinese ? "唯一被动—冲击" : "Unique Passive - Shock");
        add("passive.lolaccessories.tear_awe.title",
                chinese ? "唯一被动—敬畏" : "Unique Passive - Awe");
        add("passive.lolaccessories.awe_ad.desc",
                chinese ? "每 100 点额外法力提供 %1$s 额外攻击力。"
                        : "Grants %1$s bonus attack damage per 100 bonus mana.");
        add("passive.lolaccessories.awe_ap_1.desc",
                chinese ? "每 100 点额外法力提供 %1$s 法术强度。"
                        : "Grants %1$s ability power per 100 bonus mana.");
        add("passive.lolaccessories.awe_ap_2.desc",
                chinese ? "每 100 点额外法力提供 %1$s 法术强度。"
                        : "Grants %1$s ability power per 100 bonus mana.");
        add("passive.lolaccessories.awe_hp.desc",
                chinese ? "每 100 点额外法力提供 %1$s 额外生命值。"
                        : "Grants %1$s bonus health per 100 bonus mana.");
        add("passive.lolaccessories.awe_hs.desc",
                chinese ? "每 100 点额外法力提供 %1$s 治疗与护盾强度。"
                        : "Grants %1$s heal and shield power per 100 bonus mana.");
        add("passive.lolaccessories.tear_shock.desc",
                chinese ? "普攻命中英雄时附加 1.2%% 最大法力的物理伤害；技能命中时附加"
                                + " 4%%（近战）/3%%（远程）最大法力的物理伤害（同一目标 6.5 秒内最多触发一次）。"
                        : "Basic attacks deal 1.2%% max mana bonus physical damage; spells deal "
                                + "4%% (melee) / 3%% (ranged) max mana (once per target per 6.5s).");
        add("passive.lolaccessories.tear_lifeline.title",
                chinese ? "唯一被动—应急护盾" : "Unique Passive - Lifeline");
        add("passive.lolaccessories.tear_lifeline.desc",
                chinese ? "受到将使生命值低于 30%% 的伤害时，获得相当于 18%% 最大法力的护盾，"
                                + "持续 3 秒（90 秒冷却）。"
                        : "Upon taking damage that would bring you below 30%% health, gain a shield "
                                + "equal to 18%% max mana for 3s (90s cooldown).");
        add("passive.lolaccessories.tear_everlasting.title",
                chinese ? "唯一被动—永恒" : "Unique Passive - Everlasting");
        add("passive.lolaccessories.tear_everlasting.desc",
                chinese ? "对敌人施加移动减速效果时，获得 100 + 4.5%% 最大法力的护盾，持续 3 秒"
                                + "（8 秒冷却；附近有多名敌人时提升 80%%）。"
                        : "Slowing an enemy grants a shield of 100 + 4.5%% max mana for 3s "
                                + "(8s cooldown; increased 80%% near multiple enemies).");
        add("passive.lolaccessories.tear_consonance.title",
                chinese ? "唯一被动—共鸣" : "Unique Passive - Consonance");
        add("passive.lolaccessories.tear_consonance.desc",
                chinese ? "附近存在敌人时，每秒治疗 16 格内血量百分比最低的友方玩家，"
                                + "治疗量相当于 0.8%% 最大法力。"
                        : "Every second, heal the lowest-health nearby ally for 0.8%% max mana "
                                + "while enemies are near.");
        add("legend.lolaccessories.tear.transform",
                chinese ? "法力流涌动！你的装备蜕变为了 %s！" : "Your item has transformed into %s!");
        add("legend.lolaccessories.seraph.proc",
                chinese ? "应急护盾展开！" : "Emergency Shield engaged!");
        add("legend.lolaccessories.fimbulwinter.proc",
                chinese ? "冬之誓的守护！" : "Fimbulwinter's bulwark!");
        // AD 物理系传说（守护天使 / 育恩塔尔荒野箭 / 凡性的提醒 / 多米尼克领主的致意）
        add("passive.lolaccessories.guardian_angel.title",
                chinese ? "唯一被动—重生" : "Unique Passive - Rebirth");
        add("passive.lolaccessories.guardian_angel.desc",
                chinese ? "受到致命伤害时，像不死图腾一样免死一次，回复 50% 基础生命值并恢复 100% 最大法力值；"
                                + "冷却 60 秒。"
                        : "Upon taking lethal damage, cheat death like a Totem of Undying, restoring "
                                + "50% base health and 100% max mana; 60s cooldown.");
        add("passive.lolaccessories.yun_tal_crit.title",
                chinese ? "唯一被动—熟能生巧" : "Unique Passive - Practice Makes Lethal");
        add("passive.lolaccessories.yun_tal_crit.desc",
                chinese ? "弹射物命中时永久获得 0.4% 暴击率，至多 25%（绑定玩家，重新佩戴时恢复）。"
                        : "Projectile hits permanently grant 0.4% crit chance, up to 25% (bound to the player).");
        add("passive.lolaccessories.yun_tal_flurry.title",
                chinese ? "唯一被动—疾风连射" : "Unique Passive - Flurry");
        add("passive.lolaccessories.yun_tal_flurry.desc",
                chinese ? "弹射物命中玩家时获得 30% 蓄力速度，持续 6 秒（30 秒冷却；弹射物命中使它缩短 1 秒）。"
                        : "Projectile-hitting-a-player grants 30% draw speed for 6s (30s cooldown; "
                                + "projectile hits shorten it by 1s).");
        add("passive.lolaccessories.grievous_wounds_physical.title",
                chinese ? "唯一被动—重伤" : "Unique Passive - Grievous Wounds");
        add("passive.lolaccessories.grievous_wounds_physical.desc",
                chinese ? "物理伤害（含弹射物与铁魔法法术）命中敌人后，使其受到的治疗降低 %1$s，持续 %2$s 秒。"
                        : "Physical damage (incl. projectiles and Iron's Spells) applies Grievous Wounds, "
                                + "reducing the target's incoming healing by %1$s for %2$s seconds.");
        add("passive.lolaccessories.giant_slayer.title",
                chinese ? "唯一被动—巨人杀手" : "Unique Passive - Giant Slayer");
        add("passive.lolaccessories.giant_slayer.desc",
                chinese ? "对非友善目标造成的伤害提升 0%~15%，基于目标额外生命值（15000 额外生命时封顶）。"
                        : "Deal 0%~15% increased damage to non-friendly targets based on their bonus "
                                + "health (capped at 15,000 bonus health).");
        add("legend.lolaccessories.barrage.forced",
                chinese ? "弹幕强袭！强制触发暴击。" : "Forced barrage crit!");
        add("legend.lolaccessories.shaped_charge.proc",
                chinese ? "成型炸药！%1$s 点真实伤害。" : "Shaped charge! %1$s true damage.");
        add("legend.lolaccessories.protoplasm.proc",
                chinese ? "救主灵刃展开！" : "Protoplasm lifeline engaged!");
        add("legend.lolaccessories.fanfare.proc",
                chinese ? "嘹亮旋律！%1$s 名友军获得攻速加成。" : "Fanfare! %1$s allies gained attack speed.");
    }

    /** 装备风味词条（键：item.lolaccessories.<gearId>.lore）。 */
    private void lore(String gearId, String zh, String en) {
        add("item.lolaccessories." + gearId + ".lore", chinese ? zh : en);
    }
}
