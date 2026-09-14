package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import com.example.lolaccessories.config.ClientFxConfig;
import com.example.lolaccessories.networking.FxKind;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.blaze3d.vertex.VertexConsumer;
import com.mojang.math.Axis;
import net.minecraft.client.Camera;
import net.minecraft.client.Minecraft;
import net.minecraft.client.multiplayer.ClientLevel;
import net.minecraft.client.renderer.LightTexture;
import net.minecraft.client.renderer.MultiBufferSource;
import net.minecraft.client.renderer.RenderType;
import net.minecraft.client.renderer.culling.Frustum;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.util.Mth;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.entity.player.Player;
import net.minecraftforge.api.distmarker.Dist;
import net.minecraftforge.client.event.RenderLevelStageEvent;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.client.event.RenderPlayerEvent;
import net.minecraftforge.eventbus.api.SubscribeEvent;
import net.minecraftforge.fml.common.Mod;
import org.joml.Matrix4f;
import org.joml.Vector3f;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * 装备视觉特效的客户端渲染器。
 *
 * <p>在玩家/生物渲染完毕后叠加绘制。每件装备的特效都有自己标志性的形态，彼此不重复：</p>
 * <ul>
 *   <li>战歌·鼓舞——头顶悬浮的「竖立旋转圆法阵」符盘（billboard 符文轮盘，外沿卫星公转）；</li>
 *   <li>实现器·法力成真——脚下「六边形法阵 + 六芒星」与升腾流光；</li>
 *   <li>班德尔音管——沿身体向上扩散的「旋律音浪环」；</li>
 *   <li>猎魔人弩箭——周身「赤焰箭雨」（环绕能量箭簇 + 双层对旋火星 + 战火环）；</li>
 *   <li>败魔 / 原生质护带——常驻半透明球壳罩（带菲涅尔边缘光与柔和脉动）；</li>
 *   <li>黯炎火炬——克制版妖火缠身（多怪自适应收敛）。</li>
 * </ul>
 *
 * <p>所有发光元均采用高分辨率程序化柔光贴图（替代旧的低清“马赛克”光球），线条几何由
 * 纯顶点构成，不依赖原版粒子，运行时开销可控。</p>
 */
@Mod.EventBusSubscriber(modid = LOLAccessories.MOD_ID, value = Dist.CLIENT, bus = Mod.EventBusSubscriber.Bus.FORGE)
public final class GearFxRenderer {

    /** 通用发光元：高分辨率径向柔光贴图（白芯透明柔边），供所有光点/光晕采样。 */
    private static final ResourceLocation GLOW_TEXTURE =
            new ResourceLocation(LOLAccessories.MOD_ID, "textures/entity/echo_orb.png");
    private static final RenderType GLOW_TYPE = RenderType.entityTranslucent(GLOW_TEXTURE);
    /** 战歌符盘：LoL 风格金色符文轮盘贴图（竖直 billboard 自旋，一个四边形即一面法阵）。 */
    private static final ResourceLocation SIGIL_TEXTURE =
            new ResourceLocation(LOLAccessories.MOD_ID, "textures/fx/sigil_circle.png");
    private static final RenderType SIGIL_TYPE = RenderType.entityTranslucent(SIGIL_TEXTURE);

    /**
     * 说明：原先的线条绘制使用 {@code RenderType.lines()}（3 元素顶点 POSITION_COLOR_NORMAL）。
     * 该类型与光点/护罩共用的 {@code entityTranslucent}（6 元素顶点）在 MultiBufferSource 的
     * 共享回退缓冲里混写，会触发 BufferBuilder 的 "Not filled all elements of the vertex" 崩溃。
     * 因此本类不再申请任何 LINES 类型缓冲；所有线元一律用与光点一致的 {@link #GLOW_TYPE}
     * 完整顶点格式，画成相机朝向/贴地式的柔光描边细带。
     */

    /** 法阵 / 光环通用配色：主色=外圈线，强调色=内圈/弧带，芯色=光点与光晕。 */
    private record FxPalette(float mr, float mg, float mb,
                             float ar, float ag, float ab,
                             float cr, float cg, float cb) {
    }

    /** 战歌·鼓舞：暖金圣光（主色/强调/芯色，均偏亮白，保证线条视觉重量）。 */
    private static final FxPalette PAL_INSPIRE =
            new FxPalette(1.00F, 0.94F, 0.55F, 1.00F, 0.72F, 0.15F, 1.00F, 1.00F, 0.92F);
    /** 黯炎灼烧：明亮紫黯火（高饱和亮紫，在暗色场景下依然醒目）。 */
    private static final FxPalette PAL_TORCH =
            new FxPalette(0.80F, 0.40F, 1.00F, 0.60F, 0.18F, 0.95F, 1.00F, 0.95F, 1.00F);

    private GearFxRenderer() {
    }

    /**
     * 本帧内已经由原版 LivingEntityRenderer 系渲染事件（RenderLivingEvent.Post /
     * RenderPlayerEvent.Post）处理过的实体。
     *
     * <p>原版与多数模组生物走 LivingEntityRenderer，特效由上面的事件锚定；但部分模组生物
     * （典型如 Iron's Spellbooks 的全部生物——其渲染器继承 GeckoLib 的 GeoEntityRenderer，
     * 父类是 EntityRenderer，根本不经过 LivingEntityRenderer）不会触发这两个事件。
     * 因此还需要在 {@link #onRenderLevelStage} 的世界兜底阶段把这类“漏网”目标补画出来，
     * 本集合用于去重，避免原版路径的生物被重复绘制。</p>
     */
    private static final Set<UUID> FX_TARGETS_DRAWN_THIS_FRAME = new HashSet<>();

    @SubscribeEvent
    public static void onRenderLivingPost(RenderLivingEvent.Post<?, ?> event) {
        // 玩家单独走 RenderPlayerEvent，避免与可能存在的子类事件重复绘制
        if (event.getEntity() instanceof Player) {
            return;
        }
        FX_TARGETS_DRAWN_THIS_FRAME.add(event.getEntity().getUUID());
        renderFor(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), event.getPartialTick());
    }

    @SubscribeEvent
    public static void onRenderPlayerPost(RenderPlayerEvent.Post event) {
        FX_TARGETS_DRAWN_THIS_FRAME.add(event.getEntity().getUUID());
        renderFor(event.getEntity(), event.getPoseStack(), event.getMultiBufferSource(), event.getPartialTick());
    }

    /**
     * 世界级兜底渲染：在实体全部渲染完的 AFTER_ENTITIES 阶段，把“有特效窗口、但本帧没有
     * 被 LivingEntityRenderer 系事件绘制过”的目标补画出来，使特效对 GeckoLib 等自定义
     * 渲染器的模组生物同样生效。
     *
     * <p>坐标语义与实体渲染一致（世界坐标相对相机平移），绘制内容复用
     * {@link #renderFor}，因此与原版路径的特效观感/时序完全一致，不会产生重复绘制。</p>
     */
    @SubscribeEvent
    public static void onRenderLevelStage(RenderLevelStageEvent event) {
        if (event.getStage() != RenderLevelStageEvent.Stage.AFTER_ENTITIES) {
            return;
        }
        try {
            Minecraft mc = Minecraft.getInstance();
            ClientLevel level = mc.level;
            if (level == null || !ClientFxConfig.ENABLED.get()
                    || (!ActiveGearFx.hasActiveFx() && !ActiveSpotFx.hasSpots())) {
                return;
            }
            Camera camera = event.getCamera();
            Frustum frustum = event.getFrustum();
            if (camera == null || frustum == null) {
                return;
            }
            // 漏网目标 = 当前持有特效窗口、但本帧尚未经 LivingEntityRenderer 系事件绘制过的目标。
            Set<UUID> pending = new HashSet<>(ActiveGearFx.activeTargets());
            pending.removeAll(FX_TARGETS_DRAWN_THIS_FRAME);
            // 关键修复：pending 为空时不能在此 return。地点锚定特效（残疫·憎恨之雾、蜕生·死中新生
            // 等 FxSpotPacket）不走实体漏网目标，必须继续到下方 renderSpots 才会被绘制；否则 spot
            // 特效（紫圈 / 光球）永远不显示。下方“pending 为空且无 Spot”已由第 153 行兜底 return。
            // 第一人称下本机玩家不会触发 RenderPlayerEvent（躯干不渲染），其自身的装饰性特效
            // 不需要兜底，直接剔除，避免无谓的全量实体遍历。
            Player self = mc.player;
            if (self != null) {
                pending.remove(self.getUUID());
            }
            if (pending.isEmpty() && !ActiveSpotFx.hasSpots()) {
                return;
            }

            PoseStack poseStack = event.getPoseStack();
            MultiBufferSource buffer = mc.renderBuffers().bufferSource();
            float partialTick = event.getPartialTick();
            double camX = camera.getPosition().x;
            double camY = camera.getPosition().y;
            double camZ = camera.getPosition().z;

            for (Entity entity : level.entitiesForRendering()) {
                if (!(entity instanceof LivingEntity living) || living instanceof Player) {
                    continue;
                }
                if (!pending.contains(living.getUUID())) {
                    continue;
                }
                if (living.isRemoved() || !living.isAlive()) {
                    continue;
                }
                // 与原版实体渲染的剔除口径一致：不在视锥内（在身后/太远被裁掉）则不绘制。
                if (!frustum.isVisible(living.getBoundingBox())) {
                    continue;
                }
                if (living.distanceToSqr(camera.getPosition()) > 256.0D * 256.0D) {
                    continue;
                }
                poseStack.pushPose();
                // 坐标口径与原版实体渲染严格一致：LevelRenderer 把实体画在 partialTick 插值
                // 位置（xOld→x 之间），而非实体当前 tick 坐标。若兜底用未插值坐标，移动中的
                // 目标（尤其会走位的 ISS 法师/骑士/冰霜蜘蛛）特效会滞后身体半帧到一帧，
                // 观感上出现特效与身体错位。
                double ex = Mth.lerp((double) partialTick, living.xOld, living.getX());
                double ey = Mth.lerp((double) partialTick, living.yOld, living.getY());
                double ez = Mth.lerp((double) partialTick, living.zOld, living.getZ());
                poseStack.translate(ex - camX, ey - camY, ez - camZ);
                renderFor(living, poseStack, buffer, partialTick);
                poseStack.popPose();
            }

            // 地点锚定特效（救赎·降临的施法点法阵 / 圣光光柱）
            if (ActiveSpotFx.hasSpots()) {
                renderSpots(poseStack, buffer, level, partialTick, camX, camY, camZ);
            }
        } finally {
            // 每帧结算后清空，下一帧重新累计哪些实体已被事件绘制过。
            FX_TARGETS_DRAWN_THIS_FRAME.clear();
        }
    }

    private static void renderFor(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                   float partialTick) {
        // 客户端配置总开关：关闭时不再绘制任何叠加特效（含仍在窗口内的旧特效）
        if (!ClientFxConfig.ENABLED.get()) {
            return;
        }
        Collection<ActiveGearFx.ActiveFx> active = ActiveGearFx.getActive(entity.getUUID());
        if (active.isEmpty()) {
            return;
        }
        if (!entity.level().isClientSide) {
            return;
        }
        float age = entity.level().getGameTime() + partialTick;
        for (ActiveGearFx.ActiveFx fx : active) {
            renderFx(fx, entity, poseStack, buffer, age);
        }
    }

    private static void renderFx(ActiveGearFx.ActiveFx fx, LivingEntity entity, PoseStack poseStack,
                                   MultiBufferSource buffer, float age) {
        // 窗口淡入淡出系数 + 客户端配置的密度/亮度倍率（每个特效只读一次）
        float win = alphaWindow(fx);
        float density = ClientFxConfig.DENSITY.get().floatValue();
        float bright = ClientFxConfig.BRIGHTNESS.get().floatValue();
        switch (fx.kind) {
            case INSPIRE -> renderInspire(entity, poseStack, buffer, age, win, density, bright);
            case FANFARE -> renderFanfare(entity, poseStack, buffer, age, win, bright);
            case REALIZE -> renderRealize(entity, poseStack, buffer, age, win, density, bright);
            case BARRAGE -> renderBarrage(entity, poseStack, buffer, age, win, density, bright);
            case TORCH -> renderTorch(entity, poseStack, buffer, age, win, density, bright);
            case SHIELD_ROOKERN -> renderRookernShield(entity, poseStack, buffer, age, win, bright);
            case SHIELD_PROTOPLASM -> renderProtoplasmShield(entity, poseStack, buffer, age, win, bright);
            case SHIELD_SERAPH -> renderSeraphShield(entity, poseStack, buffer, age, win, bright);
            case SHIELD_FIMBULWINTER -> renderFimbulwinterShield(entity, poseStack, buffer, age, win, bright);
            case SHIELD_STERAK -> renderSterakShield(entity, poseStack, buffer, age, win, bright);
            case SHIELD_BLOODTHIRSTER -> renderBloodthirsterShield(entity, poseStack, buffer, age, win, bright);
            case SHIELD_MAW -> renderMawShield(entity, poseStack, buffer, age, win, bright);
            case SHIELD_BANSHEE -> renderBansheeShield(entity, poseStack, buffer, age, win, bright);
            case ZEKES_STORM -> renderZekesStorm(entity, poseStack, buffer, age, win, density, bright);
            case SUNFIRE_AEGIS -> renderSunfire(entity, poseStack, buffer, age, win, density, bright);
            case HEXPLATE_OVERDRIVE -> renderHexplateOverdrive(entity, poseStack, buffer, age, win, bright);
            case STEADFAST_AURA -> renderSteadfastAura(entity, poseStack, buffer, age, win, density, bright);
            case STORMSURGE_MARK -> renderStormsurgeMark(entity, poseStack, buffer, age, win, bright);
        }
    }

    /**
     * 窗口淡入淡出：前 0.3 秒平滑浮现、最后 0.5 秒平滑消隐，避免特效“啪”地出现/消失。
     */
    private static float alphaWindow(ActiveGearFx.ActiveFx fx) {
        if (fx.maxTicks <= 0) {
            return 1.0F;
        }
        float remaining = Math.max(0.0F, fx.remainingTicks);
        float fadeIn = Math.min(1.0F, (fx.maxTicks - remaining + 1.0F) / 6.0F);
        float fadeOut = Math.min(1.0F, remaining / 10.0F);
        return Math.min(fadeIn, fadeOut);
    }

    // ------------------------- 增幅类特效 ------------------------- //

    /**
     * 战歌·鼓舞（舒瑞娅的战歌）——「竖立旋转的圆法阵」。
     *
     * <p>与黯炎火炬“脚底火环 + 缠身火链”的形态彻底区分：本特效的核心是一面悬浮于目标头顶上方、
     * 始终竖直面对观察者（billboard）的暖金符文轮盘，绕视线轴缓缓自转；轮盘外沿另有卫星光点
     * 沿盘面轨道公转，脚下只留一团极淡暖光。符盘整体只画一个四边形，即便多名队友同时被鼓舞
     * 也不会带来明显开销。</p>
     */
    private static void renderInspire(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                      float age, float win, float density, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        float foot = Math.max(w, h * 0.16F);
        // 符盘视觉半径
        float discR = Math.max(foot * 1.15F, 0.92F);
        // 大体型放慢转速，避免视觉线速度太快
        float slow = 1.0F / (float) Math.sqrt(1.0F + discR * 0.4F);
        float spin = age * slow * 0.075F;
        float satSpin = age * slow * 0.16F;
        float discY = h + discR * 0.90F;

        float mr = hot(PAL_INSPIRE.mr()), mg = hot(PAL_INSPIRE.mg()), mb = hot(PAL_INSPIRE.mb());
        float cr = PAL_INSPIRE.cr(), cg = PAL_INSPIRE.cg(), cb = PAL_INSPIRE.cb();

        // ① 头顶竖直符盘：主视觉，符文轮盘自转
        drawSigilDisc(poseStack, buffer, 0.0F, discY, 0.0F, discR, spin,
                mr, mg, mb, 0.96F * fade);

        // ② 盘面外沿绕行的卫星光点（把“在旋转”这种动态直接呈现在盘面上）
        int sats = Math.max(4, Math.round(4.0F * density));
        float satR = discR * 1.18F;
        for (int i = 0; i < sats; i++) {
            float a = satSpin + i * Mth.TWO_PI / sats;
            drawPlaneGlow(poseStack, buffer, 0.0F, discY, 0.0F,
                    Mth.cos(a) * satR, Mth.sin(a) * satR, satR * 0.085F + 0.035F,
                    cr, cg, cb, 0.90F * fade);
        }

        // ③ 符盘下方一道细光冠，把符盘与本体“连”起来（轻量，不抢戏）
        float haloY = discY - discR * 0.34F;
        drawRing(poseStack, buffer, 0.0F, haloY, 0.0F, discR * 0.78F,
                0.0F, spin * 0.5F, 32, mr, mg, mb, 0.50F * fade);

        // ④ 盘缘向上升腾的几缕金尘
        int dust = Math.max(0, Math.round(5.0F * density));
        for (int i = 0; i < dust; i++) {
            float t = Mth.frac(age * 0.0035F + i / (float) Math.max(1, dust));
            float x = Mth.sin(t * 4.0F + i) * discR * 0.18F;
            float z = Mth.cos(t * 3.1F + i * 1.7F) * discR * 0.18F;
            float y = Mth.lerp(t, discY - discR * 0.25F, discY + discR * 0.60F);
            float env = Mth.sin(t * Mth.PI);
            float s = discR * 0.075F * (1.0F - 0.4F * t);
            drawSoftGlow(poseStack, buffer, x, y, z, s, s, s,
                    cr, cg, cb, 0.55F * env * fade);
        }

        // ⑤ 脚下极淡暖光（不做法阵、不铺圈），纯粹避免“空中符盘”与地面脱节
        float groundR = discR * 0.50F;
        drawSoftGlow(poseStack, buffer, 0.0F, 0.05F, 0.0F,
                groundR, groundR * 0.20F, groundR, 1.0F, 0.82F, 0.42F, 0.24F * fade);
    }

    /**
     * 班德尔音管·嘹亮旋律增幅——「旋律音浪环」。
     *
     * <p>三圈翠绿音波环自脚下依次生成、一边膨胀一边上升，划过身体后消失，营造“吹奏出的
     * 上升音浪”的感觉；中央胸口处始终保持一团淡淡的翠绿共鸣光。</p>
     */
    private static void renderFanfare(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                      float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float rMin = Math.max(foot * 0.45F, 0.32F);
        float rMax = Math.max(foot * 1.75F, 1.35F);
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        // 三波浪，每 40 tick 一圈：r 由小变大、y 由低升高，alpha 先强后弱
        for (int k = 0; k < 3; k++) {
            float t = Mth.frac(age / 40.0F + k / 3.0F);
            float p = 1.0F - (1.0F - t) * (1.0F - t); // ease-out：起步慢、升速渐快
            float radius = Mth.lerp(rMin, rMax, p);
            float y = Mth.lerp(h * 0.10F, h * 1.10F, p);
            float alpha = Mth.sin(t * Mth.PI);
            int seg = Math.max(20, Math.round(radius * 20.0F));
            drawCircleXZ(vc, matrix, y, radius, seg, 0.35F, 1.0F, 0.62F, 0.62F * alpha * fade);
        }

        // 胸口共鸣核心光 + 少量细碎“音符微尘”原地闪烁上飘
        float glowSize = Math.max(w, h * 0.35F) * 0.38F;
        drawSoftGlow(poseStack, buffer, 0.0F, h * 0.52F, 0.0F, glowSize, glowSize * 1.2F, glowSize,
                0.35F, 1.0F, 0.65F, 0.34F * fade);
        for (int i = 0; i < 3; i++) {
            float t = Mth.frac(age * 0.006F + i / 3.0F);
            float x = Mth.sin(t * 6.0F + i * 2.1F) * rMax * 0.30F;
            float z = Mth.cos(t * 5.0F + i) * rMax * 0.30F;
            float y = h * (0.35F + 0.65F * t);
            float s = glowSize * 0.10F;
            drawSoftGlow(poseStack, buffer, x, y, z, s, s, s,
                    0.45F, 1.0F, 0.72F, 0.45F * Mth.sin(t * Mth.PI) * fade);
        }
    }

    /**
     * 实现器·法力成真——脚下「六边形法阵 + 六芒星」。
     *
     * <p>实现器窗口只挂在佩戴者本人身上（不像灼烧 / 群体增幅那样可能同时命中多名实体），
     * 同屏目标数天然极少、没有“多怪糊屏”的风险，因此渲染特意不做多目标收敛，直接放足：
     * 外层正六边形法阵（紫）与反向旋转的内六边形（青）围出轮廓，中央双三角六芒星自转；
     * 六角顶点竖起能量棱线、顶端悬停白亮符点，阵外一圈紫青星尘公转，最外呼吸光晕上叠加
     * 两道周期性外扩的魔力涟漪让法阵“活”起来；阵心白核之上竖起细白内芯螺旋，再配三股
     * 紫青螺旋流光与上升光尘自阵面灌注进身体，把“法力成真”的充实感做足。元素全部为
     * 程序化柔光图元，单目标总量仍远低于原版粒子，不构成性能负担。</p>
     */
    private static void renderRealize(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                      float age, float win, float density, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float groundY = 0.04F;
        // 本体窗口特效同屏数量少，主阵半径给足（高于火炬收敛档的地面火环）
        float r0 = Math.max(foot * 2.05F, 1.70F);
        float slow = 1.0F / (float) Math.sqrt(1.0F + r0 * 0.4F);
        float spin = age * slow * 0.10F;

        float pr = 0.82F, pg = 0.30F, pb = 1.0F;      // 主色：亮紫
        float ar = 0.10F, ag = 0.86F, ab = 1.0F;      // 强调色：青色
        float hmr = hot(pr), hmg = hot(pg), hmb = hot(pb);
        float har = hot(ar), hag = hot(ag), hab = hot(ab);

        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        // ① 外六边形（主色紫）+ 内六边形（青色）反向旋转
        drawPolygonXZ(vc, matrix, groundY, r0, 6, spin, hmr, hmg, hmb, 0.92F * fade);
        drawPolygonXZ(vc, matrix, groundY, r0 * 0.80F, 6, -spin * 0.72F + Mth.PI / 6.0F,
                har, hag, hab, 0.78F * fade);

        // ② 六芒星：两个互错 60° 的等边三角形（提亮后为法阵中心图案）
        float rStar = r0 * 0.88F;
        drawPolygonXZ(vc, matrix, groundY, rStar, 3, spin * 0.45F, 1.0F, 1.0F, 1.0F, 0.60F * fade);
        drawPolygonXZ(vc, matrix, groundY, rStar, 3, spin * 0.45F + Mth.PI / 3.0F,
                1.0F, 1.0F, 1.0F, 0.60F * fade);

        // ③ 六角顶点能量棱线：阵力沿角点竖直上扬，顶端悬停白亮符点
        float pillarH = Math.max(h * 0.24F, 0.34F);
        float dotSize = r0 * 0.085F + 0.05F;
        for (int i = 0; i < 6; i++) {
            float a = spin + i * Mth.TWO_PI / 6.0F;
            float x = Mth.cos(a) * r0;
            float z = Mth.sin(a) * r0;
            line(vc, matrix, x, groundY + 0.02F, z, x, pillarH, z,
                    hmr, hmg, hmb, 0.42F * fade);
            drawSoftGlow(poseStack, buffer, x, pillarH, z, dotSize, dotSize, dotSize,
                    hmr, hmg, hmb, 0.95F * fade);
        }

        // ④ 每边中点的小刻度亮点（地面，贴合内层青色六边形的边）
        float midR = r0 * 0.80F;
        float innerRot = -spin * 0.72F + Mth.PI / 6.0F;
        for (int i = 0; i < 6; i++) {
            float a = innerRot + (i + 0.5F) * Mth.TWO_PI / 6.0F;
            drawSoftGlow(poseStack, buffer, Mth.cos(a) * midR, groundY + 0.03F, Mth.sin(a) * midR,
                    dotSize * 0.6F, dotSize * 0.6F, dotSize * 0.6F, har, hag, hab, 0.70F * fade);
        }

        // ⑤ 阵外星尘带：绕外沿公转的紫青小光点，密度越高颗数越多
        int orbit = Math.max(8, Math.round(6.0F + 6.0F * density));
        float orbitR = r0 * 1.08F;
        for (int i = 0; i < orbit; i++) {
            float a = spin * 0.55F - i * Mth.TWO_PI / orbit;
            float drift = 1.0F + 0.05F * Mth.sin(age * 0.06F + i * 1.7F);
            float x = Mth.cos(a) * orbitR * drift;
            float z = Mth.sin(a) * orbitR * drift;
            float s = dotSize * (0.45F + 0.20F * Mth.sin(age * 0.11F + i * 2.4F));
            float rr = (i & 1) == 0 ? hmr : har;
            float gg = (i & 1) == 0 ? hmg : hag;
            float bb = (i & 1) == 0 ? hmb : hab;
            drawSoftGlow(poseStack, buffer, x, groundY + 0.06F + 0.015F * Mth.sin(age * 0.10F + i), z,
                    s, s, s, rr, gg, bb, 0.60F * fade);
        }

        // ⑥ 最外呼吸光晕 + 两道周期性向外扩散的魔力涟漪（体现“成真之力”外溢）
        float breath = 0.5F + 0.5F * Mth.sin(age * 0.11F);
        drawGroundCircle(vc, matrix, groundY + 0.01F, r0 * (1.10F + breath * 0.10F), 60,
                hmr, hmg, hmb, (0.20F + 0.16F * breath) * fade);
        for (int k = 0; k < 2; k++) {
            float t = Mth.frac(age / 45.0F + k * 0.5F);
            float ease = 1.0F - (1.0F - t) * (1.0F - t);
            float rad = r0 * (0.45F + ease * 1.15F);
            float aa = Mth.sin(t * Mth.PI) * 0.42F;
            drawGroundCircle(vc, matrix, groundY + 0.02F, rad, 44, har, hag, hab, aa * fade);
        }

        // ⑦ 阵心双层光（紫晕 + 白核）与白亮细内芯螺旋（主体与细节的分层）
        float centerGlow = r0 * 0.45F;
        drawSoftGlow(poseStack, buffer, 0.0F, groundY + 0.03F, 0.0F, centerGlow, centerGlow, centerGlow,
                pr, pg, pb, 0.72F * fade);
        drawSoftGlow(poseStack, buffer, 0.0F, groundY + 0.05F, 0.0F, r0 * 0.16F, r0 * 0.16F, r0 * 0.16F,
                1.0F, 1.0F, 1.0F, 0.88F * fade);
        drawHelix(poseStack, buffer, h * 0.55F, r0 * 0.22F, h * 0.90F, 1, 1.4F, 26, age,
                1.0F, 1.0F, 0.96F, 0.34F * fade, 1.0F, 1.0F, 1.0F, 0.28F * fade);

        // ⑧ 三股紫青螺旋流光从阵心灌注进身体 + 同色上升光尘
        float helixR = Math.max(w * 0.50F, 0.46F);
        drawHelix(poseStack, buffer, h * 0.52F, helixR, h * 0.98F, 3, 1.8F, 46, age,
                pr, pg, pb, 0.60F * fade, ar, ag, ab, 0.50F * fade);
        int sparks = Math.max(4, Math.round(3.0F + 3.0F * density));
        for (int i = 0; i < sparks; i++) {
            float t = Mth.frac(age * 0.010F + i / (float) sparks);
            float y = Mth.lerp(t, h * 0.08F, h * 0.96F);
            float a = age * 0.30F + t * 2.6F + i * 1.31F;
            float rad = helixR * (0.30F + 1.05F * t);
            float x = Mth.cos(a) * rad;
            float z = Mth.sin(a) * rad;
            boolean cyan = (i & 1) == 1;
            float s = foot * (0.045F + 0.06F * t);
            drawSoftGlow(poseStack, buffer, x, y, z, s, s * 1.2F, s,
                    cyan ? ar : pr, cyan ? ag : pg, cyan ? ab : pb,
                    0.55F * Mth.sin(t * Mth.PI) * fade);
        }
    }

    /**
     * 猎魔人弩箭·开战弹幕——周身「赤焰箭雨」。
     *
     * <p>开战弹幕窗口只挂在佩戴者本人身上（由施放终极技能装填，与实现器同理不同屏堆叠、
     * 没有“多怪糊屏”风险），因此渲染不做多目标收敛、直接放足。核心是一圈斜绕身体公转的
     * 赤红能量弩矢：箭身带后曳的光尾、箭头白热闪烁，紧贴其内有一层反向飞旋的炽橙火星，
     * 外沿再绕一圈稀疏散落的白热战意星火；脚下铺赤红战火环与燃烧火点（呼应“战场”意象），
     * 胸口衬一团脉动的战意白光；另有两道赤色张力环沿身体高度周期性向外扩散，模拟弩箭
     * 不断上弦待发的蓄势感。元素全部为程序化柔光图元，单一目标的顶点总量远低于原版粒子。</p>
     */
    private static void renderBarrage(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                      float age, float win, float density, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float cy = h * 0.52F;
        // 本体窗口特效同屏目标数少，主轨道半径给足（高于火炬收敛档的贴体火链）
        float orbitR = Math.max(w * 1.35F, 1.02F);
        float slow = 1.0F / (float) Math.sqrt(1.0F + orbitR * 0.4F);
        float rot = age * slow * 0.11F;

        // 主色：赤红；强调色：炽橙；芯色：白热（箭雨是三档中对比度最强的一档）
        float r = 1.0F, g = 0.22F, b = 0.08F;
        float hr = hot(r), hg = hot(g), hb = hot(b);
        float or = 1.0F, og = 0.58F, ob = 0.12F;
        float hor = hot(or), hog = hot(og), hob = hot(ob);
        float wr = 1.0F, wg = 0.93F, wb = 0.80F;

        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        // ① 脚下赤红战火环：主环 + 呼吸光晕，均布燃烧火点（地面只铺圆环，不做多边形法阵，
        //    与实现器的紫青六边形在形态上彻底区分）
        float groundR = Math.max(foot * 1.55F, 1.25F);
        float gy = 0.04F;
        float breath = 0.5F + 0.5F * Mth.sin(age * 0.11F);
        drawGroundCircle(vc, matrix, gy, groundR, 56, hr, hg, hb, 0.55F * fade);
        drawGroundCircle(vc, matrix, gy + 0.01F, groundR * (0.94F + breath * 0.04F), 40,
                hor, hog, hob, (0.22F + 0.14F * breath) * fade);
        int flames = Math.max(6, Math.round(6.0F + 4.0F * density));
        float fPhase = rot * 0.7F;
        for (int i = 0; i < flames; i++) {
            float fa = fPhase + i * Mth.TWO_PI / flames;
            float flick = 1.0F + Mth.sin(age * 0.9F + i * 2.3F) * 0.22F;
            float fx2 = Mth.cos(fa) * groundR;
            float fz2 = Mth.sin(fa) * groundR;
            float fSize = foot * 0.105F * flick;
            drawSoftGlow(poseStack, buffer, fx2, gy + 0.05F, fz2,
                    fSize, fSize * 0.9F, fSize, 1.0F, 0.42F, 0.12F, 0.90F * fade);
            drawSoftGlow(poseStack, buffer, fx2, gy + 0.06F, fz2,
                    fSize * 0.42F, fSize * 0.42F, fSize * 0.42F, wr, wg, wb, 0.55F * fade);
        }

        // ② 周期性张力脉冲：两道赤色细环沿身体中部高度向外扩散（弩箭上弦的蓄势感）
        for (int k = 0; k < 2; k++) {
            float t = Mth.frac(age / 42.0F + k * 0.5F);
            float ease = 1.0F - (1.0F - t) * (1.0F - t);
            float rad = orbitR * (0.88F + ease * 1.05F);
            float aa = Mth.sin(t * Mth.PI) * 0.32F;
            drawCircleXZ(vc, matrix, cy, rad, 44, hr, hg, hb, aa * fade);
        }

        // ③ 主箭雨：八支赤红弩矢斜绕身体公转。每支带箭尾羽 + 向后拖曳的三粒光尾
        int bolts = 8;
        float len = 0.50F;
        for (int i = 0; i < bolts; i++) {
            float a = rot + i * Mth.TWO_PI / bolts;
            float bob = Mth.sin(age * 0.5F + i * 1.9F) * foot * 0.24F;
            float px = Mth.cos(a) * orbitR;
            float pz = Mth.sin(a) * orbitR;
            float py = cy + bob + (i % 2 == 0 ? foot * 0.24F : -foot * 0.24F);
            // 朝向：轨道切向 + 微微上仰（引弓待发的弩矢）
            float dx = -Mth.sin(a);
            float dz = Mth.cos(a);
            float dy = 0.30F;
            float inv = (float) (1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz));
            dx *= inv;
            dy *= inv;
            dz *= inv;

            // 箭身：尾 → 头
            float fx2 = px + dx * len * 0.58F;
            float fy2 = py + dy * len * 0.58F;
            float fz2 = pz + dz * len * 0.58F;
            float bx = px - dx * len * 0.42F;
            float by = py - dy * len * 0.42F;
            float bz = pz - dz * len * 0.42F;
            line(vc, matrix, bx, by, bz, fx2, fy2, fz2, hr, hg, hb, 0.95F * fade);
            // 箭身中段叠一道稍细的白热芯线，突出弩矢本体
            float mx2 = (bx + fx2) * 0.5F - dx * len * 0.12F;
            float my2 = (by + fy2) * 0.5F - dy * len * 0.12F;
            float mz2 = (bz + fz2) * 0.5F - dz * len * 0.12F;
            line(vc, matrix, mx2, my2, mz2, fx2, fy2, fz2, wr, wg, wb, 0.55F * fade);

            // 箭镞：两条短线张开成箭头
            float invP = (float) (1.0 / Math.sqrt(dz * dz + dx * dx) + 1e-6);
            float nx = -dz * invP;
            float nz = dx * invP;
            float head = len * 0.15F;
            float spread = len * 0.20F;
            line(vc, matrix, fx2, fy2, fz2,
                    fx2 - dx * head + nx * spread, fy2 - dy * head, fz2 - dz * head + nz * spread,
                    hr, hg, hb, 0.95F * fade);
            line(vc, matrix, fx2, fy2, fz2,
                    fx2 - dx * head - nx * spread, fy2 - dy * head, fz2 - dz * head - nz * spread,
                    hr, hg, hb, 0.95F * fade);

            // 箭尾羽：尾部后掠张开的两条短羽（比镞更收拢）
            float tail = len * 0.13F;
            float tailSpread = len * 0.13F;
            line(vc, matrix, bx, by, bz,
                    bx + dx * tail + nx * tailSpread, by + dy * tail, bz + dz * tail + nz * tailSpread,
                    hor, hog, hob, 0.80F * fade);
            line(vc, matrix, bx, by, bz,
                    bx + dx * tail - nx * tailSpread, by + dy * tail, bz + dz * tail - nz * tailSpread,
                    hor, hog, hob, 0.80F * fade);

            // 向后拖曳的三粒光尾（沿 -d 逐级衰减，营造“正高速环绕”的动势）
            for (int tr = 0; tr < 3; tr++) {
                float back = len * (0.16F + tr * 0.16F);
                float tSize = foot * (0.085F - tr * 0.024F);
                drawSoftGlow(poseStack, buffer,
                        bx - dx * back, by - dy * back, bz - dz * back,
                        tSize, tSize, tSize,
                        tr == 0 ? wr : (tr == 1 ? hor : hr),
                        tr == 0 ? wg : (tr == 1 ? hog : hg),
                        tr == 0 ? wb : (tr == 1 ? hob : hb),
                        (0.55F - tr * 0.16F) * fade);
            }

            // 箭头白热高亮：随每支箭独立闪烁
            float flicker = 1.0F + Mth.sin(age * 0.7F + i * 2.1F) * 0.20F;
            float hSize = foot * 0.11F * flicker;
            drawSoftGlow(poseStack, buffer, fx2, fy2, fz2,
                    hSize, hSize, hSize, wr, wg, wb, 0.98F * fade);
        }

        // ④ 贴身内层：炽橙火星沿反方向飞旋（在主箭之下形成双层对旋的纵深）
        int embers = Math.max(5, Math.round(5.0F + 4.0F * density));
        float emberR = orbitR * 0.78F;
        for (int i = 0; i < embers; i++) {
            float a = -rot * 0.9F - i * Mth.TWO_PI / embers;
            float bobY = Mth.sin(age * 0.75F + i * 2.2F) * foot * 0.34F;
            float x = Mth.cos(a) * emberR;
            float z = Mth.sin(a) * emberR;
            float y = cy + bobY + (i % 2 == 0 ? foot * 0.20F : -foot * 0.20F);
            float s = foot * 0.062F * (1.0F + 0.35F * Mth.sin(age * 0.9F + i * 1.6F));
            drawSoftGlow(poseStack, buffer, x, y, z, s, s, s, hor, hog, hob, 0.72F * fade);
        }

        // ⑤ 外沿白热星火带：稀疏高亮，勾勒出箭雨的球形活动边界
        int sparks = Math.max(4, Math.round(4.0F + 3.0F * density));
        float sparkR = orbitR * 1.24F;
        for (int i = 0; i < sparks; i++) {
            float a = rot * 1.3F + i * Mth.TWO_PI / sparks;
            float t = Mth.frac(age * 0.0021F + i / (float) sparks);
            float y = Mth.lerp(t, h * 0.10F, h * 0.96F);
            float rad = sparkR * (1.0F + 0.05F * Mth.sin(age * 0.08F + i * 1.7F));
            float x = Mth.cos(a) * rad;
            float z = Mth.sin(a) * rad;
            float s = foot * 0.050F * (0.85F + 0.30F * Mth.sin(age * 0.13F + i * 2.4F));
            drawSoftGlow(poseStack, buffer, x, y, z, s, s, s, wr, wg, wb, 0.50F * fade);
        }

        // ⑥ 中央战意光：胸口一团脉动赤焰 + 内层白热核心（呼应“弹幕已上膛”）
        float cGlow = orbitR * 0.60F;
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.15F);
        drawSoftGlow(poseStack, buffer, 0.0F, cy, 0.0F,
                cGlow * (1.0F + pulse * 0.08F), cGlow, cGlow * (1.0F + pulse * 0.08F),
                r, g, b, (0.18F + 0.08F * pulse) * fade);
        drawSoftGlow(poseStack, buffer, 0.0F, cy - foot * 0.05F, 0.0F,
                cGlow * 0.34F, cGlow * 0.30F, cGlow * 0.34F, wr, wg, wb, 0.38F * fade);

        // ⑦ 上升火尘：自脚下沿身体飘起的炽橙细屑
        int dust = Math.max(4, Math.round(5.0F * density));
        for (int i = 0; i < dust; i++) {
            float t = Mth.frac(age * 0.005F + i / (float) Math.max(1, dust));
            float x = Mth.sin(t * 5.0F + i * 1.3F) * emberR * 0.55F;
            float z = Mth.cos(t * 4.1F + i) * emberR * 0.55F;
            float y = Mth.lerp(t, h * 0.05F, h * 0.88F);
            float env = Mth.sin(t * Mth.PI);
            float s = foot * (0.070F - 0.025F * t);
            drawSoftGlow(poseStack, buffer, x, y, z, s, s, s,
                    hor, hog, hob, 0.50F * env * fade);
        }
    }

    // ------------------------- 常驻护罩 ------------------------- //

    /**
     * 败魔（Kaenic Rookern）·魔盾——常驻「紫晶球壳」。
     *
     * <p>刻意保持克制：单一半透明球壳 + 极淡的菲涅尔边缘白光 + 一道缓缓滚动的子午亮线。
     * 壳面本身按“相机-表面法线”做菲涅尔顶色（CPU 计算，无需自定义着色器），视觉上是一层
     * 晶透、边缘泛白的高质量能量罩，而不是线框球。整体亮度随心跳轻微起伏。</p>
     */
    private static void renderRookernShield(LivingEntity entity, PoseStack poseStack,
                                            MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float cy = h * 0.52F;
        float r = Math.max(w, h * 0.42F) * 1.10F;
        renderShieldShell(entity, poseStack, buffer, age, win, bright, cy, r * 1.12F, r * 1.12F, r * 1.12F,
                0.66F, 0.34F, 1.0F, 0.14F, 0.52F, 0.75F);
        // 一道缓缓滚动的子午高光弧
        drawRing(poseStack, buffer, 0.0F, cy, 0.0F, r * 1.13F,
                0.95F, age * 0.05F + 1.3F, 48,
                0.95F, 0.80F, 1.0F, 0.28F * win * bright);
    }

    /**
     * 原生质护带（Verdant Barrier 形态）·救主灵刃——常驻「青色水膜罩」。
     *
     * <p>与败魔同用半透明球壳渲染器，但贴体更紧、透明度更高，整体带淡淡的“液膜”质感；
     * 内部少量气泡光点缓慢上浮，呼应救主灵刃“再生”的主题。</p>
     */
    private static void renderProtoplasmShield(LivingEntity entity, PoseStack poseStack,
                                               MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float foot = Math.max(w, h * 0.16F);
        float cy = h * 0.48F;
        float rx = w * 0.55F + 0.28F;
        float ry = h * 0.46F;
        float rz = w * 0.55F + 0.28F;
        renderShieldShell(entity, poseStack, buffer, age, win, bright, cy, rx, ry, rz,
                0.20F, 1.0F, 0.92F, 0.16F, 0.40F, 0.55F);

        // 上浮的气泡光点
        for (int i = 0; i < 4; i++) {
            float t = Mth.frac(age * 0.008F + i / 4.0F);
            float a = i * 1.7F + t * 2.0F;
            float rad = Math.max(rx, rz) * (0.28F + 0.30F * Mth.sin(a));
            float y = h * (0.15F + 0.75F * t);
            float s = foot * 0.09F * (0.8F + 0.4F * Mth.sin(t * Mth.PI));
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * rad, y, Mth.sin(a) * rad,
                    s, s, s, 0.35F, 1.0F, 0.9F, 0.40F * Mth.sin(t * Mth.PI) * win * bright);
        }
    }

    /**
     * 炽天使之拥·应急护盾——「金白圣光球罩」。
     *
     * <p>与败魔同用半透明球壳渲染器，但配色为暖金圣光（呼应炽天使意象）：罩体金白、
     * 边缘亮白泛光更足；额外一道竖直滚动的圣光弧线。护盾持续 3 秒，特效窗口同步。</p>
     */
    private static void renderSeraphShield(LivingEntity entity, PoseStack poseStack,
                                           MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float cy = h * 0.52F;
        float r = Math.max(w, h * 0.42F) * 1.10F;
        renderShieldShell(entity, poseStack, buffer, age, win, bright, cy,
                r * 1.12F, r * 1.12F, r * 1.12F,
                1.0F, 0.86F, 0.42F, 0.16F, 0.55F, 0.85F);
        // 一道竖直滚动的圣光弧
        drawRing(poseStack, buffer, 0.0F, cy, 0.0F, r * 1.13F,
                0.35F, age * 0.06F, 48,
                1.0F, 0.94F, 0.72F, 0.30F * win * bright);
    }

    /**
     * 冬之誓·永恒——「冰蓝寒霜球罩」。
     *
     * <p>冰蓝主色 + 更冷的边缘白，罩面呼吸更慢（寒霜厚重感）；内部散布缓慢下沉的霜晶光点。</p>
     */
    private static void renderFimbulwinterShield(LivingEntity entity, PoseStack poseStack,
                                                 MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float foot = Math.max(w, h * 0.16F);
        float cy = h * 0.50F;
        float r = Math.max(w, h * 0.42F) * 1.12F;
        renderShieldShell(entity, poseStack, buffer, age, win, bright, cy,
                r * 1.10F, r * 1.10F, r * 1.10F,
                0.45F, 0.80F, 1.0F, 0.18F, 0.52F, 0.80F);
        // 内部下沉的霜晶光点
        for (int i = 0; i < 5; i++) {
            float t = Mth.frac(age * 0.006F + i / 5.0F);
            float a = i * 1.31F + t * 1.4F;
            float rad = r * (0.30F + 0.34F * Mth.sin(a * 1.7F));
            float y = h * (0.85F - 0.70F * t);
            float s = foot * 0.075F * (0.7F + 0.5F * Mth.sin(t * Mth.PI));
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * rad, y, Mth.sin(a) * rad,
                    s, s, s, 0.65F, 0.92F, 1.0F, 0.45F * Mth.sin(t * Mth.PI) * win * bright);
        }
    }

    /**
     * 斯特拉克的挑战护手·救主灵刃——「金色巨力球罩」。
     *
     * <p>金铜主色 + 亮金边缘（呼应斯特拉克拳套的巨力金光），罩体更紧贴、脉动稍快，
     * 额外一圈竖直滚动的金色巨力弧。</p>
     */
    private static void renderSterakShield(LivingEntity entity, PoseStack poseStack,
                                           MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float cy = h * 0.52F;
        float r = Math.max(w, h * 0.42F) * 1.10F;
        renderShieldShell(entity, poseStack, buffer, age, win, bright, cy,
                r * 1.10F, r * 1.10F, r * 1.10F,
                1.0F, 0.80F, 0.28F, 0.16F, 0.55F, 0.85F);
        drawRing(poseStack, buffer, 0.0F, cy, 0.0F, r * 1.12F,
                0.5F, age * 0.08F, 44,
                1.0F, 0.86F, 0.45F, 0.30F * win * bright);
    }

    /**
     * 饮血剑·余烬——「深红溢血球罩」。
     *
     * <p>暗红主色 + 血光边缘，罩体带缓慢“渗血”式呼吸（起伏频率更低、更深），
     * 内部血滴光点缓慢下沉。</p>
     */
    private static void renderBloodthirsterShield(LivingEntity entity, PoseStack poseStack,
                                                  MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float foot = Math.max(w, h * 0.16F);
        float cy = h * 0.50F;
        float r = Math.max(w, h * 0.42F) * 1.08F;
        renderShieldShell(entity, poseStack, buffer, age, win, bright, cy,
                r * 1.10F, r * 1.10F, r * 1.10F,
                0.90F, 0.10F, 0.10F, 0.17F, 0.50F, 0.75F);
        for (int i = 0; i < 4; i++) {
            float t = Mth.frac(age * 0.005F + i / 4.0F);
            float a = i * 1.57F + t * 1.2F;
            float rad = r * (0.30F + 0.30F * Mth.sin(a * 1.9F));
            float y = h * (0.85F - 0.70F * t);
            float s = foot * 0.08F * (0.7F + 0.5F * Mth.sin(t * Mth.PI));
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * rad, y, Mth.sin(a) * rad,
                    s, s, s, 1.0F, 0.22F, 0.18F, 0.45F * Mth.sin(t * Mth.PI) * win * bright);
        }
    }

    /**
     * 玛莫提乌斯之噬·救主灵刃——「暗影魔法护盾球罩」。
     *
     * <p>深紫主色 + 暗红边缘（呼应玛莫提乌斯的暗影 / 吸血鬼主题），罩体带缓慢的竖向魔法流光。</p>
     */
    private static void renderMawShield(LivingEntity entity, PoseStack poseStack,
                                       MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float cy = h * 0.52F;
        float r = Math.max(w, h * 0.42F) * 1.10F;
        renderShieldShell(entity, poseStack, buffer, age, win, bright, cy,
                r * 1.10F, r * 1.10F, r * 1.10F,
                0.55F, 0.20F, 1.00F, 0.16F, 0.55F, 0.85F);
        drawRing(poseStack, buffer, 0.0F, cy, 0.0F, r * 1.12F,
                0.5F, age * 0.08F, 44,
                0.75F, 0.30F, 1.00F, 0.30F * win * bright);
    }

    /**
     * 基克的聚合·聚合风暴——「冰火双色对流螺旋」。
     *
     * <p>冰蓝与炽橙两股反向螺旋绕身对流（冻结与焚烧交缠），脚下双色相扣的环，
     * 顶端冰橙光点交替闪烁——3 秒风暴窗口内极具爆发感。</p>
     */
    private static void renderZekesStorm(LivingEntity entity, PoseStack poseStack,
                                         MultiBufferSource buffer, float age, float win, float density, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float helixR = Math.max(w * 0.55F, 0.50F);
        // 双色反向螺旋：冰蓝（冷霜）+ 炽橙（烈焰）
        drawHelix(poseStack, buffer, h * 0.52F, helixR, h * 1.05F, 1, 1.6F, 34, age,
                0.45F, 0.85F, 1.0F, 0.70F * fade, 1.0F, 0.55F, 0.12F, 0.70F * fade);
        // 第二股反向对流
        drawHelix(poseStack, buffer, h * 0.52F, helixR * 0.75F, h * 1.05F, 1, 1.6F, 34, age + 1800.0F,
                1.0F, 0.55F, 0.12F, 0.50F * fade, 0.45F, 0.85F, 1.0F, 0.50F * fade);
        // 脚下双色环（快旋）
        float groundR = Math.max(foot * 1.35F, 1.05F);
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        drawGroundCircle(vc, matrix, 0.04F, groundR, 44, 0.45F, 0.85F, 1.0F, 0.55F * fade);
        drawGroundCircle(vc, matrix, 0.06F, groundR * 0.82F, 40, 1.0F, 0.55F, 0.12F, 0.55F * fade);
        // 顶部冰橙交替光点
        int dots = Math.max(4, Math.round(5.0F * density));
        for (int i = 0; i < dots; i++) {
            float a = age * 0.25F + i * Mth.TWO_PI / dots;
            boolean ice = (i & 1) == 0;
            float s = foot * 0.10F * (0.8F + 0.4F * Mth.sin(age * 0.5F + i * 2.1F));
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * groundR, h * 0.95F, Mth.sin(a) * groundR,
                    s, s, s,
                    ice ? 0.45F : 1.0F, ice ? 0.85F : 0.55F, ice ? 1.0F : 0.12F,
                    0.75F * fade);
        }
    }

    /**
     * 日炎圣盾·献祭——「常驻贴体火焰」。
     *
     * <p>克制版常驻火焰：脚下灼热环 + 双股贴体短火舌（比黯炎火炬更收敛、更暖金），
     * 佩戴期间持续滚动窗口。</p>
     */
    private static void renderSunfire(LivingEntity entity, PoseStack poseStack,
                                      MultiBufferSource buffer, float age, float win, float density, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float groundR = Math.max(foot * 1.10F, 0.85F);
        float bodyR = Math.max(foot * 0.48F, 0.40F);
        float phase = age * 0.10F;
        // 脚下灼热环（暖金）+ 火点
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        drawGroundCircle(vc, matrix, 0.04F, groundR, 40, 1.0F, 0.62F, 0.15F, 0.70F * fade);
        int marks = Math.max(5, Math.round(6.0F * density));
        for (int i = 0; i < marks; i++) {
            float a = phase + i * Mth.TWO_PI / marks;
            float s = foot * 0.09F;
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * groundR, 0.08F, Mth.sin(a) * groundR,
                    s, s, s, 1.0F, 0.68F, 0.20F, 0.80F * fade);
        }
        // 贴体双股短火舌（暖金火焰，高度只到半身）
        int chainDots = 3;
        for (int chain = 0; chain < 2; chain++) {
            float offset = chain * Mth.PI;
            for (int i = 0; i < chainDots; i++) {
                float t = i / (float) Math.max(1, chainDots - 1);
                float y = h * 0.10F + t * h * 0.45F;
                float angle = offset + phase + t * 1.0F;
                float radius = bodyR * (1.0F - 0.2F * t);
                float flicker = 1.0F + Mth.sin(age * 0.6F + i * 2.0F) * 0.2F;
                float s = foot * 0.15F * (1.0F - 0.25F * t) * flicker;
                drawSoftGlow(poseStack, buffer,
                        Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                        s, s * 1.15F, s, 1.0F, 0.58F, 0.14F, 0.50F * fade);
                drawSoftGlow(poseStack, buffer,
                        Mth.cos(angle) * radius, y + s * 0.2F, Mth.sin(angle) * radius,
                        s * 0.5F, s * 0.55F, s * 0.5F, 1.0F, 0.90F, 0.55F, 0.75F * fade);
            }
        }
    }

    /**
     * 海克斯注力刚壁·超速驱动——「电蓝攻速能量环」。
     *
     * <p>电蓝色能量环绕身高速旋转（呼应攻速暴涨），三圈上升能量波 + 胸口超载白核。</p>
     */
    private static void renderHexplateOverdrive(LivingEntity entity, PoseStack poseStack,
                                                MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float ringR = Math.max(foot * 1.25F, 0.95F);
        float cy = h * 0.52F;
        float fast = age * 0.35F;
        // 三道高速旋转的倾斜电蓝环（攻速暴涨的能量感）
        for (int k = 0; k < 3; k++) {
            float tilt = 1.20F + k * 0.45F;
            drawRing(poseStack, buffer, 0.0F, cy, 0.0F, ringR * (1.0F - k * 0.08F),
                    tilt, fast * (k % 2 == 0 ? 1.0F : -1.0F) + k * 1.1F, 40,
                    0.35F, 0.85F, 1.0F, (0.60F - k * 0.12F) * fade);
        }
        // 上升能量波（超载电流感）
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        for (int k = 0; k < 2; k++) {
            float t = Mth.frac(age / 14.0F + k * 0.5F);
            float y = Mth.lerp(t, h * 0.05F, h * 1.05F);
            float alpha = Mth.sin(t * Mth.PI) * 0.40F;
            drawCircleXZ(vc, matrix, y, ringR * (0.7F + 0.3F * t), 36,
                    0.55F, 0.92F, 1.0F, alpha * fade);
        }
        // 胸口超载白核
        float core = foot * 0.30F;
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.6F);
        drawSoftGlow(poseStack, buffer, 0.0F, cy, 0.0F,
                core * (1.0F + pulse * 0.15F), core * (1.0F + pulse * 0.15F), core,
                0.85F, 0.97F, 1.0F, 0.70F * fade);
    }

    /**
     * 自然之力·坚韧——「翠绿自然光环」。
     *
     * <p>满层坚韧时的视觉反馈：脚下舒展的大地绿法阵 + 双股翠绿上升风叶（反向对旋、
     * 顺体螺旋上升），配合周身呼吸式绿光——呼应英雄联盟自然之力的自然之息。</p>
     */
    private static void renderSteadfastAura(LivingEntity entity, PoseStack poseStack,
                                            MultiBufferSource buffer, float age, float win, float density, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float groundR = Math.max(foot * 1.30F, 1.00F);
        float bodyR = Math.max(foot * 0.50F, 0.42F);
        // 脚下大地绿法阵（双层缓旋）
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        drawGroundCircle(vc, matrix, 0.04F, groundR, 44, 0.35F, 0.95F, 0.45F, 0.60F * fade);
        drawGroundCircle(vc, matrix, 0.06F, groundR * 0.78F, 36, 0.75F, 1.0F, 0.60F, 0.45F * fade);
        // 双股反向上升风叶（翠绿 → 嫩白，顺体螺旋）
        int chainDots = 4;
        for (int chain = 0; chain < 2; chain++) {
            float offset = chain * Mth.PI + age * 0.16F;
            for (int i = 0; i < chainDots; i++) {
                float t = i / (float) chainDots;
                float y = h * 0.05F + t * h * 1.00F;
                float angle = offset - t * 2.2F;
                float radius = bodyR * (1.0F - 0.35F * t);
                float sway = 1.0F + Mth.sin(age * 0.45F + i * 1.9F) * 0.22F;
                float s = foot * 0.14F * (1.0F - 0.3F * t) * sway;
                // 外层绿
                drawSoftGlow(poseStack, buffer,
                        Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                        s, s * 1.2F, s, 0.30F, 0.95F, 0.42F, 0.55F * fade);
                // 内层嫩白
                drawSoftGlow(poseStack, buffer,
                        Mth.cos(angle) * radius, y + s * 0.25F, Mth.sin(angle) * radius,
                        s * 0.5F, s * 0.6F, s * 0.5F, 0.80F, 1.0F, 0.82F, 0.65F * fade);
            }
        }
        // 环绕旋转的绿叶光点
        int dots = Math.max(4, Math.round(6.0F * density));
        for (int i = 0; i < dots; i++) {
            float a = age * 0.22F + i * Mth.TWO_PI / dots;
            float s = foot * 0.08F * (0.85F + 0.3F * Mth.sin(age * 0.5F + i * 2.3F));
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * groundR, h * 0.45F, Mth.sin(a) * groundR,
                    s, s, s, 0.40F, 0.98F, 0.50F, 0.70F * fade);
        }
    }

    /**
     * 风暴狂涌·骤风——「紫金电弧攒聚」。
     *
     * <p>骤风标记目标：脚下快速脉动的紫罗兰电圈 + 周身攒聚的紫金电光点
     * （越接近引爆越亮），劈雷前的风暴蓄能观感。</p>
     */
    private static void renderStormsurgeMark(LivingEntity entity, PoseStack poseStack,
                                             MultiBufferSource buffer, float age, float win, float bright) {
        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float groundR = Math.max(foot * 1.20F, 0.95F);
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.9F);
        // 脚下紫罗兰电圈（双层反向快旋 + 脉动）
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        drawGroundCircle(vc, matrix, 0.04F, groundR * (1.0F + pulse * 0.10F), 40,
                0.72F, 0.55F, 1.0F, 0.65F * fade);
        drawGroundCircle(vc, matrix, 0.06F, groundR * 0.70F, 32,
                1.0F, 0.90F, 0.45F, 0.50F * fade);
        // 周身攒聚电光点（紫金，快速环绕）
        int dots = 6;
        for (int i = 0; i < dots; i++) {
            float a = age * 0.55F + i * Mth.TWO_PI / dots;
            float y = h * (0.15F + 0.10F * Mth.sin(age * 0.7F + i * 2.6F));
            float r = groundR * 0.85F;
            float s = foot * 0.10F * (0.9F + 0.4F * pulse);
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * r, y, Mth.sin(a) * r,
                    s, s * 1.3F, s, 0.80F, 0.65F, 1.0F, 0.75F * fade);
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * r, y + s * 0.2F, Mth.sin(a) * r,
                    s * 0.45F, s * 0.55F, s * 0.45F, 1.0F, 0.95F, 0.70F, 0.80F * fade);
        }
        // 头顶攒聚核心（引爆前的亮核）
        float core = foot * 0.26F * (1.0F + pulse * 0.2F);
        drawSoftGlow(poseStack, buffer, 0.0F, h * 1.02F, 0.0F,
                core, core * 1.4F, core, 0.85F, 0.72F, 1.0F, 0.60F * fade);
    }

    /**
     * 常驻球壳护罩：按经纬网格画一个真正的半透明表面（非线框），并根据每个顶点到相机的
     * 菲涅尔角逐顶点染色——正对相机的面更透、边缘轮廓更亮白，配合整体缓慢脉动形成
     * “精良常驻能量罩”的观感。刻意不做大量装饰元素。
     */
    private static void renderShieldShell(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                          float age, float win, float bright,
                                          float cy, float rx, float ry, float rz,
                                          float mr, float mg, float mb,
                                          float baseA, float rimA, float rimWhite) {
        float fade = win * bright;
        if (fade <= 0.02F) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        // 相机位置相对实体脚底的近似局部坐标（球壳各向同性，可忽略实体朝向带来的偏差）
        float toCX = (float) camera.getPosition().x - (float) entity.getX();
        float toCY = (float) camera.getPosition().y - (float) entity.getY();
        float toCZ = (float) camera.getPosition().z - (float) entity.getZ();

        int latCount = 14;
        int lonCount = 24;
        float latStep = Mth.PI / latCount;
        float lonStep = Mth.TWO_PI / lonCount;
        // 呼吸脉动：罩体随“心跳”缓慢涨缩
        float pulse = 0.5F + 0.5F * Mth.sin(age * 0.10F);
        float pulseA = 0.85F + 0.15F * pulse;

        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        // 所有顶点固定采样柔光贴图的正中心：等效于“纯白不透明”，外壳颜色完全由逐顶点颜色决定
        final float u = 0.5F, v = 0.5F;

        for (int li = 0; li < latCount; li++) {
            float th0 = -Mth.HALF_PI + li * latStep;
            float th1 = th0 + latStep;
            for (int lo = 0; lo < lonCount; lo++) {
                float ph0 = lo * lonStep;
                float ph1 = ph0 + lonStep;
                // 四个角：t00=(th0,ph0) t01=(th0,ph1) t11=(th1,ph1) t10=(th1,ph0)
                emitShellVertex(vc, matrix, th0, ph0, cy, rx, ry, rz, u, v,
                        toCX, toCY, toCZ, mr, mg, mb, baseA, rimA, rimWhite, pulseA * fade);
                emitShellVertex(vc, matrix, th0, ph1, cy, rx, ry, rz, u, v,
                        toCX, toCY, toCZ, mr, mg, mb, baseA, rimA, rimWhite, pulseA * fade);
                emitShellVertex(vc, matrix, th1, ph1, cy, rx, ry, rz, u, v,
                        toCX, toCY, toCZ, mr, mg, mb, baseA, rimA, rimWhite, pulseA * fade);
                emitShellVertex(vc, matrix, th1, ph0, cy, rx, ry, rz, u, v,
                        toCX, toCY, toCZ, mr, mg, mb, baseA, rimA, rimWhite, pulseA * fade);
            }
        }
    }

    /**
     * 向当前护罩缓冲提交一个表面顶点；按单位球参数(θ,φ)求表面点与外法线，再做菲涅尔着色。
     */
    private static void emitShellVertex(VertexConsumer vc, Matrix4f matrix,
                                        float theta, float phi, float cy,
                                        float rx, float ry, float rz, float u, float v,
                                        float toCX, float toCY, float toCZ,
                                        float mr, float mg, float mb,
                                        float baseA, float rimA, float rimWhite, float fade) {
        float cosT = Mth.cos(theta), sinT = Mth.sin(theta);
        float cosP = Mth.cos(phi), sinP = Mth.sin(phi);
        float px = rx * cosT * cosP;
        float py = cy + ry * sinT;
        float pz = rz * cosT * sinP;
        // 单位球外法线
        float nx = cosT * cosP;
        float ny = sinT;
        float nz = cosT * sinP;
        // 视线向量（相机 - 顶点）
        float dx = toCX - px;
        float dy = toCY - py;
        float dz = toCZ - pz;
        float inv = (float) (1.0 / Math.sqrt(dx * dx + dy * dy + dz * dz));
        float dot = Mth.clamp((nx * dx + ny * dy + nz * dz) * inv, -1.0F, 1.0F);
        float side = Math.max(0.0F, dot);              // 正对相机=1，转到背面→0
        float rim = 1.0F - side;                         // 边缘（视线擦过表面）=1
        float rimE = rim * rim;

        // 正对越透、边缘越亮白：晶透玻璃罩观感
        float alpha = (baseA * (0.30F + 0.70F * side) + rimA * rimE * 0.7F) * fade;
        alpha = Math.min(0.92F, alpha);
        // 背面（看穿球体内部）压低，保持“薄壳”而不是实心
        if (dot < 0.0F) {
            alpha *= 0.28F;
        }
        float white = rimWhite * rimE;
        float cr = mr * (0.62F + 0.38F * side) + white;
        float cg = mg * (0.62F + 0.38F * side) + white;
        float cb = mb * (0.62F + 0.38F * side) + white;
        float cl = (float) Math.sqrt(1.0F / (cr * cr + cg * cg + cb * cb));
        // 简单归一防过曝（护罩亮面应为高光而非纯白死白）
        if (cl < 1.0F) {
            cr *= (1.0F + (1.0F - cl) * 0.3F);
            cg *= (1.0F + (1.0F - cl) * 0.3F);
            cb *= (1.0F + (1.0F - cl) * 0.3F);
        }
        vc.vertex(matrix, px, py, pz)
                .color(Math.min(1.0F, cr), Math.min(1.0F, cg), Math.min(1.0F, cb), Math.max(0.0F, alpha))
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(nx, ny, nz)
                .endVertex();
    }

    // ------------------------- 黯炎妖火 ------------------------- //

    /**
     * 黯炎火炬（Blackfire Torch）灼烧特效——克制版「黯火缠身」。
     *
     * <p>灼烧是周期性持续效果，很容易同时命中十几只怪：因此这里刻意收敛——只保留脚底
     * 一圈转动火环 + 双股贴体盘绕的黯火链 + 少量上升余烬，去掉大法阵与焰冠。并且同屏
     * 被灼烧的实体越多（TORCH 活跃数越高），每只的特效规模自动按档收敛（≤3 完整、
     * ≤8 降一档、更多只保留骨架），避免视野里糊成一片紫光、也压住多怪时的帧开销。</p>
     */
    private static void renderTorch(LivingEntity entity, PoseStack poseStack, MultiBufferSource buffer,
                                    float age, float win, float density, float bright) {
        // 同屏拥挤度：活跃 TORCH 窗口数≈正在被灼烧的实体数，越多越收敛，避免糊成一片紫光
        int burning = ActiveGearFx.countByKind(FxKind.TORCH);
        float scale = burning <= 3 ? 1.0F : burning <= 8 ? 0.70F : 0.45F;

        float w = Math.max(entity.getBbWidth(), 0.1F);
        float h = Math.max(entity.getBbHeight(), 0.2F);
        float fade = win * bright * scale;
        if (fade <= 0.02F) {
            return;
        }
        float foot = Math.max(w, h * 0.16F);
        float groundR = Math.max(foot * 1.15F, 0.90F);
        float bodyR = Math.max(foot * 0.52F, 0.42F);
        float slow = 1.0F / (float) Math.sqrt(1.0F + groundR * 0.4F);
        float phase = age * slow * 0.10F;

        // ① 脚底一圈旋转火环：细环 + 均布火点，取代大法阵（多怪同屏不占地）
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        drawGroundCircle(vc, matrix, 0.03F, groundR, 40, 0.72F, 0.30F, 1.0F, 0.80F * fade);
        int marks = Math.max(5, Math.round(7.0F * scale));
        for (int i = 0; i < marks; i++) {
            float a = phase + i * Mth.TWO_PI / marks;
            drawSoftGlow(poseStack, buffer,
                    Mth.cos(a) * groundR, 0.08F, Mth.sin(a) * groundR,
                    0.10F, 0.10F, 0.10F, 0.92F, 0.50F, 1.0F, 0.85F * fade);
        }

        // ② 双股盘绕黯火链：贴体上升的主体火焰（收敛尺寸，保持双层火舌）
        int chainDots = Math.max(2, Math.round((3.0F + density) * scale * 0.9F));
        float flameTop = h * 0.78F;
        for (int chain = 0; chain < 2; chain++) {
            float offset = chain * Mth.PI;
            for (int i = 0; i < chainDots; i++) {
                float t = i / (float) Math.max(1, chainDots - 1);
                float y = h * 0.12F + t * flameTop;
                float angle = offset + phase + t * 1.15F;
                float radius = bodyR * (1.0F - 0.22F * t);
                float x = Mth.cos(angle) * radius;
                float z = Mth.sin(angle) * radius;
                float flicker = 1.0F + Mth.sin(age * 0.5F + chain * 2.3F + i * 1.9F) * 0.20F;
                float s = foot * 0.18F * (1.0F - 0.28F * t) * flicker;
                drawSoftGlow(poseStack, buffer, x, y, z, s, s * 1.15F, s,
                        0.70F, 0.30F, 1.00F, 0.55F * fade);
                drawSoftGlow(poseStack, buffer, x, y + s * 0.20F, z,
                        s * 0.50F, s * 0.55F, s * 0.50F,
                        PAL_TORCH.cr(), PAL_TORCH.cg(), PAL_TORCH.cb(), 0.85F * fade);
            }
        }

        // ③ 大体型才加一道缓慢缠身腰带环；拥挤档（≤8 只以上）自动省略
        if (h >= 1.8F && scale > 0.5F) {
            drawRing(poseStack, buffer, 0.0F, h * 0.60F, 0.0F, bodyR * 1.30F,
                    0.85F, age * slow * 0.05F + 1.3F, 36,
                    0.72F, 0.30F, 1.0F, 0.45F * fade);
        }

        // ④ 灰紫余烬烟：低透明缓升，收敛后仅剩的“上飘”余韵
        for (int i = 0; i < 3; i++) {
            float t = Mth.frac(age * 0.004F + i / 3.0F);
            float y = h * 0.15F + t * h * 1.10F;
            float drift = Mth.sin(age * 0.05F + i * 2.4F) * bodyR * 0.35F;
            float radius = bodyR * (0.5F + t * 1.0F) + drift;
            float angle = i * 2.094F + t;
            float s = foot * 0.26F * (0.5F + t) * scale;
            drawSoftGlow(poseStack, buffer, Mth.cos(angle) * radius, y, Mth.sin(angle) * radius,
                    s, s * 1.4F, s, 0.60F, 0.26F, 0.80F, 0.18F * (1.0F - t) * fade);
        }
    }

    // ------------------------- 几何绘制工具 ------------------------- //

    /**
     * 竖直 billboard 符盘：绕视线轴自转的金色符文轮盘。整盘只画一个四边形。
     * {@code radius} 为符盘的实际视觉半径；{@code spin} 为绕视线轴的自转角度（弧度）。
     */
    private static void drawSigilDisc(PoseStack poseStack, MultiBufferSource buffer,
                                      float x, float y, float z, float radius, float spin,
                                      float r, float g, float b, float a) {
        if (a <= 0.01F) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.mulPose(Axis.ZP.rotation(spin));
        // quad 半径 0.5 * scale = radius → 整块符盘视觉半径即 radius
        poseStack.scale(radius * 2.0F, radius * 2.0F, 1.0F);
        VertexConsumer vc = buffer.getBuffer(SIGIL_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        glowQuad(vc, matrix, 0.5F, r, g, b, a);
        poseStack.popPose();
    }

    /**
     * 在“已面向相机的 billboard 平面”上于某点绘制柔光点：用于符盘盘面内的卫星/符文光点。
     * 平面偏移 ({@code ox},{@code oy}) 以盘心为原点、垂直于视线方向，因此盘面元素永远贴合符盘。
     */
    private static void drawPlaneGlow(PoseStack poseStack, MultiBufferSource buffer,
                                      float x, float y, float z,
                                      float ox, float oy, float radius,
                                      float r, float g, float b, float a) {
        if (a <= 0.01F) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.translate(ox, oy, 0.0F);
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        glowQuad(vc, matrix, radius, r, g, b, a);
        poseStack.popPose();
    }

    /** 把颜色向白色提亮一段（45% 朝向白），提升 1px 线条/细光的视觉重量。 */
    private static float hot(float channel) {
        return channel + (1.0F - channel) * 0.45F;
    }

    private static void drawSoftGlow(PoseStack poseStack, MultiBufferSource buffer,
                                      float x, float y, float z,
                                      float sx, float sy, float sz,
                                      float r, float g, float b, float a) {
        if (a <= 0.01F) {
            return;
        }
        Camera camera = Minecraft.getInstance().gameRenderer.getMainCamera();
        poseStack.pushPose();
        poseStack.translate(x, y, z);
        poseStack.mulPose(camera.rotation());
        poseStack.scale(sx, sy, sz);

        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        glowQuad(vc, matrix, 0.5F, r, g, b, a);

        poseStack.popPose();
    }

    private static void glowQuad(VertexConsumer vc, Matrix4f matrix, float half,
                                  float r, float g, float b, float a) {
        // 正面
        glowVertex(vc, matrix, -half, half, r, g, b, a, 0.0F, 0.0F);
        glowVertex(vc, matrix, half, half, r, g, b, a, 1.0F, 0.0F);
        glowVertex(vc, matrix, half, -half, r, g, b, a, 1.0F, 1.0F);
        glowVertex(vc, matrix, -half, -half, r, g, b, a, 0.0F, 1.0F);
        // 背面
        glowVertex(vc, matrix, -half, -half, r, g, b, a, 0.0F, 1.0F);
        glowVertex(vc, matrix, half, -half, r, g, b, a, 1.0F, 1.0F);
        glowVertex(vc, matrix, half, half, r, g, b, a, 1.0F, 0.0F);
        glowVertex(vc, matrix, -half, half, r, g, b, a, 0.0F, 0.0F);
    }

    private static void glowVertex(VertexConsumer vc, Matrix4f matrix, float x, float y,
                                    float r, float g, float b, float a, float u, float v) {
        vc.vertex(matrix, x, y, 0.0F)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(0.0F, 0.0F, 1.0F)
                .endVertex();
    }

    /**
     * 女妖面纱·废除：法术屏障护罩（紫罗兰色三层缓旋涡环 + 贴地法阵刻线）。
     * 法盾就绪时常驻（服务端每秒刷新），被打破后熄灭，冷却结束重新亮起。
     */
    private static void renderBansheeShield(Entity entity, PoseStack poseStack,
                                            MultiBufferSource buffer, float age, float win, float bright) {
        float h = entity.getBbHeight();
        float cy = h * 0.5F;
        float alpha = 0.32F * win * bright;
        // 三层缓旋紫环：不同高度、半径与转向，构成“涡流屏障”轮廓
        drawRing(poseStack, buffer, 0.0F, cy - h * 0.25F, 0.0F, 0.55F,
                0.15F, age * 0.10F, 28, 0.62F, 0.30F, 0.95F, alpha);
        drawRing(poseStack, buffer, 0.0F, cy, 0.0F, 0.70F,
                0.0F, -age * 0.07F, 32, 0.72F, 0.42F, 1.00F, alpha * 0.9F);
        drawRing(poseStack, buffer, 0.0F, cy + h * 0.28F, 0.0F, 0.48F,
                0.20F, age * 0.13F, 24, 0.80F, 0.55F, 1.00F, alpha * 0.8F);
        // 脚下贴地小法阵刻线
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        drawGroundCircle(vc, matrix, 0.05F, 0.62F, 28, 0.65F, 0.35F, 1.00F, alpha * 0.7F);
    }

    /**
     * 地点锚定特效绘制（世界坐标，AFTER_ENTITIES 兜底阶段）。
     *
     * <ul>
     *   <li>{@link FxKind#REDEMPTION_CAST}——救赎·降临施法预告：贴地金色法阵，
     *       半径从 0 展开到施法范围、渐亮，附带内环与缓旋标记环；</li>
     *   <li>{@link FxKind#REDEMPTION_DESCENT}——圣光落下：八根贴天光柱 +
     *       自天而降的冲击环，落地瞬间扩散波纹；</li>
     *   <li>{@link FxKind#WARMOG_RESTORE}——狂徒之心回血：贴地柔和绿金双环
     *       （克制半径与透明度，不遮挡视野，替代原版心形粒子）。</li>
     * </ul>
     */
    private static void renderSpots(PoseStack poseStack, MultiBufferSource buffer,
                                    ClientLevel level, float partialTick,
                                    double camX, double camY, double camZ) {
        float now = level.getGameTime() + partialTick;
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        for (ActiveSpotFx.Spot spot : ActiveSpotFx.spots()) {
            float remaining = spot.expireGameTime() - now;
            if (remaining <= 0.0F) {
                continue;
            }
            float progress = Mth.clamp(1.0F - remaining / spot.totalTicks(), 0.0F, 1.0F);
            poseStack.pushPose();
            poseStack.translate(spot.x() - camX, spot.y() - camY, spot.z() - camZ);
            Matrix4f matrix = poseStack.last().pose();
            switch (spot.kind()) {
                case EMERALD_DOOM -> {
                    // 再见桃花源·抹杀翡翠法阵：三层展开法阵 + 对旋星芒环 + 八根上升光柱
                    float ease = 1.0F - (1.0F - progress) * (1.0F - progress);
                    float r = Math.max(0.6F, spot.radius() * (0.45F + 0.55F * ease));
                    float fade = Mth.clamp(remaining / 8.0F, 0.0F, 1.0F);
                    float alpha = Math.min(0.85F, 0.30F + 0.45F * progress) * fade;
                    // 贴地三层法阵（外环实、中环密、内环亮）
                    drawGroundCircle(vc, matrix, 0.05F, r, 48, 0.20F, 1.00F, 0.55F, alpha);
                    drawGroundCircle(vc, matrix, 0.05F, r * 0.72F, 40, 0.45F, 1.00F, 0.70F, alpha * 0.85F);
                    drawGroundCircle(vc, matrix, 0.06F, r * 0.45F, 32, 0.05F, 0.95F, 0.35F, alpha * 0.9F);
                    // 双层对旋星芒环（外层大环正转、内层小环反转）
                    poseStack.pushPose();
                    drawRing(poseStack, buffer, 0.0F, 0.06F, 0.0F, r * 0.6F,
                            0.0F, now * 1.6F, 12, 0.35F, 1.00F, 0.65F, alpha);
                    drawRing(poseStack, buffer, 0.0F, 0.10F, 0.0F, r * 0.34F,
                            0.0F, -now * 2.2F, 8, 0.80F, 1.00F, 0.85F, alpha);
                    poseStack.popPose();
                    // 八根绕心缓旋上升光柱
                    for (int i = 0; i < 8; i++) {
                        float ang = (float) (i / 8.0D * Math.PI * 2.0D) + now * 0.35F;
                        float px = Mth.cos(ang) * (r * 0.8F);
                        float pz = Mth.sin(ang) * (r * 0.8F);
                        line(vc, matrix, px, 0.0F, pz, px, 4.5F, pz,
                                0.30F, 1.00F, 0.60F, alpha * 0.8F);
                    }
                }
                case REDEMPTION_CAST -> {
                    // 施法预告：展开的金色法阵（半径 = 施法范围 × easeOut）
                    float ease = 1.0F - (1.0F - progress) * (1.0F - progress) * (1.0F - progress);
                    float r = spot.radius() * ease;
                    float alpha = 0.15F + 0.30F * progress;
                    drawGroundCircle(vc, matrix, 0.05F, r, 48, 1.00F, 0.94F, 0.55F, alpha);
                    drawGroundCircle(vc, matrix, 0.05F, r * 0.74F, 40, 1.00F, 0.86F, 0.45F, alpha * 0.8F);
                    // 中心缓旋小环
                    poseStack.pushPose();
                    drawRing(poseStack, buffer, 0.0F, 0.05F, 0.0F, r * 0.22F,
                            0.0F, now * 0.6F, 24, 1.00F, 1.00F, 0.85F, alpha);
                    poseStack.popPose();
                }
                case REDEMPTION_DESCENT -> {
                    // 圣光落下：贴天光柱 + 下落冲击环 + 落地扩散
                    float fade = 1.0F - 0.5F * progress;
                    float alpha = 0.35F * fade;
                    float r = spot.radius();
                    for (int i = 0; i < 8; i++) {
                        float ang = (float) (i / 8.0D * Math.PI * 2.0D);
                        float px = Mth.cos(ang) * (r * 0.62F);
                        float pz = Mth.sin(ang) * (r * 0.62F);
                        line(vc, matrix, px, 10.0F, pz, px, 0.0F, pz,
                                1.00F, 0.95F, 0.70F, alpha);
                    }
                    float fallY = (1.0F - progress) * 9.0F + 0.05F;
                    float ringAlpha = 0.20F + 0.45F * progress;
                    drawGroundCircle(vc, matrix, fallY, r, 48, 1.00F, 0.95F, 0.60F, ringAlpha);
                    if (progress > 0.8F) {
                        float boom = (progress - 0.8F) / 0.2F;
                        drawGroundCircle(vc, matrix, 0.05F, r + boom * 2.0F, 48,
                                1.00F, 0.98F, 0.80F, 0.45F * (1.0F - boom));
                        drawGroundCircle(vc, matrix, 0.05F, r, 48, 1.00F, 0.94F, 0.55F,
                                0.55F * (1.0F - boom));
                    }
                }
                case HATEFOG -> {
                    // 残疫·憎恨之雾：极淡紫色填充表现雾气覆盖区 + 紫色描边圆圈界定范围
                    drawGroundDisc(vc, matrix, 0.05F, spot.radius(), 48,
                            0.55F, 0.20F, 1.00F, 0.14F);
                    drawGroundCircle(vc, matrix, 0.06F, spot.radius(), 48,
                            0.72F, 0.30F, 1.00F, 0.55F);
                }
                case LIFE_FROM_DEATH -> {
                    // 蜕生·死中新生：击杀位置爆发的绿色治疗新星（上升光球 + 展开回血法阵）
                    float ease = 1.0F - (1.0F - progress) * (1.0F - progress);
                    float r = spot.radius() * ease;
                    float fade = 1.0F - progress;
                    // 地面回血法阵：展开的绿色光环 + 半透明填充
                    drawGroundDisc(vc, matrix, 0.05F, r, 48,
                            0.20F, 0.95F, 0.35F, 0.18F * fade);
                    drawGroundCircle(vc, matrix, 0.06F, r, 48,
                            0.30F, 1.00F, 0.45F, 0.55F * fade);
                    drawGroundCircle(vc, matrix, 0.06F, r * 0.5F, 36,
                            0.45F, 1.00F, 0.55F, 0.40F * fade);
                    // 上升的绿色治疗光球：随进度升腾并膨胀后消散
                    float orbY = 0.2F + progress * 2.2F;
                    float orbR = 0.6F + 0.4F * Mth.sin(progress * 3.14159F);
                    for (int i = 0; i < 5; i++) {
                        float yy = orbY + i * 0.18F;
                        drawRing(poseStack, buffer, 0.0F, yy, 0.0F, orbR * (0.5F + i * 0.12F),
                                0.0F, now * 0.5F + i, 28,
                                0.35F, 1.00F, 0.50F, (0.5F - i * 0.08F) * fade);
                    }
                    VertexConsumer orbVc = buffer.getBuffer(GLOW_TYPE);
                    drawGroundDisc(orbVc, matrix, orbY, orbR, 32,
                            0.30F, 1.00F, 0.55F, 0.30F * fade);
                }
                case WARMOG_RESTORE -> {
                    // 狂徒之心：贴地柔和绿金双环（呼吸明暗，克制不遮挡视野）
                    float breath = 0.8F + 0.2F * Mth.sin(now * 0.25F);
                    poseStack.pushPose();
                    drawRing(poseStack, buffer, 0.0F, 0.06F, 0.0F, 0.90F,
                            0.0F, now * 0.35F, 32, 0.45F, 0.90F, 0.55F, 0.30F * breath);
                    drawRing(poseStack, buffer, 0.0F, 0.06F, 0.0F, 0.55F,
                            0.0F, -now * 0.5F, 24, 0.95F, 0.85F, 0.40F, 0.22F * breath);
                    poseStack.popPose();
                }
                default -> {
                }
            }
            poseStack.popPose();
        }
    }

    private static void drawRing(PoseStack poseStack, MultiBufferSource buffer,
                                  float cx, float cy, float cz, float radius,
                                  float tilt, float roll, int segments,
                                  float r, float g, float b, float a) {
        poseStack.pushPose();
        poseStack.translate(cx, cy, cz);
        poseStack.mulPose(Axis.XP.rotation(tilt));
        poseStack.mulPose(Axis.YP.rotation(roll));
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();
        drawCircleXZ(vc, matrix, 0.0F, radius, segments, r, g, b, a);
        poseStack.popPose();
    }

    private static void drawCircleXZ(VertexConsumer vc, Matrix4f matrix, float y, float radius,
                                      int segments, float r, float g, float b, float a) {
        float step = Mth.TWO_PI / segments;
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = ((i + 1) % segments) * step;
            float x1 = Mth.cos(a1) * radius;
            float z1 = Mth.sin(a1) * radius;
            float x2 = Mth.cos(a2) * radius;
            float z2 = Mth.sin(a2) * radius;
            line(vc, matrix, x1, y, z1, x2, y, z2, r, g, b, a);
        }
    }

    /**
     * 地面法阵/灼烧环专用：在 XZ 水平面内画一圈柔光描边环。
     *
     * <p>光带本身平躺在水平面上：俯视、斜视都清晰可见，像“用光绘在地面的法阵刻线”，
     * 不会出现相机朝向描边在正俯视时完全消失的问题。半宽按半径折算，保持笔画粗细一致。</p>
     */
    /**
     * 水平面柔光填充圆盘（xz 平面），用于清晰标示一片地面区域（如恨雾范围/治疗新星）。
     * 三角扇两种绕序各画一遍，规避背面剔除导致俯视不可见。
     */
    private static void drawGroundDisc(VertexConsumer vc, Matrix4f matrix, float y, float radius,
                                       int segments, float r, float g, float b, float a) {
        if (a <= 0.01F || radius <= 0.0F) {
            return;
        }
        float step = Mth.TWO_PI / segments;
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = ((i + 1) % segments) * step;
            float x1 = Mth.cos(a1) * radius, z1 = Mth.sin(a1) * radius;
            float x2 = Mth.cos(a2) * radius, z2 = Mth.sin(a2) * radius;
            strokeVertex(vc, matrix, 0.0F, y, 0.0F, 0.5F, 0.5F, r, g, b, a, 0.0F, 1.0F, 0.0F);
            strokeVertex(vc, matrix, x1, y, z1, 0.5F, 0.5F, r, g, b, a, 0.0F, 1.0F, 0.0F);
            strokeVertex(vc, matrix, x2, y, z2, 0.5F, 0.5F, r, g, b, a, 0.0F, 1.0F, 0.0F);
            strokeVertex(vc, matrix, x2, y, z2, 0.5F, 0.5F, r, g, b, a, 0.0F, 1.0F, 0.0F);
            strokeVertex(vc, matrix, x1, y, z1, 0.5F, 0.5F, r, g, b, a, 0.0F, 1.0F, 0.0F);
            strokeVertex(vc, matrix, 0.0F, y, 0.0F, 0.5F, 0.5F, r, g, b, a, 0.0F, 1.0F, 0.0F);
        }
    }

    private static void drawGroundCircle(VertexConsumer vc, Matrix4f matrix, float y, float radius,
                                         int segments, float r, float g, float b, float a) {
        if (a <= 0.02F || radius <= 0.0F) {
            return;
        }
        float halfW = groundStrokeHalf(radius);
        float step = Mth.TWO_PI / segments;
        for (int i = 0; i < segments; i++) {
            float a1 = i * step;
            float a2 = ((i + 1) % segments) * step;
            groundStroke(vc, matrix,
                    Mth.cos(a1) * radius, y, Mth.sin(a1) * radius,
                    Mth.cos(a2) * radius, Mth.sin(a2) * radius,
                    halfW, r, g, b, a);
        }
    }

    /**
     * 在水平面画闭合正多边形（六边形/三角形等）。{@code rot} 为首个顶点方位角，随动画旋转。
     * 每条边按地面柔光描边（平躺于平面）绘制，供脚下法阵使用。
     */
    private static void drawPolygonXZ(VertexConsumer vc, Matrix4f matrix, float y, float radius,
                                      int sides, float rot,
                                      float r, float g, float b, float a) {
        if (sides < 3 || a <= 0.02F) {
            return;
        }
        float halfW = groundStrokeHalf(radius);
        float step = Mth.TWO_PI / sides;
        for (int i = 0; i < sides; i++) {
            float a1 = rot + i * step;
            float a2 = rot + ((i + 1) % sides) * step;
            groundStroke(vc, matrix,
                    Mth.cos(a1) * radius, y, Mth.sin(a1) * radius,
                    Mth.cos(a2) * radius, Mth.sin(a2) * radius,
                    halfW, r, g, b, a);
        }
    }

    private static void drawHelix(PoseStack poseStack, MultiBufferSource buffer,
                                   float cy, float radius, float height, int strands,
                                   float turns, int segments, float age,
                                   float r1, float g1, float b1, float a1,
                                   float r2, float g2, float b2, float a2) {
        poseStack.pushPose();
        poseStack.translate(0.0F, cy, 0.0F);
        VertexConsumer vc = buffer.getBuffer(GLOW_TYPE);
        Matrix4f matrix = poseStack.last().pose();

        float baseRotation = age * 0.25F;
        for (int strand = 0; strand < strands; strand++) {
            float offset = strand * Mth.TWO_PI / strands;
            boolean second = strand % 2 == 1;
            float r = second ? r2 : r1;
            float g = second ? g2 : g1;
            float bb = second ? b2 : b1;
            float aa = second ? a2 : a1;
            for (int i = 0; i < segments; i++) {
                float t1 = i / (float) segments;
                float t2 = (i + 1) / (float) segments;
                float y1 = (t1 - 0.5F) * height;
                float y2 = (t2 - 0.5F) * height;
                float angle1 = baseRotation + t1 * turns * Mth.TWO_PI + offset;
                float angle2 = baseRotation + t2 * turns * Mth.TWO_PI + offset;
                float x1 = Mth.cos(angle1) * radius;
                float z1 = Mth.sin(angle1) * radius;
                float x2 = Mth.cos(angle2) * radius;
                float z2 = Mth.sin(angle2) * radius;
                line(vc, matrix, x1, y1, z1, x2, y2, z2, r, g, bb, aa);
            }
        }
        poseStack.popPose();
    }

    /**
     * 相机朝向的三维线段柔光描边（替代原 {@code RenderType.lines()} 直线）：每条线段展开成
     * 面向相机的细光带，宽度按相机距离换算成近似恒定的屏幕像素。
     *
     * <p>顶点以 {@link #GLOW_TYPE} 完整 6 元素格式提交（position/color/uv/overlay/uv2/normal），
     * 与光点/护罩使用同一 RenderType 与顶点格式，从根源上杜绝共享缓冲内 3 元素/6 元素混写
     * 导致的 "Not filled all elements of the vertex"。</p>
     */
    private static void line(VertexConsumer vc, Matrix4f matrix,
                              float x1, float y1, float z1,
                              float x2, float y2, float z2,
                              float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dy = y2 - y1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (len < 1.0E-5F || a <= 0.02F) {
            return;
        }
        dx /= len;
        dy /= len;
        dz /= len;

        // 相机位置映射进线段所在坐标系：当前 pose 通向相机相对帧，帧原点即相机位置。
        // （渲染主线程单线程执行，临时对象静态复用，避免每个线段都触发小对象分配。）
        LINE_TMP_MATRIX.set(matrix).invert();
        Vector3f cam = LINE_TMP_CAM;
        LINE_TMP_MATRIX.getTranslation(cam);

        float mx = (x1 + x2) * 0.5F;
        float my = (y1 + y2) * 0.5F;
        float mz = (z1 + z2) * 0.5F;
        float vx = cam.x - mx;
        float vy = cam.y - my;
        float vz = cam.z - mz;
        float dist = (float) Math.sqrt(vx * vx + vy * vy + vz * vz);
        if (dist < 1.0E-5F) {
            return;
        }
        float halfW = strokeHalfPixelsToWorld(dist, LINE_HALF_PX);

        // 侧向量 = normalize(线段方向 × 视线方向)：与线段和视线同时垂直，展开面始终面向相机
        float cx = dy * vz - dz * vy;
        float cy = dz * vx - dx * vz;
        float cz = dx * vy - dy * vx;
        float cl = (float) Math.sqrt(cx * cx + cy * cy + cz * cz);
        float sx;
        float sy;
        float sz;
        if (cl < 1.0E-5F) {
            // 线段恰好指向相机（侧向量退化为 0）：任取一条与线段垂直的方向即可
            float ux = 0.0F;
            float uy = 0.0F;
            float uz = 1.0F;
            if (Math.abs(dz) > 0.9F) {
                ux = 1.0F;
                uz = 0.0F;
            }
            float px2 = uy * dz - uz * dy;
            float py2 = uz * dx - ux * dz;
            float pz2 = ux * dy - uy * dx;
            float pl = (float) Math.sqrt(px2 * px2 + py2 * py2 + pz2 * pz2);
            if (pl < 1.0E-5F) {
                return;
            }
            sx = px2 / pl;
            sy = py2 / pl;
            sz = pz2 / pl;
        } else {
            sx = cx / cl;
            sy = cy / cl;
            sz = cz / cl;
        }
        strokeRibbon(vc, matrix, x1, y1, z1, x2, y2, z2, sx, sy, sz, halfW,
                0.0F, 1.0F, 0.0F, r, g, b, a);
    }

    /**
     * 水平面柔光描边：线段端点位于同一高度 y（XZ 平面内），向平面内的水平垂线方向
     * 展开半宽 {@code halfW}，使光带平躺在平面上。
     */
    private static void groundStroke(VertexConsumer vc, Matrix4f matrix,
                                     float x1, float y, float z1,
                                     float x2, float z2,
                                     float halfW, float r, float g, float b, float a) {
        float dx = x2 - x1;
        float dz = z2 - z1;
        float len = (float) Math.sqrt(dx * dx + dz * dz);
        if (len < 1.0E-5F) {
            return;
        }
        float px = -dz / len;
        float pz = dx / len;
        strokeRibbon(vc, matrix, x1, y, z1, x2, y, z2, px, 0.0F, pz, halfW,
                0.0F, 1.0F, 0.0F, r, g, b, a);
    }

    /**
     * 把一段线段按给定侧向量展开成柔光描边四边形，提交到 GLOW 缓冲。
     *
     * <p>所有顶点固定采样柔光贴图水平中列（u=0.5），横截面 v 0→1 正好扫过贴图径向剖面：
     * 光带中芯最亮、向两侧平滑柔边，且沿线段长度方向亮度恒定（不会把圆形光晕拉出接缝/
     * 颗粒感）。双面各补一版绕序，配合无剔除的 GLOW 类型确保任何朝向都可见。</p>
     */
    private static void strokeRibbon(VertexConsumer vc, Matrix4f matrix,
                                     float ax, float ay, float az, float bx, float by, float bz,
                                     float sx, float sy, float sz, float halfW,
                                     float nx, float ny, float nz,
                                     float r, float g, float b, float a) {
        float apx = ax + sx * halfW;
        float apy = ay + sy * halfW;
        float apz = az + sz * halfW;
        float bpx = bx + sx * halfW;
        float bpy = by + sy * halfW;
        float bpz = bz + sz * halfW;
        float amx = ax - sx * halfW;
        float amy = ay - sy * halfW;
        float amz = az - sz * halfW;
        float bmx = bx - sx * halfW;
        float bmy = by - sy * halfW;
        float bmz = bz - sz * halfW;
        // 正面
        strokeVertex(vc, matrix, amx, amy, amz, 0.5F, 1.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, apx, apy, apz, 0.5F, 0.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, bpx, bpy, bpz, 0.5F, 0.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, amx, amy, amz, 0.5F, 1.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, bpx, bpy, bpz, 0.5F, 0.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, bmx, bmy, bmz, 0.5F, 1.0F, r, g, b, a, nx, ny, nz);
        // 背面（GLOW 双面无剔除，反向绕序补齐）
        strokeVertex(vc, matrix, amx, amy, amz, 0.5F, 1.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, bpx, bpy, bpz, 0.5F, 0.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, apx, apy, apz, 0.5F, 0.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, amx, amy, amz, 0.5F, 1.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, bmx, bmy, bmz, 0.5F, 1.0F, r, g, b, a, nx, ny, nz);
        strokeVertex(vc, matrix, bpx, bpy, bpz, 0.5F, 0.0F, r, g, b, a, nx, ny, nz);
    }

    /** 与光点同格式的 6 元素顶点提交（描边专用，仅 UV 行与几何由调用方决定）。 */
    private static void strokeVertex(VertexConsumer vc, Matrix4f matrix,
                                     float x, float y, float z,
                                     float u, float v,
                                     float r, float g, float b, float a,
                                     float nx, float ny, float nz) {
        vc.vertex(matrix, x, y, z)
                .color(r, g, b, a)
                .uv(u, v)
                .overlayCoords(OverlayTexture.NO_OVERLAY)
                .uv2(LightTexture.FULL_BRIGHT)
                .normal(nx, ny, nz)
                .endVertex();
    }

    /** 贴地描边的半宽（世界单位）：随法阵/光环半径缩放，保证笔画粗细比例协调。 */
    private static float groundStrokeHalf(float radius) {
        return Mth.clamp(radius * 0.016F, 0.008F, 0.030F);
    }

    /** 相机朝向描边的目标半宽（屏幕像素）。 */
    private static final float LINE_HALF_PX = 1.8F;

    /** 线条计算用临时矩阵/向量（渲染主线程单线程执行，可安全复用，避免逐线段小对象分配）。 */
    private static final Matrix4f LINE_TMP_MATRIX = new Matrix4f();
    private static final Vector3f LINE_TMP_CAM = new Vector3f();

    /**
     * 把「屏幕像素半宽」换算成当前相机距离下的世界半宽：利用投影公式（半视场角 + 屏幕高度），
     * 使远处描边变宽、近处变窄，最终屏幕上的视觉粗细大致恒定。
     */
    private static float strokeHalfPixelsToWorld(float distance, float halfPixels) {
        Minecraft mc = Minecraft.getInstance();
        int screenH = mc.getWindow().getScreenHeight();
        if (screenH <= 0) {
            screenH = 1080;
        }
        double fov = Math.toRadians(mc.options.fov().get().floatValue());
        double worldPerPx = Math.tan(fov * 0.5) * 2.0 / screenH;
        return (float) (distance * worldPerPx) * halfPixels + 0.004F;
    }
}
