package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 自然生命恢复（lolaccessories:natural_regen）的结算。
 *
 * <p>原版没有任何控制“自然生命恢复”的属性，因此本模组自建该属性（默认 0，值 1.0 = +100%），
 * 由事件层在玩家满足原版自然回血条件时额外补一次回血：与原版相同的 4 秒（80 tick）周期、
 * 相同的饥饿值门槛（饥饿值 ≥ 18），每次额外恢复 {@code natural_regen 值} 点生命值
 * （值 1.0 即每次多回 1 点 = 自然回血翻倍）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolRegenEvents {

    /** 与原版自然回血一致的触发周期：80 tick = 4 秒。 */
    private static final int REGEN_INTERVAL = 80;
    /** 与原版自然回血一致的饥饿值门槛（≥ 18 = 9 个鸡腿）。 */
    private static final int MIN_FOOD_LEVEL = 18;

    /** 每个玩家独立的回血计时器。 */
    private static final Map<UUID, Integer> REGEN_TIMER = new HashMap<>();

    private LolRegenEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END) {
            return;
        }
        Player player = event.player;
        if (player == null || player.level().isClientSide) {
            return;
        }
        if (player.isCreative() || player.isSpectator()) {
            return;
        }
        AttributeInstance attr = player.getAttribute(ModAttributes.LOL_NATURAL_REGEN.get());
        if (attr == null) {
            return;
        }
        double bonus = attr.getValue();
        UUID uuid = player.getUUID();
        if (bonus <= 0.0D) {
            REGEN_TIMER.remove(uuid);
            return;
        }
        // 原版自然回血条件未满足（饥饿值不足 / 生命已满）时，不累计计时
        if (player.getFoodData().getFoodLevel() < MIN_FOOD_LEVEL || player.getHealth() >= player.getMaxHealth()) {
            REGEN_TIMER.remove(uuid);
            return;
        }
        int elapsed = REGEN_TIMER.getOrDefault(uuid, 0) + 1;
        if (elapsed < REGEN_INTERVAL) {
            REGEN_TIMER.put(uuid, elapsed);
            return;
        }
        REGEN_TIMER.remove(uuid);
        if (player.getHealth() < player.getMaxHealth()) {
            // 值 1.0 = +100%：额外回血量等于自然回血量（1 点生命值）
            player.heal((float) bonus);
        }
    }
}
