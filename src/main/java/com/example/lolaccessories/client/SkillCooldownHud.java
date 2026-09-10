package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.compat.CuriosGearWear;
import com.example.lolaccessories.init.ModItems;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientLevel;
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
 * <p>同一列支持<b>多根同时出现</b>：列内每根进度条自基准行（0 号）向上逐行堆叠，后续
 * 新增主动技能只需在 {@link #ENTRIES} 注册里加一行即可，无需再写新的 HUD 类。</p>
 *
 * <p>布局锚点与原版图标行对齐，左右各一列：左列基准为原版盔甲条顶行上方，右列基准为
 * 原版食物条顶行上方。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class SkillCooldownHud {

    /** 装备图标展示边长：与进度条同宽/同厚（原 16 → 12 → 4），贴合条身、可接受模糊。 */
    private static final int ICON_SIZE = 4;
    /** 图标与进度条之间的空隙。 */
    private static final int ICON_GAP = 2;
    /** 单根进度条宽度。 */
    private static final int BAR_WIDTH = 80;
    /** 进度条高度（调扁：原 6 → 4，图标与它等宽）。 */
    private static final int BAR_HEIGHT = 4;
    /** 进度条底边距下方原版图标行顶部的空隙（图标已与条同高，不再下探蹭图标）。 */
    private static final int BAR_GAP = 3;
    /** 同一列多根冷却条之间的纵向节距（条/图标组高 6 + 4px 间隔）。 */
    private static final int ROW_STRIDE = 10;
    /** 进度条近中线端距屏幕中线的距离（左右两列镜像对称）。 */
    private static final int CENTER_NEAR_OFFSET = 11;
    /** 左列基准：原版盔甲条顶行距屏幕底部的距离。 */
    private static final int LEFT_BASE_OFFSET = 49;
    /** 右列基准：原版食物条顶行距屏幕底部的距离。 */
    private static final int RIGHT_BASE_OFFSET = 39;

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
    /** 图标底槽色（AARRGGBB），半透明黑。 */
    private static final int ICON_BACKGROUND = 0x66000000;
    /** 图标描边色（AARRGGBB）。 */
    private static final int BORDER_COLOR = 0xFF303030;

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
            Minecraft mc = Minecraft.getInstance();
            ClientLevel world = mc.level;
            if (world == null) {
                return;
            }
            level = world;
            triggerTick = world.getGameTime();
            cooldownTicks = Math.max(1, ticks);
        }

        void clear() {
            level = null;
            triggerTick = -1;
            cooldownTicks = 0;
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
    }

    private SkillCooldownHud() {
    }

    /** 由冷却同步包在主线程调用：按技能 id 开始/重置冷却计时（未知 id 静默忽略）。 */
    public static void markTriggered(String skillId, int ticks) {
        Entry entry = ENTRIES.get(skillId);
        if (entry != null) {
            entry.mark(ticks);
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
        ClientLevel world = mc.level;

        // 各列当前已占用的行数（0 号在最靠近基准行的底部）
        int[] columnRows = new int[Column.values().length];
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

            int row = columnRows[entry.column.ordinal()]++;
            renderEntry(event.getGuiGraphics(), event.getWindow().getGuiScaledWidth(),
                    event.getWindow().getGuiScaledHeight(), entry, remaining, row);
        }
    }

    private static void renderEntry(GuiGraphics graphics, int screenWidth, int screenHeight,
                                    Entry entry, float remaining, int row) {
        boolean left = entry.column == Column.LEFT;
        // 进度条近中线端：左侧列停在中线左边，右侧列停在中线右边
        int near = screenWidth / 2 + (left ? -CENTER_NEAR_OFFSET : CENTER_NEAR_OFFSET);
        int x0;
        int x1;
        if (left) {
            x0 = near - BAR_WIDTH;
            x1 = near;
        } else {
            x0 = near;
            x1 = near + BAR_WIDTH;
        }
        int baseOffset = left ? LEFT_BASE_OFFSET : RIGHT_BASE_OFFSET;
        // 基准行在 0 号，多根时向上逐行堆叠
        int y1 = screenHeight - baseOffset - BAR_GAP - row * ROW_STRIDE;
        int y0 = y1 - BAR_HEIGHT;

        // 进度条前端（左端）绘制对应装备图标，与进度条垂直居中
        int iconX = x0 - ICON_SIZE - ICON_GAP;
        int iconY = y0 - (ICON_SIZE - BAR_HEIGHT) / 2;
        renderIcon(graphics, iconX, iconY, entry.item.get());

        float ratio = remaining / entry.cooldownTicks;
        renderBar(graphics, x0, x1, y0, y1, ratio, entry.fillColor);
    }

    /** 画一根进度条（1px 深色描边 + 半透明底槽 + 按剩余比例缩短的填充，空=可再次触发）。 */
    private static void renderBar(GuiGraphics graphics, int x0, int x1, int y0, int y1,
                                  float ratio, int fillColor) {
        graphics.fill(x0 - 1, y0 - 1, x1 + 1, y0, BORDER_COLOR);
        graphics.fill(x0 - 1, y1, x1 + 1, y1 + 1, BORDER_COLOR);
        graphics.fill(x0 - 1, y0 - 1, x0, y1 + 1, BORDER_COLOR);
        graphics.fill(x1, y0 - 1, x1 + 1, y1 + 1, BORDER_COLOR);
        graphics.fill(x0, y0, x1, y1, ICON_BACKGROUND);

        int filled = Math.round((x1 - x0) * Math.max(0.0F, Math.min(1.0F, ratio)));
        if (filled > 0) {
            graphics.fill(x0, y0, x0 + filled, y1, fillColor);
        }
    }

    /** 在指定位置绘制缩小后的装备图标（深色底槽 + 1px 描边 + 缩放后的物品本体）。 */
    private static void renderIcon(GuiGraphics graphics, int x, int y, Item item) {
        graphics.fill(x - 1, y - 1, x + ICON_SIZE + 1, y, BORDER_COLOR);
        graphics.fill(x - 1, y + ICON_SIZE, x + ICON_SIZE + 1, y + ICON_SIZE + 1, BORDER_COLOR);
        graphics.fill(x - 1, y - 1, x, y + ICON_SIZE + 1, BORDER_COLOR);
        graphics.fill(x + ICON_SIZE, y - 1, x + ICON_SIZE + 1, y + ICON_SIZE + 1, BORDER_COLOR);
        graphics.fill(x, y, x + ICON_SIZE, y + ICON_SIZE, ICON_BACKGROUND);

        // 物品本体按 ICON_SIZE/16 缩放绘制；每次渲染时再取 ItemStack，避免在类装载期缓存（彼时物品未注册）
        graphics.pose().pushPose();
        graphics.pose().translate(x, y, 0);
        float scale = ICON_SIZE / 16.0F;
        graphics.pose().scale(scale, scale, 1.0F);
        graphics.renderItem(new ItemStack(item), 0, 0);
        graphics.pose().popPose();
    }
}
