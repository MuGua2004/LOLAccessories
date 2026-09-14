package com.example.lolaccessories.event;

import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.item.GearItem;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.GearFxBroadcast;
import net.minecraft.core.registries.BuiltInRegistries;
import net.minecraft.network.protocol.game.ClientboundUpdateAttributesPacket;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.TickTask;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.damagesource.DamageTypes;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ambient.AmbientCreature;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Mob;
import net.minecraft.world.entity.NeutralMob;
import net.minecraft.world.entity.TamableAnimal;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.animal.AbstractGolem;
import net.minecraft.world.entity.animal.Animal;
import net.minecraft.world.entity.animal.WaterAnimal;
import net.minecraft.world.entity.animal.allay.Allay;
import net.minecraft.world.entity.monster.Enemy;
import net.minecraft.world.entity.npc.AbstractVillager;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.phys.AABB;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import top.theillusivec4.curios.api.event.CurioChangeEvent;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 5 件新传说（3 级）装备的机制事件（服务端结算）。
 *
 * <p>目标判定口径（2026-09-07 用户确认）：装备效果只排除「友善/被动生物」（家畜、村民、
 * 被驯养宠物、蝙蝠、被动鱼类、铁傀儡/雪傀儡等默认友好单位）；其余生物实体（敌对怪、中立怪、
 * 可战斗生物、敌对玩家等）一律生效，不做英雄/野怪/小兵类别的对应映射。</p>
 *
 * <p>自动/周期类效果（无终苦楚脉冲、黯炎灼烧的施放）要求佩戴者<b>进入战斗</b>后才发动：
 * 以 {@link #COMBAT_UNTIL_MS} 记录“近期造成过伤害 / 受到过伤害”的时间窗。</p>
 *
 * <p>本文件处理：</p>
 * <ul>
 *   <li><b>暴政 / 报应</b>（霸王血铠）：实时把「最大生命 × 2.5%」与「按已损失生命提升至多
 *       （当前总攻击伤害 − 报应自身）× 12%」作为动态攻击伤害挂到佩戴者属性上，并推送属性快照，
 *       AttributesLib 面板实时反映（不再有命中附加伤害）；</li>
 *   <li><b>苦楚</b>（无终恨意）：战斗中每 4 秒对周围目标造成最大生命 × 3% 的魔法伤害，
 *       并按伤害的 250% 回复自身；</li>
 *   <li><b>不祥灼烧</b>（黯炎火炬）：法术（魔法）伤害命中会施加 3 秒灼烧，每跳
 *       基础 10 + 1% 法强；每名处于灼烧中的敌人使佩戴者的魔法伤害提高 4%；</li>
 *   <li><b>法师之祸</b>（败魔）：15 秒未受到魔法伤害时，获得最大生命 × 15% 的护盾
 *       （生命提升效果形态），受到魔法伤害即破除并重计时。</li>
 * </ul>
 *
 * <p>舒瑞娅的战歌为主动技，见 {@link LolNewActiveSkillEvents#SKILL_INSPIRING_SPEECH}；
 * 目标/友军集合工具 {@link #isLegendaryTarget}、{@link #legendTargetsAround}、
 * {@link #allyPlayersAround} 供同包主动技能复用。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolLegendPassiveEvents {

    /** 舒瑞娅的战歌 gear_id。 */
    public static final String GEAR_SHURELYAS_BATTLESONG = "shurelyas_battlesong";
    /** 霸王血铠 gear_id。 */
    public static final String GEAR_OVERLORDS_BLOODMAIL = "overlords_bloodmail";
    /** 无终恨意 gear_id。 */
    public static final String GEAR_UNENDING_DESPAIR = "unending_despair";
    /** 黯炎火炬 gear_id。 */
    public static final String GEAR_BLACKFIRE_TORCH = "blackfire_torch";
    /** 败魔 gear_id。 */
    public static final String GEAR_KAENIC_ROOKERN = "kaenic_rookern";

    /** 战斗状态默认窗口（毫秒）：进入/受击后视为战斗中的时长（12 秒）。 */
    private static final long DEFAULT_COMBAT_WINDOW_MS = 12L * 1000L;
    /** 灼烧状态表膨胀上限，超限时强制清理兜底。 */
    private static final int TORCH_BURN_CAP = 64;

    /** 战斗时间窗：UUID → 战斗中持续到该毫秒。 */
    private static final Map<UUID, Long> COMBAT_UNTIL_MS = new HashMap<>();
    /** 无终苦楚下一次脉冲毫秒：UUID → 下次脉冲时间（<=0 表示待进入战斗后再计时）。 */
    private static final Map<UUID, Long> ANGUISH_NEXT_MS = new HashMap<>();
    /** 败魔魔盾是否已给出：UUID → true。 */
    private static final Map<UUID, Boolean> ROOKERN_SHIELD_ACTIVE = new HashMap<>();
    /** 败魔最近一次受魔法伤害的毫秒：UUID → 时间戳。 */
    private static final Map<UUID, Long> ROOKERN_LAST_MAGIC_MS = new HashMap<>();
    /** 败魔护盾 FX 是否已向客户端声明（用于检测护盾出现/消失边缘，避免每秒重复发包）。 */
    private static final Map<UUID, Boolean> ROOKERN_FX_SENT = new HashMap<>();
    /** 霸王血铠动态攻击伤害已同步指纹：实体 id → 最近推送的数值指纹（换实体/首次必然重推）。 */
    private static final Map<Integer, String> BLOODMAIL_AD_SYNCED = new HashMap<>();
    /** 黯炎灼烧：目标 → （佩戴者 UUID → 灼烧状态）。同一目标可被多名佩戴者各自灼烧，逐跳求和。 */
    private static final Map<LivingEntity, Map<UUID, TorchBurnState>> TORCH_BURNS = new HashMap<>();

    private LolLegendPassiveEvents() {
    }

    // ------------------------------------------------------------------ //
    // 受伤事件：战斗记录 + 黯炎灼烧 + 败魔破盾 + 霸王血铠即时刷新
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        float original = event.getAmount();
        if (original <= 0.0F) {
            return;
        }
        LivingEntity victim = event.getEntity();
        DamageSource source = event.getSource();
        if (source == null) {
            return;
        }
        Player wearer = resolvePlayerCaster(source);
        Entity sourceEntity = source.getEntity() == null ? source.getDirectEntity() : source.getEntity();

        // 造成伤害 / 受到伤害都计入战斗窗口（仅当对手是可战斗目标）
        if (wearer != null && wearer != victim && isLegendaryTarget(wearer, victim)) {
            touchCombat(wearer);
        }
        if (victim instanceof Player hurtPlayer
                && sourceEntity instanceof LivingEntity sourceLiving
                && sourceLiving != victim && isLegendaryTarget(hurtPlayer, sourceLiving)) {
            touchCombat(hurtPlayer);
        }

        // 败魔：受到魔法伤害即破除魔盾并重计 15 秒再充能窗口
        if (victim instanceof ServerPlayer rook) {
            if (wears(rook, GEAR_KAENIC_ROOKERN) && isMagicDamage(source)) {
                breakRookernShield(rook);
            }
            // 霸王血铠：受伤后生命下降 → 立即刷新暴政/报应动态攻击伤害（未佩戴时为空操作）
            if (wears(rook, GEAR_OVERLORDS_BLOODMAIL)) {
                refreshBloodmailStats(rook);
            }
        }

        // 黯炎火炬：佩戴者的法术（魔法）伤害命中 → 施加灼烧
        if (wearer != null && wearer != victim && isMagicDamage(source)) {
            handleBalefulBlaze(wearer, victim, event);
        }
    }

    // ------------------------------------------------------------------ //
    // 周期结算：无终脉冲 / 败魔护盾（每佩戴者每秒），黯炎灼烧（全局每 0.5 秒）
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        long tick = event.getServer().getTickCount();
        if (tick % 10 != 0) {
            return;
        }
        tickTorchBurns();
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        // 每秒整一次（避免逐 tick 全量判定；秒级精度足够 4 秒脉冲与 15 秒护盾窗口）
        if (player.tickCount % 20 != 0) {
            return;
        }
        tickAnguish(player);
        tickRookern(player);
        refreshBloodmailStats(player);
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        UUID uuid = event.getEntity().getUUID();
        BLOODMAIL_AD_SYNCED.remove(event.getEntity().getId());
        COMBAT_UNTIL_MS.remove(uuid);
        ANGUISH_NEXT_MS.remove(uuid);
        ROOKERN_SHIELD_ACTIVE.remove(uuid);
        ROOKERN_LAST_MAGIC_MS.remove(uuid);
        ROOKERN_FX_SENT.remove(uuid);
        if (!TORCH_BURNS.isEmpty()) {
            TORCH_BURNS.values().forEach(burns -> burns.remove(uuid));
            TORCH_BURNS.entrySet().removeIf(entry -> entry.getValue().isEmpty());
        }
    }

    // ------------------------------------------------------------------ //
    // 霸王血铠：暴政 / 报应（实时动态攻击伤害加成，服务端结算并推送属性快照）
    // ------------------------------------------------------------------ //

    /** 暴政加成的瞬态修正器 UUID（generic.attack_damage，ADDITION）。 */
    private static final UUID TYRANNY_AD_MODIFIER = UUID.fromString("d1a3c8e4-2b7f-4a5e-9d6c-0f1e2a3b4c5d");
    /** 报应加成的瞬态修正器 UUID。 */
    private static final UUID RETRIBUTION_AD_MODIFIER = UUID.fromString("c2b4d9f0-3a8e-4b6c-a7d0-1e2f3a4b5c6e");

    /**
     * 饰品栏变动：霸王血铠进出槽位立即刷新（卸下即移除加成并同步面板）。
     * 战斗中数值刷新由每秒 tick 与受击事件兜底。
     */
    @SubscribeEvent
    public static void onCuriosChanged(CurioChangeEvent event) {
        if (event.getEntity().level().isClientSide || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        if (isOverlordsBloodmail(event.getFrom()) || isOverlordsBloodmail(event.getTo())) {
            refreshBloodmailStats(player);
            // Curios 会在该事件之后才增删槽位静态修正器，延后 1 tick 再刷一次，保证
            // 报应基数含新的静态 +30、卸下后客户端面板不会残留旧值
            MinecraftServer server = player.getServer();
            if (server != null) {
                ServerPlayer target = player;
                server.tell(new TickTask(server.getTickCount() + 1, () -> {
                    if (!target.isRemoved()) {
                        refreshBloodmailStats(target);
                    }
                }));
            }
        }
    }

    private static boolean isOverlordsBloodmail(ItemStack stack) {
        return !stack.isEmpty() && stack.getItem() instanceof GearItem gear
                && GEAR_OVERLORDS_BLOODMAIL.equals(gear.getGearId());
    }

    /**
     * 刷新霸王血铠的动态攻击伤害加成并同步到客户端属性面板。
     *
     * <p>佩戴时挂上两个瞬态 ADDITION 修正器：暴政 = 最大生命 × 2.5%（额外生命取总最大生命）；
     * 报应 = 已损失生命比例 × 12% ×（当前总攻击伤害 − 报应自身，含本件静态 +30 与暴政加成，
     * 但不计入自身，避免回灌）。generic.attack_damage 并非原版 clientSyncable 属性，
     * 服务端改值不会自动下发，因此数值变化（或换了玩家实体，如重进/重生）时主动推送
     * {@link ClientboundUpdateAttributesPacket} 快照，使 AttributesLib 面板的攻击伤害随生命实时增减。</p>
     */
    private static void refreshBloodmailStats(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance == null) {
            return;
        }
        int entityId = player.getId();
        if (!wears(player, GEAR_OVERLORDS_BLOODMAIL)) {
            // 未佩戴：仅当“曾推送过加成”或“实例仍有残留修正器”时才清理并同步，避免给普通玩家发空快照
            String zero = fingerprint(0.0D, 0.0D);
            boolean everSynced = zero.equals(BLOODMAIL_AD_SYNCED.get(entityId));
            boolean residual = modifierAmount(instance, TYRANNY_AD_MODIFIER) > 0.0D
                    || modifierAmount(instance, RETRIBUTION_AD_MODIFIER) > 0.0D;
            if (!everSynced && !residual) {
                return;
            }
            setTransientModifier(instance, TYRANNY_AD_MODIFIER, 0.0D);
            setTransientModifier(instance, RETRIBUTION_AD_MODIFIER, 0.0D);
            BLOODMAIL_AD_SYNCED.put(entityId, zero);
            syncAttackDamage(player);
            return;
        }
        double tyrannyAd = 0.0D;
        double retributionAd = 0.0D;
        GearConfig.OnHitEffect tyranny = findEffect(GEAR_OVERLORDS_BLOODMAIL, "tyranny");
        GearConfig.OnHitEffect retribution = findEffect(GEAR_OVERLORDS_BLOODMAIL, "retribution");
        double tyrannyPct = tyranny != null && tyranny.amount > 0 ? tyranny.amount : 0.025D;
        double retributionPct = retribution != null && retribution.amount > 0 ? retribution.amount : 0.12D;
        double maxHealth = player.getMaxHealth();
        // 额外生命值 = 最大生命值 − 原版基础 20（LoL「暴政」基于 bonus health）
        double bonusHealth = Math.max(0.0D, maxHealth - 20.0D);
        if (maxHealth > 0.0D) {
            tyrannyAd = bonusHealth * tyrannyPct;
            double missingRatio = Math.max(0.0D, Math.min(1.0D,
                    (maxHealth - player.getHealth()) / maxHealth));
            if (missingRatio > 0.0D) {
                double ownRetribution = modifierAmount(instance, RETRIBUTION_AD_MODIFIER);
                double base = Math.max(0.0D,
                        player.getAttributeValue(Attributes.ATTACK_DAMAGE) - ownRetribution);
                retributionAd = missingRatio * retributionPct * base;
            }
        }
        String fingerprint = fingerprint(tyrannyAd, retributionAd);
        if (fingerprint.equals(BLOODMAIL_AD_SYNCED.get(entityId))
                && closeEnough(modifierAmount(instance, TYRANNY_AD_MODIFIER), tyrannyAd)
                && closeEnough(modifierAmount(instance, RETRIBUTION_AD_MODIFIER), retributionAd)) {
            return;
        }
        setTransientModifier(instance, TYRANNY_AD_MODIFIER, tyrannyAd);
        setTransientModifier(instance, RETRIBUTION_AD_MODIFIER, retributionAd);
        BLOODMAIL_AD_SYNCED.put(entityId, fingerprint);
        syncAttackDamage(player);
    }

    /** 取修正器当前加成值（不存在返回 0）。 */
    private static double modifierAmount(AttributeInstance instance, UUID modifierId) {
        AttributeModifier modifier = instance.getModifier(modifierId);
        return modifier == null ? 0.0D : modifier.getAmount();
    }

    /** 覆盖式设置瞬态修正器（value ≤ 0 移除；数值相同则跳过）。 */
    private static void setTransientModifier(AttributeInstance instance, UUID modifierId, double value) {
        AttributeModifier existing = instance.getModifier(modifierId);
        if (value <= 0.0D) {
            if (existing != null) {
                instance.removeModifier(modifierId);
            }
            return;
        }
        if (existing != null && closeEnough(existing.getAmount(), value)) {
            return;
        }
        if (existing != null) {
            instance.removeModifier(modifierId);
        }
        instance.addTransientModifier(new AttributeModifier(modifierId,
                "lolaccessories:" + GEAR_OVERLORDS_BLOODMAIL, value, AttributeModifier.Operation.ADDITION));
    }

    private static boolean closeEnough(double a, double b) {
        return Math.abs(a - b) < 1.0E-4D;
    }

    /** 数值指纹（保留 3 位小数），避免生命微小波动造成无谓的重复推送。 */
    private static String fingerprint(double tyrannyAd, double retributionAd) {
        return (Math.round(tyrannyAd * 1000.0D) / 1000.0D) + "," + (Math.round(retributionAd * 1000.0D) / 1000.0D);
    }

    /** 把佩戴者当前攻击伤害属性实例完整快照推给自己（含 Curios 静态加成与全部瞬态修正器）。 */
    private static void syncAttackDamage(ServerPlayer player) {
        AttributeInstance instance = player.getAttribute(Attributes.ATTACK_DAMAGE);
        if (instance != null) {
            player.connection.send(new ClientboundUpdateAttributesPacket(player.getId(), List.of(instance)));
        }
    }

    // ------------------------------------------------------------------ //
    // 无终恨意
    // ------------------------------------------------------------------ //

    private static void tickAnguish(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!wears(player, GEAR_UNENDING_DESPAIR)) {
            ANGUISH_NEXT_MS.remove(uuid);
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_UNENDING_DESPAIR, "anguish");
        if (effect == null) {
            ANGUISH_NEXT_MS.remove(uuid);
            return;
        }
        long intervalMs = effect.interval_seconds > 0
                ? Math.round(effect.interval_seconds * 1000.0D) : 4L * 1000L;
        long now = System.currentTimeMillis();
        Long nextMs = ANGUISH_NEXT_MS.get(uuid);
        if (!isInCombat(uuid)) {
            // 未进入战斗：等待下一次战斗开始再计时
            ANGUISH_NEXT_MS.put(uuid, 0L);
            return;
        }
        if (nextMs == null || nextMs <= 0L) {
            ANGUISH_NEXT_MS.put(uuid, now + intervalMs);
            return;
        }
        if (now < nextMs) {
            return;
        }
        pulseAnguish(player, effect);
        ANGUISH_NEXT_MS.put(uuid, now + intervalMs);
    }

    /** 苦楚：对周围目标造成额外生命 × pct 的魔法伤害（额外生命 = 最大生命 − 20），并按伤害的 heal_pct 回复自身。 */
    private static void pulseAnguish(ServerPlayer player, GearConfig.OnHitEffect effect) {
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 8.0D;
        double bonusHealth = Math.max(0.0D, player.getMaxHealth() - 20.0D);
        float damage = (float) (bonusHealth
                * (effect.max_health_pct > 0 ? effect.max_health_pct : 0.03D));
        if (damage <= 0.0F) {
            return;
        }
        List<LivingEntity> targets = legendTargetsAround(player, player, radius);
        if (targets.isEmpty()) {
            return;
        }
        int hits = 0;
        for (LivingEntity target : targets) {
            // 魔法伤害 = 铁魔法学派伤害
            if (IronsSpellDamage.apply(player, target, damage, IronsSpellDamage.resolve(effect.school))) {
                hits++;
            }
        }
        if (hits > 0 && effect.heal_pct > 0) {
            player.heal((float) (damage * hits * effect.heal_pct * (1.0D + healPowerBonus(player))));
        }
    }

    // ------------------------------------------------------------------ //
    // 黯炎火炬
    // ------------------------------------------------------------------ //

    private static void handleBalefulBlaze(Player wearer, LivingEntity victim, LivingHurtEvent event) {
        if (victim.isDeadOrDying() || victim == wearer || !isLegendaryTarget(wearer, victim)) {
            return;
        }
        if (!wears(wearer, GEAR_BLACKFIRE_TORCH)) {
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_BLACKFIRE_TORCH, "baleful_blaze");
        if (effect == null) {
            return;
        }
        UUID uuid = wearer.getUUID();
        double tickDamage = burnTickDamage(wearer, effect);
        double seconds = effect.duration_seconds > 0 ? effect.duration_seconds : 3.0D;
        long untilMs = System.currentTimeMillis() + Math.round(seconds * 1000.0D);
        TORCH_BURNS.computeIfAbsent(victim, k -> new HashMap<>())
                .put(uuid, new TorchBurnState(tickDamage, untilMs));
        GearFxBroadcast.window(victim, FxKind.TORCH, (int) Math.round(seconds * 20.0D));

        // 点燃音效：点火“噗”一声（原版音效近似，音高随机一点避免机械感）
        victim.level().playSound(null, victim.getX(), victim.getY(), victim.getZ(),
                SoundEvents.FIRECHARGE_USE, SoundSource.PLAYERS, 0.7F,
                0.9F + victim.getRandom().nextFloat() * 0.2F);

        // 每名处于灼烧中的敌人 → 佩戴者魔法伤害提高（在本次命中即生效）
        int burning = burningCount(wearer);
        double apPct = effect.ap_pct_per_target > 0 ? effect.ap_pct_per_target : 0.04D;
        if (burning > 0 && apPct > 0) {
            event.setAmount(event.getAmount() * (float) (1.0D + apPct * burning));
        }
        if (TORCH_BURNS.size() > TORCH_BURN_CAP) {
            pruneTorchBurns();
        }
    }

    /** 全局每 0.5 秒结算一次黯炎灼烧（对各目标当前存活状态的灼烧伤害求和后一次打出）。 */
    private static void tickTorchBurns() {
        if (TORCH_BURNS.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        Iterator<Map.Entry<LivingEntity, Map<UUID, TorchBurnState>>> iterator = TORCH_BURNS.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<LivingEntity, Map<UUID, TorchBurnState>> entry = iterator.next();
            LivingEntity target = entry.getKey();
            Map<UUID, TorchBurnState> burns = entry.getValue();
            if (target.isRemoved() || !target.isAlive() || target.level().isClientSide) {
                iterator.remove();
                continue;
            }
            double total = 0.0D;
            Iterator<Map.Entry<UUID, TorchBurnState>> burnIt = burns.entrySet().iterator();
            while (burnIt.hasNext()) {
                TorchBurnState state = burnIt.next().getValue();
                if (now >= state.untilMs) {
                    burnIt.remove();
                    continue;
                }
                total += state.tickDamage;
            }
            if (burns.isEmpty()) {
                iterator.remove();
                continue;
            }
            if (total > 0.0D) {
                // 黑焰火炬灼烧：火焰学派的铁魔法伤害
                IronsSpellDamage.apply(null, target, (float) total, IronsSpellDamage.FIRE);
            }
        }
    }

    private static void pruneTorchBurns() {
        long now = System.currentTimeMillis();
        TORCH_BURNS.values().forEach(burns -> burns.entrySet().removeIf(e -> now >= e.getValue().untilMs));
        TORCH_BURNS.entrySet().removeIf(e -> e.getValue().isEmpty());
    }

    /** 每跳灼烧伤害 = burn_tick_damage + burn_ap_ratio × AP（AP ≈ 通用法术强度值 × 100）。 */
    private static double burnTickDamage(Player wearer, GearConfig.OnHitEffect effect) {
        double base = effect.burn_tick_damage > 0 ? effect.burn_tick_damage : 10.0D;
        double apRatio = effect.burn_ap_ratio > 0 ? effect.burn_ap_ratio : 0.01D;
        return base + apRatio * (genericSpellPowerValue(wearer) * 100.0D);
    }

    /** 处于本佩戴者灼烧中的目标数（用于“每目标 +X% 法强”）。 */
    private static int burningCount(Player wearer) {
        UUID uuid = wearer.getUUID();
        int count = 0;
        for (Map<UUID, TorchBurnState> burns : TORCH_BURNS.values()) {
            if (burns.containsKey(uuid)) {
                count++;
            }
        }
        return count;
    }

    // ------------------------------------------------------------------ //
    // 败魔
    // ------------------------------------------------------------------ //

    /** 败魔护盾默认持续毫秒（120 秒；到期且仍满足条件时由 tickRookern 自动重充）。 */
    private static final long ROOKERN_SHIELD_DURATION_MS = 120L * 1000L;
    /** 败魔护盾特效持续刻数（与护盾时长对应）。 */
    private static final int ROOKERN_SHIELD_FX_TICKS = 120 * 20;

    private static void tickRookern(ServerPlayer player) {
        UUID uuid = player.getUUID();
        if (!wears(player, GEAR_KAENIC_ROOKERN)) {
            ShieldHpService.clearIfSource(player, ShieldHpService.SOURCE_ROOKERN);
            ROOKERN_SHIELD_ACTIVE.remove(uuid);
            ROOKERN_LAST_MAGIC_MS.remove(uuid);
            syncRookernShieldFx(player, false);
            return;
        }
        GearConfig.OnHitEffect effect = findEffect(GEAR_KAENIC_ROOKERN, "magebane");
        if (effect == null) {
            ShieldHpService.clearIfSource(player, ShieldHpService.SOURCE_ROOKERN);
            ROOKERN_SHIELD_ACTIVE.remove(uuid);
            syncRookernShieldFx(player, false);
            return;
        }
        long intervalMs = effect.interval_seconds > 0
                ? Math.round(effect.interval_seconds * 1000.0D) : 15L * 1000L;
        long lastMagic = ROOKERN_LAST_MAGIC_MS.getOrDefault(uuid, 0L);
        long now = System.currentTimeMillis();
        boolean eligible = now - lastMagic >= intervalMs;
        if (!eligible) {
            ShieldHpService.clearIfSource(player, ShieldHpService.SOURCE_ROOKERN);
            ROOKERN_SHIELD_ACTIVE.put(uuid, false);
            syncRookernShieldFx(player, false);
            return;
        }
        boolean alreadyRookern = ShieldHpService.isSource(uuid, ShieldHpService.SOURCE_ROOKERN);
        // 玩家已带着原版吸收效果（金苹果/图腾等外部黄心）时不覆盖，待其消退后由本 tick 自动充能
        boolean blockedByVanilla = !alreadyRookern && player.hasEffect(MobEffects.ABSORPTION);
        if (!blockedByVanilla) {
            if (!alreadyRookern) {
                double pct = effect.max_health_pct > 0 ? effect.max_health_pct : 0.15D;
                double shield = player.getMaxHealth() * pct * (1.0D + healPowerBonus(player));
                // 黄心护盾：直接写吸收值（显示为金色心），不挂药水效果，避免被清除/叠加异常
                ShieldHpService.apply(player, ShieldHpService.SOURCE_ROOKERN,
                        (float) shield, ROOKERN_SHIELD_DURATION_MS, ShieldType.MAGIC);
            }
            ROOKERN_SHIELD_ACTIVE.put(uuid, true);
            syncRookernShieldFx(player, true);
        } else {
            ROOKERN_SHIELD_ACTIVE.put(uuid, false);
            syncRookernShieldFx(player, false);
        }
    }

    /** 同步败魔护盾特效开关（边缘触发，避免每秒发包）。 */
    private static void syncRookernShieldFx(ServerPlayer player, boolean active) {
        UUID uuid = player.getUUID();
        Boolean was = ROOKERN_FX_SENT.get(uuid);
        if (was != null && was == active) {
            return;
        }
        if (active) {
            GearFxBroadcast.window(player, FxKind.SHIELD_ROOKERN, ROOKERN_SHIELD_FX_TICKS);
        } else {
            GearFxBroadcast.off(player, FxKind.SHIELD_ROOKERN);
        }
        ROOKERN_FX_SENT.put(uuid, active);
    }

    /** 受到魔法伤害：破除当前魔盾并重计 15 秒（见 tickRookern 的间隔判定）。 */
    private static void breakRookernShield(ServerPlayer player) {
        UUID uuid = player.getUUID();
        ShieldHpService.clearIfSource(player, ShieldHpService.SOURCE_ROOKERN);
        ROOKERN_SHIELD_ACTIVE.put(uuid, false);
        ROOKERN_LAST_MAGIC_MS.put(uuid, System.currentTimeMillis());
        syncRookernShieldFx(player, false);
    }

    // ------------------------------------------------------------------ //
    // 战斗状态
    // ------------------------------------------------------------------ //

    /** 该玩家当前是否处于战斗窗口（供同包其他装备的战斗条件判定复用）。 */
    public static boolean isPlayerInCombat(Player player) {
        return player != null && isInCombat(player.getUUID());
    }

    private static boolean isInCombat(UUID uuid) {
        Long until = COMBAT_UNTIL_MS.get(uuid);
        return until != null && System.currentTimeMillis() < until;
    }

    /** 记为进入战斗：以默认 12 秒窗口向后顺延。 */
    private static void touchCombat(Player player) {
        COMBAT_UNTIL_MS.put(player.getUUID(), System.currentTimeMillis() + DEFAULT_COMBAT_WINDOW_MS);
    }

    // ------------------------------------------------------------------ //
    // 目标 / 友军集合（传说口径：只排除友善/被动生物，其余实体全生效）
    // ------------------------------------------------------------------ //

    /** 传说效果的生效目标：排除友善/被动生物后的全部可战斗实体；玩家按 PvP/队伍口径判定。 */
    public static boolean isLegendaryTarget(LivingEntity owner, LivingEntity target) {
        if (owner == null || target == null || owner == target || target.isDeadOrDying()) {
            return false;
        }
        if (target instanceof Player other) {
            if (owner.level().isClientSide
                    || owner.level().getServer() == null
                    || !owner.level().getServer().isPvpAllowed()) {
                return false;
            }
            boolean ownerSpectator = owner instanceof Player ownerPlayer && ownerPlayer.isSpectator();
            if (ownerSpectator || other.isSpectator()) {
                return false;
            }
            return !owner.isAlliedTo(other);
        }
        if (target instanceof Mob mob) {
            return !isFriendlyOrPassive(mob);
        }
        return false;
    }

    /**
     * 友善/被动生物判定（不参与任何装备技能效果）：被驯养的宠物、村民/流浪商人、
     * 铁傀儡/雪傀儡等默认友好单位、蝙蝠、悦灵、以及不会攻击玩家的被动动物/被动鱼类。
     * 敌对怪（Enemy）与中立可战斗生物（狼/蜜蜂/铁傀儡外…末影人、僵尸猪灵等）不算在内。
     */
    private static boolean isFriendlyOrPassive(Mob mob) {
        if (mob instanceof Enemy) {
            return false;
        }
        if (mob instanceof TamableAnimal tamed && tamed.isTame()) {
            return true;
        }
        if (mob instanceof AbstractVillager) {
            return true;
        }
        if (mob instanceof AbstractGolem) {
            return true;
        }
        if (mob instanceof AmbientCreature) {
            return true;
        }
        if (mob instanceof Allay) {
            return true;
        }
        if (mob instanceof NeutralMob) {
            // 中立可战斗生物（未驯服狼、蜜蜂、熊、末影人、僵尸猪灵等）：视为可生效目标
            return false;
        }
        if (mob instanceof Animal) {
            return true;
        }
        if (mob instanceof WaterAnimal) {
            return true;
        }
        return false;
    }

    /** 收集以 center 为圆心、半径内本传说口径的生效目标。 */
    public static List<LivingEntity> legendTargetsAround(LivingEntity center, LivingEntity owner, double radius) {
        List<LivingEntity> result = new ArrayList<>();
        if (center.level().isClientSide) {
            return result;
        }
        AABB box = AABB.ofSize(center.position(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        for (LivingEntity e : center.level().getEntitiesOfClass(LivingEntity.class, box,
                entity -> isLegendaryTarget(owner, entity))) {
            result.add(e);
        }
        return result;
    }

    /**
     * 收集舒瑞娅战歌的“友军玩家”：佩戴者自己 + 半径内非同队敌对（PvP 关闭时全体视为友军）
     * 的其它存活玩家。增益一律只作用于玩家，不对任何生物生效。
     */
    public static List<ServerPlayer> allyPlayersAround(ServerPlayer center, double radius) {
        List<ServerPlayer> result = new ArrayList<>();
        result.add(center);
        if (center.level().isClientSide) {
            return result;
        }
        AABB box = AABB.ofSize(center.position(), radius * 2.0D, radius * 2.0D, radius * 2.0D);
        boolean pvp = center.level().getServer() != null && center.level().getServer().isPvpAllowed();
        for (ServerPlayer other : center.level().getEntitiesOfClass(ServerPlayer.class, box)) {
            if (other == center || !other.isAlive() || other.isSpectator()) {
                continue;
            }
            if (!pvp || center.isAlliedTo(other)) {
                result.add(other);
            }
        }
        return result;
    }

    // ------------------------------------------------------------------ //
    // 通用工具
    // ------------------------------------------------------------------ //

    private static boolean wears(Player player, String gearId) {
        return CuriosGearWear.isWearing(player, gearId);
    }

    private static GearConfig.OnHitEffect findEffect(String gearId, String effectId) {
        GearConfig config = GearConfigManager.get(gearId);
        if (config == null) {
            return null;
        }
        return config.findEffect(effectId).orElse(null);
    }

    /** 治疗与护盾强度加成（倍率，0 = 无加成）。 */
    private static double healPowerBonus(LivingEntity entity) {
        return Math.max(0.0D, entity.getAttributeValue(ModAttributes.LOL_HEAL_POWER.get()));
    }

    /** 通用法术强度值（铁魔法 MagicPercentAttribute 语义：1.0 = 无加成，1.5 = +50%）。 */
    private static double genericSpellPowerValue(LivingEntity entity) {
        if (!IronsCompat.isLoaded()) {
            return 1.0D;
        }
        ResourceLocation id = ResourceLocation.tryParse(IronsCompat.SPELL_POWER);
        if (id == null) {
            return 1.0D;
        }
        Attribute attribute = BuiltInRegistries.ATTRIBUTE.get(id);
        if (attribute == null) {
            return 1.0D;
        }
        return entity.getAttributeValue(attribute);
    }

    private static boolean isMagicDamage(DamageSource source) {
        return IronsCompat.isIronSpellDamage(source)
                || source.is(DamageTypes.MAGIC)
                || source.is(DamageTypes.INDIRECT_MAGIC);
    }

    /** 从伤害源解析法术施放者（玩家本人直击或弹射物所有者）。 */
    private static Player resolvePlayerCaster(DamageSource source) {
        Entity cause = source.getEntity();
        if (cause == null) {
            cause = source.getDirectEntity();
        }
        if (cause instanceof Player player) {
            return player;
        }
        if (cause instanceof Projectile projectile && projectile.getOwner() instanceof Player player) {
            return player;
        }
        return null;
    }

    // ------------------------------------------------------------------ //
    // 内部状态
    // ------------------------------------------------------------------ //

    /** 黯炎灼烧状态：每 0.5 秒一跳的伤害量与过期毫秒。 */
    private static final class TorchBurnState {
        private final double tickDamage;
        private final long untilMs;

        private TorchBurnState(double tickDamage, long untilMs) {
            this.tickDamage = tickDamage;
            this.untilMs = untilMs;
        }
    }
}
