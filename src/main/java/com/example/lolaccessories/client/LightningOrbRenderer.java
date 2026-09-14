package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.entity.LightningOrbEntity;
import com.example.lolaccessories.init.ModEntityTypes;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.entity.EntityRenderer;
import net.minecraft.client.renderer.entity.EntityRendererProvider;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.EntityRenderersEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;

/**
 * 闪电弹球（海克斯科技枪刃）的客户端渲染器：复用 {@code echo_orb.png} 的径向柔光圆点纹理，
 * 仅把 RGB 改成电蓝/青色，画成「外层电蓝光晕 → 电蓝球体 → 亮白核心」的自发光闪电球，
 * 始终正对相机（billboard）。
 */
public class LightningOrbRenderer extends EntityRenderer<LightningOrbEntity> {

    private static final ResourceLocation ORB_TEXTURE =
            new ResourceLocation(LOLAccessories.MOD_ID, "textures/entity/echo_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(ORB_TEXTURE);

    public LightningOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(LightningOrbEntity entity) {
        return ORB_TEXTURE;
    }

    @Override
    public void render(LightningOrbEntity orb, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        poseStack.translate(0.0D, orb.getBbHeight() * 0.5D, 0.0D);
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer vertexConsumer = buffer.getBuffer(RENDER_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        // 三层叠出“电蓝发光球”的效果（从外到内画）
        glowQuad(vertexConsumer, matrix, 0.62F, 0.45F, 0.75F, 1.0F, 0.22F); // 外层电蓝光晕
        glowQuad(vertexConsumer, matrix, 0.30F, 0.60F, 0.85F, 1.0F, 0.85F); // 电蓝球体本体
        glowQuad(vertexConsumer, matrix, 0.13F, 0.92F, 0.97F, 1.0F, 0.95F);  // 亮白核心

        poseStack.popPose();
    }

    private static void glowQuad(VertexConsumer vertexConsumer, Matrix4f matrix, float half,
                                 float r, float g, float b, float a) {
        vertex(vertexConsumer, matrix, -half, half, r, g, b, a, 0.0F, 0.0F);
        vertex(vertexConsumer, matrix, half, half, r, g, b, a, 1.0F, 0.0F);
        vertex(vertexConsumer, matrix, half, -half, r, g, b, a, 1.0F, 1.0F);
        vertex(vertexConsumer, matrix, -half, -half, r, g, b, a, 0.0F, 1.0F);
        vertex(vertexConsumer, matrix, -half, -half, r, g, b, a, 0.0F, 1.0F);
        vertex(vertexConsumer, matrix, half, -half, r, g, b, a, 1.0F, 1.0F);
        vertex(vertexConsumer, matrix, half, half, r, g, b, a, 1.0F, 0.0F);
        vertex(vertexConsumer, matrix, -half, half, r, g, b, a, 0.0F, 0.0F);
    }

    private static void vertex(VertexConsumer vertexConsumer, Matrix4f matrix, float x, float y,
                               float r, float g, float b, float a, float u, float v) {
        vertexConsumer.vertex(matrix, x, y, 0.0F)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    /** 把 {@link LightningOrbEntity} 的渲染器注册到客户端（仅在客户端加载）。 */
    @Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientRegistrar {

        private ClientRegistrar() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntityTypes.LIGHTNING_ORB.get(), LightningOrbRenderer::new);
            // 兜底：ECHO_ORB / HEARTSTEEL_MARK 原本只在 EchoOrbRenderer 内注册，若该注册点未生效，
            // 对应实体在客户端渲染时 EntityRenderer 为 null 会直接崩溃。在此统一兜底注册，确保渲染器存在。
            event.registerEntityRenderer(ModEntityTypes.ECHO_ORB.get(), EchoOrbRenderer::new);
            event.registerEntityRenderer(ModEntityTypes.HEARTSTEEL_MARK.get(), HeartsteelMarkRenderer::new);
        }
    }
}
