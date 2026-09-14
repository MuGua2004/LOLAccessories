package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.advancement.LolAdvancementService;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.compat.IronsCompat;
import com.example.lolaccessories.config.GearConfig;
import com.example.lolaccessories.config.GearConfigManager;
import com.example.lolaccessories.networking.FxKind;
import com.example.lolaccessories.networking.LOLNetworking;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.chat.Component;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.item.ItemEntity;
import net.minecraftforge.event.entity.living.LivingDropsEvent;
import net.minecraftforge.event.entity.living.LivingHurtEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * 翡翠城（Emerald City，第 4 件神话装备，原创）——被动「梦之乌托邦」与主动「再见桃花源」。
 *
 * <p><b>唯一被动—梦之乌托邦（qionghua）：</b>佩戴者使用法术（铁魔法口径，含本模组学派伤害）
 * 对敌人造成伤害后，为其施加一层「琼华」，使目标受到的伤害提高 {@code amount}（默认 20%），
 * 至多 {@code max_stacks}（默认 10）层。层数记录在目标实体的持久化 NBT
 * （{@code ForgeData/lolaccessories/emerald_qionghua}）里，目标死亡自然清零，无时限衰减。
 * 每次命中先按<b>已有</b>层数放大本次伤害，再叠加新层——即第一段无加成，第二段吃 1 层。</p>
 *
 * <p><b>主动—再见桃花源（farewell_paradise，冷却 30 秒）：</b>若自身 {@code radius_blocks}
 * （默认 10）格内存在叠满琼华的生物，则对其使用 {@code kill} 代码抹杀，并使其掉落的
 * 战利品翻倍（{@link LivingDropsEvent} 一次性镜像掉落物）。抹杀瞬间播放翡翠法阵
 * （{@link FxKind#EMERALD_DOOM}，地点锚定走 {@code FxSpotPacket}，法阵几何在客户端
 * {@code GearFxRenderer}）+ 粒子与音效。</p>
 *
 * <p>同帧 kill 的掉落翻倍通过 {@code DOOMED} 标记表实现（一次性消费，60 秒兜底过期）。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class LolEmeraldCityEvents {

    public static final String GEAR_EMERALD_CITY = "emerald_city";
    private static final String EFFECT_QIONGHUA = "qionghua";
    private static final String EFFECT_FAREWELL = "farewell_paradise";
    /** 琼华层数存储键（目标实体持久化 NBT 的 lolaccessories 复合标签）。 */
    private static final String NBT_ROOT = "lolaccessories";
    private static final String NBT_QIONGHUA = "emerald_qionghua";
    /** 抹杀标记的兜底过期毫秒数（掉落事件未触发的异常路径防残留）。 */
    private static final long DOOMED_TTL_MS = 60_000L;
    /** UUID → 标记时刻（该目标下次掉落翻倍，一次性）。 */
    private static final Map<UUID, Long> DOOMED = new HashMap<>();

    private LolEmeraldCityEvents() {
    }

    // ------------------------------------------------------------------ //
    // 被动：梦之乌托邦（琼华叠层 + 易伤放大）+ 成就伤害累计
    // ------------------------------------------------------------------ //

    @SubscribeEvent
    public static void onLivingHurt(LivingHurtEvent event) {
        if (event.getEntity().level().isClientSide) {
            return;
        }
        if (!(event.getSource().getEntity() instanceof ServerPlayer attacker)) {
            return;
        }
        LivingEntity victim = event.getEntity();
        double amount = event.getAmount();
        // 成就【遥远于世的幻想乡】：累计造成过 100 亿铁魔法法术伤害（与是否佩戴翡翠城无关）
        if (IronsCompat.isIronSpellDamage(event.getSource())) {
            LolAdvancementService.onSpellDamageDealt(attacker, amount);
        }
        if (!CuriosGearWear.isWearing(attacker, GEAR_EMERALD_CITY)
                || !IronsCompat.isIronSpellDamage(event.getSource())
                || !LolNewEpicPassiveEvents.isEnemyOf(attacker, victim)) {
            return;
        }
        GearConfig.OnHitEffect effect = GearConfigManager.get(GEAR_EMERALD_CITY)
                .findEffect(EFFECT_QIONGHUA).orElse(null);
        if (effect == null || !effect.enabled) {
            return;
        }
        int maxStacks = effect.max_stacks > 0 ? (int) Math.round(effect.max_stacks) : 10;
        double perStack = effect.amount > 0 ? effect.amount : 0.2D;
        int stacks = getStacks(victim);
        // 先按已有层数放大本次伤害，再叠加新层
        if (stacks > 0) {
            event.setAmount((float) (amount * (1.0D + perStack * stacks)));
        }
        if (stacks < maxStacks) {
            setStacks(victim, stacks + 1);
        }
    }

    // ------------------------------------------------------------------ //
    // 主动：再见桃花源（由 LolNewActiveSkillEvents 触发链调用）
    // ------------------------------------------------------------------ //

    /** 抹杀 10 格内叠满琼华的生物；返回抹杀数量。 */
    public static int farewellParadise(ServerPlayer player) {
        if (!(player.level() instanceof ServerLevel level)) {
            return 0;
        }
        GearConfig config = GearConfigManager.get(GEAR_EMERALD_CITY);
        GearConfig.OnHitEffect effect = config.findEffect(EFFECT_FAREWELL).orElse(null);
        if (effect == null || !effect.enabled) {
            return 0;
        }
        double radius = effect.radius_blocks > 0 ? effect.radius_blocks : 10.0D;
        int maxStacks = qionghuaMaxStacks(config);
        long now = System.currentTimeMillis();
        DOOMED.entrySet().removeIf(entry -> now - entry.getValue() > DOOMED_TTL_MS);
        int killed = 0;
        for (LivingEntity victim : LolNewEpicPassiveEvents.enemiesAround(player, player, radius)) {
            if (getStacks(victim) < maxStacks) {
                continue;
            }
            doomEffects(level, victim);
            DOOMED.put(victim.getUUID(), now);
            victim.kill();
            killed++;
        }
        if (killed > 0) {
            player.sendSystemMessage(Component.translatable(
                    "skill.lolaccessories.farewell_paradise.start", killed));
        }
        return killed;
    }

    /** 掉落翻倍：被抹杀目标的掉落物镜像一份（一次性消费标记）。 */
    @SubscribeEvent
    public static void onLivingDrops(LivingDropsEvent event) {
        Long markedAt = DOOMED.remove(event.getEntity().getUUID());
        if (markedAt == null || event.getDrops().isEmpty()) {
            return;
        }
        List<ItemEntity> extra = new ArrayList<>();
        for (ItemEntity drop : event.getDrops()) {
            var stack = drop.getItem();
            if (stack.isEmpty()) {
                continue;
            }
            extra.add(new ItemEntity(drop.level(), drop.getX(), drop.getY(), drop.getZ(), stack.copy()));
        }
        event.getDrops().addAll(extra);
        LOLAccessories.LOGGER.info("[翡翠城] {} 被再见桃花源抹杀，战利品翻倍（+{} 组）",
                event.getEntity().getName().getString(), extra.size());
    }

    // ------------------------------------------------------------------ //
    // 内部工具
    // ------------------------------------------------------------------ //

    /** 抹杀特效：翡翠法阵（地点锚定）+ 粒子与音效。 */
    private static void doomEffects(ServerLevel level, LivingEntity victim) {
        LOLNetworking.sendSpotFx(level, FxKind.EMERALD_DOOM,
                victim.getX(), victim.getY(), victim.getZ(), 30, 2.6F);
        double x = victim.getX();
        double y = victim.getY() + victim.getBbHeight() * 0.5D;
        double z = victim.getZ();
        level.sendParticles(ParticleTypes.DRAGON_BREATH, x, y, z, 40, 0.5D, 0.8D, 0.5D, 0.02D);
        level.sendParticles(ParticleTypes.END_ROD, x, y + 0.5D, z, 24, 0.4D, 1.0D, 0.4D, 0.05D);
        level.sendParticles(ParticleTypes.SOUL_FIRE_FLAME, x, y, z, 20, 0.45D, 0.7D, 0.45D, 0.01D);
        level.sendParticles(ParticleTypes.SCULK_SOUL, x, y + 0.8D, z, 16, 0.4D, 0.8D, 0.4D, 0.3D);
        level.playSound(null, victim.blockPosition(),
                SoundEvents.TOTEM_USE, SoundSource.PLAYERS, 0.9F, 0.6F);
        level.playSound(null, victim.blockPosition(),
                SoundEvents.EVOKER_CAST_SPELL, SoundSource.PLAYERS, 1.0F, 0.55F);
        level.playSound(null, victim.blockPosition(),
                SoundEvents.BEACON_ACTIVATE, SoundSource.PLAYERS, 0.7F, 1.6F);
    }

    private static int qionghuaMaxStacks(GearConfig config) {
        GearConfig.OnHitEffect effect = config.findEffect(EFFECT_QIONGHUA).orElse(null);
        return effect != null && effect.max_stacks > 0 ? (int) Math.round(effect.max_stacks) : 10;
    }

    /** 读取目标身上的琼华层数（默认 0）。 */
    private static int getStacks(LivingEntity entity) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(NBT_ROOT, CompoundTag.TAG_COMPOUND)) {
            return 0;
        }
        return root.getCompound(NBT_ROOT).getInt(NBT_QIONGHUA);
    }

    /** 写入目标身上的琼华层数。 */
    private static void setStacks(LivingEntity entity, int stacks) {
        CompoundTag root = entity.getPersistentData();
        if (!root.contains(NBT_ROOT, CompoundTag.TAG_COMPOUND)) {
            root.put(NBT_ROOT, new CompoundTag());
        }
        root.getCompound(NBT_ROOT).putInt(NBT_QIONGHUA, Math.max(0, stacks));
    }
}
