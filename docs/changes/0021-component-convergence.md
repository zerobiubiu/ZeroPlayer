# 组件收敛：开关单目标、播放器 Token、触控目标与死组件清理（阶段 2E）

状态：已执行。影响范围：`core/design` SwitchRow 与死组件清理；`feature/player` PlayerTokens 与触控目标；版本号。

## 背景

UI/UX 审计阶段 2E 收敛组件（F-32 已在 1D 完成，本阶段核验）：

- **F-32（P2）**：删除对话框两套实现 —— **1D 已统一为 ConfirmDialog**，本阶段核验单实现并回填 findings 状态。
- **F-33（P2）**：SwitchRow 整行 clickable + Switch 自身 onCheckedChange，读屏双目标。
- **F-34（P2）**：播放器完全绕开设计 Token（散落 dp 与硬编码 #4A84FF）。
- **F-36（P2）**：速度胶囊 ~32dp、传输键 44dp、锁定 chip ~34dp，触控 <48dp。
- **F-31（P3）**：五个设计系统组件/模型无调用点。

## F-33 开关单目标

`SwitchRow` 外层改 `toggleable(value = checked, role = Role.Switch, onValueChange = ...)`，内部 `Switch(checked, onCheckedChange = null)`。整行单一可切换节点，读屏只停一次；标题+描述随 toggleable 合并语义。视觉不变。

## F-34 播放器 Token

新增 `PlayerTokens`（feature/player）：色板（Text/TextSecondary/Accent/Scrim/PillContainer/TrackInactive）与关键尺寸（传输键/图标/最小触控）的单一来源；间距复用应用 `Spacing` 刻度。播放器保持独立深色媒体语境，强调色仍为媒体控制专用蓝（不强制应用紫），规则写入设计系统。`PlayerOverlays` 的本地色板改为引用 PlayerTokens。

## F-36 触控目标 ≥48dp

- 速度胶囊：`defaultMinSize(minHeight = 48.dp)`；
- 传输键：44dp → 48dp（大键 56dp 保留）；
- 锁定 chip：`defaultMinSize(minHeight = 48.dp)`。
视觉尺寸尽量保留，热区扩至 ≥48dp。

## F-31 死组件清理

确认无调用点后删除：`FilterChipRow`、`ClickableSettingRow`、`PrimaryActionButton`、`InputDialog`、`MediaCardData.kt`（含 `toCardData`）、`MediaGridCard`、`MediaListRow`、`MetaChip`、`cardInteraction`（旧媒体库/网格首页遗留）。**保留 `SelectionTopBar`**（F-41 多选复用）。删除后全量编译通过。

## F-32 核验

`DeleteDialog` 基于 `ConfirmDialog(destructive = true)`，首页/详情/字幕删除共用单实现（1D 已落地）；本阶段核验并回填 findings 状态。

## 测试

| 层 | 文件 | 用例 |
|---|---|---|
| 仪器化 | 既有设置/播放器用例 | SwitchRow 单目标、播放器控件回归（完整套件） |
| 编译 | 全模块 | 死组件删除后无引用残留 |

## 执行验证记录

见 `docs/ui-ux-audit/implementation/execution-log.md` 阶段 2E 小节与 `evidence/README-2E.md`。

## 版本

用户可见交互与组件变更，按项目规则递增补丁版本：`1.0.10 → 1.0.11`（versionCode 10010 → 10011），同步 `docs/CHANGELOG.md`。

## 已知限制

- 播放器 Token 仅统一来源与关键尺寸，未重写全部覆盖层间距（保留深色语境，最小差异）。
- TalkBack 单目标朗读顺序需真机手测复核。
