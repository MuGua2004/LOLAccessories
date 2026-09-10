package com.example.lolaccessories.client;

import com.example.lolaccessories.LOLAccessories;
import net.minecraft.client.Minecraft;
import com.mojang.blaze3d.platform.NativeImage;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.item.Item;
import net.minecraftforge.registries.ForgeRegistries;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 神话装备「主色调」客户端缓存：从装备物品贴图（{@code textures/item/<id>.png}）提取
 * 高亮区色相，供神话流光字与相关特效按贴图色调渲染（如澄空之愿白光、魔王之心紫光）。
 *
 * <p>每件装备的贴图只解析一次并缓存 {@code float[]{hue, saturation}}：贴图像素遍历
 * 32×32（至多 1024 像素），首次悬停时发生，之后零开销。饱和度过低（贴图整体偏白/灰）
 * 时返回低饱和度——流光渲染退化为同色系「白光」而非彩虹，保证色调统一。</p>
 */
@net.minecraftforge.api.distmarker.OnlyIn(net.minecraftforge.api.distmarker.Dist.CLIENT)
public final class MythicHueCache {

    private static final Map<String, float[]> CACHE = new ConcurrentHashMap<>();

    private MythicHueCache() {
    }

    /**
     * 取装备贴图主色 {@code float[]{hue(0~1), saturation(0~1)}}；解析失败返回澄空白光兜底。
     */
    public static float[] getTint(Item item) {
        ResourceLocation key = ForgeRegistries.ITEMS.getKey(item);
        String id = key == null ? "unknown" : key.getPath();
        return CACHE.computeIfAbsent(id, MythicHueCache::load);
    }

    private static float[] load(String id) {
        ResourceLocation tex = new ResourceLocation(LOLAccessories.MOD_ID, "textures/item/" + id + ".png");
        var resOpt = Minecraft.getInstance().getResourceManager().getResource(tex);
        if (resOpt.isEmpty()) {
            return new float[]{0.02F, 0.85F};
        }
        try (var in = resOpt.get().open();
             NativeImage img = NativeImage.read(in)) {
            int w = img.getWidth();
            int h = img.getHeight();
            float sumSat = 0.0F;
            float sumHueX = 0.0F;
            float sumHueY = 0.0F;
            int n = 0;
            int bestSat = 0;
            float bestHue = 0.0F;
            for (int y = 0; y < h; y++) {
                for (int x = 0; x < w; x++) {
                    int px = img.getPixelRGBA(x, y); // ABGR 打包
                    int a = (px >>> 24) & 0xFF;
                    if (a < 32) {
                        continue;
                    }
                    int r = px & 0xFF;
                    int g = (px >>> 8) & 0xFF;
                    int b = (px >>> 16) & 0xFF;
                    int mx = Math.max(r, Math.max(g, b));
                    int mn = Math.min(r, Math.min(g, b));
                    int sat = mx == 0 ? 0 : (mx - mn) * 255 / mx;
                    float hue = rgbHue(r, g, b);
                    sumSat += sat / 255.0F;
                    sumHueX += Math.cos(hue * (float) Math.PI * 2.0F);
                    sumHueY += Math.sin(hue * (float) Math.PI * 2.0F);
                    n++;
                    if (sat > bestSat) {
                        bestSat = sat;
                        bestHue = hue;
                    }
                }
            }
            if (n == 0) {
                return new float[]{0.02F, 0.85F}; // 全透明兜底：红系
            }
            float avgSat = sumSat / n;
            if (avgSat < 0.2F || bestSat < 40) {
                // 贴图整体偏白/灰：低饱和「白光」流光
                return new float[]{0.0F, 0.10F};
            }
            float hue = ((float) Math.atan2(sumHueY, sumHueX) / ((float) Math.PI * 2.0F) + 1.0F) % 1.0F;
            float sat = Math.min(1.0F, 0.55F + avgSat * 0.45F);
            return new float[]{hue, sat};
        } catch (Exception e) {
            LOLAccessories.LOGGER.warn("[神话色调] 贴图解析失败 {}，使用红系兜底", tex, e);
            return new float[]{0.02F, 0.85F};
        }
    }

    /** RGB → 色相（0~1）。 */
    private static float rgbHue(int r, int g, int b) {
        float mx = Math.max(r, Math.max(g, b));
        float mn = Math.min(r, Math.min(g, b));
        float d = mx - mn;
        if (d <= 0.0F) {
            return 0.0F;
        }
        float hue;
        if (mx == r) {
            hue = ((g - b) / d) % 6.0F;
        } else if (mx == g) {
            hue = (b - r) / d + 2.0F;
        } else {
            hue = (r - g) / d + 4.0F;
        }
        return (hue / 6.0F + 1.0F) % 1.0F;
    }
}
