package com.example.lolaccessories.advancement;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModItemTags;
import com.example.lolaccessories.init.ModItems;
import com.example.lolaccessories.item.GearItem;
import net.minecraft.advancements.Advancement;
import net.minecraft.core.Holder;
import net.minecraft.core.Registry;
import net.minecraft.core.registries.Registries;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.tags.TagKey;
import net.minecraft.world.entity.player.Inventory;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.items.IItemHandlerModifiable;
import net.minecraftforge.registries.ForgeRegistries;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * 成就（进度）解锁服务。所有进度都由本服务在服务端判定后解锁，数据文件由
 * {@code ModAdvancementProvider} 生成（准则统一为 {@code minecraft:impossible}，
 * 不会自行触发，只能由这里调用 {@code award} 手动授予）。
 *
 * <p>进度口径：
 * <ul>
 *   <li><b>图鉴（第 1 件 / 集齐）</b>：凡是经手过（进过背包或饰品栏）的 LOLAccessories 装备
 *       永久记入玩家 NBT 的「已见集合」，卖掉 / 消耗 / 升级都不回退；每 2 秒扫一次玩家背包与
 *       Curios 槽位做增量判定；</li>
 *   <li><b>佩戴件数</b>：同一时间佩戴在 Curios 饰品栏里的 LOLAccessories 装备总数（含非玩家
 *       默认槽，整合包可通过 Curios 扩展槽位满足 100 件目标），达到阈值即授予；</li>
 *   <li><b>金币 / 合成 / 主动技能</b>：由各自事件即时钩子调用。</li>
 * </ul>
 * </p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolAdvancementService {

    /** 每件进度 JSON 里唯一的准则名（trigger = minecraft:impossible，由本类手动授予）。 */
    private static final String CRITERION = "unlock";
    private static final String SEEN_KEY = "adv_seen";

    // ---------- 进度 id（与 data/lolaccessories/advancements/<id>.json 一一对应） ----------
    public static final String ID_FIRST_COMMON = "heroes_journey";
    public static final String ID_ALL_COMMON = "hello_world";
    public static final String ID_FIRST_EPIC = "epic_ballad";
    public static final String ID_ALL_EPIC = "so_called_heroes";
    public static final String ID_FIRST_LEGEND = "legend_journey";
    public static final String ID_ALL_LEGEND = "endless_night";
    public static final String ID_FIRST_MYTH = "myth_writing";
    public static final String ID_ALL_MYTH = "undefeated_brave";
    public static final String ID_ALL_GEAR = "champions";
    public static final String ID_FIRST_GOLD = "first_gold";
    public static final String ID_FIRST_CRAFT = "first_craft";
    public static final String ID_HERO_POWER = "hero_power";
    /** 成就【遥远于世的幻想乡】：累计造成过 100 亿铁魔法法术伤害。 */
    public static final String ID_FANTASY_LAND = "fantasy_land";
    /** 成就【真理的使者】：完成【我们是冠军】后自动授予（致明日之诗的解锁进度）。 */
    public static final String ID_TRUTH_ENVOY = "truth_envoy";
    /** 不计入「集齐装备」统计的装备（自身依赖集齐，参与统计会死锁）。 */
    private static final Set<String> EXCLUDED_FROM_COLLECTION = Set.of("lolaccessories:poem_for_tomorrow");
    /** 遥远于世的幻想乡阈值：100 亿点（按最终结算伤害累计）。 */
    private static final double FANTASY_LAND_SPELL_DAMAGE = 1.0e10D;
    /** 玩家 NBT 里累计法术伤害的键（lolaccessories 复合标签）。 */
    private static final String SPELL_DAMAGE_KEY = "spell_damage_total";
    public static final String ID_WEARING_5 = "wearing_5";
    public static final String ID_WEARING_10 = "wearing_10";
    public static final String ID_WEARING_20 = "wearing_20";
    public static final String ID_WEARING_30 = "wearing_30";
    public static final String ID_WEARING_50 = "wearing_50";
    public static final String ID_WEARING_100 = "wearing_100";

    /** 品阶标签（下标 0=普通 1=史诗 2=传说 3=神话）。 */
    private static final TagKey<Item>[] TIER_TAGS = new TagKey[]{
            ModItemTags.TIER1, ModItemTags.TIER2, ModItemTags.TIER3, ModItemTags.TIER4};

    /** 每个玩家最近一次统计到的佩戴件数（避免每次扫描都重复判定阈值）。 */
    private static final Map<UUID, Integer> LAST_WORN = new HashMap<>();

    /** 背包扫描间隔（游戏刻），2 秒一次，成本极低。 */
    private static final int SCAN_INTERVAL = 40;

    private LolAdvancementService() {
    }

    // ================= 外部事件钩子（即时授予） =================

    /** 第一次捡到金币。 */
    public static void onGoldCollected(ServerPlayer player) {
        grant(player, ID_FIRST_GOLD);
    }

    /** 第一次合成出装备（工作台合出装备 / 付费锻造取件）。由对应事件在服务端调用。 */
    public static void onGearCrafted(ServerPlayer player, ItemStack result) {
        if (result != null && !result.isEmpty() && result.getItem() instanceof GearItem) {
            grant(player, ID_FIRST_CRAFT);
        }
    }

    /** 第一次成功释放主动技能（服务端结算完成后调用）。 */
    public static void onActiveSkillCast(ServerPlayer player) {
        grant(player, ID_HERO_POWER);
    }

    /**
     * 累计铁魔法法术伤害（成就【遥远于世的幻想乡】：累计造成过 100 亿点）。
     * 由法术命中事件在服务端调用（对所有玩家生效，与是否佩戴翡翠城无关）。
     * 累计值写入玩家持久化 NBT，跨存档保留；达到阈值即授予并停止累计。
     */
    public static void onSpellDamageDealt(ServerPlayer player, double amount) {
        if (amount <= 0.0D) {
            return;
        }
        CompoundTag data = dataOf(player);
        if (data.contains(SPELL_DAMAGE_KEY, CompoundTag.TAG_DOUBLE)
                && data.getDouble(SPELL_DAMAGE_KEY) >= FANTASY_LAND_SPELL_DAMAGE) {
            return;
        }
        double total = data.getDouble(SPELL_DAMAGE_KEY) + amount;
        if (total >= FANTASY_LAND_SPELL_DAMAGE) {
            data.putDouble(SPELL_DAMAGE_KEY, FANTASY_LAND_SPELL_DAMAGE);
            saveData(player, data);
            grant(player, ID_FANTASY_LAND);
            LOLAccessories.LOGGER.info("[成就] {} 达成遥远于世的幻想乡：累计法术伤害 100 亿",
                    player.getName().getString());
        } else {
            data.putDouble(SPELL_DAMAGE_KEY, total);
            saveData(player, data);
        }
    }

    // ================= 图鉴扫描 / 佩戴计数 =================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        if (player.level().isClientSide) {
            return;
        }
        // 每 2 秒扫一次，避免每 tick 遍历背包
        if ((player.tickCount % SCAN_INTERVAL) != 0) {
            return;
        }

        CompoundTag data = dataOf(player);
        Set<String> seen = readSeen(data);
        if (markSeen(player, seen)) {
            writeSeen(data, seen);
            saveData(player, data);
            evaluateCollection(player, seen);
        }

        int worn = countWornGear(player);
        Integer prev = LAST_WORN.get(player.getUUID());
        int prevCount = prev == null ? 0 : prev;
        if (prev == null || worn != prevCount) {
            LAST_WORN.put(player.getUUID(), worn);
            if (worn > prevCount) {
                evaluateWorn(player, worn);
            }
        }
    }

    @SubscribeEvent
    public static void onPickup(PlayerEvent.ItemPickupEvent event) {
        if (event.getEntity() instanceof ServerPlayer player
                && !event.getStack().isEmpty()
                && event.getStack().is(ModItems.GOLD_COIN.get())) {
            grant(player, ID_FIRST_GOLD);
        }
    }

    @SubscribeEvent
    public static void onCraft(PlayerEvent.ItemCraftedEvent event) {
        ItemStack result = event.getCrafting();
        if (event.getEntity() instanceof ServerPlayer player) {
            onGearCrafted(player, result);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        LAST_WORN.remove(event.getEntity().getUUID());
    }

    /**
     * 把玩家背包与 Curios 槽里出现的所有装备 id 记进「已见集合」。
     *
     * @return 本次是否新增了装备（新增才需要落盘并重算图鉴进度）
     */
    private static boolean markSeen(Player player, Set<String> seen) {
        boolean changed = false;
        Inventory inventory = player.getInventory();
        for (int i = 0; i < inventory.getContainerSize(); i++) {
            ItemStack stack = inventory.getItem(i);
            if (!stack.isEmpty() && stack.getItem() instanceof GearItem) {
                changed |= seen.add(itemId(stack.getItem()));
            }
        }
        var curiosOptional = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOptional.isPresent()) {
            // 遍历 Curios 所有类型槽位（含非玩家默认槽）
            var curios = curiosOptional.get();
            for (var stacksHandler : curios.getCurios().values()) {
                IItemHandlerModifiable curiosInventory = stacksHandler.getStacks();
                for (int i = 0; i < curiosInventory.getSlots(); i++) {
                    ItemStack stack = curiosInventory.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof GearItem) {
                        changed |= seen.add(itemId(stack.getItem()));
                    }
                }
            }
        }
        return changed;
    }

    /** 当前佩戴在 Curios 槽位（任意槽类型）上的 LOLAccessories 装备总数。 */
    private static int countWornGear(Player player) {
        var curiosOptional = CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOptional.isEmpty()) {
            return 0;
        }
        int count = 0;
        var curios = curiosOptional.get();
        for (var stacksHandler : curios.getCurios().values()) {
            IItemHandlerModifiable curiosInventory = stacksHandler.getStacks();
            for (int i = 0; i < curiosInventory.getSlots(); i++) {
                ItemStack stack = curiosInventory.getStackInSlot(i);
                if (!stack.isEmpty() && stack.getItem() instanceof GearItem) {
                    count += stack.getCount();
                }
            }
        }
        return count;
    }

    /** 佩戴件数达到的里程碑阈值 → 对应进度 id。 */
    private static void evaluateWorn(ServerPlayer player, int worn) {
        if (worn >= 5) {
            grant(player, ID_WEARING_5);
        }
        if (worn >= 10) {
            grant(player, ID_WEARING_10);
        }
        if (worn >= 20) {
            grant(player, ID_WEARING_20);
        }
        if (worn >= 30) {
            grant(player, ID_WEARING_30);
        }
        if (worn >= 50) {
            grant(player, ID_WEARING_50);
        }
        if (worn >= 100) {
            grant(player, ID_WEARING_100);
        }
    }

    /** 图鉴判定：第 1 件 / 集齐整级 / 集齐全部。只在「已见集合」新增后调用。 */
    private static void evaluateCollection(ServerPlayer player, Set<String> seen) {
        Registry<Item> registry = player.server.registryAccess().registryOrThrow(Registries.ITEM);
        int totalAll = 0;
        int haveAll = 0;
        for (int tier = 0; tier < TIER_TAGS.length; tier++) {
            int[] count = countInTier(registry, TIER_TAGS[tier], seen);
            int total = count[0];
            int have = count[1];
            if (total <= 0) {
                continue; // 该品阶当前没有装备（如神话），相关进度自然无法达成
            }
            totalAll += total;
            haveAll += have;
            switch (tier) {
                case 0 -> {
                    if (have >= 1) {
                        grant(player, ID_FIRST_COMMON);
                    }
                    if (have >= total) {
                        grant(player, ID_ALL_COMMON);
                    }
                }
                case 1 -> {
                    if (have >= 1) {
                        grant(player, ID_FIRST_EPIC);
                    }
                    if (have >= total) {
                        grant(player, ID_ALL_EPIC);
                    }
                }
                case 2 -> {
                    if (have >= 1) {
                        grant(player, ID_FIRST_LEGEND);
                    }
                    if (have >= total) {
                        grant(player, ID_ALL_LEGEND);
                    }
                }
                case 3 -> {
                    if (have >= 1) {
                        grant(player, ID_FIRST_MYTH);
                    }
                    if (have >= total) {
                        grant(player, ID_ALL_MYTH);
                    }
                }
                default -> {
                }
            }
        }
        if (totalAll > 0 && haveAll >= totalAll) {
            grant(player, ID_ALL_GEAR);
            // 成就【真理的使者】：完成「我们是冠军」后自动授予（联动发放致明日之诗）
            grant(player, ID_TRUTH_ENVOY);
        }
    }

    /** 统计玩家已见集合中某品阶标签的装备种类数（供神话挑战等外部判定）。 */
    public static int countSeenInTier(ServerPlayer player, String tagPath) {
        Registry<Item> registry = player.server.registryAccess().registryOrThrow(Registries.ITEM);
        Set<String> seen = readSeen(dataOf(player));
        return countInTier(registry, TagKey.create(Registries.ITEM,
                new ResourceLocation(LOLAccessories.MOD_ID, tagPath)), seen)[1];
    }

    /** 统计某品阶装备总数与玩家已见数。 */
    private static int[] countInTier(Registry<Item> registry, TagKey<Item> tag, Set<String> seen) {
        Optional<? extends net.minecraft.core.HolderSet.Named<Item>> holders = registry.getTag(tag);
        if (holders.isEmpty()) {
            return new int[]{0, 0};
        }
        int total = 0;
        int have = 0;
        for (Holder<Item> holder : holders.get()) {
            ResourceLocation key = registry.getKey(holder.value());
            // 致明日之诗自身的解锁条件是 champions（集齐全部装备），参与统计会死锁——排除
            if (key != null && EXCLUDED_FROM_COLLECTION.contains(key.toString())) {
                continue;
            }
            total++;
            if (key != null && seen.contains(key.toString())) {
                have++;
            }
        }
        return new int[]{total, have};
    }

    /** 把某装备的注册名（如 lolaccessories:black_cleaver）转成持久化 key。 */
    private static String itemId(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        return key == null ? "" : key.toString();
    }

    // ================= 授予 =================

    /** 手动授予一条进度（准则 unlock）。已达成 / 不存在则静默跳过。供外部（神话挑战等）调用。 */
    public static void grantAdvancement(ServerPlayer player, String id) {
        grant(player, id);
    }

    private static void grant(ServerPlayer player, String id) {
        Advancement advancement = player.server.getAdvancements()
                .getAdvancement(new ResourceLocation(LOLAccessories.MOD_ID, id));
        if (advancement == null) {
            return;
        }
        if (player.getAdvancements().getOrStartProgress(advancement).isDone()) {
            return;
        }
        if (player.getAdvancements().award(advancement, CRITERION)) {
            LOLAccessories.LOGGER.info("[进度] {} 达成进度 {}", player.getName().getString(), id);
        }
    }

    // ================= 玩家 NBT 持久化（图鉴「已见集合」） =================

    private static CompoundTag dataOf(Player player) {
        return player.getPersistentData().getCompound(LOLAccessories.MOD_ID);
    }

    private static void saveData(Player player, CompoundTag data) {
        player.getPersistentData().put(LOLAccessories.MOD_ID, data);
    }

    private static Set<String> readSeen(CompoundTag data) {
        Set<String> seen = new HashSet<>();
        ListTag list = data.getList(SEEN_KEY, net.minecraft.nbt.Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            seen.add(list.getString(i));
        }
        return seen;
    }

    private static void writeSeen(CompoundTag data, Set<String> seen) {
        ListTag list = new ListTag();
        for (String id : seen) {
            if (!id.isEmpty()) {
                list.add(StringTag.valueOf(id));
            }
        }
        data.put(SEEN_KEY, list);
    }
}
