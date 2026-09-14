package com.example.lolaccessories.item;

import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.compat.StackedGearState;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModItemTags;
import com.google.common.collect.Multimap;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotContext;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.type.capability.ICurioItem;

import net.minecraftforge.fml.loading.FMLEnvironment;

import javax.annotation.Nullable;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * 配置驱动的饰品装备基类。
 *
 * <p>所有数值（属性加成、被动效果参数）均来自 config/{modid}/gear/{gear_id}.json。
 * 实现了 {@link ICurioItem}，Curios 会自动为该物品挂上饰品能力并提供属性加成。</p>
 */
public class GearItem extends Item implements ICurioItem {

    /** 女神泪系（法力流）装备 id 集合：tooltip 显示层数条。 */
    private static final java.util.Set<String> TEAR_FAMILY_IDS = java.util.Set.of(
            "manamune", "muramana", "archangels_staff", "seraphs_embrace",
            "winters_approach", "fimbulwinter", "whispering_circlet", "diadem_of_songs");
    /** 育恩塔尔荒野箭：熟能生巧层数上限（近战口径）。 */
    private static final int YUN_TAL_STACK_CAP = 63;

    private final String gearId;

    /** tooltip 着色（appendHoverText 开头按品阶实时设置，单客户端渲染线程无并发问题）。 */
    private ChatFormatting tierTitleColor = ChatFormatting.GOLD;
    private ChatFormatting tierDescColor = ChatFormatting.GRAY;
    private ChatFormatting tierAttrNameColor = ChatFormatting.GRAY;
    private String tierTitlePrefix = "";

    public GearItem(String gearId, Properties properties) {
        super(properties);
        this.gearId = gearId;
    }

    public String getGearId() {
        return gearId;
    }

    @Override
    public Multimap<Attribute, AttributeModifier> getAttributeModifiers(SlotContext slotContext, UUID uuid, ItemStack stack) {
        return GearConfigManager.get(gearId).buildAttributeModifiers(uuid);
    }

    @Override
    public List<Component> getAttributesTooltip(List<Component> tooltips, ItemStack stack) {
        return new ArrayList<>();
    }

    @Override
    public boolean canEquipFromUse(SlotContext slotContext, ItemStack stack) {
        return true;
    }

    /**
     * 唯一限制：同一件 LOLAccessories 装备（按 gear_id 区分）全身上下只允许佩戴一件。
     * 遍历 Curios 各槽位已有的同款装备，除「即将放入的目标槽位」本身外，只要已有一件就拒绝佩戴，
     * 以此实现类似 LoL 中传说装备不可重复购买的“唯一”规则。
     */
    @Override
    public boolean canEquip(SlotContext slotContext, ItemStack stack) {
        if (!(slotContext.entity() instanceof Player player)) {
            return true;
        }
        var inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) {
            return true;
        }
        List<SlotResult> worn = inventory.get().findCurios(candidate ->
                candidate.getItem() instanceof GearItem gear && gearId.equals(gear.getGearId()));
        for (SlotResult result : worn) {
            SlotContext other = result.slotContext();
            boolean isDestination = other.identifier().equals(slotContext.identifier())
                    && other.index() == slotContext.index();
            if (!isDestination) {
                return false;
            }
        }
        // 系列互斥（如女神泪系 tear_family）：同 family_group 的装备全局仅允许佩戴一件
        // （配置里 family_group 留空即关闭该开关）
        String family = GearConfigManager.get(gearId).family_group;
        if (family != null && !family.isEmpty()) {
            List<SlotResult> wornFamily = inventory.get().findCurios(candidate ->
                    candidate.getItem() instanceof GearItem gear);
            for (SlotResult result : wornFamily) {
                SlotContext other = result.slotContext();
                boolean isDestination = other.identifier().equals(slotContext.identifier())
                        && other.index() == slotContext.index();
                if (isDestination) {
                    continue;
                }
                String otherFamily = GearConfigManager.get(((GearItem) result.stack().getItem()).getGearId()).family_group;
                if (family.equals(otherFamily)) {
                    return false;
                }
            }
        }
        return true;
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level, List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
        GearConfig config = GearConfigManager.get(gearId);

        // 按品阶着色：神话（tier4）紫色流光是主标题（另有 MythicNameTooltip 流光），
        // 属性名/被动标题/描述按 tier 差异化配色；传说（tier3）金色系，普通保持原样
        boolean mythic = stack.is(ModItemTags.TIER4);
        boolean legend = stack.is(ModItemTags.TIER3);
        if (mythic) {
            tierTitleColor = ChatFormatting.LIGHT_PURPLE;
            tierDescColor = ChatFormatting.AQUA;
            tierAttrNameColor = ChatFormatting.LIGHT_PURPLE;
            tierTitlePrefix = "\u2726 ";
        } else if (legend) {
            tierTitleColor = ChatFormatting.GOLD;
            tierDescColor = ChatFormatting.GRAY;
            tierAttrNameColor = ChatFormatting.GOLD;
            tierTitlePrefix = "\u25C6 ";
        } else {
            tierTitleColor = ChatFormatting.GOLD;
            tierDescColor = ChatFormatting.GRAY;
            tierAttrNameColor = ChatFormatting.GRAY;
            tierTitlePrefix = "";
        }

        // 属性加成（绿/红色数字 + 属性名）
        // 终极技能冷却缩减（lolaccessories:ultimate_cdr）在装备上是「唯一被动」技能而非基础属性，
        // 因此不在此处渲染成绿字属性行，而是留到下方被动区以 LoL「唯一被动—」样式展示（文案
        // 仿 LoL 官方文本，键：passive.lolaccessories.ultimate_cdr.*）。
        int statLines = 0;
        GearConfig.Attr ultimateCdr = null;
        for (GearConfig.Attr attr : config.attributes) {
            if ("lolaccessories:ultimate_cdr".equals(attr.id)) {
                ultimateCdr = attr;
                continue;
            }
            // 斯特拉克的挑战护手：+50 攻击力是被动「抓人双爪」提供的，
            // 属性行让位给被动区展示（贴近官方排版），加成本身仍然生效。
            if ("steraks_gage".equals(gearId)
                    && "minecraft:generic.attack_damage".equals(attr.id)) {
                continue;
            }
            if (attr.resolved()) {
                tooltip.add(attr.formatLine(tierAttrNameColor));
                statLines++;
            }
        }

        // 被动效果：LoL 式排版——金色「唯一被动—名称」标题行 + 灰色描述行
        // （数值随配置文件实时变化，拆成标题/描述两个词条以贴近正常游戏物品文本）
        List<Component> passiveLines = new ArrayList<>();
        for (GearConfig.OnHitEffect effect : config.on_hit_effects) {
            if (!effect.enabled) {
                continue;
            }
            if ("cleaver_shred".equals(effect.id)) {
                passiveLines.add(passiveTitle("cleaver_shred"));
                passiveLines.add(passiveDesc("cleaver_shred",
                        formatPercent(effect.per_stack),
                        formatNumber(effect.duration_seconds),
                        formatPercent(effect.max_total)));
            } else if ("cleaver_rush".equals(effect.id)) {
                passiveLines.add(passiveTitle("cleaver_rush"));
                passiveLines.add(passiveDesc("cleaver_rush",
                        formatPercent(effect.amount),
                        formatNumber(effect.duration_seconds)));
            } else if ("echo".equals(effect.id)) {
                // 回声：铁魔法联动技能，仅在安装铁魔法时展示
                if (IronsCompat.isLoaded()) {
                    passiveLines.add(passiveTitle("echo"));
                    passiveLines.add(passiveDesc("echo",
                            formatNumber(effect.cooldown_seconds),
                            formatNumber(effect.echo_count),
                            formatNumber(effect.base_damage),
                            formatPercent(effect.power_ratio),
                            formatPercent(effect.bonus_pct),
                            formatNumber(effect.radius_blocks)));
                }
            } else if ("helping_hand".equals(effect.id)) {
                // 帮助之手：对低生命值目标造成额外物理伤害（多兰家族通用唯一被动）
                passiveLines.add(passiveTitle("helping_hand"));
                passiveLines.add(passiveDesc("helping_hand",
                        formatNumber(effect.health_threshold),
                        formatNumber(effect.bonus_damage)));
            } else if ("perseverance".equals(effect.id)) {
                // 耐久专注：受伤后窗口期内自然恢复翻倍
                passiveLines.add(passiveTitle("perseverance"));
                passiveLines.add(passiveDesc("perseverance",
                        formatNumber(effect.duration_seconds)));
            } else if ("mana_restore".equals(effect.id)) {
                // 回复力：每秒回蓝，造成魔法伤害后窗口内翻倍
                passiveLines.add(passiveTitle("mana_restore"));
                passiveLines.add(passiveDesc("mana_restore",
                        formatNumber(effect.amount),
                        formatNumber(effect.duration_seconds)));
            } else if ("mana_flow".equals(effect.id)) {
                // 法力流：魔法命中叠加最大法力（计数器绑定玩家）
                passiveLines.add(passiveTitle("mana_flow"));
                int stacks = StackedGearState.readStackNbt(stack);
                passiveLines.add(passiveDesc("mana_flow",
                        formatNumber(effect.amount),
                        formatNumber(effect.max_total)));
                passiveLines.add(Component.translatable("passive.lolaccessories.mana_flow.progress",
                        formatNumber(stacks * effect.amount), formatNumber(effect.max_total))
                        .withStyle(ChatFormatting.GRAY));
            } else if ("glory".equals(effect.id)) {
                // 荣耀：击杀堆叠/死亡衰减，每层 +法术强度（黑暗封印 4% / 梅贾 5%，共享层数池）
                passiveLines.add(passiveTitle("glory"));
                int stacks = StackedGearState.readStackNbt(stack);
                passiveLines.add(passiveDesc("glory",
                        formatNumber(effect.kill_stacks),
                        formatNumber(effect.death_loss),
                        formatPercent(effect.amount),
                        formatNumber(effect.max_stacks)));
                passiveLines.add(Component.translatable("passive.lolaccessories.glory.progress",
                        formatNumber(stacks), formatNumber(effect.max_stacks))
                        .withStyle(ChatFormatting.GRAY));
                if ("mejais_soulstealer".equals(gearId)) {
                    // 梅贾专属：与黑暗封印共享层数、超出上限的层数只在佩戴窃魂卷时生效
                    passiveLines.add(Component.translatable("passive.lolaccessories.glory.mejais.note")
                            .withStyle(ChatFormatting.GRAY));
                }
            } else if ("time_stop".equals(effect.id)) {
                // 主动技「时间停止」（探索者的护臂）：走 active 前缀词条，时长/冷却代入占位符。
                // 它不是“命中生效”的被动，仅作展示；真正的触发链路见 LolTimeStopEvents 与按键类。
                passiveLines.add(activeTitle("time_stop"));
                passiveLines.add(activeDesc("time_stop",
                        formatNumber(effect.duration_seconds),
                        formatNumber(effect.cooldown_seconds)));
            } else if ("quicksilver".equals(effect.id)) {
                // 主动技「净化」（水银饰带）：解除全部有害状态并扑灭火焰。
                // 它不是“命中生效”的被动，仅作展示；真正的触发链路见 LolNewActiveSkillEvents 与按键类。
                passiveLines.add(activeTitle("quicksilver"));
                passiveLines.add(activeDesc("quicksilver",
                        formatNumber(effect.cooldown_seconds)));
            } else if ("crescent".equals(effect.id)) {
                if ("ravenous_hydra".equals(gearId)) {
                    passiveLines.add(activeTitle("crescent"));
                    passiveLines.add(activeDesc("crescent",
                            formatPercent(effect.power_ratio > 0 ? effect.power_ratio : 0.8D),
                            formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D),
                            formatNumber(effect.cooldown_seconds)));
                } else if ("titanic_hydra".equals(gearId)) {
                    GearConfig.OnHitEffect cleave = config.findEffect("titanic_cleave").orElse(null);
                    passiveLines.add(activeTitle("titanic_crescent"));
                    passiveLines.add(activeDesc("titanic_crescent",
                            formatPercent(effect.base_damage > 0 ? effect.base_damage : 0.04D),
                            formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.09D),
                            formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D),
                            formatNumber(effect.cooldown_seconds)));
                } else {
                    passiveLines.add(activeDesc("crescent",
                            formatNumber(effect.base_damage),
                            formatNumber(effect.radius_blocks),
                            formatNumber(effect.cooldown_seconds)));
                }
            } else if ("immolate".equals(effect.id)) {
                // 灼烧（斑比的熔渣）：受伤/造成伤害时点燃周围敌人
                passiveLines.add(passiveTitle("immolate"));
                passiveLines.add(passiveDesc("immolate",
                        formatNumber(effect.amount),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("inflame".equals(effect.id)) {
                // 引燃（命定灰烬）：魔法命中灼烧目标
                passiveLines.add(passiveTitle("inflame"));
                passiveLines.add(passiveDesc("inflame",
                        formatNumber(effect.amount),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("thorns".equals(effect.id)) {
                // 尖刺（棘刺背心）/荆棘（荆棘之甲）：反弹伤害并施加重伤（后者带护甲加成）
                passiveLines.add(passiveTitle("thorns"));
                if (effect.armor_ratio > 0) {
                    passiveLines.add(passiveDesc("thorns",
                            formatNumber(effect.amount),
                            formatPercent(effect.armor_ratio),
                            formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.4D),
                            formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
                } else {
                    passiveLines.add(passiveDesc("thorns",
                            formatNumber(effect.amount),
                            formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.4D),
                            formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
                }
            } else if ("eternity".equals(effect.id)) {
                // 永恒（万世催化石）：受伤回蓝、魔法命中回血
                passiveLines.add(passiveTitle("eternity"));
                passiveLines.add(passiveDesc("eternity",
                        formatPercent(effect.amount),
                        formatNumber((effect.base_damage > 0 ? effect.base_damage : 100.0D)
                                * (effect.bonus_pct > 0 ? effect.bonus_pct : 0.0D))));
            } else if ("madness".equals(effect.id)) {
                // 疯狂（幽魂面具）：命中叠加伤害层
                passiveLines.add(passiveTitle("madness"));
                passiveLines.add(passiveDesc("madness",
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D),
                        formatNumber(effect.max_stacks),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.02D)));
            } else if ("revved".equals(effect.id)) {
                // 充能（海克斯科技发电机）：周期性附伤
                passiveLines.add(passiveTitle("revved"));
                passiveLines.add(passiveDesc("revved",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 8.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 12.0D)));
            } else if ("bullseye".equals(effect.id)) {
                // 牛眼（斥候弹弓）：周期性附伤
                passiveLines.add(passiveTitle("bullseye"));
                passiveLines.add(passiveDesc("bullseye",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 8.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 12.0D)));
            } else if ("rage".equals(effect.id)) {
                // 狂怒（净蚀）：攻击后获得移速
                passiveLines.add(passiveTitle("rage"));
                passiveLines.add(passiveDesc("rage",
                        formatPercent(effect.amount),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D)));
            } else if ("spellblade".equals(effect.id)) {
                // 黄昏黎明·咒刃（施法后下一次普攻附魔法伤害并治疗）与耀光·咒刃共用 effect id，
                // 这里按装备区分展示数值与文案
                if ("dusk_and_dawn".equals(gearId)) {
                    passiveLines.add(passiveTitle("dusk_spellblade"));
                    passiveLines.add(passiveDesc("dusk_spellblade",
                            formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 10.0D),
                            formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 1.5D),
                            formatPercent(effect.ad_ratio > 0 ? effect.ad_ratio : 0.75D),
                            formatPercent(effect.ap_ratio > 0 ? effect.ap_ratio : 0.1D),
                            formatPercent(effect.heal_ap_ratio > 0 ? effect.heal_ap_ratio : 0.1D),
                            formatPercent(effect.heal_hp_ratio > 0 ? effect.heal_hp_ratio : 0.03D)));
                } else if ("essence_reaver".equals(gearId)) {
                    passiveLines.add(passiveTitle("spellblade"));
                    passiveLines.add(passiveDesc("spellblade",
                            formatPercent(effect.power_ratio > 0 ? effect.power_ratio : 1.25D)));
                } else {
                    // 咒刃（耀光）：魔法命中后下一次普攻附伤
                    passiveLines.add(passiveTitle("spellblade"));
                    passiveLines.add(passiveDesc("spellblade",
                            formatPercent(effect.power_ratio > 0 ? effect.power_ratio : 1.0D)));
                }
            } else if ("cleave".equals(effect.id)) {
                // 顺劈（提亚马特/贪欲九头蛇被动）：物理攻击溅射周围敌人（文案按装备区分）
                passiveLines.add(passiveTitle("cleave"));
                passiveLines.add(passiveDesc("cleave",
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 2.0D),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.6D)));
            } else if ("terminus".equals(effect.id)) {
                passiveLines.add(passiveTitle("terminus"));
                passiveLines.add(passiveDesc("terminus",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 30.0D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 3),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D)));
            } else if ("shipwrecker".equals(effect.id)) {
                passiveLines.add(passiveTitle("shipwrecker"));
                passiveLines.add(passiveDesc("shipwrecker",
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 100),
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 40.0D),
                        formatPercent(effect.power_ratio > 0 ? effect.power_ratio : 1.0D)));
            } else if ("titanic_cleave".equals(effect.id)) {
                passiveLines.add(passiveTitle("titanic_cleave"));
                passiveLines.add(passiveDesc("titanic_cleave",
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 2.5D),
                        formatPercent(effect.base_damage > 0 ? effect.base_damage : 0.01D),
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.03D)));
            } else if ("warmog_heart".equals(effect.id)) {
                // 狂徒之心（狂徒铠甲）：饰品栏生命加成达标后脱战回复
                passiveLines.add(passiveTitle("warmog_heart"));
                passiveLines.add(passiveDesc("warmog_heart",
                        formatNumber(effect.amount > 0 ? effect.amount : 1500.0D),
                        formatPercent(effect.base_damage > 0 ? effect.base_damage : 0.05D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D)));
            } else if ("colossal_consumption".equals(effect.id)) {
                // 心之钢：歌莉娅巨人（属性型被动）+ 庞然吞食（吞食印记）+ 涨血计数器
                passiveLines.add(passiveTitle("goliath"));
                passiveLines.add(passiveDesc("goliath"));
                passiveLines.add(passiveTitle("colossal_consumption"));
                passiveLines.add(passiveDesc("colossal_consumption"));
                double fedBonus = stack.getTag() != null
                        ? stack.getTag().getDouble("lolaccessories_heartsteel_item_bonus") : 0.0D;
                passiveLines.add(Component.translatable("passive.lolaccessories.heartsteel.progress",
                        formatNumber(fedBonus)).withStyle(ChatFormatting.GOLD));
            } else if ("mock_fate".equals(effect.id)) {
                // 主动技「嘲弄命运」（命运十面骰）——数值为固定设计，直接写死在词条里
                com.example.lolaccessories.LOLAccessories.LOGGER.info("[命运骰] tooltip 数值：duration={} cooldown={}",
                        effect.duration_seconds, effect.cooldown_seconds);
                passiveLines.add(activeTitle("mock_fate"));
                passiveLines.add(activeDesc("mock_fate"));
            } else if ("qionghua".equals(effect.id)) {
                // 梦之乌托邦（翡翠城）：法术命中施加琼华，目标受到的伤害提高
                passiveLines.add(passiveTitle("qionghua"));
                passiveLines.add(passiveDesc("qionghua",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.2D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 10)));
            } else if ("farewell_paradise".equals(effect.id)) {
                // 主动技「再见桃花源」（翡翠城）——数值为固定设计，直接写死在词条里
                passiveLines.add(activeTitle("farewell_paradise"));
                passiveLines.add(activeDesc("farewell_paradise"));
            } else if ("endless_grief".equals(effect.id)) {
                // 主动技「此恨无绝」（灵恸）——数值为固定设计，直接写死在词条里
                passiveLines.add(activeTitle("endless_grief"));
                passiveLines.add(activeDesc("endless_grief"));
            } else if ("new_clothes".equals(effect.id)) {
                // 唯一被动「皇帝的新衣」（隐身衣）：99% 物理/魔法伤害减免（激活式投影）
                passiveLines.add(passiveTitle("new_clothes"));
                passiveLines.add(passiveDesc("new_clothes",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.99D)));
            } else if ("i_want_for_nothing".equals(effect.id)) {
                // 唯一被动「我什么都不缺了」（天帝）——三段成长，数值为固定设计
                passiveLines.add(passiveTitle("i_want_for_nothing"));
                passiveLines.add(passiveDesc("i_want_for_nothing"));
            } else if ("poem_of_truth".equals(effect.id)) {
                // 唯一被动「代行真理」（致明日之诗）：攻击附带真理伤害 + 佩戴切创造模式
                passiveLines.add(passiveTitle("poem_of_truth"));
                passiveLines.add(passiveDesc("poem_of_truth",
                        formatNumber(effect.true_damage > 0 ? effect.true_damage : 1000.0D)));
                passiveLines.add(passiveDesc("poem_of_truth.creative", 1.0D));
            } else if ("zeal".equals(effect.id)) {
                // 霜火风暴（基克的聚合）：终极技能后进入战斗召唤风暴
                passiveLines.add(passiveTitle("zeal"));
                passiveLines.add(passiveDesc("zeal",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 30.0D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 4.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 45.0D)));
            } else if ("stormrazor_dynamic_arrow".equals(effect.id)) {
                // 岚切：弹射物伤害与攻击力等额（动态）
                passiveLines.add(passiveTitle("stormrazor_dynamic_arrow"));
                passiveLines.add(passiveDesc("stormrazor_dynamic_arrow"));
            } else if ("stormrazor_bolt".equals(effect.id)) {
                // 盈能：电弧（岚切）
                passiveLines.add(passiveTitle("stormrazor_bolt"));
                passiveLines.add(passiveDesc("stormrazor_bolt",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 100.0D),
                        formatPercent(0.45D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 1.5D)));
            } else if ("pledge".equals(effect.id)) {
                // 誓约（骑士之誓）
                passiveLines.add(passiveTitle("pledge"));
                passiveLines.add(passiveDesc("pledge",
                        formatPercent(0.14D), formatPercent(0.12D)));
                passiveLines.add(activeTitle("pledge"));
                passiveLines.add(activeDesc("pledge"));
            } else if ("winds_fury".equals(effect.id)) {
                // 风怒（卢安娜的飓风）：普攻附带额外箭矢
                passiveLines.add(passiveTitle("winds_fury"));
                passiveLines.add(passiveDesc("winds_fury",
                        formatNumber(effect.count > 0 ? effect.count : 2),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.65D)));
            } else if ("intervention".equals(effect.id)) {
                // 主动技「降临」（救赎）：以施法时的玩家坐标为中心，延迟后圣光落下（敌伤友疗）
                passiveLines.add(activeTitle("intervention"));
                passiveLines.add(activeDesc("intervention",
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.5D),
                        formatPercent(effect.base_damage > 0 ? effect.base_damage : 0.10D),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.25D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 3.5D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 120.0D)));
            } else if ("winters_caress".equals(effect.id)) {
                // 冬之抚慰（冰霜之心光环）：周围敌人降攻速
                passiveLines.add(passiveTitle("winters_caress"));
                passiveLines.add(passiveDesc("winters_caress",
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 3.5D),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.20D)));
            } else if ("icathian_bite".equals(effect.id)) {
                // 艾卡西亚之咬（纳什之牙）：普攻附加法强魔法伤害
                passiveLines.add(passiveTitle("icathian_bite"));
                passiveLines.add(passiveDesc("icathian_bite",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 15.0D),
                        formatPercent(effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.15D)));
            } else if ("rimefrost".equals(effect.id)) {
                // 凝霜（瑞莱的冰晶节杖）：魔法伤害减速
                passiveLines.add(passiveTitle("rimefrost"));
                passiveLines.add(passiveDesc("rimefrost",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.30D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 1.0D)));
            } else if ("hatefog".equals(effect.id)) {
                // 憎恨之雾（残疫）：大招命中脚下紫雾圈
                passiveLines.add(passiveTitle("hatefog"));
                passiveLines.add(passiveDesc("hatefog",
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 4.5D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D),
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 15.0D),
                        formatPercent(effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.0125D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 3.0D)));
            } else if ("statikk_chain".equals(effect.id)) {
                // 盈能：电刃闪电（斯塔缇克电刃）
                passiveLines.add(passiveTitle("statikk_chain"));
                passiveLines.add(passiveDesc("statikk_chain",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 70.0D),
                        formatNumber(effect.amount > 0 ? effect.amount : 6.0D)));
            } else if ("spell_amplify".equals(effect.id)) {
                // 法术放大器（灭世者的死亡之帽）
                passiveLines.add(passiveTitle("spell_amplify"));
                passiveLines.add(passiveDesc("spell_amplify",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.30D)));
            } else if ("wit_end_hit".equals(effect.id)) {
                // 磨蚀（智慧末刃）：命中附加魔法伤害
                passiveLines.add(passiveTitle("wit_end_hit"));
                passiveLines.add(passiveDesc("wit_end_hit",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 45.0D)));
            } else if ("firecannon_bolt".equals(effect.id)) {
                // 盈能：火炮（疾射火炮）
                passiveLines.add(passiveTitle("firecannon_bolt"));
                passiveLines.add(passiveDesc("firecannon_bolt",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 120.0D)));
            } else if ("quicken".equals(effect.id)) {
                // 疾行（三相之力 Quicken）：普攻命中后短暂加速
                passiveLines.add(passiveTitle("quicken"));
                passiveLines.add(passiveDesc("quicken",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.2D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D)));
            } else if ("warmog_vigor".equals(effect.id)) {
                // 狂徒之活力（狂徒铠甲）：额外生命 = 12% 装备生命值
                passiveLines.add(passiveTitle("warmog_vigor"));
                passiveLines.add(passiveDesc("warmog_vigor",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.12D)));
            } else if ("annul".equals(effect.id)) {
                // 法盾（翠绿屏障）：格挡一次魔法伤害
                passiveLines.add(passiveTitle("annul"));
                passiveLines.add(passiveDesc("annul",
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 60.0D)));
            } else if ("rock_solid".equals(effect.id)) {
                // 磐石（守望者铠甲）：受击固定减伤
                passiveLines.add(passiveTitle("rock_solid"));
                passiveLines.add(passiveDesc("rock_solid",
                        formatNumber(effect.reduction_flat),
                        formatPercent(effect.reduction_cap_pct)));
            } else if ("inspiring_speech".equals(effect.id)) {
                // 主动技「鼓舞」（舒瑞娅的战歌）：对佩戴者与附近友方玩家施加短时移速
                passiveLines.add(activeTitle("inspiring_speech"));
                passiveLines.add(activeDesc("inspiring_speech",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.3D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 4.0D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 100.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 75.0D)));
            } else if ("tyranny".equals(effect.id)) {
                // 暴政（霸王血铠）：攻击伤害加成 = 最大生命 × amount（动态属性，AA 面板实时显示）
                passiveLines.add(passiveTitle("tyranny"));
                passiveLines.add(passiveDesc("tyranny",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.025D)));
            } else if ("retribution".equals(effect.id)) {
                // 报应（霸王血铠）：生命越低，攻击伤害加成越高（不含本被动自身）
                passiveLines.add(passiveTitle("retribution"));
                passiveLines.add(passiveDesc("retribution",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.12D)));
            } else if ("anguish".equals(effect.id)) {
                // 苦楚（无终恨意）：战斗中周期魔法 AOE 并回血
                passiveLines.add(passiveTitle("anguish"));
                passiveLines.add(passiveDesc("anguish",
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.03D),
                        formatNumber(effect.interval_seconds > 0 ? effect.interval_seconds : 4.0D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 8.0D),
                        formatPercent(effect.heal_pct > 0 ? effect.heal_pct : 0.0D)));
            } else if ("baleful_blaze".equals(effect.id)) {
                // 不祥灼烧（黯炎火炬）：魔法命中灼烧 + 每灼烧目标加成法伤
                passiveLines.add(passiveTitle("baleful_blaze"));
                passiveLines.add(passiveDesc("baleful_blaze",
                        formatNumber(effect.burn_tick_damage > 0 ? effect.burn_tick_damage : 10.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D),
                        formatPercent(effect.burn_ap_ratio > 0 ? effect.burn_ap_ratio : 0.01D),
                        formatPercent(effect.ap_pct_per_target > 0 ? effect.ap_pct_per_target : 0.04D)));
            } else if ("magebane".equals(effect.id)) {
                // 法师之祸（败魔）：15 秒未受魔法伤害获得最大生命比例护盾
                passiveLines.add(passiveTitle("magebane"));
                passiveLines.add(passiveDesc("magebane",
                        formatNumber(effect.interval_seconds > 0 ? effect.interval_seconds : 15.0D),
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.15D)));
            } else if ("lifeline".equals(effect.id)) {
                // 生命残片（海克斯饮魔刀）：低血护盾
                passiveLines.add(passiveTitle("lifeline"));
                passiveLines.add(passiveDesc("lifeline",
                        formatPercent(effect.trigger_health_percent > 0 ? effect.trigger_health_percent : 0.3D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.5D),
                        formatNumber(effect.shield_amount > 0 ? effect.shield_amount : 150.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 60.0D)));
            } else if ("enlighten".equals(effect.id)) {
                // 启迪（遗失的章节）：升级回蓝
                passiveLines.add(passiveTitle("enlighten"));
                passiveLines.add(passiveDesc("enlighten",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.2D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("grievous_wounds_phys".equals(effect.id)) {
                // 重伤（死刑宣告）：物理伤害命中后降低目标受到的治疗
                passiveLines.add(passiveTitle("grievous_wounds_phys"));
                passiveLines.add(passiveDesc("grievous_wounds_phys",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.4D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("grievous_wounds_magic".equals(effect.id)) {
                // 重伤（湮灭宝珠）：魔法伤害命中后降低目标受到的治疗
                passiveLines.add(passiveTitle("grievous_wounds_magic"));
                passiveLines.add(passiveDesc("grievous_wounds_magic",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.4D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("tear_stack".equals(effect.id)) {
                // 法力流（女神泪系传说）：命中叠层，满 360 自动蜕变
                passiveLines.add(passiveTitle("tear_stack"));
                int tearStacks = StackedGearState.readStackNbt(stack);
                passiveLines.add(passiveDesc("tear_stack",
                        formatNumber(effect.amount),
                        formatNumber(effect.amount * 2)));
                passiveLines.add(Component.translatable("item.lolaccessories.tear_family.progress",
                        formatNumber(tearStacks)).withStyle(ChatFormatting.AQUA));
            } else if ("tear_awe".equals(effect.id)) {
                // 敬畏（Awe）：按装备类别显示不同属性换算。
                // json 的 amount 语义 = 「每 100 点额外法力提供的属性」——点数属性（AD/生命）
                // 显示为每 100 点法力 +N 点；倍率属性（法强/治疗强度）显示为每 100 点法力 +N%。
                passiveLines.add(passiveTitle("tear_awe"));
                // 每 100 点额外法力的提供量：点数属性（AD/生命）用数字，倍率属性（法强/治疗强度）用百分比
                boolean flatAttr = "manamune".equals(gearId) || "muramana".equals(gearId)
                        || "winters_approach".equals(gearId) || "fimbulwinter".equals(gearId);
                String per100 = flatAttr ? formatNumber(effect.amount * 100.0D)
                        : formatPercent(effect.amount);
                String aweKey = switch (gearId) {
                    case "archangels_staff" -> "awe_ap_1";
                    case "seraphs_embrace" -> "awe_ap_2";
                    case "winters_approach", "fimbulwinter" -> "awe_hp";
                    case "whispering_circlet", "diadem_of_songs" -> "awe_hs";
                    default -> "awe_ad";
                };
                passiveLines.add(Component.translatable("passive.lolaccessories." + aweKey + ".desc",
                        per100).withStyle(tierDescColor));
            } else if ("tear_shock".equals(effect.id)) {
                passiveLines.add(passiveTitle("tear_shock"));
                passiveLines.add(passiveDesc("tear_shock"));
            } else if ("tear_lifeline".equals(effect.id)) {
                passiveLines.add(passiveTitle("tear_lifeline"));
                passiveLines.add(passiveDesc("tear_lifeline"));
            } else if ("tear_everlasting".equals(effect.id)) {
                passiveLines.add(passiveTitle("tear_everlasting"));
                passiveLines.add(passiveDesc("tear_everlasting"));
            } else if ("tear_consonance".equals(effect.id)) {
                passiveLines.add(passiveTitle("tear_consonance"));
                passiveLines.add(passiveDesc("tear_consonance"));
            } else if ("guardian_angel".equals(effect.id)) {
                passiveLines.add(passiveTitle("guardian_angel"));
                passiveLines.add(passiveDesc("guardian_angel"));
            } else if ("yun_tal_crit".equals(effect.id)) {
                passiveLines.add(passiveTitle("yun_tal_crit"));
                int yunStacks = StackedGearState.readStackNbt(stack);
                passiveLines.add(passiveDesc("yun_tal_crit", formatPercent(effect.amount)));
                passiveLines.add(Component.translatable("item.lolaccessories.yun_tal.progress",
                        formatNumber(yunStacks), formatNumber(YUN_TAL_STACK_CAP),
                        formatPercent(Math.min(0.25D, yunStacks * effect.amount)))
                        .withStyle(ChatFormatting.AQUA));
            } else if ("yun_tal_flurry".equals(effect.id)) {
                passiveLines.add(passiveTitle("yun_tal_flurry"));
                passiveLines.add(passiveDesc("yun_tal_flurry",
                        formatPercent(effect.amount),
                        formatNumber(effect.duration_seconds),
                        formatNumber(effect.cooldown_seconds)));
            } else if ("grievous_wounds_physical".equals(effect.id)) {
                passiveLines.add(passiveTitle("grievous_wounds_physical"));
                passiveLines.add(passiveDesc("grievous_wounds_physical",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.4D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("giant_slayer".equals(effect.id)) {
                passiveLines.add(passiveTitle("giant_slayer"));
                passiveLines.add(passiveDesc("giant_slayer",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.15D),
                        formatNumber(effect.max_total > 0 ? effect.max_total : 15000.0D)));
            } else if ("clear_sky".equals(effect.id)) {
                // 澄澈天空（澄空之愿）：弹射物伤害概率转化为虚空伤害
                passiveLines.add(passiveTitle("clear_sky"));
                passiveLines.add(passiveDesc("clear_sky",
                        formatPercent(effect.chance > 0 ? effect.chance : 0.5D)));
            } else if ("waltz".equals(effect.id)) {
                // 幽影华尔兹（幻影之舞）：无视单位碰撞体积
                passiveLines.add(passiveTitle("waltz"));
                passiveLines.add(passiveDesc("waltz"));
            } else if ("zeal".equals(effect.id)) {
                // 霜火风暴（基克的聚合）：终极技能就绪 → 进入战斗召唤风暴（占位符与文案一一对应）
                passiveLines.add(passiveTitle("zeal"));
                passiveLines.add(passiveDesc("zeal",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 30.0D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 4.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 45.0D)));
            } else if ("grasping_claws".equals(effect.id)) {
                // 抓人双爪（斯特拉克的挑战护手）：获得固定攻击力（MC 基础攻击只有 1 点，
                // 按用户口径固定 +50 点，加成由 gear JSON 的 attack_damage 属性承载）
                passiveLines.add(passiveTitle("grasping_claws"));
                passiveLines.add(passiveDesc("grasping_claws",
                        formatNumber(effect.amount > 0 ? effect.amount : 50.0D)));
            } else if ("vigor".equals(effect.id)) {
                // 无匹活力（振奋盔甲）：受到的治疗与护盾提升（治疗由 Apothic healing_received 结算，
                // 护盾由 ShieldHpService 同属性乘区）
                passiveLines.add(passiveTitle("vigor"));
                passiveLines.add(passiveDesc("vigor",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.30D)));
            } else if ("sunfire".equals(effect.id)) {
                // 献祭（日炎圣盾）：佩戴期间每秒点燃周围敌人
                passiveLines.add(passiveTitle("sunfire"));
                passiveLines.add(passiveDesc("sunfire",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 15.0D),
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.01D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D)));
            } else if ("remnant".equals(effect.id)) {
                // 余烬（饮血剑）：满血溢出治疗转血色护盾
                passiveLines.add(passiveTitle("remnant"));
                passiveLines.add(passiveDesc("remnant",
                        formatNumber(effect.shield_amount > 0 ? effect.shield_amount : 200.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 25.0D)));
            } else if ("overdrive".equals(effect.id)) {
                // 超速驱动（海克斯注力刚壁）：施放终极技能后攻速/移速超载（近战数值）
                passiveLines.add(passiveTitle("overdrive"));
                passiveLines.add(passiveDesc("overdrive",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.50D),
                        formatPercent(effect.move_speed_ratio > 0 ? effect.move_speed_ratio : 0.20D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 8.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 30.0D)));
            } else if ("lifeline".equals(effect.id) && "steraks_gage".equals(gearId)) {
                // 救主灵刃（斯特拉克的挑战护手专属展示：60% 额外生命护盾，4.5 秒衰减，无冷却）
                passiveLines.add(passiveTitle("steraks_lifeline"));
                passiveLines.add(passiveDesc("steraks_lifeline",
                        formatPercent(effect.trigger_health_percent > 0 ? effect.trigger_health_percent : 0.30D),
                        formatPercent(effect.shield_amount > 0 ? effect.shield_amount : 0.60D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 4.5D)));
            } else if ("demon_king".equals(effect.id)) {
                // 我是大魔王（魔王之心）：诅咒/增益动态乘区
                passiveLines.add(passiveTitle("demon_king"));
                passiveLines.add(passiveDesc("demon_king",
                        formatPercent(effect.curse_ratio > 0 ? effect.curse_ratio : 0.10D),
                        formatPercent(effect.buff_ratio > 0 ? effect.buff_ratio : 0.15D)));
            } else if ("focused_will".equals(effect.id)) {
                passiveLines.add(passiveTitle("focused_will"));
                passiveLines.add(passiveDesc("focused_will",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.03D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 4),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D)));
            } else if ("dragonforce".equals(effect.id)) {
                // 龙之力量（朔极之矛）：基础技能急速，仅作用于基础技能冷却（终极技能不受影响）
                passiveLines.add(passiveTitle("dragonforce"));
                passiveLines.add(passiveDesc("dragonforce",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.25D)));
            } else if ("nightstalker".equals(effect.id)) {
                passiveLines.add(passiveTitle("nightstalker"));
                passiveLines.add(passiveDesc("nightstalker",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 50.0D),
                        formatPercent(effect.armor_pierce_scale > 0 ? effect.armor_pierce_scale : 1.5D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 3.0D)));
            } else if ("skipper".equals(effect.id)) {
                passiveLines.add(passiveTitle("skipper"));
                passiveLines.add(passiveDesc("skipper",
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 10.0D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 5),
                        formatPercent(effect.ad_ratio > 0 ? effect.ad_ratio : 1.2D),
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.05D)));
            } else if ("shaped_charge".equals(effect.id)) {
                // 成型炸药（破垒者）：近战普攻命中后结算真实伤害（含穿甲加成）
                passiveLines.add(passiveTitle("shaped_charge"));
                passiveLines.add(passiveDesc("shaped_charge",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 50.0D),
                        formatNumber(effect.armor_pierce_scale > 0 ? effect.armor_pierce_scale : 1.5D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 20.0D)));
            } else if ("famine".equals(effect.id)) {
                // 饥馑（无穷饥渴）：攻击力按英雄联盟「近战」口径的比例转化为冷却缩减（不再区分近战/远程）
                passiveLines.add(passiveTitle("famine"));
                passiveLines.add(passiveDesc("famine",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.05D),
                        formatPercent(effect.melee_ratio > 0 ? effect.melee_ratio : 0.13D)));
            } else if ("feast".equals(effect.id)) {
                // 盛宴（无穷饥渴）：击杀/助攻后获得全能吸血
                passiveLines.add(passiveTitle("feast"));
                passiveLines.add(passiveDesc("feast",
                        formatPercent(effect.omnivamp_ratio > 0 ? effect.omnivamp_ratio : 0.15D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 8.0D),
                        formatNumber(effect.damage_window_seconds > 0 ? effect.damage_window_seconds : 3.0D)));
            } else if ("barrage".equals(effect.id)) {
                // 开战弹幕（猎魔人弩箭）：施放终极技能后，接下来几次远程普攻必定会心并附真实伤害
                passiveLines.add(passiveTitle("barrage"));
                passiveLines.add(passiveDesc("barrage",
                        formatNumber(effect.amount > 0 ? effect.amount : 3.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 8.0D),
                        formatPercent(effect.crit_true_ratio > 0 ? effect.crit_true_ratio : 0.15D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 20.0D)));
            } else if ("long_shot".equals(effect.id)) {
                // 高倍望远镜（海克斯镜片 C44）：远程普攻命中距离越远伤害越高
                passiveLines.add(passiveTitle("long_shot"));
                passiveLines.add(passiveDesc("long_shot",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.1D),
                        formatNumber(effect.distance_blocks > 0 ? effect.distance_blocks : 50.0D)));
            } else if ("fanfare".equals(effect.id)) {
                // 嘹亮旋律（班德尔音管）：对目标施加负面药水效果后，自身与友军获移速/攻速
                passiveLines.add(passiveTitle("fanfare"));
                passiveLines.add(passiveDesc("fanfare",
                        formatPercent(effect.move_speed_ratio > 0 ? effect.move_speed_ratio : 0.2D),
                        formatPercent(effect.attack_speed_ratio > 0 ? effect.attack_speed_ratio : 0.3D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 8.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 30.0D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 10.0D)));
            } else if ("realize".equals(effect.id)) {
                // 主动技「法力成真」（实现器）：短暂时间内铁魔法施法冷却近乎立即完成且耗蓝双倍
                passiveLines.add(activeTitle("realize"));
                passiveLines.add(activeDesc("realize",
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D),
                        formatNumber(effect.mana_cost_multiplier > 0 ? effect.mana_cost_multiplier : 2.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 180.0D)));
            } else if ("protoplasm".equals(effect.id)) {
                // 救主灵刃（原生质护带）：低血触发临时最大生命 + 持续治疗 + 移速韧性
                passiveLines.add(passiveTitle("protoplasm"));
                passiveLines.add(passiveDesc("protoplasm",
                        formatPercent(effect.trigger_health_percent > 0 ? effect.trigger_health_percent : 0.3D),
                        formatNumber(effect.health_min),
                        formatNumber(effect.health_max),
                        formatNumber(effect.heal_min),
                        formatNumber(effect.heal_max),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 90.0D),
                        formatPercent(effect.move_speed_ratio > 0 ? effect.move_speed_ratio : 0.1D),
                        formatPercent(effect.tenacity_ratio > 0 ? effect.tenacity_ratio : 0.25D)));
            } else if ("steadfast".equals(effect.id)) {
                // 坚韧（自然之力）：受到魔法伤害叠层，满层获得额外魔抗与移速
                passiveLines.add(passiveTitle("steadfast"));
                passiveLines.add(passiveDesc("steadfast",
                        formatNumber(effect.amount > 0 ? effect.amount : 70.0D),
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.06D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 8),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 7.0D)));
            } else if ("hypershot".equals(effect.id)) {
                // 超频射击（视界专注）：远距离魔法命中标记目标，标记期间对其魔法增伤
                passiveLines.add(passiveTitle("hypershot"));
                passiveLines.add(passiveDesc("hypershot",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.10D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 70.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D)));
            } else if ("void_corruption".equals(effect.id)) {
                // 虚空侵蚀（裂隙制造者）：与敌方作战时每秒叠层增伤，满层获全能吸血（近战/远程）
                passiveLines.add(passiveTitle("void_corruption"));
                passiveLines.add(passiveDesc("void_corruption",
                        formatPercent(effect.per_stack > 0 ? effect.per_stack : 0.02D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 4),
                        formatNumber(effect.interval_seconds > 0 ? effect.interval_seconds : 1.0D),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.10D),
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.06D)));
            } else if ("void_infusion".equals(effect.id)) {
                // 虚空灌注（裂隙制造者）：获得相当于 2% 额外生命值的法术强度（附属词条，不单列标题）
                passiveLines.add(passiveDesc("void_infusion",
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.02D)));
            } else if ("cinderbloom".equals(effect.id)) {
                // 灰烬绽放（影焰）：对低生命值目标造成的魔法伤害提高
                passiveLines.add(passiveTitle("cinderbloom"));
                passiveLines.add(passiveDesc("cinderbloom",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.20D),
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.40D)));
            } else if ("stormraider".equals(effect.id)) {
                // 风暴掠袭（风暴狂涌）：短窗内累计伤害达阈值后延迟引爆雷电伤害并获移速
                passiveLines.add(passiveTitle("stormraider"));
                passiveLines.add(passiveDesc("stormraider",
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.25D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D),
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 125.0D),
                        formatPercent(effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.10D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 30.0D)));
            } else if ("ignore_pain".equals(effect.id)) {
                // 无视痛苦（死亡之舞）：所受伤害的一部分转为流血，3 秒内扣完
                passiveLines.add(passiveTitle("ignore_pain"));
                passiveLines.add(passiveDesc("ignore_pain",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.30D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("defy".equals(effect.id)) {
                // 蔑视（死亡之舞）：3 秒内被我伤害过的目标阵亡 → 净化流血 + 持续回血
                passiveLines.add(passiveTitle("defy"));
                passiveLines.add(passiveDesc("defy",
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.75D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D)));
            } else if ("lightshield_strike".equals(effect.id)) {
                // 光盾打击（焚天）：对目标的第一次普攻必定暴击并回复生命（每目标 10 秒）
                passiveLines.add(passiveTitle("lightshield_strike"));
                passiveLines.add(passiveDesc("lightshield_strike",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.90D),
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.45D),
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.04D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 10.0D)));
            } else if ("shockwave".equals(effect.id)) {
                // 主动技「破阵冲击波」（挺进破坏者）
                passiveLines.add(activeTitle("shockwave"));
                passiveLines.add(activeDesc("shockwave",
                        formatPercent(effect.power_ratio > 0 ? effect.power_ratio : 0.80D),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.35D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 4.5D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 15.0D)));
            } else if ("torment".equals(effect.id)) {
                // 折磨（兰德里的折磨）：技能伤害灼烧目标（每 0.5 秒 1% 最大生命，3 秒）
                passiveLines.add(passiveTitle("torment"));
                passiveLines.add(passiveDesc("torment",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.02D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("suffering".equals(effect.id)) {
                // 受苦（兰德里的折磨）：作战每秒 +2% 伤害，至多 6%（附属词条）
                passiveLines.add(passiveDesc("suffering",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.02D),
                        formatPercent(effect.amount > 0 ? effect.amount * (effect.max_stacks > 0 ? effect.max_stacks : 3) : 0.06D)));
            } else if ("timeless".equals(effect.id)) {
                // 时无级（时光之杖）：每 60 秒成长一层，满层升 1 级
                passiveLines.add(passiveTitle("timeless"));
                passiveLines.add(passiveDesc("timeless",
                        formatNumber(effect.interval_seconds > 0 ? effect.interval_seconds : 60.0D),
                        formatNumber(effect.amount > 0 ? effect.amount : 10.0D),
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 30.0D),
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.03D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 10)));
            } else if ("iceborn_spellblade".equals(effect.id)) {
                // 咒刃（冰脉护手）：施法后下一次普攻额外物理伤害 + 冰冷地带
                passiveLines.add(passiveTitle("iceborn_spellblade"));
                passiveLines.add(passiveDesc("iceborn_spellblade",
                        formatPercent(effect.amount > 0 ? effect.amount : 1.50D),
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.25D),
                        formatPercent(effect.melee_ratio > 0 ? effect.melee_ratio : 0.125D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 3.0D)));
            } else if ("protean".equals(effect.id)) {
                // 虚空生物的复原力（千变者贾修）：战斗 5 秒后 +30% 护甲/魔抗直到战斗结束
                passiveLines.add(passiveTitle("protean"));
                passiveLines.add(passiveDesc("protean",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.30D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 5.0D)));
            } else if ("bring_it_down".equals(effect.id)) {
                // 放倒它（海妖杀手）：每第三次弹射物攻击造成额外物理伤害（已损失生命加成）
                passiveLines.add(passiveTitle("bring_it_down"));
                passiveLines.add(passiveDesc("bring_it_down",
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks + 1 : 3),
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 150.0D),
                        formatPercent(effect.amount > 0 ? effect.amount : 0.75D)));
            } else if ("shieldbow_lifeline".equals(effect.id)) {
                // 救主灵刃（不朽盾弓）：致命伤害 → 白盾 3 秒
                passiveLines.add(passiveTitle("shieldbow_lifeline"));
                passiveLines.add(passiveDesc("shieldbow_lifeline",
                        formatPercent(effect.trigger_health_percent > 0 ? effect.trigger_health_percent : 0.30D),
                        formatNumber(effect.shield_amount > 0 ? effect.shield_amount : 550.0D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 90.0D)));
            } else if ("naavori_flicker".equals(effect.id)) {
                passiveLines.add(passiveTitle("naavori_flicker"));
                passiveLines.add(passiveDesc("naavori_flicker",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.15D)));
            } else if ("collector_execute".equals(effect.id)) {
                passiveLines.add(passiveTitle("collector_execute"));
                passiveLines.add(passiveDesc("collector_execute",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.05D)));
            } else if ("collector_toll".equals(effect.id)) {
                passiveLines.add(passiveTitle("collector_toll"));
                passiveLines.add(passiveDesc("collector_toll",
                        formatNumber(effect.amount > 0 ? effect.amount : 25.0D)));
            } else if ("ever_rising_moon".equals(effect.id)) {
                passiveLines.add(passiveTitle("ever_rising_moon"));
                passiveLines.add(passiveDesc("ever_rising_moon",
                        formatNumber(effect.amount > 0 ? effect.amount : 150.0D),
                        formatPercent(effect.bonus_pct > 0 ? effect.bonus_pct : 0.40D),
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 75.0D),
                        formatPercent(effect.power_ratio > 0 ? effect.power_ratio : 0.20D),
                        formatPercent(effect.max_health_pct > 0 ? effect.max_health_pct : 0.08D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 6.0D)));
            } else if ("shield_reaver".equals(effect.id)) {
                passiveLines.add(passiveTitle("shield_reaver"));
                passiveLines.add(passiveDesc("shield_reaver",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.05D)));
            } else if ("wrath".equals(effect.id)) {
                // 鬼索·愤怒：普攻附带固定魔法伤害（攻击特效）
                passiveLines.add(passiveTitle("wrath"));
                passiveLines.add(passiveDesc("wrath"));
            } else if ("seething_strike".equals(effect.id)) {
                // 鬼索·汹涌打击：普攻叠攻速，满层后每第 3 次攻击额外再触发一次攻击特效
                passiveLines.add(passiveTitle("seething_strike"));
                passiveLines.add(passiveDesc("seething_strike",
                        formatPercent(effect.per_stack > 0 ? effect.per_stack : 0.08D),
                        formatNumber(effect.max_stacks > 0 ? effect.max_stacks : 4),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 4.0D)));
            } else if ("life_from_death".equals(effect.id)) {
                // 蜕生·死中新生：击杀位置爆发治疗新星，治疗自身与友方玩家
                passiveLines.add(passiveTitle("life_from_death"));
                passiveLines.add(passiveDesc("life_from_death",
                        formatNumber(effect.base_damage > 0 ? effect.base_damage : 100.0D),
                        formatPercent(effect.ap_power_ratio > 0 ? effect.ap_power_ratio : 0.20D),
                        formatNumber(effect.radius_blocks > 0 ? effect.radius_blocks : 4.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 60.0D)));
            } else if ("haunt".equals(effect.id)) {
                // 幽梦·萦绕：脱战移速
                passiveLines.add(passiveTitle("haunt"));
                passiveLines.add(passiveDesc("haunt",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.06D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D)));
            } else if ("mercurial".equals(effect.id)) {
                // 水银弯刀·水银：解除全部有害状态 + 短时移速
                passiveLines.add(activeTitle("mercurial"));
                passiveLines.add(activeDesc("mercurial",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.50D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 90.0D)));
            } else if ("wraith_step".equals(effect.id)) {
                // 幽梦·鬼步：短时移速 + 无视单位碰撞
                passiveLines.add(activeTitle("wraith_step"));
                passiveLines.add(activeDesc("wraith_step",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.20D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 6.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 45.0D)));
            } else if ("gunblade".equals(effect.id)) {
                // 主动技「闪电弹球」（海克斯科技枪刃）
                passiveLines.add(activeTitle("gunblade"));
                passiveLines.add(activeDesc("gunblade",
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 40.0D)));
            } else if ("rocketbelt".equals(effect.id)) {
                // 主动技「魔法弹突进」（海克斯科技火箭腰带）
                passiveLines.add(activeTitle("rocketbelt"));
                passiveLines.add(activeDesc("rocketbelt",
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 40.0D)));
            } else if ("maw_lifeline".equals(effect.id)) {
                // 被动「救主灵刃」（玛莫提乌斯之噬）
                passiveLines.add(passiveTitle("maw_lifeline"));
                passiveLines.add(passiveDesc("maw_lifeline"));
            } else if ("randuins_resilience".equals(effect.id)) {
                // 被动「坚韧」（兰顿之兆）：受到暴击伤害减免
                passiveLines.add(passiveTitle("randuins_resilience"));
                passiveLines.add(passiveDesc("randuins_resilience",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.30D)));
            } else if ("randuins_active".equals(effect.id)) {
                // 主动技「减速光环」（兰顿之兆）
                passiveLines.add(activeTitle("randuins_active"));
                passiveLines.add(activeDesc("randuins_active",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.35D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 2.0D),
                        formatNumber(effect.cooldown_seconds > 0 ? effect.cooldown_seconds : 60.0D)));
            } else if ("ruined_king_current".equals(effect.id)) {
                // 雾之锋（破败王者之刃）：普攻造成当前生命值额外伤害
                passiveLines.add(passiveTitle("ruined_king_current"));
                passiveLines.add(passiveDesc("ruined_king_current",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.08D)));
            } else if ("ruined_king_claw".equals(effect.id)) {
                // 抓挠之影（破败王者之刃）：同一目标第三次普攻造成短暂减速
                passiveLines.add(passiveTitle("ruined_king_claw"));
                passiveLines.add(passiveDesc("ruined_king_claw",
                        formatPercent(effect.amount > 0 ? effect.amount : 0.30D),
                        formatNumber(effect.duration_seconds > 0 ? effect.duration_seconds : 1.0D)));
            } else if ("maw_vengeful".equals(effect.id)) {
                // 被动「复仇之噬」（玛莫提乌斯之噬）
                passiveLines.add(passiveTitle("maw_vengeful"));
                passiveLines.add(passiveDesc("maw_vengeful",
                        formatNumber(effect.amount > 0 ? effect.amount : 20.0D),
                        formatNumber(effect.magic_resist_amount > 0 ? effect.magic_resist_amount : 35.0D)));
            }
        }

        // 终极技能冷却缩减：数值虽经属性机制注入（gear JSON attributes 的 lolaccessories:ultimate_cdr
        // 条目），但它本质是「唯一被动」技能（弩箭·守夜 / 注力刚壁·海克斯充能），被动标题按装备区分。
        if (ultimateCdr != null && ultimateCdr.resolved()) {
            String ucKey = "experimental_hexplate".equals(gearId) ? "hexcharged"
                    : "fiendhunter_bolts".equals(gearId) ? "vigil"
                    : "zekes_convergence".equals(gearId) ? "icicle_burn" : "ultimate_cdr";
            passiveLines.add(passiveTitle(ucKey));
            passiveLines.add(passiveDesc("ultimate_cdr",
                    ultimateCdr.percent ? formatPercent(ultimateCdr.amount) : formatNumber(ultimateCdr.amount)));
        }

        if (!passiveLines.isEmpty()) {
            if (statLines > 0) {
                tooltip.add(Component.empty());
            }
            tooltip.addAll(passiveLines);
        }

        // 风味文案：灰色斜体的小字，位于 tooltip 末尾（键：item.lolaccessories.<gearId>.lore）
        appendLore(tooltip);
    }

    /** 风味文案：若语言文件存在该装备的风味词条，则以灰色斜体追加到 tooltip 末尾。 */
    private void appendLore(List<Component> tooltip) {
        String loreKey = "item.lolaccessories." + gearId + ".lore";
        if (!hasLanguageKey(loreKey)) {
            return;
        }
        if (!tooltip.isEmpty()) {
            tooltip.add(Component.empty());
        }
        tooltip.add(Component.translatable(loreKey)
                .withStyle(ChatFormatting.GRAY, ChatFormatting.ITALIC));
    }

    /** 语言键是否存在。仅客户端渲染 tooltip 时才需检查；专用服务器不渲染，直接视为存在。 */
    private static boolean hasLanguageKey(String key) {
        if (!FMLEnvironment.dist.isDedicatedServer()) {
            return net.minecraft.client.resources.language.I18n.exists(key);
        }
        return true;
    }

    /** 被动标题行：如「唯一被动—切割」，品阶决定颜色与装饰前缀。优先取装备专属词条。 */
    private Component passiveTitle(String effectId) {
        return Component.literal(tierTitlePrefix)
                .append(Component.translatable(pickKey("passive", effectId, "title")))
                .withStyle(tierTitleColor);
    }

    /** 被动描述行，数值顺序由对应语言词条的占位符决定，颜色随品阶。优先取装备专属词条。 */
    private Component passiveDesc(String effectId, Object... args) {
        return Component.translatable(pickKey("passive", effectId, "desc"), args)
                .withStyle(tierDescColor);
    }

    /** 主动技标题行：如「唯一主动—凝滞」，品阶决定颜色与装饰前缀。优先取装备专属词条。 */
    private Component activeTitle(String skillId) {
        return Component.literal(tierTitlePrefix)
                .append(Component.translatable(pickKey("active", skillId, "title")))
                .withStyle(tierTitleColor);
    }

    /** 主动技描述行，数值顺序由对应语言词条的占位符决定，颜色随品阶。优先取装备专属词条。 */
    private Component activeDesc(String skillId, Object... args) {
        return Component.translatable(pickKey("active", skillId, "desc"), args)
                .withStyle(tierDescColor);
    }

    /**
     * 词条 key 解析：同一 effect id 常被多件装备共用（如提亚马特与贪欲九头蛇的
     * cleave/crescent、耀光与三相的 spellblade），装备专属词条
     * {@code <prefix>.lolaccessories.<gearId>.<id>.<suffix>} 存在时优先，否则回退通用词条。
     */
    private String pickKey(String prefix, String id, String suffix) {
        String specific = prefix + ".lolaccessories." + gearId + "." + id + "." + suffix;
        if (net.minecraft.locale.Language.getInstance().has(specific)) {
            return specific;
        }
        return prefix + ".lolaccessories." + id + "." + suffix;
    }

    private static String formatPercent(double value) {
        return formatNumber(Math.round(value * 1000.0) / 10.0) + "%";
    }

    private static String formatNumber(double value) {
        if (value == Math.floor(value) && !Double.isInfinite(value)) {
            return String.valueOf((long) value);
        }
        return String.format(Locale.ROOT, "%.1f", value);
    }
}
