package com.example.lolaccessories.client;

import com.example.lolaccessories.compat.CuriosGearWear;
import net.minecraft.world.entity.player.Player;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 幻影之舞佩戴状态查询（带逐 tick 缓存）。
 *
 * <p>{@code PhantomDancerCollisionMixin} 注入的 {@code LivingEntity#isPushable} 在实体间
 * 推挤检测里调用极频繁（每实体对每 tick 多次），直接走 Curios 槽位遍历会产生可观开销。
 * 这里按「玩家 UUID → 游戏 tick」缓存判定结果：同一 tick 内重复查询直接命中缓存，
 * 跨 tick 失效重查。双端（服务端物理 + 客户端预测）各自独立缓存，语义一致。</p>
 *
 * <p>注意：本类位于 client 包仅为与其它纯客户端 FX 类区分；mixin 注入点在双端都会加载
 * （mixins.json 的公共列表），本类不引用任何仅客户端类型，可安全双端使用。</p>
 */
public final class PhantomDancerState {

    private static final Map<UUID, CachedFlag> CACHE = new ConcurrentHashMap<>();

    private PhantomDancerState() {
    }

    /** 该玩家当前是否佩戴幻影之舞（同一游戏 tick 内结果缓存复用）。 */
    public static boolean isWearing(Player player) {
        UUID uuid = player.getUUID();
        long tick = player.level().getGameTime();
        CachedFlag cached = CACHE.get(uuid);
        if (cached != null && cached.tick == tick) {
            return cached.value;
        }
        boolean value = CuriosGearWear.isWearing(player, "phantom_dancer");
        CACHE.put(uuid, new CachedFlag(tick, value));
        if (CACHE.size() > 512) {
            CACHE.entrySet().removeIf(e -> e.getValue().tick < tick - 20);
        }
        return value;
    }

    private record CachedFlag(long tick, boolean value) {
    }
}
