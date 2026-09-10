package com.example.lolaccessories.config;

import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;

/**
 * 客户端装备特效质量配置（{@link ModConfig.Type#CLIENT}，仅客户端读取）。
 *
 * <p>管理战歌增幅、黯炎灼烧等特效的“炫度”：总开关 + 光尘/火花密度倍率 + 整体亮度倍率。
 * 想完全关掉这些叠加光效就把 {@code enabled} 设为 false；觉得不够花就调高 {@code density}，
 * 觉得刺眼就调低 {@code brightness}。改完在游戏中会热重载，无需重启。</p>
 *
 * <p>字段全部为静态只读的 ConfigValue，渲染线程直接调用 {@code .get()}，开销可忽略。</p>
 */
public final class ClientFxConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 装备叠加特效总开关。 */
    public static final ForgeConfigSpec.BooleanValue ENABLED = BUILDER
            .comment("是否绘制战歌/火炬等装备叠加光效（false 完全关闭，含已激活的窗口）")
            .define("enabled", true);

    /** 光尘/火花/火苗细节密度倍率（0 为最简，1 为默认，上限 3）。 */
    public static final ForgeConfigSpec.DoubleValue DENSITY = BUILDER
            .comment("特效细节密度倍率：0.0~3.0，默认 1.0。"
                    + "影响上升光尘、脚底光点、火苗分段与火花数量，调高更华丽、耗电也更多")
            .defineInRange("density", 1.0D, 0.0D, 3.0D);

    /** 整体亮度/透明度倍率。 */
    public static final ForgeConfigSpec.DoubleValue BRIGHTNESS = BUILDER
            .comment("特效整体亮度倍率：0.25~2.0，默认 1.0（嫌闪眼可下调）")
            .defineInRange("brightness", 1.0D, 0.25D, 2.0D);

    /** 已构建的配置规格（注册到 {@link ModConfig.Type#CLIENT}）。 */
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    private ClientFxConfig() {
    }
}
