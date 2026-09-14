package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.effect.MobEffect;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.effect.MobEffects;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDamageEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 命运十面骰（Fate's Die，第 3 件神话装备）——主动技「嘲弄命运」。
 *
 * <p>投掷一颗 10 面骰，获得持续 30 秒的随机效果（10 种概率均等），掷骰结果在聊天栏
 * 通知；冷却 180 秒。十种效果：</p>
 *
 * <ol>
 *   <li>生命虹吸：造成的伤害转化为对自身的治疗（按最终伤害等量回血）；</li>
 *   <li>命运眷顾：传说暴击率 +666%；</li>
 *   <li>命运嘲弄：传说暴击伤害 -666%；</li>
 *   <li>疾风骤雨：移动速度 +1000%（MULTIPLY_TOTAL 独立乘区）；</li>
 *   <li>大幸运：幸运值 +999；</li>
 *   <li>天神下凡：造成的伤害提高 10000%（LivingDamageEvent 最末端的绝对独立乘区，×101）；</li>
 *   <li>死亡凝视：立即受到相当于自身最大生命 999% 的虚空伤害（原版 fellOutOfWorld 语义）；</li>
 *   <li>混沌药剂：随机获得 17 种药水效果（正负混合，各 30 秒）；</li>
 *   <li>消失诅咒：全身已穿戴盔甲被施加消失诅咒；</li>
 *   <li>命运馈赠：持续期间每 3 秒获得一件随机物品（按注册表物品 id 随机 give）。</li>
 * </ol>
 *
 * <p>增益（1/2/4/5/6/8/10）与减益（3/7/9）分别有金色/暗紫粒子特效；属性类效果
 * （2/3/4/5）挂 transient modifier，到期由 tick 统一移除。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolFateDiceEvents {

    public static final String GEAR_FATE_DIE = "fate_die";
    /** 嘲弄命运持续时间（毫秒）。 */
    private static final long DURATION_MS = 30000L;
    /** 效果 10 的掉落间隔（毫秒）。 */
    private static final long DROP_INTERVAL_MS = 3000L;

    /** 混沌药水池（正负混合，随机抽 17 种）。 */
    private static final List<MobEffect> CHAOS_POOL = List.of(
            MobEffects.MOVEMENT_SPEED, MobEffects.MOVEMENT_SLOWDOWN, MobEffects.DIG_SPEED,
            MobEffects.DIG_SLOWDOWN, MobEffects.DAMAGE_BOOST, MobEffects.JUMP,
            MobEffects.REGENERATION, MobEffects.DAMAGE_RESISTANCE, MobEffects.FIRE_RESISTANCE,
            MobEffects.WATER_BREATHING, MobEffects.INVISIBILITY, MobEffects.BLINDNESS,
            MobEffects.NIGHT_VISION, MobEffects.WEAKNESS, MobEffects.POISON, MobEffects.WITHER,
            MobEffects.HEALTH_BOOST, MobEffects.ABSORPTION, MobEffects.GLOWING,
            MobEffects.LEVITATION, MobEffects.LUCK, MobEffects.UNLUCK, MobEffects.DARKNESS,
            MobEffects.CONFUSION, MobEffects.HUNGER, MobEffects.SLOW_FALLING);

    /** 进行中的命运效果。 */
    private static final Map<UUID, DiceState> ACTIVE = new HashMap<>();

    /** 一次掷骰的持续状态。 */
    private static final class DiceState {
        final int result;
        final long expireMs;
        long nextDropMs;
        /** 到期需要移除的属性 modifier（uuid → 属性）。 */
        final Map<UUID, net.minecraft.world.entity.ai.attributes.Attribute> modifiers = new HashMap<>();

        DiceState(int result, long expireMs) {
            this.result = result;
            this.expireMs = expireMs;
            this.nextDropMs = 0L;
        }
    }

    private LolFateDiceEvents() {
    }

    // ------------------------------------------------------------------ //
    // 掷骰（由主动技系统调用）
    // ------------------------------------------------------------------ //

    /** 掷骰并应用效果；返回 1（供主动技回执统计）。 */
    public static int rollFate(ServerPlayer player) {
        int roll = player.getRandom().nextInt(10) + 1;
        long now = System.currentTimeMillis();
        DiceState state = new DiceState(roll, now + DURATION_MS);
        clearModifiers(player, state);
        ACTIVE.put(player.getUUID(), state);
        applyResult(player, state, now);

        // 聊天栏通知结果
        player.sendSystemMessage(Component.translatable("msg.lolaccessories.mock_fate.result." + roll));
        // 结果特效：1/3/7/9 为减益
        boolean blessing = roll != 1 && roll != 3 && roll != 7 && roll != 9;
        if (player.level() instanceof ServerLevel level) {
            if (blessing) {
                level.sendParticles(ParticleTypes.TOTEM_OF_UNDYING,
                        player.getX(), player.getY() + player.getBbHeight() * 0.6D, player.getZ(),
                        40, 0.4D, 0.6D, 0.4D, 0.08D);
                level.sendParticles(ParticleTypes.END_ROD,
                        player.getX(), player.getY() + 1.0D, player.getZ(), 12, 0.5D, 0.8D, 0.5D, 0.02D);
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.8F, 1.4F);
            } else {
                level.sendParticles(ParticleTypes.LARGE_SMOKE,
                        player.getX(), player.getY() + 1.0D, player.getZ(), 30, 0.4D, 0.8D, 0.4D, 0.02D);
                level.sendParticles(ParticleTypes.SOUL,
                        player.getX(), player.getY() + 1.0D, player.getZ(), 16, 0.5D, 0.8D, 0.5D, 0.01D);
                player.level().playSound(null, player.blockPosition(),
                        SoundEvents.WITHER_HURT, SoundSource.PLAYERS, 0.6F, 1.2F);
            }
        }
        LOLAccessories.LOGGER.info("[命运骰] {} 掷出 {} 点", player.getName().getString(), roll);
        return 1;
    }

    /** 应用掷骰结果。 */
    private static void applyResult(ServerPlayer player, DiceState state, long now) {
        switch (state.result) {
            case 1, 6 -> {
                // 伤害转治疗 / 伤害提高：在 LivingDamageEvent 结算
            }
            case 2 -> addMod(player, state, "crit_chance", ModAttributes.LOL_CRIT_CHANCE.get(),
                    6.66D, AttributeModifier.Operation.ADDITION);
            case 3 -> addMod(player, state, "crit_damage", ModAttributes.LOL_CRIT_DAMAGE.get(),
                    -6.66D, AttributeModifier.Operation.ADDITION);
            case 4 -> addMod(player, state, "move_speed", Attributes.MOVEMENT_SPEED,
                    10.0D, AttributeModifier.Operation.MULTIPLY_TOTAL);
            case 5 -> addMod(player, state, "luck", Attributes.LUCK,
                    999.0D, AttributeModifier.Operation.ADDITION);
            case 7 -> {
                // 死亡凝视：999% 最大生命的虚空伤害（无视减免，原版虚空语义）
                player.hurt(player.damageSources().fellOutOfWorld(),
                        (float) (player.getMaxHealth() * 9.99D));
            }
            case 8 -> {
                // 混沌药剂：随机 17 种药水效果
                List<MobEffect> pool = new ArrayList<>(CHAOS_POOL);
                java.util.Collections.shuffle(pool);
                for (int i = 0; i < 17 && i < pool.size(); i++) {
                    MobEffect effect = pool.get(i);
                    int dur = effect.isInstantenous() ? 1 : (int) (DURATION_MS / 50L);
                    player.addEffect(new MobEffectInstance(effect, dur,
                            player.getRandom().nextInt(2), true, true));
                }
            }
            case 9 -> {
                // 消失诅咒：全身已穿戴盔甲
                for (var slot : new net.minecraft.world.entity.EquipmentSlot[]{
                        net.minecraft.world.entity.EquipmentSlot.HEAD,
                        net.minecraft.world.entity.EquipmentSlot.CHEST,
                        net.minecraft.world.entity.EquipmentSlot.LEGS,
                        net.minecraft.world.entity.EquipmentSlot.FEET}) {
                    ItemStack armor = player.getItemBySlot(slot);
                    if (!armor.isEmpty()) {
                        armor.enchant(net.minecraft.world.item.enchantment.Enchantments.VANISHING_CURSE, 1);
                    }
                }
            }
            case 10 -> state.nextDropMs = now + DROP_INTERVAL_MS;
            default -> {
            }
        }
    }

    /** 挂 transient modifier 并记录（到期统一移除）。 */
    private static void addMod(ServerPlayer player, DiceState state, String tag,
                               net.minecraft.world.entity.ai.attributes.Attribute attribute,
                               double amount, AttributeModifier.Operation op) {
        AttributeInstance attr = player.getAttribute(attribute);
        if (attr == null) {
            return;
        }
        UUID id = UUID.nameUUIDFromBytes(
                (player.getUUID() + ":fate_die:" + tag).getBytes());
        state.modifiers.put(id, attribute);
        if (attr.getModifier(id) != null) {
            attr.removeModifier(id);
        }
        attr.addTransientModifier(new AttributeModifier(id, "fate_die_" + tag, amount, op));
    }

    private static void clearModifiers(ServerPlayer player, DiceState state) {
        for (Map.Entry<UUID, net.minecraft.world.entity.ai.attributes.Attribute> e : state.modifiers.entrySet()) {
            AttributeInstance attr = player.getAttribute(e.getValue());
            if (attr != null && attr.getModifier(e.getKey()) != null) {
                attr.removeModifier(e.getKey());
            }
        }
        state.modifiers.clear();
    }

    // ------------------------------------------------------------------ //
    // 每 tick：到期清理 / 环绕特效 / 命运馈赠
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onPlayerTick(TickEvent.PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END || event.player.level().isClientSide) {
            return;
        }
        if (!(event.player instanceof ServerPlayer player)) {
            return;
        }
        DiceState state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        long now = System.currentTimeMillis();
        if (now >= state.expireMs) {
            clearModifiers(player, state);
            ACTIVE.remove(player.getUUID());
            return;
        }
        boolean blessing = state.result != 1 && state.result != 3
                && state.result != 7 && state.result != 9;
        if (player.level() instanceof ServerLevel level) {
            // 环绕粒子（每秒）
            if (player.tickCount % 20 == 0) {
                double r = player.getBbWidth() + 0.4D;
                for (int i = 0; i < 6; i++) {
                    double a = Math.PI * 2 * i / 6 + player.tickCount * 0.08D;
                    level.sendParticles(blessing ? ParticleTypes.END_ROD : ParticleTypes.SOUL_FIRE_FLAME,
                            player.getX() + Math.cos(a) * r, player.getY() + 1.0D,
                            player.getZ() + Math.sin(a) * r, 1, 0, 0.05D, 0, 0.0D);
                }
            }
            // 命运馈赠：每 3 秒一件随机物品
            if (state.result == 10 && now >= state.nextDropMs) {
                state.nextDropMs = now + DROP_INTERVAL_MS;
                var items = List.copyOf(ForgeRegistries.ITEMS.getValues());
                var item = items.get(player.getRandom().nextInt(items.size()));
                if (item != net.minecraft.world.item.Items.AIR) {
                    player.getInventory().placeItemBackInInventory(new ItemStack(item));
                    level.sendParticles(ParticleTypes.HAPPY_VILLAGER,
                            player.getX(), player.getY() + 1.2D, player.getZ(), 6, 0.3D, 0.4D, 0.3D, 0.0D);
                }
            }
        }
    }

    // ------------------------------------------------------------------ //
    // 伤害结算（效果 1 / 效果 6）
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingDamage(LivingDamageEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer player)) {
            return;
        }
        DiceState state = ACTIVE.get(player.getUUID());
        if (state == null) {
            return;
        }
        if (state.result == 1) {
            // 生命虹吸：造成的伤害转化为对目标（被伤害的怪物）的治疗
            event.getEntity().heal(event.getAmount());
        } else if (state.result == 6) {
            // 天神下凡：绝对最终独立乘区 ×101（+10000%）
            event.setAmount(event.getAmount() * 101.0F);
        }
    }

    /** 主动技入口：校验佩戴命运骰并掷骰。返回命中数（1）。 */
    public static int rollFateChecked(ServerPlayer player) {
        boolean[] wearing = {false};
        CuriosGearWear.forEachEquippedGear(player, gear -> {
            if (GEAR_FATE_DIE.equals(gear.getGearId())) {
                wearing[0] = true;
            }
        });
        if (!wearing[0]) {
            return 0;
        }
        return rollFate(player);
    }

    /** 供 GearItem tooltip / 主动技读取持续时间与冷却。 */
    public static GearConfig.OnHitEffect effect() {
        GearConfig cfg = GearConfigManager.get(GEAR_FATE_DIE);
        return cfg != null ? cfg.findEffect("mock_fate").orElse(null) : null;
    }
}
