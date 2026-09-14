package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.compat.StackedGearState;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModItems;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.GearFxBroadcast;
import net.minecraft.ChatFormatting;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.chat.Component;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.living.MobEffectEvent;
import net.minecraftforge.event.TickEvent.Phase;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.CuriosApi;
import top.theillusivec4.curios.api.SlotResult;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 女神泪系列（法力流 Manaflow）传说装备的服务端结算。
 *
 * <p>规则（用户 2026-09-09 重申）：</p>
 * <ul>
 *   <li><b>唯一叠层条</b>：层数（额外法力 0~360）绑定玩家持久化 NBT，全系列共享同一叠层条；
 *       无论更换哪件未蜕变装备，层数都延续；</li>
 *   <li><b>叠层</b>：佩戴「未蜕变」装备时，普攻/技能命中获得层数（+3，命中玩家 +6，
 *       对应 LoL 每次 3/6 额外法力）；佩戴蜕变版后<b>无法再叠层</b>；</li>
 *   <li><b>蜕变</b>：层数叠满 360 时，检测佩戴中的未蜕变装备并令其蜕变（原位替换为独立注册的
 *       蜕变版物品），随后<b>清空叠层</b>；佩戴未蜕变装备时若叠层条已满也会立即蜕变；</li>
 *   <li><b>退化</b>：蜕变版被叠层不足的玩家佩戴时立即退化回前级（刚完成蜕变者豁免 5 秒，
 *       避免自我退化死循环）；</li>
 *   <li><b>系列互斥</b>：同 family_group（tear_family）装备全局仅允许佩戴一件，
 *       配置里 family_group 留空即关闭（见 GearItem.canEquip）。</li>
 * </ul>
 *
 * <p>各装备被动：</p>
 * <ul>
 *   <li><b>Awe 敬畏</b>（魔宗/魔切：额外攻击力；大天使/炽天使：法术强度；凛冬/冬之誓：生命值）
 *       = 比率 × 额外法力（叠层 + 装备本体法力），动态属性每秒刷新；</li>
 *   <li><b>Harmony 和谐</b>（耳语头环/歌之权冠）：治疗与护盾强度 = 0.5% × 额外法力，动态刷新；</li>
 *   <li><b>Shock 冲击</b>（魔切）：普攻命中附加 1.2% 最大法力的物理伤害；技能命中附加
 *       4%（近战）/3%（远程）最大法力（同目标 6.5 秒内只触发一次）；</li>
 *   <li><b>Lifeline 应急护盾</b>（炽天使之拥）：受到将使生命低于 30% 的伤害时，获得
 *       18% 最大法力的护盾 3 秒（90 秒冷却，走 ShieldHpService 黄心）；</li>
 *   <li><b>Everlasting 永恒</b>（冬之誓）：对敌人施加移动减速效果时，获得
 *       100 + 4.5% 最大法力的护盾 3 秒（8 秒冷却；附近多名敌人时提升 80%）；</li>
 *   <li><b>Consonance 共鸣</b>（歌之权冠）：每秒治疗范围内血量百分比最低的友方玩家
 *       0.8% 最大法力（仅当附近存在可交战目标）。</li>
 * </ul>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolTearFamilyEvents {

    /** 全系列唯一叠层条（玩家持久化 NBT 键），语义为「额外法力值」0~360。 */
    public static final String STACK_KEY = "tear_family_stack";
    /** 叠层上限（LoL：360 额外法力）。 */
    public static final int STACK_CAP = 360;
    /** 每次命中获得的层数（对应 LoL 每次 +3 额外法力）。 */
    public static final int HIT_MANA = 3;
    /** 命中玩家时获得的层数（对应 LoL 对英雄 +6）。 */
    public static final int HIT_PLAYER_MANA = 6;
    /** 充能周期（LoL：每 8 秒获得一层充能）。 */
    private static final long CHARGE_PERIOD_MS = 8000L;
    /** 玩家 NBT：当前充能层数。 */
    public static final String CHARGES_KEY = "tear_family_charges";
    /** 玩家 NBT：上次充能时刻。 */
    private static final String CHARGE_TIME_KEY = "tear_family_charge_time";

    /** 未蜕变 → 蜕变。 */
    private static final Map<String, String> TRANSFORMS = Map.of(
            "manamune", "muramana",
            "archangels_staff", "seraphs_embrace",
            "winters_approach", "fimbulwinter",
            "whispering_circlet", "diadem_of_songs");

    /** 蜕变 → 前级（仅供工具查询）。 */
    private static final Map<String, String> PREV = Map.of(
            "muramana", "manamune",
            "seraphs_embrace", "archangels_staff",
            "fimbulwinter", "winters_approach",
            "diadem_of_songs", "whispering_circlet");

    /**
     * Awe/Harmony 动态属性：gearId → { 属性id, 每点额外法力的加成 }。
     *
     * <p><b>单位（务必区分两类）：</b></p>
     * <ul>
     *   <li><b>点数属性</b>（攻击力 / 最大生命）：比率 = 每点额外法力提供的属性<b>点数</b>。
     *       魔宗/魔切 0.02 → 1000 额外法力 = +20 攻击力；凛冬/冬之誓 0.15 → +150 生命。</li>
     *   <li><b>倍率属性</b>（铁法法术强度 / 治疗与护盾强度，默认 1.0 = 100%）：比率 = 每点额外
     *       法力提供的<b>百分比（小数）</b>。炽天使 0.0002 → 1000 额外法力 = +20% 法强
     *       （此前误配 0.02，放大 100 倍成 +2000%，已修复）；大天使 0.0001；和谐 0.00005
     *       （1000 额外法力 = +5% 治疗与护盾强度）。</li>
     * </ul>
     */
    private static final Map<String, AweEntry> AWES = Map.of(
            "manamune", new AweEntry("minecraft:generic.attack_damage", 0.02D),
            "muramana", new AweEntry("minecraft:generic.attack_damage", 0.02D),
            "archangels_staff", new AweEntry(IronsCompat.SPELL_POWER, 0.0001D),
            "seraphs_embrace", new AweEntry(IronsCompat.SPELL_POWER, 0.0002D),
            "winters_approach", new AweEntry("minecraft:generic.max_health", 0.15D),
            "fimbulwinter", new AweEntry("minecraft:generic.max_health", 0.15D),
            "whispering_circlet", new AweEntry("lolaccessories:heal_power", 0.00005D),
            "diadem_of_songs", new AweEntry("lolaccessories:heal_power", 0.00005D));

    private static final long SHOCK_TARGET_CD_MS = 6500L;
    private static final long SERAPH_CD_MS = 90000L;
    private static final long FIMBUL_CD_MS = 8000L;

    /** Shock 的同目标触发冷却。 */
    private static final Map<UUID, Map<Integer, Long>> SHOCK_CD = new HashMap<>();
    /** 炽天使应急护盾冷却。 */
    private static final Map<UUID, Long> SERAPH_CD = new HashMap<>();
    /** 冬之誓永恒护盾冷却。 */
    private static final Map<UUID, Long> FIMBUL_CD = new HashMap<>();

    private record AweEntry(String attributeId, double ratio) {
    }

    private LolTearFamilyEvents() {
    }

    // ===================== 叠层 + Shock + Lifeline =====================

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)
                || attacker == victim || !attacker.isAlive()) {
            return;
        }
        // 目标友善/被动不参与（口径与其它装备一致）
        boolean validTarget = LolLegendPassiveEvents.isLegendaryTarget(attacker, victim);

        String worn = wornUntransformedTear(attacker);
        if (worn != null) {
            // 叠层：消耗 1 层充能，命中 +3（本模组无英雄/野怪之分，统一数值）
            if (consumeCharge(attacker, worn)) {
                int next = addStacks(attacker, HIT_MANA);
                if (next >= STACK_CAP) {
                    tryTransform(attacker);
                }
            }
        }

        EntityDamageContext ctx = new EntityDamageContext(event);
        boolean direct = ctx.direct();
        boolean spell = IronsCompat.isIronSpellDamage(event.getSource());

        // Shock（魔切）
        if (validTarget && CuriosGearWear.isWearing(attacker, "muramana")) {
            double maxMana = maxMana(attacker);
            if (spell) {
                // 技能命中：按用户规则，近战/远程数值区分一律取近战值（4%）；同目标 6.5 秒一次
                long now = System.currentTimeMillis();
                Map<Integer, Long> cds = SHOCK_CD.computeIfAbsent(attacker.getUUID(), k -> new HashMap<>());
                Long until = cds.get(victim.getId());
                if (until == null || until <= now) {
                    cds.put(victim.getId(), now + SHOCK_TARGET_CD_MS);
                    event.setAmount(event.getAmount() + (float) (maxMana * 0.04D));
                }
            } else if (direct) {
                // 普攻命中：统一 1.2%（无英雄/野怪之分）
                event.setAmount(event.getAmount() + (float) (maxMana * 0.012D));
            }
        }
    }

    // ===================== Everlasting（冬之誓） =====================

    @SubscribeEvent
    public static void onMobEffectAdded(MobEffectEvent.Added event) {
        if (event.getEntity().level().isClientSide
                || !(event.getEffectSource() instanceof ServerPlayer applier)) {
            return;
        }
        LivingEntity target = event.getEntity();
        if (!CuriosGearWear.isWearing(applier, "fimbulwinter")
                || !event.getEffectInstance().getEffect().equals(MobEffects.MOVEMENT_SLOWDOWN)
                || !LolLegendPassiveEvents.isLegendaryTarget(applier, target)) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = FIMBUL_CD.get(applier.getUUID());
        if (until != null && until > now) {
            return;
        }
        FIMBUL_CD.put(applier.getUUID(), now + FIMBUL_CD_MS);
        double maxMana = maxMana(applier);
        // 附近 9 格内可交战目标 ≥ 2 时提升 80%
        int nearby = 0;
        for (LivingEntity e : applier.level().getEntitiesOfClass(LivingEntity.class,
                applier.getBoundingBox().inflate(9.0D),
                e -> e != applier && LolLegendPassiveEvents.isLegendaryTarget(applier, e))) {
            if (++nearby >= 2) {
                break;
            }
        }
        float amount = (float) (100.0D + maxMana * 0.045D);
        if (nearby >= 2) {
            amount *= 1.8F;
        }
        ShieldHpService.apply(applier, "fimbulwinter", amount, 3000L, ShieldType.WHITE);
        // 冰蓝寒霜球罩特效（与护盾同窗口 3 秒）+ 冷却条 HUD
        GearFxBroadcast.window(applier, FxKind.SHIELD_FIMBULWINTER, 60);
        com.example.lolaccessories.networking.LOLNetworking.sendSkillCooldown(applier,
                "fimbulwinter", (int) (FIMBUL_CD_MS / 50L));
    }

    // ===================== Lifeline（炽天使之拥） =====================

    @SubscribeEvent
    public static void onSeraphLifeline(LivingHurtEvent event) {
        if (event.isCanceled() || event.getAmount() <= 0.0F
                || !(event.getEntity() instanceof ServerPlayer victim)
                || victim.level().isClientSide) {
            return;
        }
        if (!CuriosGearWear.isWearing(victim, "seraphs_embrace")) {
            return;
        }
        float wouldBe = victim.getHealth() - event.getAmount();
        if (wouldBe > victim.getMaxHealth() * 0.30F) {
            return;
        }
        long now = System.currentTimeMillis();
        Long until = SERAPH_CD.get(victim.getUUID());
        if (until != null && until > now) {
            return;
        }
        SERAPH_CD.put(victim.getUUID(), now + SERAPH_CD_MS);
        double maxMana = maxMana(victim);
        ShieldHpService.apply(victim, "seraphs_embrace", (float) (maxMana * 0.18D), 3000L,
                ShieldType.WHITE);
        // 金白圣光球罩特效（与护盾同窗口 3 秒）+ 冷却条 HUD
        GearFxBroadcast.window(victim, FxKind.SHIELD_SERAPH, 60);
        com.example.lolaccessories.networking.LOLNetworking.sendSkillCooldown(victim,
                "seraphs_embrace", (int) (SERAPH_CD_MS / 50L));
    }

    // ===================== 每秒：动态属性 / 蜕变 / 共鸣 / 镜像 =====================

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)
                || player.tickCount % 20 != 0) {
            return;
        }
        // 充能积累：每 8 秒 +1（上限按佩戴装备，魔宗/凛冬 4 层、大天使/耳语 5 层）
        String worn = wornUntransformedTear(player);
        if (worn != null) {
            int cap = chargeCap(worn);
            long now = System.currentTimeMillis();
            var tag = lolTag(player);
            int charges = tag.getInt(CHARGES_KEY);
            long last = tag.contains(CHARGE_TIME_KEY) ? tag.getLong(CHARGE_TIME_KEY) : now;
            if (charges < cap && now - last >= CHARGE_PERIOD_MS) {
                int add = (int) ((now - last) / CHARGE_PERIOD_MS);
                charges = Math.min(cap, charges + add);
                tag.putInt(CHARGES_KEY, charges);
                tag.putLong(CHARGE_TIME_KEY, now);
            }
        }
        refreshAweAndHarmony(player);
        // 蜕变检测（佩戴中即检测，用户规则 2 的常态路径）
        if (worn != null && getStacks(player) >= STACK_CAP) {
            tryTransform(player);
        }
        consonance(player);
        syncStackNbt(player);
    }

    // ===================== 佩戴变动：蜕变 / 退化 =====================

    @SubscribeEvent
    public static void onCurioChange(CurioChangeEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer player)
                || player.level().isClientSide
                || !(event.getTo().getItem() instanceof com.example.lolaccessories.item.GearItem gear)) {
            return;
        }
        String id = gear.getGearId();
        // 佩戴未蜕变版：叠层已满则立即蜕变（用户规则 2 的佩戴路径）
        if (TRANSFORMS.containsKey(id) && getStacks(player) >= STACK_CAP) {
            transformTo(player, id, TRANSFORMS.get(id));
        }
    }

    // ===================== 内部工具 =====================

    /** 玩家当前佩戴的「未蜕变」女神泪系装备 id（无则 null）。 */
    private static String wornUntransformedTear(ServerPlayer player) {
        for (String id : TRANSFORMS.keySet()) {
            if (CuriosGearWear.isWearing(player, id)) {
                return id;
            }
        }
        return null;
    }

    private static int getStacks(ServerPlayer player) {
        return StackedGearState.getStacks(player, STACK_KEY);
    }

    /** 读取/创建玩家持久化 NBT 里的模组数据区。 */
    private static net.minecraft.nbt.CompoundTag lolTag(ServerPlayer player) {
        var root = player.getPersistentData();
        if (!root.contains("lolaccessories", net.minecraft.nbt.CompoundTag.TAG_COMPOUND)) {
            root.put("lolaccessories", new net.minecraft.nbt.CompoundTag());
        }
        return root.getCompound("lolaccessories");
    }

    /** 命中消耗一层充能并叠层；无充能返回 false。 */
    private static boolean consumeCharge(ServerPlayer player, String gearId) {
        var tag = lolTag(player);
        int charges = tag.getInt(CHARGES_KEY);
        if (charges <= 0) {
            return false;
        }
        tag.putInt(CHARGES_KEY, charges - 1);
        return true;
    }

    /** 充能上限：魔宗/凛冬 4 层，大天使/耳语 5 层（LoL 同款）。 */
    private static int chargeCap(String gearId) {
        GearConfig.OnHitEffect effect = GearConfigManager.get(gearId).findEffect("tear_stack").orElse(null);
        return effect != null && effect.max_stacks > 0 ? effect.max_stacks : 4;
    }

    private static int addStacks(ServerPlayer player, int gain) {
        int next = StackedGearState.addStacks(player, STACK_KEY, gain, STACK_CAP);
        StackedGearState.ensureModifiers(player);
        return next;
    }

    /**
     * 额外法力 = 最大法力值 − 铁魔法基础 100（用户 2026-09-09 定义，含所有来源）。
     * 未装铁魔法时退化为「装备法力 + 叠层」。
     */
    private static double bonusMana(ServerPlayer player, String gearId) {
        if (IronsCompat.isLoaded()) {
            Attribute attr = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(IronsCompat.MAX_MANA));
            if (attr != null) {
                AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) {
                    return Math.max(0.0D, inst.getValue() - 100.0D);
                }
            }
        }
        double gearMana = 0.0D;
        for (GearConfig.Attr attr : GearConfigManager.get(gearId).attributes) {
            if (IronsCompat.MAX_MANA.equals(attr.id) && attr.resolved()) {
                gearMana += attr.amount;
            }
        }
        boolean untransformed = TRANSFORMS.containsKey(gearId);
        return gearMana + (untransformed ? getStacks(player) : 0);
    }

    /** 有效最大法力：铁魔法属性总值（未装铁魔法时退化为装备法力 + 叠层）。 */
    private static double maxMana(ServerPlayer player) {
        if (IronsCompat.isLoaded()) {
            Attribute attr = BuiltInRegistries.ATTRIBUTE.get(new ResourceLocation(IronsCompat.MAX_MANA));
            if (attr != null) {
                AttributeInstance inst = player.getAttribute(attr);
                if (inst != null) {
                    return inst.getValue();
                }
            }
        }
        for (String id : TRANSFORMS.keySet()) {
            if (CuriosGearWear.isWearing(player, id)) {
                return bonusMana(player, id);
            }
        }
        for (String id : PREV.keySet()) {
            if (CuriosGearWear.isWearing(player, id)) {
                return bonusMana(player, id);
            }
        }
        return 0.0D;
    }

    /** 蜕变：把玩家佩戴的 fromGear 原位替换为 toGear，清空叠层。 */
    private static void transformTo(ServerPlayer player, String fromGear, String toGear) {
        var inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) {
            return;
        }
        List<SlotResult> worn = inventory.get().findCurios(stack ->
                stack.getItem() instanceof com.example.lolaccessories.item.GearItem g
                        && fromGear.equals(g.getGearId()));
        if (worn.isEmpty()) {
            return;
        }
        SlotResult slot = worn.get(0);
        var ctx = slot.slotContext();
        ItemStack transformed = new ItemStack(
                BuiltInRegistries.ITEM.get(new ResourceLocation(LOLAccessories.MOD_ID, toGear)));
        inventory.get().getCurios().get(ctx.identifier()).getStacks().setStackInSlot(ctx.index(), transformed);
        // 蜕变：清空叠层
        StackedGearState.setStacks(player, STACK_KEY, 0);
    }

    /** 蜕变检测入口（层数已满时调用）。 */
    private static void tryTransform(ServerPlayer player) {
        String worn = wornUntransformedTear(player);
        if (worn != null) {
            transformTo(player, worn, TRANSFORMS.get(worn));
        }
    }

    /** Awe / Harmony 动态属性刷新（每秒）。 */
    private static void refreshAweAndHarmony(ServerPlayer player) {
        for (Map.Entry<String, AweEntry> entry : AWES.entrySet()) {
            String gearId = entry.getKey();
            AweEntry awe = entry.getValue();
            boolean wearing = CuriosGearWear.isWearing(player, gearId);
            double bonus = wearing ? bonusMana(player, gearId) : 0.0D;
            applyDynamic(player, awe.attributeId,
                    UUID.nameUUIDFromBytes(("lolaccessories:tear_awe:" + gearId).getBytes()),
                    awe.ratio * bonus, "lolaccessories:tear_awe_" + gearId);
        }
    }

    /** 在玩家属性实例上增删瞬态 ADDITION 修正器（value ≤ 0 移除，数值不变时跳过）。 */
    private static void applyDynamic(Player player, String attributeId, UUID uuid,
                                     double value, String name) {
        ResourceLocation id = ResourceLocation.tryParse(attributeId);
        if (id == null) {
            return;
        }
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = player.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier existing = instance.getModifier(uuid);
        if (existing != null && existing.getAmount() == value) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(uuid);
        }
        if (value > 0.0D) {
            instance.addTransientModifier(new AttributeModifier(uuid, name, value,
                    AttributeModifier.Operation.ADDITION));
        }
    }

    /** Consonance（歌之权冠）：每秒治疗范围内血量%最低的友方玩家 0.8% 最大法力。 */
    private static void consonance(ServerPlayer player) {
        if (!CuriosGearWear.isWearing(player, "diadem_of_songs")) {
            return;
        }
        // 交战条件：16 格内存在可交战目标
        boolean inCombat = !player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(16.0D),
                e -> e != player && LolLegendPassiveEvents.isLegendaryTarget(player, e)).isEmpty();
        if (!inCombat) {
            return;
        }
        Player best = null;
        float bestPct = 1.0F;
        for (Player p : player.level().getEntitiesOfClass(Player.class,
                player.getBoundingBox().inflate(16.0D),
                p -> p != player && p.isAlive() && !p.isSpectator()
                        && p.getHealth() < p.getMaxHealth())) {
            float pct = p.getHealth() / p.getMaxHealth();
            if (pct < bestPct) {
                bestPct = pct;
                best = p;
            }
        }
        if (best == null) {
            return;
        }
        double heal = maxMana(player) * 0.008D;
        if (heal > 0.0D) {
            best.heal((float) heal);
            // 共鸣增幅特效：目标身上心形 + 音符粒子（每秒治疗时轻量点缀）
            if (player.level() instanceof net.minecraft.server.level.ServerLevel sl) {
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                        best.getX(), best.getY() + best.getBbHeight() + 0.3D, best.getZ(),
                        2, 0.25D, 0.1D, 0.25D, 0.0D);
                sl.sendParticles(net.minecraft.core.particles.ParticleTypes.NOTE,
                        best.getX(), best.getY() + best.getBbHeight() * 0.7D, best.getZ(),
                        1, 0.2D, 0.1D, 0.2D, 1.0D);
            }
        }
    }

    /** 把叠层数镜像到佩戴中物品的 NBT（tooltip / 客户端 HUD 读取）。 */
    private static void syncStackNbt(ServerPlayer player) {
        int count = getStacks(player);
        var inventory = CuriosApi.getCuriosInventory(player).resolve();
        if (inventory.isEmpty()) {
            return;
        }
        List<SlotResult> worn = inventory.get().findCurios(stack ->
                stack.getItem() instanceof com.example.lolaccessories.item.GearItem gear
                        && (TRANSFORMS.containsKey(gear.getGearId())));
        for (SlotResult result : worn) {
            result.stack().getOrCreateTag().putInt(StackedGearState.TAG_STACKS, count);
        }
    }

    /** 伤害上下文小工具（直接攻击判定）。 */
    private record EntityDamageContext(LivingHurtEvent event) {
        boolean direct() {
            return event.getSource().getDirectEntity() == event.getSource().getEntity()
                    && event.getSource().getDirectEntity() != null;
        }
    }
}
