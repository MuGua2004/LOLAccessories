package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.networking.FxKind;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.Collection;
import java.util.Collections;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/**
 * 客户端装备特效状态管理器。
 *
 * <p>服务端通过 {@link com.example.lolaccessories.networking.GearFxPacket} 通知目标实体
 * 应该亮起/熄灭哪种视觉特效。这里在客户端维护“每实体每类特效”的剩余刻数，过期自动清除。</p>
 *
 * <p>护罩类特效（败魔 / 原生质护带）共享同一个“护罩槽位”：后到先占，避免不同装备的
 * 球形护罩在视觉上重叠。增幅类特效（战歌 / 实现器 / 音管 / 弩箭弹幕）各自独立，但同 kind
 * 重复触发时只会刷新剩余时间，不会叠出多层。</p>
 */
public final class ActiveGearFx {

    private static final Map<UUID, EnumMap<FxKind, ActiveFx>> FX = new HashMap<>();
    private static final Set<FxKind> SHIELD_KINDS = EnumSet.of(
            FxKind.SHIELD_ROOKERN, FxKind.SHIELD_PROTOPLASM,
            FxKind.SHIELD_SERAPH, FxKind.SHIELD_FIMBULWINTER,
            FxKind.SHIELD_STERAK, FxKind.SHIELD_BLOODTHIRSTER, FxKind.SHIELD_MAW);

    private ActiveGearFx() {
    }

    /**
     * 应用服务端下发的特效开关。
     *
     * @param kind   特效类型
     * @param target 目标实体 UUID
     * @param active true 点亮，false 熄灭
     * @param ticks  点亮时的持续游戏刻（熄灭时忽略）
     */
    public static void apply(FxKind kind, UUID target, boolean active, int ticks) {
        if (active) {
            add(kind, target, Math.max(1, ticks));
        } else {
            remove(kind, target);
        }
    }

    private static void add(FxKind kind, UUID target, int ticks) {
        EnumMap<FxKind, ActiveFx> map = FX.computeIfAbsent(target, k -> new EnumMap<>(FxKind.class));
        // 护罩类共享槽位：新的护罩到来时，先熄灭旧的护罩特效。
        if (SHIELD_KINDS.contains(kind)) {
            Iterator<Map.Entry<FxKind, ActiveFx>> it = map.entrySet().iterator();
            while (it.hasNext()) {
                Map.Entry<FxKind, ActiveFx> entry = it.next();
                if (SHIELD_KINDS.contains(entry.getKey()) && entry.getKey() != kind) {
                    it.remove();
                }
            }
        }
        ActiveFx fx = map.get(kind);
        if (fx == null) {
            map.put(kind, new ActiveFx(kind, ticks));
        } else {
            fx.remainingTicks = ticks;
        }
    }

    private static void remove(FxKind kind, UUID target) {
        EnumMap<FxKind, ActiveFx> map = FX.get(target);
        if (map == null) {
            return;
        }
        map.remove(kind);
        if (map.isEmpty()) {
            FX.remove(target);
        }
    }

    /** 返回某实体当前激活的全部特效（只读）。 */
    public static Collection<ActiveFx> getActive(UUID target) {
        EnumMap<FxKind, ActiveFx> map = FX.get(target);
        return map == null ? Collections.emptyList() : Collections.unmodifiableCollection(map.values());
    }

    /** 当前是否存在任意激活中的特效窗口（供每帧兜底渲染快速出口）。 */
    public static boolean hasActiveFx() {
        return !FX.isEmpty();
    }

    /** 当前持有特效窗口的全部目标 UUID（只读快照，供世界级兜底渲染查询）。 */
    public static Set<UUID> activeTargets() {
        if (FX.isEmpty()) {
            return Collections.emptySet();
        }
        return new HashSet<>(FX.keySet());
    }

    /** 检查某实体是否仍有指定特效。 */
    public static boolean isActive(UUID target, FxKind kind) {
        EnumMap<FxKind, ActiveFx> map = FX.get(target);
        return map != null && map.containsKey(kind);
    }

    /** 统计当前有多少个实体正激活着 {@code kind} 特效（用于同屏拥挤度自适应）。 */
    public static int countByKind(FxKind kind) {
        int count = 0;
        for (EnumMap<FxKind, ActiveFx> map : FX.values()) {
            if (map.containsKey(kind)) {
                count++;
            }
        }
        return count;
    }

    /** 客户端每 tick 衰减剩余时间，过期即移除。 */
    private static void tick() {
        if (FX.isEmpty()) {
            return;
        }
        Iterator<Map.Entry<UUID, EnumMap<FxKind, ActiveFx>>> outer = FX.entrySet().iterator();
        while (outer.hasNext()) {
            Map.Entry<UUID, EnumMap<FxKind, ActiveFx>> entry = outer.next();
            Iterator<Map.Entry<FxKind, ActiveFx>> inner = entry.getValue().entrySet().iterator();
            while (inner.hasNext()) {
                ActiveFx fx = inner.next().getValue();
                if (--fx.remainingTicks <= 0) {
                    inner.remove();
                }
            }
            if (entry.getValue().isEmpty()) {
                outer.remove();
            }
        }
    }

    /** 单个激活的特效条目。 */
    public static final class ActiveFx {
        public final FxKind kind;
        public final int maxTicks;
        public int remainingTicks;

        private ActiveFx(FxKind kind, int ticks) {
            this.kind = kind;
            this.maxTicks = ticks;
            this.remainingTicks = ticks;
        }
    }

    /** 客户端 tick 事件订阅器：仅在客户端加载。 */
    @Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
    public static final class ClientTicker {

        private ClientTicker() {
        }

        @SubscribeEvent
        public static void onClientTick(TickEvent.ClientTickEvent event) {
            if (event.phase == TickEvent.Phase.END) {
                tick();
            }
        }
    }
}
