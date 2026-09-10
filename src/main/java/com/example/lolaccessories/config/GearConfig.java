package com.example.lolaccessories.config;

import com.example.lolaccessories.LOLAccessories;
import com.google.common.collect.ArrayListMultimap;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/**
 * 单个装备的 JSON 配置映射。所有可调整数值都在这里声明，运行时由 {@link GearConfigManager} 加载。
 */
public class GearConfig {

    private static final Logger LOGGER = LOLAccessories.LOGGER;

    public String gear_id = "";
    public String slot = "curio";
    public int max_stack_size = 1;
    /**
     * 是否让佩戴者的法术伤害也参与 LOL 暴击。
     * 默认 false（法术不暴击）；个别「特殊装备」可在配置里置 true 开启。
     */
    public boolean spell_crit = false;
    /**
     * 装备所属「系列组」（如女神泪系 {@code tear_family}）：同组装备全局只允许佩戴一件。
     * 留空表示不参与系列互斥（即关闭该开关，整合包作者可自行编辑）。
     */
    public String family_group = "";
    /**
     * 蜕变目标 gear_id（女神泪系「法力流」叠满 360 额外法力后自动变身；留空表示不蜕变）。
     * 蜕变版装备应把本字段留空，并配置 {@code transform_from} 供退化检测。
     */
    public String transform_to = "";
    /** 蜕变前级 gear_id（蜕变版佩戴者叠层不足时退化回该装备；留空表示不可退化）。 */
    public String transform_from = "";
    public List<Attr> attributes = new ArrayList<>();
    public List<OnHitEffect> on_hit_effects = new ArrayList<>();

    public GearConfig fallback(String id) {
        this.gear_id = id;
        this.slot = "curio";
        this.max_stack_size = 1;
        this.attributes = new ArrayList<>();
        this.on_hit_effects = new ArrayList<>();
        return this;
    }

    public void resolve() {
        for (Attr attr : attributes) {
            attr.resolve();
        }
        if (max_stack_size < 1) {
            max_stack_size = 1;
        }
        if (gear_id == null || gear_id.isEmpty()) {
            gear_id = "unknown";
        }
        if (slot == null || slot.isEmpty()) {
            slot = "curio";
        }
    }

    public Optional<OnHitEffect> findEffect(String id) {
        for (OnHitEffect effect : on_hit_effects) {
            if (id.equals(effect.id)) {
                return Optional.of(effect);
            }
        }
        return Optional.empty();
    }

    public Multimap<Attribute, AttributeModifier> buildAttributeModifiers(UUID slotUuid) {
        Multimap<Attribute, AttributeModifier> result = ArrayListMultimap.create();
        for (Attr attr : attributes) {
            if (!attr.resolved()) {
                continue;
            }
            // 每个属性使用从栏位 UUID 派生的稳定 UUID，避免不同装备/属性之间冲突
            UUID modifierId = UUID.nameUUIDFromBytes((gear_id + ":" + attr.id + ":" + slotUuid).getBytes(StandardCharsets.UTF_8));
            result.put(attr.getAttribute(), new AttributeModifier(modifierId, gear_id + "/" + attr.id, attr.amount, attr.getOp()));
        }
        return result;
    }

    public static class Attr {
        public String id = "";
        public String operation = "ADDITION";
        public double amount = 0.0;
        /** 是否按百分比显示（如 +20%）；false 时显示数值（如 +600）。 */
        public boolean percent = false;

        private transient Attribute attribute;
        private transient AttributeModifier.Operation op = AttributeModifier.Operation.ADDITION;

        public void resolve() {
            op = parseOperation(operation);
            resolved();
        }

        /** 可选模组的属性命名空间别名（1.20.1 AttributesLib 曾用/正在用的两种前缀）。 */
        private static final String[][] NAMESPACE_ALIASES = {
                {"attributeslib", "apothic_attributes"},
                {"apothic_attributes", "attributeslib"},
        };

        /**
         * 属性 id 的改名迁移表（完整 {@code namespace:path} → 完整新 id）。
         * 本模组早前版本自建的属性已废弃，统一改为直接引用 Apothic Attributes（attributeslib）
         * 原生的同名/对应属性：lolaccessories:arrow_damage / projectile_damage → attributeslib:
         * arrow_damage（远程弹射伤害倍率）；lolaccessories:draw_speed → attributeslib:draw_speed
         * （远程武器蓄力速度，默认 1.0 = 100%，带原生效应）；lolaccessories:life_steal →
         * attributeslib:life_steal（生命偷取，由 Apothic 原生事件层结算）。老配置文件里仍引用
         * 旧 id 时，常规解析失败后会按此表迁移，保证旧配置无需手改即生效。
         */
        private static final Map<String, String> ID_MIGRATIONS = Map.of(
                "lolaccessories:arrow_damage", "attributeslib:arrow_damage",
                "lolaccessories:projectile_damage", "attributeslib:arrow_damage",
                "lolaccessories:draw_speed", "attributeslib:draw_speed",
                "lolaccessories:life_steal", "attributeslib:life_steal"
        );

        /**
         * 属性可能来自可选模组（如铁魔法、Apothic Attributes），其注册时机晚于本模组物品的注册。
         * 每次使用前自动重试解析，避免“先跳过、后补注册”导致加成永久丢失；
         * 未安装对应模组时返回 false，加成与 tooltip 行均不会出现。
         *
         * <p>对会随模组版本改名的命名空间（Apothic Attributes 在 1.20.1 用 {@code attributeslib}，
         * 较新版本改名 {@code apothic_attributes}）做<b>别名回退</b>：主 ID 找不到时自动尝试
         * 另一个命名空间的同名属性，保证配置里写哪个都能命中。</p>
         *
         * <p>别名回退仍找不到时，再按 {@link #ID_MIGRATIONS} 做旧配置的 id 改名迁移（迁移目标
         * 同样会走一次别名回退）。</p>
         */
        public boolean resolved() {
            if (attribute == null) {
                ResourceLocation loc = ResourceLocation.tryParse(id);
                if (loc == null) {
                    LOGGER.warn("[GearConfig] 属性 id 无法解析 '{}'，已跳过", id);
                    return false;
                }
                attribute = lookupWithNamespaceAlias(loc);
                if (attribute == null) {
                    String migratedFull = ID_MIGRATIONS.get(loc.toString());
                    if (migratedFull != null) {
                        ResourceLocation migrated = ResourceLocation.tryParse(migratedFull);
                        if (migrated != null) {
                            attribute = lookupWithNamespaceAlias(migrated);
                            if (attribute != null) {
                                LOGGER.info("[GearConfig] 属性 '{}' 已迁移为 '{}'，旧配置自动命中", id, migrated);
                            }
                        }
                    }
                }
                if (attribute == null) {
                    LOGGER.debug("[GearConfig] 属性 '{}' 当前未注册（可能缺少可选模组），稍后自动重试", id);
                }
            }
            return attribute != null;
        }

        /** 先按原 id 查注册表；miss 时按 {@link #NAMESPACE_ALIASES} 尝试另一命名空间的同名属性。 */
        private static Attribute lookupWithNamespaceAlias(ResourceLocation loc) {
            Attribute result = BuiltInRegistries.ATTRIBUTE.get(loc);
            if (result == null) {
                for (String[] aliasPair : NAMESPACE_ALIASES) {
                    if (loc.getNamespace().equals(aliasPair[0])) {
                        ResourceLocation alt = new ResourceLocation(aliasPair[1], loc.getPath());
                        result = BuiltInRegistries.ATTRIBUTE.get(alt);
                        if (result != null) {
                            LOGGER.debug("[GearConfig] 属性 '{}' 未注册，别名 '{}' 命中，已自动映射", loc, alt);
                        }
                        break;
                    }
                }
            }
            return result;
        }

        public Attribute getAttribute() {
            return attribute;
        }

        public AttributeModifier.Operation getOp() {
            return op;
        }

        public boolean isPercentage() {
            return percent || op != AttributeModifier.Operation.ADDITION;
        }

        public Component formatLine() {
            return formatLine(ChatFormatting.GRAY);
        }

        /** 属性行（数值绿/红 + 属性名按 nameColor 着色，供传说/神话装备差异化 tooltip 配色）。 */
        public Component formatLine(ChatFormatting nameColor) {
            Component value = formatAmount(amount, isPercentage());
            return value.copy()
                    .append(" ")
                    .append(Component.translatable(attribute.getDescriptionId()).withStyle(nameColor));
        }

        private static AttributeModifier.Operation parseOperation(String value) {
            if (value == null) {
                return AttributeModifier.Operation.ADDITION;
            }
            String upper = value.toUpperCase(Locale.ROOT);
            try {
                return AttributeModifier.Operation.valueOf(upper);
            } catch (IllegalArgumentException ignored) {
                try {
                    int ordinal = Integer.parseInt(upper);
                    return AttributeModifier.Operation.values()[ordinal];
                } catch (NumberFormatException | ArrayIndexOutOfBoundsException alsoIgnored) {
                    LOGGER.warn("[GearConfig] 未知操作 '{}'，回退为 ADDITION", value);
                    return AttributeModifier.Operation.ADDITION;
                }
            }
        }

        private static Component formatAmount(double amount, boolean percentage) {
            String raw = formatNumber(amount * (percentage ? 100.0 : 1.0));
            String text = (amount >= 0 ? "+" : "") + raw + (percentage ? "%" : "");
            return Component.literal(text).withStyle(amount >= 0 ? ChatFormatting.GREEN : ChatFormatting.RED);
        }

        private static String formatNumber(double value) {
            // 先做两位小数的舍入，避免 0.2*100 这类浮点误差导致 “20.0%”
            double rounded = Math.round(value * 100.0) / 100.0;
            double abs = Math.abs(rounded);
            if (abs == Math.floor(abs)) {
                return String.valueOf((long) abs);
            }
            return String.format(Locale.ROOT, "%.1f", abs);
        }
    }

    public static class OnHitEffect {
        public String id = "";
        public String target = "victim";
        public boolean enabled = true;

        // cleaver_shred
        public double per_stack = 0.0;
        public int max_stacks = 1;
        public double max_total = 1.0;

        // cleaver_rush
        public double amount = 0.0;

        // 共用
        public double duration_seconds = 0.0;

        // clear_sky（澄澈天空：澄空之愿）
        /** 弹射物伤害转化为虚空伤害的概率（0.5 = 50%）。 */
        public double chance = 0.0;

        // echo（回声，铁魔法联动技能，见 ludens_echo.json；不会在普通命中里生效，由 IronMagicEvents 处理）
        /** 回声基础弹道数；每有 100% 末影法术强度会额外 +1 道。 */
        public double echo_count = 6.0;
        public double cooldown_seconds = 12.0;
        /** 主目标周围多少格内的敌对生物会被回声索敌。 */
        public double radius_blocks = 3.0;
        public double base_damage = 0.0;
        /** 每道全额回声伤害 = base_damage + power_ratio × 末影法术强度（百分数，1.0=100%）。 */
        public double power_ratio = 0.0;
        /** 敌对生物不足时，折返主目标的回声每道造成的伤害比例（0.2 = 20%）。 */
        public double bonus_pct = 0.0;

        // helping_hand（帮助之手：多兰盾/多兰戒/多兰盔/女神之泪共用，只生效一次）
        /** 对「生命值低于 health_threshold」的目标额外造成的物理伤害点数。 */
        public double bonus_damage = 0.0;
        /** 帮助之手的生命值阈值（默认 100，即满 10 颗心以下的目标）。 */
        public double health_threshold = 100.0;

        // perseverance（耐久专注：多兰盾，受伤后自然恢复翻倍窗口）
        // amount = 窗口期间额外追加的自然恢复量（与装备静态加成相同 → 即翻倍）；
        // duration_seconds = 窗口时长。

        // mana_restore（回复力：多兰戒）
        // amount = 每秒回复法力值；duration_seconds = 造成魔法伤害后效果翻倍的窗口时长。

        // mana_flow（法力流：女神之泪）
        // amount = 每次魔法命中增加的最大法力值；max_total = 加成上限。

        // glory（荣耀：黑暗封印）
        // amount = 每层提供的法术强度（百分数，0.04 = +4%）；max_stacks = 层数上限。
        /** 击杀合格生物一次获得的层数。 */
        public double kill_stacks = 0.0;
        /** 佩戴者死亡时损失的层数。 */
        public double death_loss = 0.0;
        /** 计入击杀的门槛：目标最大生命值 > 佩戴者最大生命值 × 该倍数。 */
        public double kill_health_ratio = 2.0;

        // lifeline（生命残片：海克斯饮魔刀）
        /** 护盾吸收的伤害点数。 */
        public double shield_amount = 0.0;
        /** 触发护盾的生命百分比线（0.30 = 生命值低于 30%）。 */
        public double trigger_health_percent = 0.0;

        // rock_solid（磐石：守望者铠甲）
        /** 每次被攻击命中时固定减免的伤害点数（15 = 15 点）。 */
        public double reduction_flat = 0.0;
        /** 单次减免不超过所受攻击伤害的比例（0.2 = 20%）。 */
        public double reduction_cap_pct = 0.0;

        // ---- 传说（3 级）系列新增字段 ----
        // tyranny（暴政：霸王血铠）：amount = 攻击伤害加成比例，加成 = 最大生命 × amount（0.025 = 2.5%，实时挂属性）。
        // retribution（报应：霸王血铠）：amount = 已损失生命加成系数，加成 = 已损比例 × amount ×
        //     （当前总攻击伤害 − 报应自身），0.12 = 12%，实时挂属性（不含本被动自身，避免回灌）。
        // inspiring_speech（鼓舞：舒瑞娅的战歌·主动）：amount = 移速提升比例（0.30 = +30%）；
        //     duration_seconds = 持续秒数；cooldown_seconds = 冷却秒数；radius_blocks = 作用半径（格）。

        // anguish（苦楚：无终恨意·周期性脉冲）
        /** 以佩戴者当前最大生命为基数的效果量（无终脉冲伤害 0.03 = 3%；败魔护盾 0.15 = 15%）。 */
        public double max_health_pct = 0.0;
        /** 两次触发的间隔秒数（无终苦楚 4；败魔法师之祸再充能 15）。 */
        public double interval_seconds = 0.0;
        /** 对本次造成伤害按比例回血（倍率语义，2.5 = 回复伤害的 250%）。 */
        public double heal_pct = 0.0;
        /** 周期效果要求进入战斗才发动的战斗窗口秒数（0 = 不限制）。 */
        public double combat_window_seconds = 0.0;

        // baleful_blaze（灼烧：黯炎火炬）
        /** 灼烧每跳的基础伤害点数。 */
        public double burn_tick_damage = 0.0;
        /** 灼烧每跳按法术强度加成的比例（0.01 = 每跳 +1% 法强，法强按通用法术强度属性净值折算）。 */
        public double burn_ap_ratio = 0.0;
        /** 每名正处于本装备灼烧中的敌人为本装备佩戴者提供的法强比例（0.04 = 每目标 +4%）。 */
        public double ap_pct_per_target = 0.0;

        // ---- 2026 海克斯赛季传说新增字段 ----
        // spellblade（咒刃：黄昏与黎明）duration_seconds = 强化窗口秒数；cooldown_seconds = 咒刃自冷却
        /** 咒刃额外魔法伤害 = 攻击力 × ad_ratio（0.75）。 */
        public double ad_ratio = 0.0;
        /** 咒刃额外魔法伤害另加 = 法术强度净值 × ap_ratio（0.10；法强按通用法术强度属性净值折算）。 */
        public double ap_ratio = 0.0;
        /** 咒刃命中治疗 = 法术强度净值 × heal_ap_ratio（0.10）。 */
        public double heal_ap_ratio = 0.0;
        /** 咒刃命中治疗另加 = 总最大生命 × heal_hp_ratio（0.03）。 */
        public double heal_hp_ratio = 0.0;

        // barrage（开战弹幕：猎魔人弩箭）amount = 可覆盖攻击次数；duration_seconds = 窗口秒数
        /** 弹幕命中强制暴击时按「常规暴击伤害 × crit_fraction」结算（0.8 = 80% 常规暴击伤害）。 */
        public double crit_fraction = 0.0;
        /** 若本次本应暴击则改为附加的额外真实伤害比例（0.15 = 15% × 原伤害）。 */
        public double crit_true_ratio = 0.0;

        // shaped_charge（成型炸药：破垒者）cooldown_seconds = 20；base_damage = 真伤基础值
        /** 炸药真伤按佩戴者穿甲点数 × armor_pierce_scale 追加（1.5）。 */
        public double armor_pierce_scale = 0.0;

        // famine（饥馑：无穷饥渴）amount = 基础冷却缩减点数（0.05 = +5% 冷却缩减）
        /** 每点攻击力转换冷却缩减的近战比例（0.13）。 */
        public double melee_ratio = 0.0;
        /** 每点攻击力转换冷却缩减的远程比例（0.10）。 */
        public double ranged_ratio = 0.0;
        // feast（盛宴：无穷饥渴）duration_seconds = 全能吸血持续秒数
        /** 判定「近期伤害过目标」的窗口秒数（3 秒）。 */
        public double damage_window_seconds = 0.0;
        /** 盛宴期间追加的全能吸血比例（0.15）。 */
        public double omnivamp_ratio = 0.0;

        // long_shot（高倍望远镜：海克斯镜片）amount = 满距离最大增伤（0.10）
        /** 增伤达到最大值所需命中距离（格，50）。 */
        public double distance_blocks = 0.0;

        // realize（法力成真：实现器·主动）duration_seconds = 5；cooldown_seconds = 180（不随冷却缩减）
        /** 激活期间施放法术的魔力消耗倍率（2 = 翻倍）。 */
        public double mana_cost_multiplier = 0.0;

        // fanfare（嘹亮旋律：班德尔音管）cooldown_seconds = 30；duration_seconds = 持续；radius_blocks = 半径
        /** Fanfare 授予自身的移速比例（0.20）。 */
        public double move_speed_ratio = 0.0;
        /** Fanfare 授予自身与友军的攻速比例（0.30）。 */
        public double attack_speed_ratio = 0.0;

        // protoplasm（救主灵刃：原生质护带）trigger_health_percent = 0.30；cooldown_seconds = 90
        /** 救主灵刃临时最大生命下限/上限（按玩家等级插值，默认 100 / 300）。 */
        public double health_min = 0.0;
        public double health_max = 0.0;
        /** 救主灵刃 5 秒治疗总量下限/上限（按玩家等级插值，默认 100 / 400）。 */
        public double heal_min = 0.0;
        public double heal_max = 0.0;
        /** 救主灵刃触发期间的韧性加成比例（0.25）。 */
        public double tenacity_ratio = 0.0;

        // ---- 2026 传说第 2 批新增字段 ----
        // lifeline（救主灵刃：斯特拉克的挑战护手）复用 trigger_health_percent / shield_amount /
        //     duration_seconds / cooldown_seconds；attack_flat = 触发期间额外攻击力（MC 基础攻击
        //     只有 1 点，故按用户要求改为固定 +50 点攻击力的怒火增益）。
        /** 救主灵刃触发期间的额外攻击力点数（0 = 不加攻击力）。 */
        public double attack_flat = 0.0;

        // glory（荣耀：黑暗封印/梅贾的窃魂卷共享层数池）——梅贾专属：
        /** 移速加成要求的最低层数（10 层以上提供 move_speed_ratio 移速）。 */
        public double move_speed_stack_threshold = 0.0;
        // 移速比例复用上方 fanfare 的 move_speed_ratio 字段。

        // zeal（聚合风暴：基克的聚合）
        /** 积累多少次攻击命中后释放聚合风暴（8）。 */
        public double charge_hits = 0.0;
        /** 风暴持续时间秒数（用于特效窗口）。 */
        // duration_seconds / radius_blocks / base_damage / cooldown_seconds 复用

        // sunfire（献祭：日炎圣盾）——interval_seconds = 灼烧间隔；radius_blocks = 半径；
        //     base_damage = 每跳基础魔法伤害；max_health_pct = 每跳按最大生命加成比例（0.01）。

        // remnant（余烬：饮血剑）——shield_amount = 溢出吸血可转化的护盾上限；
        //     duration_seconds = 护盾持续秒数（到时缓慢清空由到期机制处理）。

        // supernova（超新星：海克斯注力刚壁·主动）——base_damage = 新星魔法伤害基础值；
        //     power_ratio = 按通用法术强度净值的加成比例（0.15）；radius_blocks = 新星半径；
        //     cooldown_seconds = 40；dash_power = 冲刺初速度（1.6）。
        /** 冲刺初速度（格/tick，沿视角方向）。 */
        public double dash_power = 0.0;

        // demon_king（我是大魔王：魔王之心）——装备全部属性按携带的诅咒/增益动态乘区。
        /** 每个诅咒附魔或负面药水效果提供的属性提升比例（0.10 = +10%，加算累加）。 */
        public double curse_ratio = 0.0;
        /** 每个正面附魔或正面药水效果带来的属性降低比例（0.15 = -15%，加算累加、可与提升抵消）。 */
        public double buff_ratio = 0.0;
    }
}
