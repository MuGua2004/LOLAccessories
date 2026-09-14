package com.example.lolaccessories.entity;

import com.example.lolaccessories.init.ModEntityTypes;
import net.minecraft.core.particles.ParticleTypes;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.protocol.Packet;
import net.minecraft.network.protocol.game.ClientGamePacketListener;
import net.minecraft.server.level.ServerLevel;
import com.example.lolaccessories.compat.IronsSpellDamage;
import net.minecraft.world.damagesource.DamageSource;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.level.Level;
import net.minecraft.world.phys.Vec3;
import net.minecraftforge.entity.IEntityAdditionalSpawnData;
import net.minecraftforge.network.NetworkHooks;

import javax.annotation.Nullable;
import java.util.List;
import java.util.UUID;

/**
 * 海克斯科技枪刃——「闪电弹球」（参考铁魔法电蓝闪电视觉）。
 *
 * <p>与卢登的回声同思路：纯服务端驱动的追踪弹，命中目标时结算魔法伤害并治疗施法者；
 * 这里不复用 {@link EchoOrbEntity} 是为了给出独立的闪电外观（电蓝/青色 + 噼啪电光粒子），
 * 不让枪刃看起来像末影紫球。客户端由 {@code LightningOrbRenderer} 画成电蓝发光球，不依赖
 * 之前出过问题的 spot 叠加管道，因此不会重复「特效不显示」。</p>
 *
 * <p>无目标（player 视线方向没有敌人）时，弹球沿视线方向直线飞出并在射程尽头无害消散，
 * 不会因找不到目标而报错；此时 {@code target} 为 null，仅造成视觉上的电光一闪。</p>
 */
public class LightningOrbEntity extends Entity implements IEntityAdditionalSpawnData {

    /** 弹体巡航速度（格/游戏刻）。 */
    private static final double SPEED = 1.25D;
    /** 每刻把当前飞行方向往「朝向目标」方向拉近的比例。 */
    private static final double STEER = 0.22D;
    /** 距目标中心多近视为命中（格）。 */
    private static final double ARRIVE_DIST = 0.9D;
    /** 飞行寿命上限（游戏刻）。 */
    private static final int MAX_AGE = 60;
    /** 无目标时向前飞行的射程（格）。 */
    private static final double NO_TARGET_REACH = 18.0D;
    /** 目标丢失后的 UUID 再查询范围（格）。 */
    private static final double TARGET_LOOKUP_RANGE = 64.0D;

    @Nullable
    private LivingEntity target;
    @Nullable
    private UUID targetId;
    @Nullable
    private LivingEntity owner;
    @Nullable
    private UUID ownerId;
    /** 命中目标后造成的魔法伤害。 */
    private float damage;
    /** 命中后治疗施法者的量（枪刃：按伤害比例回血）。 */
    private float healAmount;
    /** 铁魔法学派（配置里的短名，如 lightning / fire，结算时 resolve）。 */
    private String school = IronsSpellDamage.ENDER;
    private Vec3 motion = Vec3.ZERO;
    @Nullable
    private Vec3 lastAim;
    private int age;

    public LightningOrbEntity(EntityType<? extends LightningOrbEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /**
     * 在服务端发射一道闪电弹：从 {@code start} 出发，平滑转向 {@code target}（可空）命中后
     * 造成 {@code damage} 点魔法伤害 + 治疗施法者 {@code healAmount}。
     */
    public static void launch(ServerLevel level, LivingEntity owner, @Nullable LivingEntity target,
                              float damage, float healAmount, String school, Vec3 start, Vec3 outward) {
        LightningOrbEntity orb = new LightningOrbEntity(ModEntityTypes.LIGHTNING_ORB.get(), level);
        orb.setPos(start.x, start.y, start.z);
        orb.target = target;
        if (target != null) {
            orb.targetId = target.getUUID();
            orb.lastAim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        } else {
            // 无目标：沿视线方向飞出一段射程，作为纯粹的电光前冲
            Vec3 dir = outward.lengthSqr() < 1.0E-4D
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : outward.normalize();
            orb.lastAim = start.add(dir.scale(NO_TARGET_REACH));
        }
        orb.owner = owner;
        orb.ownerId = owner.getUUID();
        orb.damage = Math.max(0.0F, damage);
        orb.healAmount = Math.max(0.0F, healAmount);
        orb.school = school == null || school.isEmpty() ? IronsSpellDamage.ENDER : school;

        Vec3 dir = outward.lengthSqr() < 1.0E-4D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : outward.normalize();
        orb.motion = new Vec3(dir.x, 0.30D, dir.z).normalize().scale(SPEED * 0.45D);

        level.addFreshEntity(orb);
    }

    @Override
    protected void defineSynchedData() {
    }

    @Override
    public void tick() {
        super.tick();
        if (isRemoved()) {
            return;
        }
        age++;
        if (age >= MAX_AGE) {
            burstFx();
            discard();
            return;
        }

        if (target == null && targetId != null
                && (!level().isClientSide || age % 4 == 0)) {
            findTarget();
        }

        Vec3 aim = computeAim();
        Vec3 pos = position();
        if (aim == null) {
            Vec3 glide = motion.lengthSqr() > 1.0E-6D ? motion.normalize() : new Vec3(0.0D, -0.1D, 0.0D);
            setPos(pos.add(glide.scale(0.5D)));
            return;
        }

        Vec3 toAim = aim.subtract(pos);
        double distSq = toAim.lengthSqr();
        if (target != null && distSq <= ARRIVE_DIST * ARRIVE_DIST) {
            impact();
            return;
        }

        Vec3 desired = toAim.normalize().scale(SPEED);
        motion = motion.scale(1.0D - STEER).add(desired.scale(STEER));
        setPos(pos.add(motion));

        // 服务端每 3 刻放一缕电蓝魔法尘埃当拖尾
        if (!level().isClientSide && age % 3 == 0) {
            Vec3 p = position();
            ((ServerLevel) level()).sendParticles(ParticleTypes.END_ROD,
                    p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 命中：服务端结算伤害 + 治疗，双端播放命中特效，随后移除弹体。 */
    private void impact() {
        if (!level().isClientSide) {
            dealDamage();
        }
        burstFx();
        discard();
    }

    /** 结算伤害（魔法伤害）+ 治疗施法者；无目标或目标已消失时不结算。 */
    private void dealDamage() {
        if (target == null || target.isRemoved()
                || !target.isAlive() || target.level() != level()) {
            return;
        }
        if (owner == null && ownerId != null) {
            owner = findEntity(ownerId);
        }
        Entity ownerEntity = owner != null && !owner.isRemoved() ? owner : null;
        target.invulnerableTime = 0;
        if (!IronsSpellDamage.apply(ownerEntity, target, damage, IronsSpellDamage.resolve(school))) {
            target.hurt(target.damageSources().indirectMagic(this, ownerEntity), damage);
        }
        // 枪刃：命中后按 healAmount 治疗施法者
        if (healAmount > 0.0F && ownerEntity instanceof LivingEntity healer
                && healer.isAlive()) {
            healer.heal(healAmount);
        }
    }

    /** 命中/消散特效：电蓝能量迸发 + 噼啪电光。 */
    private void burstFx() {
        Vec3 p = position();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.END_ROD,
                    p.x, p.y, p.z, 16, 0.4D, 0.4D, 0.4D, 0.18D);
            serverLevel.sendParticles(ParticleTypes.ELECTRIC_SPARK,
                    p.x, p.y, p.z, 10, 0.35D, 0.35D, 0.35D, 0.25D);
            serverLevel.sendParticles(ParticleTypes.CRIT,
                    p.x, p.y, p.z, 8, 0.3D, 0.3D, 0.3D, 0.2D);
        } else {
            level().addParticle(ParticleTypes.END_ROD, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
            level().addParticle(ParticleTypes.CRIT, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
        }
    }

    @Nullable
    private Vec3 computeAim() {
        if (target != null && !target.isRemoved() && target.level() == level()) {
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            lastAim = aim;
            return aim;
        }
        return lastAim;
    }

    private void findTarget() {
        if (targetId == null) {
            return;
        }
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(TARGET_LOOKUP_RANGE),
                entity -> entity.getUUID().equals(targetId));
        if (!found.isEmpty()) {
            target = found.get(0);
            if (lastAim == null) {
                lastAim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            }
        }
    }

    @Nullable
    private LivingEntity findEntity(UUID uuid) {
        List<LivingEntity> found = level().getEntitiesOfClass(LivingEntity.class,
                getBoundingBox().inflate(TARGET_LOOKUP_RANGE),
                entity -> entity.getUUID().equals(uuid));
        return found.isEmpty() ? null : found.get(0);
    }

    @Override
    public Packet<ClientGamePacketListener> getAddEntityPacket() {
        return NetworkHooks.getEntitySpawningPacket(this);
    }

    @Override
    public void writeSpawnData(FriendlyByteBuf buffer) {
        buffer.writeBoolean(targetId != null);
        if (targetId != null) {
            buffer.writeUUID(targetId);
        }
        buffer.writeDouble(motion.x);
        buffer.writeDouble(motion.y);
        buffer.writeDouble(motion.z);
        Vec3 aim = lastAim != null ? lastAim : position();
        buffer.writeDouble(aim.x);
        buffer.writeDouble(aim.y);
        buffer.writeDouble(aim.z);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        if (buffer.readBoolean()) {
            targetId = buffer.readUUID();
        }
        motion = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        lastAim = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
    }

    @Override
    protected void addAdditionalSaveData(CompoundTag tag) {
        if (ownerId != null) {
            tag.putUUID("Owner", ownerId);
        }
        if (targetId != null) {
            tag.putUUID("Target", targetId);
        }
        tag.putFloat("Damage", damage);
        tag.putFloat("Heal", healAmount);
        tag.putString("School", school);
        tag.putInt("Age", age);
        tag.putDouble("MotionX", motion.x);
        tag.putDouble("MotionY", motion.y);
        tag.putDouble("MotionZ", motion.z);
        if (lastAim != null) {
            tag.putDouble("AimX", lastAim.x);
            tag.putDouble("AimY", lastAim.y);
            tag.putDouble("AimZ", lastAim.z);
        }
    }

    @Override
    protected void readAdditionalSaveData(CompoundTag tag) {
        ownerId = tag.hasUUID("Owner") ? tag.getUUID("Owner") : null;
        targetId = tag.hasUUID("Target") ? tag.getUUID("Target") : null;
        damage = tag.getFloat("Damage");
        healAmount = tag.getFloat("Heal");
        school = tag.getString("School");
        age = tag.getInt("Age");
        motion = new Vec3(tag.getDouble("MotionX"), tag.getDouble("MotionY"), tag.getDouble("MotionZ"));
        if (tag.contains("AimX") && tag.contains("AimY") && tag.contains("AimZ")) {
            lastAim = new Vec3(tag.getDouble("AimX"), tag.getDouble("AimY"), tag.getDouble("AimZ"));
        }
    }
}
