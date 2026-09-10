package com.example.lolaccessories.crafting;

import com.example.lolaccessories.init.ModItems;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import net.minecraft.core.RegistryAccess;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.GsonHelper;
import net.minecraft.world.Container;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.item.crafting.Ingredient;
import net.minecraft.world.item.crafting.RecipeSerializer;
import net.minecraft.world.item.crafting.RecipeType;
import net.minecraft.world.item.crafting.ShapedRecipe;
import net.minecraft.world.item.crafting.SmithingRecipe;
import net.minecraft.world.level.Level;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * 「付费锻造」配方：在锻造台中按固定顺序放入 1~3 件下级装备（其余槽位留空）即可预览成品，
 * 取件时需额外支付费用（花费物品不放入锻造台，由 {@link com.example.lolaccessories.money.GoldWallet}
 * 直接从背包与末影箱扣除；锻造台的取件拦截见 mixin/SmithingMenuMixin）。
 *
 * <p>槽位约定（组件按下标 0、1、2 依次放入基底、附加、模板槽；只会用到与组件数对应的槽，
 * 其余槽必须为空）：
 * <ul>
 *     <li>单组件：放入锻造台「基底」槽；</li>
 *     <li>两组件：基底 + 附加槽；</li>
 *     <li>三组件：基底 + 附加 + 模板槽。</li>
 * </ul>
 * 取件校验与费用扣除由 SmithingMenuMixin 在 {@code mayPickup} / {@code onTake} 中完成，
 * 配方只需携带组件、成品与费用清单。</p>
 *
 * <p>JSON 结构与魔改（该配方也是标准数据包条目，任意整合包可照常操作）：
 * <ul>
 *     <li>覆盖：在更高优先级数据包里放同路径文件即可，如
 *         {@code data/lolaccessories/recipes/chain_vest.json}（改 components / cost / result 均生效）；</li>
 *     <li>禁用：Forge 对任意 recipe type 都在解析前统一处理顶层 {@code "conditions"}，
 *         在覆盖文件里写 {@code "conditions":[{"type":"forge:false"}]} 即可整条禁用；</li>
 *     <li>组件项是原版 {@link Ingredient}（支持 {@code {"item":...}} 或 {@code {"tag":...}}），
 *         因此也可用 tag 批量放宽/替换材料。</li>
 * </ul>
 * 花费字段的写法：
 * <ul>
 *     <li>新写法（任意物品，可多件）：{@code "cost":[{"item":"lolaccessories:gold_coin","count":50},
 *         {"item":"minecraft:nether_star","count":1}]}；</li>
 *     <li>旧写法（仅金币，向后兼容）：{@code "gold":50}，等价于 50 枚金币；若同时写了
 *         {@code cost}，则以 {@code cost} 为准。</li>
 * </ul>
 * 2 级装备（史诗，由 1 级升级而来）的默认花费：金币 100~200（各配方取 100~200 间的定值，
 * 当前为 195 / 143 / 176 / 194），并额外消耗 1 颗下界之星。此后新增 2 级装备未特别说明时
 * 均沿用此规则；品阶归属见 {@code ModGearTierTagsProvider}。</p>
 */
public class PaidSmithingRecipe implements SmithingRecipe {

    private final ResourceLocation id;
    private final Ingredient[] components;
    private final ItemStack result;
    private final List<ItemStack> costs;

    public PaidSmithingRecipe(ResourceLocation id, Ingredient[] components,
                              ItemStack result, List<ItemStack> costs) {
        this.id = id;
        this.components = components;
        this.result = result;
        this.costs = Collections.unmodifiableList(new ArrayList<>(costs));
    }

    /** 组件数（1~3）。 */
    public int getComponentCount() {
        return components.length;
    }

    /**
     * 取件花费清单：每项是一个 ItemStack，{@link ItemStack#getCount()} 为需要数量，
     * 取件时从背包与末影箱自动扣除。
     */
    public List<ItemStack> getCosts() {
        return costs;
    }

    public Ingredient getComponent(int index) {
        return components[index];
    }

    /**
     * 该槽位对应的组件下标；槽位 1=基底、2=附加、0=模板。若组件数小于该槽位对应下标 + 1，
     * 说明这个槽在本配方中不被使用。
     */
    private static int componentIndexOfSlot(int slot) {
        return slot == 1 ? 0 : slot == 2 ? 1 : 2;
    }

    @Override
    public ResourceLocation getId() {
        return this.id;
    }

    @Override
    public boolean matches(Container container, Level level) {
        if (container.getContainerSize() < 3) {
            return false;
        }
        for (int slot = 0; slot < 3; slot++) {
            int index = componentIndexOfSlot(slot);
            ItemStack stack = container.getItem(slot);
            if (index < components.length) {
                if (!components[index].test(stack)) {
                    return false;
                }
            } else if (!stack.isEmpty()) {
                return false; // 该槽位不在配方内，必须留空
            }
        }
        return true;
    }

    @Override
    public ItemStack assemble(Container container, RegistryAccess registryAccess) {
        return result.copy();
    }

    @Override
    public boolean isTemplateIngredient(ItemStack stack) {
        return components.length > 2 && components[2].test(stack);
    }

    @Override
    public boolean isBaseIngredient(ItemStack stack) {
        return components.length > 0 && components[0].test(stack);
    }

    @Override
    public boolean isAdditionIngredient(ItemStack stack) {
        return components.length > 1 && components[1].test(stack);
    }

    @Override
    public ItemStack getResultItem(RegistryAccess registryAccess) {
        return result.copy();
    }

    /** 归类到原版 SMITHING 类型，锻造台界面才能拿到这些配方。 */
    @Override
    public RecipeType<?> getType() {
        return RecipeType.SMITHING;
    }

    @Override
    public RecipeSerializer<?> getSerializer() {
        return PaidSmithingRecipe.Serializer.INSTANCE;
    }

    /** JSON 示例：{@code data/lolaccessories/recipes/chain_vest.json}。 */
    public static class Serializer implements RecipeSerializer<PaidSmithingRecipe> {

        public static final Serializer INSTANCE = new Serializer();

        private Serializer() {
        }

        @Override
        public PaidSmithingRecipe fromJson(ResourceLocation id, JsonObject json) {
            JsonArray arr = GsonHelper.getAsJsonArray(json, "components");
            int count = Math.max(1, Math.min(3, arr.size()));
            Ingredient[] components = new Ingredient[count];
            for (int i = 0; i < count; i++) {
                components[i] = Ingredient.fromJson(arr.get(i));
            }
            ItemStack result = ShapedRecipe.itemStackFromJson(GsonHelper.getAsJsonObject(json, "result"));

            List<ItemStack> costs = new ArrayList<>();
            if (json.has("cost")) {
                for (JsonElement element : GsonHelper.getAsJsonArray(json, "cost")) {
                    if (!element.isJsonObject()) {
                        continue;
                    }
                    ItemStack cost = ShapedRecipe.itemStackFromJson(element.getAsJsonObject());
                    if (!cost.isEmpty() && cost.getCount() > 0) {
                        costs.add(cost);
                    }
                }
            }
            // 旧写法 gold 字段向后兼容：未写 cost 时视为金币花费
            if (costs.isEmpty()) {
                int legacyGold = Math.max(0, GsonHelper.getAsInt(json, "gold", 0));
                if (legacyGold > 0) {
                    costs.add(new ItemStack(ModItems.GOLD_COIN.get(), legacyGold));
                }
            }
            return new PaidSmithingRecipe(id, components, result, costs);
        }

        @Override
        public PaidSmithingRecipe fromNetwork(ResourceLocation id, FriendlyByteBuf buf) {
            int count = Math.max(1, Math.min(3, buf.readVarInt()));
            Ingredient[] components = new Ingredient[count];
            for (int i = 0; i < count; i++) {
                components[i] = Ingredient.fromNetwork(buf);
            }
            ItemStack result = buf.readItem();
            int costSize = buf.readVarInt();
            List<ItemStack> costs = new ArrayList<>(costSize);
            for (int i = 0; i < costSize; i++) {
                ItemStack cost = buf.readItem();
                if (!cost.isEmpty() && cost.getCount() > 0) {
                    costs.add(cost);
                }
            }
            return new PaidSmithingRecipe(id, components, result, costs);
        }

        @Override
        public void toNetwork(FriendlyByteBuf buf, PaidSmithingRecipe recipe) {
            buf.writeVarInt(recipe.components.length);
            for (Ingredient ingredient : recipe.components) {
                ingredient.toNetwork(buf);
            }
            buf.writeItem(recipe.result);
            buf.writeVarInt(recipe.costs.size());
            for (ItemStack cost : recipe.costs) {
                buf.writeItem(cost);
            }
        }
    }
}
