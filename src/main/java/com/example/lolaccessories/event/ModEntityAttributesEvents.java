package com.example.lolaccessories.event;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.entity.TestBruteEntity;
import com.example.lolaccessories.entity.TestPlayerDummyEntity;
import com.example.lolaccessories.init.ModEntityTypes;
import net.minecraftforge.event.entity.EntityAttributeCreationEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

/** 注册测试实体的属性表（EchoOrb 为弹射物无需属性）。 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, bus = Mod.EventBusSubscriber.Bus.MOD)
public final class ModEntityAttributesEvents {

    @SubscribeEvent
    public static void onEntityAttributes(EntityAttributeCreationEvent event) {
        event.put(ModEntityTypes.TEST_BRUTE.get(), TestBruteEntity.createAttributes().build());
        event.put(ModEntityTypes.TEST_PLAYER_DUMMY.get(), TestPlayerDummyEntity.createAttributes().build());

    }

    private ModEntityAttributesEvents() {
    }
}
