package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.sounds.SoundEvent;
import net.minecraftforge.registries.DeferredRegister;
import net.minecraftforge.registries.ForgeRegistries;
import net.minecraftforge.registries.RegistryObject;

/** 本模组自定义音效（心之钢击碎印记等）。 */
public final class ModSounds {

    public static final DeferredRegister<SoundEvent> SOUND_EVENTS =
            DeferredRegister.create(ForgeRegistries.SOUND_EVENTS, LOLAccessories.MOD_ID);

    /** 心之钢·庞然吞食：击碎印记（提取自英雄联盟客户端的原版音效）。 */
    public static final RegistryObject<SoundEvent> HEARTSTEEL_SHATTER =
            SOUND_EVENTS.register("heartsteel_shatter", () -> SoundEvent.createVariableRangeEvent(
                    new ResourceLocation(LOLAccessories.MOD_ID, "heartsteel_shatter")));

    private ModSounds() {
    }
}
