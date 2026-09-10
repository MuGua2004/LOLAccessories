package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.entity.HeartsteelMarkEntity;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import org.joml.Matrix4f;

/**
 * 心之钢吞食印记渲染：三圈金色法阵悬浮在目标头顶、平放缓旋、逐层扩展成熟
 * （3 秒 = 60 tick，圈 i 在进度过 i/3 后从中心展开到自身半径）。法阵半径由
 * 实体按目标碰撞箱宽度同步，自动适配怪物体型。
 */
public class HeartsteelMarkRenderer extends EntityRenderer<HeartsteelMarkEntity> {

    /** 法阵贴图（复用神话装备的圆形法阵，128×128 中心镂空）。 */
    private static final ResourceLocation TEXTURE =
            new ResourceLocation(LOLAccessories.MOD_ID, "textures/fx/sigil_circle.png");

    /** 法阵顶点色：心之钢金。 */
    private static final float R = 1.0F;
    private static final float G = 0.86F;
    private static final float B = 0.45F;

    private static boolean LOGGED_RENDER;

    public HeartsteelMarkRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(HeartsteelMarkEntity entity) {
        return TEXTURE;
    }

    @Override
    public void render(HeartsteelMarkEntity entity, float entityYaw, float partialTick,
                       PoseStack poseStack, MultiBufferSource buffer, int packedLight) {
        float progress = Mth.clamp((entity.tickCount + partialTick) / (float) HeartsteelMarkEntity.MATURE_TICKS, 0.0F, 1.0F);
        float radius = entity.getMarkRadius();
        if (!LOGGED_RENDER) {
            LOGGED_RENDER = true;
            LOLAccessories.LOGGER.info("[心之钢] 印记渲染器已被调用（实体已同步到客户端），半径={} 进度={}", radius, progress);
        }
        if (radius <= 0.01F || progress <= 0.0F) {
            return;
        }
        poseStack.pushPose();
        // 竖立：billboard 面向摄像机（始终正对观察者，看得最清）
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());
        // 面内缓慢旋转（每秒约 86°）
        poseStack.mulPose(Axis.ZP.rotationDegrees((entity.tickCount + partialTick) * 1.44F));

        VertexConsumer vc = buffer.getBuffer(RenderType.entityCutoutNoCull(TEXTURE));
        Matrix4f matrix = poseStack.last().pose();
        // 三圈逐层扩展：圈 i（内→外）在进度 i/3 后出现，从 0 扩到自身满径
        for (int i = 0; i < 3; i++) {
            float ringProgress = Mth.clamp(progress * 3.0F - i, 0.0F, 1.0F);
            if (ringProgress <= 0.0F) {
                continue;
            }
            float ringRadius = radius * (i + 1) / 3.0F * ringProgress;
            float alpha = Math.min(1.0F, ringProgress * 2.0F) * (0.55F + 0.15F * i);
            drawQuad(vc, matrix, ringRadius, alpha);
        }
        poseStack.popPose();
        super.render(entity, entityYaw, partialTick, poseStack, buffer, packedLight);
    }

    /** 画一张以旋转轴为中心、边长 2×radius 的双面法阵 quad。 */
    private static void drawQuad(VertexConsumer vc, Matrix4f matrix, float radius, float alpha) {
        // 正面
        vc.vertex(matrix, -radius, -radius, 0).color(R, G, B, alpha)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, 1).endVertex();
        vc.vertex(matrix, radius, -radius, 0).color(R, G, B, alpha)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, 1).endVertex();
        vc.vertex(matrix, radius, radius, 0).color(R, G, B, alpha)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, 1).endVertex();
        vc.vertex(matrix, -radius, radius, 0).color(R, G, B, alpha)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, 1).endVertex();
        // 背面（从下往上看也可见）
        vc.vertex(matrix, -radius, radius, 0).color(R, G, B, alpha)
                .uv(0.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, -1).endVertex();
        vc.vertex(matrix, radius, radius, 0).color(R, G, B, alpha)
                .uv(1.0F, 1.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, -1).endVertex();
        vc.vertex(matrix, radius, -radius, 0).color(R, G, B, alpha)
                .uv(1.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, -1).endVertex();
        vc.vertex(matrix, -radius, -radius, 0).color(R, G, B, alpha)
                .uv(0.0F, 0.0F).overlayCoords(OverlayTexture.NO_OVERLAY).uv2(0xF000F0)
                .normal(0, 0, -1).endVertex();
    }
}
