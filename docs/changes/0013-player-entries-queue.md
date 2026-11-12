# 播放器入口归位与真实播放队列（阶段 1C）

状态：已执行。影响范围：`feature/player` 的 `PlayerScreen.kt`、`PlayerOverlays.kt`、`PlaybackSession.kt`、`PlaybackCoordinator.kt`、`PlayerViewModel.kt`，新增 `QueueSelection.kt`；`app` 版本号与 debug 测试宿主；相关测试与文档。

## 背景

UI/UX 审计阶段 1C 修复播放器两个 P1 问题：

- **F-19（P1）**：顶栏第二个图标是 `ScreenRotation`（旋转语义），实际打开“画面比例”面板；真正的“旋转屏幕”藏在“更多”面板第二层。图标与结果不一致，旋转这一高频操作需要两次点击。
- **F-20（P1）**：底栏“播放列表”面板只有上一集/下一集与 4 个播放模式，没有队列条目、当前项高亮或跳转；标题“播放列表 · 2/2”名不副实。

前置阶段成果保持不变：F-37（设置即时保存）、F-16（面板单选语义）、F-17（移除引擎技术行）、F-38（面板沉浸式）。

## 根因

### F-19

`PlayerTopBar` 把“画面比例”入口画成了旋转图标（`Icons.Outlined.ScreenRotation`，描述“画面比例与旋转”），而旋转动作只作为“更多”面板的一个 `SheetAction` 存在。一个图标承载两种语义，且高频动作被降级到二级面板。

### F-20

队列面板直接用通用 `ActionSheet` 拼了 6 个动作/单选项，从未渲染 `PlayerUiState.queue`。`PlaybackSession.playQueueIndex` 是私有挂起函数，只被 `playNext`/`playPrevious`/自动连播调用，UI 没有受控的跳转入口（阶段 0 决策 D3 已确认要做跳转，但必须新增受控 API，不暴露可变索引）。

## 实施

### F-19：顶栏比例/旋转归位

| 文件 | 改动 |
|---|---|
| `feature/player/.../PlayerOverlays.kt` | `PlayerTopBar` 新增 `onRotate` 回调；比例入口改用 `Icons.Outlined.AspectRatio`（描述“画面比例”）；新增旋转按钮 `Icons.Outlined.ScreenRotation`（描述“旋转屏幕”）；底栏队列入口描述改为“播放队列” |
| `feature/player/.../PlayerScreen.kt` | 顶栏接线 `onRotate = { activity?.toggleOrientation() }`（复用既有横竖屏切换逻辑）；从“更多”面板删除“旋转屏幕”行，全应用仅保留顶栏一个旋转入口 |

顶栏保持紧凑：返回、片名、画面比例、旋转屏幕、字幕、更多，共 5 个图标按钮；标题仍按剩余宽度单行省略。画面比例面板与“更多”面板的锁定/音轨/比例导航行原样保留。

### F-20：真实播放队列

| 文件 | 改动 |
|---|---|
| `feature/player/.../QueueSelection.kt` | 新增：跳转受理判定纯逻辑 `QueueSelection.shouldAccept`（空队列/越界/重复目标/切换中 → 不受理） |
| `feature/player/.../PlaybackSession.kt` | 新增公开受控入口 `playQueueItem(index)`：主线程同步受理判定，受理后经与 `playNext`/`playPrevious` 相同的切源门（`transitionMutex`）调用既有 `playQueueIndex`；`queueSwitchInFlight` 标记防止快速连点反复切源；外部 `open()` 重新装载时清除标记；`playQueueIndex` 的媒体缺失文案改为中性的“文件不存在或已被删除” |
| `feature/player/.../PlaybackCoordinator.kt`、`PlayerViewModel.kt` | 逐层委托 `playQueueItem(index)`；不暴露可变 `queueIndex` |
| `feature/player/.../PlayerScreen.kt` | 新增 `PlaybackQueueSheet`：自定义 `ModalBottomSheet`（同字幕面板模式），复用 `ImmersiveSheetWindowEffect` 保持沉浸式；上半部 `LazyColumn` 渲染 `state.queue`（限高 320dp 且不超过窗口一半），仅当前项高亮带勾（F-16 语义），打开/当前项变化时 `scrollToItem` 定位；下半部保留上一集/下一集与 4 个播放模式；空队列复用 `EmptyState`；原 `SubtitleRow` 泛化为 `SelectionRow`（字幕/队列/模式共用单选行）；“画面比例”“更多”面板内容抽为共享构建器 `aspectSheetActions`/`moreSheetActions`，页面与测试断言同一份内容 |

队列条目标题取路径文件名（`substringAfterLast('/')`），不展示完整路径（隐私）；不新增逐条副标题。当前项以 `state.queueIndex` 判定；播放状态变化后面板经同一 `PlayerUiState` 自动同步。

## 受控 API 设计

```
UI（队列面板点击）
  → PlayerViewModel.playQueueItem(index)
  → PlaybackCoordinator.playQueueItem(index)
  → PlaybackSession.playQueueItem(index)
      受理判定（主线程同步，QueueSelection.shouldAccept）：
        - 空队列 / index 越界 → 无操作
        - index == 当前 queueIndex → 无操作（重复点击）
        - 目标 mediaId == 当前 mediaId → 无操作（陈旧/重复条目）
        - 已有跳转在执行（queueSwitchInFlight）→ 无操作（快速连点）
      受理后 scope.launch + transitionMutex.withLock：
        → 既有 playQueueIndex(index, settings)
          （ensureMedia 兜底解析、媒体存在性校验、更新 queueIndex、playMedia）
```

- **索引而非 mediaId**：`QueueItem` 含 `mediaId + path`，但 mediaId 可能为 0（尚未入库，需 `ensureMedia` 兜底），且 UI 高亮/定位都以索引为准；索引在锁内二次校验，媒体缺失走既有错误路径。
- **MediaSession 契约不变**：跳转复用 `playMedia`（与上一集/下一集/自动连播同一路径），`SessionMedia` 随切源更新；关闭播放页仍停服、重开仍新会话。
- **队列顺序与播放模式**：队列始终按目录列举顺序渲染；随机/循环模式只影响“下一集”与自动连播的选取（既有逻辑），不改变队列展示顺序。

## 设计决策

- **旋转只留一个入口**：顶栏独立按钮；“更多”面板删除旋转行，避免双入口（审计建议二选一）。
- **比例与旋转严格区分**：画面比例（Fit/Fill/裁剪/16:9/4:3）是视频渲染参数；旋转是 Activity 方向控制，二者互不影响。
- **队列面板用自定义面板而非扩展 ActionSheet**：`ActionSheet` 的普通 `Column` 不适合长队列；不改动共享组件，避免波及其他面板（与字幕面板同一模式）。
- **术语改“播放队列”**：这是当前目录的播放队列，不是媒体库播放列表；入口描述与面板标题同步更名，不做播放列表 CRUD/持久化（属后续阶段，明确不在本次范围）。
- **测试断言生产构建器**：`aspectSheetActions`/`moreSheetActions`/`PlaybackQueueSheet` 为公开函数，仪器化测试直接渲染生产代码，不复制平行实现。

## 测试

| 层 | 文件 | 用例 |
|---|---|---|
| JVM 单测 | `feature/player/src/test/.../QueueSelectionTest.kt` | 10 例：空队列、负索引、越界、末项合法、当前索引重复、同媒体陈旧条目、未知 mediaId 不误杀、切换中连点忽略、切换完成后再点受理、不同目标受理 |
| 仪器化 F-19 | `app/src/androidTest/.../PlayerEntriesInstrumentedTest.kt` | 4 例：比例图标打开比例面板（单选语义+选择回调）；旋转一次点击切换横竖屏并可切回；五个入口描述准确且无旧混合描述；更多面板无旋转/引擎行、三入口回调可用 |
| 仪器化 F-20 | `app/src/androidTest/.../PlayerQueueInstrumentedTest.kt` | 4 例：队列渲染+仅当前项高亮+点击发出意图+上一集/下一集/模式可用+状态同步；空队列 EmptyState 不崩溃；24 项长队列滚动与当前项定位；真实 libmpv 沙箱内点击跳转真实切源、顺序不变、快速连点只执行一次、上一集/下一集仍走既有路径 |

旋转测试使用 debug 专用宿主 `RotationTestActivity`（`app/src/debug/`，`configChanges` 与 `MainActivity` 一致），真实播放测试沿用 `MpvPlaybackInstrumentedTest` 的沙箱方式（临时目录+内存数据库+真实 libmpv），且 Surface 与面板共用同一宿主 Activity。

## 执行验证记录

- 执行环境：Windows 11；JBR（`C:\Program Files\Android\Android Studio\jbr`）；Gradle 9.6.0；AGP 9.5.1；compileSdk 37；模拟器 `Medium_Phone_API_37.0`（API 37，x86_64 + ARM64 转译），**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`）运行仪器化测试与截图取证。

| 验证 | 结果 |
|---|---|
| `:app:assembleDebug` | 成功；`aapt2 dump badging` 确认 `versionCode=10004 versionName=1.0.4`，仅 `arm64-v8a` |
| `lintDebug`（根任务，14 模块） | **0 error** |
| `testDebugUnitTest`（全模块） | **102 tests，2 skipped，0 failure / 0 error**（原 92，新增 QueueSelectionTest 10） |
| `:app:connectedDebugAndroidTest`（headless） | **50/50 通过，0 failure / 0 error / 0 skipped**（原 42，新增 F-19 4 例 + F-20 4 例；F-16/17/37/38 与 MediaSession 生命周期回归全部通过） |

截图证据：`docs/ui-ux-audit/implementation/evidence/`（竖/横、浅/深、1.0/1.3 字号、队列当前项+非当前项、长队列滚动），索引见 `evidence/README-1C.md`。

## 版本

用户可见行为变更，按项目规则递增补丁版本：`1.0.3 → 1.0.4`（versionCode 10003 → 10004），同步 `docs/CHANGELOG.md`。

## 已知限制

- 模拟器为 x86_64 + ARM64 转译，旋转手感、手势导航与厂商系统栏差异需真机确认。
- 队列跳转的“切换中”判定基于会话内标记；系统媒体会话外部命令与 UI 跳转的并发仍由切源门串行化，未新增外部命令入口。
- 长队列（数百项以上）的滚动性能沿用文件树列举能力，未做分页（与首页一致的已知限制）。
- 播放队列不包含媒体库级播放列表/CRUD/拖拽排序（明确不在阶段 1C 范围）。
