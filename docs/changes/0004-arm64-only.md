# ARM64 单架构打包

- 状态：已执行
- 影响范围：Android 安装包和 libmpv 静态构建
- 版本：1.0.0

## 背景

此前的调试安装包含四种 ABI 的播放器原生库。按用户要求，应用只保留 ARM64 架构，减少安装包体积。去掉其他 ABI 是安装兼容性变化，因此递增主版本；版本号不代表真机硬件解码已验证。

## 方案

- Android 构建只打包 `arm64-v8a`；应用 ID `com.privateplayer.media` 不变。
- 原生构建脚本只构建 ARM64，移除其他架构的预编译库及构建分支。
- libmpv、FFmpeg 等第三方代码仍静态链接进 `libplayer.so`；Android 运行时仍会加载 JNI 和 Android 系统共享库，不需要用户另外安装播放组件。
- 保留覆盖安装和私有文件；不执行会卸载应用的 Gradle connected 测试任务。

## 实施

- `app/build.gradle.kts`：ABI 筛选与版本信息。
- `core/playback/src/main/jniLibs/`：仅保留 ARM64 播放库。
- `tools/mpv-static/build.sh`：单架构构建入口。
- `tools/mpv-static/NOTICE.txt`、`app/src/main/assets/third-party-licenses/NOTICE.txt`：同步构建与许可证说明。
- `README.md`、`docs/architecture/`、`docs/CHANGELOG.md`：同步兼容范围和验证边界。

## 执行验证记录

- 环境：Windows 11 x64；Android Emulator 37.2.13，Android 17 x86_64 镜像，`libndk_translation` 支持 ARM64；原生重建使用 WSL2 Ubuntu 24.04 和 Android NDK r29。
- `tools/mpv-static/build.sh --jobs 8 --output .../arm64-only-check`：ARM64 原生构建完成，依赖与导出符号检查通过。重建结果不宣称字节可复现；打包使用既有已验证 ARM64 库。
- `./gradlew.bat :app:assembleDebug :app:assembleDebugAndroidTest --no-daemon`：ARM64 单架构中间构建 `BUILD SUCCESSFUL`。当时 APK 为 49,158,602 字节（46.881 MiB）；后续功能改动后的最终大小以最终 APK 实测为准。
- `aapt dump badging`：中间 APK 的 `native-code` 为 `arm64-v8a`，应用 ID 不变，版本为 1.0.0。`zipalign -c -P 16 4` 成功，APK v2 签名验证通过。
- 在只读模拟器中 `adb install -r` 成功；`dumpsys package` 显示 `primaryCpuAbi=arm64-v8a`、`secondaryCpuAbi=null`。覆盖安装前后，原有两段测试视频的 SHA-256 保持不变。
- 直接执行测试 APK 的 `MpvPlaybackInstrumentedTest`，结果 `OK (1 test)`；真实 Surface 输出画面并验证音轨、字幕和暂停跳转。测试没有调用会卸载应用的 Gradle connected 任务。

## 后续验证建议与已知限制

上述模拟器通过转译执行 ARM64 代码，不是实体 ARM 设备，也不能证明真实硬件解码或扬声器音质。应在物理 ARM64 设备上覆盖安装，核对已有文件，再测试硬件解码、音频、字幕与播放手势。32 位 ARM 设备和不具备 ARM64 转译能力的 x86 设备无法安装本版本。
