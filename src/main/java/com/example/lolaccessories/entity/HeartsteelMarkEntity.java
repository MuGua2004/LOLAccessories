package com.example.lolaccessories.entity;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.syncher.EntityDataAccessor;
import net.minecraft.network.syncher.EntityDataSerializers;
import net.minecraft.network.syncher.SynchedEntityData;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.sounds.SoundEvents;
import net.minecraft.sounds.SoundSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;

import java.util.UUID;

/**
 * 心之钢·庞然吞食印记（视觉实体）：三圈法阵悬浮在目标头顶、逐层扩展成熟（3 秒），
 * 平放旋转、半径随目标体型（碰撞箱宽度）缩放。只有触发者能看见（服务端向其他玩家
 * 发隐藏包，见 HeartsteelEvents）。印记不击碎就一直保持（目标死亡自动消散）。
 */
public class HeartsteelMarkEntity extends Entity {

    /** 成熟所需 tick（3 秒）。 */
    public static final int MATURE_TICKS = 60;
    /** 印记法阵半径（同步到客户端，随目标体型变化）。 */
    private static final EntityDataAccessor<Float> DATA_RADIUS =
            SynchedEntityData.defineId(HeartsteelMarkEntity.class, EntityDataSerializers.FLOAT);
    /** 半径系数：法阵直径 ≈ 目标碰撞箱宽度的 1.5 倍（最小 0.6 / 最大 2.4 格）。 */
    private static final float RADIUS_FACTOR = 0.75F;
    private static final float RADIUS_MIN = 0.3F;
    private static final float RADIUS_MAX = 1.2F;

    /** 被标记目标。 */
    private UUID targetId;
    /** 成熟提示是否已播放。 */
    private boolean matureFxDone;

    public HeartsteelMarkEntity(EntityType<? extends HeartsteelMarkEntity> type, Level level) {
        super(type, level);
        this.noPhysics = true;
    }

    public void setTargetId(UUID targetId) {
        this.targetId = targetId;
    }

    public UUID getTargetId() {
        return targetId;
    }

    /** 是否已成熟（3 秒）。 */
    public boolean isMatured() {
        return this.tickCount >= MATURE_TICKS;
    }

    @Override
    public void tick() {
        super.tick();
        if (this.level().isClientSide) {
            return;
        }
        LivingEntity target = resolveTarget();
        if (target == null) {
            this.discard();
            return;
        }
        // 悬浮在目标头顶，法阵半径适配体型
        this.moveTo(target.getX(), target.getY() + target.getBbHeight() + 0.15D,
                target.getZ(), target.getYRot(), 0.0F);
        float radius = Mth_clamp(target.getBbWidth() * RADIUS_FACTOR, RADIUS_MIN, RADIUS_MAX);
        if (Math.abs(this.entityData.get(DATA_RADIUS) - radius) > 0.01F) {
            this.entityData.set(DATA_RADIUS, radius);
        }
        // 成熟瞬间：提示音 + 粒子
        if (!this.matureFxDone && this.isMatured()) {
            this.matureFxDone = true;
            if (this.level() instanceof ServerLevel level) {
                level.sendParticles(ParticleTypes.END_ROD,
                        target.getX(), target.getEyeY() + 0.5D, target.getZ(), 8, 0.2D, 0.2D, 0.2D, 0.02D);
                level.playSound(null, target.blockPosition(),
                        SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.7F, 0.5F);
            }
        }
    }

    private LivingEntity resolveTarget() {
        if (this.targetId == null || !(this.level() instanceof ServerLevel level)) {
            return null;
        }
        if (level.getEntity(this.targetId) instanceof LivingEntity living && living.isAlive()) {
            return living;
        }
        return null;
    }

    private static float Mth_clamp(float v, float min, float max) {
        return v < min ? min : Math.min(v, max);
    }

    public float getMarkRadius() {
        return this.entityData.get(DATA_RADIUS);
    }

    @Override
    protected void defineSynchedData() {
        this.entityData.define(DATA_RADIUS, 0.5F);
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        this.targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (this.targetId != null) {
            tag.putUUID("Target", this.targetId);
        }
    }

    /** 不存盘：印记跟随战斗窗口，重进世界自然消失（孤儿清理兜底见 HeartsteelEvents）。 */
    @Override
    public boolean shouldBeSaved() {
        return false;
    }

    @Override
    public boolean isPickable() {
        return false;
    }

    @Override
    public boolean isPushable() {
        return false;
    }

    @Override
    public boolean shouldRenderAtSqrDistance(double distance) {
        return distance < 64.0D * 64.0D;
    }

    static {
        // 供日志/调试识别
        LOLAccessories.LOGGER.debug("HeartsteelMarkEntity loaded");
    }
}
