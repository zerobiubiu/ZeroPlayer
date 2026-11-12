# 横屏渲染验证与处置（阶段 1F）

状态：已执行。影响范围：仅验证与文档；未修改任何业务源码、样式、Token、翻译或配置。

## 背景

UI/UX 审计问题 F-27（P1，待验证）：1.0.1 基线在模拟器横屏播放数秒后画面出现错位条纹（缓冲宽度/步长不匹配的典型表现），证据 `assets/player-landscape.png`、`player-landscape-2.png`。审计建议先定性再决定是否修复，未定性前不重构渲染链路。

本阶段按任务指令仅在模拟器（API 37，x86_64 + ARM64 转译，headless）复验，不等待真机；若为模拟器图形限制则记录证据关闭，不盲改 libmpv/Surface。

## 验证方法

- 设备：`Medium_Phone_API_37.0`（Android 17 / API 37），1080×2400 / 420dpi，headless（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- 素材：真实 libmpv 播放 `playback-regression.mkv`（SMPTE 彩条 + 烧录时间码/帧计数，16 秒），经 SAF 导入私有树后点击播放。客户机媒体流全程静音（stream 3 索引 0），取证后 `am force-stop`。
- 操作：竖屏播放 → 顶栏“旋转屏幕”一次切横屏（`rotation=1`）→ 播放中连续截帧（约 4s / 14.9s）→ 横屏拖动进度条跳转 → 再点旋转回竖屏（`rotation=0`）→ 返回键退出。
- 交叉验证：`uiautomator dump` 的 `rotation` 属性、`dumpsys window` 焦点、`dumpsys activity services` 服务残留、截图逐张读图。

## 观察结果

| 检查项 | 结果 |
|---|---|
| 横屏渲染（播放中 4s / 14.9s 两帧） | 彩条边缘垂直整齐、烧录时间码/帧计数清晰可读，**无错位条纹/撕裂** |
| 横屏控件 | 顶栏（返回/比例/旋转/字幕/更多）与底栏（时间/进度/倍速/传输/队列）布局正确，安全区避让正常 |
| 横屏进度 | 拖动进度条跳转生效（00:09 预览浮层 + 时间行同步），画面无撕裂 |
| 系统栏 | 横屏播放期间状态栏/导航栏隐藏（截图无系统图标），沉浸式保持 |
| 方向切换 | 旋转一次横屏、再点回竖屏，`rotation` 1↔0 正确，切换后画面正常 |
| 返回行为 | 返回键退出播放页后 `dumpsys activity services` 无服务残留，MediaSession 会话清空（生命周期契约不变） |

证据：`evidence/player-portrait-1f.png`、`player-landscape-1f.png`、`player-landscape-2-1f.png`、`player-landscape-seek-1f.png`，索引见 `evidence/README-1F.md`。

## 结论

- **F-27 在当前构建（1.0.6）与当前模拟器上不复现**：横屏播放、跳转、方向切换、系统栏与返回行为均正常，渲染无撕裂。
- 1.0.1 基线的撕裂证据与当前干净帧并存，说明该现象与当时的构建/模拟器图形状态相关；1B–1E 未改动 Surface/libmpv 渲染链路（`resizeSurface` 仍经 `android-surface-size` 正常下发），当前无法归因为应用层缺陷。
- 按任务指令以模拟器证据关闭 F-27，标注限制：x86_64 + ARM64 转译 + SwiftShader 软件渲染，不能排除实体 ARM 设备/硬解路径的差异；真机横屏 ≥5 分钟回归仍列为残余风险（阶段 4 矩阵）。
- **未修改任何渲染代码**；不盲改 libmpv/Surface。

## 版本

纯验证与文档，无用户可见行为变更，不升版本（保持 1.0.6）。

## 已知限制

- 仅模拟器证据；实体 ARM 真机、硬解、不同 API 的横屏表现未验证。
- 播放素材为 16 秒测试片，未做 ≥5 分钟长时横屏压测（阶段 4 补齐）。
