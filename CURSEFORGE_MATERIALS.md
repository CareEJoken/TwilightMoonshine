# CurseForge Release Materials — Twilight Moonshine 1.0.0

本项目自己的发布物料箱。`CURSEFORGE_PROJECT_DESCRIPTION`（下一节）可直接整段粘贴到
CurseForge 项目页的 Description 编辑器（支持 Markdown）。

---

## 1. 可粘贴的项目描述

````markdown
# Twilight Moonshine

An addon for **Twilight Forest** (1.21.1) by CareEJoken.

> 中文说明：这是一个暮色森林附属模组：月兔与月光、月光私酿与月石建材、重新启用的蘑菇塔与任务岛、新的药水与秘密配方，以及夕阳甲虫乐队。依赖暮色森林 4.8+。

## What does it do?

**Moon Rabbits & the Moon**
- Moon rabbits with ambient/hurt/death/jump sounds, a sneeze mechanic, and grow-by-feeding (with its own advancement).
- A moon rabbit trophy for your base.
- The Moonshine potion: 8 minutes of Fire Resistance + Water Breathing + Night Vision. Hand a moon rabbit three distinct potions (Twilight / Glowing / Resistance) and it trades you one.

**Brewing & Alchemy**
- New potions: Resistance (3 tiers), Glowing (base + long), Twilight.
- **Twilight Embrace**: while it lasts, twilight animals follow you and hostile mobs stand down.
- Secret pages that unlock hidden recipes (moon rabbit sneezes, luring animals, glowing trails).
- New crafting chain: moon stone shards → moon stone bricks → full moon stone building family (block/stairs/slab/wall), plus twilight plant extract, twilight glow essence, and twilight alloy powder.
- Potion arrows: any potion + 8 arrows = 8 effect arrows.

**Twilight Forest Adjustments**
- **Mushroom Tower** and **Quest Island** generation re-enabled (commented out in TF 4.8).
- The Mushroom Tower is shown on the magic map with a custom decoration and works with `/locate`.
- Rebalanced twilight daylight colors.
- 7 new advancements, including "Embrace Day" and "The Sunset Beetle Band".

## Requirements

- Minecraft **1.21.1**
- NeoForge **21.1.x**
- Twilight Forest **4.8 or newer** (required)

## Installation

1. Install NeoForge 21.1.x and Twilight Forest 4.8+ for 1.21.1.
2. Drop `twilightmoonshine-1.0.0.jar` into your `mods` folder.
3. Launch and enjoy.

## Credits

Inspired by and built alongside the Twilight Forest mod by Team Twilight. Twilight Forest is licensed under LGPLv3; Twilight Moonshine is licensed under MIT.

## Support

Found a bug or have an idea? Leave a comment on this project page.
````

## 2. 项目设置要点（建项时）

| 字段 | 值 |
|---|---|
| 项目名称 | Twilight Moonshine |
| 游戏 | Minecraft |
| 项目类型 | Mod |
| 分类 | Addons → Twilight Forest（若列表里没有就选 Addons/Adventure and RPG,并在描述里写清是 TF 附属） |
| 图标 | `pack.png`（512×512，位于 src/main/resources，可直接使用） |
| 版本号 | 1.0.0（与 gradle.properties 的 `mod_version` 一致） |

## 3. 上传文件步骤（重要）

1. 在项目页 **Files → Upload File** 上传 `D:\twilight forest\TwilightMoonshine\build\libs\twilightmoonshine-1.0.0.jar`（此 jar 为 release 构建，无任何实验/调试代码残留）。
2. 选择游戏版本：**1.21.1**；Modloader：**NeoForge 21.1.220**（上传页面的版本下拉里选 21.1.220；如果是列表勾选就把看到的最接近的 21.1.x 勾上）。
3. **依赖标记（关键）**：在 Upload 页面的 **Dependencies** 区域搜索 *Twilight Forest*，将其标记为 **Required**。这样玩家缺 TF 时 CurseForge 会自动提示并一键安装。
4. 发布前用 **Edit / Review** 检查：文件 `LICENSE`（MIT）、changelog 贴 `CHANGELOG.md` 的 1.0.0 一节。
5. 发布为 Public 后，项目页 Get URL → 之后可回头把 `neoforge.mods.toml` 的 `links` 字段补上（目前暂缺）。

## 4. 截图清单（CurseForge 新项目要求 ≥3 张，建议 5-8 张）

游戏内 F2 截图（建议 1920×1080 或更大，避免压缩模糊），从这几个里挑：

1. 月兔在月光下的暮色森林（傍晚/夜晚效果最佳，突出"月光"主题）
2. 月光私酿酒瓶特写（手持或物品展示框）
3. 月石建筑家族搭的建筑（砖块+楼梯+台阶+墙的组合）
4. **蘑菇塔**在地图上的魔法地图显示（打开地图界面截图）
5. 蘑菇塔实景全景
6. 任务岛实景全景
7. 妖精之眼/荧光引路的秘密配方探索场景（荧光药水或挖开秘密页）
8. 夕阳甲虫乐队进度达成画面（三只甲虫 + 唱机共鸣）

> 说明：蘑菇塔与任务岛的坐标可在配置里调整或按种子搜索；若临时开 `/locate mushroom_tower`（本 mod 提供的是地图装饰，原版 `/locate` 对本 mod 的塔不一定有效——这句话按实际情况核对后保留或删除）。
