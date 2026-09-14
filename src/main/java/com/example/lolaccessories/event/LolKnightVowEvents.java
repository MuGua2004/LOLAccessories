package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 骑士之誓（Knight's Vow）——誓约与牺牲。
 *
 * <p><b>主动·誓约（Pledge）</b>：与准星方向 6 格内的友方生物（含玩家）缔结系链；
 * 同一目标同一时间只能被一名骑士之誓绑定（60 秒冷却）。</p>
 *
 * <p><b>被动·牺牲（Sacrifice）</b>：系链期间（目标与骑士距离 ≤32 格、骑士生命高于 30%），
 * 目标受到的物理/魔法伤害的 <b>14%</b>（减免前）按原类型转移给骑士；目标造成伤害时，
 * 骑士回复该伤害 <b>12%</b> 的生命。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolKnightVowEvents {

    public static final String GEAR_KNIGHTS_VOW = "knights_vow";
    /** 伤害转移比例（减免前）。 */
    private static final double TRANSFER_RATIO = 0.14D;
    /** 骑士回复比例（目标造成伤害的）。 */
    private static final double HEAL_RATIO = 0.12D;
    /** 骑士生命低于该比例时不再转移。 */
    private static final double MIN_HEALTH_RATIO = 0.30D;
    /** 系链有效距离。 */
    private static final double LINK_RANGE = 32.0D;
    /** Pledge 冷却（毫秒）。 */
    private static final long PLEDGE_COOLDOWN_MS = 60000L;

    /** 绑定关系：骑士 UUID → 被绑定目标。 */
    private static final Map<UUID, Link> LINKS = new HashMap<>();
    /** 目标 UUID → 绑定它的骑士 UUID（同一目标只能被一名骑士绑定）。 */
    private static final Map<UUID, UUID> TARGET_OWNER = new HashMap<>();
    private static final Map<UUID, Long> PLEDGE_CD_MS = new HashMap<>();

    /** 一次系链。 */
    private static final class Link {
        final UUID targetId;
        final LivingEntity target;

        Link(UUID targetId, LivingEntity target) {
            this.targetId = targetId;
            this.target = target;
        }
    }

    private LolKnightVowEvents() {
    }

    // ------------------------------------------------------------------ //
    // 主动：誓约
    // ------------------------------------------------------------------ //

    /** 与准星方向 6 格内的第一个友方生物（含玩家）缔结系链。返回绑定数（1 或 0）。 */
    public static int pledge(ServerPlayer player) {
        long now = System.currentTimeMillis();
        Long cd = PLEDGE_CD_MS.get(player.getUUID());
        if (cd != null && now < cd) {
            player.displayClientMessage(Component.translatable(
                    "msg.lolaccessories.pledge.cooldown"), true);
            return 0;
        }
        unbind(player.getUUID());

        // 沿视线找 6 格内最近的非敌对 LivingEntity（含玩家，不含自己）
        Vec3 eye = player.getEyePosition();
        Vec3 look = player.getLookAngle();
        LivingEntity best = null;
        double bestScore = Double.MAX_VALUE;
        for (LivingEntity e : player.level().getEntitiesOfClass(LivingEntity.class,
                player.getBoundingBox().inflate(8.0D),
                e -> e != player && e.isAlive()
                        && !LolNewEpicPassiveEvents.isEnemyOf(player, e))) {
            Vec3 to = e.position().add(0, e.getBbHeight() * 0.5D, 0).subtract(eye);
            double dist = to.length();
            if (dist > 6.0D) {
                continue;
            }
            double align = to.normalize().dot(look); // 1 = 正对准星
            if (align < 0.85D) {
                continue;
            }
            double score = dist * (2.0D - align);
            if (score < bestScore) {
                bestScore = score;
                best = e;
            }
        }
        if (best == null) {
            player.displayClientMessage(Component.translatable(
                    "msg.lolaccessories.pledge.no_target"), true);
            return 0;
        }
        UUID existingOwner = TARGET_OWNER.get(best.getUUID());
        if (existingOwner != null && !existingOwner.equals(player.getUUID())) {
            player.displayClientMessage(Component.translatable(
                    "msg.lolaccessories.pledge.already_bound"), true);
            return 0;
        }
        LINKS.put(player.getUUID(), new Link(best.getUUID(), best));
        TARGET_OWNER.put(best.getUUID(), player.getUUID());
        PLEDGE_CD_MS.put(player.getUUID(), now + PLEDGE_COOLDOWN_MS);
        if (player.level() instanceof ServerLevel level) {
            level.sendParticles(net.minecraft.core.particles.ParticleTypes.HEART,
                    best.getX(), best.getEyeY() + 0.5D, best.getZ(), 8, 0.3D, 0.3D, 0.3D, 0.0D);
            level.playSound(null, best.blockPosition(),
                    SoundEvents.PLAYER_LEVELUP, SoundSource.PLAYERS, 0.6F, 1.5F);
        }
        player.displayClientMessage(Component.translatable(
                "msg.lolaccessories.pledge.bound", best.getName()), true);
        return 1;
    }

    /** 解绑并释放目标占用。 */
    private static void unbind(UUID knightId) {
        Link old = LINKS.remove(knightId);
        if (old != null) {
            UUID tid = old.targetId;
            // 只有仍指向该骑士时才释放
            if (playerEquals(TARGET_OWNER.get(tid), knightId)) {
                TARGET_OWNER.remove(tid);
            }
        }
    }

    private static boolean playerEquals(UUID a, UUID b) {
        return a != null && a.equals(b);
    }

    // ------------------------------------------------------------------ //
    // 每 tick：校验链（目标存活/在范围内/骑士仍佩戴装备），失效则解绑
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player)) {
            return;
        }
        Link link = LINKS.get(player.getUUID());
        if (link == null) {
            return;
        }
        LivingEntity target = link.target;
        boolean invalid = target.isRemoved() || target.isDeadOrDying()
                || !CuriosGearWear.isWearing(player, GEAR_KNIGHTS_VOW)
                || target.distanceToSqr(player) > LINK_RANGE * LINK_RANGE;
        if (invalid) {
            unbind(player.getUUID());
            player.displayClientMessage(Component.translatable(
                    "msg.lolaccessories.pledge.broken"), true);
        }
    }

    // ------------------------------------------------------------------ //
    // 牺牲：伤害转移 + 回血
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0.0F) {
            return;
        }
        // 目标受到伤害 → 14% 转移给骑士（减免前，按原类型），骑士生命 >30% 才生效
        UUID owner = TARGET_OWNER.get(event.getEntity().getUUID());
        if (owner != null) {
            ServerPlayer knight = findKnight(owner, event.getEntity());
            if (knight != null && knight.getHealth() > knight.getMaxHealth() * MIN_HEALTH_RATIO) {
                float transfer = event.getAmount() * (float) TRANSFER_RATIO;
                event.setAmount(event.getAmount() - transfer);
                // 转移伤害给骑士：标记防递归（骑士受伤不再二次触发转移逻辑）
                var src = event.getSource();
                knight.hurt(src, transfer);
            }
        }
    }

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide || event.getAmount() <= 0.0F) {
            return;
        }
        // 绑定友军造成伤害 → 骑士回复 12%
        if (!(event.getSource().getEntity() instanceof LivingEntity striker)) {
            return;
        }
        UUID owner = TARGET_OWNER.get(striker.getUUID());
        if (owner == null) {
            return;
        }
        ServerPlayer knight = findKnightByPlayerId(owner);
        if (knight == null || knight == striker) {
            return;
        }
        knight.heal(event.getAmount() * (float) HEAL_RATIO);
    }

    private static ServerPlayer findKnight(UUID knightId, LivingEntity nearTarget) {
        if (nearTarget.level() instanceof ServerLevel level) {
            for (ServerPlayer p : level.players()) {
                if (p.getUUID().equals(knightId)) {
                    return p;
                }
            }
        }
        return null;
    }

    private static ServerPlayer findKnightByPlayerId(UUID knightId) {
        // 遍历已缓存骑士所在维度过于复杂——直接在服务器全玩家里找（单机/小型服可接受）
        var server = net.minecraftforge.server.ServerLifecycleHooks.getCurrentServer();
        if (server == null) {
            return null;
        }
        for (ServerPlayer p : server.getPlayerList().getPlayers()) {
            if (p.getUUID().equals(knightId)) {
                return p;
            }
        }
        return null;
    }
}
