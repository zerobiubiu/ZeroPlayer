# 阶段 4 证据索引（最终回归）

最终 APK（1.0.14/10014）的视觉矩阵截图与回归结论。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。
- 交付报告：[final-regression-report.md](../final-regression-report.md)

## 最终视觉矩阵（逐张读图核验）

| 文件 | 状态 | 说明 |
|---|---|---|
| `final-home-light-4.png` | 浅色·竖屏·1.0 | 空状态单一“导入文件”CTA，文案无“加号”歧义 |
| `final-home-dark-4.png` | 深色·竖屏·1.0 | 深色首页 |
| `final-settings-light-4.png` | 浅色·竖屏·1.0 | 设置一级 5 任务分组 |
| `final-settings-playback-4.png` | 浅色·竖屏·1.0 | 播放与字幕：紧凑数值行、无常住范围提示、四组分段 |
| `final-settings-playback-font130-4.png` | 浅色·竖屏·1.3 | 1.3 倍字号紧凑行不截断 |
| `final-settings-dark-4.png` | 深色·竖屏·1.0 | 深色设置 |

## 回归结论

- 最终 APK 通过：编译、Lint 0 error、136 JVM 单测（2 skipped）、63/63 仪器化、文案门禁。
- 41 项问题全部处置；保留项与核心契约未破坏。
- 各阶段 before/after 截图见 README-1B～README-3。

## 限制

- 2.0 字号、横屏设置页、平板/折叠屏、真机硬解/长时压测未取证（见交付报告残余风险）。
