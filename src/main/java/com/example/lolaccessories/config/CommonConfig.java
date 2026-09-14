package com.example.lolaccessories.config;

import com.example.lolaccessories.LOLAccessories;
import net.minecraftforge.common.ForgeConfigSpec;
import net.minecraftforge.fml.config.ModConfig;
import net.minecraftforge.fml.event.config.ModConfigEvent;

/**
 * 通用（COMMON）配置：服主级别的全局开关。
 *
 * <p><b>低数值模式</b>（{@code lowValueMode}）：开启后，本模组所有饰品 JSON
 * （config/lolaccessories/gear/*.json）中的「强度类」数值——基础属性 amount 与
 * 技能/被动的伤害、治疗、护盾、属性点数、各种比例系数——在加载时统一乘以 1/10。</p>
 *
 * <p>行为要点：</p>
 * <ul>
 *   <li><b>只影响内存</b>：外置 JSON 文件不会被改写，关闭后重新加载即恢复原值；</li>
 *   <li><b>机制类字段不缩放</b>：冷却/持续/间隔/窗口秒数、作用半径与距离、概率与
 *       概率系数、层数与数量上限、生命阈值与触发线、法力消耗倍率、冲刺初速度等；</li>
 *   <li>切换后需重进游戏或执行 {@code /lolaccessories reload} 让装备缓存重载；</li>
 *   <li>多人游戏时服务端与客户端需各自开启，否则服务端结算与客户端 tooltip 不一致。</li>
 * </ul>
 */
public final class CommonConfig {

    private static final ForgeConfigSpec.Builder BUILDER = new ForgeConfigSpec.Builder();

    /** 低数值模式总开关（默认关闭）。 */
    public static final ForgeConfigSpec.BooleanValue LOW_VALUE_MODE = BUILDER
            .comment("低数值模式：开启后所有饰品数值（基础属性 + 技能/被动的伤害、治疗、护盾、比例等强度数值）"
                    + "降为原来的 1/10。只影响运行时数值，不修改 config/lolaccessories/gear/ 下的 JSON 文件；"
                    + "冷却/持续/半径/概率/层数上限等机制类字段不缩放。切换后需重进游戏或 /lolaccessories reload 生效")
            .define("lowValueMode", false);

    /** 已构建的配置规格（注册到 {@link ModConfig.Type#COMMON}）。 */
    public static final ForgeConfigSpec SPEC = BUILDER.build();

    /** 配置加载完成后的缓存值（避免配置尚未加载时读取异常；未加载前视为关闭）。 */
    private static volatile boolean cachedLowValue = false;

    private CommonConfig() {
    }

    /** 是否开启低数值模式（配置未加载完成时返回 false）。 */
    public static boolean isLowValueMode() {
        return cachedLowValue;
    }

    /**
     * MOD 总线配置事件（Loading / Reloading）：刷新缓存开关；
     * 开关发生变化时清空装备配置缓存，让饰品下次读取时按新模式重新缩放。
     * 由主类构造器通过 {@code modEventBus.addListener(CommonConfig::onModConfig)} 注册。
     */
    public static void onModConfig(ModConfigEvent event) {
        if (event.getConfig().getSpec() != SPEC) {
            return;
        }
        boolean newValue = LOW_VALUE_MODE.get();
        if (cachedLowValue != newValue) {
            LOLAccessories.LOGGER.info("[CommonConfig] 低数值模式已切换为 {}，装备配置缓存将重新加载", newValue);
            GearConfigManager.reload();
        }
        cachedLowValue = newValue;
    }
}
