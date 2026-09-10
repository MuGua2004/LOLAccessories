package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import com.example.lolaccessories.init.ModEntityTypes;
import com.example.lolaccessories.init.ModSounds;
import com.example.lolaccessories.entity.HeartsteelMarkEntity;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.nbt.ListTag;
import net.minecraft.nbt.StringTag;
import net.minecraft.nbt.Tag;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.Display;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.UUID;

/**
 * 心之钢（Heartsteel）——歌莉娅巨人 + 庞然吞食。
 *
 * <p><b>歌莉娅巨人（Goliath）</b>：原作每 1000 最大生命值获得 3% 体型提升；本模组不涉及
 * 体型改动，等额替换为 <b>3% 物理伤害百分比减免</b>（实时跟随当前最大生命值计算，
 * 挂在 lolaccessories:physical_damage_reduction 属性上，由 {@link LolDefenseEvents} 结算）。</p>
 *
 * <p><b>庞然吞食（Colossal Consumption）</b>：佩戴心之钢时，与敌人交战（12 格内有敌对目标）
 * 即锁定其中<b>生命值最高</b>的目标生成吞食印记——同一时刻只对一名目标生效；该目标的
 * 独立 30 秒冷却期间（不受冷却缩减影响）顺位选择下一名目标。印记 3 秒内由小长大（成熟），
 * 成熟后佩戴者的下一次<b>近战或弹射物攻击</b>（铁魔法的魔法伤害不触发）将其击碎：
 * 造成 70 + 6% 佩戴者最大生命值的额外物理伤害，并按<b>计算出的初始伤害</b>（与实际造成
 * 伤害无关）的 10% 永久增加最大生命值。每名目标仅可提供一次生命值增长，且其最大生命值
 * 至少为佩戴者的 2 倍；层数记录在玩家自身 NBT 上（跨存档保留），需装备心之钢才生效。</p>
 *
 * <p>印记为专用视觉实体（三圈法阵悬浮目标头顶，逐层扩展成熟，仅触发者可见）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class HeartsteelEvents {

    /** 心之钢 gear_id。 */
    public static final String GEAR_HEARTSTEEL = "heartsteel";
    /** 印记成熟时间（毫秒）。 */
    private static final long MARK_MATURE_MS = 3000L;
    /** 印记扫描半径（与敌人进入战斗状态的判定距离）。 */
    private static final double ENGAGE_RADIUS = 12.0D;
    /** 每目标独立冷却（毫秒），不受冷却缩减影响。 */
    private static final long TARGET_COOLDOWN_MS = 30000L;
    /** 印记实体 tag（世界加载后清理孤儿印记用）。 */
    private static final String MARK_ENTITY_TAG = "lolaccessories_heartsteel_mark";

    /** 涨血 modifier 固定 UUID（transient，数值由 NBT 每秒恢复；同包统计时排除）。 */
    static final UUID HEALTH_BONUS_UUID = UUID.fromString("d8f4c0de-5177-4b1a-9a2e-4eea1b7c0151");
    /** 歌莉娅减免 modifier 固定 UUID。 */
    private static final UUID GOLIATH_UUID = UUID.fromString("5e2ab7f1-90c3-4d68-b1d0-77c9a3f6e204");

    private static final String NBT_FED_LIST = "lolaccessories_heartsteel_fed";
    private static final String NBT_HEALTH_BONUS = "lolaccessories_heartsteel_bonus";

    /** 活跃印记：目标 UUID → 印记（同一时刻全局仅一个，因“只能对一个目标生效”）。 */
    private static final Map<UUID, Mark> MARKS = new HashMap<>();
    /** 每目标独立冷却：目标 UUID → 冷却截止毫秒。 */
    private static final Map<UUID, Long> TARGET_CD_MS = new HashMap<>();

    /** 一枚吞食印记。 */
    private static final class Mark {
        final HeartsteelMarkEntity entity;
        final LivingEntity target;
        final UUID ownerId;
        final long spawnMs;

        Mark(HeartsteelMarkEntity entity, LivingEntity target, UUID ownerId, long spawnMs) {
            this.entity = entity;
            this.target = target;
            this.ownerId = ownerId;
            this.spawnMs = spawnMs;
        }

        boolean matured(long now) {
            return now - spawnMs >= MARK_MATURE_MS;
        }
    }

    private HeartsteelEvents() {
    }

    // ------------------------------------------------------------------ //
    // 每 tick：印记跟随 / 成长 / 过期清理；每秒：扫描生成 + 歌莉娅 + 涨血恢复
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        Player player = event.player;
        long now = System.currentTimeMillis();
        updateMarks(player, now);
        if (player.tickCount % 20 != 0) {
            return;
        }
        if (!isWearingHeartsteel(player)) {
            clearGoliath(player);
            return;
        }
        updateGoliath(player);
        restoreHealthBonus(player);
        trySpawnMark(player, now);
    }

    /** 印记实体自行跟随目标；这里只做内存表清理（目标死亡/印记消失时释放，可顺位下一名）。 */
    private static void updateMarks(Player player, long now) {
        Iterator<Map.Entry<UUID, Mark>> it = MARKS.entrySet().iterator();
        while (it.hasNext()) {
            Mark mark = it.next().getValue();
            if (mark.entity.isRemoved() || mark.target.isRemoved() || mark.target.isDeadOrDying()) {
                it.remove();
            }
        }
    }

    /** 扫描交战目标，对生命值最高且无冷却者生成印记（同一时刻仅一枚）。 */
    private static void trySpawnMark(Player player, long now) {
        if (!MARKS.isEmpty()) {
            return;
        }
        LivingEntity best = null;
        for (LivingEntity enemy : LolNewEpicPassiveEvents.enemiesAround(player, player, ENGAGE_RADIUS)) {
            UUID id = enemy.getUUID();
            Long cd = TARGET_CD_MS.get(id);
            if (cd != null && now < cd) {
                continue;
            }
            if (best == null || enemy.getMaxHealth() > best.getMaxHealth()) {
                best = enemy;
            }
        }
        if (best == null) {
            return;
        }
        if (!(player.level() instanceof ServerLevel level)) {
            return;
        }
        // 印记 = 三圈法阵视觉实体（悬浮目标头顶、逐层扩展成熟、半径适配体型）
        HeartsteelMarkEntity markEntity = new HeartsteelMarkEntity(
                ModEntityTypes.HEARTSTEEL_MARK.get(), level);
        markEntity.setTargetId(best.getUUID());
        markEntity.moveTo(best.getX(), best.getY() + best.getBbHeight() + 0.15D,
                best.getZ(), best.getYRot(), 0.0F);
        // 先登记再入世：入世事件会校验 MARKS，未登记的印记会被当作孤儿清除
        MARKS.put(best.getUUID(), new Mark(markEntity, best, player.getUUID(), now));
        level.addFreshEntity(markEntity);
        LOLAccessories.LOGGER.info("[心之钢] 对 {} 生成吞食印记（实体 {}）", best.getName().getString(), markEntity.getId());
        level.playSound(null, best.blockPosition(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 0.8F, 0.6F);
        // 印记只对触发者可见：向其他所有同维度玩家发「移除实体」包（服务端继续追踪，
        // 触发者可见性如常；其他玩家客户端不渲染该印记）。这些玩家也无法击碎它（结算校验 ownerId）。
        net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket hide =
                new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(markEntity.getId());
        for (ServerPlayer other : player.getServer().getPlayerList().getPlayers()) {
            if (other != player && other.level() == player.level()) {
                other.connection.send(hide);
            }
        }
    }

    /** 玩家（重）登录时，若场上有印记则对其补发隐藏包（否则会被 tracked entity 重新 spawn）。 */
    @SubscribeEvent
    public static void onPlayerLoggedIn(net.minecraftforge.event.entity.player.PlayerEvent.PlayerLoggedInEvent event) {
        if (!(event.getEntity() instanceof ServerPlayer joining) || joining.getServer() == null) {
            return;
        }
        for (Mark mark : MARKS.values()) {
            if (mark.ownerId.equals(joining.getUUID()) || !mark.entity.isAlive()
                    || mark.entity.level() != joining.level()) {
                continue;
            }
            joining.connection.send(
                    new net.minecraft.network.protocol.game.ClientboundRemoveEntitiesPacket(mark.entity.getId()));
        }
    }

    /** 清理不在活跃表中的印记实体（上次会话遗留等；运行期印记均登记在 MARKS）。 */
    @SubscribeEvent
    public static void onEntityJoinLevel(net.minecraftforge.event.entity.EntityJoinLevelEvent event) {
        if (event.getLevel().isClientSide) {
            return;
        }
        if (event.getEntity() instanceof HeartsteelMarkEntity mark
                && !MARKS.containsKey(mark.getTargetId())) {
            event.setCanceled(true);
        }
    }

    // ------------------------------------------------------------------ //
    // 击碎结算
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        Mark mark = MARKS.get(victim.getUUID());
        if (mark == null) {
            return;
        }
        if (!mark.matured(System.currentTimeMillis())) {
            LOLAccessories.LOGGER.info("[心之钢] 命中带印记目标但印记未成熟，忽略");
            return;
        }
        if (!mark.ownerId.equals(player.getUUID())) {
            return;
        }
        // 仅近战与弹射物触发，铁魔法的魔法伤害不触发
        Entity direct = event.getSource().getDirectEntity();
        boolean melee = direct instanceof LivingEntity && direct == player;
        boolean projectile = direct instanceof Projectile;
        if ((!melee && !projectile) || LolNewEpicPassiveEvents.isMagicDamage(event.getSource())) {
            return;
        }
        shatterMark(player, victim, mark, event);
    }

    private static void shatterMark(ServerPlayer player, LivingEntity victim, Mark mark,
                                    LivingHurtEvent event) {
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_HEARTSTEEL)
                .findEffect("colossal_consumption").orElse(null);
        double base = effect != null && effect.base_damage > 0 ? effect.base_damage : 70.0D;
        double pct = effect != null && effect.amount > 0 ? effect.amount : 0.06D;
        // 初始伤害（与实际造成伤害无关）= 70 + 6% 佩戴者最大生命值
        double initial = base + pct * player.getMaxHealth();
        event.setAmount(event.getAmount() + (float) initial);

        // 涨血：初始伤害的 10%，每目标一次，且目标最大生命 ≥ 玩家 2 倍
        if (!hasFed(player, victim) && victim.getMaxHealth() >= player.getMaxHealth() * 2.0D) {
            markFed(player, victim);
            addHealthBonus(player, initial * 0.10D);
        }

        // 音效（英雄联盟客户端提取的心之钢击碎音效）+ 粒子 + 清理
        player.level().playSound(null, victim.blockPosition(),
                ModSounds.HEARTSTEEL_SHATTER.get(), SoundSource.PLAYERS, 1.2F, 1.0F);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(ParticleTypes.CRIT,
                    victim.getX(), victim.getEyeY(), victim.getZ(), 12, 0.4D, 0.5D, 0.4D, 0.15D);
        }
        mark.entity.discard();
        MARKS.remove(victim.getUUID());
        TARGET_CD_MS.put(victim.getUUID(), System.currentTimeMillis() + TARGET_COOLDOWN_MS);
    }

    // ------------------------------------------------------------------ //
    // 歌莉娅巨人 / 涨血挂载 / NBT
    // ------------------------------------------------------------------ //

    /** 歌莉娅：每 1000 最大生命值 → +3% 物理伤害减免（原版无上限）。 */
    private static void updateGoliath(Player player) {
        double goliath = Math.floor(player.getMaxHealth() / 1000.0D) * 0.03D;
        AttributeInstance attr = player.getAttribute(ModAttributes.LOL_PHYSICAL_DAMAGE_REDUCTION.get());
        if (attr == null) {
            return;
        }
        AttributeModifier existing = attr.getModifier(GOLIATH_UUID);
        if (existing != null) {
            attr.removeModifier(GOLIATH_UUID);
        }
        if (goliath > 0.0D) {
            attr.addTransientModifier(new AttributeModifier(GOLIATH_UUID,
                    "heartsteel_goliath", goliath, AttributeModifier.Operation.ADDITION));
        }
    }

    private static void clearGoliath(Player player) {
        AttributeInstance attr = player.getAttribute(ModAttributes.LOL_PHYSICAL_DAMAGE_REDUCTION.get());
        if (attr != null && attr.getModifier(GOLIATH_UUID) != null) {
            attr.removeModifier(GOLIATH_UUID);
        }
    }

    /** 物品 NBT 计数器键（tooltip 显示用，随 Curios 同步到客户端）。 */
    private static final String NBT_ITEM_HEALTH_BONUS = "lolaccessories_heartsteel_item_bonus";

    /** 每秒把 NBT 记录的涨血总量恢复为 transient modifier（跨存档保留），并同步到物品 NBT 供 tooltip 显示。 */
    private static void restoreHealthBonus(Player player) {
        addHealthBonus(player, 0.0D);
        ItemStack stack = com.example.lolaccessories.compat.CuriosGearWear.findEquippedStack(
                player, GEAR_HEARTSTEEL);
        if (!stack.isEmpty()) {
            stack.getOrCreateTag().putDouble(NBT_ITEM_HEALTH_BONUS,
                    player.getPersistentData().getDouble(NBT_HEALTH_BONUS));
        }
    }

    /** 累计涨血：NBT += delta，并把 max_health 的固定 modifier 重设为 NBT 值。 */
    private static void addHealthBonus(Player player, double delta) {
        CompoundTag data = player.getPersistentData();
        double total = data.contains(NBT_HEALTH_BONUS, Tag.TAG_DOUBLE)
                ? data.getDouble(NBT_HEALTH_BONUS) : 0.0D;
        total += delta;
        data.putDouble(NBT_HEALTH_BONUS, total);
        AttributeInstance attr = player.getAttribute(net.minecraft.world.entity.ai.attributes.Attributes.MAX_HEALTH);
        if (attr == null) {
            return;
        }
        if (attr.getModifier(HEALTH_BONUS_UUID) != null) {
            attr.removeModifier(HEALTH_BONUS_UUID);
        }
        if (total > 0.0D) {
            attr.addTransientModifier(new AttributeModifier(HEALTH_BONUS_UUID,
                    "heartsteel_health", total, AttributeModifier.Operation.ADDITION));
        }
    }

    private static boolean hasFed(Player player, LivingEntity target) {
        String key = target.getUUID().toString();
        ListTag list = player.getPersistentData().getList(NBT_FED_LIST, Tag.TAG_STRING);
        for (int i = 0; i < list.size(); i++) {
            if (list.getString(i).equals(key)) {
                return true;
            }
        }
        return false;
    }

    private static void markFed(Player player, LivingEntity target) {
        ListTag list = player.getPersistentData().getList(NBT_FED_LIST, Tag.TAG_STRING);
        list.add(StringTag.valueOf(target.getUUID().toString()));
        player.getPersistentData().put(NBT_FED_LIST, list);
    }

    private static boolean isWearingHeartsteel(Player player) {
        boolean[] found = {false};
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            if (GEAR_HEARTSTEEL.equals(gear.getGearId())) {
                found[0] = true;
            }
        });
        return found[0];
    }
}
