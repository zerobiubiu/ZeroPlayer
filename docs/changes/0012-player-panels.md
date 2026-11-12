# 播放器面板语义与沉浸式修复（阶段 1B）

状态：已执行。影响范围：`core/design` 的 `Dialogs.kt`、`feature/player` 的 `PlayerScreen.kt`、相关测试与构建配置。

## 背景

UI/UX 审计阶段 1B 集中修复播放器面板的三个问题：

- **F-16（P1）**：倍速/画面比例/音轨/播放模式面板的每一行都带 `Icons.Outlined.Check`，视觉上像“全部已选中”，只有文案里的“（当前）”区分状态。
- **F-38（P1）**：播放器处于沉浸式时，打开任意底部面板会让系统状态栏重新出现，电池/时间图标压在画面与顶栏上。
- **F-17（P2，因同面板提前）**：“更多”面板里禁用的“播放引擎”行带三行技术说明，与可用操作同权重，抢占视觉重点。

## 根因

### F-38

`ModalBottomSheet` 运行在**独立的对话框窗口**（Material3 的 `ModalBottomSheetDialogWrapper`，一个 `ComponentDialog`）。旧实现只在 Activity 窗口上 `hide(systemBars())`，并在面板状态变化时重复隐藏 Activity 窗口——但这对面板自己的窗口无效，面板窗口默认显示系统栏。

修复：在面板内容里沿父视图链找到实现 `DialogWindowProvider` 的对话框根视图，拿到面板自己的窗口，对其 insets controller 隐藏系统栏；卸载时恢复。所有调用幂等，不做高频重复隐藏。

### F-16

`SheetAction` 没有语义类型，调用方给所有行传同一个 `Check` 图标。修复：引入 `SheetActionKind`（SingleChoice / Action / Navigation / Status）与 `selected`，只有“单选项且被选中”才显示勾选并高亮容器；单选项带 `Role.RadioButton` 语义，读屏播报“已选中/未选中”；导航项行尾显示箭头；状态项不注册点击语义。

### F-17

直接从“更多”面板删除“播放引擎”禁用行。引擎信息仍可在 设置·关于 查看（`InfoRow("播放引擎")` / `InfoRow("引擎状态")`），满足 T-17 的风险缓解。

## 实施

| 文件 | 改动 |
|---|---|
| `core/design/.../Dialogs.kt` | 新增 `SheetActionKind`、`SheetAction.selected`/`showsSelectionMark`；`ActionSheet` 增加 `immersive` 参数；新增公开 `ImmersiveSheetWindowEffect()`；`SheetActionRow` 按语义渲染（勾选/高亮/箭头/不可点击） |
| `core/design/build.gradle.kts` | 新增 `androidx.core:core-ktx`（窗口 insets）与 `testImplementation(junit)` |
| `feature/player/.../PlayerScreen.kt` | 五个面板接入语义类型与 `immersive=true`；删除“播放引擎”行；移除按面板状态重复隐藏 Activity 窗口的旧 `LaunchedEffect`；字幕行改用 `selectable` 单选语义；播放模式各自图标 |
| `core/design/src/test/.../SheetActionSelectionTest.kt` | 新增 8 个 JVM 用例：选中语义 |
| `app/src/androidTest/.../PlayerSheetSemanticsInstrumentedTest.kt` | 新增 6 个用例：单选/动作/导航/状态语义、开关、面板切换 |
| `app/src/androidTest/.../PlayerSheetImmersiveInstrumentedTest.kt` | 新增 4 个用例：沉浸式面板状态栏隐藏/恢复（dumpsys 断言） |

## 设计决策

- **不把所有行统一成同一种交互**：单选、动作、导航、状态四类分别表达，符合任务“不强行统一”的要求。
- **不靠改图标颜色掩盖语义**：未选中项根本不渲染勾选图标，而非把勾选调灰。
- **不增加多余文字**：移除“（当前）”后缀，改由勾选+高亮+读屏语义表达；导航用箭头而非新增说明文字。
- **沉浸式只作用于播放器面板**：`immersive` 默认 false，普通页面面板不受影响。
- **F-19/F-20 不提前实施**：顶栏图标与队列渲染保持原样，仅做 F-16/F-17/F-38 所需的最小共享组件调整。

## 执行验证记录

- 执行环境：Windows 11；JBR 25.0.4；Gradle 9.6.0；AGP 9.5.1；compileSdk 37；模拟器 `Medium_Phone_API_37.0`（API 37，x86_64 + ARM64 转译）。

| 验证 | 结果 |
|---|---|
| `:core:design:testDebugUnitTest` | 8 tests，0 failure |
| `:core:design` / `:feature:player` compileDebugKotlin | 成功 |
| `PlayerSheetSemanticsInstrumentedTest` | 6/6 通过 |
| `PlayerSheetImmersiveInstrumentedTest` | 4/4 通过（dumpsys 断言状态栏隐藏/恢复） |
| 实机五个面板 | 倍速/比例/音轨/字幕/队列仅当前项勾选+高亮；更多无“播放引擎”行、导航带箭头；打开/关闭/重开状态栏均隐藏 |
| `:app:assembleDebug` | 成功 |
| `lintDebug` | 0 error（14 模块） |
| `testDebugUnitTest` | 92 tests，0 failure（原 84，+8） |
| 完整 `connectedDebugAndroidTest` | 42/42 通过，0 failure/error/skipped（原 32，+10） |

截图证据：`docs/ui-ux-audit/implementation/evidence/player-*-after.png` 及深色/横屏/1.3 字号变体，索引见 `evidence/README-1B.md`。

## 版本

用户可见行为变更，按项目规则递增补丁版本：`1.0.2 → 1.0.3`（versionCode 10002 → 10003），同步 `docs/CHANGELOG.md`。

## 后续验证建议

1. 真机（API 26/34/37）复验手势导航与三键导航下面板沉浸式与临时显示行为。
2. TalkBack 手测：单选项播报“已选中/未选中”，状态项不被读成可操作。

## 已知限制

- 模拟器为 x86_64 + ARM64 转译，手势导航/厂商系统栏差异需真机确认。
- 播放列表面板仍只展示模式与上/下一集（F-20 队列渲染属阶段 1C，未实施）。
