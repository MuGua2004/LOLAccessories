package com.example.lolaccessories.item;

import net.minecraft.network.chat.Component;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.TooltipFlag;
import net.minecraft.world.level.Level;

import javax.annotation.Nullable;
import java.util.List;

/**
 * 金币（Gold Coin）。
 *
 * <p>击杀生物掉落的通用货币，不属于 Curios 饰品；堆叠上限 64（原版默认值），
 * 可用 1 个金块无序合成。tooltip 无风味描述（自定义物品，官方无对应描述，不自行添加）。</p>
 */
public class GoldCoinItem extends Item {

    public GoldCoinItem(Properties properties) {
        super(properties);
    }

    @Override
    public void appendHoverText(ItemStack stack, @Nullable Level level,
                                List<Component> tooltip, TooltipFlag flag) {
        super.appendHoverText(stack, level, tooltip, flag);
    }
}
