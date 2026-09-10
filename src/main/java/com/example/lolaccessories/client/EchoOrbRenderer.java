package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.entity.EchoOrbEntity;
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
 * 回声光球的客户端渲染器。
 *
 * <p>用原版粒子图集里的径向渐变圆点 {@code generic_0} 当纹理，配上半透明渲染 + 全亮光照，
 * 画成一个由“外层光晕 → 紫色光球 → 亮白核心”三层叠出的自发光紫色光球，始终正对相机
 * （billboard），移动时就是一颗明亮的紫色能量球，而不是淡薄的粒子点。</p>
 */
public class EchoOrbRenderer extends EntityRenderer<EchoOrbEntity> {

    /** 纹理：自带的径向柔光圆点（白心透明柔边），画成光球时中心亮、边缘柔。 */
    private static final ResourceLocation ORB_TEXTURE =
            new ResourceLocation(LOLAccessories.MOD_ID, "textures/entity/echo_orb.png");
    private static final RenderType RENDER_TYPE = RenderType.entityTranslucent(ORB_TEXTURE);

    public EchoOrbRenderer(EntityRendererProvider.Context context) {
        super(context);
    }

    @Override
    public ResourceLocation getTextureLocation(EchoOrbEntity entity) {
        return ORB_TEXTURE;
    }

    @Override
    public void render(EchoOrbEntity orb, float yaw, float partialTick, PoseStack poseStack,
                       MultiBufferSource buffer, int packedLight) {
        poseStack.pushPose();
        // 让光球中心对准弹体中心
        poseStack.translate(0.0D, orb.getBbHeight() * 0.5D, 0.0D);
        // 始终正对相机
        poseStack.mulPose(this.entityRenderDispatcher.cameraOrientation());

        VertexConsumer vertexConsumer = buffer.getBuffer(RENDER_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        // 三层叠出“发光紫球”的效果（从外到内画）
        glowQuad(vertexConsumer, matrix, 0.62F, 0.72F, 0.42F, 1.0F, 0.22F); // 外层紫色光晕
        glowQuad(vertexConsumer, matrix, 0.30F, 0.86F, 0.48F, 1.0F, 0.85F); // 紫色光球本体
        glowQuad(vertexConsumer, matrix, 0.13F, 1.0F, 0.82F, 1.0F, 0.95F);  // 亮白核心

        poseStack.popPose();
    }

    /**
     * 画一个以原点为中心、半边长 {@code half} 的发光方块。为避免背面剔除把某侧视角下的光球
     * 整块裁掉，正反两面的顶点环绕方向各画一遍（两侧看都是光球）。
     */
    private static void glowQuad(VertexConsumer vertexConsumer, Matrix4f matrix, float half,
                                 float r, float g, float b, float a) {
        // 正面（CCW，法线朝 +Z）
        vertex(vertexConsumer, matrix, -half, half, r, g, b, a, 0.0F, 0.0F);
        vertex(vertexConsumer, matrix, half, half, r, g, b, a, 1.0F, 0.0F);
        vertex(vertexConsumer, matrix, half, -half, r, g, b, a, 1.0F, 1.0F);
        vertex(vertexConsumer, matrix, -half, -half, r, g, b, a, 0.0F, 1.0F);
        // 背面（反向环绕）
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
                // entityTranslucent 用的是 NEW_ENTITY 顶点格式：写完 position/color/uv/overlay/uv2 后
                // 还必须补一个法线要素，否则 endVertex 会报 "Not filled all elements of the vertex" 直接崩。
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    /** 把 {@link EchoOrbEntity} 的渲染器注册到客户端（仅在客户端加载）。 */
    @Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID,
            bus = Mod.EventBusSubscriber.Bus.MOD, value = Dist.CLIENT)
    public static final class ClientRegistrar {

        private ClientRegistrar() {
        }

        @SubscribeEvent
        public static void registerRenderers(EntityRenderersEvent.RegisterRenderers event) {
            event.registerEntityRenderer(ModEntityTypes.ECHO_ORB.get(), EchoOrbRenderer::new);
        }
    }
}
