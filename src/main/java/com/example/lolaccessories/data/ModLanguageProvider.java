package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.init.ModItems;
import com.example.lolaccessories.init.ModMobEffects;
import net.minecraft.data.PackOutput;
import net.minecraftforge.common.data.LanguageProvider;

import java.util.regex.Pattern;

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

    /**
     * 自动转义语言文本中的裸 %，避免 Component.translatable 内部 String.format 把
     * "40%" 当成格式符抛异常，导致 %1$s 等占位符无法替换、原样显示在 tooltip 中。
     * 保留已有的 %1$s、%% 等合法格式标记。
     */
    private static final Pattern STANDALONE_PERCENT = Pattern.compile("(?<!%)(%(?!(\\d+\\$)?[sd]|%))");

    @Override
    public void add(String key, String value) {
        super.add(key, escapePercents(value));
    }

    private static String escapePercents(String value) {
        return STANDALONE_PERCENT.matcher(value).replaceAll("%%");
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
                {"fantasy_land", "遥远于世的幻想乡", "A Fantasy Land Far Beyond",
                        "累计造成过 100 亿点铁魔法法术伤害。", "Deal 10,000,000,000 cumulative Iron"
                                + "'s Spells spell damage."},
                {"fraud_death", "欺诈死神", "Cheating Death",
                        "在主副手、盔甲槽、背包、快捷栏和末影箱均没有任何物品的情况下，击杀坚守者。隐身衣将承认你。",
                        "Kill the Warden with your hands, armor, inventory, hotbar and ender chest all "
                                + "completely empty. The Emperor's New Clothes will acknowledge you."},
                {"rui_tiandi", "瑞天帝", "The Heavenly Emperor",
                        "完成挑战：总计消耗 10 万法力值，只有生命值上限大于一万时，使用的法力才计数。天帝将降临。",
                        "Spend a total of 100,000 mana—only mana spent above 10,000 max health counts. "
                                + "The Heavenly Emperor shall descend."},
                {"farewell", "告别", "Farewell",
                        "在与恶魂相距 20 格时，用近战攻击将其击杀。灵恸将为你哀恸。",
                        "Kill a ghast with a melee attack while standing 20 blocks away. "
                                + "The Souls' Lament shall mourn for you."},
                {"truth_envoy", "真理的使者", "Envoy of Truth",
                        "完成成就【我们是冠军】。致明日之诗将传颂你的名字。",
                        "Complete We Are the Champions. The Poem for Tomorrow shall sing your name."},
        };
        for (String[] adv : achievements) {
            String prefix = "advancements." + LOLAccessories.MOD_ID + "." + adv[0] + ".";
            add(prefix + "title", chinese ? adv[1] : adv[2]);
            add(prefix + "description", chinese ? adv[3] : adv[4]);
        }

        // ---------- 物品 ----------
        addItem(ModItems.BLACK_CLEAVER, chinese ? "黑色切割者" : "The Black Cleaver");
        addItem(ModItems.RAVENOUS_HYDRA, chinese ? "贪欲九头蛇" : "Ravenous Hydra");
        addItem(ModItems.THORNMAIL, chinese ? "荆棘之甲" : "Thornmail");
        addItem(ModItems.TRINITY_FORCE, chinese ? "三相之力" : "Trinity Force");
        addItem(ModItems.WARMOGS_ARMOR, chinese ? "狂徒铠甲" : "Warmog's Armor");
        addItem(ModItems.HEARTSTEEL, chinese ? "心之钢" : "Heartsteel");
        addItem(ModItems.FATE_DIE, chinese ? "命运十面骰" : "Fate's Die");
        add("key." + LOLAccessories.MOD_ID + ".mock_fate",
                chinese ? "命运十面骰：嘲弄命运" : "Fate's Die: Mock Fate");
        addItem(ModItems.EMERALD_CITY, chinese ? "翡翠城" : "Emerald City");
        addItem(ModItems.EMPERORS_NEW_CLOTHES, chinese ? "隐身衣" : "Emperor's New Clothes");
        addItem(ModItems.HEAVENLY_EMPEROR, chinese ? "天帝" : "Heavenly Emperor");
        addItem(ModItems.SOULS_LAMENT, chinese ? "灵恸" : "Souls' Lament");
        addItem(ModItems.POEM_FOR_TOMORROW, chinese ? "致明日之诗" : "Poem for Tomorrow");
        add("key." + LOLAccessories.MOD_ID + ".farewell_paradise",
                chinese ? "翡翠城：再见桃花源" : "Emerald City: Farewell Paradise");
        addItem(ModItems.RUNAAN_HURRICANE, chinese ? "卢安娜的飓风" : "Runaan's Hurricane");
        addItem(ModItems.STATIKK_SHIV, chinese ? "斯塔缇克电刃" : "Statikk Shiv");
        addItem(ModItems.RABADONS_DEATHCAP, chinese ? "灭世者的死亡之帽" : "Rabadon's Deathcap");
        addItem(ModItems.WITS_END, chinese ? "智慧末刃" : "Wit's End");
        addItem(ModItems.RAPID_FIRECANNON, chinese ? "疾射火炮" : "Rapid Firecannon");
        add("passive.lolaccessories.runaan_hurricane.winds_fury.title",
                chinese ? "唯一被动—风怒" : "Unique Passive - Wind's Fury");
        add("passive.lolaccessories.runaan_hurricane.winds_fury.desc",
                chinese ? "普攻命中后，向附近其他敌人射出 %1$s 支箭，每支造成 %2$s 攻击伤害。"
                        : "Basic attacks fire %1$s bolts at nearby enemies, each dealing %2$s of your "
                                + "attack damage.");
        add("passive.lolaccessories.statikk_shiv.statikk_chain.title",
                chinese ? "盈能—电火花" : "Energized - Electrospark");
        add("passive.lolaccessories.statikk_shiv.statikk_chain.desc",
                chinese ? "移动和普攻积攒盈能。盈能攻击命中时放出链状闪电，对目标及附近至多 %2$s 名敌人"
                                + "造成 %1$s 点闪电魔法伤害。"
                        : "Moving and attacking builds Energized. Your next attack releases chain lightning, "
                                + "dealing %1$s lightning magic damage to the target and up to %2$s nearby enemies.");
        add("passive.lolaccessories.rabadons_deathcap.spell_amplify.title",
                chinese ? "唯一被动—魔法杰作" : "Unique Passive - Magical Opus");
        add("passive.lolaccessories.rabadons_deathcap.spell_amplify.desc",
                chinese ? "你的法术强度提高 %1$s。"
                        : "Your spell power is increased by %1$s.");
        add("passive.lolaccessories.wits_end.wit_end_hit.title",
                chinese ? "唯一被动—磨蚀" : "Unique Passive - Fray");
        add("passive.lolaccessories.wits_end.wit_end_hit.desc",
                chinese ? "普攻命中时附加 %1$s 点邪术魔法伤害。"
                        : "Basic attacks deal %1$s bonus eldritch magic damage.");
        add("passive.lolaccessories.rapid_firecannon.firecannon_bolt.title",
                chinese ? "盈能—火炮" : "Energized - Firecannon");
        add("passive.lolaccessories.rapid_firecannon.firecannon_bolt.desc",
                chinese ? "移动和普攻积攒盈能。盈能攻击附加 %1$s 点火焰魔法伤害。"
                        : "Moving and attacking builds Energized. Your next attack deals %1$s bonus fire "
                                + "magic damage.");
        // ---------- 第八批 5 件传说（岚切 / 巫妖之祸 / 女妖面纱 / 救赎 / 骑士之誓） ----------
        addItem(ModItems.STORMRAZOR, chinese ? "岚切" : "Stormrazor");
        addItem(ModItems.LICH_BANE, chinese ? "巫妖之祸" : "Lich Bane");
        addItem(ModItems.BANSHEES_VEIL, chinese ? "女妖面纱" : "Banshee's Veil");
        addItem(ModItems.REDEMPTION, chinese ? "救赎" : "Redemption");
        addItem(ModItems.KNIGHTS_VOW, chinese ? "骑士之誓" : "Knight's Vow");
        addItem(ModItems.FROZEN_HEART, chinese ? "冰霜之心" : "Frozen Heart");
        addItem(ModItems.NASHORS_TOOTH, chinese ? "纳什之牙" : "Nashor's Tooth");
        addItem(ModItems.RYLAIS_CRYSTAL_SCEPTER, chinese ? "瑞莱的冰晶节杖" : "Rylai's Crystal Scepter");
        addItem(ModItems.MALIGNANCE, chinese ? "残疫" : "Malignance");
        addItem(ModItems.GUINSOOS_RAGEBLADE, chinese ? "鬼索的狂暴之刃" : "Guinsoo's Rageblade");
        addItem(ModItems.VOID_STAFF, chinese ? "虚空之杖" : "Void Staff");
        addItem(ModItems.CRYPTBLOOM, chinese ? "蜕生" : "Cryptbloom");
        addItem(ModItems.MERCURIAL_SCIMITAR, chinese ? "水银弯刀" : "Mercurial Scimitar");
        addItem(ModItems.YOUMUUS_GHOSTBLADE, chinese ? "幽梦之灵" : "Youmuu's Ghostblade");
        // ---------- 第九批 4 件传说的被动词条（官方名） ----------
        add("passive.lolaccessories.frozen_heart.winters_caress.title",
                chinese ? "唯一光环—冬之抚慰" : "Unique Aura - Winter's Caress");
        add("passive.lolaccessories.frozen_heart.winters_caress.desc",
                chinese ? "冰封寒气萦绕周身：%1$s 格内与自己进入战斗状态的敌对生物攻击速度降低 %2$s"
                                + "（脱离战斗后消散）。"
                        : "Enemies within %1$s blocks that are in combat with you have their attack speed "
                                + "reduced by %2$s (fades when out of combat).");
        add("passive.lolaccessories.nashors_tooth.icathian_bite.title",
                chinese ? "唯一被动—艾卡西亚之咬" : "Unique Passive - Icathian Bite");
        add("passive.lolaccessories.nashors_tooth.icathian_bite.desc",
                chinese ? "普通攻击额外造成 %1$s 点外加相当于 %2$s 法术强度的魔法伤害（攻击特效）。"
                        : "Basic attacks deal bonus magic damage equal to %1$s plus %2$s of your "
                                + "spell power (on-hit).");
        add("passive.lolaccessories.rylais_crystal_scepter.rimefrost.title",
                chinese ? "唯一被动—凝霜" : "Unique Passive - Rimefrost");
        add("passive.lolaccessories.rylais_crystal_scepter.rimefrost.desc",
                chinese ? "造成魔法伤害时，使目标减速 %1$s，持续 %2$s 秒。"
                        : "Damaging an enemy with magic slows them by %1$s for %2$s seconds.");
        add("passive.lolaccessories.malignance.hatefog.title",
                chinese ? "唯一被动—憎恨之雾" : "Unique Passive - Hatefog");
        add("passive.lolaccessories.malignance.hatefog.desc",
                chinese ? "终极技能命中敌人后，在其脚下生成一片紫色恨雾（半径 %1$s 格，持续 %2$s 秒）："
                                + "雾中敌人每 0.5 秒受到 %3$s 点外加相当于 %4$s 法术强度的魔法伤害，"
                                + "首次进入雾中还会降低 10 点魔法抗性（每名目标独立 %5$s 秒冷却）。"
                        : "When your ultimate hits an enemy, a purple hatefog blooms beneath them "
                                + "(radius %1$s blocks, lasting %2$s seconds): enemies inside take %3$s plus "
                                + "%4$s of your spell power as magic damage every 0.5 seconds, and lose "
                                + "10 magic resist on first touch (each target every %5$s seconds).");
        add("passive.lolaccessories.wrath.title",
                chinese ? "唯一被动—愤怒" : "Unique Passive - Wrath");
        add("passive.lolaccessories.wrath.desc",
                chinese ? "你的攻击额外造成 30 点魔法伤害（每次普攻/弹射物命中触发，视为攻击特效）。"
                        : "Your attacks deal an additional 30 magic damage (triggers on each basic attack / "
                        + "projectile hit, treated as an on-hit effect).");
        add("passive.lolaccessories.seething_strike.title",
                chinese ? "唯一被动—汹涌打击" : "Unique Passive - Seething Strike");
        add("passive.lolaccessories.seething_strike.desc",
                chinese ? "每次攻击叠加 %1$s 攻击速度，最多 %2$s 层（%3$s 秒）。叠满后每第 3 次攻击会额外再触发一次攻击特效。"
                        : "Each attack grants %1$s attack speed, up to %2$s stacks (%3$s seconds). "
                        + "At max stacks, every 3rd attack also re-triggers your on-hit effect once more.");
        add("passive.lolaccessories.life_from_death.title",
                chinese ? "唯一被动—死中新生" : "Unique Passive - Life from Death");
        add("passive.lolaccessories.life_from_death.desc",
                chinese ? "击杀敌人后，在其死亡位置爆发治疗新星，治疗自身与 %3$s 格内友方玩家 %1$s（+%2$s 法术强度），每隔 %4$s 秒可触发一次。"
                        : "Killing an enemy bursts a heal nova at their death location, healing you and "
                        + "allied players within %3$s blocks for %1$s (+%2$s spell power), "
                        + "usable every %4$s seconds.");
        add("passive.lolaccessories.haunt.title",
                chinese ? "唯一被动—萦绕" : "Unique Passive - Haunt");
        add("passive.lolaccessories.haunt.desc",
                chinese ? "脱战 %2$s 秒后获得 +%1$s 移动速度；进入战斗后立刻失效。"
                        : "While out of combat for %2$s seconds, gain +%1$s movement speed; "
                        + "loses immediately upon entering combat.");
        add("active.lolaccessories.mercurial.title",
                chinese ? "唯一主动—水银" : "Unique Active - Quicksilver");
        add("active.lolaccessories.mercurial.desc",
                chinese ? "解除自身全部有害状态，并获得 +%1$s 移动速度，持续 %2$s 秒（冷却 %3$s 秒）。"
                        : "Cleanse all harmful effects on yourself and gain +%1$s movement speed for %2$s "
                        + "seconds (cooldown %3$s seconds).");
        add("active.lolaccessories.wraith_step.title",
                chinese ? "唯一主动—鬼步" : "Unique Active - Wraith Step");
        add("active.lolaccessories.wraith_step.desc",
                chinese ? "获得 +%1$s 移动速度，持续 %2$s 秒，期间无视单位碰撞（冷却 %3$s 秒）。"
                        : "Gain +%1$s movement speed for %2$s seconds and ignore unit collision "
                        + "during it (cooldown %3$s seconds).");
        add("skill.lolaccessories.mercurial.need_item",
                chinese ? "未佩戴§6水银弯刀§r，无法发动。" : "You are not wearing Mercurial Scimitar.");
        add("skill.lolaccessories.mercurial.cooldown",
                chinese ? "水银弯刀冷却中：还需 §e%s§r 秒。" : "Mercurial Scimitar on cooldown: §e%s§r s left.");
        add("skill.lolaccessories.wraith_step.need_item",
                chinese ? "未佩戴§6幽梦之灵§r，无法发动。" : "You are not wearing Youmuu's Ghostblade.");
        add("skill.lolaccessories.wraith_step.cooldown",
                chinese ? "幽梦之灵冷却中：还需 §e%s§r 秒。" : "Youmuu's Ghostblade on cooldown: §e%s§r s left.");
        add("key." + LOLAccessories.MOD_ID + ".mercurial",
                chinese ? "水银弯刀·水银" : "Mercurial Scimitar: Quicksilver");
        add("key." + LOLAccessories.MOD_ID + ".wraith_step",
                chinese ? "幽梦之灵·鬼步" : "Youmuu's Ghostblade: Wraith Step");
        add("effect.lolaccessories.wraith_step",
                chinese ? "鬼步" : "Wraith Step");
        add("key." + LOLAccessories.MOD_ID + ".pledge",
                chinese ? "骑士之誓：誓约" : "Knight's Vow: Pledge");
        add("key." + LOLAccessories.MOD_ID + ".intervention",
                chinese ? "救赎：降临" : "Redemption: Intervention");
        // 救赎·降临（主动技）
        add("active.lolaccessories.intervention.title",
                chinese ? "唯一主动—降临" : "Unique Active - Intervention");
        add("active.lolaccessories.intervention.desc",
                chinese ? "指定你脚下的位置，%1$s 秒后召唤圣光落下：范围内的敌人受到相当于其最大生命"
                                + " %2$s 的神圣魔法伤害，友方（含你自己）回复相当于其最大生命 %3$s 的生命。"
                                + "作用范围 %4$s 格。冷却时间：%5$s 秒。"
                        : "Mark the ground beneath you; after %1$s seconds, holy light descends: enemies in "
                                + "range take holy magic damage equal to %2$s of their max health, while allies "
                                + "(including you) are healed for %3$s of their max health. Range: %4$s blocks. "
                                + "Cooldown: %5$s seconds.");
        add("passive.lolaccessories.stormrazor_bolt.title",
                chinese ? "盈能—电弧" : "Energized - Bolt");
        add("passive.lolaccessories.stormrazor_bolt.desc",
                chinese ? "移动和普攻积攒盈能。盈能攻击造成 %1$s 点闪电魔法伤害，并使你获得 %2$s 移动速度，"
                                + "持续 %3$s 秒。"
                        : "Moving and attacking builds Energized. Your next attack deals %1$s lightning magic "
                                + "damage and grants you %2$s movement speed for %3$s seconds.");
        add("passive.lolaccessories.lich_bane.spellblade.title",
                chinese ? "唯一被动—咒刃" : "Unique Passive - Spellblade");
        add("passive.lolaccessories.lich_bane.spellblade.desc",
                chinese ? "施放法术后，你的下一次攻击获得 50% 攻击速度，并造成相当于 75% 攻击伤害"
                                + "外加 %1$s 法术强度的额外魔法伤害（攻击特效），冷却 1.5 秒。"
                        : "After using an ability, your next attack gains 50% attack speed and deals bonus "
                                + "magic damage equal to 75% of your attack damage plus %1$s of your spell "
                                + "power. Cooldown: 1.5 seconds.");
        add("passive.lolaccessories.banshees_veil.annul.title",
                chinese ? "唯一被动—废除" : "Unique Passive - Annul");
        add("passive.lolaccessories.banshees_veil.annul.desc",
                chinese ? "获得一层法术护盾，格挡下一个命中的铁魔法法术。若 40 秒内未被打破，护盾将重新充能。"
                        : "Gain a spell shield that blocks the next Iron's spell that hits you. If not "
                                + "broken within 40 seconds, the shield recharges.");
        add("passive.lolaccessories.pledge.title",
                chinese ? "唯一被动—牺牲" : "Unique Passive - Sacrifice");
        add("passive.lolaccessories.pledge.desc",
                chinese ? "与一名友方生物缔结系链：其受到的伤害 %1$s 转移给你承受；"
                                + "当其造成伤害时，你会回复相当于该伤害 %2$s 的生命。"
                        : "Link to an ally: %1$s of the damage they take is redirected to you; when they "
                                + "deal damage, you are healed for %2$s of that damage.");
        add("active.lolaccessories.pledge.title",
                chinese ? "唯一主动—誓约" : "Unique Active - Pledge");
        add("active.lolaccessories.pledge.desc",
                chinese ? "与准星方向的友方生物（含玩家）缔结系链。冷却 60 秒。"
                        : "Link to the ally (including players) in your crosshair. Cooldown: 60 seconds.");
        add("msg.lolaccessories.pledge.bound",
                chinese ? "已与 %s 缔结誓约！" : "Pledged to %s!");
        add("msg.lolaccessories.pledge.broken",
                chinese ? "誓约已解除。" : "Your pledge has been broken.");
        add("msg.lolaccessories.pledge.cooldown",
                chinese ? "誓约冷却中。" : "Pledge is on cooldown.");
        add("msg.lolaccessories.pledge.no_target",
                chinese ? "准星方向没有可缔结誓约的友方目标。" : "No valid ally in your crosshair.");
        add("msg.lolaccessories.pledge.already_bound",
                chinese ? "该目标已被另一名骑士之誓绑定。" : "That target is already bound to another Knight's Vow.");
        add("active.lolaccessories.mock_fate.title",
                chinese ? "唯一主动—嘲弄命运" : "Unique Active - Mock Fate");
        add("active.lolaccessories.mock_fate.desc",
                chinese ? "投掷一颗 10 面骰，获得持续 30 秒的随机效果，冷却 180 秒。"
                        : "Roll a ten-sided die and gain a random effect for 30 seconds. Cooldown: 180 seconds.");
        add("passive.lolaccessories.qionghua.title",
                chinese ? "唯一被动—梦之乌托邦" : "Unique Passive - Dream Utopia");
        add("passive.lolaccessories.qionghua.desc",
                chinese ? "使用法术对敌人造成伤害后，为其施加一层琼华，使其受到的伤害提高 %1$s，至多 %2$s 层。"
                        : "Dealing spell damage to an enemy applies one Qionghua stack, increasing damage it takes by %1$s, up to %2$s stacks.");
        add("active.lolaccessories.farewell_paradise.title",
                chinese ? "唯一主动—再见桃花源" : "Unique Active - Farewell Paradise");
        add("active.lolaccessories.farewell_paradise.desc",
                chinese ? "抹杀 10 格内叠满 10 层琼华的生物，并使其掉落的战利品翻倍。冷却时间 30 秒。"
                        : "Erase creatures within 10 blocks that carry 10 Qionghua stacks and double the loot they drop. Cooldown: 30 seconds.");
        add("passive.lolaccessories.zeal.desc",
                chinese ? "施放终极技能后，进入战斗时召唤风暴环绕自身，持续 %3$s 秒，"
                                + "每秒对 %2$s 格内的敌人造成 %1$s 点冰霜魔法伤害并施加 30% 减速。冷却时间：%4$s 秒。"
                        : "After casting an ultimate, entering combat summons a storm around you for %3$s "
                                + "seconds, dealing %1$s ice magic damage per second to enemies within %2$s "
                                + "blocks and slowing them by 30%. Cooldown: %4$s seconds.");
        String[] fatesZh = {
                "掷出 1 点——生命虹吸：你造成的伤害转化为对目标的治疗！",
                "掷出 2 点——命运眷顾：传说暴击率提高 666%！",
                "掷出 3 点——命运嘲弄：传说暴击伤害降低 666%！",
                "掷出 4 点——疾风骤雨：移动速度提高 1000%！",
                "掷出 5 点——大幸运：幸运值提高 999！",
                "掷出 6 点——天神下凡：造成的伤害提高 10000%！",
                "掷出 7 点——死亡凝视：受到相当于自身最大生命 999% 的虚空伤害！",
                "掷出 8 点——混沌药剂：随机获得 17 种药水效果！",
                "掷出 9 点——消失诅咒：全身盔甲被施加消失诅咒！",
                "掷出 10 点——命运馈赠：每 3 秒获得一件随机物品！"};
        String[] fatesEn = {
                "Rolled 1 - Life Siphon: damage you deal heals you instead!",
                "Rolled 2 - Fate's Favor: +666% legendary crit chance!",
                "Rolled 3 - Fate's Mockery: -666% legendary crit damage!",
                "Rolled 4 - Tempest: +1000% movement speed!",
                "Rolled 5 - Great Luck: +999 Luck!",
                "Rolled 6 - Divine Ascension: +10000% damage dealt!",
                "Rolled 7 - Death's Gaze: take void damage equal to 999% of your max health!",
                "Rolled 8 - Chaos Elixir: gain 17 random potion effects!",
                "Rolled 9 - Vanishing Curse: your whole armor is cursed with Vanishing!",
                "Rolled 10 - Fate's Gift: gain a random item every 3 seconds!"};
        for (int i = 0; i < 10; i++) {
            add("msg.lolaccessories.mock_fate.result." + (i + 1), chinese ? fatesZh[i] : fatesEn[i]);
        }
        add("subtitles.lolaccessories.heartsteel_shatter",
                chinese ? "心之钢：击碎印记" : "Heartsteel shatters mark");
        add("msg.lolaccessories.endermite_streak",
                chinese ? "末影螨连击：%1$s/3" : "Endermite streak: %1$s/3");
        add("advancements.lolaccessories.fate_dance.title",
                chinese ? "命运须伴我起舞" : "Fate Must Dance With Me");
        add("advancements.lolaccessories.fate_dance.description",
                chinese ? "用末影珍珠连续三次扔出末影螨"
                        : "Get an endermite from three ender pearls in a row");
        add("passive.lolaccessories.heartsteel.progress",
                chinese ? "已累积生命值：%1$s" : "Bonus health accumulated: %1$s");
        // ---------- 第六批 5 件传说的装备专属被动词条（官方名，数值为本模组实现口径） ----------
        add("passive.lolaccessories.ravenous_hydra.cleave.title",
                chinese ? "唯一被动—顺劈" : "Unique Passive - Cleave");
        add("passive.lolaccessories.ravenous_hydra.cleave.desc",
                chinese ? "普攻会额外对目标周围 %1$s 格内的其他敌人造成相当于 %2$s 攻击力的物理伤害。"
                        : "Basic attacks deal physical damage equal to %2$s of your attack damage to other "
                                + "enemies within %1$s blocks of the target.");
        add("active.lolaccessories.ravenous_hydra.crescent.title",
                chinese ? "唯一主动—嗜血新月" : "Unique Active - Ravenous Crescent");
        add("active.lolaccessories.ravenous_hydra.crescent.desc",
                chinese ? "对周围 %2$s 格内的所有敌人造成相当于 %1$s 攻击力的物理伤害。冷却时间：%3$s 秒。"
                        : "Deal physical damage equal to %1$s of your attack damage to all enemies within "
                                + "%2$s blocks. Cooldown: %3$s seconds.");
        add("passive.lolaccessories.thornmail.thorns.title",
                chinese ? "唯一被动—荆棘" : "Unique Passive - Thorns");
        add("passive.lolaccessories.thornmail.thorns.desc",
                chinese ? "受到普攻命中时，对攻击者造成 %1$s 点外加 %2$s 额外护甲的自然魔法伤害，"
                                + "并使其受到的治疗效果降低 %3$s，持续 %4$s 秒。"
                        : "When struck by a basic attack, deal %1$s nature magic damage plus %2$s of your "
                                + "bonus armor to the attacker and reduce their healing by %3$s for %4$s seconds.");
        add("passive.lolaccessories.trinity_force.quicken.title",
                chinese ? "唯一被动—疾行" : "Unique Passive - Quicken");
        add("passive.lolaccessories.trinity_force.quicken.desc",
                chinese ? "普攻命中后获得 %1$s 移动速度，持续 %2$s 秒。"
                        : "Basic attacks grant %1$s movement speed for %2$s seconds.");
        add("passive.lolaccessories.warmogs_armor.warmog_vigor.title",
                chinese ? "唯一被动—狂徒之活力" : "Unique Passive - Warmog's Vigor");
        add("passive.lolaccessories.warmogs_armor.warmog_vigor.desc",
                chinese ? "获得额外生命值，相当于装备生命值的 %1$s。"
                        : "Gain bonus health equal to %1$s of your equipped health.");
        add("passive.lolaccessories.trinity_force.spellblade.title",
                chinese ? "唯一被动—咒刃" : "Unique Passive - Spellblade");
        add("passive.lolaccessories.trinity_force.spellblade.desc",
                chinese ? "施放法术后，你的下一次普攻额外造成相当于 %1$s 攻击力的物理伤害，冷却 1.5 秒。"
                        : "After using an ability, your next basic attack deals bonus physical damage equal to "
                                + "%1$s of your attack damage. Cooldown: 1.5 seconds.");
        add("passive.lolaccessories.warmogs_armor.warmog_heart.title",
                chinese ? "唯一被动—狂徒之心" : "Unique Passive - Warmog's Heart");
        add("passive.lolaccessories.warmogs_armor.warmog_heart.desc",
                chinese ? "若拥有 %1$s 额外生命值，且在 %3$s 秒内未受到伤害，则每秒回复 %2$s 最大生命值。"
                        : "If you have at least %1$s bonus health and have not taken damage for %3$s seconds, "
                                + "restore %2$s of your max health each second.");
        add("entity.lolaccessories.test_brute",
                chinese ? "测试蛮兵（100万血）" : "Test Brute (1,000,000 HP)");
        add("entity.lolaccessories.test_player_dummy",
                chinese ? "测试假人（玩家标签）" : "Test Dummy (Player Tag)");
        add("passive.lolaccessories.goliath.title",
                chinese ? "唯一被动—歌莉娅巨人" : "Unique Passive - Goliath");
        add("passive.lolaccessories.goliath.desc",
                chinese ? "每 1000 最大生命值获得 3% 物理伤害减免。"
                        : "Gain 3% physical damage reduction per 1000 max health.");
        add("passive.lolaccessories.colossal_consumption.title",
                chinese ? "唯一被动—庞然吞食" : "Unique Passive - Colossal Consumption");
        add("passive.lolaccessories.colossal_consumption.desc",
                chinese ? "与目标交战时对其施加吞食印记，印记在 3 秒内成熟；用攻击命中被标记的目标，"
                                + "造成 70 + 6% 最大生命值的物理伤害，并获得相当于该伤害 10% 的最大生命值。"
                        : "While fighting a target, brand it with a mark that matures over 3 seconds; "
                                + "hitting the marked target with an attack deals 70 + 6% of your max health "
                                + "as physical damage and grants max health equal to 10% of the damage.");
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
        // 第十四批 5 件 3 级（传说）装备
        addItem(ModItems.FORCE_OF_NATURE, chinese ? "自然之力" : "Force of Nature");
        addItem(ModItems.HORIZON_FOCUS, chinese ? "视界专注" : "Horizon Focus");
        addItem(ModItems.RIFTMAKER, chinese ? "裂隙制造者" : "Riftmaker");
        addItem(ModItems.SHADOWFLAME, chinese ? "影焰" : "Shadowflame");
        addItem(ModItems.STORMSURGE, chinese ? "风暴狂涌" : "Stormsurge");
        // 第十五批 5 件 3 级（传说）装备
        addItem(ModItems.DEATHS_DANCE, chinese ? "死亡之舞" : "Death's Dance");
        addItem(ModItems.CHEMPUNK_CHAINSWORD, chinese ? "炼金朋克链锯剑" : "Chempunk Chainsword");
        addItem(ModItems.SUNDERED_SKY, chinese ? "焚天" : "Sundered Sky");
        addItem(ModItems.STRIDEBREAKER, chinese ? "挺进破坏者" : "Stridebreaker");
        addItem(ModItems.LIANDRYS_TORMENT, chinese ? "兰德里的折磨" : "Liandry's Torment");
        // 第六批 5 件 3 级（传说）装备
        addItem(ModItems.ROD_OF_AGES, chinese ? "时光之杖" : "Rod of Ages");
        addItem(ModItems.ICEBORN_GAUNTLET, chinese ? "冰脉护手" : "Iceborn Gauntlet");
        addItem(ModItems.JAKSHO, chinese ? "千变者贾修" : "Jak'Sho, the Protean");
        addItem(ModItems.KRAKEN_SLAYER, chinese ? "海妖杀手" : "Kraken Slayer");
        addItem(ModItems.IMMORTAL_SHIELDBOW, chinese ? "不朽盾弓" : "Immortal Shieldbow");
        addItem(ModItems.NAAVORI_FLICKERBLADE, chinese ? "纳沃利烁刃" : "Naavori Flickerblade");
        addItem(ModItems.THE_COLLECTOR, chinese ? "收集者" : "The Collector");
        addItem(ModItems.ECLIPSE, chinese ? "星蚀" : "Eclipse");
        addItem(ModItems.SERYLDAS, chinese ? "赛瑞尔达的怨恨" : "Serylda's Grudge");
        addItem(ModItems.SERPENTS_FANG, chinese ? "巨蛇之牙" : "Serpent's Fang");
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
        // 第十五批 6 件
        addItem(ModItems.AXIOM_ARC, chinese ? "公理圆弧" : "Axiom Arc");
        addItem(ModItems.HUBRIS, chinese ? "狂妄" : "Hubris");
        addItem(ModItems.PROFANE_HYDRA, chinese ? "亵渎九头蛇" : "Profane Hydra");
        addItem(ModItems.VOLTAIC_CYCLOSWORD, chinese ? "电震涡流剑" : "Voltaic Cyclosword");
        addItem(ModItems.BLOODLETTERS_CURSE, chinese ? "放血者的诅咒" : "Bloodletter's Curse");
        addItem(ModItems.ABYSSAL_MASK, chinese ? "深渊面具" : "Abyssal Mask");
        // 第十六批 7 双 2 级（史诗）鞋
        addItem(ModItems.BERSERKER_GREAVES, chinese ? "狂战士胫甲" : "Berserker's Greaves");
        addItem(ModItems.SYMBIOTE_SOLES, chinese ? "暴食胫甲" : "Symbiote Soles");
        addItem(ModItems.SWIFT_BOOTS, chinese ? "轻灵之靴" : "Swift Boots");
        addItem(ModItems.SORCERERS_SHOES, chinese ? "法师之靴" : "Sorcerer's Shoes");
        addItem(ModItems.PLATED_STEELCAPS, chinese ? "铁板靴" : "Plated Steelcaps");
        addItem(ModItems.MERCURYS_TREADS, chinese ? "水银之靴" : "Mercury's Treads");
        addItem(ModItems.IONIAN_BOOTS, chinese ? "明朗之靴" : "Ionian Boots of Lucidity");
        // 第十六批 7 双 3 级（传说）鞋
        addItem(ModItems.IMMORTAL_PATH, chinese ? "不朽之路" : "Undying Path");
        addItem(ModItems.SWIFTMARCH, chinese ? "迅速进军" : "Swiftmarch");
        addItem(ModItems.GUNMETAL_GREAVES, chinese ? "炮铜胫甲" : "Gunmetal Greaves");
        addItem(ModItems.CRIMSON_LUCIDITY, chinese ? "猩红明朗" : "Crimson Lucidity");
        addItem(ModItems.CHAINLACED_CRUSHERS, chinese ? "带链碾碎者" : "Chainlaced Crushers");
        addItem(ModItems.ARMORED_ADVANCE, chinese ? "装甲战靴" : "Armored Advance");
        addItem(ModItems.SPELLSLINGERS_SHOES, chinese ? "灵能使之靴" : "Spellslinger's Shoes");
        // 金币
        addItem(ModItems.GOLD_COIN, chinese ? "金币" : "Gold Coin");

        // ---------- 第十一批 5 件装备 ----------
        addItem(ModItems.RANDUINS_OMEN, chinese ? "兰顿之兆" : "Randuin's Omen");
        addItem(ModItems.HEXTECH_GUNBLADE, chinese ? "海克斯科技枪刃" : "Hextech Gunblade");
        addItem(ModItems.HEXTECH_ROCKETBELT, chinese ? "海克斯科技火箭腰带" : "Hextech Rocketbelt");
        addItem(ModItems.RUINED_KING, chinese ? "破败王者之刃" : "Blade of the Ruined King");
        addItem(ModItems.MAW_OF_MALMORTIUS, chinese ? "玛莫提乌斯之噬" : "Maw of Malmortius");
        // 第十二批 5 件传说装备
        addItem(ModItems.ZHONYAS_HOURGLASS, chinese ? "中娅沙漏" : "Zhonya's Hourglass");
        addItem(ModItems.SPEAR_OF_SHOJIN, chinese ? "朔极之矛" : "Spear of Shojin");
        addItem(ModItems.MORELLONOMICON, chinese ? "莫雷洛秘典" : "Morellonomicon");
        addItem(ModItems.UMBRAL_GLAIVE, chinese ? "黯影阔剑" : "Umbral Glaive");
        addItem(ModItems.HULLBREAKER, chinese ? "破舰者" : "Hullbreaker");
        addItem(ModItems.TERMINUS, chinese ? "界弓" : "Terminus");
        addItem(ModItems.ESSENCE_REAVER, chinese ? "夺萃之镰" : "Essence Reaver");
        addItem(ModItems.DEAD_MANS_PLATE, chinese ? "亡者的板甲" : "Dead Man's Plate");
        addItem(ModItems.TITANIC_HYDRA, chinese ? "巨型九头蛇" : "Titanic Hydra");
        addItem(ModItems.EDGE_OF_NIGHT, chinese ? "夜之锋刃" : "Edge of Night");

        // 海克斯科技枪刃·闪电箭（主动，Lightning Bolt）
        add("active.lolaccessories.gunblade.title",
                chinese ? "唯一主动—闪电箭" : "Unique Active - Lightning Bolt");
        add("active.lolaccessories.gunblade.desc",
                chinese ? "朝准星方向发射一道闪电箭，命中目标造成 175(+30% 法术强度) 的魔法伤害，"
                                + "并减速 25%（持续 1.5 秒）。冷却时间：%1$s 秒。"
                        : "Fire a Lightning Bolt toward your crosshair; on hit it deals 175 (+30% Ability "
                                + "Power) magic damage and slows the target by 25% for 1.5 seconds. "
                                + "Cooldown: %1$s seconds.");
        // 海克斯科技火箭腰带·超音速（主动，Supersonic）
        add("active.lolaccessories.rocketbelt.title",
                chinese ? "唯一主动—超音速" : "Unique Active - Supersonic");
        add("active.lolaccessories.rocketbelt.desc",
                chinese ? "向准星方向冲刺并射出 7 枚火箭，命中敌人造成 100(+10% 法术强度) 魔法伤害。"
                                + "冷却时间：%1$s 秒。"
                        : "Dash toward your crosshair and fire 7 rockets that deal 100 (+10% Ability Power) "
                                + "magic damage. Cooldown: %1$s seconds.");
        // 玛莫提乌斯之噬·救主灵刃（被动）
        add("passive.lolaccessories.maw_lifeline.title",
                chinese ? "唯一被动—救主灵刃" : "Unique Passive - Lifeline");
        add("passive.lolaccessories.maw_lifeline.desc",
                chinese ? "当受到伤害即将使生命值降至 30% 以下时，获得基于最大生命值的魔法护盾，持续 3 秒。"
                        : "When taking damage that would reduce you below 30% health, gain a magic shield "
                                + "based on your maximum health for 3 seconds.");
        // 兰顿之兆·坚韧（被动，Resilience）
        add("passive.lolaccessories.randuins_resilience.title",
                chinese ? "唯一被动—坚韧" : "Unique Passive - Resilience");
        add("passive.lolaccessories.randuins_resilience.desc",
                chinese ? "受到的暴击伤害降低 %1$s。"
                        : "Reduces incoming critical strike damage by %1$s.");
        // 兰顿之兆·谦卑（主动，Humility）
        add("active.lolaccessories.randuins_active.title",
                chinese ? "唯一主动—谦卑" : "Unique Active - Humility");
        add("active.lolaccessories.randuins_active.desc",
                chinese ? "减速附近敌方单位的移动速度 %1$s，持续 %2$s 秒。冷却时间：%3$s 秒。"
                        : "Slows the movement speed of nearby enemy units by %1$s for %2$s seconds. "
                                + "Cooldown: %3$s seconds.");
        // 破败王者之刃·雾之锋 / 抓挠之影（被动）
        add("passive.lolaccessories.ruined_king_current.title",
                chinese ? "唯一被动—雾之锋" : "Unique Passive - Mist's Edge");
        add("passive.lolaccessories.ruined_king_current.desc",
                chinese ? "普攻额外造成目标当前生命值 %1$s 的物理伤害（至少 15）。"
                        : "Basic attacks deal bonus physical damage equal to %1$s of the target's current "
                                + "health (minimum 15).");
        add("passive.lolaccessories.ruined_king_claw.title",
                chinese ? "唯一被动—抓挠之影" : "Unique Passive - Clawing Shadows");
        add("passive.lolaccessories.ruined_king_claw.desc",
                chinese ? "攻击同一目标 3 次后，使其减速 %1$s，持续 %2$s 秒。"
                        : "After attacking the same target 3 times, slow it by %1$s for %2$s seconds.");
        // 主动技能提示（need_item / cooldown）与 tryTrigger 中文本一致
        add("skill.lolaccessories.gunblade.need_item",
                chinese ? "需要：海克斯科技枪刃" : "Requires: Hextech Gunblade");
        add("skill.lolaccessories.gunblade.cooldown",
                chinese ? "海克斯科技枪刃冷却中" : "Hextech Gunblade is on cooldown");
        add("skill.lolaccessories.rocketbelt.need_item",
                chinese ? "需要：海克斯科技火箭腰带" : "Requires: Hextech Rocketbelt");
        add("skill.lolaccessories.rocketbelt.cooldown",
                chinese ? "海克斯科技火箭腰带冷却中" : "Hextech Rocketbelt is on cooldown");
        add("skill.lolaccessories.randuins_active.need_item",
                chinese ? "需要：兰顿之兆" : "Requires: Randuin's Omen");
        add("skill.lolaccessories.randuins_active.cooldown",
                chinese ? "兰顿之兆冷却中" : "Randuin's Omen is on cooldown");

        // 按键绑定名称（缺失会导致按键设置里显示原始键名 key.lolaccessories.xxx）
        add("key." + LOLAccessories.MOD_ID + ".gunblade",
                chinese ? "海克斯科技枪刃：闪电箭" : "Hextech Gunblade: Lightning Bolt");
        add("key." + LOLAccessories.MOD_ID + ".rocketbelt",
                chinese ? "海克斯科技火箭腰带：超音速" : "Hextech Rocketbelt: Supersonic");
        add("key." + LOLAccessories.MOD_ID + ".randuins_active",
                chinese ? "兰顿之兆：谦卑" : "Randuin's Omen: Humility");
        add("key." + LOLAccessories.MOD_ID + ".endless_grief",
                chinese ? "灵恸：此恨无绝" : "Souls' Lament: Endless Grief");

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
                chinese ? "当前加成：+%s 最大法力，上限 +%s"
                        : "Current: +%s max mana, cap +%s");
        add("passive.lolaccessories.glory.title",
                chinese ? "唯一被动—荣耀" : "Unique Passive - Glory");
        add("passive.lolaccessories.glory.desc",
                chinese ? "击杀最大生命值远超你的生物时，获得 %1$s 层荣耀，至多 %4$s 层；阵亡时损失 %2$s 层。每层提供 +%3$s 法术强度，仅在装备时生效。"
                        : "Killing creatures with far more max health than you grants %1$s Glory stacks, up to %4$s. Dying while equipped loses %2$s stacks. Each stack grants +%3$s spell power while equipped.");
        add("passive.lolaccessories.glory.progress",
                chinese ? "当前层数：%s / %s" : "Current stacks: %s / %s");

        // ---------- 第五批 34 件 2 级（史诗）装备新增被动的 tooltip 词条 ----------
        // 数值参数与 LolNewEpicPassiveEvents 结算口径一致，由 GearItem 按 effect.id 分支渲染。
        add("passive.lolaccessories.immolate.title",
                chinese ? "唯一被动—灼烧" : "Unique Passive - Immolate");
        add("passive.lolaccessories.immolate.desc",
                chinese ? "造成或受到伤害时，点燃周围 %2$s 格内的敌人，使其每秒受到 %1$s 点火焰魔法伤害，"
                                + "持续 %3$s 秒。"
                        : "When you deal or take damage, ignite enemies within %2$s blocks, burning "
                                + "them for %1$s fire magic damage per second for %3$s seconds.");
        add("passive.lolaccessories.inflame.title",
                chinese ? "唯一被动—引燃" : "Unique Passive - Inflame");
        add("passive.lolaccessories.inflame.desc",
                chinese ? "魔法伤害命中敌人时将其点燃，使其每秒受到 %1$s 点火焰魔法伤害，持续 %2$s 秒。"
                        : "Magic damage ignites the target, dealing %1$s fire magic damage per second for %2$s seconds.");
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
                chinese ? "对敌方造成伤害会叠加疯狂，每层使你造成的伤害提高 %3$s，至多 %2$s 层；%1$s 秒内未继续命中则层数消失。"
                        : "Dealing damage to enemies builds Madness, granting %3$s increased damage per stack, up to %2$s stacks. Stacks expire after %1$s seconds without a hit.");
        add("passive.lolaccessories.focused_will.title",
                chinese ? "唯一被动—专注意志" : "Unique Passive - Focused Will");
        add("passive.lolaccessories.focused_will.desc",
                chinese ? "造成伤害时每秒至多获得 1 层专注意志，每层使你造成的所有伤害提高 %1$s，至多 %2$s 层；%3$s 秒未造成伤害则层数消失。"
                        : "Dealing damage grants at most 1 Focused Will stack per second. Each stack increases all damage dealt by %1$s, up to %2$s stacks; stacks expire after %3$s seconds without dealing damage.");
        add("passive.lolaccessories.dragonforce.title",
                chinese ? "唯一被动—龙之力量" : "Unique Passive - Dragonforce");
        add("passive.lolaccessories.dragonforce.desc",
                chinese ? "获得 %1$s 基础技能冷却缩减：仅缩短基础技能的冷却时间，不影响终极技能。"
                        : "Grants %1$s basic ability haste: reduces the cooldown of basic abilities only; ultimate abilities are unaffected.");
        add("passive.lolaccessories.nightstalker.title",
                chinese ? "唯一被动—夜行者" : "Unique Passive - Nightstalker");
        add("passive.lolaccessories.nightstalker.desc",
                chinese ? "隐身状态下的普通攻击额外造成 %1$s + %2$s 固定护甲穿透的真实伤害；每 %3$s 秒至多触发一次。"
                        : "Basic attacks while invisible deal %1$s + %2$s of your flat armor penetration as bonus true damage. Triggers at most once every %3$s seconds.");
        add("passive.lolaccessories.skipper.title",
                chinese ? "唯一被动—船长" : "Unique Passive - Skipper");
        add("passive.lolaccessories.skipper.desc",
                chinese ? "普通攻击命中敌人获得 1 层船长，持续 %1$s 秒。叠至 %2$s 层时消耗所有层数，使该次攻击额外造成 %3$s 基础攻击力 + %4$s 最大生命值的物理伤害。"
                        : "Basic attacks against enemies grant 1 Skipper stack for %1$s seconds. At %2$s stacks, consume all stacks to deal %3$s base attack damage plus %4$s maximum health as bonus physical damage.");
        // 第十四批：自然之力 / 视界专注 / 裂隙制造者 / 影焰 / 风暴狂涌
        add("passive.lolaccessories.steadfast.title",
                chinese ? "唯一被动—坚韧" : "Unique Passive - Steadfast");
        add("passive.lolaccessories.steadfast.desc",
                chinese ? "受到魔法伤害时叠加坚韧：叠满 %3$s 层时获得 %1$s 点魔法抗性和 %2$s 移速加成；%4$s 秒内未再受到魔法伤害则层数消失。"
                        : "Taking magic damage builds Steadfast: at %3$s stacks gain %1$s magic resist and %2$s movement speed. Stacks expire after %4$s seconds without taking magic damage.");
        add("passive.lolaccessories.hypershot.title",
                chinese ? "唯一被动—超频射击" : "Unique Passive - Hypershot");
        add("passive.lolaccessories.hypershot.desc",
                chinese ? "以魔法伤害命中 %2$s 格以外的敌人时，标记目标 %3$s 秒：标记期间你对其造成的魔法伤害提高 %1$s。"
                        : "Hitting an enemy with magic damage from %2$s blocks or farther marks them for %3$s seconds, increasing your magic damage against them by %1$s while marked.");
        add("passive.lolaccessories.void_corruption.title",
                chinese ? "唯一被动—虚空侵蚀" : "Unique Passive - Void Corruption");
        add("passive.lolaccessories.void_corruption.desc",
                chinese ? "与敌方作战时，每过 %3$s 秒叠加 1 层虚空侵蚀，每层使你造成的伤害提高 %1$s，至多 %2$s 层；脱离战斗后层数消退。叠满时获得 %4$s（远程 %5$s）全能吸血。"
                        : "While in combat with enemies, gain 1 Void Corruption stack every %3$s seconds, increasing your damage by %1$s per stack, up to %2$s stacks. Stacks fade when out of combat. At maximum stacks, gain %4$s (%5$s ranged) omnivamp.");
        add("passive.lolaccessories.void_infusion.desc",
                chinese ? "获得相当于你 %1$s 额外生命值的法术强度。"
                        : "Gain spell power equal to %1$s of your bonus health.");
        add("passive.lolaccessories.cinderbloom.title",
                chinese ? "唯一被动—灰烬绽放" : "Unique Passive - Cinderbloom");
        add("passive.lolaccessories.cinderbloom.desc",
                chinese ? "对生命值低于 %2$s 的敌人造成的魔法伤害提高 %1$s。"
                        : "Deal %1$s increased magic damage to enemies below %2$s health.");
        add("passive.lolaccessories.stormraider.title",
                chinese ? "唯一被动—风暴掠袭" : "Unique Passive - Stormraider");
        add("passive.lolaccessories.stormraider.desc",
                chinese ? "在 %2$s 秒内对同一名敌人累计造成相当于其最大生命值 %1$s 的伤害时，延迟引爆风暴：造成 %3$s 点外加 %4$s 法术强度的魔法伤害，并获得短暂移速加成（冷却 %5$s 秒）。"
                        : "Dealing damage equal to %1$s of an enemy's max health within %2$s seconds detonates a delayed storm: dealing %3$s plus %4$s spell power magic damage and granting brief movement speed (cooldown %5$s seconds).");
        // 第十五批：死亡之舞 / 焚天 / 兰德里的折磨 / 挺进破坏者
        add("passive.lolaccessories.ignore_pain.title",
                chinese ? "唯一被动—无视痛苦" : "Unique Passive - Ignore Pain");
        add("passive.lolaccessories.ignore_pain.desc",
                chinese ? "所受的一部分伤害（%1$s）会以流血形式在 %2$s 秒里持续扣除。"
                        : "A portion (%1$s) of damage taken is instead drained as bleed over %2$s seconds.");
        add("passive.lolaccessories.defy.title",
                chinese ? "唯一被动—蔑视" : "Unique Passive - Defy");
        add("passive.lolaccessories.defy.desc",
                chinese ? "如果一名在过去 3 秒内被你造成过伤害的敌人阵亡，会净化无视痛苦的剩余伤害，并在 %2$s 秒里持续为你回复 %1$s 额外攻击力的生命值。"
                        : "If an enemy you damaged in the last 3 seconds dies, cleanse the remaining bleed and restore %1$s bonus attack damage as health over %2$s seconds.");
        add("passive.lolaccessories.lightshield_strike.title",
                chinese ? "唯一被动—光盾打击" : "Unique Passive - Lightshield Strike");
        add("passive.lolaccessories.lightshield_strike.desc",
                chinese ? "你对一名敌人打出的第一次攻击会必定暴击，并回复 %1$s（远程 %2$s）攻击力外加 %3$s 已损失生命值的生命值（每目标 %4$s 秒冷却）。"
                        : "Your first attack against an enemy critically strikes and restores %1$s (%2$s ranged) attack damage plus %3$s missing health (per-target %4$s seconds cooldown).");
        add("passive.lolaccessories.torment.title",
                chinese ? "唯一被动—折磨" : "Unique Passive - Torment");
        add("passive.lolaccessories.torment.desc",
                chinese ? "伤害型技能会灼烧敌人，每 0.5 秒造成 %1$s 最大生命值的魔法伤害，持续 %2$s 秒。"
                        : "Damaging abilities burn enemies for %1$s of their max health magic damage every 0.5 seconds for %2$s seconds.");
        add("passive.lolaccessories.suffering.desc",
                chinese ? "在与敌方作战时，每过 1 秒就会造成 %1$s 额外伤害，至多至 %2$s。"
                        : "While in combat with enemies, deal %1$s bonus damage every second, up to %2$s.");
        add("active.lolaccessories.shockwave.title",
                chinese ? "主动技—破阵冲击波" : "Active - Breaking Shockwave");
        add("active.lolaccessories.shockwave.desc",
                chinese ? "对周围 %4$s 格内的敌人造成 %1$s 攻击力的物理伤害并减速 %2$s，持续 %3$s 秒；每命中一个敌方目标就会获得持续衰减的 %2$s 移速（冷却 %5$s 秒）。"
                        : "Deal %1$s attack damage physical damage to enemies within %4$s blocks and slow them by %2$s for %3$s seconds; each enemy hit grants decaying %2$s movement speed (cooldown %5$s seconds).");
        add("skill.lolaccessories.shockwave.need_item",
                chinese ? "需要装备挺进破坏者" : "Requires Stridebreaker equipped");
        // 第六批：时光之杖 / 冰脉护手 / 千变者贾修 / 海妖杀手 / 不朽盾弓
        add("passive.lolaccessories.timeless.title",
                chinese ? "唯一被动—时无级" : "Unique Passive - Timeless");
        add("passive.lolaccessories.timeless.desc",
                chinese ? "这件装备每 %1$s 秒获得 %2$s 生命值、%3$s 法力值和 %4$s 法术强度，至多至 %5$s 层。在达到最大层数时，还会使你的等级提升 1 级。"
                        : "This item gains %2$s health, %3$s mana and %4$s spell power every %1$s seconds, up to %5$s stacks. At maximum stacks, your level increases by 1.");
        add("passive.lolaccessories.iceborn_spellblade.title",
                chinese ? "唯一被动—咒刃" : "Unique Passive - Spellblade");
        add("passive.lolaccessories.iceborn_spellblade.desc",
                chinese ? "在施放一个技能后，你的下一次攻击造成 %1$s 攻击力的额外物理伤害并生成一个持续 %4$s 秒的冰冷地带（半径 %5$s 格）：近战减速 %2$s、远程减速 %3$s。"
                        : "After casting an ability, your next attack deals %1$s attack damage bonus physical damage and creates an icy zone (radius %5$s blocks) for %4$s seconds: %2$s melee / %3$s ranged slow.");
        add("passive.lolaccessories.protean.title",
                chinese ? "唯一被动—虚空生物的复原力" : "Unique Passive - Voidborn Resilience");
        add("passive.lolaccessories.protean.desc",
                chinese ? "在与敌方战斗 %2$s 秒后，使你的护甲和魔法抗性提升 %1$s，持续到战斗结束为止。"
                        : "After %2$s seconds in combat with enemies, your armor and magic resist increase by %1$s until combat ends.");
        add("passive.lolaccessories.bring_it_down.title",
                chinese ? "唯一被动—放倒它" : "Unique Passive - Bring It Down");
        add("passive.lolaccessories.bring_it_down.desc",
                chinese ? "每第 %1$s 次弹射物攻击造成 %2$s 点起的额外物理伤害，这个伤害会基于目标的已损失生命值获得至多 %3$s 的提升。"
                        : "Every %1$s projectile attack deals bonus physical damage starting at %2$s, increased by up to %3$s based on the target's missing health.");
        add("passive.lolaccessories.shieldbow_lifeline.title",
                chinese ? "唯一被动—救主灵刃" : "Unique Passive - Lifeline");
        add("passive.lolaccessories.shieldbow_lifeline.desc",
                chinese ? "在受到将使你的生命值跌到 %1$s 以下的伤害时，提供 %2$s 点持续 %3$s 秒的护盾（冷却 %4$s 秒）。"
                        : "Upon taking damage that would reduce your health below %1$s, gain a %2$s shield for %3$s seconds (cooldown %4$s seconds).");
                add("passive.lolaccessories.naavori_flicker.title",
                chinese ? "唯一被动—超凡入圣" : "Unique Passive - Transcendence");
        add("passive.lolaccessories.naavori_flicker.desc",
                chinese ? "攻击会使各基础技能的冷却时间缩短 %1$s 剩余冷却时间。"
                        : "Attacks reduce the remaining cooldowns of your basic abilities by %1$s.");
        add("passive.lolaccessories.collector_execute.title",
                chinese ? "唯一被动—死" : "Unique Passive - Death");
        add("passive.lolaccessories.collector_execute.desc",
                chinese ? "你的伤害会处决低于 %1$s 生命值的敌人。"
                        : "Your damage executes enemies below %1$s health.");
        add("passive.lolaccessories.collector_toll.title",
                chinese ? "唯一被动—税" : "Unique Passive - Toll");
        add("passive.lolaccessories.collector_toll.desc",
                chinese ? "击杀敌人时提供 %1$s 额外金币。"
                        : "Killing an enemy grants %1$s bonus gold.");
        add("passive.lolaccessories.ever_rising_moon.title",
                chinese ? "唯一被动—永升之月" : "Unique Passive - Ever Rising Moon");
        add("passive.lolaccessories.ever_rising_moon.desc",
                chinese ? "在 2 秒内用 2 次独立的攻击或技能命中敌人时，获得 %1$s（远程 %3$s）外加 %2$s（远程 %4$s）额外攻击力的护盾，持续 2 秒，并对目标造成 %5$s 最大生命值的额外物理伤害（冷却 %6$s 秒）。"
                        : "Hitting an enemy with 2 separate attacks or abilities within 2 seconds grants a %1$s (%3$s ranged) plus %2$s (%4$s ranged bonus AD) shield for 2 seconds and deals %5$s max health bonus physical damage (cooldown %6$s seconds).");
        add("passive.lolaccessories.shield_reaver.title",
                chinese ? "唯一被动—掠盾者" : "Unique Passive - Shield Reaver");
        add("passive.lolaccessories.shield_reaver.desc",
                chinese ? "对有护盾的目标造成的伤害提高 %1$s。"
                        : "Deal %1$s increased damage to targets with shields.");
                // 第十五批被动
        add("passive.lolaccessories.flux.title",
                chinese ? "唯一被动—涌动" : "Unique Passive - Flux");
        add("passive.lolaccessories.flux.desc",
                chinese ? "在你对一名敌方英雄造成伤害后，如果该英雄在 3 秒内阵亡，则你会在 2 秒内获得 500 终极技能急速。"
                        : "After damaging an enemy champion, if it dies within 3 seconds, gain 500 Ultimate Haste for 2 seconds.");
        add("passive.lolaccessories.notoriety.title",
                chinese ? "唯一被动—盛名" : "Unique Passive - Notoriety");
        add("passive.lolaccessories.notoriety.desc",
                chinese ? "如果一名在过去 3 秒内曾被你造成过伤害的英雄阵亡，则获得持续 90 秒的 12+3×已击杀英雄数的攻击力。"
                        : "If a champion damaged by you within the last 3 seconds dies, gain 12 plus 3 times the number of champions killed as attack damage for 90 seconds.");
        add("passive.lolaccessories.profane_hydra.cleave.title",
                chinese ? "唯一被动—顺劈" : "Unique Passive - Cleave");
        add("passive.lolaccessories.profane_hydra.cleave.desc",
                chinese ? "普通攻击会对附近的敌人们造成物理伤害。"
                        : "Basic attacks deal physical damage to nearby enemies.");
        add("passive.lolaccessories.heretical_cleave.title",
                chinese ? "唯一主动—邪斩" : "Unique Active - Heretical Cleave");
        add("passive.lolaccessories.heretical_cleave.desc",
                chinese ? "对你附近的敌人们造成物理伤害（冷却 10 秒，与九头蛇系列共用按键）。"
                        : "Deal physical damage to nearby enemies (10s cooldown, shared with Hydra items).");
        add("passive.lolaccessories.galvanize.title",
                chinese ? "唯一被动—通电/苍穹" : "Unique Passive - Galvanize/Firmament");
        add("passive.lolaccessories.galvanize.desc",
                chinese ? "攻击与技能命中获得盈能（移动也会充能），叠满 100 层后下一次攻击或技能命中造成相当于目标 9%%（近战）/7%%（远程）当前生命值的额外物理伤害（对野怪至多 200），并获得 15（近战）/12（远程）穿甲，持续 4 秒。"
                        : "Attacks and skill hits build Energized stacks (moving also charges). At 100 stacks, your next attack or skill hit deals bonus physical damage equal to 9%% (melee) / 7%% (ranged) of the target's current health (capped at 200 vs monsters) and grants 15 (melee) / 12 (ranged) Lethality for 4 seconds.");
        add("passive.lolaccessories.blight.title",
                chinese ? "唯一被动—恶劣衰朽" : "Unique Passive - Withering Blight");
        add("passive.lolaccessories.blight.desc",
                chinese ? "对英雄造成魔法伤害时，获得 7.5% 传说百分比法术穿透，持续 6 秒（至多叠加 4 层）。"
                        : "Dealing magic damage to a champion grants 7.5% legendary percent magic penetration for 6 seconds (stacks up to 4).");
        add("passive.lolaccessories.ruin.title",
                chinese ? "唯一被动—损毁" : "Unique Passive - Ruin");
        add("passive.lolaccessories.ruin.desc",
                chinese ? "使附近的敌方英雄承受 12% 额外魔法伤害。"
                        : "Nearby enemy champions take 12% increased magic damage.");
        // 第十六批被动
        add("passive.lolaccessories.bloodfeast.title",
                chinese ? "唯一被动—杀戮" : "Unique Passive - Slaughter");
        add("passive.lolaccessories.bloodfeast.desc",
                chinese ? "参与击杀英雄后获得 0.6% 全能吸血，可叠加至多 10 次。"
                        : "Takedowns grant 0.6% omnivamp, stacking up to 10 times.");
        add("passive.lolaccessories.swift_step.title",
                chinese ? "唯一被动—迅捷步" : "Unique Passive - Swift Step");
        add("passive.lolaccessories.swift_step.desc",
                chinese ? "将受到的减速效果的效能降低 25%。"
                        : "Reduces the effectiveness of slows by 25%.");
                add("passive.lolaccessories.now_to_forever.title",
                chinese ? "唯一被动—现在到永远" : "Unique Passive - Now and Forever");
        add("passive.lolaccessories.now_to_forever.desc",
                chinese ? "在一半生命值以上时，造成 4% 额外伤害；在一半生命值以下时，获得 12% 额外治疗值、护盾值和回复。"
                        : "Above half health, deal 4% bonus damage. Below half health, gain 12% increased healing, shielding and regeneration.");
        add("passive.lolaccessories.noxian_fervor.title",
                chinese ? "唯一被动—诺克萨斯的狂热" : "Unique Passive - Noxian Fervor");
        add("passive.lolaccessories.noxian_fervor.desc",
                chinese ? "获得相当于你 5% 移动速度加成的适应之力。"
                        : "Gain Adaptive Force equal to 5% of your bonus movement speed.");
        add("passive.lolaccessories.noxian_haste.title",
                chinese ? "唯一被动—诺克萨斯的急速" : "Unique Passive - Noxian Haste");
        add("passive.lolaccessories.noxian_haste.desc",
                chinese ? "施放技能或装备主动技后，获得持续 4 秒的 10%（近战）/8%（远程）移动速度。"
                        : "After casting a spell or gear active, gain 10% (melee) / 8% (ranged) Move Speed for 4 seconds.");
        add("passive.lolaccessories.noxian_persistence.title",
                chinese ? "唯一被动—诺克萨斯的不懈" : "Unique Passive - Noxian Persistence");
        add("passive.lolaccessories.noxian_persistence.desc",
                chinese ? "在受到来自英雄的魔法伤害后，获得一个持续 5 秒的魔法护盾（100-200 + 8% 额外生命值，冷却 15 秒）。"
                        : "After taking magic damage from a champion, gain a magic shield for 5 seconds (100-200 + 8% bonus health, 15s cooldown).");
        add("passive.lolaccessories.noxian_endurance.title",
                chinese ? "唯一被动—诺克萨斯的耐久" : "Unique Passive - Noxian Endurance");
        add("passive.lolaccessories.noxian_endurance.desc",
                chinese ? "在受到来自英雄的物理伤害后，获得一个持续 5 秒的物理护盾（100-200 + 8% 额外生命值，冷却 15 秒）。"
                        : "After taking physical damage from a champion, gain a physical shield for 5 seconds (100-200 + 8% bonus health, 15s cooldown).");
add("passive.lolaccessories.plated.title",
                chinese ? "唯一被动—镀板" : "Unique Passive - Plating");
        add("passive.lolaccessories.plated.desc",
                chinese ? "使即将到来的攻击（物理）伤害降低 10%。"
                        : "Reduces incoming attack (physical) damage by 10%.");
add("attribute.lolaccessories.adaptive_force",
                chinese ? "适应之力" : "Adaptive Force");
                add("attribute.lolaccessories.ability_haste",
                chinese ? "技能急速" : "Ability Haste");
        add("attribute.lolaccessories.ultimate_haste",
                chinese ? "终极技能急速" : "Ultimate Haste");
        add("attribute.lolaccessories.gear_haste",
                chinese ? "装备技能急速" : "Gear Haste");
add("attribute.lolaccessories.true_damage",
                chinese ? "真理伤害" : "True Damage");
        add("attribute.lolaccessories.true_damage_ap_ratio",
                chinese ? "真理伤害（法强加成）" : "True Damage (AP Scaling)");
        add("attribute.lolaccessories.true_damage_ad_ratio",
                chinese ? "真理伤害（攻击加成）" : "True Damage (AD Scaling)");
add("key.lolaccessories.shockwave",
                chinese ? "挺进破坏者：破阵冲击波" : "Stridebreaker: Breaking Shockwave");
        add("passive.lolaccessories.revved.title",
                chinese ? "唯一被动—充能" : "Unique Passive - Revved");
        add("passive.lolaccessories.revved.desc",
                chinese ? "命中敌人时释放积蓄的充能，额外造成 %1$s 点闪电魔法伤害；"
                                + "每 %2$s 秒至多触发一次。"
                        : "Hitting an enemy releases stored charge, dealing %1$s bonus lightning "
                                + "magic damage. Triggers at most once every %2$s seconds.");
        add("passive.lolaccessories.bullseye.title",
                chinese ? "唯一被动—牛眼" : "Unique Passive - Bullseye");
        add("passive.lolaccessories.bullseye.desc",
                chinese ? "精准命中敌人的要害，额外造成 %1$s 点闪电魔法伤害；"
                                + "每 %2$s 秒至多触发一次。"
                        : "A well-aimed hit finds the enemy's weak point, dealing %1$s bonus lightning "
                                + "magic damage. Triggers at most once every %2$s seconds.");
        add("passive.lolaccessories.rage.title",
                chinese ? "唯一被动—狂怒" : "Unique Passive - Rage");
        add("passive.lolaccessories.rage.desc",
                chinese ? "普通攻击命中敌方后，获得 %1$s 移动速度，持续 %2$s 秒。"
                        : "After attacking an enemy, gain %1$s bonus movement speed for %2$s seconds.");
        add("passive.lolaccessories.spellblade.title",
                chinese ? "唯一被动—咒刃" : "Unique Passive - Spellblade");
        add("passive.lolaccessories.spellblade.desc",
                chinese ? "施放技能后，你的下一次普通攻击附带相当于 %1$s 攻击力的额外物理伤害。"
                        : "After casting a spell, your next basic attack deals bonus physical "
                                + "damage equal to %1$s of your attack damage.");
        add("active.lolaccessories.titanic_hydra.titanic_crescent.title",
                chinese ? "唯一主动—刚斩" : "Unique Active - Titanic Crescent");
        add("active.lolaccessories.titanic_hydra.titanic_crescent.desc",
                chinese ? "强化接下来 10 秒内的下一次普攻：对主目标额外造成 %1$s 最大生命值物理伤害，并对其前方锥形范围内其他敌人造成 %2$s 最大生命值物理伤害。冷却时间：%4$s 秒。"
                        : "Empower your next basic attack within 10 seconds: deal bonus physical damage equal to %1$s maximum health to the primary target and %2$s maximum health to other enemies in a cone ahead of it. Cooldown: %4$s seconds.");
        add("passive.lolaccessories.cleave.title",
                chinese ? "唯一被动—顺劈" : "Unique Passive - Cleave");
        add("passive.lolaccessories.cleave.desc",
                chinese ? "普通攻击命中时会产生顺劈，对目标 %1$s 格内的其他敌人造成相当于你 %2$s 攻击力的物理伤害。"
                        : "Basic attacks cleave, dealing %2$s of your attack damage as physical damage "
                                + "to other enemies within %1$s blocks of the target.");
        add("passive.lolaccessories.terminus.title",
                chinese ? "唯一被动—交相" : "Unique Passive - Juxtaposition");
        add("passive.lolaccessories.terminus.desc",
                chinese ? "普攻额外造成 %1$s 点魔法伤害，外加 10% 额外攻击力与 10% 法术强度；交替获得光明（护甲和魔抗）或阴影（护甲穿透和法术穿透）层数，每种至多 %2$s 层，持续 %3$s 秒。"
                        : "Basic attacks deal %1$s bonus magic damage plus 10% bonus attack damage and 10% spell power, alternating Light (armor and magic resist) and Dark (armor and magic penetration) stacks, up to %2$s each for %3$s seconds.");
        add("passive.lolaccessories.essence_reaver.spellblade.desc",
                chinese ? "施放技能后，你 10 秒内的下一次普通攻击附带相当于 %1$s 基础攻击力、外加基于暴击率至多 50 点的额外物理伤害，并回复相当于该咒刃伤害 50% 的法力值。冷却 1.5 秒。"
                        : "After casting a spell, your next basic attack within 10 seconds deals bonus physical damage equal to %1$s base attack damage, plus up to 50 based on critical strike chance, and restores mana equal to 50% of that Spellblade damage. 1.5 second cooldown.");
        add("passive.lolaccessories.shipwrecker.title",
                chinese ? "唯一被动—沉船者" : "Unique Passive - Shipwrecker");
        add("passive.lolaccessories.shipwrecker.desc",
                chinese ? "移动时每 0.25 秒积攒 7 层动量，至多 %1$s 层。满层获得移速并留下红色拖尾；下一次普攻按当前动量额外造成至多 %2$s + %3$s 攻击力的物理伤害。"
                        : "Moving grants 7 Momentum every 0.25 seconds, up to %1$s stacks. At full stacks, gain movement speed and leave a red wake; your next basic attack deals up to %2$s plus %3$s attack damage as bonus physical damage based on current Momentum." );
        add("passive.lolaccessories.titanic_cleave.title",
                chinese ? "唯一被动—顺劈" : "Unique Passive - Cleave");
        add("passive.lolaccessories.titanic_cleave.desc",
                chinese ? "普攻对主目标额外造成 %2$s 最大生命值物理伤害，并对 %1$s 格内其他敌人造成 %3$s 最大生命值物理伤害。"
                        : "Basic attacks deal bonus physical damage equal to %2$s maximum health to the primary target and %3$s maximum health to other enemies within %1$s blocks.");
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
                                + "。"
                        : "The lower your health, the more attack damage you gain—up to %1$s of your "
                                + "current attack damage .");
        add("passive.lolaccessories.anguish.title",
                chinese ? "唯一被动—苦楚" : "Unique Passive - Anguish");
        add("passive.lolaccessories.anguish.desc",
                chinese ? "战斗中每 %2$s 秒，对 %3$s 格内的敌人造成相当于你额外生命值 %1$s 的邪术魔法伤害，"
                                + "并为你回复该伤害 %4$s 的生命值。"
                        : "While in combat, every %2$s seconds deal eldritch magic damage equal to %1$s of "
                                + "your maximum health to enemies within %3$s blocks, healing you for "
                                + "%4$s of the damage dealt.");
        add("passive.lolaccessories.baleful_blaze.title",
                chinese ? "唯一被动—不祥灼烧" : "Unique Passive - Baleful Blaze");
        add("passive.lolaccessories.baleful_blaze.desc",
                chinese ? "你的魔法伤害会灼烧目标，持续 %2$s 秒：每秒两跳，每跳造成 %1$s + %3$s×法术强度"
                                + " 的火焰魔法伤害；每名正在灼烧的敌人使你造成的魔法伤害提高 %4$s。"
                        : "Your magic damage burns the target for %2$s seconds: two ticks per second, "
                                + "each dealing %1$s + %3$s × spell power fire magic damage. Each burning "
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
        add("message.lolaccessories.boots_conflict",
                chinese ? "你已持有一双鞋，无法再锻造另一双鞋"
                        : "You already own a pair of boots and cannot forge another");
add("tooltip.lolaccessories.paid_smithing.title",
                chinese ? "锻造费用（取件时自动从背包与末影箱扣除）"
                        : "Smithing cost (auto-deducted from inventory & ender chest on pickup)");

        // ---------- 主动技能：时间停止（探索者的护臂 / 中娅沙漏） ----------
        // tooltip 条目（金色标题 + 灰色描述，由 GearItem 的 time_stop 分支渲染）
        add("active.lolaccessories.time_stop.title",
                chinese ? "唯一主动—时间停止" : "Unique Active - Time Stop");
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
                chinese ? "时间停止（探索者的护臂 / 中娅沙漏）" : "Time Stop (Seeker's Armguard / Zhonya's Hourglass)");
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
        add("skill.lolaccessories.mock_fate.cooldown",
                chinese ? "嘲弄命运冷却中：剩余 %1$s 秒。" : "Mock Fate on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.mock_fate.need_item",
                chinese ? "需要佩戴命运十面骰才能掷骰。" : "Equip Fate's Die to roll.");
        add("skill.lolaccessories.farewell_paradise.cooldown",
                chinese ? "再见桃花源冷却中：剩余 %1$s 秒。" : "Farewell Paradise on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.farewell_paradise.need_item",
                chinese ? "需要佩戴「%1$s」才能使用再见桃花源。"
                        : "You must be wearing %1$s to use Farewell Paradise.");
        add("skill.lolaccessories.farewell_paradise.start",
                chinese ? "再见桃花源！%1$s 个生物被抹杀，战利品翻倍。"
                        : "Farewell Paradise! %1$s creatures erased, loot doubled.");
        add("skill.lolaccessories.endless_grief.cooldown",
                chinese ? "此恨无绝冷却中：剩余 %1$s 秒。" : "Endless Grief on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.endless_grief.need_item",
                chinese ? "需要佩戴「%1$s」才能使用此恨无绝。"
                        : "You must be wearing %1$s to use Endless Grief.");
        add("skill.lolaccessories.endless_grief.start",
                chinese ? "此恨无绝！30 秒内，你的近战攻击将哀恸为虚空。"
                        : "Endless Grief! Your melee attacks mourn into the void for 30 seconds.");
        add("skill.lolaccessories.endless_grief.proc",
                chinese ? "此恨无绝！近战伤害化为虚空。"
                        : "Endless Grief! Melee damage turned void.");
        add("skill.lolaccessories.poem_for_tomorrow.creative",
                chinese ? "致明日之诗低语：游戏模式已切换为创造模式。"
                        : "The Poem for Tomorrow whispers: game mode set to Creative.");
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
                                + " %3$s×攻击力 + %4$s×法术强度 的神圣魔法伤害，并为你回复 %5$s×法术强度"
                                + " + %6$s×最大生命 的生命值。咒刃触发后，需再等 %2$s 秒才能由施法重新装填。"
                        : "After casting a spell, your next attack gains Spellblade for %1$s seconds: "
                                + "it deals %3$s × attack damage + %4$s × spell power bonus holy magic "
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
                                + "获得 %2$s 攻击速度，持续 %3$s 秒。冷却时间：%4$s 秒。"
                        : "After afflicting an enemy you recently damaged with a harmful effect, you "
                                + "gain %1$s movement speed and allies within %5$s blocks  gain %2$s attack speed for %3$s seconds. Cooldown: %4$s seconds.");
        // 原生质护带：救主灵刃
        add("passive.lolaccessories.protoplasm.title",
                chinese ? "唯一被动—救主灵刃" : "Unique Passive - Protoplasm");
        add("passive.lolaccessories.protoplasm.desc",
                chinese ? "当一次伤害将使你的生命值降至 %1$s 以下时，获得 %2$s~%3$s 点临时最大生命值，并在"
                                + " %6$s 秒内逐步回复 %4$s~%5$s 点生命；同时移动速度提升"
                                + " %8$s、韧性提升 %9$s。冷却时间：%7$s 秒。"
                        : "When damage would drop your health below %1$s, gain %2$s–%3$s temporary max "
                                + "health and recover %4$s–%5$s health over %6$s seconds , along with %8$s movement speed and %9$s tenacity. "
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
                chinese ? "获得 %1$s 终极技能冷却缩减。"
                        : "Grants %1$s ultimate cooldown reduction .");
        // 澄空之愿：唯一被动—澄澈天空（弹射物伤害概率转虚空/真实伤害）
        add("passive.lolaccessories.clear_sky.title",
                chinese ? "唯一被动—澄澈天空" : "Unique Passive - Clear Sky");
        add("passive.lolaccessories.clear_sky.desc",
                chinese ? "造成的所有弹射物伤害有 %1$s 的概率转化为§9虚空伤害§7，无视护甲与魔抗。"
                        : "All projectile damage you deal has a %1$s chance to become §9void damage§7, ignoring armor and magic resist.");
        add("active.lolaccessories.endless_grief.title",
                chinese ? "主动技能—此恨无绝" : "Active - Endless Grief");
        add("active.lolaccessories.endless_grief.desc",
                chinese ? "使用技能后 30 秒内，近战攻击有概率（暴击率 × 50%）整段转化为§9虚空伤害§7"
                                + "（无视护甲与魔抗），并按暴击伤害 × 80% 追加额外增伤。"
                                + "冷却 300 秒；每击杀 1 个敌对生物，减少 5 秒冷却。"
                        : "For 30 seconds after use, melee attacks have a chance (crit chance × 50%) to become"
                                + " §9void damage§7 (ignoring armor and magic resist) with bonus damage equal to"
                                + " crit damage × 80%. 300s cooldown; each hostile kill reduces it by 5s.");
        add("passive.lolaccessories.poem_of_truth.title",
                chinese ? "唯一被动—代行真理" : "Unique Passive - Poem of Truth");
        add("passive.lolaccessories.poem_of_truth.desc",
                chinese ? "攻击附带 %1$s 点§5真理伤害§7。"
                        : "Attacks carry %1$s points of §5True Damage§7.");
        add("passive.lolaccessories.poem_of_truth.creative.desc",
                chinese ? "装备于饰品栏后，将你的游戏模式切换为§a创造模式§7。"
                        : "While equipped, your game mode is switched to §aCreative§7.");
        add("passive.lolaccessories.new_clothes.title",
                chinese ? "唯一被动—皇帝的新衣" : "Unique Passive - The Emperor's New Clothes");
        add("passive.lolaccessories.new_clothes.desc",
                chinese ? "获得 %1$s 物理伤害减免与 %1$s 魔法伤害减免。看不见的衣服，只护得住看不见的人。"
                        : "Gain %1$s physical damage reduction and %1$s magic damage reduction. "
                                + "Clothes unseen protect only those who carry nothing.");
        add("passive.lolaccessories.i_want_for_nothing.title",
                chinese ? "唯一被动—我什么都不缺了" : "Unique Passive - I Want For Nothing");
        add("passive.lolaccessories.i_want_for_nothing.desc",
                chinese ? "每释放一次法术，获得相当于消耗法力 1% 的法术强度；法术每次命中，"
                                + "获得相当于 0.1% 最大生命值的最大法力值；每击杀一个敌对生物，"
                                + "获得相当于 1% 法强的最大生命值。累计永久保留，佩戴时生效。"
                        : "Each spell cast grants spell power equal to 1% of its mana cost; each spell hit "
                                + "grants max mana equal to 0.1% of your max health; each hostile kill grants "
                                + "max health equal to 1% of your spell power. Growth is permanent, active while worn.");
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
        add("passive.lolaccessories.vigor.title",
                chinese ? "唯一被动—无拘活力" : "Unique Passive - Boundless Vitality");
        add("passive.lolaccessories.vigor.desc",
                chinese ? "受到的所有治疗与护盾效果提升 %1$s。"
                        : "All healing and shields you receive are increased by %1$s.");
        add("passive.lolaccessories.sunfire.title",
                chinese ? "唯一被动—献祭" : "Unique Passive - Sunfire");
        add("passive.lolaccessories.sunfire.desc",
                chinese ? "进入战斗后，灼烧 %3$s 格内的敌人，每秒造成 %1$s 点外加 %2$s 额外生命值的火焰魔法伤害。"
                        : "While in combat, scorch enemies within %3$s blocks for %1$s plus %2$s bonus "
                                + "health fire magic damage per second.");
        add("passive.lolaccessories.remnant.title",
                chinese ? "唯一被动—灵液护盾" : "Unique Passive - Ichorshield");
        add("passive.lolaccessories.remnant.desc",
                chinese ? "生命值已满时溢出的治疗转化为血色护盾，护盾上限 %1$s 点，持续 %2$s 秒。"
                        : "Overflow healing at full health becomes a blood shield, up to %1$s, "
                                + "lasting %2$s seconds.");
        add("passive.lolaccessories.overdrive.title",
                chinese ? "唯一被动—过载" : "Unique Passive - Overdrive");
        add("passive.lolaccessories.overdrive.desc",
                chinese ? "施放终极技能后 %3$s 秒内获得 +%1$s 攻击速度与等额蓄力速度、+%2$s 移速。"
                                + "内部冷却：%4$s 秒。"
                        : "Casting an ultimate grants +%1$s attack speed, equal draw speed and "
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
        add("skill.lolaccessories.intervention.cooldown",
                chinese ? "降临冷却中：剩余 %1$s 秒。" : "Intervention on cooldown: %1$s s remaining.");
        add("skill.lolaccessories.intervention.need_item",
                chinese ? "需要佩戴「%1$s」才能使用降临。"
                        : "You must be wearing %1$s to use Intervention.");
        add("skill.lolaccessories.intervention.result",
                chinese ? "降临生效：治疗 %1$s 名友方（含自己），命中 %2$s 名敌人。"
                        : "Intervention resolved: healed %1$s ally(ies), struck %2$s enemy(ies).");
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
                chinese ? "每 8 秒获得一层充能，普攻或技能命中时消耗充能获得 %1$s 点额外法力，命中玩家时翻倍；叠满 360 额外法力后自动蜕变。"
                        : "Gain a charge every 8 seconds; attacks and spells consume a charge to grant %1$s bonus mana, doubled against champions. Transforms at 360 bonus mana.");
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
                chinese ? "普攻命中英雄时附加 1.2%% 最大法力的物理伤害；技能命中时附加 4%% 近战或 3%% 远程最大法力的物理伤害。"
                        : "Basic attacks deal 1.2%% max mana bonus physical damage; spells deal 4%% melee or 3%% ranged max mana bonus physical damage.");
        add("passive.lolaccessories.tear_lifeline.title",
                chinese ? "唯一被动—应急护盾" : "Unique Passive - Lifeline");
        add("passive.lolaccessories.tear_lifeline.desc",
                chinese ? "受到将使生命值低于 30%% 的伤害时，获得相当于 18%% 最大法力的护盾，持续 3 秒，冷却 90 秒。"
                        : "Upon taking damage that would bring you below 30%% health, gain a shield equal to 18%% max mana for 3 seconds. Cooldown: 90 seconds.");
        add("passive.lolaccessories.tear_everlasting.title",
                chinese ? "唯一被动—永恒" : "Unique Passive - Everlasting");
        add("passive.lolaccessories.tear_everlasting.desc",
                chinese ? "对敌人施加移动减速效果时，获得 100 + 4.5%% 最大法力的护盾，持续 3 秒，冷却 8 秒；附近有多名敌人时护盾提升 80%%。"
                        : "Slowing an enemy grants a shield of 100 + 4.5%% max mana for 3 seconds. Cooldown: 8 seconds; increased 80%% near multiple enemies.");
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
                chinese ? "弹射物命中时永久获得 0.4% 暴击率，至多 25%。"
                        : "Projectile hits permanently grant 0.4% crit chance, up to 25% .");
        add("passive.lolaccessories.yun_tal_flurry.title",
                chinese ? "唯一被动—疾风连射" : "Unique Passive - Flurry");
        add("passive.lolaccessories.yun_tal_flurry.desc",
                chinese ? "弹射物命中玩家时获得 30%% 蓄力速度，持续 6 秒，冷却 30 秒；弹射物命中使其缩短 1 秒。"
                        : "Hitting a player with a projectile grants 30%% draw speed for 6 seconds. Cooldown: 30 seconds; projectile hits shorten it by 1 second.");
        add("passive.lolaccessories.grievous_wounds_physical.title",
                chinese ? "唯一被动—重伤" : "Unique Passive - Grievous Wounds");
        add("passive.lolaccessories.grievous_wounds_physical.desc",
                chinese ? "物理伤害命中敌人后，使其受到的治疗降低 %1$s，持续 %2$s 秒。"
                        : "Physical damage  applies Grievous Wounds, "
                                + "reducing the target's incoming healing by %1$s for %2$s seconds.");
        add("passive.lolaccessories.giant_slayer.title",
                chinese ? "唯一被动—巨人杀手" : "Unique Passive - Giant Slayer");
        add("passive.lolaccessories.giant_slayer.desc",
                chinese ? "对非友善目标造成的伤害提升 0%~15%，基于目标额外生命值，至多基于 15000 点。"
                        : "Deal 0%~15% increased damage to non-friendly targets based on their bonus "
                                + "health, capped at 15,000.");
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
