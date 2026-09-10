package com.example.lolaccessories.data;

import com.example.lolaccessories.LOLAccessories;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.data.CachedOutput;
import net.minecraft.data.DataProvider;
import net.minecraft.data.PackOutput;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

/**
 * 成就（进度）数据生成：{@code data/lolaccessories/advancements/*.json} 与进度标签页背景纹理。
 *
 * <p>采用原版进度系统，所有进度的准则统一为 {@code trigger: "minecraft:impossible"}，
 * 由服务端 {@code LolAdvancementService} 在判定条件达成时手动 {@code award}（准则名 unlock），
 * 完成时的聊天紫字横幅与右上角 toast 均走原版机制。</p>
 *
 * <p>标题配色：全部进度为「高级成就」淡紫（{@value #ADVANCED_COLOR}）；神话相关进度
 * （书写神话 / 不败的勇者）额外使用深紫 + 加粗的醒目样式（{@value #MYTHIC_COLOR}）。
 * 字体走原版合成（不加粗/加粗由样式决定），不引入任何外部字体文件。</p>
 *
 * <p>背景纹理不由此类生成，由静态资源提供（{@value #BACKGROUND}），
 * 使 root 具备独立标签页；纹理文件由仓库维护（src/main/resources 下的
 * {@code assets/lolaccessories/textures/gui/advancements/backgrounds/achievements.png}）。
 * 注意不能放进 src/generated/resources：数据生成器的 HashCache 会把未产出的文件
 * 当作 stale 清理。</p>
 */
public class ModAdvancementProvider implements DataProvider {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    /** 普通「高级成就」标题颜色（淡紫）。 */
    private static final String ADVANCED_COLOR = "#c48bff";
    /** 神话相关进度标题颜色（深紫，加粗醒目）。 */
    private static final String MYTHIC_COLOR = "#7b2cff";
    /** 描述文字颜色。 */
    private static final String DESC_COLOR = "#b3a6c4";
    /** 背景贴图（root 专用，独立标签页锚点）。 */
    private static final String BACKGROUND =
            LOLAccessories.MOD_ID + ":textures/gui/advancements/backgrounds/achievements.png";

    /** 逐条进度：[id, 父级 id(null=root 的直属根用 root), 图标 item, frame, 是否神话样式]。 */
    private static final String[][] ADVANCEMENTS = {
            // 装备收集（每个品阶 1 件 + 集齐）
            {"heroes_journey", "root", LOLAccessories.MOD_ID + ":doran_blade", "task", "false"},
            {"hello_world", "heroes_journey", LOLAccessories.MOD_ID + ":dark_seal", "goal", "false"},
            {"epic_ballad", "root", LOLAccessories.MOD_ID + ":negatron_cloak", "task", "false"},
            {"so_called_heroes", "epic_ballad", LOLAccessories.MOD_ID + ":wardens_mail", "goal", "false"},
            {"legend_journey", "root", LOLAccessories.MOD_ID + ":black_cleaver", "task", "false"},
            {"endless_night", "legend_journey", LOLAccessories.MOD_ID + ":infinity_edge", "goal", "false"},
            {"myth_writing", "root", LOLAccessories.MOD_ID + ":clear_skys_wish", "challenge", "true"},
            {"undefeated_brave", "myth_writing", LOLAccessories.MOD_ID + ":clear_skys_wish", "challenge", "true"},
            {"heaven_fall", "myth_writing", LOLAccessories.MOD_ID + ":clear_skys_wish", "challenge", "true"},
            {"unimaginable_horror", "myth_writing", LOLAccessories.MOD_ID + ":demon_heart", "challenge", "true"},
            // 全收集
            {"champions", "undefeated_brave", LOLAccessories.MOD_ID + ":gold_coin", "challenge", "false"},
            // 金币 / 合成
            {"first_gold", "root", LOLAccessories.MOD_ID + ":gold_coin", "task", "false"},
            {"first_craft", "root", LOLAccessories.MOD_ID + ":giant_belt", "task", "false"},
            // 主动技能
            {"hero_power", "root", LOLAccessories.MOD_ID + ":seekers_armguard", "task", "false"},
            // 同时佩戴件数（图标统一换成装备贴图）
            {"wearing_5", "root", LOLAccessories.MOD_ID + ":boots", "task", "false"},
            {"wearing_10", "wearing_5", LOLAccessories.MOD_ID + ":chain_vest", "task", "false"},
            {"wearing_20", "wearing_10", LOLAccessories.MOD_ID + ":recurve_bow", "goal", "false"},
            {"wearing_30", "wearing_20", LOLAccessories.MOD_ID + ":kaenic_rookern", "goal", "false"},
            {"wearing_50", "wearing_30", LOLAccessories.MOD_ID + ":dusk_and_dawn", "challenge", "false"},
            {"wearing_100", "wearing_50", LOLAccessories.MOD_ID + ":actualizer", "challenge", "false"},
    };

    private final PackOutput packOutput;

    public ModAdvancementProvider(PackOutput packOutput) {
        this.packOutput = packOutput;
    }

    @Override
    public CompletableFuture<?> run(CachedOutput cache) {
        List<CompletableFuture<?>> writes = new ArrayList<>();
        Path dataRoot = packOutput.getOutputFolder().resolve("data/" + LOLAccessories.MOD_ID + "/advancements");

        // root：独立标签页锚点，不作任何授予
        writes.add(DataProvider.saveStable(cache, buildRootJson(),
                dataRoot.resolve("root.json")));

        for (String[] adv : ADVANCEMENTS) {
            String id = adv[0];
            boolean mythic = "true".equals(adv[4]);
            writes.add(DataProvider.saveStable(cache, buildJson(id, adv[1], adv[2], adv[3], mythic),
                    dataRoot.resolve(id + ".json")));
        }
        return CompletableFuture.allOf(writes.toArray(new CompletableFuture<?>[0]));
    }

    @Override
    public String getName() {
        return "LOLAccessories Advancements + background";
    }

    /** 生成 root（标签页锚点，含背景纹理，不授予）。 */
    private JsonObject buildRootJson() {
        JsonObject root = new JsonObject();
        JsonObject display = new JsonObject();
        display.add("icon", icon(LOLAccessories.MOD_ID + ":long_sword"));
        display.add("title", title("root", ADVANCED_COLOR, false));
        display.add("description", description("root"));
        display.addProperty("background", BACKGROUND);
        display.addProperty("frame", "task");
        display.addProperty("show_toast", false);
        display.addProperty("announce_to_chat", false);
        display.addProperty("hidden", false);
        root.add("display", display);
        root.add("criteria", criteria());
        return root;
    }

    private JsonObject buildJson(String id, String parent, String iconItem, String frame, boolean mythic) {
        JsonObject json = new JsonObject();
        json.addProperty("parent", LOLAccessories.MOD_ID + ":" + parent);
        JsonObject display = new JsonObject();
        display.add("icon", icon(iconItem));
        display.add("title", title(id, mythic ? MYTHIC_COLOR : ADVANCED_COLOR, mythic));
        display.add("description", description(id));
        display.addProperty("frame", frame);
        display.addProperty("show_toast", true);
        display.addProperty("announce_to_chat", true);
        display.addProperty("hidden", false);
        json.add("display", display);
        json.add("criteria", criteria());
        return json;
    }

    private static JsonObject icon(String itemId) {
        JsonObject icon = new JsonObject();
        icon.addProperty("item", itemId);
        return icon;
    }

    private static JsonObject title(String id, String color, boolean mythic) {
        JsonObject title = new JsonObject();
        title.addProperty("translate", "advancements." + LOLAccessories.MOD_ID + "." + id + ".title");
        title.addProperty("color", color);
        if (mythic) {
            title.addProperty("bold", true);
        }
        return title;
    }

    private static JsonObject description(String id) {
        JsonObject desc = new JsonObject();
        desc.addProperty("translate", "advancements." + LOLAccessories.MOD_ID + "." + id + ".description");
        desc.addProperty("color", DESC_COLOR);
        return desc;
    }

    /** 准则：不可自动触发的 impossible，只能由 LolAdvancementService 手动授予。 */
    private static JsonObject criteria() {
        JsonObject trigger = new JsonObject();
        trigger.addProperty("trigger", "minecraft:impossible");
        JsonObject criteria = new JsonObject();
        criteria.add("unlock", trigger);
        return criteria;
    }

}
