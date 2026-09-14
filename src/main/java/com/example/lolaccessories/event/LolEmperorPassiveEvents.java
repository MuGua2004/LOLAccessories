package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.init.ModAttributes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.ai.attributes.AttributeInstance;
import net.minecraft.world.entity.ai.attributes.AttributeModifier;
import net.minecraft.world.entity.ai.attributes.AttributeModifier.Operation;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Monster;
import net.minecraftforge.event.TickEvent;
import net.minecraftforge.event.entity.living.LivingDeathEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.event.TickEvent.PlayerTickEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.UUID;

/**
 * 天帝（Heavenly Emperor）唯一被动「我什么都不缺了（I Want For Nothing）」三段成长。
 *
 * <ul>
 * <li>每释放一次法术，获得相当于消耗法力 1% 的法术强度；</li>
 * <li>法术每次命中，获得相当于 0.1% 最大生命值的最大法力值；</li>
 * <li>每击杀一个敌对生物，获得相当于 1% 法强的最大生命值。</li>
 * </ul>
 *
 * <p>累计规则（2026-09 用户口径）：累计值<b>绑定玩家永久保存</b>（存玩家持久数据，
 * 死亡、换存档读档均不丢失），但<b>只有佩戴该饰品时才激活</b>——激活即把累计值以
 * 瞬时（transient）修正器挂到对应属性上，摘下即失效，累计本身不清零。</p>
 *
 * <p>实现口径：权威累计值全部存玩家持久数据（{@code lolaccessories_tiandi_*}），
 * 属性修正器只是投影并幂等重挂（每秒 diff 校准，防任何外部清理导致丢失）。
 * 法术施放由铁魔法联动层（IronsAbilityHasteEvents）在 SpellOnCastEvent 时回调
 * {@link #onIronSpellCast}；「法术命中」按本模组口径 = 魔法伤害命中
 * （原版魔法 + Iron's Spells 法术伤害，见 {@code LolNewGearPassiveEvents#isMagicDamage}）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolEmperorPassiveEvents {

    private static final String GEAR = "heavenly_emperor";

    private static final UUID TIANDI_AP_UUID =
            UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-0000000000a1");
    private static final UUID TIANDI_MANA_UUID =
            UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-0000000000a2");
    private static final UUID TIANDI_HEALTH_UUID =
            UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-0000000000a3");

    /** 累计值（权威值，玩家持久数据，随存档保存）。 */
    /** 隐身衣（皇帝的新衣）：99% 物理/魔法伤害减免投影修饰器 UUID。 */
    private static final UUID CLOAK_PHYS_UUID =
            UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-0000000000b2");
    private static final UUID CLOAK_MAGIC_UUID =
            UUID.fromString("b2c3d4e5-7777-4a5b-8c6d-0000000000b3");

    /** 隐身衣 gear_id / 效果 id。 */
    private static final String GEAR_CLOAK = "emperors_new_clothes";
    private static final String EFFECT_NEW_CLOTHES = "new_clothes";

    private static final String TAG_AP = "lolaccessories_tiandi_ap";
    private static final String TAG_MANA = "lolaccessories_tiandi_mana";
    private static final String TAG_HEALTH = "lolaccessories_tiandi_health";
    /** 瑞天帝：生命上限 &gt; 一万时的法力消耗累计。 */
    private static final String TAG_MANA_SPENT = "lolaccessories_tiandi_mana_spent";

    /** 瑞天帝：生命值上限门槛（大于该值时消耗的法力才计数）。 */
    private static final double RUI_HEALTH_GATE = 10000.0D;
    /** 瑞天帝：法力消耗累计目标。 */
    private static final double RUI_MANA_GOAL = 100000.0D;

    private LolEmperorPassiveEvents() {
    }

    /** 施法回调：由 ISS 联动层在 SpellOnCastEvent 时调用（manaCost = 原始法力消耗）。 */
    public static void onIronSpellCast(ServerPlayer player, int manaCost) {
        if (manaCost <= 0 || !CuriosGearWear.isWearing(player, GEAR)) {
            return;
        }
        CompoundTag data = player.getPersistentData();
        // 法强累计：消耗法力的 1%
        data.putDouble(TAG_AP, data.getDouble(TAG_AP) + manaCost * 0.01D);
        sync(player, spellPowerAttr(), TIANDI_AP_UUID, TAG_AP);
        // 瑞天帝：仅生命上限 > 10000 时的法力消耗计数
        if (player.getMaxHealth() > RUI_HEALTH_GATE) {
            double total = data.getDouble(TAG_MANA_SPENT) + manaCost;
            data.putDouble(TAG_MANA_SPENT, total);
            LolMythicChallengeEvents.tryUnlock(player, GEAR,
                    LolMythicChallengeEvents.TYPE_MANA_SPENT_TOTAL, total);
        }
    }

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        DamageSource source = event.getSource();
        if (!(source.getEntity() instanceof ServerPlayer attacker)
                || attacker == event.getEntity()
                || !isMagicDamage(source)
                || !CuriosGearWear.isWearing(attacker, GEAR)) {
            return;
        }
        // 法力累计：每次法术命中 +0.1% 玩家最大生命值
        CompoundTag data = attacker.getPersistentData();
        data.putDouble(TAG_MANA, data.getDouble(TAG_MANA) + attacker.getMaxHealth() * 0.001D);
        sync(attacker, ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("irons_spellbooks", "max_mana")), TIANDI_MANA_UUID, TAG_MANA);
    }

    @SubscribeEvent
    public static void onLivingDeath(LivingDeathEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer killer)
                || !(event.getEntity() instanceof Monster)
                || !CuriosGearWear.isWearing(killer, GEAR)) {
            return;
        }
        // 生命累计：每击杀一个敌对生物 +1% 玩家法术强度
        CompoundTag data = killer.getPersistentData();
        data.putDouble(TAG_HEALTH, data.getDouble(TAG_HEALTH) + spellPowerOf(killer) * 0.01D);
        sync(killer, Attributes.MAX_HEALTH, TIANDI_HEALTH_UUID, TAG_HEALTH);
    }

    /** 佩戴激活/摘下失效：每秒 diff 校准三个投影修正器（累计值本身不受影响）。 */
    @SubscribeEvent
    public static void onPlayerTick(PlayerTickEvent event) {
        if (event.phase != TickEvent.Phase.END
                || !(event.player instanceof ServerPlayer player) || player.level().isClientSide
                || player.tickCount % 20 != 0) {
            return;
        }
        boolean wearing = CuriosGearWear.isWearing(player, GEAR);
        sync(player, spellPowerAttr(), TIANDI_AP_UUID, wearing ? TAG_AP : null);
        sync(player, ForgeRegistries.ATTRIBUTES.getValue(
                new ResourceLocation("irons_spellbooks", "max_mana")),
                TIANDI_MANA_UUID, wearing ? TAG_MANA : null);
        sync(player, Attributes.MAX_HEALTH, TIANDI_HEALTH_UUID, wearing ? TAG_HEALTH : null);

        // 隐身衣·皇帝的新衣：佩戴时激活 99% 物理/魔法伤害减免（摘下失效，数值来自配置）
        boolean cloak = CuriosGearWear.isWearing(player, GEAR_CLOAK);
        double reduction = 0.0D;
        if (cloak) {
            GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_CLOAK)
                    .findEffect(EFFECT_NEW_CLOTHES).orElse(null);
            reduction = effect != null && effect.amount > 0 ? effect.amount : 0.99D;
        }
        syncValue(player, ModAttributes.LOL_PHYSICAL_DAMAGE_REDUCTION.get(),
                CLOAK_PHYS_UUID, cloak ? reduction : 0.0D);
        syncValue(player, ModAttributes.LOL_MAGIC_DAMAGE_REDUCTION.get(),
                CLOAK_MAGIC_UUID, cloak ? reduction : 0.0D);
    }

    /**
     * 投影同步：tag 非空且修正器值已一致 → 跳过；否则移除后按累计值重挂（transient，
     * 权威值在持久数据里，任何外部清理都会在下一次校准时恢复）。
     */
    private static void sync(ServerPlayer player, net.minecraft.world.entity.ai.attributes.Attribute attr,
                             UUID uuid, String tag) {
        if (attr == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        double value = tag == null ? 0.0D : Math.max(0.0D, player.getPersistentData().getDouble(tag));
        AttributeModifier existing = inst.getModifier(uuid);
        if (existing != null && existing.getAmount() == value) {
            return;
        }
        inst.removeModifier(uuid);
        if (value > 0.0D) {
            inst.addTransientModifier(new AttributeModifier(uuid, "lolaccessories_tiandi",
                    value, Operation.ADDITION));
        }
    }

    /** 固定值投影：value &gt; 0 时幂等挂 transient 修正器，否则移除。 */
    private static void syncValue(ServerPlayer player,
                                  net.minecraft.world.entity.ai.attributes.Attribute attr,
                                  UUID uuid, double value) {
        if (attr == null) {
            return;
        }
        AttributeInstance inst = player.getAttribute(attr);
        if (inst == null) {
            return;
        }
        AttributeModifier existing = inst.getModifier(uuid);
        if (existing != null && existing.getAmount() == value) {
            return;
        }
        inst.removeModifier(uuid);
        if (value > 0.0D) {
            inst.addTransientModifier(new AttributeModifier(uuid, "lolaccessories_cloak",
                    value, Operation.ADDITION));
        }
    }

    private static net.minecraft.world.entity.ai.attributes.Attribute spellPowerAttr() {
        return ForgeRegistries.ATTRIBUTES.getValue(new ResourceLocation("irons_spellbooks", "spell_power"));
    }

    private static double spellPowerOf(ServerPlayer player) {
        net.minecraft.world.entity.ai.attributes.Attribute attr = spellPowerAttr();
        if (attr == null) {
            return 0.0D;
        }
        AttributeInstance inst = player.getAttribute(attr);
        return inst == null ? 0.0D : inst.getValue();
    }

    private static boolean isMagicDamage(DamageSource source) {
        return source.is(net.minecraft.world.damagesource.DamageTypes.MAGIC)
                || source.is(net.minecraft.world.damagesource.DamageTypes.INDIRECT_MAGIC)
                || com.example.lolaccessories.compat.IronsCompat.isIronSpellDamage(source);
    }
}
