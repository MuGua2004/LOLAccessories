package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.client.gearfx.MythicTextRenderer;
import com.example.lolaccessories.client.MythicHueCache;
import com.example.lolaccessories.client.tooltip.MythicNameTooltip;
import com.example.lolaccessories.init.ModItemTags;
import com.mojang.datafixers.util.Either;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;
import net.minecraft.world.inventory.tooltip.TooltipComponent;
import net.minecraft.world.item.ItemStack;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.api.distmarker.OnlyIn;
import net.minecraftforge.client.event.RenderTooltipEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;

import java.util.List;

/**
 * 传说 / 神话装备 tooltip 钩子：
 * <ul>
 *   <li>神话（tier4）首行替换为流光字组件；</li>
 *   <li>传说（tier3）/ 神话（tier4）的描述行（Style=灰/青/淡紫）自动给
 *       数字、单位与核心机制词套上关键词色（§6 金数 / §b 青单位 / §d 紫机制 / §c 红生命等）。</li>
 * </ul>
 */
@OnlyIn(Dist.CLIENT)
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT)
public final class ClientMythicTooltipEvents {

    private ClientMythicTooltipEvents() {
    }

    @SubscribeEvent
    public static void onGatherTooltipComponents(RenderTooltipEvent.GatherComponents event) {
        ItemStack stack = event.getItemStack();
        if (stack.isEmpty()) {
            return;
        }
        List<Either<FormattedText, TooltipComponent>> elements = event.getTooltipElements();
        if (elements.isEmpty()) {
            return;
        }
        boolean mythic = stack.is(ModItemTags.TIER4);
        boolean legend = stack.is(ModItemTags.TIER3);
        // 神话首行：流光字（色调随装备贴图主色）
        if (mythic) {
            Either<FormattedText, TooltipComponent> first = elements.get(0);
            first.left().ifPresent(line -> {
                String text = line.getString();
                if (text != null && !text.isBlank()) {
                    float[] tint = MythicHueCache.getTint(stack.getItem());
                    elements.set(0, Either.right(new MythicNameTooltip(text, tint[0], tint[1])));
                }
            });
        }
        // 传说 / 神话：描述行关键词着色（跳过属性行——其父 Style 为绿/红/金，不是灰系）
        if (mythic || legend) {
            colorizeDescKeywords(elements);
        }
    }

    /** 对 Style=灰/青/淡紫 的描述 left 元素套上 § 关键词色，保留原底色。 */
    private static void colorizeDescKeywords(List<Either<FormattedText, TooltipComponent>> elements) {
        for (int i = 0; i < elements.size(); i++) {
            Either<FormattedText, TooltipComponent> e = elements.get(i);
            if (e.right().isPresent()) {
                continue;
            }
            FormattedText ft = e.left().orElse(null);
            if (!(ft instanceof Component comp)) {
                continue;
            }
            Style st = comp.getStyle();
            if (st.getColor() == null) {
                continue;
            }
            int c = st.getColor().getValue();
            // 仅作用于被动/主动描述行：GRAY(0x555555) / AQUA(0x55FFFF) / LIGHT_PURPLE(0xFF55FF)
            if (c != 0x555555 && c != 0x55FFFF && c != ChatFormatting.LIGHT_PURPLE.getColor()) {
                continue;
            }
            String raw = comp.getString();
            if (raw == null || raw.isBlank() || raw.indexOf('§') >= 0) {
                continue;
            }
            String colored = MythicTextRenderer.colorizeDescKeywords(raw);
            if (colored.equals(raw)) {
                continue;
            }
            elements.set(i, Either.left(Component.literal(colored).withStyle(st)));
        }
    }
}
