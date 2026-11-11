# 播放引擎抽象

状态：现状文档  
影响范围：`core:playback`、`feature:player`、`tools/mpv-static`

## 1. 接口与依赖方向

`core/playback/PlaybackEngine.kt` 暴露引擎无关的播放源、状态、音轨、字幕轨、媒体信息与错误事件。主要能力包括初始化、挂载/调整/解绑 `Surface`、加载、播放/暂停/停止、跳转、倍速、音量、音轨和字幕选择、字幕延迟及样式、画面比例、诊断与释放。

- `snapshot`、`audioTracks`、`subtitleTracks` 是 `StateFlow`；`events` 传递播放结束、失败和轨道变化。
- `PlaybackSource` 使用 Storage 层解析的应用私有文件路径。真实引擎再次按规范路径限制媒体与外挂字幕的根目录，不接受外部 URI 或网络地址。
- UI 通过 ViewModel 和共享 `PlaybackCoordinator` 借用引擎，生产引擎由协调器创建和释放；`domain` 不依赖 Compose 或 MPV。
- `diagnostics()` 返回引擎版本、`hwdec-current`、实际解码器和编解码信息；无法读取的字段为 `null`，不推断成功。

## 2. 后端与打包

`DefaultPlaybackEngineProvider.create()` 固定创建 `MpvPlaybackEngine`，不再使用模拟时钟或回退到 Mock。初始化、加载或播放失败时返回错误；不会把不可播放的媒体显示成正常播放。

`arm64-v8a` 的 `libplayer.so` 静态包含 libmpv、FFmpeg 和其他播放依赖。APK 仅支持 ARM64，不再包含其他 ABI。Kotlin JNI 包装加载随包入口库，Android 系统库及应用已有 AndroidX `.so` 仍按平台方式动态加载。安装后播放不依赖另行下载的原生库；FFmpeg 构建关闭网络协议，应用独立的网页上传服务不受影响。

- JNI 绑定基于 `jarnedemeulemeester/libmpv-android` v1.0.0 的代码，包含 UTF-16/标准 UTF-8 转换、MPV 错误回传和结束原因回调。
- 原生源码版本、下载地址与 SHA-256 固定在 `tools/mpv-static/sources.tsv`；`tools/mpv-static/build.sh` 在 Linux / WSL2 和 NDK r29 下构建。
- `MpvNativeProbe` 检查 JNI 库能否加载。加载成功只证明链接成功；不代表视频、音频或硬解已验证。
- ARM64 的可重新链接材料与来源许可须按实际分发条款保留；应用随包携带第三方许可，具体限制见 `tools/mpv-static/NOTICE.txt`。

## 3. 播放与 Surface 生命周期

播放器的 `SurfaceView` 在创建、尺寸变化与销毁时，通过 ViewModel 将 `Surface` 交给共享播放协调器；UI 不引用 JNI 类型。引擎在解绑时先停用视频输出，再释放 JNI 持有的表面引用；页面离开或 Activity 重建不会因 Surface 销毁而释放仍在播放的引擎。离开页面时恢复进入前的屏幕方向。

页面 ViewModel 记录该导航项是否已经提交选片请求，后续 `ON_START` 只借用当前共享会话，不按旧路由反复选片。后台自动下一文件不依赖页面存活；如果服务已经释放，则按协调器保留的最后媒体建立暂停会话。该请求标记不是独立的播放状态，实际媒体和进度仍来自 libmpv。

引擎首选 `vo=gpu`、`gpu-context=android`，尝试 `mediacodec` / `mediacodec-copy`；不支持或失败时由 MPV 回退软件解码。编码格式、渲染驱动和设备实现会影响实际结果。硬解验收必须查看实际 `hwdec-current` 和设备解码器，不能以设备宣称支持或库加载成功替代。

MPV 属性观察驱动进度、时长、暂停状态、播放意图和轨道；结束事件保留 EOF、停止及错误的区别。普通文本字幕默认放在视频画面下侧，不占屏幕黑边；ASS 等自带排版的字幕不强制覆盖原样式。同目录同名字幕和已有单独导入的字幕均可加载。

## 4. 播放器状态管理

播放器按同目录的视频文件构造顺序播放范围，支持循环、随机与自动播放下一文件；不向用户提供独立播放列表。真实播放进度会持久化，未实际开始时不写入虚假的观看进度。长按临时倍速在松手时恢复原速度，倍速请求不在界面主线程执行。

为减轻长按倍速开始与结束时的音频链路重建，初始化时显式加载 libmpv 自带 `scaletempo2`。常速也会经过该滤镜；需要在真机复核听感、延迟与功耗，不能仅凭模拟器断言完全无卡顿。

横向手势区分快速短扫与逐秒微调，显示虚拟进度条并提供触觉反馈；大范围跳转使用底部实际进度条。播放器内的手机音量键只调整媒体音量并显示百分比，不主动弹出系统音量条。

MPV 技术验证由设备仪器测试承担，不再提供独立用户页面。

## 5. Android 系统媒体会话

AndroidX Media3 Common / Session 1.11.1 仅提供系统会话和控制接口，不引入 ExoPlayer 或第二套 FFmpeg。`MpvMedia3Player` 继承 `SimpleBasePlayer`，把播放、暂停、当前文件跳转、倍速和引擎音量请求转交现有 `PlaybackEngine`；不允许外部控制器设置文件或网络播放源。

`PlayerMediaSessionService` 管理会话和共享播放实例。播放界面与系统会话使用同一引擎，页面离开时只解除该页面的 Surface；播放期间的进度保存和自动下一文件不依赖页面存活。服务负责音频焦点、前台通知及后台播放所需的 CPU 唤醒；暂停、结束、错误和服务销毁时释放不再需要的资源。

会话状态来自引擎快照和当前媒体信息：媒体 ID 不匹配时不把旧进度映射给新标题，播放意图与实际播放状态分别映射。外部命令与切源共用同步门，以避免旧媒体命令作用于新文件；系统控制与界面操作仍以 libmpv 的实际结果为准。标题、时长和封面仅在已有数据可用时提供。

Media3 官方说明：[自定义 Player](https://developer.android.com/media/media3/session/player)、[后台播放服务](https://developer.android.com/media/media3/session/background-playback)。


## 6. 已验证范围与限制

在 Android 17 / x86_64 模拟器中，已使用应用私有目录内的 H.264/AAC、双音轨、SRT 字幕样本验证实际画面、暂停、跳转、轨道选择、中文字幕及旋转后显示。`AudioTrack` 输出初始化成功；本次没有人工听音结论。模拟器的 MediaCodec 路径出现 `CodecException` 后回退软件解码，`hwdec-current=no`；这不是物理设备硬解成功的证据。

导入时 Android 系统元数据探测可能漏掉容器中的字幕轨，详情页因此标注“导入探测”；实际轨道以播放器解析结果为准。ARM 真机、低版本 Android、不同编码和字幕、后台恢复、长视频及硬解仍需按 `mpv-integration-feasibility.md` 的矩阵验证。
