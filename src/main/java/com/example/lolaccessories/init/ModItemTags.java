package com.example.lolaccessories.init;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.core.registries.Registries;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.tags.TagKey;
import net.minecraft.world.item.Item;

/**
 * 装备品阶标签（按获取途径划分的装备等级）。
 *
 * <p>约定（决定装备属于哪一级的规则见 {@code ModGearTierTagsProvider}）：
 * <ul>
 *     <li>{@link #TIER1}：1 级装备（普通）。在工作台（crafting_shaped/shapeless）合成的装备；
 *         供后续在锻造台付费锻造升级为 2 级装备；</li>
 *     <li>{@link #TIER2}：2 级装备（史诗）。由 1 级装备在锻造台经 {@code paid_smithing}
 *         配方升级而来；</li>
 *     <li>{@link #TIER3}：3 级装备（传说）。由 2 级装备在锻造台升级而来，
 *         目前包括黑色切割者、卢登的回声、无尽之刃三件传说装备；</li>
 *     <li>{@link #TIER4}：4 级装备（神话）。当前尚未实装，标签先置空预留；
 *         后续新增每件神话装备都要加入该标签（配套的专属进度才会随新装备自动生效）。</li>
 * </ul>
 * </p>
 *
 * <p>这些标签供数据包配方（组件/原料可直接用 {@code "tag": "lolaccessories:tierX"} 引用整级装备）、
 * 兼容扩展与后续代码按品阶检索装备使用；由 {@code ModGearTierTagsProvider} 数据生成。</p>
 */
public final class ModItemTags {

    /** 1 级装备（普通，工作台合成）。 */
    public static final TagKey<Item> TIER1 = itemTag("tier1");

    /** 2 级装备（史诗，锻造台由 1 级升级）。 */
    public static final TagKey<Item> TIER2 = itemTag("tier2");

    /** 3 级装备（传说，锻造台由 2 级升级）。 */
    public static final TagKey<Item> TIER3 = itemTag("tier3");

    /** 4 级装备（神话，尚未实装，先置空预留；新增神话装备时补进 {@code tier4} 标签）。 */
    public static final TagKey<Item> TIER4 = itemTag("tier4");

    private static TagKey<Item> itemTag(String path) {
        return TagKey.create(Registries.ITEM, new ResourceLocation(LOLAccessories.MOD_ID, path));
    }

    private ModItemTags() {
    }
}
