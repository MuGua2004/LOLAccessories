# LOLAccessories

Minecraft 1.20.1 + Forge 模组项目骨架。

| 项 | 值 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.23 |
| ForgeGradle | 6.x（`net.minecraftforge.gradle`） |
| Gradle | 8.8（wrapper 自带） |
| Java | 17（Mojang 1.18+ 起的要求） |
| 映射 | `official`（Mojang 官方名） |
| modid | `lolaccessories` |
| 前置模组 | Curios API `5.14.1+1.20.1`（运行时必需） |

## 前置依赖：Curios API

本模组以 [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios)（Forge 1.20.1）为前置。依赖声明在 `build.gradle`：

```groovy
compileOnly fg.deobf("top.theillusivec4.curios:curios-forge:5.14.1+1.20.1:api") // 编译期：只用 API 构件
runtimeOnly fg.deobf("top.theillusivec4.curios:curios-forge:5.14.1+1.20.1")     // 运行期：完整实现
```

版本号集中在 `gradle.properties` 的 `curios_version`；Curios 托管在 `https://maven.theillusivec4.top/`，已在 `repositories` 配好。`fg.deobf()` 会把依赖重映射到本项目的映射表。

### 为什么运行配置要加两行 mixin 参数

Curios 自带 Mixin，其 `curios.refmap.json` 记录的是 SRG 名（如 `f_19803_`），而开发环境跑的是 Mojang 官方名。不处理的话 Mixin 会拿着 SRG 名去找字段，直接抛 `InvalidAccessorException` 崩溃（详见 [Curios#359](https://github.com/TheIllusiveC4/Curios/issues/359)）。

`build.gradle` 的 `runs.configureEach` 里已加入官方推荐的解法：

```groovy
property 'mixin.env.remapRefMap', 'true'
property 'mixin.env.refMapRemappingFile', "${buildDir}/createSrgToMcp/output.srg"
```

并让所有 run 任务先执行 `createSrgToMcp` 生成映射文件。本模组自身不用 Mixin，该配置没有副作用。

## 环境要求

- JDK 17（已验证：本机 17.0.11）
- 无需单独安装 Gradle，使用仓库内的 `gradlew` / `gradlew.bat`

## 常用命令

```powershell
# 构建（含编译、资源处理、混淆重映射），产物在 build/libs/
.\gradlew.bat build

# 生成数据（语言文件、物品模型、配方）→ src/generated/resources/
.\gradlew.bat runData

# 启动客户端 / 服务端调试
.\gradlew.bat runClient
.\gradlew.bat runServer

# 生成 IntelliJ IDEA / Eclipse 运行配置
.\gradlew.bat genIntellijRuns
.\gradlew.bat genEclipseRuns
```

首次执行会下载 Minecraft 与 Forge 依赖并反编译，耗时较长（十几分钟属正常）。

## 目录结构

```
src/main/java/com/example/lolaccessories/
├── LOLAccessories.java           # 主类：初始化配置目录、注册 DeferredRegister
├── config/
│   ├── GearConfig.java           # 装备配置数据模型（JSON 映射）
│   └── GearConfigManager.java    # 配置加载 / 模板生成 / 缓存 / 重载
├── init/
│   ├── ModItems.java             # 物品注册（黑色切割者…）
│   ├── ModMobEffects.java        # 药水效果注册（护甲削减、热烈）
│   └── ModCreativeModeTabs.java  # 创造模式物品栏
├── item/
│   └── GearItem.java             # 配置驱动饰品基类（Curios ICurioItem）
├── effect/
│   ├── ArmorShredEffect.java     # 切割：护甲削减（属性效果，数值读配置）
│   └── SpeedBurstEffect.java     # 热烈：移速加成（属性效果，数值读配置）
├── event/
│   └── ModForgeEvents.java       # 被动触发（LivingHurtEvent）+ 重载命令
└── data/
    ├── ModDataGenerators.java    # datagen 入口（GatherDataEvent）
    ├── ModLanguageProvider.java  # en_us / zh_cn 语言文件
    ├── ModItemModelProvider.java # 物品模型 JSON
    ├── ModCurioTagsProvider.java # Curios 栏位物品标签（data/curios/tags/items/）
    └── ModRecipeProvider.java    # 配方 JSON

src/main/resources/
├── META-INF/mods.toml            # 模组元数据（构建时由 gradle.properties 替换变量）
├── pack.mcmeta
├── config/lolaccessories/gear/   # 默认装备配置模板（首次运行自动复制到 config 目录）
├── data/lolaccessories/curios/entities/default_player_slots.json  # 把 hands 手饰栏分配给玩家（Curios 不会默认给玩家任何栏位！）
└── assets/lolaccessories/textures/item/  # 32x32 像素化 LOL 官方图标 PNG（datagen 无法生成，必须手放）

src/generated/resources/          # datagen 产物，勿手动编辑
```

## 装备系统与配置文件

每件装备的**全部数值**都由一个 JSON 配置文件驱动，运行目录下的位置：

```
config/lolaccessories/gear/<gear_id>.json
```

首次启动时若文件不存在，会从 jar 内模板自动复制一份；玩家/服主改完数值后执行 `/lolaccessories reload`（需要 2 级权限）即可热生效。字段含义：

```jsonc
{
  "gear_id": "black_cleaver",      // 物品注册名
  "slot": "hands",               // Curios 栏位（hands = 手饰）
  "max_stack_size": 1,
  "attributes": [                  // 穿戴时提供的属性（operation: ADDITION / MULTIPLY_BASE / MULTIPLY_TOTAL）
    { "id": "minecraft:generic.max_health",   "operation": "ADDITION", "amount": 400.0 },
    { "id": "minecraft:generic.attack_damage", "operation": "ADDITION", "amount": 45.0 }
  ],
  "on_hit_effects": [              // 造成伤害时触发的被动（以药水效果实现）
    {                              // 切割：对目标叠护甲削减
      "id": "cleaver_shred", "target": "victim", "enabled": true,
      "per_stack": 0.06,           // 每层削减 6% 护甲
      "max_stacks": 5,             // 最多 5 层
      "max_total": 0.30,           // 总上限 30%（与 max_stacks 取小）
      "duration_seconds": 6.0
    },
    {                              // 热烈：自身移速加成
      "id": "cleaver_rush", "target": "self", "enabled": true,
      "amount": 0.20,              // +20% 移动速度
      "duration_seconds": 2.0
    }
  ]
}
```

### 已收录装备

| 装备 | 栏位 | 说明 |
| --- | --- | --- |
| 黑色切割者 The Black Cleaver | 手饰 hands | +400 生命 / +45 攻击；切割：命中叠 6% 护甲削减 6 秒，至多 30%；热烈：命中自身 2 秒 +20% 移速 |

## 添加一件新装备

1. 在 `src/main/resources/config/lolaccessories/gear/` 新建 `<gear_id>.json`（数值模板）
2. 在 `ModItems` 注册 `GearItem`；在 `ModCreativeModeTabs.displayItems` 里放入物品栏
3. 把 16x16 纹理 PNG 放到 `assets/lolaccessories/textures/item/<注册名>.png`
4. 需要自带被动时在 `ModMobEffects` 注册效果并按 `ModCurioTagsProvider` 补栏位标签；若使用了新栏位，还要把该槽位加进 `data/lolaccessories/curios/entities/default_player_slots.json` 分配给玩家，否则玩家身上不会出现该栏位；最后补语言键
5. 跑 `.\gradlew.bat runData` 再 `.\gradlew.bat build`

## 修改 modid / 包名

`modid` 只在 `gradle.properties` 的 `mod_id` 和 `LOLAccessories.MOD_ID` 两处定义，二者必须一致；
`mods.toml` 里的 `${mod_id}` 会在构建时自动替换。改包名时同步修改 `mod_group_id` 与源码目录。

## 许可证

`mod_license` 目前沿用 MDK 默认值 `All Rights Reserved`（`gradle.properties`）。
正式发布前请改成你选择的许可证，并填入真实的 `mod_authors` 与 `mod_description`。

本仓库基于 Forge MDK 生成，许可证见 `LICENSE.txt` / `CREDITS.txt`。
