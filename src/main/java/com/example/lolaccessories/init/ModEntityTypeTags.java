package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.tags.TagKey;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.EntityType;

/** 本模组实体类型标签（测试假人等）。 */
public final class ModEntityTypeTags {

    /** 「玩家替身」：敌我判定时按玩家语义处理（测试假人用）。 */
    public static final TagKey<EntityType<?>> PLAYER_LIKE =
            TagKey.create(Registries.ENTITY_TYPE,
                    new ResourceLocation(LOLAccessories.MOD_ID, "player_like"));

    private ModEntityTypeTags() {
    }
}
