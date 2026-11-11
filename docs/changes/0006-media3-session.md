# Media3 系统媒体会话与 libmpv

- 状态：已执行
- 影响范围：播放器生命周期、系统媒体控制、后台播放
- 相关现状文档：[播放引擎抽象](../architecture/playback-abstraction.md)

## 背景

原播放器由页面 ViewModel 创建和释放 libmpv，页面退后台时暂停，无法满足通知栏、锁屏、媒体按键及页面退出后继续播放的要求。系统会话不能通过额外创建第二个引擎来规避生命周期冲突。

## 方案

1. 使用 AndroidX Media3 Common / Session **1.11.1**；不引入 `media3-exoplayer`，FFmpeg 仍只来自已有 libmpv 静态构建。
2. `MpvMedia3Player` 继承 `SimpleBasePlayer`，适配真实引擎的播放/暂停、当前文件跳转、速度、音量和媒体元数据；未实现的设置播放源和队列变更不对外声明。
3. `PlayerMediaSessionService` 管理 `MediaSession`，经共享协调器和应用页面借用同一引擎。页面离开只解绑自己的 Surface；服务销毁时释放会话、适配器、音频焦点、唤醒锁和引擎。
4. `PlaybackSnapshot` 同时携带真实状态、播放意图和当前媒体 ID。只有 ID 匹配时，系统会话才公开该媒体与进度；外部命令和切源共享同步门，并按提交顺序抑制过期请求。
5. 系统通知内容使用 Media3 原生通知提供器，但前台资格由服务统一管理：Android 15+ 恢复播放前先建立媒体前台服务资格，再请求音频焦点；被系统拒绝时保持暂停并显示错误。实际播放期间持有 CPU 唤醒锁，暂停、结束、错误和销毁时释放。标题、时长和封面使用已有数据；没有作者数据时不虚构。
6. 后台播放进度、同目录自动下一文件及循环模式由与服务同生命周期的播放会话处理，不随 Compose 页面销毁或重建重复创建。
7. 服务销毁不在主线程等待原生销毁：异步释放屏障串联旧实例关闭与新实例初始化，避免主线程等锁死锁，同时禁止两个有效原生实例重叠。
8. 页面不再拥有引擎释放时机后，暂停中的跳转也必须按真实位置变化保存断点；保留“实际播放过才记录”的约束，跳转不重复增加播放次数。

## 实施文件

- 版本及依赖：`gradle/libs.versions.toml`、`core/playback/build.gradle.kts`、`app/build.gradle.kts`。
- 引擎与适配：`core/playback/.../PlaybackEngine.kt`、`mpv/MpvPlaybackEngine.kt`、`session/MpvMedia3Player.kt`、`session/SessionMedia.kt`。
- 生命周期：`feature/player/.../PlaybackCoordinator.kt`、`PlaybackSession.kt`、`FocusPlaybackEngine.kt`、`PlayerViewModel.kt`、`PlayerScreen.kt`。
- 系统服务：`app/src/main/java/com/privateplayer/media/playback/PlayerMediaSessionService.kt`、`app/src/main/AndroidManifest.xml`。
- 回归：`core/playback/src/test/.../session/`、`app/src/androidTest/.../MediaSessionLifecycleInstrumentedTest.kt`、`PlaybackReleaseBarrierInstrumentedTest.kt`、`MpvPlaybackInstrumentedTest.kt`。
- 文档与许可：`README.md`、`docs/architecture/playback-abstraction.md`、`docs/CHANGELOG.md`、`app/src/main/assets/third-party-licenses/NOTICE.txt`。

## 后续验证建议与已知限制

- 优先在真实 ARM64 设备验证通知栏、锁屏、蓝牙耳机按键、音频焦点争用及不同厂商的前台服务限制；模拟器媒体键不能证明实体蓝牙链路。
- Android 15 及以上对后台音频焦点和前台服务启动有限制；系统拒绝时应保持暂停并显示错误，而不是报告播放成功。
- 不开放外部媒体地址或播放列表设置；会话只控制用户已导入应用私有目录的当前文件。
- Media3 Session 的必要传递依赖包含 `media3-datasource`；这不等于启用网络播放或引入另一解码器。

## 执行验证记录

- 执行环境：Windows 11 x64；Android 17 / x86_64 只读模拟器，通过 ARM64 转译运行应用原生库。
- 官方版本与依赖来源：[Media3 发布记录](https://developer.android.com/jetpack/androidx/releases/media3)、[1.11.1 Session POM](https://dl.google.com/dl/android/maven2/androidx/media3/media3-session/1.11.1/media3-session-1.11.1.pom)。
- `:core:playback:testDebugUnitTest`：状态映射 10 项、命令顺序/竞态 12 项，均通过。
- `:app:assembleDebug :app:assembleDebugAndroidTest :app:lintDebug`：`BUILD SUCCESSFUL`，Lint 为 0 错误、10 警告；未设置 Lint 基线或关闭检查。
- `:app:dependencies --configuration debugRuntimeClasspath`：实际解析 Media3 Common / Session / DataSource / Database 均为 1.11.1；依赖图无 ExoPlayer 或独立 FFmpeg 播放依赖。
- 首轮编译中修正 Future 泛型、Compose 作用域及跨模块可空值；Lint 中用公开 `onKeyDown` / `onKeyUp` 替代受限按键分发 API，明确 Media3 API opt-in，并为播放唤醒锁加上有界续期。
- 手工安装测试 APK 后执行 `adb shell am instrument`，`MediaSessionLifecycleInstrumentedTest` 与 `PlaybackReleaseBarrierInstrumentedTest` 得到 `OK (2 tests)`，26.972 秒。前者使用真实 libmpv 验证控制器和页面双向控制、媒体标题/时长/封面、暂停跳转持久化、息屏播放、全局系统媒体键、Activity 重建、退出重开、后台下一文件、终播重播、损坏文件错误和服务销毁后空会话；后者通过受控阻塞引擎注入验证释放屏障，不能冒充实体设备 JNI 压力测试。
- 重开页面的测试必须先确认目标播放器已显示：仅等待“控制器仍为同一媒体”会在导航完成前立即满足。实际日志证明延迟的是新导航项首次启动，并非旧 ViewModel 丢失状态；临时导航票据方案和诊断日志均已撤回。
- 本轮交付 APK：`app/build/outputs/apk/debug/ZeroPlayer-1.0.0-arm64-v8a.apk`，版本 `1.0.0` / code `4`，**51,634,179 字节（49.24 MiB）**；SHA-256：`b24a7cf35014edb8a3b7580ba26e354caa6d409e7331fbbb0f5783ee4d194810`。
- `aapt dump badging` 确认应用 ID 不变、minSdk 26、targetSdk 37、仅 `arm64-v8a`；APK 中三份原生库均为 AArch64，ELF LOAD 段与 ZIP 原生库条目通过 16 KiB 对齐核验。`apksigner verify` 验证 APK v2 签名成功，当前为 Android Debug 调试签名包，不作为正式商店发行签名。
- 随包 `libplayer.so` SHA-256 仍为 `fd04b92c17f6cf89128a1ab69e879168b8317853c80cb8c8f2c11fda6edb0fed`；本次没有重建或替换第三方播放引擎。
- 可丢弃模拟器中的原生系统界面冒烟得到 `OK (1 test)`，19.249 秒：按当前可见控件的真实位置注入触摸，分别点击系统媒体卡片和真实锁屏上的播放、暂停按钮，由外部控制器确认同一媒体 ID 的真实播放状态转换。不是直接调用 `controller.play()` / `pause()` 来替代系统按钮。验收后移除临时冒烟类，并恢复临时锁屏设置。
- 原生界面截图：[系统媒体控制中心](../assets/media3-system-controls.png)、[锁屏媒体控件](../assets/media3-lockscreen-controls.png)。截图只证明该模拟器的实际呈现；实体蓝牙链路、ARM 硬解及不同厂商界面仍需对应设备回归。
- 错误样本预先放入独立子目录，避免另一个仓库实例的通知被误当成应用 UI 的文件变化；清理通过文件树仓库删除整个唯一测试目录及其记录，不残留损坏媒体或影响原有文件。
- 移除临时界面冒烟类后，重新执行 `:app:assembleDebugAndroidTest :app:lintDebug`，`BUILD SUCCESSFUL in 21s`；现有 Lint 为 0 错误、10 警告，未设置基线或关闭检查。设备仅使用本次启动的只读副本，原有两份媒体的 SHA-256 验收前后相同。
