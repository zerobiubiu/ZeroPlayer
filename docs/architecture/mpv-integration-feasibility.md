# MPV / libmpv 集成可行性验证报告

状态：现状文档（2026-11 源码核查、自建构建与模拟器实测）  
影响范围：`core:playback`、`feature:player`、`tools/mpv-static`

结论：**自建 Android libmpv 已在模拟器实际播放并接入正式播放器；模拟器的 MediaCodec 解码失败后回退软件解码，物理设备硬解尚未验证。**

## 1. 验证目标

1. libmpv 能否作为依赖接入 Android 应用（而不是假设桌面 MPV 接口可用）；
2. 渲染路径：如何把画面输出到 Android `Surface`；
3. 硬件解码能力：设备与 mpv 选项如何配合；
4. 工程约束：构建环境、ABI、包体、许可证；
5. 给出可执行的接入步骤与风险清单。

## 2. 证据与事实

### 2.1 mpv-android 不是可依赖的 AAR

官方 README 明确：

> mpv-android is **not** a library/module (AAR) you can import into your app.
> If you'd like to use libmpv in your app you can use our code as inspiration.
> The important parts are `MPVLib`, `BaseMPVView` and the native code. Native code is built by these scripts.
>
> 来源：https://github.com/mpv-android/mpv-android/blob/master/README.md

结论：不存在“加一行依赖即可”的官方途径；要么自建 JNI 包装 + 自编原生库，要么引入第三方打包产物（需自行承担许可证与维护风险）。

### 2.2 JNI 包装与 Surface 渲染

`MPVLib.kt`（官方参考实现）核心声明：

```kotlin
object MPVLib {
    init { for (lib in arrayOf("mpv", "player")) System.loadLibrary(lib) }
    external fun create(appctx: Context)
    external fun init()
    external fun destroy()
    external fun attachSurface(surface: Surface)
    external fun detachSurface()
    external fun command(cmd: Array<out String>)
    external fun setOptionString(name: String, value: String): Int
    external fun getPropertyString(property: String): String?
    // 属性观测、事件回调等
}
```

`BaseMPVView.kt` 继承 `SurfaceView`，在 `surfaceCreated` 调用 `attachSurface(holder.surface)`，在 `surfaceDestroyed` 中**先确保渲染停止**再 `detachSurface()`。

结论：渲染路径成熟且明确——`SurfaceView` + `ANativeWindow`，本项目的 `PlaybackEngine.attachSurface(Surface?)` 接口已按此设计。

### 2.3 渲染与硬解配置（官方默认值）

`MPVView.kt` 中：

```kotlin
setVo(if (gpu_next) "gpu-next" else "gpu")
setOptionString("gpu-context", "android")
setOptionString("hwdec", hwdec)                 // 硬解开启时为 mediacodec 系列
setOptionString("hwdec-codecs", "h264,hevc,mpeg4,mpeg2video,vp8,vp9,av1")
```

结论：硬解依赖 `mediacodec` / `mediacodec-copy`，需要在目标设备上验证；不同 SoC 的 MediaCodec 行为差异较大，必须逐设备验证。本项目的 `CodecCapabilityProbe` 已能在运行时列出设备解码器与硬解标记，为后续选择 `hwdec` 提供依据。

### 2.4 原生构建环境

`buildscripts/README.md`：

> Compiling the native parts is a process separate from Gradle and the app won't work if you skip this.
> This process is supported on Linux and macOS. Windows (or WSL) will **not** work.
> `./download.sh` … `./buildall.sh`（默认仅 armv7l，可追加 arm64 / x86 / x86_64）

结论：上游官方脚本声明不支持 WSL；本项目使用单独的固定源码构建脚本，曾在 WSL2 Ubuntu 24.04、NDK r29 上实际构建四种 ABI。当前项目按要求仅保留 ARM64，不能把该结果扩大为上游官方脚本支持 WSL。

### 2.5 许可证

- JNI 包装来源及对应许可见 `tools/mpv-static/LICENSE.libmpv-android`；包装层许可不代表所有被链接组件的许可。
- 本项目 mpv 配置 `-Dgpl=false`，FFmpeg 未启用 GPL、version3 或 nonfree，但这不足以单独证明整个产物的许可证。
- 静态链接的 LGPL 及其他组件可能要求提供对应源码、许可、修改和可重新链接材料；随包许可不自动完成全部义务。见 `tools/mpv-static/NOTICE.txt`。

### 2.6 包体与 ABI

0.3.0 曾构建四种 ABI；从 1.0.0 起，工程和 APK 仅保留 `arm64-v8a`。libmpv 与 FFmpeg 等第三方库静态链接进 JNI 库；Android 系统库和既有 AndroidX `.so` 仍按平台机制动态加载。

其他 CPU 架构的设备无法直接安装运行，除非系统本身提供 ARM64 转译；应用 ID 不变。

## 3. 本机运行时探测结果

环境：Windows 11、Android Emulator 37.2.13、Android 17 / API 37 / x86_64。样本为应用私有目录中的 H.264 640×360 视频、两条 AAC 音轨和一条内嵌 SRT 中文字幕，文件名包含中文和 emoji。

以下为 0.3.0 阶段的历史验证记录；当时使用的专用验证页已移除，后续以设备仪器测试和正式播放器核验为准。

| 探测项 | 实际结果 | 证据边界 |
|---|---|---|
| JNI 与 libmpv | 真实 `libplayer.so` 加载并初始化，运行时版本 `mpv v0.41.0` | 加载本身不等于解码成功 |
| 视频与 Surface | 不同时间的屏幕截图显示不同视频帧；播放中旋转至横屏后仍显示画面 | 模拟器、单一样本；真机生命周期待测 |
| 播放控制 | 验证页暂停后进度保持，快进 10 秒从 00:00 到 00:10；正式播放器显示真实画面 | 其他手势和长视频需回归 |
| 音轨和字幕 | 实际解析两条音轨并切到第二条；选择内嵌 SRT 后屏幕显示中文字幕 | `AudioTrack` 初始化成功，但本次未人工听音 |
| 硬件解码 | 尝试 `mediacodec` 和 `mediacodec-copy` 时模拟器发生 `CodecException`，随后软件回退；页面显示 `hwdec-current=no` | **不是硬解成功**；没有连接物理设备 |
| 私有文件名 | 中文及非 BMP 字符文件名经 JNI 正常加载并播放 | 仅对该样本验证 |

## 4. 结论

1. `SurfaceView`、JNI 与 libmpv 视频解码链路已在模拟器接通；`AudioTrack` 输出已初始化，但未人工听音；播放器不再使用 Mock。
2. 自建静态依赖单 JNI 库满足单 APK 安装后的本地播放需求，但 JNI、AndroidX 与系统库仍存在动态加载。
3. MediaCodec 失败后的软件回退已观察到；真机能否硬解仍须按实际设备和编码验证。
4. 原生源码、许可证和可重新链接材料必须随分发方案妥善保留，不能只依据包装仓库的许可证标签判断。

## 5. 后续设备验证矩阵

已完成固定源码的 ARM64 原生构建、`MpvPlaybackEngine`、Surface 挂载，以及历史版本的模拟器基础播放。后续目标设备至少验证：

1. 1080p H.264 / HEVC 的实际 `hwdec-current`、解码器、播放与 Seek。
2. 4K HEVC / AV1（设备支持时）、软件回退与长时间播放。
3. 多音轨切换、外挂 SRT/ASS、字幕延迟与样式。
4. 横竖屏切换、退后台/返回前台及 Surface 重建。
5. 播放结束、单个循环、目录循环、随机和自动下一文件。

## 6. 风险清单

| 风险 | 影响 | 缓解 |
|---|---|---|
| 原生构建环境或依赖漂移 | 无法复现、产物不一致 | 固定源码及摘要、记录 NDK/工具版本；保留构建工作区 |
| 静态链接许可证 | 对外分发风险 | 核查各组件和构建选项，保存源码、许可、修改及重新链接材料 |
| 包体与架构兼容性 | ARM64 以外设备不能直接安装 | 明确标注仅 ARM64，不提供额外架构 APK |
| MediaCodec 兼容性 | 部分设备黑屏/绿屏/掉帧 | 逐设备验证；准备 `mediacodec-copy` 与软解回退 |
| Surface 生命周期 | 切换/后台后黑屏 | `detachSurface` 前停止渲染，重建后重新 attach（参考 BaseMPVView） |
| 字幕渲染差异 | ASS 特效不一致 | 依赖 libass（mpv 内置），以实机验证为准 |
| 许可证变更 | 合规 | 记录 mpv/FFmpeg 版本与对应源码快照 |
