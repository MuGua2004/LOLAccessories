package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 金币掉落：玩家亲手击杀生物时，按目标的「最大生命值」分档掉落金币。
 *
 * <ul>
 *   <li>低于 100 血：5～9 枚</li>
 *   <li>100～999 血：23～67 枚</li>
 *   <li>1000～99999 血：200～399 枚</li>
 *   <li>10 万血及以上：500～1000 枚</li>
 * </ul>
 *
 * <p>仅「击杀者是玩家」才算数（含弓箭/投掷等间接击杀），防止刷怪塔与岩浆、
 * 窒息等环境致死无脑刷币；掉落物生成在死亡位置附近。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolGoldDropEvents {

    private LolGoldDropEvents() {
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        LivingEntity dead = event.getEntity();
        // 玩家（或被玩家控制的假人）不产出金币
        if (dead instanceof Player) {
            return;
        }
        if (event.getSource() == null) {
            return;
        }
        // getEntity() 会解析到真正的击杀者：远程射击/投掷由射出者承担
        Entity killer = event.getSource().getEntity();
        if (!(killer instanceof Player)) {
            return;
        }

        int amount = rollAmount(dead.getMaxHealth(), dead);
        if (amount <= 0) {
            return;
        }
        spawnCoins(dead, amount);
        LOLAccessories.LOGGER.debug("[金币] {} 击杀了 {}（{} 血），掉落 {} 枚金币",
                killer.getName().getString(), dead.getName().getString(),
                (long) dead.getMaxHealth(), amount);
    }

    /** 依据目标最大生命值掷出掉落数量（含端点）。 */
    private static int rollAmount(double maxHealth, LivingEntity dead) {
        if (maxHealth < 100.0D) {
            return 5 + dead.getRandom().nextInt(5);    // 5～9
        }
        if (maxHealth < 1000.0D) {
            return 23 + dead.getRandom().nextInt(45);  // 23～67
        }
        if (maxHealth < 100000.0D) {
            return 200 + dead.getRandom().nextInt(200); // 200～399
        }
        return 500 + dead.getRandom().nextInt(501);    // 500～1000
    }

    private static void spawnCoins(LivingEntity dead, int amount) {
        ItemStack stack = new ItemStack(ModItems.GOLD_COIN.get(), amount);
        ItemEntity coins = new ItemEntity(dead.level(),
                dead.getX(), dead.getY() + 0.1D, dead.getZ(), stack);
        coins.setPickUpDelay(10);
        dead.level().addFreshEntity(coins);
    }
}
