# LOLAccessories

把经典 MOBA 风格的装备系统带入 Minecraft 的 Forge 模组。**108 件 Curios 饰品**、**101 个配方**、**四阶成长体系**，全部数值由 JSON 配置驱动，无需改代码即可调整。

> A Minecraft Forge mod that brings a MOBA-inspired equipment system into the game: 108 curio-slot accessories, 101 recipes, four progression tiers — all data-driven via JSON.

| 项 | 值 |
| --- | --- |
| Minecraft | 1.20.1 |
| Forge | 47.4.10 |
| 映射 | `official`（Mojang 官方名） |
| Java | 17 |
| modid | `lolaccessories` |
| 许可证 | **MIT** |

## 特性

### 四阶品阶体系

| 品阶 | 获取方式 | 内容 |
| --- | --- | --- |
| **普通**（tier1） | 工作台合成 | 长剑、短剑、B.F. 大剑、布甲、十字镐、多兰系列、女神之泪等基础件 |
| **史诗**（tier2） | 锻造台升级 | 斑驳熔渣、凯旋之石、以太精魄、复合弓、荆棘之甲、耀光、狂热之刃等 |
| **传说**（tier3） | 锻造台升级（下界之星 ×3 + 龙蛋 ×1 + 金币） | 黑色切割者、卢登的回声、无尽之刃、幻影之舞、斯特拉克的挑战护手、饮血剑、海克斯注力刚壁、守护天使、日炎圣盾、振奋盔甲、败魔、舒瑞娅的战歌等 |
| **神话**（tier4） | 终极装备 | 澄空之愿、魔王之心 —— 带旋转魔法阵特效 |

### 系统

- **金币经济**：专用金币物品，锻造台取件时自动从背包与末影箱扣除
- **主动技能**（按键触发）：时间停止、火箭腰带冲刺等，各带专属冷却 HUD 条
- **被动效果**：命中/击杀/阵亡触发、救主（守护天使重生）、护盾球罩、灼烧、免碰撞（幻影之舞）、层数叠加（梅贾的窃魂卷与黑暗封印共享荣耀层数池）、伤害增幅、受治疗与护盾强度、终极技能急速
- **视觉特效**：神话装备图标下方旋转魔法阵、护盾球罩（败魔 / 原生质护带 / 炽天使之拥 / 冬之誓）、网络广播的特效窗口
- **数据驱动**：所有冷却、倍率、持续时间、半径、阈值都在 `config/lolaccessories/gear/<装备id>.json`
- **本地化**：简体中文 + 英文

## 依赖（均为必装）

| 模组 | 版本 |
| --- | --- |
| Minecraft Forge | 1.20.1-47.4.10 |
| [Curios API](https://www.curseforge.com/minecraft/mc-mods/curios) | 5.14.1+1.20.1 |
| [Iron's Spells 'n Spellbooks](https://www.curseforge.com/minecraft/mc-mods/irons-spells-n-spellbooks) | 1.20.1 线 |
| [Apothic Attributes](https://www.curseforge.com/minecraft/mc-mods/apothic-attributes) | `attributeslib` 1.0.0+ |

## 安装

把 `lolaccessories-1.0.jar` 与上述依赖一起放入 `mods` 目录即可。

## 构建（开发者）

```powershell
# 构建，产物在 build/libs/
.\gradlew.bat build

# 生成数据（语言文件、模型、配方）→ src/generated/resources/
.\gradlew.bat runData

# 启动调试客户端 / 服务端
.\gradlew.bat runClient
.\gradlew.bat runServer
```

无需单独安装 Gradle，用仓库内的 `gradlew` / `gradlew.bat` 即可。首次构建会下载并反编译 Minecraft 与 Forge 依赖，耗时较长。

> **Mixin 说明**：本模组使用 Mixin（幻影之舞免碰撞、神话法阵渲染等），`build.gradle` 的 run 配置已加入
> `mixin.env.remapRefMap` / `mixin.env.refMapRemappingFile` 并让 run 任务先执行 `createSrgToMcp`，
> 以处理 Curios 自带 Mixin 的 SRG 名问题（详见 [Curios#359](https://github.com/TheIllusiveC4/Curios/issues/359)）。

## 配置

装备配置文件位于运行目录：

```
config/lolaccessories/gear/<装备id>.json
```

首次启动若不存在会从 jar 内模板自动复制。修改后执行 `/lolaccessories reload`（需 2 级权限）热生效，无需重启。

客户端特效质量在 `config/lolaccessories-client.toml`（总开关 / 粒子密度 / 亮度）。

## 目录结构

```
src/main/java/com/example/lolaccessories/
├── LOLAccessories.java        # 主类：注册 DeferredRegister、配置目录初始化
├── config/                    # 装备配置数据模型与加载/缓存/重载
├── init/                      # 物品、属性、创造模式物品栏、标签注册
├── item/GearItem.java         # 配置驱动的饰品基类（Curios ICurioItem）
├── event/                     # 各系列被动与主动技能的事件处理
├── client/                    # 特效渲染、冷却 HUD、法阵渲染
├── networking/                # 特效与冷却的网络包
├── crafting/                  # paid_smithing 付费锻造配方
├── mixin/                     # 免碰撞、法阵叠加、锻造取件扣费等
├── compat/                    # Curios / 铁魔法 / Apothic 兼容层
└── data/                      # datagen（语言、模型、配方、标签）

src/main/resources/
├── META-INF/mods.toml         # 模组元数据（构建时由 gradle.properties 替换变量）
├── config/lolaccessories/gear/          # 装备配置模板（108 件）
├── data/lolaccessories/recipes/         # 配方（101 个）
└── assets/lolaccessories/textures/      # 物品与特效贴图
```

## 新增一件装备

1. 在 `src/main/resources/config/lolaccessories/gear/` 新建 `<gear_id>.json`
2. 在 `ModItems` 注册 `GearItem`，并加入创造模式物品栏
3. 放贴图到 `assets/lolaccessories/textures/item/<注册名>.png`
4. 在 `data/lolaccessories/recipes/` 加配方；按品阶加入 `ModGearTierTagsProvider` 的 tier 标签
5. 补语言键（`ModLanguageProvider`）
6. 跑 `.\gradlew.bat runData` 再 `.\gradlew.bat build`

## 许可证

本项目采用 **MIT** 许可证，详见 [LICENSE.txt](LICENSE.txt)。

> **免责声明**：本模组为非商业粉丝向作品。英雄联盟及相关装备名称、数值与美术素材的商标权和著作权归
> Riot Games, Inc. 所有。本模组与 Riot Games 无关联，亦未获其认可或赞助。MIT 许可证仅覆盖本项目
> 原创的代码、配置与素材，不授予任何第三方知识产权的使用权。
>
> *This is a non-commercial fan project. League of Legends and all associated item names, statistics and
> artwork are trademarks and copyrights of Riot Games, Inc. This project is not affiliated with or endorsed
> by Riot Games. The MIT License covers only the original source code, configuration files and assets
> authored by this project.*
