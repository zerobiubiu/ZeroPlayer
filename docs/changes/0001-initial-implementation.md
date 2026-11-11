# 0001 第一阶段实现：工程、设计系统、核心页面、数据层、导入链路、播放抽象、MPV 验证

状态：已执行（定稿）
影响范围：全工程
前置：无

## 1. 背景

从零建立 Android 私人影音管理与播放应用。已确定：Kotlin、Compose、Material 3、Room、Hilt、WorkManager、SAF、Coil、DataStore、MPV（后续接入）、本地文件私有存储。

需求约束：原文件不得修改；导入必须复制到私有存储且流式处理大文件；播放器交互全部自绘；MPV 不得伪接入。

## 2. 方案概要

- 15 个模块：`app` / 6×`core:*` / `domain` / `data` / 6×`feature:*`（详见 `architecture/overview.md`）。
- 数据层：Room（6 张表）+ DataStore + 私有存储 + SAF 导入 + WorkManager 恢复。
- 播放：`PlaybackEngine` 抽象 + Mock 引擎；`MpvNativeProbe`/`CodecCapabilityProbe` 提供真实探测；播放器界面完整自绘。
- 设计系统：M3 主题（明/暗/动态取色）、统一间距/圆角/字体、媒体卡片与状态组件。
- 导入：持久化任务、1 MiB 流式复制、字节级续传、原子提交、重复检测、空间检查、取消/重试、前台通知。

## 3. 改动文件清单（按模块）

```text
settings.gradle.kts / build.gradle.kts / gradle.properties / gradle/libs.versions.toml
gradle/wrapper/*、gradlew、gradlew.bat
app/
  build.gradle.kts、src/main/AndroidManifest.xml
  java/com/privateplayer/media/{MainActivity,PrivatePlayerApplication,PrivatePlayerRoot,AppViewModel,Routes}.kt
  res/{values,values-night,mipmap-anydpi-v26,drawable,xml}/*
core/common/      dispatchers、ApplicationScope、Formatters、FileNameRules（含单元测试）
core/design/      Color/Typography/Shapes/Spacing/Theme、MediaComponents、States、Bars、Dialogs、MediaCardData
core/database/    6 实体、5 DAO、PrivatePlayerDatabase、DatabaseModule、schema 导出
core/datastore/   PreferencesSettingsRepository（全部偏好）+ Hilt 绑定
core/storage/     PrivateStorage、ImportCopier、MediaProbe、SafDocuments、StorageBindings
core/playback/    PlaybackEngine、MockPlaybackEngine、MpvNativeProbe、CodecCapabilityProbe、EngineProvider
domain/           Media/Folder/Playlist/ImportTask/Settings 模型、仓库接口、用例
data/             6 个 Repository 实现、ImportWorker、WorkManagerImportScheduler、Mapper、Hilt 绑定
feature/home/     HomeViewModel、HomeScreen（继续观看/最近播放/最近导入/收藏/快捷入口）
feature/library/  LibraryViewModel/Screen、FolderBrowse、Category、PlaylistDetail、共享集合渲染
feature/detail/   MediaDetailViewModel/Screen（元信息、字幕、重命名/移动/删除/文件信息）
feature/importer/ ImportViewModel/Screen（选择、进度、取消、重试、失败原因、存储空间）
feature/player/   PlayerViewModel/Screen/Overlays/Gestures、MpvProbeScreen/ViewModel
feature/settings/ SettingsViewModel/Screen（播放/字幕/手势/显示/导入/存储/关于）
docs/             架构、决策、排查、变更记录
```

## 4. 执行验证记录

### 4.1 执行环境

| 项 | 值 |
|---|---|
| 宿主 | Windows 11 10.0.26300 x64 |
| JDK | Android Studio JBR 25.0.4 |
| 构建 | Gradle 9.6.0、AGP 9.5.1、内置 Kotlin（KGP 2.2.10）、KSP 2.3.13 |
| Android SDK | Platform 37、Build-Tools 36.0.0 |
| 运行设备 | Android 模拟器，Android 17 / API 37 / x86_64（Medium_Phone_API_37.0） |

### 4.2 命令与结果

| 命令 | 结果 |
|---|---|
| `gradlew :app:assembleDebug --no-build-cache --rerun-tasks` | BUILD SUCCESSFUL；`app-debug.apk` 21,948,664 字节 |
| `gradlew :core:common:testDebugUnitTest` | BUILD SUCCESSFUL；11 个用例全部通过（文件名规则、字节/时长/相对时间格式化） |
| `adb install -r app-debug.apk` + `am start` | 安装启动正常，无崩溃 |
| `adb logcat`（导入失败场景） | `WM-WorkerWrapper: Could not create Worker` → 已按 troubleshooting/0003 修复后复测通过 |

### 4.3 端到端行为验证（模拟器实测）

| 场景 | 操作 | 结果 |
|---|---|---|
| 导入两个视频 | SAF 多选 sample-a(4.8MB)、sample-b(95KB) | 两条任务 `COMPLETED`；`files/media/` 出现两个文件；`cache/temporary/` 为空；生成 2 张缩略图 |
| 大文件导入 | 385 MB / 16:48 的 mp4 | `COMPLETED`；数据库大小 403,765,616 字节与磁盘一致；缩略图生成 |
| 重复检测 | 再次导入 sample-a | UI 提示“跳过重复文件：sample-a.mp4”，不产生新任务 |
| 损坏文件 | 4 KB 随机数据命名为 mp4 | 状态 `FAILED`，提示“无法解析视频信息，文件可能已损坏或格式不受支持”；临时文件保留；重试仍失败；移除后临时文件清理 |
| 搜索 | 媒体库输入 `big` | 列表从 3 项变为 1 项（sample-big） |
| 排序/视图 | 名称 A→Z + 列表视图 | 顺序 sample-a、sample-b、sample-big，列表行显示分辨率/大小/导入时间 |
| 文件夹 | 新建 “Movies”，详情页移动 sample-a | 文件夹列表 Movies=1、未归档=1；磁盘文件未移动（逻辑归档） |
| 收藏 | 详情收藏 + 多选批量收藏 | 数据库 `isFavorite` 更新；收藏页 2 项 |
| 重命名 | sample-b → Renamed | 详情标题更新；磁盘文件 `Renamed.mp4`；数据库 `fileName/relativePath` 同步 |
| 删除 | 删除 Renamed | 磁盘文件消失、数据库记录删除、列表变 2 项，自动返回列表 |
| 播放器 | 播放 sample-big，暂停、拖动进度 | 暂停生效；拖动进度 00:02 → 00:06；标题/时间/倍速/字幕 chip 正常 |
| 倍速 | 设为 2.0×，切到队列中另一条 | 倍速保持 2.0×（修复了每次装载重置为 1.0 的问题） |
| 字幕 | 详情导入 `sample-a.srt` → 播放 sample-a | `files/subtitles/2/sample-a.srt` 存在；播放器字幕 chip 显示 `sample-a.srt`；字幕面板含关闭/轨道/±100ms/重置 |
| 记忆进度 | 播放 48% 后返回首页 | “继续观看”出现 sample-a，显示 48% |
| 播放次数 | 完整播放 6 秒短片一次 | `playCount` 16 → 17（只 +1，修复了每 5 秒保存都累加的问题） |
| 自动下一集 | sample-a 播完 | 自动切到队列下一条 sample-b |
| 锁定 | 播放器点击锁定 | 控制层隐藏，显示“已锁定，点击解锁”；点击解锁恢复 |
| 横竖屏 | 更多 → 旋转屏幕 | 转为 2400×1080 横屏，控制层重排 |
| MPV 验证页 | libmpv / 硬解 / Surface / 引擎链路 | libmpv 未打包（如实显示）；Surface 创建 1 次、尺寸 932×524、销毁计数随页面退出 +1；识别 11 个解码器，硬解支持 avc/hevc/vp9；引擎链路 `IDLE→PLAYING→PAUSED→seek 6000ms→PLAYING` |

### 4.4 验证中发现并修复的问题

| 问题 | 处理 |
|---|---|
| KSP 2.2.10 与 AGP 9 冲突、stdlib 2.4 元数据不兼容 | 升 KSP 2.3.13 + 统一 stdlib（troubleshooting/0001） |
| 构建缓存导致 KSP 生成类缺失，运行时 `NoSuchMethodException: HomeViewModel.<init>` | 关闭构建缓存 `clean + --rerun-tasks` 重新生成；已记录规避方式 |
| WorkManager 默认初始化早于 Hilt 注入，`@HiltWorker` 无法创建 | 移除 `androidx.startup` 初始化器（troubleshooting/0003） |
| 首次进入导入页“可用空间 0 B” | `freeBytes()` 在 media 目录未创建时回退到 filesDir；统计前补齐目录 |
| 切集后倍速/画面比例被重置 | Mock `initialize()` 保留 speed/volume/aspect |
| 播放次数按保存周期累加 | 改为每次装载只记一次 |
| 播放历史按保存周期重复写入 | 改为每次播放会话只写一条 |
| 底部导航高亮错位（library?tab= 与 library 不匹配） | 按基础路由匹配 |
| 媒体库 5 个 tab 标签被截断 | 改用 `PrimaryScrollableTabRow` |
| 首页横向列表在数据插入后前半张卡片被裁 | 首项 id 变化时回到起点 |
| 播放器引擎角标与标题重叠、结束提示压住按钮、上一个/下一个导致按钮不对称 | 角标移至左下、结束提示下移、上一集/下一集固定占位 |
| 播放器内弹出底部面板时系统栏短暂出现 | 面板状态变化后重新隐藏系统栏 |

## 5. 已知限制与残余风险

1. **libmpv 未接入**：无解码、渲染、音频输出；播放器显示封面并标注“预览引擎”。详见 `architecture/mpv-integration-feasibility.md`。
2. **手势运行期验证不足**：双击快进/快退、长按临时倍速、左右半屏亮度/音量滑动的代码已完成，但通过 adb 合成输入的节奏无法稳定触发双击/长按窗口；已验证拖动进度条与快进/快退按钮。需在真机/手工交互中回归。
3. **导入续传未做真实中断验证**：模拟器复制 385 MB 仅约 1 秒，无法在复制中途杀进程；续传逻辑（截断 + 跳过字节）、启动对账、`CANCELED` 清理均已实现并做代码审查，建议在真机 + 大文件（>5 GB）场景复测。
4. **取消导入未做运行期验证**：同上，复制过快；取消路径实现为“先写数据库状态，worker 下次进度检查停止并清理”。
5. **后台播放未实现**：离开播放器即保存进度并释放引擎，无媒体会话/通知控制（不在第一阶段范围）。
6. **列表未分页**：媒体库一次性查询全部匹配记录，超大库（数万条）需要改为分页。
7. **仅测试纯函数**：`core:common` 有 9 个单元测试；Repository/Worker/播放状态机无自动化测试，依赖端到端手测。
8. **仅模拟器验证**：未在真实 ARM 设备验证 MediaCodec 行为；硬解结论仅代表模拟器。
9. **屏幕方向不会自动恢复**：播放器内切换横屏后离开页面，Activity 仍保持横屏（未做退出复位）。
10. **GPL 合规**：若后续打包 libmpv/FFmpeg，需要确认许可证义务（详见可行性报告）。

## 6. 后续验证建议

- 真机（ARM64）验证导入大文件、进程中断续传、取消、硬解播放；
- 手工回归播放器手势（双击、长按、左右滑）；
- 接入 libmpv 后按可行性报告第 5 节矩阵复测；
- 为 Repository 增加 Room in-memory 测试，为导入状态机增加协程测试。
