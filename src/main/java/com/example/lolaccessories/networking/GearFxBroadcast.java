package com.example.lolaccessories.networking;

import net.minecraft.server.level.ServerLevel;
import net.minecraft.world.entity.Entity;

/**
 * 装备特效的服务端广播入口。
 *
 * <p>所有特效都锚定在某只活着/可追踪的实体上（佩戴者、友军玩家或灼烧中的敌人），
 * 因此统一用 {@code TRACKING_ENTITY} 分发给能看到该实体的所有玩家；目标玩家本人
 * 也在追踪列表里，能立即看到自己身上的效果。</p>
 */
public final class GearFxBroadcast {

    private GearFxBroadcast() {
    }

    /** 点亮一段窗口特效：持续 {@code ticks} 游戏刻后客户端自行消退。 */
    public static void window(Entity target, FxKind kind, int ticks) {
        if (target == null || target.isRemoved() || ticks <= 0) {
            return;
        }
        if (!(target.level() instanceof ServerLevel)) {
            return;
        }
        LOLNetworking.sendGearFx(target, kind, true, ticks);
    }

    /** 提前熄灭某特效（如魔盾被破、法力成真/救主灵刃窗口中断）。 */
    public static void off(Entity target, FxKind kind) {
        if (target == null || target.isRemoved()) {
            return;
        }
        if (!(target.level() instanceof ServerLevel)) {
            return;
        }
        LOLNetworking.sendGearFx(target, kind, false, 0);
    }

    /** 单次触发既亮起也立即熄灭（服务端显式收尾用），一般用 {@link #window} 即可。 */
    public static void pulse(Entity target, FxKind kind, int ticks) {
        window(target, kind, ticks);
    }
}
