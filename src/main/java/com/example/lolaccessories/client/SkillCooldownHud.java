package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.config.ClientFxConfig;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.entity.player.Player;
import net.minecraft.world.item.Item;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderGuiOverlayEvent;
import net.minecraftforge.client.gui.overlay.ForgeGui;
import net.minecraftforge.client.gui.overlay.VanillaGuiOverlay;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.function.Predicate;
import java.util.function.Supplier;

/**
 * 主动技能/被动冷却可视化总控（客户端）。
 *
 * <p>统一维护所有「冷却条」：每个技能一个 {@link Entry}，含技能 id、所属列（左/右）、
 * 装备图标与可见条件。收到服务端冷却包后按技能 id 标记起始时刻，HUD 上从该行起画一根
 * 渐短的进度条，并在前端附装备小图标。</p>
 *
 * <p>同一列支持<b>多根同时出现</b>：每根进度条自基准行（0 号）向上逐行堆叠，后续
 * 新增主动技能只需在 {@link #ENTRIES} 注册里加一行即可，无需再写新的 HUD 类。</p>
 *
 * <p>布局锚点：<b>快捷栏左缘外侧、向上偏移</b>——单列从快捷栏上方 10px 处起，
 * 多根向上堆叠。不占快捷栏正上方中央区（避开铁魔法等其他模组的 HUD 条），
 * 仍可用 skillBar.xOffset / yOffset 在配置里微调错开。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkillCooldownHud {

    /** 单根进度条宽度。 */
    private static final int BAR_WIDTH = 72;
    /** 进度条高度（RPG 斜面发光条，压缩一半）。 */
    private static final int BAR_HEIGHT = 6;
    /** 同一列多根冷却条之间的纵向节距（条高 6 + 4px 间隔）。 */
    private static final int ROW_STRIDE = 10;
    /** 条组左缘与快捷栏右缘的横向空隙。 */
    private static final int HOTBAR_RIGHT_GAP = 8;
    /** 基准行（0 号）条底距屏幕底部的距离：与左侧护盾条平齐、贴快捷栏右上角。 */
    private static final int HOTBAR_TOP_BASE = 25;

    /** 时间停止（探索者的护臂）进度条填充色（AARRGGBB）：金身般的琥珀金。 */
    private static final int TIME_STOP_FILL = 0xFFFFC44D;
    /** 回声（卢登的回声）进度条填充色（AARRGGBB）：紫。 */
    private static final int ECHO_FILL = 0xFFB26BFF;
    /** 净化（水银饰带）进度条填充色（AARRGGBB）：水银般的银蓝。 */
    private static final int QUICKSILVER_FILL = 0xFF9FD8FF;
    /** 新月（提亚马特）进度条填充色（AARRGGBB）：熔铸的赤铜。 */
    private static final int CRESCENT_FILL = 0xFFFFB36B;
    /** 鼓舞（舒瑞娅的战歌）进度条填充色（AARRGGBB）：海风般的暖金青。 */
    private static final int INSPIRING_SPEECH_FILL = 0xFF8FE3C8;
    /** 法力成真（实现器）进度条填充色（AARRGGBB）：映出未来的炽金。 */
    private static final int REALIZE_FILL = 0xFFFFD700;
    /** 超速驱动（海克斯注力刚壁）进度条填充色（AARRGGBB）：电蓝。 */
    private static final int OVERDRIVE_FILL = 0xFF58C8FF;
    /** 聚合风暴（基克的聚合）进度条填充色（AARRGGBB）：冰橙对撞。 */
    private static final int ZEKES_FILL = 0xFF7FB8FF;
    /** 救主灵刃（斯特拉克的挑战护手）进度条填充色（AARRGGBB）：巨力金。 */
    private static final int STERAKS_FILL = 0xFFFFC82E;
    /** 应急护盾（炽天使之拥）进度条填充色（AARRGGBB）：圣光金白。 */
    private static final int SERAPH_FILL = 0xFFFFE9A8;
    /** 永恒（冬之誓）进度条填充色（AARRGGBB）：寒霜冰蓝。 */
    private static final int FIMBULWINTER_FILL = 0xFFA8D8FF;
    /** 重生（守护天使）进度条填充色（AARRGGBB）：复活圣绿。 */
    private static final int GA_FILL = 0xFF8FFFB2;
    /** 闪电弹球（海克斯科技枪刃）进度条填充色（AARRGGBB）：电蓝青。 */
    private static final int GUNBLADE_FILL = 0xFF5AD2FF;
    /** 魔法弹突进（海克斯科技火箭腰带）进度条填充色（AARRGGBB）：火箭橙红。 */
    private static final int ROCKETBELT_FILL = 0xFFFF7A4D;
    /** 破阵冲击波（挺进破坏者）进度条填充色（AARRGGBB）：青绿冲击。 */
    private static final int SHOCKWAVE_FILL = 0xFF4FD8C8;

    /** 冷却条属于左列还是右列。 */
    private enum Column {
        LEFT, RIGHT
    }

    /** 一条冷却记录：技能标识 + 布局归属 + 装备图标 + 可见条件 + 冷却计时状态。 */
    private static final class Entry {
        final String skillId;
        final Column column;
        /** 装备图标来源；渲染时才取值（物品注册时机晚于类装载）。 */
        final Supplier<Item> item;
        /** 额外可见条件（如是否仍佩戴对应装备、水下是否让位）。 */
        final Predicate<Player> visible;
        /** 进度条填充色（AARRGGBB）。 */
        final int fillColor;

        ClientLevel level;
        long triggerTick = -1;
        int cooldownTicks = 0;
        /** 调试通道：冷却条无视可见条件强制显示，直到该游戏刻（/lolaccessories cdtest）。 */
        long debugUntilTick = -1;

        Entry(String skillId, Column column, Supplier<Item> item, Predicate<Player> visible, int fillColor) {
            this.skillId = skillId;
            this.column = column;
            this.item = item;
            this.visible = visible;
            this.fillColor = fillColor;
        }

        /** 冷却记录是否归属指定世界。 */
        boolean tracks(ClientLevel world) {
            return level == world;
        }

        /** 由冷却包在主线程调用：开始/重置冷却计时。 */
        void mark(int ticks) {
            mark(ticks, false);
        }

        /** 由冷却包在主线程调用：开始/重置冷却计时（debug 置真时无视可见条件强制显示）。 */
        void mark(int ticks, boolean debug) {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel world = mc.level;
            if (world == null) {
                return;
            }
            level = world;
            triggerTick = world.getGameTime();
            cooldownTicks = Math.max(1, ticks);
            if (debug) {
                debugUntilTick = triggerTick + cooldownTicks;
            }
        }

        void clear() {
            level = null;
            triggerTick = -1;
            cooldownTicks = 0;
            debugUntilTick = -1;
        }
    }

    /** 全部冷却条目，按注册顺序遍历保证同列内行序稳定。 */
    private static final Map<String, Entry> ENTRIES = new LinkedHashMap<>();

    private static void register(String skillId, Column column, Supplier<Item> item,
                                 Predicate<Player> visible, int fillColor) {
        ENTRIES.put(skillId, new Entry(skillId, column, item, visible, fillColor));
    }

    static {
        // 探索者的护臂——主动技「时间停止」（左侧，基准：盔甲条上方）
        register("time_stop", Column.LEFT,
                ModItems.SEEKERS_ARMGUARD::get,
                p -> CuriosGearWear.isWearing(p, "seekers_armguard"),
                TIME_STOP_FILL);
        // 水银饰带——主动技「净化」（左侧；同一列多根冷却条自动向上堆叠）
        register("quicksilver", Column.LEFT,
                ModItems.QUICKSILVER_SASH::get,
                p -> CuriosGearWear.isWearing(p, "quicksilver_sash"),
                QUICKSILVER_FILL);
        // 提亚马特——主动技「新月」（左侧）
        register("crescent", Column.LEFT,
                ModItems.TIAMAT::get,
                p -> CuriosGearWear.isWearing(p, "tiamat"),
                CRESCENT_FILL);
        // 舒瑞娅的战歌——主动技「鼓舞」（左侧）
        register("inspiring_speech", Column.LEFT,
                ModItems.SHURELYAS_BATTLESONG::get,
                p -> CuriosGearWear.isWearing(p, "shurelyas_battlesong"),
                INSPIRING_SPEECH_FILL);
        // 实现器——主动技「法力成真」（左侧）
        register("realize", Column.LEFT,
                ModItems.ACTUALIZER::get,
                p -> CuriosGearWear.isWearing(p, "actualizer"),
                REALIZE_FILL);
        // 卢登的回声——被动「回声」（右侧，基准：食物条上方）
        register("echo", Column.RIGHT,
                ModItems.LUDENS_ECHO::get,
                p -> !p.isUnderWater() && p.getAirSupply() >= 300
                        && CuriosGearWear.isWearing(p, "ludens_echo"),
                ECHO_FILL);
        // 海克斯注力刚壁——被动「超速驱动」（右侧）
        register("overdrive", Column.RIGHT,
                ModItems.EXPERIMENTAL_HEXPLATE::get,
                p -> CuriosGearWear.isWearing(p, "experimental_hexplate"),
                OVERDRIVE_FILL);
        // 基克的聚合——被动「聚合风暴」（右侧）
        register("zekes_convergence", Column.RIGHT,
                ModItems.ZEKES_CONVERGENCE::get,
                p -> CuriosGearWear.isWearing(p, "zekes_convergence"),
                ZEKES_FILL);
        // 炽天使之拥——被动「应急护盾」（右侧）
        register("seraphs_embrace", Column.RIGHT,
                ModItems.SERAPHS_EMBRACE::get,
                p -> CuriosGearWear.isWearing(p, "seraphs_embrace"),
                SERAPH_FILL);
        // 冬之誓——被动「永恒」（右侧）
        register("fimbulwinter", Column.RIGHT,
                ModItems.FIMBULWINTER::get,
                p -> CuriosGearWear.isWearing(p, "fimbulwinter"),
                FIMBULWINTER_FILL);
        // 守护天使——被动「重生」（右侧）
        register("guardian_angel", Column.RIGHT,
                ModItems.GUARDIAN_ANGEL::get,
                p -> CuriosGearWear.isWearing(p, "guardian_angel"),
                GA_FILL);
        // 海克斯科技枪刃——主动技「闪电弹球」（左侧，跟其他主动技同列）
        register("gunblade", Column.LEFT,
                ModItems.HEXTECH_GUNBLADE::get,
                p -> CuriosGearWear.isWearing(p, "hextech_gunblade"),
                GUNBLADE_FILL);
        // 海克斯科技火箭腰带——主动技「魔法弹突进」（右侧，跟其他被动同列）
        register("rocketbelt", Column.RIGHT,
                ModItems.HEXTECH_ROCKETBELT::get,
                p -> CuriosGearWear.isWearing(p, "hextech_rocketbelt"),
                ROCKETBELT_FILL);
        // 挺进破坏者——主动技「破阵冲击波」
        register("shockwave", Column.RIGHT,
                ModItems.STRIDEBREAKER::get,
                p -> CuriosGearWear.isWearing(p, "stridebreaker"),
                SHOCKWAVE_FILL);
    }

    private SkillCooldownHud() {
    }

    /** 由冷却同步包在主线程调用：按技能 id 开始/重置冷却计时（未知 id 静默忽略）。 */
    public static void markTriggered(String skillId, int ticks) {
        markTriggered(skillId, ticks, false);
    }

    /** 由冷却同步包在主线程调用（debug 置真时无视可见条件强制显示冷却条）。 */
    public static void markTriggered(String skillId, int ticks, boolean debug) {
        Entry entry = ENTRIES.get(skillId);
        if (entry != null) {
            entry.mark(ticks, debug);
        }
    }

    @SubscribeEvent
    public static void onRenderOverlay(RenderGuiOverlayEvent.Post event) {
        if (event.getOverlay() != VanillaGuiOverlay.AIR_LEVEL.type()) {
            return;
        }
        Minecraft mc = Minecraft.getInstance();
        if (mc.options.hideGui || mc.level == null) {
            return;
        }
        Player player = mc.player;
        if (player == null || player.getVehicle() != null) {
            return;
        }
        if (!(mc.gui instanceof ForgeGui gui) || !gui.shouldDrawSurvivalElements()) {
            return;
        }
        if (!ClientFxConfig.SKILL_BAR_ENABLED.get()) {
            return;
        }
        ClientLevel world = mc.level;

        // 当前已占用的行数（0 号在最底部，多根向上堆叠）
        int rowCount = 0;
        for (Entry entry : ENTRIES.values()) {
            if (entry.level == null) {
                continue;
            }
            // 跨世界/跨维度：冷却记录作废
            if (!entry.tracks(world)) {
                entry.clear();
                continue;
            }
            // 已卸下对应装备 / 处于让位场景则本轮不画（计时保留，重新满足条件后可续显）
            if (!entry.visible.test(player)) {
                continue;
            }

            long now = world.getGameTime();
            long elapsed = Math.max(0, now - entry.triggerTick);
            float remaining = entry.cooldownTicks - elapsed - event.getPartialTick();
            if (remaining <= 0.0F) {
                entry.clear();
                continue;
            }
            // 调试通道激活期间无视常规可见条件（不要求佩戴对应装备、水下不让位）
            boolean debugActive = entry.debugUntilTick > now;
            if (!debugActive && !entry.visible.test(player)) {
                continue;
            }

            renderEntry(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight(), entry, remaining, rowCount++);
        }
    }

    private static void renderEntry(GuiGraphics graphics, int screenWidth, int screenHeight,
                                    Entry entry, float remaining, int row) {
        // 锚点：快捷栏右缘（屏幕中线右 91px），条组左缘再让出 HOTBAR_RIGHT_GAP
        // xOffset 正值 = 向屏幕右侧（外侧）推、负值 = 向快捷栏收
        int xLeft = screenWidth / 2 + 91 + HOTBAR_RIGHT_GAP + ClientFxConfig.SKILL_BAR_X_OFFSET.get();
        // 基准行在 0 号（与左侧护盾条平齐），多根时向上逐行堆叠；yOffset 正值整体上抬
        int y1 = screenHeight - HOTBAR_TOP_BASE - row * ROW_STRIDE
                - ClientFxConfig.SKILL_BAR_Y_OFFSET.get();
        int y0 = y1 - BAR_HEIGHT;
        int barX1 = xLeft + BAR_WIDTH;

        // RPG 斜面发光条（无数字计数：就绪瞬间整条点亮即天然提示）
        float ratio = Math.max(0.0F, Math.min(1.0F, remaining / entry.cooldownTicks));
        HudBars.drawBar(graphics, xLeft, barX1, y0, y1, ratio, entry.fillColor);

        // 最后 1 秒临界白闪：填充区逐渐叠白，提示「马上就好」
        if (remaining <= 20.0F) {
            int filled = Math.round((BAR_WIDTH - 2) * ratio);
            int alpha = (int) ((1.0F - remaining / 20.0F) * 0.55F * 255.0F);
            graphics.fill(xLeft + 1, y0 + 1, xLeft + 1 + filled, y1 - 1, (alpha << 24) | 0xFFFFFF);
        }
    }
}
