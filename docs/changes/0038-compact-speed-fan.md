# 单手倍速扇面：原点选档与按住反馈

状态：1.4.0（10400）验收记录。范围：`feature/player` 的扇面、触摸状态与临时倍速反馈，`app/src/androidTest` 的系统手势/真实引擎验收。与[导入失败恢复](0037-import-failure-recovery.md)同包；[0034](0034-speed-fan-live-preview.md)保留 1.3.0 的历史行为和截图。

## 原地与回到原点不是同一件事

| 手指的动作 | 抬手前 | 抬手后 |
|---|---|---|
| 按住不动，或仅在 Android 触摸阈值内颤动 | 保持按下前的精确速度，不产生候选 | 不改速 |
| 主动滑动超过阈值，落在 1× 方块内（包括从另一档滑回） | 立即试播 1× | 保持 1× |
| 滑到其余任一档位 | 立即试播该档，不改变暂停或播放意图 | 保持最后一档 |
| 滑出实际扇面，包括仍在外接方框中的点 | 立即恢复手势前原速，不必等松手；重新进入可继续试播 | 在外部松手保留原速 |
| 收到系统取消、返回、多指或失去宿主 | 结束试播，恢复原速 | 迟到的抬手不再提交 |

保留轻点操作和 13 个单选项，常规选中态与正在试播的候选分开。键帽移开手指显示候选，上方四分之一处显示同一引擎实际临时速度。长按视频画面仍是独立的临时加速，不弹出扇面、松手归还原速。两种手势都不写默认偏好。

轻点按[可见扇形几何](../../feature/player/src/main/java/com/privateplayer/feature/player/SpeedFanGeometry.kt)命中；长按的[单指轨迹](../../feature/player/src/main/java/com/privateplayer/feature/player/SpeedFanStroke.kt)只保留前次坐标、时间、档位及约 48 ms 内的有向位移，不保存完整手势。相邻边界内的小幅往返保留原档，有意沿边界法向移动则缩短切档所需距离；跨多档直接用当前点，交线不拼出手指没经过的“第三档”。按下的[会话试播句柄](../../feature/player/src/main/java/com/privateplayer/feature/player/SpeedPreview.kt)负责精确恢复，松手不会先重置再设置。

## 验收口径

- 对照基线采用 0034 同姿态的 1080×2400 / 420 dpi、字体 1.3 的实测按钮和标签坐标。入口可点击区域、最远标签的中心距离、外弧半径与底距分开度量；标签盒不是完整的扇形命中区。
- [系统触摸](../../app/src/androidTest/java/com/privateplayer/media/PlayerSystemTouch.kt)以屏幕坐标注入，整条链沿用原始 DOWN 时间与指针。设备用例的预期来自已渲染的单选标签、扇面外接框、系统 slop 和人工规定的轨迹；不调用生产 `hit`。观察实际 `PlaybackEngine.snapshot`、候选/常规单选、键帽与 HUD，并核对真实 Media3 与系统触觉记录。
- 横竖屏、画面比例和字号均记录实际读数。指尖到数字的净距是坐标代理；模拟器不能验证真人拇指的握持范围、触感、实体屏触控采样或所有厂商设备。

### 1.3.0 的可复核对照

从[原始坐标](../ui-ux-audit/implementation/evidence/speed-fan-1.3.0.txt)对 13 个标签中心逐个计算到按钮中心的欧氏距离，除以 `420/160` 换算 dp。外弧另从旧原图下沿描边（左下方块的左边至弧的右端）量，不把最远标签当作半径；抗锯齿使边界约有 1 px 不确定度。

| 1.3.0，字体 1.3 | 最远数字中心 | 外弧半径 / 短边 | 原点底距 |
|---|---:|---:|---:|
| [竖屏](../ui-ux-audit/implementation/evidence/speed-fan-portrait-1.3.0.png) | 777.8 px / 296.3 dp | 约 972 px / 370 dp / 90.0% | 52 px / 19.8 dp |
| [横屏](../ui-ux-audit/implementation/evidence/speed-fan-landscape-1.3.0.png) | 817.6 px / 311.5 dp | 约 1018 px / 388 dp / 94.3% | 52 px / 19.8 dp |

## 离线门禁与轨迹

Windows x64、Android Studio JBR、SDK 37 / Build Tools 36.0.0。首次同一 Gradle 调用为：

```powershell
.\gradlew.bat --offline --console=plain "-Pandroid.builder.sdkDownload=false" :app:testDebugUnitTest --tests com.privateplayer.media.ImportErrorMappingTest :feature:player:testDebugUnitTest lintDebug :app:assembleDebug :app:assembleDebugAndroidTest
```

首轮不是全绿：导入测试 10 项已通过，但 `:feature:player:lintDebug` 拦下组合内直接读取 `StateFlow.value`。已改为跟随生命周期收集试播撤销信号，由同一候选驱动键帽和反馈，并让 JVM 随机轨迹改用独立极坐标预期。修后只重跑上述命令中的播放器 JVM、根 `lintDebug` 和两项打包（25 秒，663 项任务，54 执行、609 为最新）。按实际 JUnit XML 计：导入 10/10、播放器 36/36，失败、错误、跳过皆为 0；14 份模块 lint 报告合计 0 error、14 warning。不是全项目设备套件。

- [确定性 JVM 轨迹](../../feature/player/src/test/java/com/privateplayer/feature/player/SpeedFanStrokeTest.kt)的独立预期从 1× 方块与 12 个标签盒推导半径，以 `hypot` / `atan2` 决定物理档。密度 1、1.5、2.625、3.5；窗口 230×190、320×256、411×915、915×411、1280×800 dp。`0x1400` 种子每组合 1200 步共 24,000 样点；包含 0/8/16/33/210 ms 间隔、跃迁和折返。断言未发现与物理点/上一实际档都不符的第三档或中心目标丢失。
- 另对 17 条内界做 ±1 dp 来回抖动，深入 7.25 dp 必须切档；6 个角/环交点量两方向和斜入；4 dp、12 dp 触摸阈值对照。以 8/16/33/64 ms 对同一行程重采样，分别 60/960 ms 进入并停留再折返。这里的约 120/60/30 Hz 是输入时间戳，不是硬件报告率。
- [设备轨迹](../../app/src/androidTest/java/com/privateplayer/media/PlayerSpeedFanPaths.kt)从真实窗口的 13 个单选项及面板位置反推。精细组允许在慢过界期间出现前档或后档，但整段引擎速度流必须不倒跳、无第三档；连续快扫组按同一 `downTime` 异步投递 11 个 MOVE 和马上 UP，只有结束后才同步 Compose 与引擎。

## 同配置实测：可达距离、快速输入与动效

以下完整手势矩阵取自键帽避邻修复后的 1.4.0 应用（SHA-256 前缀 `38a0e2e5`）。两向同一台独占 API 37 转译模拟器、1080×2400 / 420 dpi、字体 1.3。扇面半径都是 **588 px / 224 dp**，占短边 **54.4%**；按钮中心到最远数字中心 **173.0 dp**，比 1.3.0 同姿态少 **41.6% / 44.4%**。1× 方块仍为 48 dp，底距 **52 px / 19.8 dp**。缩短的是实际触达距离，不是把全屏热区画小。

同一真实暂停帧 **166 ms** 上，竖屏 33 个、横屏 57 个非中心精细样点全部符合预期。两向速度流均为 `[2, 2.5, 4, 5, 2.5, 8, 2.5, 5, 2, 5]`；包含 0.45–0.9 dp 抖动、10 dp 有意切换和外弧内/外 2 dp 再入。另异步投递 11 个 MOVE 掠过四扇再反向，竖/横首 MOVE 至 UP **111 / 83 ms**、末 MOVE 至 UP **0 / 1 ms**。两向最终都是 **4×**，重开仍只勾 4×。竖屏观测速度 `[5, 0.5, 2.5, 4, 7, 4]`；横屏 `[5, 0.5, 4, 7, 4]`，快速掠过的 2.5× 没有单独的快照；不能把中间输入采样等同于已发布的每次速度。没有观察到跨入其它环、终点丢失或抬手回跳。另在 4× 按住试播 0.25× 时真实旋转，旋转和旧 UP 之后都是 4×。

候选 5× 时键帽字形距指尖 **36.48 dp**；HUD 中心分别在视频高度的 **25.02% / 25.05%**。同指保持不动 **1613 / 1606 ms** 仍显示实际 5×，不因旧提示超时而消失。弧外再入时[连续取三帧](../ui-ux-audit/implementation/evidence/speed-fan-motion-1.4.0.png)：测试时钟 0/32/192 ms，箭头质心相对首帧横移 **2.0 / 6.7 px**，数字与暂停帧不变。这里记录的是一次换档入场，不是循环播放的假进度。

### 手势语义与会话

本段普通字号与 Media3 使用与 [0037](0037-import-failure-recovery.md) 相同的应用（`9f54791c…`），设备探针已按实际数字盒取证。双向各轻点全部 13 档。系统 slop 为 21 px；在 2× 上原地、阈内微动（≤0.82 slop）、主动在方块内右移 26.25 px、从 5× 折返方块及从 5× 划出实际弧边，抬手前/后的速度依次为 **2/2、2/2、1/1、1/1、2/2**，真实暂停位置始终 **5083 ms**。自定义原速 1.375× 时，回到方块确实试播 1×，弧外、系统 CANCEL、Back、第二根指针则各自恢复精确的 1.375×。

横屏播放中的 0.5× 试播在 UP 前 **6250→6417 ms**，UP 后仍为 0.5×；长按画面独立经历 **0.75→2→0.75×**，实播 **6750→7167 ms**。按住 5× 切至下一媒体之后，旧 UP 没有泄漏到新片，新片为 **1.375× @ 82 ms**。真实 MainActivity / Media3 在 1.375× 暂停、按住试播 5× 时退后台，恢复 1.375×，默认偏好前后均 1×。系统振动历史在打开设置时是 `[0,4,4,4]`（长按、0.5×、5×、1× 各一次，同档三次移动不重复），关闭时为 `[]`。这组普通字号手势与 Media3 长方法 `OK (2 tests)`，83.467 秒。

### 被设备拦下的两类问题

1. 最初把文字节点的 `TextLayoutResult.hasVisualOverflow` 当作已经裁字。实测 1.3 字体的 `2.5×` 父段落宽 200 px，真实 Text 节点宽 107 px、四字符盒宽 105 px、末字位置 4；该布尔值不能回答像素裁剪问题。现在逐字检查每个字符及乘号，与真实 Text/键帽两层边界比较，检查行数、末字和省略；断言前保存该次真实窗口。慢过界允许前后两档的样点则先同步候选，再比对同一时点的引擎，不用上一个时点的速度去查询下一时点的数字。
2. 首批 1.4.0 的截屏仍不合格：2.5× 键帽自身没截字，但右下透出邻档 3×、左上露出 0.25× 的尾部。小窗口 760×1280 / 440 dpi、字体 1.3、系统动画 0 的 0.25× 键帽和上方 HUD 还相交了 **32 px**。这些是产品表面问题，不是文字测量误报；不能仅改断言或用全屏截图的整体观感抵消。

修复用同一键帽圆角裁掉下方扇层文字和分隔线，不遮断真实视频；同档沿用一个靠近指尖、避开邻字的偏移。小窗优先保证指腹净距和 HUD 不与键帽相叠。第二轮小窗在 760×1280 / 440 dpi、1.3 字体、动画 0 下两向实际全档、取消与暂停/播放均通过（57.833 秒），但横屏「已选 1.375×」和 3× 仍被遮住一部分。最后专修这两个冲突，不把两个浮层分开等同于所有数字无遮挡。

### 实屏读数（不是源码 alpha）

| 证据 | 像素与语义 |
|---|---|
| [黑底 2.5×](../ui-ux-audit/implementation/evidence/speed-fan-portrait-1.4.0.png) / [亮红底 2.5×](../ui-ux-audit/implementation/evidence/speed-fan-decimal-landscape-1.4.0.png) | 两向实际 2.5×、常规选中仍是 2×，指尖至数字框均 **62.24 dp**。白字对实际键帽底色 `30,30,30` / `71,30,30` 的对比度为 **16.67:1 / 14.26:1**。每个图内抽取扣掉字形及圆角边线后的 5745 个像素，各自只有上述一种底色，无透入的邻字；末尾乘号区域均有 224 个近白像素。 |
| [未展开的同一暂停帧](../ui-ux-audit/implementation/evidence/speed-fan-raw-landscape-1.4.0.png) / [按住 5×](../ui-ux-audit/implementation/evidence/speed-fan-landscape-1.4.0.png) | 对照重合区域，未选档内离开字/线的红、黑、绿原视频分别为 `(254,0,0)`、`(0,0,0)`、`(1,255,1)`，展开后为 `(97,26,26)`、`(26,26,26)`、`(26,97,26)`；颜色通道差约保留 28%，不是不透明填色。扇面/两提示之外按 4 px 栅格抽查 134234 点，RGB 各通道差均不超过 2。样张中的彩条、烧录时间是视频本身。 |

## 最终布局与交付件

主动试播时暂收起非预设基线的可见徽标；轻点展开、按住未滑都仍可查「已选 1.375×」，不会误勾 13 档中的某一项。常规窗 HUD 仍居中。仅当居中的 HUD 压到实际标签且扇右有足够空间，才把它移到扇右；高度仍由上四分之一与安全边界决定。沿用 Compose / Material 3、播放器独立深色令牌，没加教程或常驻说明。

最终只补验变动的三处，不重跑导入。[定向用例](../../app/src/androidTest/java/com/privateplayer/media/PlayerSpeedPickerProbe.kt)在非预设 1.375× 实际暂停后确认三件事：静止时基线可见，主动选档时第三牌退出；两个数字的字形盒、末尾乘号与 HUD 箭头确实出现在同一张系统合成帧内；13 个选项的真实文字盒与键帽、HUD **分别**无交集。第一次直接取系统截图会先取到上一帧，不能仅凭语义坐标算通过；最终证据保留在原指针仍 DOWN 时、经像素确认的原图，未绘制或修改页面内容。离开后均恢复 **1.375× @ 166 ms**。

| 最终包实屏 | 字号 / 系统动画 | 读数 |
|---|---|---|
| [小竖屏 0.25×](../ui-ux-audit/implementation/evidence/speed-fan-compact-portrait-1.4.0.png) | 760×1280 / 440 dpi，1.3 / 0 | 键帽 `[322,390][529,496]`，指尖到字形 **56.89 dp**；HUD 在屏高 25%，两者垂直相距 11 px。 |
| [小横屏 0.25×](../ui-ux-audit/implementation/evidence/speed-fan-compact-landscape-1.4.0.png) | 1280×760 / 440 dpi，1.3 / 0 | 键帽 `[322,95][529,201]`，净距 **52.91 dp**。HUD 从冲突的左缘 404 px 移到 **740 px**，中心高度 28.03%；3× 和乘号完整。两姿态合为 `OK (1 test)`，11.725 秒。 |
| [常规横屏 2.5× 正对照](../ui-ux-audit/implementation/evidence/speed-fan-control-1.4.0.png) | 2400×1080 / 420 dpi，1.3 / 1 | 净距 **62.35 dp**；HUD 中心高度 25.05%，未为小窗修正而移向右侧。`OK (1 test)`，9.216 秒。 |

[原始设备坐标与实速节录](../ui-ux-audit/implementation/evidence/speed-fan-1.4.0.txt)按应用散列分组。`9f54791c…` 是导入 11 项及首批播放器会话验证的应用，**不是**最终交付文件；`38a0e2e5…` 是两向边界/快速轨迹与键帽材质取证包。两者和最后包都是 1.4.0（10400），没有构建 1.3.1。

最后的生产改动只重跑 `:feature:player:testDebugUnitTest :feature:player:lintDebug :app:assembleDebug :app:assembleDebugAndroidTest`（13 秒，465 项任务）；为合成帧采证只再打测试 APK（2 秒，342 项）。最终受影响 JVM **49 项＝播放器 39＋导入 10，0 失败 / 0 错误 / 0 跳过**；播放器模块 lint 0 issue，根 lint 的 14 模块结果见前文。没有把多轮相同用例累加为项目全量通过。

| 交付文件 | 字节 | SHA-256 |
|---|---:|---|
| `app/build/outputs/apk/debug/app-debug.apk` | 53,174,711 | `7475fe1dab2ae43842435373d9a10f18bbdca7ef287681509bb4ef975b356f7b` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | 2,912,807 | `57035ef821430607729fdf28309efc4f4b7f9ce8f724bc0977a8ee02c849fad2` |

`aapt2` 实读应用 `com.privateplayer.media`、`1.4.0 / 10400`、arm64-v8a；测试包 `com.privateplayer.media.test` 的标准 AGP 清单不设独立版本号，instrumentation 指向上述应用。最后在专用设备逐包读取已安装 `base.apk` 的 SHA-256，与表中两份交付件完全相同。

设备仅用 `emulator-5592`、`-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save`；静音，媒体音量 0。通过限定 serial 的 `adb install -r -t`、`am instrument -w -r -e class …` 执行；首次全屏确认确实点了系统的 `Got it`。结束时停应用，仅关闭该模拟器，核实 serial 消失、两个专属进程退出，删除本次临时盘；启动日志归入 `build/reports/import-recovery-1.4.0/boot/`，构建与原始仪器输出在 `build/reports/speed-fan-1.4.0/`。没有用 `connected*`、下载依赖、更换 libmpv、接触用户媒体、提交或推送。物理拇指包络、实际触觉、OEM 窗口与硬件解码不由本次模拟器结果保证。
