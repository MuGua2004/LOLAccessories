# LOLAccessories 更新日志 / Changelog

## 1.2

### English

- **Mythic item icons redrawn** — Clear Sky's Wish and Demon Heart now use
  League of Legends style 32x32 pixel art icons: a faceted sky-blue gem with
  gold filigree and light rays, and a demonic heart wrapped in black iron
  thorns and curved horns with a dark violet core
- Sigil FX textures and the sigil renderer are **unchanged** in this release

### 中文

- **神话装备图标重绘** —— 澄空之愿与魔王之心改为英雄联盟风格的 32×32 像素图标：
  澄空之愿为天青宝石切面配金框与光芒，魔王之心为缠绕黑荆棘与犄角的恶魔心脏（暗紫核心）
- **法阵贴图与法阵渲染逻辑本版本未做任何改动**

---

## 1.1

### English

- **Recipes** — 15 legendary items now use their official League of Legends build paths:
  Sterak's Gage, Phantom Dancer, Bloodthirster, Guardian Angel, Mejai's Soulstealer,
  Actualizer, Sunfire Aegis, Zeke's Convergence, Bandlepipes, Dusk and Dawn,
  Endless Hunger, Hexoptics C44, Spirit Visage, Experimental Hexplate, Mortal Reminder
- **Resolved two component conflicts** — Actualizer adds a gold ingot to differ from
  Luden's Echo; Bandlepipes uses a different slot order than Zeke's Convergence
  (both share identical official components)
- **Legendary smithing costs** — restored the Nether Star x3 + Dragon Egg x1 requirement
  for 16 items that were missing it
- **Mythic sigil FX** — fixed rotation not rendering (angle reached ~2e9 radians, far
  beyond float precision, so it was effectively frozen) and the skewed / black rendering
  (blit coordinates are transformed by the pose matrix; the UV now samples the full
  texture instead of only the top-left 31%)
- **Combat rules** — Iron's Spellbooks spell damage no longer triggers effects marked
  "triggered by physical damage"
- **Mortal Reminder** — attack damage replaced with an equal amount of projectile damage
  (+35% arrow damage, using the mod's 10 AD = 10% projectile conversion)

### 中文

- **配方** —— 15 件传说装备改为官方合成路径：斯特拉克的挑战护手、幻影之舞、饮血剑、
  守护天使、梅贾的窃魂卷、Actualizer、日炎圣盾、基克的聚合、班德尔风笛、日月轮回、
  无尽饥渴、法术镜片 C44、振奋盔甲、海克斯注力刚壁、凡性的提醒
- **解决两处材料冲突** —— Actualizer 加金锭以区别于卢登的回声；班德尔风笛与基克的聚合
  采用不同槽位顺序（两件官方配方本就完全相同）
- **传说锻造花费** —— 为 16 件缺失的装备补回「下界之星 ×3 + 龙蛋 ×1」
- **神话法阵特效** —— 修复旋转不生效（角度达 ~2e9 弧度，远超 float 精度而实际冻结）
  以及菱形 / 发黑渲染（blit 坐标会受 pose 矩阵变换；UV 改为采样完整贴图，不再只取左上 31%）
- **战斗规则** —— 铁魔法法术伤害不再触发「由物理伤害触发」的效果
- **凡性的提醒** —— 攻击力改为等额弹射物伤害（+35% 弹射物伤害，按 10 攻击力 = 10% 换算）

---

## 1.0 — Initial Release / 首发版本

### English

First public release.

- **108 curio-slot accessories** across **4 tiers**: Common (tier 1), Epic (tier 2), Legendary (tier 3), Mythic (tier 4)
- **101 crafting & smithing recipes** — workbench crafts for tier 1, paid smithing upgrades for tier 2/3
- **Gold-coin economy** — costs auto-deducted from inventory and Ender Chest on smithing pickup
- **Legendary upgrade cost**: Nether Star x3 + Dragon Egg x1 + gold coins
- **2 Mythic items** — Clear Sky's Wish and Demon's Heart, with rotating magic sigil FX under the item icon
- **Active skills** with keybind triggers — Time Stop, Rocketbelt dash — each with a dedicated cooldown HUD bar
- **Passive effects** — on-hit, on-kill, life-saving (Guardian Angel revive), shield shells, burn DoT, no-collision (Phantom Dancer), stacking (Mejai's Soulstealer & Dark Seal share one Glory pool), damage amplification (Mortal Reminder / Lord Dominik's Regards), incoming heal & shield power, ultimate haste
- **Visual effects** — shield bubbles (Rookern / Protoplasm / Seraph's / Fimbulwinter), per-item FX windows broadcast over the network
- **Fully data-driven** — tune every cooldown, multiplier, duration, radius and ratio in `config/lolaccessories/gear/<item_id>.json`, no recompile needed
- **Localization** — English (en_us) and Simplified Chinese (zh_cn)
- **Requires** — Minecraft 1.20.1, Forge, Curios API, Iron's Spells 'n Spellbooks, Apothic Attributes

### 中文

首次公开发布。

- **108 件 Curios 槽位饰品**，贯穿 **4 个品阶**：普通（1 级）、史诗（2 级）、传说（3 级）、神话（4 级）
- **101 个合成与锻造配方** —— 1 级工作台合成，2/3 级锻造台付费升级
- **金币经济** —— 锻造台取件时自动从背包与末影箱扣除花费
- **传说升级花费**：下界之星 ×3 + 龙蛋 ×1 + 金币
- **2 件神话装备** —— 澄空之愿、魔王之心，物品图标下方带旋转魔法阵特效
- **主动技能**（按键触发）—— 时间停止、火箭腰带冲刺，各自带专属冷却 HUD 条
- **被动效果** —— 命中/击杀触发、救主（守护天使重生）、护盾球罩、灼烧、免碰撞（幻影之舞）、层数叠加（梅贾的窃魂卷与黑暗封印共享荣耀层数池）、伤害增幅（凡性提醒 / 多米尼克领主的致意）、受治疗与护盾强度、终极技能急速
- **视觉特效** —— 护盾球罩（败魔 / 原生质护带 / 炽天使之拥 / 冬之誓），按物品的网络广播特效窗口
- **完全数据驱动** —— 所有冷却、倍率、持续时间、半径与阈值均可在 `config/lolaccessories/gear/<装备id>.json` 调整，无需重编译
- **本地化** —— 简体中文与英文
- **依赖** —— Minecraft 1.20.1、Forge、Curios API、Iron's Spells 'n Spellbooks、Apothic Attributes

---

## 后续更新日志格式建议（供参考）

```
## 1.2
### Added / 新增
- ...

### Fixed / 修复
- ...

### Changed / 调整
- ...
```
