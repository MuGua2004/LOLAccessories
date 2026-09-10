package com.example.lolaccessories.money;

import com.example.lolaccessories.init.ModItems;
import net.minecraft.world.Container;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;

/**
 * 物品“钱包”：统计 / 扣除玩家身上某种物品的数量。
 *
 * <p>约定：花费物品只从玩家的<b>背包</b>与<b>末影箱</b>中扣除（锻造等付费合成不要求把花费
 * 物品放进合成容器），统计顺序为背包优先、末影箱兜底。金币（{@link ModItems#GOLD_COIN}）
 * 只是其中一种可被扣除的物品。</p>
 */
public final class GoldWallet {

    private GoldWallet() {
    }

    /** 统计玩家背包 + 末影箱中指定物品的总数。 */
    public static int countItems(Player player, Item item) {
        if (item == null) {
            return 0;
        }
        int total = countIn(player.getInventory(), item);
        total += countIn(player.getEnderChestInventory(), item);
        return total;
    }

    /** 尝试扣除指定数量物品（背包优先，不足部分从末影箱补扣）。成功返回 true。 */
    public static boolean trySpendItems(Player player, Item item, int amount) {
        if (item == null || amount <= 0) {
            return true;
        }
        if (countItems(player, item) < amount) {
            return false;
        }
        int need = amount;
        need = removeFrom(player.getInventory(), item, need);
        if (need > 0) {
            need = removeFrom(player.getEnderChestInventory(), item, need);
        }
        return need == 0;
    }

    /** 统计玩家背包 + 末影箱中的金币总数。 */
    public static int countCoins(Player player) {
        return countItems(player, ModItems.GOLD_COIN.get());
    }

    /** 尝试扣除指定数量金币（背包优先，不足部分从末影箱补扣）。成功返回 true。 */
    public static boolean trySpendCoins(Player player, int amount) {
        return trySpendItems(player, ModItems.GOLD_COIN.get(), amount);
    }

    private static int countIn(Container container, Item item) {
        int count = 0;
        for (int i = 0; i < container.getContainerSize(); i++) {
            ItemStack stack = container.getItem(i);
            if (matches(stack, item)) {
                count += stack.getCount();
            }
        }
        return count;
    }

    /** 从容器中扣除至多 need 个指定物品，返回剩余待扣数量。 */
    private static int removeFrom(Container container, Item item, int need) {
        for (int i = 0; i < container.getContainerSize() && need > 0; i++) {
            ItemStack stack = container.getItem(i);
            if (matches(stack, item)) {
                int take = Math.min(stack.getCount(), need);
                stack.shrink(take);
                need -= take;
            }
        }
        return need;
    }

    private static boolean matches(ItemStack stack, Item item) {
        return !stack.isEmpty() && stack.getItem() == item;
    }
}
