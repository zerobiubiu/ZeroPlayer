# 阶段 1F 证据索引

横屏渲染验证（F-27）的实机截图与结论。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- 素材：`playback-regression.mkv`（SMPTE 彩条 + 烧录时间码/帧计数，16 秒），真实 libmpv 播放；客户机媒体流静音（stream 3 索引 0），取证后 `am force-stop`。
- before 基线：`docs/ui-ux-audit/assets/player-landscape.png`、`player-landscape-2.png`（1.0.1 撕裂条纹）。
- 变更记录：[changes/0016-landscape-verification.md](../../../changes/0016-landscape-verification.md)

## 截图（逐张读图核验）

| 文件 | 状态 | 说明 |
|---|---|---|
| `player-portrait-1f.png` | 竖屏·播放中 | 彩条渲染正常，控件齐全 |
| `player-landscape-1f.png` | 横屏·播放中约 4s | 彩条边缘整齐、时间码清晰，无撕裂；系统栏隐藏 |
| `player-landscape-2-1f.png` | 横屏·播放中约 14.9s | 控件自动隐藏后画面仍干净，无条纹 |
| `player-landscape-seek-1f.png` | 横屏·进度跳转 00:09 | 预览浮层 + 时间行同步，画面无撕裂 |

## 结论

- F-27 在当前构建（1.0.6）与当前模拟器上**不复现**；横屏渲染/控件/进度/系统栏/方向切换/返回停服均正常。
- 以模拟器证据关闭 F-27，标注限制：转译 + SwiftShader 软件渲染，实体 ARM/硬解差异未排除；真机长时横屏回归列为残余风险（阶段 4）。
- 未修改任何渲染代码。

## 限制

- 仅模拟器；16 秒测试片，未做 ≥5 分钟长时横屏压测。
