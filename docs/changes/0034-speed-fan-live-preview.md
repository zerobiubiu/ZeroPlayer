# 扇形倍速与按住试播

状态：1.3.0（10300），兼容的播放操作变更。范围：`feature/player` 的倍速面板、会话临时速度和相关测试，`feature/settings` 的震动开关名称。替代 [0028](0028-speed-picker-and-launcher-icon.md) 的网格与「松手前只看候选」行为；不修改默认倍速范围、已有偏好、私有媒体或 libmpv 二进制。

## 怎么选

轻点左下角的当前倍速，向上、向右展开四分之一扇面。再轻点档位即可切换；轻点方形原点是选择 **1×**。拖动不是必需操作。

| 从左（靠正上）到右（靠正右） | 近 → 中 → 远 |
|---|---|
| 第一扇 | 0.75× → 0.5× → **0.25×**（最高处） |
| 第二扇 | 2× → 2.5× → 3× |
| 第三扇 | 3.5× → 4× → 5× |
| 第四扇 | 6× → 7× → 8× |

需求原文的「第六」与参考图和四组明细不一致，以图示四扇和全部列出的数值为准，6/7/8 在最右的第四扇。旧网格的 1.25、1.5、1.75 不占新扇位，外部播放器/默认速度仍可使用范围内的自定义值。

长按按钮产生一次系统长按反馈；同一根手指进入档位时直接以该速度试播，不等待抬手。跨档再反馈，同档内移动不重复。常规档保留选中语义，试播档另报「正在试播，松开选定」，不能把临时档报成已提交。长按期间回到原点或弧外立即恢复按下前的**精确**速度，不是回到 1×；再移入可继续试播。在有效档抬手就保持该档，系统取消、返回、窗口离开或切片不能留下旧的临时速度。试播不改默认值，不跳转，不改变播放/暂停意图。进度与倍速震动共用原有开关；长按**视频画面**仍走独立的临时加速与松手恢复。

## 回归入口

- [真实单指扇面探针](../../app/src/androidTest/java/com/privateplayer/media/PlayerSpeedPickerProbe.kt)：发送系统 DOWN/MOVE/UP 或 CANCEL，同一 downTime；从可见标签中心与原点独立比较角度/半径，不调用生产几何函数。覆盖未抬手的引擎快照、矩形内圆弧外、非预设原点恢复、轻点 1×、试播前后和图片。
- [libmpv 样片](../../app/src/androidTest/java/com/privateplayer/media/MpvPlaybackInstrumentedTest.kt)与[真实 MainActivity / Media3](../../app/src/androidTest/java/com/privateplayer/media/MediaSessionLifecycleInstrumentedTest.kt)：会话换片、外部精确速度、前后台和真实偏好。16 秒彩条片的全档和取消矩阵在暂停时操作，播放推进另取短区段；不会用长时间维持 8× 把片尾当成手势结果。

## 合并构建与第一次设备验收

Windows x64、本机 Android Studio JBR、SDK 37、Build Tools 36.0.0，仅构建调试包。首个包括播放器、文案与矢量图标的版本正常离线运行：

```powershell
.\gradlew.bat --offline --console=plain "-Pandroid.builder.sdkDownload=false" testDebugUnitTest lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

`BUILD SUCCESSFUL in 48s`；753 项任务：252 执行、3 来自缓存、498 为最新。该完整运行的 30 个 JUnit XML 用 UTF‑8 逐类核对 `<testcase>` 与 suite 计数：167 项＝165 通过＋2 跳过，0 失败/错误。两条均为 `FileTreePathsTest` 在本机没有建符号链接权限的用例（「清理非空目录不跟随其中的符号链接」「拒绝符号链接目录文件和根目录」）。14 份模块 Lint 报告共 0 error、14 warning。后续修订的局部验证不再累加到这些数字。

独占 `emulator-5594`，API 37、x86_64/ARM64 转译、1080×2400 / 420 dpi。只从本机 AVD 复制硬件配置至新临时盘，使用 `-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save`。实测媒体音量 0 且静音。用限定该 serial 的 `adb install -r -t` 和 `shell am instrument -w -r -e class …`；未使用 `connected*`。用户设备与原 AVD 用户盘不动。基线真实 `MainActivity` 收到「Viewing full screen」，实际点 `Got it`；未用系统设置伪造首次确认。

- 首包两向的 12 个有效档均在同一手指 **MOVE、尚未 UP** 时抵达真实 libmpv，暂停位置始终是 **5250 ms**。中间回到原点或矩形内的外弧角，读数均是 **1.375**；在 8× 的 UP 前/后也都为 **8**，观察流中没有短暂 1.375。两向分别点 8×、0.25×、1× 成功；CANCEL、系统返回及无有效档的 UP 都保持基线。横屏另一段在播放中试播 0.5×，**6000 → 6250 ms 后才松手，UP 后 6332 ms**。画面长按独立从 0.75→2→0.75、6667→6917 ms。切片方法在第 1 段按住 5× 时切到第 2 段，UP 后第 2 段为 **1.375× @ 82 ms**。这两个方法的 instrumentation 状态均为 0。
- 首包图片暴露弧边漏出底栏剩余时间/顶部标题残片，横屏的「当前 1.375×」也容易与 0.5× 试播混淆；保留真实画面与原指针链，展开时暂不绘制常规控制栏，原点方块与按钮短边和底边对齐，非预设标签明确是「已选」值。
- 首轮原拟 9 个方法，前两项完成；第三项真实 MainActivity 的 1.375→按住 5→退后台 1.375 和默认值 1.0 已被记录，但**该方法当轮没有通过**。14:45:19 的第一个 fatal 是 PID 7943、TID 7957（AndroidJUnitRunner），ABI x86_64、guest arm64。`libc.so:abort → libart.so:Runtime::Abort/Thread::AssertNoPendingException` 携带既有 JUnit 断言「关闭丢失断点：控制器=5151，关闭后库=4500」。最后动作为返回文件树、停服务、旧控制器断连，对应断言先分别等控制器、数据库，到下一时点又读取一次控制器；不是所见的 libmpv 解码线程崩溃，也没有据此认定硬件转译或内存不足。修正测试在同一暂停媒体上同时取控制器/库，二者各对 `seekTo(4500)` 误差 <150 ms 且彼此 <150 ms。真实关闭后再对 4500 和关闭前控制器，重开首 READY 对目标保留最多提前 300 ms 并加向后上限；原断连/停服、播放次数和后续状态检查均保留。中断后六个尚未运行的定向用例另见 [0035](0035-ui-copy-density-audit.md#验证方式与边界)。

## 修后扇面、反馈与截屏

在本次应用的前景窗口使用系统触摸投递，不按 Compose 测试动作代替现场滑动。修后同一根手指的速度流与物理坐标保存在[设备日志](../ui-ux-audit/implementation/evidence/speed-fan-1.3.0.txt)；末行标识首次单独完成的换片样本。0.5× 和 5× 是实际引擎速度，而不只是 UI 所示的候选。

| 场景 | 设备所见 |
|---|---|
| [竖屏点开扇面](../ui-ux-audit/implementation/evidence/speed-fan-portrait-1.3.0.png) | 1080×2400，1.3 字号。左下 1× 方块 `[98,2222][224,2348]`（126×126 px＝48×48 dp），底边距 52 px，约 20 dp；白底为实际已选 0.5×。四扇数字近→远各三档，0.25× 在最高处。修后右弧不再漏出上版的底栏「0」。 |
| [横屏按住 0.5× 试播](../ui-ux-audit/implementation/evidence/speed-fan-landscape-1.3.0.png) | 2400×1080、同字号与底距，原点 `[98,902][224,1028]`。左上方 0.5× 描边且有下划线，右上角「已选 1.375×」是手势前常规速度。12 档及 1× 完整显示，左右各自实点/滑选，标签无相交或残缺顶栏。横屏播放中的该档 **5832→6000 ms**，UP 后仍 **0.5× @ 6082 ms**。视频本身的时间戳/彩条是样片画面。 |
| 暂停或中止 | 两向同指实测完整快照序列均为 `[1.375, 0.75, 0.5, 1.375, 0.25, 2, 2.5, 3, 3.5, 4, 5, 1.375, 6, 7, 8]`，位置始终 5250 ms；回原点/实际圆外、CANCEL、Back 各自恢复准确原速。播放中长按**画面**另测 0.75→2→0.75，6417→6832 ms。 |
| 系统震动 | 真 `MainActivity`、Media3 暂停于 1.375×，在独立模拟器打开/关闭同一偏好，真实 DOWN→0.5×→在本档再 MOVE 三次→5×→CANCEL。按应用 UID 与新时间戳从 `dumpsys vibrator_manager` 的 recent history 比对：开为 `[0, 4, 4]`（一次 `LONG_PRESS`、两次 `CLOCK_TICK`），关为 `[]`；结束后恢复测试前偏好。JVM 同类覆盖按住中原点/弧外重新进入、同档去重以及无需第二次设置的提交，修后定向 **5/5**。模拟器事件不等于实体马达手感。 |

此轮三项设备定向中，图标与修后扇面的方法状态均为 0；第三项全流程不通过，不能称为 `OK (3 tests)`。新同步检查在同一媒体暂停 **10 秒**仍见控制器 5067 ms、无播放意图，而服务 dump 的真实引擎和页面均为 PAUSED @ 4500 ms、Room 为 4500 ms、playCount=1（`device-delivery.log:43–49`）。因此「不同步只因为夹具跨时取值」的初步判断**不成立**：控制器确实长期保留旧位置，必须修正适配器，不能靠放宽误差或绕过控制器解决。

## 追加修复：暂停小幅跳转也更新系统控制器

[Media3 适配器](../../core/playback/src/main/java/com/privateplayer/core/playback/session/MpvMedia3Player.kt)过去只对引擎新快照调用 `invalidateState()`。在 Media3 1.11.1 中，同一条目的不足 1000 ms 位置变化默认不自动通知为位置跳变；异步 seek 提交时 libmpv 还可能停在上一帧。现在只在**同一媒体前后均已就绪、暂停且无播放意图**、真实位置有变时，用 `DISCONTINUITY_REASON_INTERNAL` 发布实际落点。无二次 seek、不伪报命令目标；常规播放 tick、首次加载、换片、仅变速度/缓冲或相同位置重读不制造跳变。[映射单测](../../core/playback/src/test/java/com/privateplayer/core/playback/session/SessionPlaybackMappingTest.kt)覆盖正反两个方向及这些反例。

修后仅跑 `:core:playback:testDebugUnitTest :core:playback:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`（同样离线），**19 秒 / 427 项，40 执行、387 为最新**。核心播放 2 类 24 项全过，模块 lint 无警告。长方法的第一版物理探针把 Slider 的**整个触控宽度**当作有效轨道，名义目标 3900 ms；三条实际读数均为 3667 ms。此处是探针坐标错误，不是控制器再次落后。改为对称中点（16 秒片的 8000 ms），在其前 700 ms 准备起点，保留独立已知目标及三路 <150 ms 门槛，不扩大超时。

只重打测试 APK（6 秒，342 项，6 执行），覆盖安装后单独执行：

```text
adb -s emulator-5594 shell am instrument -w -r -e speedEvidence true -e class com.privateplayer.media.MediaSessionLifecycleInstrumentedTest#serviceAndControllerSharePlaybackAcrossBackgroundAndActivityRecreation com.privateplayer.media.test/androidx.test.runner.AndroidJUnitRunner
```

**`OK (1 test)`，27.818 秒，方法状态 0，`INSTRUMENTATION_CODE: -1`。** 同一媒体 ID 8、暂停且 playCount=1，控制器 / Room / 服务 dump 中的真实 libmpv 三列读数每次完全相同：20% **3200** → 准备 **7300** → 单指点进度条中点 **8000** → 外部控制器后移 **7300** → 恢复原步骤 **3200** → 关闭前 **4500 ms**。返回文件树后服务停止、旧控制器断开，库仍为 **4500 ms**；新会话首个 READY 为 **4505 ms**，重开后播放次数为 2。后台播下一段、文件错误、CPU 锁释放与最终服务销毁等原断言也都跑完。该轮系统反馈仍为开启 `[0,4,4]`、关闭 `[]`。

## 最终交付和边界

| 调试产物 | 字节 | SHA‑256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 53,684,274 | `ede201105d8b6a8fb3555941f8a0fd09fa38700235348889208c91f709bc464a` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,851,513 | `f2e6d05c5ef799fb59f982516fa541fc8b0130aa819286cd4394b3dd6cbd82c9` |

本机 `build/reports/speed-fan-1.3.0/` 保留共同门禁 `combined-build.log`、`gate-summary.txt`，首批/补跑及故障诊断 `device-main.log`、`device-copy.log`、`session-crash.log`、`device-delivery.log`，最终 `final-session-gate.log`、`device-session-accepted.log`、`accepted-session-readings.txt`、`delivered-icon-equivalence.txt` 和 `cleanup.txt`。安装采样的[图标记录](0036-launcher-vector.md)与最终应用内 8 份前景/单色资源以及两个自适应入口逐项相同，未为适配器修复重复采图。

结束时媒体流静音、音量 0；只关闭本次模拟器，确认端口 5594 对应的序列、进程均已消失并移除专用临时 AVD。没有安装发行包、使用 `connected*`、清理用户设备、提交或推送。没有将 emulator 系统事件、转译的 libmpv 或图片推断为真机触感、硬件解码、高倍速听感或真人 TalkBack 体验；本次是一次项目共同门禁加必要定向复验，不是所有仪器化用例一次全绿。
