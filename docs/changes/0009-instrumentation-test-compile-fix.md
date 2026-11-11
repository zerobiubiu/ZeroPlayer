# 修复仪器化测试编译阻塞（B-01）

状态：已执行。影响范围：`app/src/androidTest` 三个测试文件；未修改生产代码、样式、Token、翻译或配置。

## 背景

阶段 0 基线发现 `:app:assembleDebugAndroidTest` 失败：设置页拆分、字幕记忆接口新增、`PlaybackServiceStarter` 从 SAM 改为普通接口后，3 个测试文件未同步，导致全部 8 个仪器化测试类无法编译，UI/E2E 门禁不可用。

## 根因与修复

### 1. `DetailSettingsInstrumentedTest` 引用旧设置页

- 根因：生产代码已把单页 `SettingsScreen` 拆为 `SettingsHubScreen`（一级入口）与 `SettingsSectionScreen`（二级页）；测试仍按单页滚动断言“显示/存储管理/关于”。
- 修复：测试改为“一级 Hub + 选中二级页”的宿主组合，点击入口进入对应二级页，再用 `返回` 回到一级；保留对主题、存储数值、关于页、无媒体库/网格密度的断言。
- 断言差异：
  - 原：同一页滚动到“显示”，断言“主题”存在。
  - 新：点击“显示”进入二级页，断言“主题模式”下拉与当前值“跟随系统”；网格密度与媒体库仍断言不存在。
  - 存储/关于改为点击入口后断言，数值与文案断言保持一致；新增引擎名“测试引擎”断言。
- 未放宽：断言数量未减少，仅按新的信息架构调整定位方式。

### 2. `DetailMediaRepository` 缺少新接口方法

- 根因：`MediaRepository` 新增 `suspend fun setSelectedSubtitleTrack(mediaId: Long, trackId: String?)`。
- 修复：测试替身补实现，调用即 `error("详情页不应调用")`；详情页本身不调用该方法，保持“禁止意外调用”的严格性。

### 3. 两处 `PlaybackServiceStarter` SAM 构造失效

- 根因：接口由 `fun interface` 改为带 `start()`/`stop()` 的普通接口（关闭播放页需要 stop）。
- 修复：`MpvPlaybackInstrumentedTest` 与 `PlaybackReleaseBarrierInstrumentedTest` 改为显式对象：`start()` 返回 null，`stop()` 空实现。
- 语义：测试不启动真实服务，仅满足协调器构造；与原 `{ null }` 行为等价。

## 实施文件

| 文件 | 变更 |
|---|---|
| `app/src/androidTest/java/com/privateplayer/media/DetailSettingsInstrumentedTest.kt` | 导入新组件；设置测试改为 Hub+Section 宿主；补仓库替身方法 |
| `app/src/androidTest/java/com/privateplayer/media/MpvPlaybackInstrumentedTest.kt` | `PlaybackServiceStarter` 显式对象 |
| `app/src/androidTest/java/com/privateplayer/media/PlaybackReleaseBarrierInstrumentedTest.kt` | 同上 |

未修改任何生产源码、Gradle 配置、资源或审计基线。

## 执行验证

环境：Windows 11 x64；Android 17（API 37）x86_64 模拟器（ARM64 转译）；JBR 25；Gradle 9.6.0。

| 验证 | 命令 | 结果 |
|---|---|---|
| 仪器化测试编译 | `:app:assembleDebugAndroidTest` | **成功**（32s） |
| 仪器化 Java 编译 | `:app:compileDebugAndroidTestJavaWithJavac` | `NO-SOURCE`（无 Java 测试源码） |
| 定向测试 1 | `connectedDebugAndroidTest` + `DetailSettingsInstrumentedTest` | **2/2 通过** |
| 定向测试 2 | + `PlaybackReleaseBarrierInstrumentedTest,MpvPlaybackInstrumentedTest` | **5/5 通过** |
| 完整仪器化套件 | `:app:connectedDebugAndroidTest -PallowDestructiveConnectedTests=true` | **31 tests，30 通过，1 失败，0 error，0 skipped** |
| 失败复现 | 单独运行 `MediaSessionLifecycleInstrumentedTest` | **1/1 失败**（稳定复现） |
| JVM 单元测试 | `testDebugUnitTest` | 76 tests，2 skipped，0 失败 |
| 编译 | `:app:assembleDebug` | 成功 |
| Lint | `lintDebug` | 0 error，app 10 warning |

### 完整套件失败详情（不属于 B-01 编译问题）

- 测试：`MediaSessionLifecycleInstrumentedTest.serviceAndControllerSharePlaybackAcrossBackgroundAndActivityRecreation`
- 断言：等待「退出页面后重开不重载」超时；真实控制器 `connected=false, mediaId=null`，服务端已是新会话。
- 根因：该测试仍假设“返回文件树后服务继续、重开不重载”。当前产品行为（1.0.1 起）是**关闭播放页即停止播放并释放服务**；重开会创建新会话并从断点续播。两者的 playCount/控制器生命周期预期不同。
- 判定：**测试断言失败**，非编译失败、非环境问题；与本次三个编译修复无关。未在本任务中改写该测试，因为改写会变更断言语义（playCount 1→2、控制器重连），属于独立的行为契约测试更新，需产品/测试确认后单独立项。

## 结论

- B-01 的**编译与运行能力已解除**：测试 APK 可编译，`connectedDebugAndroidTest` 可执行，定向修复的 7 个用例全部通过。
- 完整套件仍为 30/31：唯一失败是既有测试对已变更产品行为的过时断言，不是 B-01 残留编译问题。
- 建议下一步：阶段 1A-1 前，单独处理该行为测试（确认“返回即停止”为正式契约后更新断言），或将其列为已知测试债务；不影响 T-37 的编码与 JVM 验证。

## 边界

- 未提交/推送 Git。
- 未修改生产代码、UI、Token、翻译、配置或审计基线。
