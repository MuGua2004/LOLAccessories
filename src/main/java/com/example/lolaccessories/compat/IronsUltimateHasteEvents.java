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
 * 终极技能冷却缩减——铁魔法冷却结算层（仅在安装铁魔法时由 {@link IronsCompat#registerDynamicSubscribers}
 * 反射注册，未安装铁魔法时本类不会被加载）。
 *
 * <p>数值来自玩家属性表的 {@link ModAttributes#LOL_ULTIMATE_CDR}（猎魔人弩箭的 gear JSON 在
 * attributes 中声明该加成）。它在装备上是<b>唯一被动</b>形态，但当前以属性机制承载并结算——
 * 展示层（{@code GearItem}）按「唯一被动」样式排版，属性本身仍按属性加成注入玩家。</p>
 *
 * <p>终极技能定义为与开战弹幕一致的门槛（基础法力消耗严格大于
 * {@link IronsLegendCastingEvents#ULTIMATE_MANA_THRESHOLD}，200 不算）。施法瞬间
 * （{@link SpellOnCastEvent}）记录一次「待决终极」标记；同一次施法栈内 ISS 随后派发
 * {@link SpellCooldownAddedEvent.Pre}，消费该标记并在 ISS 普通冷却缩减（含玩家冷却缩减属性、
 * 法杖冷却修正等已计入 {@code getEffectiveCooldown()}）之后，再乘一次独立因子：
 * {@code 最终冷却 = ceil(有效冷却 × (1 − 缩减倍率))}——即与普通冷却缩减构成独立乘区。</p>
 *
 * <p>标记按玩家 + 法术 id 匹配，且任何 {@code SpellCooldownAddedEvent.Pre} 派发时都会消费一次
 * （铁魔法对卷轴施法也会派发 Pre 但随后直接返回、不真正计冷却，因此该情况下缩放结果无副作用）。
 * 冷却结算为同线程内同步调用，标记生命周期很短，超时条目按 30 秒清理，防止异常路径下残留。</p>
 */
public final class IronsUltimateHasteEvents {

    /** 待决「终极」标记的最大存活毫秒数，防止个别不进入冷却结算路径的施法留下陈旧标记。 */
    private static final long PENDING_MAX_AGE_MS = 30_000L;

    /** 玩家 UUID → 该玩家最近一次「终极」施法记录（同一法术 id + 记录时刻）。 */
    private static final Map<UUID, PendingUltimate> PENDING = new HashMap<>();

    private IronsUltimateHasteEvents() {
    }

    /** 注册到 Forge 总线（仅铁魔法已安装时调用）。 */
    public static void register() {
        MinecraftForge.EVENT_BUS.register(IronsUltimateHasteEvents.class);
    }

    private record PendingUltimate(String spellId, long recordedAtMs) {
    }

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        // 顺带清理超时陈旧标记，防止异常路径下长期残留
        long cutoff = System.currentTimeMillis() - PENDING_MAX_AGE_MS;
        PENDING.entrySet().removeIf(entry -> entry.getValue().recordedAtMs() < cutoff);
        // 仅当本施法满足终极门槛（基础法力消耗严格 > 200）时才记录；其余施法不写入标记，
        // 这样之后它真正计入冷却时会消费掉更早留下的陈旧标记，避免误缩放非终极技能。
        if (event.getOriginalManaCost() <= IronsLegendCastingEvents.ULTIMATE_MANA_THRESHOLD) {
            PENDING.remove(player.getUUID());
            return;
        }
        PENDING.put(player.getUUID(),
                new PendingUltimate(event.getSpellId(), System.currentTimeMillis()));
    }

    @SubscribeEvent
    public static void onCooldownAdded(SpellCooldownAddedEvent.Pre event) {
        LivingEntity entity = event.getEntity();
        if (entity == null) {
            return;
        }
        UUID uuid = entity.getUUID();
        PendingUltimate pending = PENDING.remove(uuid);
        if (pending == null) {
            return;
        }
        if (event.isCanceled()) {
            return;
        }
        AbstractSpell spell = event.getSpell();
        if (spell == null || !spell.getSpellId().equals(pending.spellId())) {
            // 冷却归属的法术与标记不一致：不缩放（陈旧标记已在上面消费掉）。
            return;
        }
        if (System.currentTimeMillis() - pending.recordedAtMs() > PENDING_MAX_AGE_MS) {
            return;
        }
        // 缩减倍率直接取自玩家属性表（猎魔人弩箭通过 gear JSON attributes 配置向玩家注入
        // lolaccessories:ultimate_cdr 属性；未佩戴该装备时属性为 0，不触发缩放）。
        double ratio = Math.max(0.0D, entity.getAttributeValue(ModAttributes.LOL_ULTIMATE_CDR.get()));
        if (ratio <= 0.0D) {
            return;
        }
        int effective = event.getEffectiveCooldown();
        if (effective <= 1) {
            return;
        }
        int scaled = Math.max(1, (int) Math.ceil(effective * (1.0D - ratio)));
        if (scaled < effective) {
            event.setEffectiveCooldown(scaled);
        }
    }

    @SubscribeEvent
    public static void onPlayerLoggedOut(PlayerEvent.PlayerLoggedOutEvent event) {
        PENDING.remove(event.getEntity().getUUID());
    }
}
