package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.entity.ai.attributes.Attribute;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.effect.MobEffectCategory;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * 魔王之心·「我是大魔王」（demon_king）——装备全部属性按携带的诅咒/增益动态乘区。
 *
 * <p><b>规则（用户口径）：</b>每拥有 1 个诅咒附魔（原版绑定/消失诅咒等 isCurse 红色诅咒）
 * 或 1 个负面药水效果 → 装备全部数值 +10%（加算累加）；每拥有 1 个正面附魔或 1 个正面
 * 药水效果 → 全部数值 -15%（加算累加，与提升直接相加抵消）。最终系数
 * {@code mult = 1 + 0.10×诅咒 - 0.15×增益}，可能低于 0（此时属性被完全削没）。</p>
 *
 * <p><b>实现与性能（用户强调）：</b>每 20 tick（1 秒）扫描一次；先做轻量计数缓存——
 * 诅咒/增益计数与上次相同则直接返回，零属性写入。计数变化时才对装备配置里每条
 * 已解析属性挂/更新一个 MULTIPLY_TOTAL 瞬态修正器（固定 UUID，值 = mult-1；mult ≤ 0
 * 时移除修正器）。扫描范围：盔甲 + 主副手 + Curios 全部槽位，每件物品只遍历其附魔表
 * （< 20 项）与玩家药水列表（< 16 项），无任何世界查询，开销可忽略。</p>
 *
 * <p>乘区语义：MULTIPLY_TOTAL 作用于「基础值 + 全部加成」——对以 ADDITION 注入的装备
 * 属性而言，视觉上即「装备数值提高/降低 N%」（与基础值一同被乘，属可接受的近似）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolDemonHeartEvents {

    public static final String GEAR_DEMON_HEART = "demon_heart";
    private static final String EFFECT_ID = "demon_king";

    /** 玩家 UUID → 上次计算的 (诅咒数, 增益数)，变化才重写属性。 */
    private static final Map<UUID, long[]> LAST_COUNTS = new HashMap<>();

    private LolDemonHeartEvents() {
    }

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide
                || !(event.player instanceof ServerPlayer player) || player.isSpectator()) {
            return;
        }
        if (player.tickCount % 20 != 0) {
            return;
        }
        boolean wearing = CuriosGearWear.isWearing(player, GEAR_DEMON_HEART);
        GearConfig config = GearConfigManager.get(GEAR_DEMON_HEART);
        GearConfig.OnHitEffect effect = config == null ? null : config.findEffect(EFFECT_ID).orElse(null);
        if (!wearing || effect == null || !effect.enabled) {
            // 卸下时清掉乘区修正器
            long[] last = LAST_COUNTS.remove(player.getUUID());
            if (last != null) {
                clearAllModifiers(player, config);
            }
            return;
        }

        int[] counts = new int[2]; // [0]=诅咒数 [1]=增益数
        // 1) 药水效果（负面 +1 诅咒；正面 +1 增益）
        counts[0] += (int) player.getActiveEffects().stream()
                .filter(e -> e.getEffect().getCategory() == MobEffectCategory.HARMFUL).count();
        counts[1] += (int) player.getActiveEffects().stream()
                .filter(e -> e.getEffect().getCategory() == MobEffectCategory.BENEFICIAL).count();
        // 2) 附魔：盔甲 + 主副手 + Curios 全部槽位（含魔王之心自身可被附魔）
        for (ItemStack stack : player.getInventory().armor) {
            int[] c = countEnchants(stack);
            counts[0] += c[0];
            counts[1] += c[1];
        }
        int[] hands = countEnchants(player.getMainHandItem());
        counts[0] += hands[0];
        counts[1] += hands[1];
        hands = countEnchants(player.getOffhandItem());
        counts[0] += hands[0];
        counts[1] += hands[1];
        var curiosOptional = top.theillusivec4.curios.api.CuriosApi.getCuriosInventory(player).resolve();
        if (curiosOptional.isPresent()) {
            for (var stacksHandler : curiosOptional.get().getCurios().values()) {
                var inv = stacksHandler.getStacks();
                for (int i = 0; i < inv.getSlots(); i++) {
                    int[] c = countEnchants(inv.getStackInSlot(i));
                    counts[0] += c[0];
                    counts[1] += c[1];
                }
            }
        }
        int curses = counts[0];
        int buffs = counts[1];

        // 轻量缓存：计数未变化则跳过（零属性写入）
        long[] last = LAST_COUNTS.get(player.getUUID());
        if (last != null && last[0] == curses && last[1] == buffs) {
            return;
        }
        LAST_COUNTS.put(player.getUUID(), new long[]{curses, buffs});

        double curseRatio = effect.curse_ratio > 0 ? effect.curse_ratio : 0.10D;
        double buffRatio = effect.buff_ratio > 0 ? effect.buff_ratio : 0.15D;
        double mult = 1.0D + curseRatio * curses - buffRatio * buffs;
        applyMultipliers(player, config, mult);
        if (curses > 0 || buffs > 0) {
            LOLAccessories.LOGGER.debug("[大魔王] {} 诅咒 {} / 增益 {} → 属性系数 {}",
                    player.getName().getString(), curses, buffs, String.format("%.2f", mult));
        }
    }

    /** 统计一件物品上的 (诅咒附魔数, 正面附魔数)。 */
    private static int[] countEnchants(ItemStack stack) {
        int curse = 0;
        int normal = 0;
        if (stack == null || stack.isEmpty() || !stack.isEnchanted()) {
            return new int[]{0, 0};
        }
        for (var entry : stack.getAllEnchantments().entrySet()) {
            if (entry.getKey().isCurse()) {
                curse++;
            } else {
                normal++;
            }
        }
        return new int[]{curse, normal};
    }

    /** 对装备配置里每条已解析属性挂/更新/移除 MULTIPLY_TOTAL 乘区修正器。 */
    private static void applyMultipliers(ServerPlayer player, GearConfig config, double mult) {
        if (config == null) {
            return;
        }
        double add = mult - 1.0D;
        for (GearConfig.Attr attr : config.attributes) {
            if (!attr.resolved()) {
                continue;
            }
            Attribute attribute = attr.getAttribute();
            AttributeInstance instance = player.getAttribute(attribute);
            if (instance == null) {
                continue;
            }
            UUID uuid = UUID.nameUUIDFromBytes(
                    ("lolaccessories:demon_king:" + config.gear_id + ":" + attr.id)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            AttributeModifier existing = instance.getModifier(uuid);
            if (add <= -1.0D) {
                // 系数 ≤ 0：属性归零语义 → 移除乘区、把乘区表现为 -100%（MULTIPLY_TOTAL -1）
                if (existing == null || existing.getAmount() != -1.0D) {
                    instance.removeModifier(uuid);
                    instance.addTransientModifier(new AttributeModifier(uuid,
                            "lolaccessories:demon_king", -1.0D, AttributeModifier.Operation.MULTIPLY_TOTAL));
                }
                continue;
            }
            if (existing != null && existing.getAmount() == add) {
                continue;
            }
            instance.removeModifier(uuid);
            if (add != 0.0D) {
                instance.addTransientModifier(new AttributeModifier(uuid,
                        "lolaccessories:demon_king", add, AttributeModifier.Operation.MULTIPLY_TOTAL));
            }
        }
    }

    /** 卸下装备：移除全部乘区修正器。 */
    private static void clearAllModifiers(ServerPlayer player, GearConfig config) {
        if (config == null) {
            return;
        }
        for (GearConfig.Attr attr : config.attributes) {
            if (!attr.resolved()) {
                continue;
            }
            AttributeInstance instance = player.getAttribute(attr.getAttribute());
            if (instance == null) {
                continue;
            }
            UUID uuid = UUID.nameUUIDFromBytes(
                    ("lolaccessories:demon_king:" + config.gear_id + ":" + attr.id)
                            .getBytes(java.nio.charset.StandardCharsets.UTF_8));
            instance.removeModifier(uuid);
        }
    }
}
