package com.example.lolaccessories.compat;

import com.example.lolaccessories.init.ModAttributes;
import io.redspace.ironsspellbooks.api.events.SpellCooldownAddedEvent;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import io.redspace.ironsspellbooks.api.spells.AbstractSpell;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.LivingEntity;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.player.PlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 技能急速体系——铁魔法冷却结算层（仅在安装铁魔法时由 {@link IronsCompat#registerDynamicSubscribers}
 * 反射注册，未安装铁魔法时本类不会被加载）。
 *
 * <p>全部按英雄联盟官方公式换算：{@code 冷却缩减 = 急速 / (100 + 急速)}
 * （100 急速 = 50%、200 = 66.7%、300 = 75%）。两个独立乘区依次结算：</p>
 * <ol>
 * <li><b>技能急速</b>（{@link ModAttributes#LOL_ABILITY_HASTE}）：作用于<b>所有</b>铁魔法法术冷却
 *     （含终极）；朔极之矛「龙之力量」的 33 点急速只叠加到<b>基础技能</b>（非终极）上
 *     （原 25% 冷却缩减按公式 {@code 0.25 = x/(100+x)} 反推为 33 急速）。</li>
 * <li><b>终极技能急速</b>（{@link ModAttributes#LOL_ULTIMATE_HASTE}）：只作用于终极技能
 *     （基础法力消耗严格大于 {@link IronsLegendCastingEvents#ULTIMATE_MANA_THRESHOLD}），
 *     在技能急速乘区之后再乘一次独立因子。</li>
 * </ol>
 *
 * <p>结算顺序：{@code 最终冷却 = ceil(ISS 有效冷却 × (1 − CDR(AH)) × (1 − CDR(终极急速)))}——
 * ISS 普通冷却缩减属性、法杖冷却修正等已计入 {@code getEffectiveCooldown()}，与之构成独立乘区。</p>
 *
 * <p>终极判定沿用「待决标记」机制：施法瞬间（{@link SpellOnCastEvent}）记录一次标记，同一次
 * 施法栈内 ISS 随后派发 {@link SpellCooldownAddedEvent.Pre} 时消费；标记按玩家 + 法术 id 匹配，
 * 超时条目按 30 秒清理。</p>
 */
public final class IronsAbilityHasteEvents {

    /** 待决「终极」标记的最大存活毫秒数，防止个别不进入冷却结算路径的施法留下陈旧标记。 */
    private static final long PENDING_MAX_AGE_MS = 30_000L;

    /** 朔极之矛「龙之力量」：只作用于基础技能的急速点数（原 25% 冷却缩减按公式反推）。 */
    private static final double SHOJIN_HASTE = 33.0D;

    /** 玩家 UUID → 最近一次施法记录（法术 id、是否终极与记录时刻）。 */
    private static final Map<UUID, PendingSpell> PENDING = new HashMap<>();

    private IronsAbilityHasteEvents() {
    }

    /** 注册到 Forge 总线（仅铁魔法已安装时调用）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(IronsAbilityHasteEvents.class);
    }

    private record PendingSpell(String spellId, boolean ultimate, long recordedAtMs) {
    }

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 顺带清理超时陈旧标记，防止异常路径下长期残留
        long cutoff = System.currentTimeMillis() - PENDING_MAX_AGE_MS;
        PENDING.entrySet().removeIf(entry -> entry.getValue().recordedAtMs() < cutoff);
        boolean ultimate = event.getOriginalManaCost() > IronsLegendCastingEvents.ULTIMATE_MANA_THRESHOLD;
        // 猩红明朗·诺克萨斯的急速：施放法术触发移速增益
        com.example.lolaccessories.event.LolNewGearPassiveEvents.onIronSpellCast(player);
        // 天帝·我什么都不缺了：施法累计法强（消耗法力的 1%）与瑞天帝计数
        com.example.lolaccessories.event.LolEmperorPassiveEvents.onIronSpellCast(
                player, event.getOriginalManaCost());
        PENDING.put(player.getUUID(),
                new PendingSpell(event.getSpellId(), ultimate, System.currentTimeMillis()));
    }

    @SubscribeEvent
    public static void onCooldownAdded(SpellCooldownAddedEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity == null || event.isCanceled()) {
            return;
        }
        UUID uuid = entity.getUUID();
        PendingSpell pending = PENDING.remove(uuid);
        boolean ultimate = false;
        if (pending != null) {
            AbstractSpell spell = event.getSpell();
            if (spell != null && spell.getSpellId().equals(pending.spellId())
                    && System.currentTimeMillis() - pending.recordedAtMs() <= PENDING_MAX_AGE_MS) {
                ultimate = pending.ultimate();
            }
        }
        int effective = event.getEffectiveCooldown();
        if (effective <= 1) {
            return;
        }
        // 乘区一：技能急速（所有法术；龙之力量只叠加到基础技能）
        double totalHaste = hasteOf(entity, ModAttributes.LOL_ABILITY_HASTE.get());
        if (!ultimate && entity instanceof ServerPlayer player
                && CuriosGearWear.isWearing(player, "spear_of_shojin")) {
            totalHaste += SHOJIN_HASTE;
        }
        if (totalHaste > 0.0D) {
            effective = scale(effective, totalHaste);
        }
        // 乘区二：终极技能急速（仅终极）
        if (ultimate) {
            double ultHaste = hasteOf(entity, ModAttributes.LOL_ULTIMATE_HASTE.get());
            if (ultHaste > 0.0D) {
                effective = scale(effective, ultHaste);
            }
        }
        event.setEffectiveCooldown(effective);
    }

    /** LoL 公式：{@code 冷却 = ceil(当前冷却 × (1 − 急速/(100+急速)))}。 */
    private static int scale(int effective, double haste) {
        double cdr = haste / (100.0D + haste);
        int scaled = Math.max(1, (int) Math.ceil(effective * (1.0D - cdr)));
        return Math.min(scaled, effective);
    }

    private static double hasteOf(LivingEntity entity, net.minecraft.world.entity.ai.attributes.Attribute attribute) {
        var instance = entity.getAttribute(attribute);
        return instance == null ? 0.0D : Math.max(0.0D, instance.getValue());
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }
}
