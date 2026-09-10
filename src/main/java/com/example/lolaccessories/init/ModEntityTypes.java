package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.entity.EchoOrbEntity;
import com.example.lolaccessories.entity.HeartsteelMarkEntity;
import com.example.lolaccessories.entity.TestBruteEntity;
import com.example.lolaccessories.entity.TestPlayerDummyEntity;
import net.minecraft.world.entity.EntityType;
import net.minecraft.world.entity.MobCategory;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/**
 * 实体类型注册。
 *
 * <p>卢登的回声——「回声」弹射物是纯服务端驱动的追踪光球：不参与碰撞、无属性、飞行寿命极短，
 * 只在命中目标时对目标造成一次纯魔法伤害。客户端由一个自定义渲染器把它画成紫色发光光球。</p>
 */
public final class ModEntityTypes {

    /** 实体类型注册器。 */
    public static final DeferredRegister<EntityType<?>> ENTITY_TYPES =
            DeferredRegister.create(ForgeRegistries.ENTITY_TYPES, LOLAccessories.MOD_ID);

    /** 回声光球（视觉追踪弹射物，伤害结算以命中为准）。 */
    public static final RegistryObject<EntityType<EchoOrbEntity>> ECHO_ORB =
            ENTITY_TYPES.register("echo_orb", () -> EntityType.Builder.<EchoOrbEntity>of(
                            EchoOrbEntity::new, MobCategory.MISC)
                    .sized(0.3F, 0.3F)
                    .clientTrackingRange(10)
                    .build("echo_orb"));

    /** 测试蛮兵：100 万血敌对假怪（无 AI，模型套用僵尸）。 */
    public static final RegistryObject<EntityType<TestBruteEntity>> TEST_BRUTE =
            ENTITY_TYPES.register("test_brute", () -> EntityType.Builder.<TestBruteEntity>of(
                            TestBruteEntity::new, MobCategory.MONSTER)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("test_brute"));

    /** 测试假人：玩家标签（player_like）假人（无 AI，模型套用猪灵）。 */
    public static final RegistryObject<EntityType<TestPlayerDummyEntity>> TEST_PLAYER_DUMMY =
            ENTITY_TYPES.register("test_player_dummy", () -> EntityType.Builder.<TestPlayerDummyEntity>of(
                            TestPlayerDummyEntity::new, MobCategory.CREATURE)
                    .sized(0.6F, 1.95F)
                    .clientTrackingRange(10)
                    .build("test_player_dummy"));

    /** 心之钢吞食印记：三圈法阵视觉实体（悬浮目标头顶，仅触发者可见）。 */
    public static final RegistryObject<EntityType<HeartsteelMarkEntity>> HEARTSTEEL_MARK =
            ENTITY_TYPES.register("heartsteel_mark", () -> EntityType.Builder.<HeartsteelMarkEntity>of(
                            HeartsteelMarkEntity::new, MobCategory.MISC)
                    .sized(0.1F, 0.1F)
                    .clientTrackingRange(10)
                    .build("heartsteel_mark"));

    private ModEntityTypes() {
    }
}
