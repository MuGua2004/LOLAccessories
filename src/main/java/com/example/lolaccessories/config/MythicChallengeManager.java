package com.example.lolaccessories.config;

import com.example.lolaccessories.LOLAccessories;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 神话装备「挑战解锁」配置管理器（与 {@link GearConfigManager} 同款机制）：
 *
 * <ul>
 *   <li>配置目录 {@code config/lolaccessories/mythic/<gear_id>.json}，首次缺失时从
 *       jar 内默认模板（{@code config/lolaccessories/mythic/}）复制落地——整合包作者
 *       直接改磁盘文件即可魔改挑战类型、阈值、进度 id 或整体禁用；</li>
 *   <li>懒加载 + 缓存，{@link #reload()} 供指令/调试刷新；</li>
 *   <li>文件损坏时返回禁用态兜底，不影响游戏运行。</li>
 * </ul>
 */
public final class MythicChallengeManager {

    private static final Logger LOGGER = LOLAccessories.LOGGER;
    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();

    private static final Map<String, MythicChallengeConfig> CACHE = new ConcurrentHashMap<>();
    private static Path root;

    private MythicChallengeManager() {
    }

    /** 初始化配置目录（可在 mod 构造阶段调用）。 */
    public static void init() {
        if (root != null) {
            return;
        }
        root = FMLPaths.CONFIGDIR.get().resolve(LOLAccessories.MOD_ID).resolve("mythic");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            LOGGER.error("[MythicChallenge] 无法创建配置目录 {}", root, e);
        }
    }

    /** 获取神话装备的挑战配置（懒加载；缺失时从 jar 模板落地）。 */
    public static MythicChallengeConfig get(String gearId) {
        if (gearId == null || gearId.isEmpty()) {
            return new MythicChallengeConfig().fallback(gearId == null ? "unknown" : gearId);
        }
        if (root == null) {
            init();
        }
        return CACHE.computeIfAbsent(gearId, MythicChallengeManager::load);
    }

    public static void reload() {
        CACHE.clear();
        LOGGER.info("[MythicChallenge] 已清空缓存，下次读取时会从磁盘重新加载");
    }

    private static MythicChallengeConfig load(String gearId) {
        Path file = root.resolve(gearId + ".json");
        if (!Files.exists(file)) {
            copyDefault(gearId, file);
        }
        if (!Files.exists(file)) {
            LOGGER.error("[MythicChallenge] 找不到神话解锁配置 {}，使用禁用态兜底", file);
            return new MythicChallengeConfig().fallback(gearId);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            MythicChallengeConfig config = GSON.fromJson(JsonParser.parseReader(reader), MythicChallengeConfig.class);
            if (config == null) {
                return new MythicChallengeConfig().fallback(gearId);
            }
            if (config.gear_id == null || config.gear_id.isEmpty()) {
                config.gear_id = gearId;
            }
            return config;
        } catch (Exception e) {
            LOGGER.error("[MythicChallenge] 解析配置失败 {}，使用禁用态兜底", file, e);
            return new MythicChallengeConfig().fallback(gearId);
        }
    }

    /** 首次落地：从 jar 内默认模板复制；jar 内没有（如整合包删了模板）则生成最小骨架。 */
    private static void copyDefault(String gearId, Path file) {
        try (InputStream in = MythicChallengeManager.class.getResourceAsStream(
                "/config/" + LOLAccessories.MOD_ID + "/mythic/" + gearId + ".json")) {
            if (in != null) {
                Files.copy(in, file);
                LOGGER.info("[MythicChallenge] 已落地默认神话解锁配置 {}", file);
                return;
            }
        } catch (IOException e) {
            LOGGER.warn("[MythicChallenge] 复制默认模板失败 {}", file, e);
        }
        // 骨架兜底：禁用态，等作者自行填写
        JsonObject json = new JsonObject();
        json.addProperty("template_version", 1);
        json.addProperty("gear_id", gearId);
        json.addProperty("enabled", false);
        json.addProperty("advancement_id", "");
        JsonObject challenge = new JsonObject();
        challenge.addProperty("type", "");
        challenge.addProperty("threshold", 0.0);
        json.add("challenge", challenge);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(json, writer);
        } catch (IOException e) {
            LOGGER.error("[MythicChallenge] 写入骨架配置失败 {}", file, e);
        }
    }
}
