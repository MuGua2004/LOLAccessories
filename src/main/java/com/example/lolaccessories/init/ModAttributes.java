package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.MobCategory;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.RangedAttribute;
import net.minecraftforge.event.entity.EntityAttributeModificationEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 本模组「LOL 独立数值」属性注册。
 *
 * <p>这两个属性是真正注册到 Forge {@code ForgeRegistries.ATTRIBUTES} 的属性，任何能识别
 * 原版属性的模组（属性显示、Curios 等）都能读取与展示；资源名（{@code lolaccessories:crit_chance}
 * 与 {@code lolaccessories:crit_damage}）自带命名空间，不会与其它模组冲突。属性在中文客户端的
 * 显示名为「传说暴击率」「传说暴击伤害」（见语言文件），刻意与常见的“暴击率/暴击伤害”中文
 * 汉化错开，避免被其它模组或汉化包的同名词条顶掉而无法区分。</p>
 *
 * <p>语义约定（数值存储与展示均为「百分比语义」）：</p>
 * <ul>
 *   <li>{@link #LOL_CRIT_CHANCE}：传说暴击率，0.0 ~ 1.0（0% ~ 100%），默认 0。RangedAttribute
 *       的上限 1.0 即「暴击率至多 100%」的硬性封顶；装备用 ADDITION 修正器往上加，面板永远 ≤100%。</li>
 *   <li>{@link #LOL_CRIT_DAMAGE}：传说暴击伤害，最终伤害的独立乘区倍率，默认 1.5（150%），
 *       即无任何加成时暴击造成「原伤害 × 150%」；装备以「暴击伤害 +X%」累加到该值上。</li>
 * </ul>
 */
public final class ModAttributes {

    /** 暴击伤害的基础默认倍率 = 150%（独立乘区，作用于最终伤害）。 */
    public static final double DEFAULT_CRIT_DAMAGE = 1.5D;

    public static final DeferredRegister<Attribute> ATTRIBUTES =
            DeferredRegister.create(ForgeRegistries.ATTRIBUTES, LOLAccessories.MOD_ID);

    /** 传说暴击率：0~1，封顶 100%。溢出到 100% 之外的部分由暴击结算时按规则转化为暴击伤害。 */
    public static final RegistryObject<Attribute> LOL_CRIT_CHANCE =
            ATTRIBUTES.register("crit_chance", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".crit_chance",
                    0.0D, 0.0D, 1.0D).setSyncable(true));

    /** 传说暴击伤害：默认 1.5（150%），装备加成直接加到这个最终倍率上。 */
    public static final RegistryObject<Attribute> LOL_CRIT_DAMAGE =
            ATTRIBUTES.register("crit_damage", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".crit_damage",
                    DEFAULT_CRIT_DAMAGE, 1.0D, 100.0D).setSyncable(true));

    /**
     * 传说魔法抗性：数值型抗性点数（与原版铁魔法百分比魔抗相独立）。
     *
     * <p>原版没有“魔法抗性”概念，铁魔法只有百分比魔抗（spell_resist），而抗魔斗篷这类
     * 装备要的是 LoL 式的“数值法抗”。因此本模组自建该属性，数值直接取「抗性点数」，
     * 减免直接套用 LoL 公式：{@code 减免 = MR / (100 + MR)}。对象：原版的魔法伤害
     * （伤害类型 magic / indirect_magic）以及铁魔法的法术伤害。</p>
     */
    public static final RegistryObject<Attribute> LOL_MAGIC_RESIST =
            ATTRIBUTES.register("magic_resist", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".magic_resist",
                    0.0D, 0.0D, 1000.0D).setSyncable(true));

    /**
     * 法术穿透（固定值）：对铁魔法法术伤害生效的固定点数法穿。
     *
     * <p>分两个结算层生效：</p>
     * <ul>
     *   <li>对<b>传说魔法抗性</b>{@link #LOL_MAGIC_RESIST}：仍按点数全额扣减
     *       （有效魔抗 = MR × (1 − pct) − flat，见 LolDefenseEvents）。</li>
     *   <li>对<b>原版护甲</b>：不再全额生效——只按 30% 效力扣减护甲点数
     *       （法穿被用于“压过”原版物理护甲层时效率大幅削弱，见
     *       LivingEntityMagicPenMixin 的换算）。固穿不削减护甲韧性，也无法削减
     *       铁魔法学派魔抗（学派层只有百分比法穿能穿透）。</li>
     * </ul>
     * <p>存储语义为点数：+5 = amount 5.0。</p>
     */
    public static final RegistryObject<Attribute> LOL_MAGIC_PEN =
            ATTRIBUTES.register("magic_pen", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".magic_pen",
                    0.0D, 0.0D, 1000.0D).setSyncable(true));

    /**
     * 百分比法术穿透：按百分比无视目标的多层防御。
     *
     * <p>百分比语义存储与展示（0~1，+40% = 0.40）。分两个结算层生效：</p>
     * <ul>
     *   <li>对<b>传说魔法抗性</b>与<b>铁魔法学派魔抗</b>：维持全额削减不变
     *       （有效值 = 原值 × (1 − pct)，IronsSpellSchoolPen / LolDefenseEvents）。</li>
     *   <li>对<b>原版护甲</b>：不再全额生效——对护甲值只按 30% 效力、对护甲韧性只按
     *       10% 效力折算（见 LivingEntityMagicPenMixin）。</li>
     * </ul>
     * <p>先于固定法穿结算（有效值 = 原值 × (1 − pct) − flat）。</p>
     */
    public static final RegistryObject<Attribute> LOL_MAGIC_PEN_PERCENT =
            ATTRIBUTES.register("magic_pen_percent", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".magic_pen_percent",
                    0.0D, 0.0D, 1.0D).setSyncable(true));

    /**
     * 自然生命恢复：治疗宝珠等装备的「+X% 自然生命恢复」属性。
     *
     * <p>原版没有任何能直接控制“自然生命恢复”的属性，因此自建本属性；语义为「倍率」：
     * 值 1.0 = +100%（自然回血翻倍，事件层在玩家满足自然回血条件时把回血量加上该值）。
     * 默认 0 = 无加成。</p>
     */
    public static final RegistryObject<Attribute> LOL_NATURAL_REGEN =
            ATTRIBUTES.register("natural_regen", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".natural_regen",
                    0.0D, 0.0D, 10.0D).setSyncable(true));

    /**
     * 治疗与护盾强度（Heal &amp; Shield Power）：倍率语义的属性。
     *
     * <p>面向玩家输出的治疗与护盾效果：本模组事件层在处理我们自身给出的治疗（如万世催化石
     * 的永恒回血）与护盾（生命提升药水效果形态，如海克斯饮魔刀的生命残片）时，把数值乘以
     * {@code 1 + 该属性净加成}（如禁忌雕像 +8% → amount 0.08）。语义与{@link #LOL_NATURAL_REGEN}
     * 一致按「倍率」存储与展示：0.08 = +8%。上限给到 10.0（+1000%）避免误配置爆值。</p>
     *
     * <p>收到治疗提升（attributeslib 的 healing_received）由 Apothic Attributes 自行结算，
     * 本模组不干预，后续传说装备需要时直接引用那个属性即可。</p>
     */
    public static final RegistryObject<Attribute> LOL_HEAL_POWER =
            ATTRIBUTES.register("heal_power", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".heal_power",
                    0.0D, 0.0D, 10.0D).setSyncable(true));

    /**
     * 传说韧性：对目标施加到玩家身上的负面药水效果时长做百分比减免（0.25 = -25%）。
     *
     * <p>原版/铁魔法/史诗战斗/Apothic 均无现成韧性属性，因此自建。语义为「时长减免倍率」，
     * 存储 0 ~ 1（0 = 无减免，1 = 全部免控）；生效链路：任何给玩家挂负面药水效果的来源在
     * 施加成功时，把效果的剩余时长乘以 {@code (1 - 净韧性)}（见
     * {@code com.example.lolaccessories.event.LolTenacityEvents}）。</p>
     */
    public static final RegistryObject<Attribute> LOL_TENACITY =
            ATTRIBUTES.register("tenacity", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".tenacity",
                    0.0D, 0.0D, 1.0D).setSyncable(true));

    /**
     * 全能吸血（Omnivamp）：玩家造成的<b>所有类型</b>伤害（近战/远程/法术/持续/真伤）都按该比例回血。
     *
     * <p>参考英雄联盟的全能吸血；与 Apothic 的生命偷取（attributeslib:life_steal，仅近战直击由
     * Apothic 原生结算）是两个独立的回复来源，可以同时存在并各自结算。存储与展示为百分比语义
     * （0.08 = +8%），上限 1.0 = 100%。结算见 {@code event.LolOmnivampEvents}。</p>
     */
    public static final RegistryObject<Attribute> LOL_OMNIVAMP =
            ATTRIBUTES.register("omnivamp", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".omnivamp",
                    0.0D, 0.0D, 1.0D).setSyncable(true));

    /**
     * 终极技能冷却缩减（Ultimate Cooldown Reduction）：只缩短铁魔法终极技能（基础法力消耗
     * 严格大于 200 的卷轴/法书法术）的冷却时间。
     *
     * <p>语义与普通冷却缩减一致（0.30 = -30%，原作按「终极技能急速」表述：+30 急速即记 0.30，
     * 遵循本模组 +10 急速 = +10% 冷却缩减的折算，不再按比例缩减）。在 ISS 冷却结算时作为
     * <b>独立乘区</b>在普通冷却缩减之后再乘一次：
     * {@code 最终冷却 = 有效冷却 × (1 − 终极冷却缩减)}。存储与展示为百分比语义。结算见
     * {@code compat.IronsUltimateHasteEvents}。</p>
     */
    public static final RegistryObject<Attribute> LOL_ULTIMATE_CDR =
            ATTRIBUTES.register("ultimate_cdr", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".ultimate_cdr",
                    0.0D, 0.0D, 1.0D).setSyncable(true));

    /**
     * 物理伤害百分比减免：对非魔法类伤害（近战/远程/爆炸/坠落等，不含虚空/真实伤害）按比例减免。
     *
     * <p>百分比语义存储与展示（0.2 = -20%）；作为<b>独立乘区</b>在目标受到伤害时折算
     * （伤害 × (1 − 减免)），与护甲、魔法抗性、其他减伤互不干扰。默认 0，暂无装备引用，供后续
     * 装备使用。结算见 {@code event.LolDefenseEvents}。</p>
     */
    public static final RegistryObject<Attribute> LOL_PHYSICAL_DAMAGE_REDUCTION =
            ATTRIBUTES.register("physical_damage_reduction", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".physical_damage_reduction",
                    0.0D, 0.0D, 1.0D).setSyncable(true));

    /**
     * 魔法伤害百分比减免：对魔法类伤害（原版 magic/indirect_magic + 铁魔法法术伤害）按比例减免。
     *
     * <p>百分比语义存储与展示（0.2 = -20%）；作为<b>独立乘区</b>在目标受到魔法伤害时、于传说
     * 魔法抗性折算之后再乘一次。默认 0，暂无装备引用，供后续装备使用。结算见
     * {@code event.LolDefenseEvents}。</p>
     */
    public static final RegistryObject<Attribute> LOL_MAGIC_DAMAGE_REDUCTION =
            ATTRIBUTES.register("magic_damage_reduction", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".magic_damage_reduction",
                    0.0D, 0.0D, 1.0D).setSyncable(true));

    /**
     * 适应之力（Adaptive Force）：点数语义的动态属性。
     *
     * <p>结算规则（每 0.5 秒重算一次，见 {@code event.LolAdaptiveForceEvents}）：</p>
     * <ul>
     * <li>比较佩戴者当前<b>装备加成部分</b>的三项面板——攻击力（点数）、法术强度
     *     （百分比 × 100 化为点数）、弹射物伤害（百分比 × 100 化为点数）；
     *     百分比形式的换算即「先化成小数形式再乘 100」（如 +45% 法术强度 → 45 点）。</li>
     * <li>把适应之力数值<b>加到最高的一项</b>上：加给攻击力时为点数直加，
     *     加给法术强度/弹射物伤害时按百分比语义折算（+15 适应之力 → +0.15）。</li>
     * </ul>
     * <p>存储语义为点数：+15 = amount 15.0。</p>
     */
    public static final RegistryObject<Attribute> LOL_ADAPTIVE_FORCE =
            ATTRIBUTES.register("adaptive_force", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".adaptive_force",
                    0.0D, 0.0D, 1000.0D).setSyncable(true));

    /**
     * 真理伤害（True Damage）基础点数：攻击命中时额外造成的真理伤害固定值。
     *
     * <p>真理伤害是<b>独立的伤害来源</b>（类似攻击伤害），不从普通伤害转换。攻击命中实体时
     * 按下式结算（见 {@code event.LolTrueDamageEvents}）：</p>
     * <pre>真理伤害 = 真理伤害点数 + 法强加成比例 × 法术强度 + 攻击加成比例 × 攻击力</pre>
     * <ul>
     * <li><b>不可阻隔</b>：在伤害事件最早期直接结算生效，不进入护甲/魔抗/护盾/闪避/
     *     免疫等任何减免与拦截流程（即使普通伤害被完全挡下，真理伤害仍然生效）；</li>
     * <li><b>直削最大生命值</b>：按真理伤害数值永久削减目标的最大生命值
     *     （属性修正器随实体 NBT 持久化）；</li>
     * <li><b>静滞</b>：受到真理伤害的目标被静滞——生物永久抹除 AI（不再思考/索敌/行动），
     *     玩家被短暂压制无法行动；</li>
     * <li><b>抹除</b>：生物最大生命值因此归零时被直接从游戏中移除（不触发死亡事件，
     *     无掉落无经验），且该类型生物在本存档中将被禁止再生成；</li>
     * <li><b>玩家特例</b>：单人存档中玩家不会被抹除（最大生命值至多削减到 1 点，可正常
     *     复活）；多人服务器中最大生命值归零的玩家会被踢出并删除其玩家数据。</li>
     * </ul>
     */
    public static final RegistryObject<Attribute> LOL_TRUE_DAMAGE =
            ATTRIBUTES.register("true_damage", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".true_damage",
                    0.0D, 0.0D, 10000.0D).setSyncable(true));

    /** 真理伤害的法强加成比例：0.30 = 额外造成 30% 法术强度的真理伤害。 */
    public static final RegistryObject<Attribute> LOL_TRUE_DAMAGE_AP_RATIO =
            ATTRIBUTES.register("true_damage_ap_ratio", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".true_damage_ap_ratio",
                    0.0D, 0.0D, 10.0D).setSyncable(true));

    /** 真理伤害的攻击力加成比例：0.30 = 额外造成 30% 攻击力的真理伤害。 */
    public static final RegistryObject<Attribute> LOL_TRUE_DAMAGE_AD_RATIO =
            ATTRIBUTES.register("true_damage_ad_ratio", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".true_damage_ad_ratio",
                    0.0D, 0.0D, 10.0D).setSyncable(true));

    /**
     * 技能急速（Ability Haste）：点数语义，按英雄联盟官方公式换算冷却缩减：
     * {@code 冷却缩减 = 急速 / (100 + 急速)}（100 急速 = 50%、200 = 66.7%、300 = 75%）。
     *
     * <p>作为<b>独立乘区</b>作用于铁魔法法术冷却（见 {@code compat.IronsAbilityHasteEvents}）：
     * {@code 最终冷却 = ceil(ISS 有效冷却 × (1 − 急速换算缩减))}，与铁魔法自身的冷却缩减属性、
     * 法杖修正等构成独立乘区，与本模组终极技能急速（对终极技能）再构成独立乘区。</p>
     *
     * <p>不作用于模组装备的主动技能冷却——那是 {@link #LOL_GEAR_HASTE} 的领域。</p>
     */
    public static final RegistryObject<Attribute> LOL_ABILITY_HASTE =
            ATTRIBUTES.register("ability_haste", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".ability_haste",
                    0.0D, 0.0D, 1000.0D).setSyncable(true));

    /**
     * 终极技能急速（Ultimate Haste）：点数语义，换算公式同英雄联盟官方
     * （{@code CDR = 急速 / (100 + 急速)}），只作用于铁魔法<b>终极技能</b>（基础法力消耗
     * 高于门槛的卷轴/法书）的冷却，作为技能急速乘区之后的<b>再一个独立乘区</b>。
     *
     * <p>原「终极冷却缩减」（{@link #LOL_ULTIMATE_CDR}，百分比语义）的装备已全部迁移为
     * 本属性（猎魔人弩箭 30 / 海克斯注力刚壁 30 / 残疫 20 / 基克的汇聚 18 / 朔极之矛 33），
     * 旧属性保留注册仅供存量存档兼容，结算层不再读取。</p>
     */
    public static final RegistryObject<Attribute> LOL_ULTIMATE_HASTE =
            ATTRIBUTES.register("ultimate_haste", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".ultimate_haste",
                    0.0D, 0.0D, 1000.0D).setSyncable(true));

    /**
     * 装备技能急速（Gear Haste）：点数语义，换算公式同英雄联盟官方
     * （{@code CDR = 急速 / (100 + 急速)}），<b>只作用于本模组装备的主动技能冷却</b>
     * （时间停止、各装备主动技、回声被动等模组自己的冷却结算点），作为独立乘区。
     * 不作用于铁魔法法术冷却——那是 {@link #LOL_ABILITY_HASTE} 的领域。
     */
    public static final RegistryObject<Attribute> LOL_GEAR_HASTE =
            ATTRIBUTES.register("gear_haste", () -> new RangedAttribute(
                    "attribute." + LOLAccessories.MOD_ID + ".gear_haste",
                    0.0D, 0.0D, 1000.0D).setSyncable(true));

    /**
     * 把本模组 LOL 独立属性注册进「玩家」的默认属性表。
     *
     * <p>属性显示类模组（Apothic Attributes / AttributesLib 的属性面板）会遍历所有已注册属性，
     * 只展示玩家身上<b>存在实例</b>的属性（{@code player.getAttribute(...) != null}）。属性若只靠
     * Curios 装备时临时创建实例，客户端经常拿不到稳定的实例，导致面板里整行不显示。注册进
     * 默认属性表后，服务端/客户端玩家从生成起就拥有这些属性实例，面板即可稳定展示，
     * 装备修正器也通过 {@code setSyncable(true)} 正常同步到客户端显示实际数值。</p>
     *
     * <p>注：生命偷取不自建属性，装备配置直接引用 Apothic Attributes 的
     * {@code attributeslib:life_steal}（其原生事件层在近战命中时结算回血，远程不再由本模组
     * 兜底——远程吸血请用全能吸血 {@link #LOL_OMNIVAMP}）。</p>
     */
    public static void addAttributesToPlayer(EntityAttributeModificationEvent event) {
        event.add(EntityType.PLAYER, LOL_CRIT_CHANCE.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_CRIT_DAMAGE.get(), DEFAULT_CRIT_DAMAGE);
        event.add(EntityType.PLAYER, LOL_MAGIC_RESIST.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_MAGIC_PEN.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_MAGIC_PEN_PERCENT.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_NATURAL_REGEN.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_HEAL_POWER.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_TENACITY.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_OMNIVAMP.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_ULTIMATE_CDR.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_PHYSICAL_DAMAGE_REDUCTION.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_MAGIC_DAMAGE_REDUCTION.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_ADAPTIVE_FORCE.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_TRUE_DAMAGE.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_TRUE_DAMAGE_AP_RATIO.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_TRUE_DAMAGE_AD_RATIO.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_ABILITY_HASTE.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_ULTIMATE_HASTE.get(), 0.0D);
        event.add(EntityType.PLAYER, LOL_GEAR_HASTE.get(), 0.0D);
    }

    /**
     * 把传说魔法抗性注册进「原版与铁魔法生物」的默认属性表（不再覆盖全模组 MONSTER）。
     *
     * <p>整合包作者经常会给自己的敌对生物配置独立防御体系，过去把 {@code lolaccessories:magic_resist}
     * 属性槽注入到<b>所有</b> MONSTER 实体类型上，会干扰他们自己的属性注册/显示（并可能与该属性
     * 的重复 add 冲突）。因此这里收窄范围：只给 {@code minecraft:} 与 {@code irons_spellbooks:}
     * 命名空间下以 {@link MobCategory#MONSTER} 类别注册的实体类型补属性槽（默认 0），第三方模组
     * 的敌对生物一概不碰，交给整合包自行决定。</p>
     *
     * <p>具体数值（默认魔抗 = 最大生命值的 10%，铁魔法 Boss 翻倍）在怪物生成入世时写入属性基础值，
     * 见 {@code com.example.lolaccessories.event.MobMagicResistEvents}；这里只保证“有属性槽”。</p>
     */
    public static void addMagicResistToHostileMobs(EntityAttributeModificationEvent event) {
        Attribute magicResist = LOL_MAGIC_RESIST.get();
        for (EntityType<?> type : event.getTypes()) {
            if (type.getCategory() != MobCategory.MONSTER) {
                continue;
            }
            ResourceLocation key = ForgeRegistries.ENTITY_TYPES.getKey(type);
            if (key == null) {
                continue;
            }
            String namespace = key.getNamespace();
            if (!namespace.equals("minecraft") && !namespace.equals("irons_spellbooks")) {
                continue;
            }
            // add/has 的泛型签名是 EntityType<? extends LivingEntity>，此处作有界转换
            @SuppressWarnings("unchecked")
            EntityType<? extends LivingEntity> livingType =
                    (EntityType<? extends LivingEntity>) type;
            event.add(livingType, magicResist, 0.0D);
        }
    }

    private ModAttributes() {
    }
}
