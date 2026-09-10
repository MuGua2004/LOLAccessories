package com.example.lolaccessories.entity;

import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.ai.attributes.AttributeSupplier;
import net.minecraft.world.entity.ai.attributes.Attributes;
import net.minecraft.world.entity.monster.Zombie;
import net.minecraft.world.level.Level;

/**
 * 测试蛮兵（Test Brute）：100 万血、敌对生物、无 AI、永不自然消失。
 * 直接继承原版 {@link Zombie}——原版僵尸渲染器（ZombieRenderer）内部会把实体强转回
 * Zombie，继承它才能复用僵尸模型而不崩。AI 用 {@code setNoAi(true)} 关闭，
 * 顺带屏蔽阳光燃烧与目标寻路，供装备效果站桩测试。
 * 用 {@code /summon lolaccessories:test_brute} 召唤。
 */
public class TestBruteEntity extends Zombie {

    public TestBruteEntity(EntityType<? extends Zombie> type, Level level) {
        super(type, level);
        this.setNoAi(true);
        this.setPersistenceRequired();
    }

    public static AttributeSupplier.Builder createAttributes() {
        return Zombie.createAttributes()
                .add(Attributes.MAX_HEALTH, 1000000.0D);
    }

    @Override
    public void registerGoals() {
        // 无 AI：不注册任何目标/移动/攻击 Goal
    }

    @Override
    public boolean isSunBurnTick() {
        // 测试用：不在阳光下燃烧
        return false;
    }

    @Override
    public boolean removeWhenFarAway(double distance) {
        return false;
    }
}
