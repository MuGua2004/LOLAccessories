package com.example.lolaccessories.client.tooltip;

import com.example.lolaccessories.client.gearfx.MythicTextRenderer;
import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.screens.inventory.tooltip.ClientTooltipComponent;
import net.minecraft.client.renderer.MultiBufferSource;
import org.joml.Matrix4f;

/**
 * 神话装备流光标题的客户端渲染：委托 {@link MythicTextRenderer}
 * 逐字符绘制「HSL 流光 + 呼吸亮度 + 暗紫描边」的装备名。
 */
public class ClientMythicNameTooltip implements ClientTooltipComponent {

    private static final int HEIGHT = 10;

    private final MythicNameTooltip data;

    public ClientMythicNameTooltip(MythicNameTooltip data) {
        this.data = data;
    }

    @Override
    public int getHeight() {
        return HEIGHT;
    }

    @Override
    public int getWidth(Font font) {
        return MythicTextRenderer.flowingTextWidth(font, data.text());
    }

    @Override
    public void renderText(Font font, int x, int y, Matrix4f matrix,
                           MultiBufferSource.BufferSource buffers) {
        MythicTextRenderer.drawFlowingText(font, data.text(), x, y, matrix, buffers,
                System.currentTimeMillis(), data.hue(), data.sat());
    }
}
