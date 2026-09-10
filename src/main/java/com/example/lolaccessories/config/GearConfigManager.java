package com.example.lolaccessories.config;

import com.example.lolaccessories.compat.IronsSpellDamage;
import com.example.lolaccessories.LOLAccessories;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.google.gson.JsonSyntaxException;
import net.minecraftforge.fml.loading.FMLPaths;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 装备配置文件管理器。
 *
 * <p>每个装备对应 config/{modid}/gear/{gear_id}.json，首次加载时若文件不存在，会从
 * jar 内的默认模板（/config/{modid}/gear/*.json）复制一份出来，玩家可自由修改数值，
 * 修改后执行 {@code /lolaccessories reload} 即可生效。</p>
 *
 * <p>外置配置文件一旦生成就不会被覆盖；当模组更新给某件装备<b>新增或调整了条目</b>
 * （属性/被动）时，旧文件不会自动带上新条目，导致玩家困惑“更新了怎么没变化”。
 * 因此默认模板自带单调递增的 {@code template_version}，加载时若发现外置文件的版本号
 * 低于 jar 模板，会按模板的条目集合自动合并一次（见 {@link #syncWithTemplate}）：
 * 模板新增的条目补入、模板移除的旧条目清理、两边同 id 的条目保留外置文件里玩家改过的
 * 数值，最后把版本号对齐并回写。此后玩家继续修改只影响数值，不会被再次重写。</p>
 */
public final class GearConfigManager {

    private static final Logger LOGGER = LOLAccessories.LOGGER;
    private static final Gson GSON = new GsonBuilder()
            .setPrettyPrinting()
            .disableHtmlEscaping()
            .create();

    private static final Map<String, GearConfig> CACHE = new ConcurrentHashMap<>();
    private static Path root;

    private GearConfigManager() {
    }

    /** 初始化配置目录（可在 mod 构造阶段调用，路径在此时已可用）。 */
    public static void init() {
        if (root != null) {
            return;
        }
        root = FMLPaths.CONFIGDIR.get().resolve(LOLAccessories.MOD_ID).resolve("gear");
        try {
            Files.createDirectories(root);
        } catch (IOException e) {
            LOGGER.error("[GearConfig] 无法创建配置目录 {}", root, e);
        }
    }

    /**
     * 获取装备配置，缓存命中则直接返回；未加载过则从磁盘解析并缓存。
     * 玩家编辑文件后调用 {@link #reload()} 会清空缓存并重新读取。
     */
    public static GearConfig get(String gearId) {
        if (gearId == null || gearId.isEmpty()) {
            return new GearConfig().fallback("unknown");
        }
        if (root == null) {
            init();
        }
        return CACHE.computeIfAbsent(gearId, GearConfigManager::load);
    }

    public static void reload() {
        CACHE.clear();
        LOGGER.info("[GearConfig] 已清空缓存，下次读取时会从磁盘重新加载");
    }

    private static GearConfig load(String gearId) {
        Path file = root.resolve(gearId + ".json");
        if (!Files.exists(file)) {
            copyDefault(gearId);
        }
        if (!Files.exists(file)) {
            LOGGER.error("[GearConfig] 找不到装备配置文件 {}，使用空配置", file);
            return new GearConfig().fallback(gearId);
        }
        try (Reader reader = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
            JsonObject merged = syncWithTemplate(gearId, file, JsonParser.parseReader(reader));
            GearConfig config = GSON.fromJson(merged, GearConfig.class);
            config.resolve();
            // 旧配置（合并时按 id 保留玩家条目）可能没有 school 字段；
            // 用代码内的默认学派映射兜底，保证每件装备的魔法伤害仍有自己的学派。
            for (GearConfig.OnHitEffect effect : config.on_hit_effects) {
                if (effect.school == null || effect.school.isEmpty()) {
                    String fallback = IronsSpellDamage.defaultSchoolFor(config.gear_id, effect.id);
                    if (fallback != null) {
                        effect.school = fallback;
                    }
                }
            }
            LOGGER.info("[GearConfig] 已加载配置 {}", file);
            return config;
        } catch (JsonSyntaxException | IOException e) {
            LOGGER.error("[GearConfig] 解析装备配置 {} 失败：{}", file, e.getMessage());
            return new GearConfig().fallback(gearId);
        }
    }

    private static void copyDefault(String gearId) {
        Path target = root.resolve(gearId + ".json");
        if (Files.exists(target)) {
            return;
        }
        String resource = "/config/" + LOLAccessories.MOD_ID + "/gear/" + gearId + ".json";
        try (InputStream in = GearConfigManager.class.getResourceAsStream(resource)) {
            if (in == null) {
                LOGGER.warn("[GearConfig] jar 内不存在默认配置模板 {}", resource);
                return;
            }
            Files.copy(in, target);
            LOGGER.info("[GearConfig] 已从模板生成默认配置 {}", target);
        } catch (IOException e) {
            LOGGER.error("[GearConfig] 复制默认配置 {} 失败", resource, e);
        }
    }

    /**
     * 读取 jar 内默认模板并返回其 JSON 根对象；模板缺失/损坏时返回 {@code null}（不阻碍加载）。
     */
    private static JsonObject readTemplate(String gearId) {
        String resource = "/config/" + LOLAccessories.MOD_ID + "/gear/" + gearId + ".json";
        try (InputStream in = GearConfigManager.class.getResourceAsStream(resource)) {
            if (in == null) {
                return null;
            }
            try (Reader reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
                JsonElement parsed = JsonParser.parseReader(reader);
                return parsed != null && parsed.isJsonObject() ? parsed.getAsJsonObject() : null;
            }
        } catch (JsonSyntaxException | IOException e) {
            LOGGER.warn("[GearConfig] 读取 jar 模板 {} 失败：{}", resource, e.getMessage());
            return null;
        }
    }

    /**
     * 模板同步：外置文件版本号低于 jar 模板时，按模板条目集合对齐一次。
     *
     * <p>条目（attributes / on_hit_effects）按 {@code id} 对照合并，合并规则：</p>
     * <ul>
     *   <li>两边同 id：保留磁盘里玩家改过的数值（amount/operation/percent 等字段）；</li>
     *   <li>仅模板有的 id：作为「新增条目」补入（顺序跟随模板）；</li>
     *   <li>仅磁盘有的 id：视为模板已废弃/改名的旧条目，清理掉。</li>
     * </ul>
     * 合并后若内容有变化则回写文件并把 {@code template_version} 对齐到模板版本。
     *
     * @param gearId 装备 id
     * @param file   外置配置文件（存在）
     * @param disk   外置配置已解析出的 JSON 根对象
     * @return 应继续用于解析的 JSON 根对象（可能是合并后的外置对象）
     */
    private static JsonObject syncWithTemplate(String gearId, Path file, JsonElement disk) {
        if (disk == null || !disk.isJsonObject()) {
            return disk != null && disk.isJsonObject() ? disk.getAsJsonObject() : new JsonObject();
        }
        JsonObject diskJson = disk.getAsJsonObject();
        JsonObject template = readTemplate(gearId);
        if (template == null) {
            return diskJson;
        }
        int diskVersion = jsonVersion(diskJson);
        int templateVersion = jsonVersion(template);
        if (diskVersion >= templateVersion) {
            return diskJson;
        }

        List<String> added = new ArrayList<>();
        List<String> removed = new ArrayList<>();
        boolean changed = mergeEntrySet(diskJson, "attributes", template, added, removed);
        changed |= mergeEntrySet(diskJson, "on_hit_effects", template, added, removed);
        if (!changed) {
            return diskJson;
        }

        diskJson.addProperty("template_version", templateVersion);
        try (Writer writer = Files.newBufferedWriter(file, StandardCharsets.UTF_8)) {
            GSON.toJson(diskJson, writer);
            LOGGER.info("[GearConfig] {} 外置配置过旧（版本 {} < {}），已按模板自动同步并回写",
                    gearId, diskVersion, templateVersion);
        } catch (IOException e) {
            LOGGER.error("[GearConfig] 回写同步后的配置 {} 失败（仅影响本次加载结果）", file, e);
        }
        if (!added.isEmpty()) {
            LOGGER.info("[GearConfig] {} 已自动补充新条目：{}", gearId, added);
        }
        if (!removed.isEmpty()) {
            LOGGER.info("[GearConfig] {} 已自动清理废弃条目：{}", gearId, removed);
        }
        return diskJson;
    }

    /** 合并顶层 {@code key} 数组（按元素 {@code id} 对照）。有增删改动返回 true。 */
    private static boolean mergeEntrySet(JsonObject disk, String key, JsonObject template,
                                         List<String> added, List<String> removed) {
        JsonArray diskArray = disk.has(key) ? disk.getAsJsonArray(key) : new JsonArray();
        JsonArray tplArray = template.has(key) ? template.getAsJsonArray(key) : new JsonArray();
        Map<String, JsonObject> diskById = indexById(diskArray);
        Map<String, JsonObject> tplById = indexById(tplArray);

        boolean changed = false;
        for (String id : diskById.keySet()) {
            if (!tplById.containsKey(id)) {
                removed.add(id);
                changed = true;
            }
        }
        JsonArray merged = new JsonArray();
        for (Map.Entry<String, JsonObject> entry : tplById.entrySet()) {
            String id = entry.getKey();
            JsonObject diskKeep = diskById.get(id);
            if (diskKeep != null) {
                merged.add(diskKeep); // 保留玩家改过的数值
            } else {
                added.add(id);
                changed = true;
                merged.add(entry.getValue()); // 模板新增条目
            }
        }
        if (changed) {
            disk.add(key, merged);
        }
        return changed;
    }

    /** 以 {@code id} 字段建立顺序索引（无 id 或非对象的元素直接忽略）。 */
    private static Map<String, JsonObject> indexById(JsonArray array) {
        Map<String, JsonObject> result = new LinkedHashMap<>();
        if (array == null) {
            return result;
        }
        for (JsonElement element : array) {
            if (element == null || !element.isJsonObject()) {
                continue;
            }
            JsonObject object = element.getAsJsonObject();
            if (object.has("id") && !object.get("id").isJsonNull()) {
                result.put(object.get("id").getAsString(), object);
            }
        }
        return result;
    }

    /** 读取 JSON 顶层 {@code template_version}，缺失视为 0（最旧）。 */
    private static int jsonVersion(JsonObject json) {
        if (json.has("template_version") && !json.get("template_version").isJsonNull()) {
            try {
                return json.get("template_version").getAsInt();
            } catch (NumberFormatException | UnsupportedOperationException ignored) {
                return 0;
            }
        }
        return 0;
    }
}
