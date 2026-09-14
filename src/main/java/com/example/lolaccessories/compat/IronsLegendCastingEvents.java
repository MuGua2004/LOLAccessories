package com.example.lolaccessories.compat;

import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.event.Lol2026LegendPassiveEvents;
import com.example.lolaccessories.event.LolNewEpicPassiveEvents;
import com.example.lolaccessories.event.LolNewLegendPassivesEvents;
import io.redspace.ironsspellbooks.api.events.SpellOnCastEvent;
import net.minecraft.server.level.ServerPlayer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.eventbus.api.SubscribeEvent;

/**
 * 2026 传说装备与铁魔法（Iron's Spells 'n Spellbooks）施法链路的联动订阅器。
 *
 * <p>本类的方法签名直接引用铁魔法事件类型，只能由 {@code IronsCompat.registerDynamicSubscribers()}
 * 在探测到铁魔法存在后通过反射注册（铁魔法缺席时不能被 JVM 加载）。</p>
 *
 * <ul>
 *   <li>黄昏黎明·咒刃：每次成功施法后装填下一次普攻；</li>
 *   <li>猎魔人弩箭·开战弹幕：施放基础法力消耗 &gt; 200 的终极技能后装填 3 次远程普攻；</li>
 *   <li>实现器·法力成真：窗口内施法法力消耗翻倍（config 的 mana_cost_multiplier）。</li>
 * </ul>
 */
public final class IronsLegendCastingEvents {

    /** 终极技能判定门槛：基础法力消耗严格大于该值的法术视为终极技能（200 本身不算）。 */
    public static final int ULTIMATE_MANA_THRESHOLD = 200;

    private IronsLegendCastingEvents() {
    }

    public static void register() {
        MinecraftForge.EVENT_BUS.register(IronsLegendCastingEvents.class);
    }

    @SubscribeEvent
    public static void onSpellCast(SpellOnCastEvent event) {
        if (event.isCanceled() || !(event.getEntity() instanceof ServerPlayer player)) {
            return;
        }
        int originalManaCost = event.getOriginalManaCost();

        // 1) 实现器·法力成真：窗口内施法法力消耗翻倍
        if (Lol2026LegendPassiveEvents.isRealizeActive(player)) {
            GearConfig config = GearConfigManager.get(Lol2026LegendPassiveEvents.GEAR_ACTUALIZER);
            double multiplier = 2.0D;
            if (config != null) {
                multiplier = config.findEffect(Lol2026LegendPassiveEvents.EFFECT_REALIZE)
                        .map(e -> e.mana_cost_multiplier > 0 ? e.mana_cost_multiplier : 2.0D)
                        .orElse(2.0D);
            }
            int doubled = (int) Math.ceil(originalManaCost * multiplier);
            event.setManaCost(Math.max(originalManaCost, doubled));
        }

        // 2) 黄昏黎明与夺萃之镰·咒刃装填
        Lol2026LegendPassiveEvents.armDuskSpellblade(player);
        LolNewEpicPassiveEvents.armEssenceReaverSpellblade(player);

        // 3) 猎魔人弩箭·开战弹幕装填（基础法力消耗严格大于 200 视为终极技能）
        if (originalManaCost > ULTIMATE_MANA_THRESHOLD) {
            Lol2026LegendPassiveEvents.armBarrage(player);
            // 海克斯注力刚壁·过载：施放终极技能后进入攻速/移速超载
            LolNewLegendPassivesEvents.onUltimateCast(player);
            // 基克的聚合·霜火风暴：施放终极技能后 5 秒内就绪一个风暴
            LolNewLegendPassivesEvents.onUltimateCastZeke(player);
            // 残疫·憎恨之雾：施放终极技能后 3 秒内的魔法伤害会在敌人脚下生成恨雾
            com.example.lolaccessories.event.LolMalignanceEvents.onUltimateCast(player);
        }
    }
}
