package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 适应之力（Adaptive Force）动态加成：比较攻击力 / 法术强度 / 弹射物伤害三项的
 * <b>装备加成部分</b>（百分比形式的法强与弹射物先化成小数再 × 100 化为点数），
 * 把适应之力数值加到最高的一项上。每 0.5 秒重算一次。
 *
 * <p>重算前先摘除本系统挂的修正器再比较面板，避免「适应之力自身加成」参与比较造成振荡。
 * 加成形式与目标属性一致：攻击力加点数，法术强度/弹射物伤害按百分比语义加（+15 适应之力
 * → amount 0.15）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolAdaptiveForceEvents {

    private static final UUID ADAPTIVE_APPLY_UUID =
            UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-000000000071");
    /** 目标属性 key：0=攻击力（点数），1=法术强度（百分比），2=弹射物伤害（百分比）。 */
    private static final Map<UUID, Integer> LAST_TARGET = new HashMap<>();

    private LolAdaptiveForceEvents() {
    }

    @SubscribeEvent
    public static void onServerTick(TickEvent.ServerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.getServer().getTickCount() % 10 != 0) {
            return;
        }
        MinecraftServer server = event.getServer();
        for (ServerPlayer player : server.getPlayerList().getPlayers()) {
            AttributeInstance adaptiveAttr = player.getAttribute(ModAttributes.LOL_ADAPTIVE_FORCE.get());
            double adaptive = adaptiveAttr == null ? 0.0D : adaptiveAttr.getValue();
            // 比较前先摘除上次施加的修正器，保证比较基准不含适应之力自身
            clearApplied(player);
            if (adaptive <= 0.0D) {
                LAST_TARGET.remove(player.getUUID());
                continue;
            }
            int target = pickHighest(player);
            apply(player, target, adaptive);
            LAST_TARGET.put(player.getUUID(), target);
        }
    }

    /** 比较三项装备加成（百分比 × 100 化点数），返回最高项的 key。 */
    private static int pickHighest(ServerPlayer player) {
        double ad = bonusOf(player.getAttribute(Attributes.ATTACK_DAMAGE), 1.0D);
        double ap = bonusOf(spellPowerAttr(player), 1.0D) * 100.0D;
        double arrow = bonusOf(arrowDamageAttr(player), 1.0D) * 100.0D;
        if (ad >= ap && ad >= arrow) {
            return 0;
        }
        return ap >= arrow ? 1 : 2;
    }

    private static void apply(ServerPlayer player, int target, double adaptive) {
        AttributeInstance attr = switch (target) {
            case 0 -> player.getAttribute(Attributes.ATTACK_DAMAGE);
            case 1 -> spellPowerAttr(player);
            default -> arrowDamageAttr(player);
        };
        if (attr == null) {
            return;
        }
        double amount = target == 0 ? adaptive : adaptive / 100.0D;
        attr.addTransientModifier(new AttributeModifier(ADAPTIVE_APPLY_UUID,
                "lolaccessories_adaptive_force", amount, AttributeModifier.Operation.ADDITION));
    }

    private static void clearApplied(ServerPlayer player) {
        Integer last = LAST_TARGET.get(player.getUUID());
        if (last == null) {
            return;
        }
        AttributeInstance attr = switch (last) {
            case 0 -> player.getAttribute(Attributes.ATTACK_DAMAGE);
            case 1 -> spellPowerAttr(player);
            default -> arrowDamageAttr(player);
        };
        if (attr != null && attr.getModifier(ADAPTIVE_APPLY_UUID) != null) {
            attr.removeModifier(ADAPTIVE_APPLY_UUID);
        }
    }

    /** 属性的「装备加成部分」：总值 − 基础值（各自 base 均为 1.0）。 */
    private static double bonusOf(AttributeInstance attr, double base) {
        return attr == null ? 0.0D : Math.max(0.0D, attr.getValue() - base);
    }

    private static AttributeInstance spellPowerAttr(ServerPlayer player) {
        return player.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("irons_spellbooks", "spell_power")));
    }

    private static AttributeInstance arrowDamageAttr(ServerPlayer player) {
        return player.getAttribute(ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("attributeslib", "arrow_damage")));
    }
}
