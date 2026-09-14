package com.example.lolaccessories.client;

import com.example.lolaccessories.networking.FxKind;
import net.minecraft.client.Minecraft;
import net.minecraft.world.level.Level;

import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * 地点锚定特效的客户端生命周期（客户端专用）。
 *
 * <p>服务端通过 {@link com.example.lolaccessories.networking.FxSpotPacket} 通知
 * 「在某个世界坐标播放某种特效、持续 N tick」，本类维护特效窗口列表；
 * {@link GearFxRenderer} 在世界渲染兜底阶段（AFTER_ENTITIES）绘制。</p>
 */
public final class ActiveSpotFx {

    /** 一条地点特效。{@code radius} 为作用半径（法阵圈大小）。 */
    public record Spot(FxKind kind, double x, double y, double z, float radius,
                       long totalTicks, long expireGameTime) {
    }

    private static final List<Spot> SPOTS = new CopyOnWriteArrayList<>();

    private ActiveSpotFx() {
    }

    /** 收到服务端特效包：登记一条地点特效窗口。 */
    public static void apply(FxKind kind, double x, double y, double z, int ticks, float radius) {
        Level level = Minecraft.getInstance().level;
        if (level == null || ticks <= 0) {
            return;
        }
        SPOTS.add(new Spot(kind, x, y, z, radius, ticks, level.getGameTime() + ticks));
    }

    /** 当前生效的地点特效（先清理过期项；CopyOnWriteArrayList 的迭代器不支持 remove，用 removeIf）。 */
    public static List<Spot> spots() {
        Level level = Minecraft.getInstance().level;
        if (level != null) {
            long now = level.getGameTime();
            SPOTS.removeIf(s -> now > s.expireGameTime());
        }
        return SPOTS;
    }

    public static boolean hasSpots() {
        return !spots().isEmpty();
    }
}
