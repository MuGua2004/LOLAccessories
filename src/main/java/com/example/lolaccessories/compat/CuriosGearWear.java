package com.example.lolaccessories.compat;

import com.example.lolaccessories.item.GearItem;
import net.minecraft.world.entity.player.Player;
import top.theillusivec4.curios.api.CuriosApi;

import java.util.List;

/**
 * Curios 佩戴查询小工具。
 *
 * <p>通过 gear_id 判断玩家是否在 Curios 槽位佩戴了对应饰品。
 * 服务端（触发被动）与客户端（HUD 渲染）共用同一逻辑。</p>
 */
public final class CuriosGearWear {

    private CuriosGearWear() {
    }

    /** 玩家是否佩戴了 gearId 对应的饰品（任意 Curios 槽位）。 */
    public static boolean isWearing(Player player, String gearId) {
        var equipped = CuriosApi.getCuriosInventory(player)
                .resolve()
                .map(handler -> handler.findCurios(
                        stack -> stack.getItem() instanceof GearItem gear
                                && gearId.equals(gear.getGearId())))
                .orElse(List.of());
        return !equipped.isEmpty();
    }

    /** 遍历玩家当前佩戴的全部 LOLAccessories 饰品（任意 Curios 槽位，包括非玩家默认槽）。 */
    public static void forEachEquippedGear(Player player, java.util.function.Consumer<GearItem> action) {
        CuriosApi.getCuriosInventory(player).resolve().ifPresent(handler -> {
            for (var stacksHandler : handler.getCurios().values()) {
                var inventory = stacksHandler.getStacks();
                for (int i = 0; i < inventory.getSlots(); i++) {
                    var stack = inventory.getStackInSlot(i);
                    if (!stack.isEmpty() && stack.getItem() instanceof GearItem gear) {
                        action.accept(gear);
                    }
                }
            }
        });
    }
}
