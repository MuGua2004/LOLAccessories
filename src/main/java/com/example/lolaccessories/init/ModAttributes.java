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
