package com.example.lolaccessories.entity;

import com.example.lolaccessories.init.ModEntityTypes;
import com.example.lolaccessories.event.LolNewEpicPassiveEvents;
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
import java.util.UUID;/**
 * 卢登的回声——「回声」追踪弹射物（紫色光球）。
 *
 * <p>索敌/弹道参考原版潜影贝射弹（Shulker Bullet）的思路：弹体先在命中点周围被“弹”出来，
 * 随后每个服务端游戏刻把飞行方向平滑转向目标，形成先散开再收拢/折返的曲线轨迹。速度刻意
 * 调快（约 1.15 格/刻，潜影贝射弹的数倍），配合短暂的飞行寿命，观感上是“快速命中”而不是
 * 慢悠悠飘过去。</p>
 *
 * <p>伤害在弹体真正命中（距目标中心足够近）的当刻结算：纯魔法伤害、击杀归属给施法玩家。
 * 服务器与客户端运行同一套追踪算法：客户端只播光球与命中特效，不结算伤害。</p>
 *
 * <p>注：若目标在弹体飞抵前已经死亡，弹体会飞完动画落到其消失位置并无害消散——
 * 尸体无法被二次伤害，这也是“先结算法术伤害、后弹射物索敌”机制的自然结果。</p>
 */
public class EchoOrbEntity extends Entity implements IEntityAdditionalSpawnData {

    /** 弹体巡航速度（格/游戏刻），明显快于潜影贝射弹。 */
    private static final double SPEED = 1.15D;
    /** 每刻把当前飞行方向往“朝向目标”方向拉近的比例：越大转弯越急、轨迹越弯。 */
    private static final double STEER = 0.20D;
    /** 距目标中心多近视为命中（格）。 */
    private static final double ARRIVE_DIST = 0.85D;
    /** 飞行寿命上限（游戏刻），防止目标跑丢后无限追踪。 */
    private static final int MAX_AGE = 60;
    /** 目标丢失后的 UUID 再查询范围（格）。 */
    private static final double TARGET_LOOKUP_RANGE = 64.0D;

    /** 追踪目标（服务端权威持有；客户端在需要时按 UUID 重新查找）。 */
    @Nullable
    private LivingEntity target;
    /** 追踪目标 UUID（用于客户端/跨存档恢复）。 */
    @Nullable
    private UUID targetId;
    /** 施法者（用于击杀归属）。 */
    @Nullable
    private LivingEntity owner;
    /** 施法者 UUID。 */
    @Nullable
    private UUID ownerId;
    /** 命中目标后造成的魔法伤害。 */
    private float damage;
    /** 本次法球采用的铁魔法学派伤害类型。 */
    private String damageSchool = IronsSpellDamage.ENDER;
    /** 是否为火箭腰带使用的固定方向直线射弹。 */
    private boolean straightFlight;
    /** 当前飞行速度向量（格/刻）。 */
    private Vec3 motion = Vec3.ZERO;
    /** 最近一次有效的目标中心点：目标死亡/消失后仍飞向该点，避免弹体原地发愣。 */
    @Nullable
    private Vec3 lastAim;
    /** 存活时长（游戏刻）。 */
    private int age;

    public EchoOrbEntity(EntityType<? extends EchoOrbEntity> type, Level level) {
        super(type, level);
        this.noCulling = true;
    }

    /**
     * 在服务端发射一道追踪回声：从 {@code start} 出发，先沿 {@code outward} 方向向外上方弹出，
     * 之后平滑转向 {@code target}，命中后造成 {@code damage} 点纯魔法伤害。
     */
    public static void launch(ServerLevel level, LivingEntity owner, @Nullable LivingEntity target,
                              float damage, Vec3 start, Vec3 outward) {
        launch(level, owner, target, damage, IronsSpellDamage.ENDER, start, outward);
    }

    /** 以指定铁魔法学派发射法球。 */
    public static void launch(ServerLevel level, LivingEntity owner, @Nullable LivingEntity target,
                              float damage, String school, Vec3 start, Vec3 outward) {
        EchoOrbEntity orb = new EchoOrbEntity(ModEntityTypes.ECHO_ORB.get(), level);
        orb.setPos(start.x, start.y, start.z);
        if (target != null) {
            orb.target = target;
            orb.targetId = target.getUUID();
            orb.lastAim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
        } else {
            // 无目标（火箭腰带向前突进时）沿视线方向飞出一段射程，命中阶段不结算伤害
            Vec3 dir = outward.lengthSqr() < 1.0E-4D
                    ? new Vec3(1.0D, 0.0D, 0.0D)
                    : outward.normalize();
            orb.lastAim = start.add(dir.scale(18.0D));
        }
        orb.owner = owner;
        orb.ownerId = owner.getUUID();
        orb.damage = Math.max(0.0F, damage);
        orb.damageSchool = IronsSpellDamage.resolve(school);

        Vec3 dir = outward.lengthSqr() < 1.0E-4D
                ? new Vec3(1.0D, 0.0D, 0.0D)
                : outward.normalize();
        // 先向外上方低速弹出，随追踪逐刻加速——弧线“先炸开、再收拢”的来源
        orb.motion = new Vec3(dir.x, 0.32D, dir.z).normalize().scale(SPEED * 0.4D);

        level.addFreshEntity(orb);
    }

    /** 发射不索敌、不转向的火箭腰带直线法球，接触敌对单位时结算伤害。 */
    public static void launchStraight(ServerLevel level, LivingEntity owner, float damage,
                                      String school, Vec3 start, Vec3 direction) {
        EchoOrbEntity orb = new EchoOrbEntity(ModEntityTypes.ECHO_ORB.get(), level);
        orb.setPos(start.x, start.y, start.z);
        orb.owner = owner;
        orb.ownerId = owner.getUUID();
        orb.damage = Math.max(0.0F, damage);
        orb.damageSchool = IronsSpellDamage.resolve(school);
        orb.straightFlight = true;
        Vec3 dir = direction.lengthSqr() < 1.0E-4D
                ? new Vec3(1.0D, 0.0D, 0.0D) : direction.normalize();
        orb.motion = dir.scale(SPEED);
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
            // 寿命耗尽：原地绽放后无害消散（不结算伤害）
            burstFx();
            discard();
            return;
        }

        if (straightFlight) {
            tickStraightFlight();
            return;
        }

        // 目标引用失效时按 UUID 重新找回（服务端每刻、客户端低频）
        if (target == null && targetId != null
                && (!level().isClientSide || age % 4 == 0)) {
            findTarget();
        }

        Vec3 aim = computeAim();
        Vec3 pos = position();
        if (aim == null) {
            // 没有可追踪点（目标消失且从未记录过位置）：沿原方向惯性滑行直至消散
            Vec3 glide = motion.lengthSqr() > 1.0E-6D ? motion.normalize() : new Vec3(0.0D, -0.1D, 0.0D);
            setPos(pos.add(glide.scale(0.5D)));
            return;
        }

        Vec3 toAim = aim.subtract(pos);
        double distSq = toAim.lengthSqr();
        if (distSq <= ARRIVE_DIST * ARRIVE_DIST) {
            impact();
            return;
        }

        // 平滑转向：新速度 = 旧速度 + (指向目标的速度 − 旧速度) × STEER，产生弯曲追踪轨迹
        Vec3 desired = toAim.normalize().scale(SPEED);
        motion = motion.scale(1.0D - STEER).add(desired.scale(STEER));
        setPos(pos.add(motion));

        // 服务端每 3 刻放一缕紫色魔法尘埃当拖尾，让飞行轨迹更清晰
        if (!level().isClientSide && age % 3 == 0) {
            Vec3 p = position();
            ((ServerLevel) level()).sendParticles(ParticleTypes.WITCH,
                    p.x, p.y, p.z, 1, 0.0D, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 火箭腰带法球：保持固定方向飞行，并在接触首个敌对单位时爆炸。 */
    private void tickStraightFlight() {
        Vec3 next = position().add(motion);
        setPos(next);
        if (!level().isClientSide) {
            if (owner == null && ownerId != null) {
                owner = findEntity(ownerId);
            }
            LivingEntity hit = level().getEntitiesOfClass(LivingEntity.class,
                    getBoundingBox().inflate(0.35D), entity -> entity.isAlive()
                            && entity != owner
                            && (owner == null || LolNewEpicPassiveEvents.isEnemyOf(owner, entity)))
                    .stream().findFirst().orElse(null);
            if (hit != null) {
                target = hit;
                targetId = hit.getUUID();
                impact();
                return;
            }
        }
        if (!level().isClientSide && age % 3 == 0) {
            ((ServerLevel) level()).sendParticles(ParticleTypes.FLAME,
                    next.x, next.y, next.z, 2, 0.08D, 0.08D, 0.08D, 0.01D);
        }
    }

    /** 命中：服务端结算伤害，双端播放命中特效，随后移除弹体。 */
    private void impact() {
        if (!level().isClientSide) {
            dealDamage();
        }
        burstFx();
        discard();
    }

    /** 结算伤害：纯魔法伤害、击杀归属施法者。目标已死亡/消失则不结算。 */
    private void dealDamage() {
        if (target == null || target.isRemoved()) {
            return;
        }
        if (!target.isAlive() || target.level() != level()) {
            return;
        }
        if (owner == null && ownerId != null) {
            owner = findEntity(ownerId);
        }
        Entity ownerEntity = owner != null && !owner.isRemoved() ? owner : null;
        // 一轮回声里，主目标往往要连续吃下“全额 + 数道折返”多道伤害，折返弹几乎同时抵达。
        // 原版目标受伤后会进入 20 tick 的无敌帧，若不清掉，第二道起的回声会被直接吞掉，
        // 表现为“主目标只吃到一道全额伤害”。命中结算前清空该计时器，保证每道回声如实入账。
        target.invulnerableTime = 0;
        // 卢登的回声：魔法伤害走铁魔法「末影」学派（保持原末影口径：原装备给的是末影法强，
        // 技能学派不变）。见 IronsSpellDamage——本模组所有装备技能的魔法伤害都是铁魔法伤害。
        if (!IronsSpellDamage.apply(ownerEntity, target, damage, damageSchool)) {
            // 学派伤害未结算（如铁魔法缺席时的回退已被 apply 内部处理，此处仅在被完全取消时兜底）
            target.hurt(target.damageSources().indirectMagic(this, ownerEntity), damage);
        }
    }

    /** 命中/消散特效：紫色能量迸发。 */
    private void burstFx() {
        Vec3 p = position();
        if (level() instanceof ServerLevel serverLevel) {
            serverLevel.sendParticles(ParticleTypes.PORTAL,
                    p.x, p.y, p.z, 14, 0.4D, 0.4D, 0.4D, 0.15D);
            serverLevel.sendParticles(ParticleTypes.ENCHANTED_HIT,
                    p.x, p.y, p.z, 8, 0.35D, 0.35D, 0.35D, 0.3D);
            serverLevel.sendParticles(ParticleTypes.WITCH,
                    p.x, p.y, p.z, 6, 0.3D, 0.3D, 0.3D, 0.0D);
        } else {
            level().addParticle(ParticleTypes.PORTAL, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
            level().addParticle(ParticleTypes.ENCHANTED_HIT, p.x, p.y, p.z, 0.0D, 0.0D, 0.0D);
        }
    }

    /** 当前追踪点：目标中心；目标死亡/消失后冻结在最后一个有效位置。 */
    @Nullable
    private Vec3 computeAim() {
        if (target != null && !target.isRemoved() && target.level() == level()) {
            Vec3 aim = target.position().add(0.0D, target.getBbHeight() * 0.5D, 0.0D);
            lastAim = aim;
            return aim;
        }
        return lastAim;
    }

    /** 在自身附近按 UUID 找回目标（客户端也共用，保证双端轨迹一致）。 */
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
        buffer.writeBoolean(straightFlight);
        buffer.writeBoolean(targetId != null);
        if (targetId != null) {
            buffer.writeUUID(targetId);
        }
        // 同步初始飞行速度与首个追踪点：客户端据此复现同样的弧线轨迹
        buffer.writeDouble(motion.x);
        buffer.writeDouble(motion.y);
        buffer.writeDouble(motion.z);
        buffer.writeUtf(damageSchool);
        Vec3 aim = lastAim != null ? lastAim : position();
        buffer.writeDouble(aim.x);
        buffer.writeDouble(aim.y);
        buffer.writeDouble(aim.z);
    }

    @Override
    public void readSpawnData(FriendlyByteBuf buffer) {
        straightFlight = buffer.readBoolean();
        if (buffer.readBoolean()) {
            targetId = buffer.readUUID();
        }
        motion = new Vec3(buffer.readDouble(), buffer.readDouble(), buffer.readDouble());
        damageSchool = buffer.readUtf();
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
        tag.putString("DamageSchool", damageSchool);
        tag.putBoolean("StraightFlight", straightFlight);
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
        damageSchool = tag.contains("DamageSchool") ? tag.getString("DamageSchool") : IronsSpellDamage.ENDER;
        straightFlight = tag.getBoolean("StraightFlight");
        age = tag.getInt("Age");
        motion = new Vec3(tag.getDouble("MotionX"), tag.getDouble("MotionY"), tag.getDouble("MotionZ"));
        if (tag.contains("AimX") && tag.contains("AimY") && tag.contains("AimZ")) {
            lastAim = new Vec3(tag.getDouble("AimX"), tag.getDouble("AimY"), tag.getDouble("AimZ"));
        }
    }
}
