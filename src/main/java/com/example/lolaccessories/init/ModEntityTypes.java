package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.entity.EchoOrbEntity;
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

    private ModEntityTypes() {
    }
}
