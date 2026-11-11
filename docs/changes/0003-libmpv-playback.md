# libmpv 播放、ZeroPlayer 品牌与容量单位

状态：已执行  
版本：0.3.0  
影响范围：播放引擎、播放器、应用名称与图标、容量展示、原生构建和设备测试

## 背景

0.2.0 的播放器使用模拟时钟，既不解码也不输出画面或声音。用户要求修复实际播放，先验证 Android libmpv、Surface 与硬件解码，再接入正式播放器；同时要求 APK 自包含、媒体只从应用私有目录播放。随后要求修正容量单位标签，并将应用更名为 ZeroPlayer、重绘图标。

## 方案

1. 先构建并运行独立 MPV 验证页，确认私有文件、Surface、真实视频帧、音轨、字幕及控制链路；确认模拟器的 MediaCodec 失败后可回退软件解码，再切换正式播放器。
2. 固定 mpv 0.41.0、FFmpeg 8.1 等源码及 SHA-256，使用 WSL2 Ubuntu 和 Android NDK r29 构建四种 ABI。libmpv、FFmpeg 及相关第三方库静态链接进每个 ABI 的 `libplayer.so`；JNI、AndroidX 和 Android 系统库仍按平台机制动态加载，安装后无需下载播放库。FFmpeg 构建关闭网络协议，不影响应用自己的网页上传。
3. 删除 Mock 后端，统一通过 `PlaybackEngine` 返回真实状态与错误；Surface 创建、尺寸和销毁由播放器页面经 ViewModel 转交引擎。文件路径限制在应用私有媒体和字幕目录。
4. 应用显示名称、首页及网页品牌改为 ZeroPlayer，保留 Android 应用 ID `com.privateplayer.media`；自适应图标以环形零和播放符号构成，提供单色主题版本。
5. 保留 1024 进制的数值计算，将应用及网页的标签统一改为 `KiB`、`MiB`、`GiB`、`TiB`。详情页对导入时可能漏检的音轨、字幕信息标注来源，以播放器实际轨道为准。
6. 设备测试实测会卸载被测应用并清除其私有数据，现对 `connected*AndroidTest` 增加显式保护；只能在确认可丢弃的模拟器上用 `-PallowDestructiveConnectedTests=true` 运行。

## 实施文件

- `tools/mpv-static/build.sh`、`sources.tsv`、`jni/`、`NOTICE.txt`、`LICENSE.libmpv-android`：固定源码、JNI 包装、静态链接、16 KiB 页对齐与许可证说明。32 位 x86 为避免 `R_386_32` 非 PIC 重定位而关闭 FFmpeg 汇编；其他 ABI 保留架构优化。
- `core/playback/src/main/java/.../PlaybackEngine.kt`、`PlaybackEngineProvider.kt`、`mpv/MpvPlaybackEngine.kt`、`mpv/MpvNativeProbe.kt`、`dev/jdtech/mpv/MPVLib.kt`：真实引擎、状态/轨道与 JNI；删除 `MockPlaybackEngine`。
- `core/playback/src/main/jniLibs/{arm64-v8a,armeabi-v7a,x86,x86_64}/libplayer.so`：各 ABI 已剥离的随包 JNI 库；未剥离目标、静态库与重新链接材料保留在本机构建工作区。
- `feature/player/.../PlayerScreen.kt`、`PlayerOverlays.kt`、`PlayerGestures.kt`、`PlayerViewModel.kt`、`MpvProbeScreen.kt`、`MpvProbeViewModel.kt`：真实画面、播放器控制、Surface 生命周期、验证页、临时倍速与进度保存。
- `feature/detail/.../MediaDetailScreen.kt`、`feature/settings/.../SettingsScreen.kt`、`domain/.../Settings.kt`：导入探测说明、字幕样式边界及第三方许可提示。
- `app/src/main/res/values/strings.xml`、`values/colors.xml`、`drawable/ic_launcher_foreground.xml`、`drawable/ic_launcher_monochrome.xml`、`mipmap-anydpi-v26/ic_launcher*.xml`、`feature/home/.../HomeScreen.kt`：名称与自适应图标；`applicationId` 未改变。
- `core/common/.../Formatters.kt`、`CommonLogicTest.kt`、`data/src/main/assets/web-upload/app.js`、`index.html`：二进制单位及网页品牌。
- `app/build.gradle.kts`：版本号、设备测试运行器及防误删保护；`app/src/androidTest/`：真实视频帧、音轨、字幕和跳转回归。
- `app/src/main/assets/third-party-licenses/`：随包许可和源码版本清单；静态链接分发仍须按实际许可证提供相应源码与可重新链接材料。
- `README.md`、`docs/README.md`、`docs/CHANGELOG.md`、`docs/architecture/{overview,playback-abstraction,mpv-integration-feasibility,feature-status}.md`：同步现状、验证范围和风险。

## 数据恢复与测试安全事件

在现有模拟器上运行 `:app:connectedDebugAndroidTest` 后，Android Gradle 测试任务收尾执行了目标包的 `deletePackageX`，导致模拟器中的应用私有媒体与数据库被删除。该操作没有作用于实体手机；模拟器 `/sdcard/Download` 的原始视频未被删除或修改。

随后通过系统文件选择器重新导入此前存在的 `sample-a.mp4` 和 `sample-big.mp4`，并分别对原文件和私有副本执行 SHA-256：

- `sample-a.mp4` 两侧均为 `9c6a35bcac5a76e1a86558bfb2aa1d080430057d0f5a178a5e849ef3eb6e1af9`。
- `sample-big.mp4` 两侧均为 `a184500b1781723a587f7f023f7aeaf2b4211b601fe83eb7624a594114d66f48`。

模拟器重启后确认两段私有视频仍在，ZeroPlayer 首页显示 `2 部 · 389.6 MiB`。

未找到此前数据库的可用备份，旧播放进度及其他数据库记录不能恢复。后续新增 Gradle 保护，在没有显式属性时阻止设备测试；实测拒绝后应用私有哨兵文件仍在。

## 后续验证建议

- 在目标 ARM 真机上验证 H.264/HEVC/AV1 的 `hwdec-current`、实际解码器、音频听感、Surface 重建和软件回退；必要时覆盖 4K、长视频和不同厂商。
- 回归外挂 SRT/ASS、字幕样式与延迟、画面比例、锁屏/退后台、播放列表循环和随机播放。
- 对外分发前按实际组件许可证准备对应源码、许可声明、修改和可重新链接材料；评估 ABI 拆包以降低安装包体积。
- 设备测试只使用可丢弃模拟器。不要在含个人数据的设备或模拟器上用确认属性解除保护。

## 执行验证记录

### 环境

- Windows 11 x64；Android Studio JBR 25；Gradle 9.6.0 / AGP 9.5.1。
- WSL2 Ubuntu 24.04.5、Meson 1.8.5、Android NDK 29.0.14206865；NDK 下载文件 SHA-1 与 Google 发布值 `87e2bb7e9be5d6a1c6cdf5ec40dd4e0c6d07c30b` 一致。
- Android Emulator 37.2.13，Android 17 / API 37，x86_64；没有连接 ARM 物理手机。

### 命令、实际输出与结论

| 验证 | 命令或操作 | 实际结果 |
|---|---|---|
| 原生构建 | `ANDROID_NDK_HOME=... MPV_WORK_DIR=... bash tools/mpv-static/build.sh --abis <ABI> --jobs 4 --output <目录>`，分别运行四种 ABI | 四种 ABI 均生成 `libplayer.so`；脚本检查 JNI、解码符号和 `DT_NEEDED`，仅允许 Android 系统共享库 |
| 构建与容量单测 | `gradlew.bat :core:common:testDebugUnitTest :app:assembleDebug --no-daemon` | `BUILD SUCCESSFUL`；核心模块 11 项单元测试通过，含 1023 B、KiB、MiB、GiB、TiB 边界 |
| APK 构建 | `gradlew.bat :app:assembleDebug --no-daemon` | `BUILD SUCCESSFUL`；包名 `com.privateplayer.media`，显示名 `ZeroPlayer`，版本 `0.3.0` |
| 真实解码回归 | `:app:connectedDebugAndroidTest`，在加入保护之前执行 | `MpvPlaybackInstrumentedTest` 1 项通过、0 失败；使用私有中文/emoji 文件名、真实 Surface 的 PixelCopy 视频帧、双音轨、内嵌字幕与暂停跳转。**该命令随后卸载了目标应用，见上方数据安全事件** |
| 误删保护 | 无确认属性执行 `:app:connectedDebugAndroidTest` | 按设计失败并提示设备数据将被清除；配置缓存正常保存，事先写入的私有哨兵文件仍存在 |
| 离线播放 | 模拟器启用飞行模式，安装后的应用播放私有 H.264/AAC 样本 | 播放界面显示 libmpv、真实变化的视频帧和进度；结束后恢复飞行模式原状态 |
| 控制与轨道 | 模拟器实际操作播放器和验证页 | 暂停期间位置保持，快进从 00:01 到 00:11；双音轨切到 `jpn`，内嵌 `zho` 字幕实际显示；长按 2.0× 松开恢复 1.0×，横向滑动跳转至 00:09 |
| Surface 与硬解 | 验证页播放中旋转；读取 MPV 日志及 `hwdec-current` | 横屏后仍显示视频；模拟器 MediaCodec 报 `CodecException` 并回退软件，`hwdec-current=no`。`AudioTrack` 输出初始化成功，未人工听音 |
| 应用名、图标与容量 | 模拟器启动器、首页及导入页 | 桌面与首页显示 `ZeroPlayer`，自适应图标可见；容量显示示例 `6.7 GiB · 391.8 MiB` |
| 网页名称与单位 | 用实际 HTML/CSS/JS 静态资源在本机浏览器选择 2,250,675 字节测试文件 | 页面显示 `ZeroPlayer` 和 `2.15 MiB`；只选择文件，未重复上传。截图工具超时，使用真实 DOM 快照验证 |
| APK 结构与签名 | `zipalign -c -P 16 4 app-debug.apk`；`apksigner verify --verbose app-debug.apk` | 16 KiB 对齐检查退出码 0；APK 签名 v2 验证通过。Gradle 未找到 Windows 原生剥离工具而提示原样打包；库已由 Linux NDK `llvm-strip` 剥离 |

### 交付物

- Debug APK：`app/build/outputs/apk/debug/app-debug.apk`，169,203,854 字节。
- SHA-256：`B141120D534BE537FE40D76A9ED15803555FF6B85C05486CCAEF4BDDE948742F`。
- 该 APK 为调试签名版本，不等于已完成应用商店发布或全设备兼容性验证。

### 已知限制

仅模拟器验证实际播放，尚无物理硬解及人工听音证据；Android 系统元数据探测可能漏掉容器字幕。浏览器服务在截图及关闭测试标签时超时，网页静态资源、名称与单位已由真实 DOM 快照核验；临时网页服务与端口转发已停止。历史模拟器数据库记录无法恢复；原始外部视频保留且两段可恢复样本已校验重导。
