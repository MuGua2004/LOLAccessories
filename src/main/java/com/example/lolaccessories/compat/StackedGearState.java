package com.example.lolaccessories.compat;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.item.GearItem;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;

import java.util.List;
import java.util.UUID;

/**
 * 「绑定玩家」叠层计数中枢（黑暗封印的荣耀 / 女神之泪的法力流）。
 *
 * <p>两条规则与物品本体解耦，层数真正存放在<b>玩家</b>的持久化 NBT 里（服务端，
 * {@code Entity#getPersistentData}，跨存档/重登保留）：</p>
 * <ul>
 *   <li>层数只在<b>装备该饰品时</b>才会增加/减少/产生属性加成（击杀、死亡、魔法命中等）；</li>
 *   <li>不装备时的击杀 / 死亡 / 命中一律不改层数。</li>
 * </ul>
 *
 * <p>动态加成不写进饰品自带的静态属性表（那样只能贴一个固定值），而是由本类在服务端按
 * 当前层数向玩家属性实例实时增删<b>瞬态修正器</b>：黑暗封印 → 铁魔法法术强度，
 * 女神之泪 → 铁魔法最大法力。层数同时镜像到 Curios 槽位里物品栈的 NBT，
 * 供 tooltip 显示当前数值。</p>
 */
public final class StackedGearState {

    public static final String DARK_SEAL_ITEM = "dark_seal";
    public static final String MEJAIS_ITEM = "mejais_soulstealer";
    public static final String TEAR_OF_GODDESS_ITEM = "tear_of_goddess";

    /** 黑暗封印 / 梅贾的窃魂卷<b>共享</b>的层数池 key（荣耀 Glory）。 */
    public static final String SEAL_STACK_KEY = "dark_seal_glory";
    public static final String TEAR_STACK_KEY = "tear_of_goddess_mana_flow";

    /** 物品栈 NBT 上镜像层数用的标签名（tooltip 读取）。 */
    public static final String TAG_STACKS = "lolaccessories_stacks";

    private static final String PLAYER_DATA_KEY = "lolaccessories";
    private static final UUID SEAL_MODIFIER = UUID.fromString("6a2c9d14-88a1-4b9c-a4d3-2f6e7a9c0d1e");
    private static final UUID TEAR_MODIFIER = UUID.fromString("7b3da6b8-2f4e-4a91-b5c8-4f90e1a2b3c4");
    /** 梅贾的窃魂卷：层数达标移速修正器（MULTIPLY_BASE）。 */
    private static final UUID MEJAIS_MS_MODIFIER = UUID.fromString("8c4eb7c9-3a5f-4b02-a6d9-5e01f2b3c4d5");

    private StackedGearState() {
    }

    /** 读取 tooltip 用的层数（默认 0）。 */
    public static int readStackNbt(ItemStack stack) {
        CompoundTag tag = stack.getTag();
        if (tag != null && tag.contains(TAG_STACKS, CompoundTag.TAG_INT)) {
            return tag.getInt(TAG_STACKS);
        }
        return 0;
    }

    public static int getStacks(Player player, String stackKey) {
        return stacksTag(player).getInt(stackKey);
    }

    public static void setStacks(Player player, String stackKey, int stacks) {
        stacksTag(player).putInt(stackKey, Math.max(0, stacks));
    }

    /** 叠加（自动封顶到 cap，cap ≤ 0 表示不封顶）。 */
    public static int addStacks(Player player, String stackKey, int gain, int cap) {
        int current = getStacks(player, stackKey);
        int next = cap > 0 ? Math.min(cap, current + gain) : current + gain;
        setStacks(player, stackKey, next);
        return next;
    }

    /** 衰减（至少保留到 0 层）。 */
    public static int loseStacks(Player player, String stackKey, int loss) {
        int current = getStacks(player, stackKey);
        int next = Math.max(0, current - loss);
        setStacks(player, stackKey, next);
        return next;
    }

    /**
     * 根据当前佩戴状态与层数，重算「荣耀/法力流」的动态属性加成并刷新 Curios 槽位上物品的
     * 层数镜像。任何层数或佩戴状态的变化后都应调用一次（也由逐秒 tick 兜底）。
     */
    public static void ensureModifiers(Player player) {
        if (player == null || player.level().isClientSide) {
            return;
        }
        ensureDarkSeal(player);
        ensureTearOfGoddess(player);
    }

    private static void ensureDarkSeal(Player player) {
        GearConfig.OnHitEffect sealGlory = gloryEffect(DARK_SEAL_ITEM);
        GearConfig.OnHitEffect mejaisGlory = gloryEffect(MEJAIS_ITEM);
        if ((sealGlory == null || !sealGlory.enabled)
                && (mejaisGlory == null || !mejaisGlory.enabled)) {
            return;
        }
        // 黑暗封印与梅贾的窃魂卷共享同一层数池（SEAL_STACK_KEY），全局只佩戴其一
        //（两件配置 family_group = seal_family 互斥）。层数本身始终保留在玩家身上：
        boolean wearingSeal = CuriosGearWear.isWearing(player, DARK_SEAL_ITEM);
        boolean wearingMejais = CuriosGearWear.isWearing(player, MEJAIS_ITEM);
        int stacks = getStacks(player, SEAL_STACK_KEY);

        // 法术强度：佩戴黑暗封印 → 只有前 max_stacks（10）层生效；
        // 佩戴梅贾 → 全部层数生效（最多 20 层），每层法强按梅贾配置（5%）。
        double spellPower = 0.0D;
        if (wearingSeal && sealGlory != null && sealGlory.enabled) {
            int cap = Math.max(1, (int) Math.round(sealGlory.max_stacks));
            spellPower = Math.min(stacks, cap) * sealGlory.amount;
        } else if (wearingMejais && mejaisGlory != null && mejaisGlory.enabled) {
            spellPower = stacks * mejaisGlory.amount;
        }
        // 每层 +法术强度（百分数）：铁魔法属性默认值 1.0，直接 ADDITION 即净加成
        applyDynamic(player, IronsCompat.SPELL_POWER, SEAL_MODIFIER,
                spellPower, "lolaccessories:glory_stack");

        // 梅贾专属：层数达到阈值（10）后提供移速加成（MULTIPLY_BASE）
        double moveSpeed = 0.0D;
        if (wearingMejais && mejaisGlory != null && mejaisGlory.enabled
                && mejaisGlory.move_speed_ratio > 0.0D) {
            int threshold = (int) Math.round(mejaisGlory.move_speed_stack_threshold);
            if (threshold <= 0 || stacks >= threshold) {
                moveSpeed = mejaisGlory.move_speed_ratio;
            }
        }
        applyDynamic(player, "minecraft:generic.movement_speed", MEJAIS_MS_MODIFIER,
                moveSpeed, "lolaccessories:mejais_swift", true);

        if (wearingSeal) {
            syncCurioStackNbt(player, DARK_SEAL_ITEM, SEAL_STACK_KEY);
        }
        if (wearingMejais) {
            syncCurioStackNbt(player, MEJAIS_ITEM, SEAL_STACK_KEY);
        }
    }

    private static void ensureTearOfGoddess(Player player) {
        GearConfig.OnHitEffect flow = manaFlowEffect();
        if (flow == null || !flow.enabled) {
            return;
        }
        boolean wearing = CuriosGearWear.isWearing(player, TEAR_OF_GODDESS_ITEM);
        int stacks = wearing ? getStacks(player, TEAR_STACK_KEY) : 0;
        // 每次命中 +amount 点最大法力（平坦加成）
        applyDynamic(player, IronsCompat.MAX_MANA, TEAR_MODIFIER,
                stacks * flow.amount, "lolaccessories:tear_mana_flow");
        syncCurioStackNbt(player, TEAR_OF_GODDESS_ITEM, TEAR_STACK_KEY);
    }

    /** 在玩家属性实例上增删一个瞬态 ADDITION 修正器（value ≤ 0 时移除）。 */
    private static void applyDynamic(Player player, String attributeId, UUID uuid,
                                     double value, String name) {
        applyDynamic(player, attributeId, uuid, value, name, false);
    }

    /**
     * 在玩家属性实例上增删一个瞬态修正器（value ≤ 0 时移除）。
     *
     * @param multiplyBase true 使用 MULTIPLY_BASE（百分比语义，如移速 +10%），false 用 ADDITION
     */
    private static void applyDynamic(Player player, String attributeId, UUID uuid,
                                     double value, String name, boolean multiplyBase) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            return;
        }
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return; // 未装铁魔法等导致属性不存在时静默跳过
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        // 数值未变时直接跳过：叠层修正器只是「快照」，不装备后也会被本类的逐秒兜底重建，
        // 因此无需每秒 remove+add 反复扰动玩家属性实例（也能减少对其它模组同时刻遍历的影响）
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null && existing.getAmount() == value) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        if (value > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(uuid, name, value,
                    multiplyBase ? AttributeModifier.Operation.MULTIPLY_BASE
                            : AttributeModifier.Operation.ADDITION));
        }
    }

    /** 把玩家的层数镜像写入 Curios 槽位中对应饰品的栈 NBT（tooltip 用）。 */
    private static void syncCurioStackNbt(Player player, String gearId, String stackKey) {
        int count = getStacks(player, stackKey);
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
            List<SlotResult> worn = handler.findCurios(stack -> stack.getItem() instanceof GearItem gear
                    && gearId.equals(gear.getGearId()));
            for (SlotResult result : worn) {
                result.stack().getOrCreateTag().putInt(TAG_STACKS, count);
            }
        });
    }

    private static GearConfig.OnHitEffect gloryEffect(String gearId) {
        return GearConfigManager.get(gearId).findEffect("glory").orElse(null);
    }

    private static GearConfig.OnHitEffect manaFlowEffect() {
        return GearConfigManager.get(TEAR_OF_GODDESS_ITEM).findEffect("mana_flow").orElse(null);
    }

    /** 读取/创建玩家持久化 NBT 里的叠层数据区。 */
    private static CompoundTag stacksTag(Player player) {
        CompoundTag root = player.getPersistentData();
        if (!root.contains(PLAYER_DATA_KEY, CompoundTag.TAG_COMPOUND)) {
            root.put(PLAYER_DATA_KEY, new CompoundTag());
        }
        return root.getCompound(PLAYER_DATA_KEY);
    }
}
