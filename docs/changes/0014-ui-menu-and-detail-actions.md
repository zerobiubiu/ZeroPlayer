# 菜单分组与详情页文件操作（阶段 1D）

状态：已执行。影响范围：`core/design` 新增共享文件菜单与对话框；`feature/home` 行菜单改用共享分组；`feature/detail` 新增更多菜单与五项文件操作；`feature/player` 播放协调器新增文件操作保护；`domain` 新增 `PlaybackStopper` 端口；`app` 绑定播放停止器、详情页接线导出/分享回调、版本号；相关测试与文档。

## 背景

UI/UX 审计阶段 1D 修复两个问题：

- **F-22（P2）**：首页文件行菜单 6 项平铺，仅一条分隔线，删除与普通操作同权重，误触风险高。
- **F-21（P1）**：详情页只有播放、字幕与文件信息；重命名/移动/删除/导出/分享都必须回首页长按同一文件，路径重复且容易找错行。

前置阶段成果保持不变：F-37（设置即时保存）、F-16/F-17/F-38（播放器面板）、F-19/F-20（入口归位与真实队列）。

## 根因

### F-22

`FileTreeRow` 的 `DropdownMenu` 直接平铺 `DropdownMenuItem`，分组只靠分享后一条分隔线；删除仅靠红字区分。菜单定义内联在行组件里，详情页无法复用。

### F-21

`MediaDetailScreen` 顶栏只有“文件信息”一个动作；`MediaDetailViewModel` 只注入字幕相关能力，没有文件操作。导出/分享的 SAF 启动器注册在根导航，详情页拿不到回调。

## 实施

### F-22：共享分组菜单

| 文件 | 改动 |
|---|---|
| `core/design/.../FileActionMenu.kt` | 新增：`FileAction` 枚举（名称+图标全应用唯一）、`fileActionMenuGroups`（信息→传输→管理→危险）、`FileActionMenuContent`（组间 `HorizontalDivider`，删除 error 色独立最后）、`rememberFileActionMenuGroups` |
| `feature/home/.../FileTreeRow.kt` | 行菜单改用共享构建器；长按与三点按钮共用同一 `DropdownMenu`；首页视频行子集不含“文件信息” |

分组规则：信息（视频详情/文件信息）→ 传输（导出/分享）→ 管理（重命名/移动）→ 危险（删除）。不加组标题与逐项说明（ux-writing 功能极简）；删除始终单独成组、位于最后、error 色、保留二次确认。目录与非视频文件按类型裁剪信息/传输组。

### F-21：详情页更多菜单与五项操作

| 文件 | 改动 |
|---|---|
| `core/design/.../FileDialogs.kt` | 新增：`NameDialog`/`MoveDialog`/`DeleteDialog` 与 `nameError`/`canConfirmName` 从 `feature/home` 迁入并公开；`DeleteDialog` 改基于 `ConfirmDialog(destructive=true)`，全应用删除确认仅一套组件 |
| `feature/home/.../HomeDialogs.kt` | 仅保留首页对话框状态 `HomeDialog`；对话框本体迁出 |
| `feature/detail/.../MediaDetailScreen.kt` | 顶栏保留“文件信息”，新增“更多”（MoreVert）打开共享分组菜单（导出/分享/重命名/移动/删除，不含“视频详情”）；重命名/移动/删除复用共享对话框；新增 `onExportFile`/`onShareFile` 回调 |
| `feature/detail/.../MediaDetailViewModel.kt` | 注入 `FileTreeRepository` 与 `PlaybackStopper`；新增 `rename`/`move`/`delete`/`directoriesForMove`/`treeEntry`；`isWorking` 串行保护；操作结果经消息反馈，媒体记录由 `observeById` 自动同步 |
| `app/.../PrivatePlayerRoot.kt` | 导出回调提取为 `exportFile`，首页与详情页共用同一 SAF 启动器；详情页接线 `onExportFile`/`onShareFile` |

重命名校验非法字符与重复（`NameDialog` 行内提示+禁用确认），保留扩展名由用户输入决定（与首页一致）；移动复用 `MoveDialog` 与目录过滤规则；删除复用 `DeleteDialog`（点名对象、不可撤销、单次确认），成功后 `media==null` 自动返回，首页 `observeDirectory` 同步。导出/分享复用根导航唯一实现，单文件，取消/失败经 `FileTransferViewModel` 既有反馈。

### 播放安全（F-21 关键约束）

| 文件 | 改动 |
|---|---|
| `domain/.../PlaybackStopper.kt` | 新增端口：`stopIfAffected(path)`；领域层不感知播放实现；`NoOpPlaybackStopper` 供无会话场景与测试 |
| `feature/player/.../PlaybackCoordinator.kt` | 新增 `stopForFileOperation(path)`：命中当前媒体/活动队列/待装载目标时经 `closeFromPlayerUi` 停服，等待会话释放后清除待装载/续播引用；未命中不打断无关播放 |
| `feature/player/.../PlaybackSession.kt` | 新增 `currentTreePath()` 只读访问当前媒体树路径 |
| `app/.../playback/PlaybackStopperModule.kt` | 新增：`CoordinatorPlaybackStopper` 把端口接到唯一协调器（Hilt 绑定） |
| `feature/home/.../HomeViewModel.kt` | 注入 `PlaybackStopper`；rename/move/delete 前先 `stopIfAffected` |

协调放在同时可见文件仓库与播放协调器的应用层（方案 a）：`FileTreeRepository` 不引入播放逻辑，MediaSession 生命周期契约不变（关闭停服、重开新会话）。队列随会话销毁，重开按目录重新列举，天然不保留已删除/已移动引用。重命名/移动当前播放媒体同样先停服，避免引擎读取失效路径；重开后按新路径装载并续播（断点已持久化）。

## 设计决策

- **共享菜单与对话框放 core/design**：首页与详情页都只依赖 core/design/domain，迁移后单一实现，不放宽 feature 内部可见性。
- **删除确认统一为 ConfirmDialog**：顺带关闭 F-32 的重复实现（首页删除与详情删字幕外观/代码同源）；不新增第三套对话框。
- **播放停止走领域端口**：feature 页面不依赖 feature/player；应用层绑定，符合依赖方向。
- **导出/分享回调从根传入**：SAF 启动器必须在稳定位置注册，详情页不重复实现。
- **菜单不加组标题**：分组用分隔线表达，避免菜单变长（审计风险项）。

## 测试

| 层 | 文件 | 用例 |
|---|---|---|
| JVM 单测 | `core/design/.../FileActionMenuGroupingTest.kt` | 11 例：分组顺序/子集/目录裁剪/删除独立最后（7）+ 名称校验（4） |
| 仪器化 F-22/F-21 | `app/.../FileMenuAndDetailActionsInstrumentedTest.kt` | 7 例：共享菜单分组与回调；首页行菜单分组+删除确认+长按同源；详情更多菜单五项；重命名校验与同步；删除确认+自动返回；移动选择器与同步；导出/分享回调 |
| 仪器化一致性/安全 | `app/.../FileTreeConsistencyInstrumentedTest.kt` | +3 例：详情与首页同仓库结果一致；删除正在播放媒体先停服且队列不残留、无关文件不打断播放；移动正在播放媒体先停服且重开按新目录重建队列 |

既有 `DetailSettingsInstrumentedTest` 的 `MediaDetailViewModel` 构造与 `MediaDetailScreen` 调用同步更新（新增 `PlaybackStopper` 与导出/分享参数），断言未削弱。

## 执行验证记录

- 执行环境：Windows 11；JBR（`C:\Program Files\Android\Android Studio\jbr`）；Gradle 9.6.0；AGP 9.5.1；compileSdk 37；模拟器 `Medium_Phone_API_37.0`（API 37，x86_64 + ARM64 转译），**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。

| 验证 | 结果 |
|---|---|
| `:app:assembleDebug` | 成功；`aapt2 dump badging` 确认 `versionCode=10005 versionName=1.0.5`，仅 `arm64-v8a` |
| `lintDebug`（根任务，14 模块） | **0 error** |
| `testDebugUnitTest`（全模块） | **113 tests，2 skipped，0 failure / 0 error**（原 102，新增 11） |
| `:app:connectedDebugAndroidTest`（headless） | **60/60 通过，0 failure / 0 error / 0 skipped**（原 50，新增 F-22/F-21 7 例 + 一致性/安全 3 例；F-16/17/19/20/37/38 与 MediaSession 生命周期回归全部通过） |

截图证据：`docs/ui-ux-audit/implementation/evidence/`，索引见 `evidence/README-1D.md`。

## 版本

用户可见行为变更，按项目规则递增补丁版本：`1.0.4 → 1.0.5`（versionCode 10004 → 10005），同步 `docs/CHANGELOG.md`。

## 已知限制

- 模拟器为 x86_64 + ARM64 转译，SAF 云端目标、实体蓝牙与厂商差异需真机确认。
- 播放停止等待设 10 秒上限；极端情况下引擎释放超时仍会继续文件操作（与既有释放屏障一致的兜底），真实设备时序需复核。
- 重命名保留扩展名依赖用户输入（与首页既有行为一致），未强制锁定扩展名。
- 截图收尾按时间压缩：仅取证核心静态集（浅色 9 张、深色首页/行菜单/详情 3 张、1.3 倍字号两菜单 2 张，共 14 张）；深色更多菜单、2.0 倍字号、横屏及播放态/结果态截图未取证，播放安全以仪器化测试为准（见 `evidence/README-1D.md` 限制）。
- 批量操作（F-41）不在本阶段范围。
