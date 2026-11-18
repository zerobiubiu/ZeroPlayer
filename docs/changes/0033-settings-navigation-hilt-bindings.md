# 修复从首页打开「设置」时闪退

状态：已修复，1.2.3（10203）。影响范围：[Hilt 构建约束](../../build.gradle.kts)、[应用默认版本](../../app/build.gradle.kts)与[真实设置导航回归](../../app/src/androidTest/java/com/privateplayer/media/SettingsNavigationInstrumentedTest.kt)。不改设置页面、Room 结构、偏好键或私有媒体格式。

## 原包中缺的是绑定，不是设置类

用户报告从首页点底部「设置」即闪退。以下定位依据是**本机保全的 1.2.2 调试 APK**（10202，SHA-256 `430496fe5845f5e0761e1cdf35f147dae68a738eb7def128a9fb2a2798e25148`），不是用户手机的日志；没有为确认现象再次启动旧版。

[根导航](../../app/src/main/java/com/privateplayer/media/PrivatePlayerRoot.kt)打开[设置一级页](../../feature/settings/src/main/java/com/privateplayer/feature/settings/SettingsScreen.kt)时，页面向 Hilt 请求 `SettingsViewModel`。旧 APK 的 `classes12.dex` 有这个类和它的六参数注入构造，却没有它的 `HiltModules$KeyModule` 和 `$BindsModule`；`classes17.dex` 的应用级 key 表与 provider 表各有 7 项，都不含设置。相同 APK 中首页、详情、导入与播放器的绑定都在。也就是说，界面要用的类打进了包，但 Hilt 没有它的创建入口。

本机原有 `:feature:settings:kspDebugKotlin` 构建缓存条目只记录了缺失的输出树；播放器也留有这类旧条目（[0028](0028-speed-picker-and-launcher-icon.md)记载过播放器绑定的单次重建）。不据此断言 KSP **最初为何**产生空输出。本次只对设置和播放器两个模块的应用变体增加约束：KSP 的缓存键带上所需 ViewModel，生成后须有非空 key、binding 两份 Hilt 聚合标记；Kotlin 编译同时将两项分别声明为必需文件输入，覆盖 KSP 从缓存恢复而不执行任务末尾检查的情况。不靠清缓存、增加无参构造或绕开依赖注入解决。

## 普通离线构建：从原坏状态出发

Windows x64、Android Studio JBR 25.0.4、本机 SDK 37 / Build Tools 36.0.0。在原 KSP 生成树未修补、原构建缓存未清空的状态下运行：

```powershell
.\gradlew.bat --offline --console=plain "-Pandroid.builder.sdkDownload=false" :app:assembleDebug :app:assembleDebugAndroidTest
```

- 首次 `BUILD SUCCESSFUL in 15s`，359 项（30 执行、3 来自缓存、326 为最新）。设置与播放器的 `kspDebugKotlin`、`compileDebugKotlin` 均实际执行；`:app:hiltAggregateDepsDebug` 为 `FROM-CACHE`，`:app:hiltJavaCompileDebug` 和两份 APK 的打包实际执行。
- 不改源码再执行同一命令，`BUILD SUCCESSFUL in 1s`，359 项全部 `UP-TO-DATE`。两次应用与测试 APK 的 SHA-256 分别相同；没有 `clean`、强制重跑或禁用构建缓存。
- **交付 APK 的 DEX 对照：** `classes12.dex` 有设置 VM 的 `Factory`、`KeyModule`、`BindsModule` 以及版本信息 provider 的工厂，`classes2.dex` 有两份聚合标记。`classes17.dex` 的两张表各 8 项，均含设置且保留播放器；实际 provider 调用原六参数构造。

| 调试产物 | 字节数 | SHA-256（与设备安装的 `base.apk` 相同） |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 52,849,539 | `bb61984518de7f40add607ebeab6971d03aa01ec6be376edf381044a600a2fb8` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,813,118 | `f3c51ddbf3a5f744c5db05729d2f1da4b8208f90e56a02758155ca7ed895ec33` |

## 覆盖安装和真实点击

独占 API 37 `emulator-5592`，1080×2400，只复用已安装的系统镜像和硬件配置，用户盘、SD 卡与快照不沿用。启用 `-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save`，客机媒体流静音、音量 0。

1. 在空盘上安装保全的 1.2.2，不启动旧应用（包状态 `notLaunched=true`、无 PID）。只在该测试应用的 `files/media/` 写入合成的 204 B 静音 WAV。用 `adb -s emulator-5592 install -r -t` 覆盖安装上表的 1.2.3。UID 仍为 10235，首次安装时间不变；同一文件覆盖前后 SHA-256 均为 `d1d7a521db9ef3cbc7cfe7cd7ebadae7e90d90c5effc75b27794b03e6d9d7d28`。这只证明该测试文件保留，不代表核验了真实用户的全部媒体、数据库或偏好。
2. 从系统 Launcher 实际点击 `ZeroPlayer` 冷启，允许首次通知权限；首页显示该 WAV「文件 · 204 B」。点击底栏「设置」后，看到「观看 / 文件 / 应用」三组七项，含「0 个视频 · 204 B」「版本 1.2.3 · libmpv」。
3. 点「播放 → 播放进度」，菜单实际显示「不记录」「仅记录，从头播放」「记录并续播」，原值「记录并续播」带勾；未改偏好。系统返回关闭菜单，再返回设置一级。点「首页 → 设置 → 字幕」，看到「字幕样式」「字幕字号 100 %」；点工具栏「返回」，回到仍可操作的设置一级。
4. 从初次首页到返回设置一级，PID 同为 **7067**，`topResumedActivity` 是 `com.privateplayer.media/.MainActivity`。从更新开始至手动导航结束（客机 epoch `1795010360.077`～`1795010523.738`），只查询 UID 10235 的 main/crash 日志、过滤 `AndroidRuntime:E`，**0 条匹配记录**。

另以 `adb -s emulator-5592 shell am instrument -w -r -e class com.privateplayer.media.SettingsNavigationInstrumentedTest#settingsTabOpensSectionsAfterLaunchAndReentry com.privateplayer.media.test/androidx.test.runner.AndroidJUnitRunner` 运行唯一新增回归。它启动真正的 `MainActivity`，使用生产的 NavHost、Hilt、Room 与 DataStore，断言底栏选中状态、播放页实际菜单选项、字幕页标题以及二级返回/标签再入；不直接 `setContent` 设置页，也不传入替身 ViewModel。结果 **`OK (1 test)`，5.292 秒**，方法状态码 0、`INSTRUMENTATION_CODE: -1`、adb 退出码 0。含这次运行的后续同范围日志查询也没有 `AndroidRuntime:E` 记录。

本机 `build/reports/settings-navigation-1.2.3/3hukNF/` 保留两次构建日志、`before-` / `after-` 的 DEX 对照和安装记录、`manual-navigation.txt`、一级和进度菜单截图、过滤后的日志、`instrumentation.txt` 与 `cleanup.txt`。结束时只关闭本次模拟器，确认其序列及进程消失、专用临时磁盘已删除。未运行 `connected*`、全套测试或实体机测试；未构建或安装发布签名包。
