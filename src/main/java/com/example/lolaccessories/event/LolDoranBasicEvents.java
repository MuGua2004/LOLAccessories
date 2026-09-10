package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.entity.EchoOrbEntity;
import com.example.lolaccessories.init.ModMobEffects;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.entity.projectile.Projectile;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.EventPriority;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/**
 * 多兰基础系被动：
 *
 * <p>1. <b>帮助之手</b>（多兰盾/戒/盔、女神之泪共用，<b>唯一被动</b>）——佩戴者用近战或
 * 物理弹射物攻击「生命值低于阈值」的目标时，额外 +bonus_damage 点物理伤害；多名不同
 * 多兰饰品同时佩戴也只生效一次。</p>
 *
 * <p>2. <b>耐久专注</b>（多兰盾）——佩戴者自己受到伤害后的 8 秒内自然生命恢复翻倍
 * （通过 {@link ModMobEffects#PERSEVERANCE} 向本模组自然恢复属性追加等量修正器实现）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolDoranBasicEvents {

    private static final String DORAN_SHIELD = "doran_shield";

    /** 自带「帮助之手」的装备（多兰家族 + 女神之泪），唯一被动，任一佩戴即触发一次。 */
    private static final String[] HELPING_HAND_GEAR = {
            "doran_shield", "doran_ring", "doran_helmet", "tear_of_goddess"
    };

    private LolDoranBasicEvents() {
    }

    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide || event.isCanceled()) {
            return;
        }
        if (event.getAmount() <= 0.0F) {
            return;
        }
        DamageSource source = event.getSource();
        if (source == null) {
            return;
        }
        LivingEntity victim = event.getEntity();
        if (!victim.isAlive()) {
            return;
        }

        // 1. 帮助之手：加成属于攻击方造成的额外物理伤害
        Entity rawAttacker = source.getEntity();
        if (rawAttacker instanceof Player attacker
                && attacker != victim
                && attacker.isAlive()
                && isPhysicalBasicAttack(source, attacker)) {
            applyHelpingHand(event, attacker, victim);
        }

        // 2. 耐久专注：多兰盾佩戴者自己受伤时刷新翻倍窗口
        if (victim instanceof Player wearer && wearer.isAlive()) {
            applyPerseverance(wearer);
        }
    }

    /** 是否属于该玩家造成的「近战 / 物理弹射物」攻击（LoL 中“普通攻击”范畴），法术类一律不算。 */
    static boolean isPhysicalBasicAttack(DamageSource source, Player attacker) {
        boolean magic = IronsCompat.isIronSpellDamage(source)
                || source.getDirectEntity() instanceof EchoOrbEntity;
        if (magic) {
            return false;
        }
        Entity direct = source.getDirectEntity();
        if (direct == attacker) {
            return true; // 近战
        }
        if (direct instanceof Projectile projectile) {
            Entity owner = projectile.getOwner();
            return owner != null && owner.getUUID().equals(attacker.getUUID());
        }
        return false;
    }

    private static void applyHelpingHand(LivingHurtEvent event, Player attacker, LivingEntity victim) {
        GearConfig.OnHitEffect helping = findHelpingHandEffect(attacker);
        if (helping == null || !helping.enabled) {
            return;
        }
        double threshold = helping.health_threshold > 0.0D ? helping.health_threshold : 100.0D;
        if (victim.getHealth() >= threshold) {
            return;
        }
        double bonus = Math.max(0.0D, helping.bonus_damage);
        if (bonus <= 0.0D) {
            return;
        }
        event.setAmount(event.getAmount() + (float) bonus);
        LOLAccessories.LOGGER.debug("[帮助之手] {} 对生命值 {} 的 {} 附加 {} 点物理伤害",
                attacker.getName().getString(),
                String.format(java.util.Locale.ROOT, "%.1f", victim.getHealth()),
                victim.getName().getString(), bonus);
    }

    /** 遍历多兰家族，任一佩戴者身上带启用中的帮助之手即取其配置参数（只取第一件）。 */
    private static GearConfig.OnHitEffect findHelpingHandEffect(Player player) {
        for (String gearId : HELPING_HAND_GEAR) {
            if (CuriosGearWear.isWearing(player, gearId)) {
                GearConfig config = GearConfigManager.get(gearId);
                GearConfig.OnHitEffect effect = config.findEffect("helping_hand").orElse(null);
                if (effect != null && effect.enabled) {
                    return effect;
                }
            }
        }
        return null;
    }

    /** 耐久专注：受伤后 duration_seconds 内自然恢复翻倍（效果每次受伤都刷新时长）。 */
    private static void applyPerseverance(Player wearer) {
        if (!CuriosGearWear.isWearing(wearer, DORAN_SHIELD)) {
            return;
        }
        GearConfig config = GearConfigManager.get(DORAN_SHIELD);
        GearConfig.OnHitEffect perseverance = config.findEffect("perseverance").orElse(null);
        if (perseverance == null || !perseverance.enabled || perseverance.amount <= 0.0D) {
            return;
        }
        int duration = Math.round((float) (perseverance.duration_seconds * 20.0F));
        if (duration <= 0) {
            return;
        }
        wearer.removeEffect(ModMobEffects.PERSEVERANCE.get());
        wearer.addEffect(new MobEffectInstance(ModMobEffects.PERSEVERANCE.get(),
                duration, 0, false, false, false));
        LOLAccessories.LOGGER.debug("[耐久专注] {} 受到伤害，自然恢复翻倍 {} 秒",
                wearer.getName().getString(), perseverance.duration_seconds);
    }
}
