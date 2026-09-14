package com.example.lolaccessories.mixin;

import com.example.lolaccessories.event.LolTrueDamageEvents;
import net.minecraft.nbt.CompoundTag;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.level.Level;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * 真理伤害——抹除生物的深层禁生成标记。
 *
 * <p>被真理伤害抹除的生物类型会写入存档级禁生成名单（{@code ErasureSavedData}）。
 * 这里挂在 {@link EntityType#create(Level)} 的返回处，给名单内类型的实例打上
 * {@code lolaccessories_banned_spawn} 标记；随后由
 * {@code LolTrueDamageEvents#onEntityJoinLevel} 在实体加入世界时按标记/名单取消——
 * 实体可以被创建（第三方模组如 Apotheosis 会拿它继续走 Boss 化流程，不判空也不会崩），
 * 但<b>永远不会真正出现在世界里</b>。</p>
 *
 * <p>历史教训（1.3.81 及之前）：此 mixin 曾经直接让 {@code create} 返回 null，
 * 导致不判空的第三方模组（Apotheosis 自然 Boss 升级）NPE 崩服，且原版
 * {@code NaturalSpawner} 对每次失败的生成刷 {@code Can't spawn entity of type}
 * 警告——因此改为「标记 + 入界拦截」的两段式方案。</p>
 */
@Mixin(EntityType.class)
public abstract class EntityTypeCreateMixin {

    @Inject(method = "create(Lnet/minecraft/world/level/Level;)Lnet/minecraft/world/entity/Entity;",
            at = @At("RETURN"))
    private void lolaccessories$markErasedEntityType(Level level, CallbackInfoReturnable<Entity> cir) {
        Entity entity = cir.getReturnValue();
        if (entity == null || level == null || level.isClientSide) {
            return;
        }
        MinecraftServer server = level.getServer();
        if (server == null) {
            return;
        }
        @SuppressWarnings("unchecked")
        EntityType<? extends Entity> self = (EntityType<? extends Entity>) (Object) this;
        if (LolTrueDamageEvents.ErasureSavedData.get(server).isBanned(self)) {
            CompoundTag data = entity.getPersistentData();
            data.putBoolean(LolTrueDamageEvents.BANNED_SPAWN_TAG, true);
        }
    }
}
