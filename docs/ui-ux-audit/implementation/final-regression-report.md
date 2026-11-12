# UI/UX 整改完整回归与交付报告（阶段 4）

状态：已执行。本报告汇总 41 项问题（F-01～F-41）的整改结果、验证证据与残余风险。

## 一、交付概览

- 应用：ZeroPlayer `com.privateplayer.media`，最终版本 **1.0.14 / versionCode 10014**，仅 `arm64-v8a`（aapt2 核验）。
- 整改范围：审计 41 项全部处置；其中 40 项已验证问题修复，1 项待验证问题（F-27）以模拟器证据关闭。
- 保留项（未破坏）：F-37 即时保存 + SettingsWriteQueue、F-16 面板单选语义、F-17 移除引擎行、F-38 ImmersiveSheetWindowEffect、F-19 比例/旋转顶栏、F-20 PlaybackQueueSheet + QueueSelection + playQueueItem、F-22 FileActionMenu、F-21 详情文件操作 + PlaybackStopper、libmpv 核心、MediaSession 生命周期契约（返回停服、重开新会话续播）、transitionMutex、私有存储导入复制、无 DB schema 变更。

## 二、门禁与测试（最终 APK）

| 验证 | 命令 | 结果 |
|---|---|---|
| 编译 | `:app:assembleDebug` | 成功；1.0.14/10014，arm64-v8a |
| 静态检查 | `lintDebug`（14 模块） | **0 error** |
| JVM 单测 | `testDebugUnitTest`（全模块） | **136 tests，2 skipped，0 failure / 0 error** |
| 仪器化 | `:app:connectedDebugAndroidTest`（headless，-PallowDestructiveConnectedTests=true） | **63/63 通过，0 failure / 0 error / 0 skipped** |
| 文案门禁 | `CopyResourceGateTest` | 通过（硬编码白名单 + 术语黑名单 + 同源断言） |

仪器化分布：DetailSettings 3、ExternalImport 1、FileMenuAndDetailActions 7、FileTransfer 1、FileTreeConsistency 23、ImportCopier 1、MediaSessionLifecycle 1、MpvPlayback 4、PlaybackReleaseBarrier 1、PlayerEntries 4、PlayerQueue 4、PlayerSheetImmersive 4、PlayerSheetSemantics 6、WifiRiskAndImportTerms 3。

## 三、41 项问题处置汇总

| 编号 | 优先级 | 状态 | 阶段 | 变更记录 |
|---|---|---|---|---|
| F-01 | P2 | 已修复 | 2B | 0018 |
| F-02 | P3 | 已修复 | 3 | 0024 |
| F-03 | P3 | 已修复 | 3 | 0024 |
| F-04 | P2 | 已修复 | 2B | 0018 |
| F-05 | P3 | 已修复 | 1E | 0015 |
| F-06 | P2 | 已修复 | 2C | 0019 |
| F-07 | P2 | 已修复 | 2C | 0019 |
| F-08 | P2 | 已修复 | 2D | 0020 |
| F-09 | P1 | 已修复 | 1E | 0015 |
| F-10 | P1 | 已修复 | 1E | 0015 |
| F-11 | P3 | 已修复 | 3 | 0024 |
| F-12 | P2 | 已修复 | 2D | 0020 |
| F-13 | P2 | 已修复 | 2F | 0022 |
| F-14 | P1 | 已修复 | 1E | 0015 |
| F-15 | P2 | 已修复 | 2C | 0019 |
| F-16 | P1 | 已修复 | 1B | 0012 |
| F-17 | P2 | 已修复 | 1B | 0012 |
| F-18 | P2 | 已修复 | 2A | 0017 |
| F-19 | P1 | 已修复 | 1C | 0013 |
| F-20 | P1 | 已修复 | 1C | 0013 |
| F-21 | P1 | 已修复 | 1D | 0014 |
| F-22 | P2 | 已修复 | 1D | 0014 |
| F-23 | P2 | 已修复 | 2B | 0018 |
| F-24 | P2 | 已修复 | 2B | 0018 |
| F-25 | P2 | 已修复 | 2C | 0019 |
| F-26 | P2 | 已修复 | 2A | 0017 |
| F-27 | P1 | 已关闭（模拟器不复现） | 1F | 0016 |
| F-28 | P3 | 已修复 | 3 | 0024 |
| F-29 | P2 | 已修复 | 2A | 0017 |
| F-30 | P2 | 已修复 | 2B | 0018 |
| F-31 | P3 | 已修复 | 2E | 0021 |
| F-32 | P2 | 已修复 | 1D | 0014 |
| F-33 | P2 | 已修复 | 2E | 0021 |
| F-34 | P2 | 已修复 | 2E | 0021 |
| F-35 | P3 | 已修复 | 3 | 0024 |
| F-36 | P2 | 已修复 | 2E | 0021 |
| F-37 | P1 | 已修复 | 1A | 0011 |
| F-38 | P1 | 已修复 | 1B | 0012 |
| F-39 | P2 | 已修复 | 2D | 0020 |
| F-40 | P2 | 已修复 | 2F | 0022 |
| F-41 | P2 | 已修复 | 2G | 0023 |

## 四、验证矩阵覆盖

- 设备：模拟器 `Medium_Phone_API_37.0`（API 37，x86_64 + ARM64 转译），headless（命令行取证）。
- 主题：浅色 / 深色（`cmd uimode night`）。
- 字号：1.0 / 1.3（关键页面截图）；2.0 见残余风险。
- 方向：竖屏 / 横屏（播放器横屏渲染、控件、进度、系统栏、返回验证，F-27）。
- 功能回归：播放器（播放/暂停/切轨/字幕/队列跳转/返回停服/重开续播/MediaSession 生命周期）、文件管理（单文件与批量删除/移动、文件树与数据库一致）、设置（即时保存/数值校验/分组导航）、Wi-Fi（风险块/权限恢复/复制反馈/术语）。
- 截图证据：`evidence/README-1B.md`～`README-3.md` 及各阶段 PNG。

## 五、残余风险与未覆盖项

1. **实体 ARM 真机**：无真机证据；硬件解码、厂商 Surface/AudioTrack、蓝牙按键、听感/震感需真机回归（D2–D4）。
2. **F-27 横屏**：模拟器不复现并以证据关闭；实体 ARM/硬解与 ≥5 分钟长时横屏未验证。
3. **字号 2.0 / 显示大小最大**：仅验证 1.0/1.3；2.0 全矩阵未取证。
4. **平板/折叠屏**：D6 仅基本可用，无专门布局；未做折叠态验证。
5. **TalkBack 实际朗读**：语义经仪器化断言（单目标/单选/自定义操作），真人朗读顺序需手测。
6. **SAF 云端目标 / 大文件长时导入**：DocumentsUI 本地可用；云盘与数 GB 长时导入需真机。
7. **数据层错误原因文案**：部分仍为数据层中文（经 UI 透传），未全部资源化（门禁白名单登记内部字符串）；后续可细化结构化错误码。
8. **文件类型图标区分**（视频/字幕/文本）：审计建议项，未实施（非必须）。
9. **F-28 行菜单**：隐藏三点后触屏经详情页更多菜单管理；首次使用引导未加。

## 六、结论

- 41 项问题全部处置：40 项修复 + 1 项（F-27）以模拟器证据关闭并标注真机残余风险。
- 最终 APK 通过编译、Lint（0 error）、136 JVM 单测、63 仪器化测试；文案门禁通过。
- 保留项与核心契约（libmpv、MediaSession 生命周期、transitionMutex、私有导入复制、无 DB schema 变更）均未破坏。
- 无 P0/P1 新增问题；既有 P1 全部关闭。
- 残余风险集中于实体真机/硬解/长时压测/2.0 字号/折叠屏，已在上方书面列出，需后续真机阶段补齐。
