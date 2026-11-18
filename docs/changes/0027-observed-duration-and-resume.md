# 未知时长视频的断点续播与片尾重开

状态：已执行，1.1.1（10101）兼容修复。影响范围：播放会话、进度用例、媒体仓库与 Room 的单行更新。沿用 1.1.0 的三种断点策略和 Room v3，不迁移或批量修复历史数据。

## 原因与修复

`MediaProbe` 没读到时长时可以返回 `0`，`ImportWorker` 按此值入库；它不代表视频不能播放。libmpv 解码后，`PlaybackSession` 将实测时长连同位置交给 `SavePlaybackProgressUseCase`。此前实测值用于限制断点，却没有经仓库写回媒体行。重开时库中只有片尾位置、没有用于判断 98% 的时长，可能直接从 EOF 开始。

- [进度用例](../../domain/src/main/java/com/privateplayer/domain/usecase/MediaUseCases.kt)将原始 `durationMs` 作为 `observedDurationMs` 传给[仓库接口](../../domain/src/main/java/com/privateplayer/domain/repository/Repositories.kt)，再由[仓库实现](../../data/src/main/java/com/privateplayer/data/repository/MediaRepositoryImpl.kt)原样交给 [MediaDao.recordProgress](../../core/database/src/main/java/com/privateplayer/core/database/dao/MediaDao.kt)。用于断点上界和历史的回退值不冒充引擎实测值。
- `UPDATE media ... WHERE id = :id` 在原有断点、次数、观看时长、时间戳的同一次写入中执行 `CASE WHEN durationMs <= 0 AND :observedDurationMs > 0 THEN :observedDurationMs ELSE durationMs END`。不加查询或更新、不覆盖整行，已有正时长不被新观测改写。未播放的旧行不动，不从位置猜测时长。历史仍只在本次首次计数时记录。
- 设备首轮还发现第二个写入来源：[PlaybackSession](../../feature/player/src/main/java/com/privateplayer/feature/player/PlaybackSession.kt) 在收到 EOF 后保存了片长，随后关闭同一会话仍会保存；最后一帧的位置可能比片长早。现在 `persistProgress()` 只看本次读取的同一份快照：`ENDED` 且时长为正才用片长，否则用实际位置。去掉冗余的 `endOfPlayback` 参数，没有略过关闭时的保存或统计。98% 阈值和逐文件初始定位均未调整。

其他改动：[app/build.gradle.kts](../../app/build.gradle.kts) 仅调整两个默认版本值；[播放器仪器化测试](../../app/src/androidTest/java/com/privateplayer/media/MpvPlaybackInstrumentedTest.kt)复用隔离夹具并让重建的会话从正式工厂取得新引擎；[文件树仪器化测试](../../app/src/androidTest/java/com/privateplayer/media/FileTreeConsistencyInstrumentedTest.kt)迁移直接 DAO 调用并增加用例/数据库边界断言；[详情设置](../../app/src/androidTest/java/com/privateplayer/media/DetailSettingsInstrumentedTest.kt)、[菜单与详情](../../app/src/androidTest/java/com/privateplayer/media/FileMenuAndDetailActionsInstrumentedTest.kt)的仓库替身同步换签名，没有旧重载。同步[版本日志](../CHANGELOG.md)、[文档索引](../README.md)、[播放架构](../architecture/playback-abstraction.md)与本记录；0025/0026 和历史证据保持原样。

## 离线构建与 JVM 测试

2026-11-18，Windows x64，进程级 Android Studio JBR 25.0.4、已缓存的 Gradle 9.6.0 与 SDK 37。两次均执行：

```powershell
$env:JAVA_HOME = 'C:\Program Files\Android\Android Studio\jbr'
.\gradlew.bat --offline --console=plain '-Pandroid.builder.sdkDownload=false' `
  :feature:player:testDebugUnitTest --tests com.privateplayer.feature.player.PlaybackProgressModeTest `
  :app:assembleDebug :app:assembleDebugAndroidTest
```

首轮 `BUILD SUCCESSFUL in 22s`（368 项，82 执行、286 为最新）。修正终态保存后，最终 `BUILD SUCCESSFUL in 7s`（368 项，25 执行、6 命中缓存、337 为最新）。最终 `PlaybackProgressModeTest` XML：`tests=3, skipped=0, failures=0, errors=0`，覆盖三模式、旧标志兼容、98% 边界和未知时长。未执行 Release 构建。

## 定向设备验证

使用既有 `Medium_Phone_API_37.0` 启动新 `emulator-5584`（API 37，x86_64，支持 arm64-v8a 转译）。启动前设备列表为空、5584/5585 空闲；使用独立临时磁盘目录及 `-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save -no-boot-anim`。冷启动后确认没有目标包；`cmd media_session volume --set 0` 返回后实际仍为 5，改用 `cmd audio set-volume 3 0`、`set-device-volume 3 0 2`、`adj-mute 3`，在播放前核实 `STREAM_MUSIC: Muted: true, streamVolume:0`。只向这一序列安装以下两个 APK；修后也只在该实例以 `install -r` 更新这两包：

- `app/build/outputs/apk/debug/app-debug.apk`（设备报告 `versionName=1.1.1`、`versionCode=10101`）
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`

```powershell
$m = 'com.privateplayer.media.MpvPlaybackInstrumentedTest'
$f = 'com.privateplayer.media.FileTreeConsistencyInstrumentedTest'
$cases = "$m#unknownDurationResumesAtFirstPlayableAfterClosingMidway,$m#unknownDurationAtEofStartsNewPlaybackFromBeginning,$f#progressBackfillsOnlyMissingDurationAndCountsEachSaveOnce"
adb -s emulator-5584 shell am instrument -w -r -e class $cases com.privateplayer.media.test/androidx.test.runner.AndroidJUnitRunner
adb -s emulator-5584 logcat -d -s 'ZeroPlayerProgressTest:I' '*:S'
```

首轮运行的是带时长回填的 1.1.1，不是 1.1.0 旧包：`Tests run: 3, Failures: 1`（10.63 秒）。中途续播、数据库边界通过；EOF 后真正新装片的首状态却是 `ENDED positionMs=15000 durationMs=16000`。据此修正上述关闭时的持久化，并加强关闭后必须仍等于完整片尾的断言。两个 APK 重建后以相同选择器重跑，三个方法各返回 `INSTRUMENTATION_STATUS_CODE: 0`，最终 **`Time: 10`、`OK (3 tests)`**。

两个播放场景各自从新的真实 Room 内存库、独立目录中的 `playback-regression.mkv` 开始；装片前只将该夹具行的时长置为零并读库断言。关闭时释放旧会话和原生引擎，重开从 Room 读取；**先订阅新引擎再发起装片**，取首个非空闲、非加载状态，不等待进度进入期望区间。结果取自最终运行，单位毫秒：

| 场景 | 实际结果 |
|---|---|
| 中途关闭并重开 | 初始库时长 `0`；引擎时长 `16000`，关闭前 `3000`，Room 位置/时长 `3000/16000`；新引擎首个可播状态 `PLAYING@3000`。原会话次数 `1`、观看 `2918`、历史 `1` 条。 |
| 真实 EOF 后关闭并重开 | 初始库时长 `0`；在片尾前跳转，由 libmpv 自行到达 `ENDED`。终态原始位置 `15250`、实测时长 `16000`；EOF 时 Room 为 `16000/16000`，关闭写入排空后仍为 `16000/16000`。新引擎首状态 `PLAYING@0`，随后正常推进到 `250`；原会话次数 `1`、历史 `1` 条。 |
| 用例 → 真实 Room 写入边界 | 初始 `-1` 遇 `0`、`-20` 仍为 `-1`；观察到 `10000` 后补齐，再传 `20000` 仍为 `10000`。四次各增加 `100`，最终位置 `600`、观看 `400`、次数 `1`、历史 `1` 条，另一条未播放的媒体整行不变。 |

结束时媒体流仍静音、音量 `0`。仅向本次序列发送 `adb -s emulator-5584 emu kill`，核实该序列已消失、所属 launcher/QEMU 均退出，并已删除本次临时磁盘与日志目录。

**边界：** 实测覆盖这份样片、真实 libmpv、Surface、生产会话/仓库与 Room；零时长是独立夹具注入的合法导入结果，不是声称系统对该样片实际探测失败。没有在正式媒体或用户设备上运行，也没有重跑 1.1.0 的全部仪器化、Lint、系统通知/锁屏或实体 ARM 硬解验收。本记录不替代[1.1.0 的历史验收](0026-player-subtitles-and-navigation.md)。
