package com.example.lolaccessories.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.piglin.Piglin;
import net.minecraft.world.level.Level;

/**
 * 测试假人（Test Dummy）：带 {@code lolaccessories:player_like} 实体类型标签——
 * 本模组的敌我判定（isEnemyOf）把它按「玩家」语义处理，用于测试只对玩家目标
 * 生效的装备效果。直接继承原版 {@link Piglin}——原版猪灵渲染器内部会强转回 Piglin，
 * 继承它才能复用猪灵模型而不崩。AI 用 {@code setNoAi(true)} 关闭（Brain 不 tick，
 * 不会仇恨/攻击玩家），永不自然消失。
 * 用 {@code /summon lolaccessories:test_player_dummy} 召唤。
 */
public class TestPlayerDummyEntity extends Piglin {

    public TestPlayerDummyEntity(EntityType<? extends Piglin> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Piglin.createAttributes()
                .add(Attributes.MAX_HEALTH, 1000.0D);
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
