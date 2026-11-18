# 播放器选速手势与启动图标

状态：1.2.0（10200）兼容功能。影响范围：`feature:player` 的倍速入口、`app` 的启动图标及两者的仪器化验收。不迁移数据，不替换 libmpv，不改变长按画面临时加速的设置范围；沿用 [1.1.1 的断点修复](0027-observed-duration-and-resume.md)。

## 两种选速方式

- 轻点左下角按钮，再轻点一个档位；或从按钮按住不放，滑到格子后抬手。按住时移动只预览候选，离格松手或系统取消都不改速度。按系统返回或点面板外只收起选速板，不结束播放。
- 选速板保留 0.25–8× 共 13 档。单选语义只给当前档，手指指向的候选标为「松开切换」。若 Media3 设置非预设值，面板显示精确值且不勾选附近档；按钮容纳不超过两位的小数，更长的值以 `≈` 明示近似，读屏仍提供精确值。不便长按或拖动时，仍可轻点选档。
- 在视频画面上长按才使用设置里的临时倍速；松开恢复先前的常规速度。左下按钮的滑选不触发视频的跳转预览、临时加速或音量调节。

## 桌面图标

保留紫色底、白色折带与播放三角负形。普通和圆形启动入口共用五档密度前景；主题图标使用同一边缘与负形的单色层，不把紫底压入蒙版。原始 [SVG](../assets/zeroplayer-icon.svg) 保留，[离线生成器](../../tools/render-launcher-icon.cjs)从原路径生成前景、单色层和[文档预览](../assets/zeroplayer-launcher.png)，Android 构建直接打包 PNG，不调用生成器或下载工具。预览不作为桌面安装证据。

正式导出使用本机预装的 sharp 0.35.5 / librsvg 2.63.2。生成器离线复算的 5 档普通/单色 alpha 逐像素一致，连同像素四角的最大半径分别为 30.150 / 29.702 / 29.606 / 29.454 / 29.379 dp，均小于 33 dp 安全半径。按 72 dp 可见窗口采样至 48 / 24 px，仍分别有 13×18 / 6×8 px 的纯透明内孔。另一次 Bun 工具探测在共享缓存新增了目录；没有将其加入工程依赖，也没有删除共享缓存，不据此宣称整个探测过程无网络。

## 手势测试入口

- [PlayerSpeedControlStateTest](../../feature/player/src/test/java/com/privateplayer/feature/player/PlayerSpeedControlStateTest.kt) 观察提交回调：跨档只预览，释放到候选提交一次，重复释放、离格、取消与上次面板的坐标不提交。首轮实际编译发现嵌套 Row 隐式取用 `BoxWithConstraints.maxWidth`；在外层先计算可用宽度后，单独运行此类 **2/2 通过**（Gradle 12 秒，71 项任务，10 执行、61 为最新）。这是状态机证明，不是设备触摸证明。
- [真实页面用例](../../app/src/androidTest/java/com/privateplayer/media/MpvPlaybackInstrumentedTest.kt)复用独立文件夹、Room、libmpv 与 `RotationTestActivity`；[手势验收](../../app/src/androidTest/java/com/privateplayer/media/PlayerSpeedPickerProbe.kt)只读引擎快照，实际操作用系统 `MotionEvent`。同一指针的 DOWN/MOVE/UP 沿用同一 `downTime`，另有系统 CANCEL。共享对话框原有的单选、重开和沉浸断言保留在仍使用 ActionSheet 的音轨与画面比例用例中。

## 离线构建与范围

Windows x64 使用本机 Android Studio JBR、SDK 37、Build Tools 36.0.0、Gradle 9.6，均以 `--offline --console=plain -Pandroid.builder.sdkDownload=false` 运行。四个 1.2.0 功能生产源冻结后先构建 `testDebugUnitTest :app:assembleDebug`（31 秒，425 项：128 执行、2 来自缓存、295 为最新）；其中播放器先排除了刚单跑的两个方法所在类，随后按**正常不带任何筛选**的 `:feature:player:testDebugUnitTest` 完整重跑（4 秒，播放器测试任务实际执行），最终不以筛选结果替代该模块。

最终 JVM XML：30 类、164 项，162 通过、2 跳过、0 失败/错误。`app`、`data`、`core:design`、`core:playback`、`feature:home`、`feature:player`、`feature:settings` 的 152 项实际执行（含 2 跳过），`core:common` 12 项为已有最新结果；没有把前置的 2 项再加总。新增文件类型、局域网监听与地址筛选的专题见 [0029](0029-home-material-file-icons.md)、[0030](0030-wifi-upload-and-interfaces.md)。

冷装调试包确认 `versionName=1.2.0`、`versionCode=10200`。`aapt2 dump xmltree/resources` 解析最终清单的正常及圆形入口分别为 `mipmap/ic_launcher`（`0x7f0b0000`）及 `mipmap/ic_launcher_round`（`0x7f0b0001`），ZIP 内有两个 adaptive XML、五种密度各一份前景与单色 PNG。APK 与测试 APK 使用 `app/build/outputs/apk/debug/`、`app/build/outputs/apk/androidTest/debug/` 下的调试产物，不使用或签发发布包。

测试源冻结后单独执行根 `lintDebug :app:assembleDebugAndroidTest`（43 秒，635 项：162 执行、473 为最新）。14 份模块 Lint 报告共 0 error、14 warning；相对 1.1.0 记录多 1 条上游图形重复警告，见 [0029](0029-home-material-file-icons.md)。

## 冷装与桌面实景

独占 `emulator-5584`（API 37、x86_64 转译 arm64-v8a），使用已安装系统镜像和独立临时 AVD 磁盘，`-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save`。安装前目标包和 Launcher 的应用列表中均无 ZeroPlayer；随后只在该序列安装本版调试 APK。点入系统桌面真实的「All apps」并搜索应用名，[Pixel Launcher 截图](../ui-ux-audit/implementation/evidence/launcher-installed-1.2.0.png)中的 `ZeroPlayer` 搜索结果是 `com.google.android.apps.nexuslauncher` 的可点击节点，边界 `[30,339][285,645]`，深紫圆形底、白色折带和三角负形均可见。该图不是文档预览或从 APK 单独解码的位图。

Android 13+ 单色另用已安装 APK 的系统 `AdaptiveIconDrawable` 核对：普通和圆形资源在相同系统遮罩下呈现一致，两者 `getMonochrome()` 同形。216×216 独立画布中，在有不透明墨迹的扫描线两侧之间统计到 1,507 个低 alpha 像素。[按系统主题色绘出的负形](../ui-ux-audit/implementation/evidence/launcher-monochrome-render-1.2.0.png)是提取层后的着色结果，**不是**声称在桌面设置开启了主题图标。

## 设备问题与修正

此次只用指定 serial 的 `adb install` 与 `am instrument -w -r -e class …`，不运行会卸载和清空应用的 `connected*`。首次定向 **17 项、14 通过、3 失败**：速度板及旧物理手势被新设备首次进入沉浸式的系统教学抢焦点；真导航到播放器时，临时 debug 编译产物缺少 `PlayerViewModel` 的 Hilt 绑定。其余 14 项包括单色启动图标、音轨/比例对话框、文件长按操作、Wi-Fi 手机面板及打包 WebView 删除回归。

`PlayerViewModel` 保留 `@HiltViewModel` 与原注入构造；没有添加无参构造或在页面绕过 DI。仅用 `--offline --no-build-cache :feature:player:kspDebugKotlin --rerun :app:assembleDebug :app:assembleDebugAndroidTest` 重生受影响的 debug 绑定与下游包（11 秒，358 项，22 执行）。生成后的 debug Dagger registry 含该类的 key、provider 和含两个实际参数的构造。原实例更新包后从真实文件列表点 `01-film.mkv`，系统显示「Viewing full screen」，实际点 `Got it` 后焦点回播放器，片名 `01-film`、进度 `00:01 / 00:16`、底栏 `1×` 可见。没有通过修改系统首用标记或删去返回、旋转断言来绕过问题。

仅重跑上述三项：`speedPickerSupportsTapAndSinglePointerScrubInBothOrientations`、`physicalGesturesAndPausedFrameSurviveBothOrientations`、`serviceAndControllerSharePlaybackAcrossBackgroundAndActivityRecreation`。三项均返回状态 0，**`OK (3 tests)`，48.039 秒**。两轮合起来覆盖 17 项，不称为一次跑完全部仪器化。最终调试 APK 为 **53,167,652 B**，测试 APK **2,803,782 B**。

### 实际按下、滑动和抬手

同一 16 秒彩条片，字号 **1.3**。坐标单位为屏幕物理像素；速度来自同一真实 libmpv 的 `snapshot`，时间单位 ms。截图与[原始坐标/引擎日志](../ui-ux-audit/implementation/evidence/speed-picker-1.2.0.txt)均为本版新采集。

| 场景 | 观察 |
|---|---|
| 播放中打开后等待 | 在 1× 从 166 播至 5000；选速板跨过底栏 4 秒隐藏时限，仍能点选。含点选过程共 4615 ms，引擎读数序列仅 `[1.0, 0.5]`，仍在播放。 |
| [竖屏轻点](../ui-ux-audit/implementation/evidence/speed-picker-portrait-1.2.0.png) / 横屏轻点 | 两个方向依次物理选 8×、0.25×；引擎分别变成 `8.0`、`0.25`。按钮边界始终为竖屏 `[53,2222][268,2348]`、横屏 `[53,902][268,1028]`：都为 215×126 px，约 82×48 dp。两向 13 档均同屏、不重叠，所有选项触区至少 48×48 dp；打开面板不拉出系统栏，点外或系统返回只收起面板。 |
| 竖屏连续滑选 | 在 `(160.5,2285)` 按住，依次移至 3× `(171.5,2123)`、1.75× `(205,1982)`，中途不抬手。抬手前引擎一直 `0.25@5083`，一次 UP 后 `1.75@5083`。 |
| [横屏连续滑选](../ui-ux-audit/implementation/evidence/speed-picker-landscape-1.2.0.png) | 在 `(160.5,965)` 按住，依次移至 3× `(171.5,803)`、6× `(573,803)`。截图中白底带勾的实际档仍是 0.25×，蓝边为 6× 候选；抬手前 `0.25@5083`，UP 后 `6.0@5083`。没有第二次点击。 |
| 放弃与画面手势 | 两向都先滑入 8×，分别移出再抬手或在格内发系统 CANCEL；竖屏保持 `1.75@5083`，横屏保持 `6.0@5083`。整段没有误 seek、临时倍速或改变暂停状态。另从 0.75× 恢复播放，长按**画面**到 2× 并真实推进，松手恢复 0.75×（5417→5667）。 |
| 外部速度 / 原交互 | 真实 Media3 将速度置 8×，再置非预设 1.375×；按钮显示 `≈1.38×`、无障碍值与面板显示 `1.375×`，无预设被误勾。经界面点 0.5×，控制器同步。原有双击/拖动/暂停帧横竖互转也通过：两次均 55/55 色块，时间码墨迹 99% / 100%。 |

**留档与边界：** 本机 `build/reports/speed-picker-1.2.0/` 保留四类原始记录：`merged-production-jvm.log` / `player-complete.log`、`merged-lint-instrumentation.log`、`player-hilt-repair.log`、`device-instrumentation.log` / `device-player-three.log`。Wi-Fi 浏览器联调结束后撤回本次转发，客机 `8765` 与测试中继均无监听；退出前再次确认媒体流静音且音量 0。只关闭本次模拟器，序列及其两个进程均已消失，临时 AVD 磁盘、夹具和脚本已清，保留 APK 与证据。未在实体机复核触感、厂商桌面遮罩、真人 TalkBack 或 8× 听感；未发版、打标签、提交或推送。
