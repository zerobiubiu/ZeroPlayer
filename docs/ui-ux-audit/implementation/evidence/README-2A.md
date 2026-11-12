# 阶段 2A 证据索引

设置信息架构重组与数值表单紧凑化（F-18 / F-26 / F-29）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/settings-hub-light.png`、`settings-播放.png`、`settings-显示.png`。
- 变更记录：[changes/0017-settings-ia-numeric-forms.md](../../../changes/0017-settings-ia-numeric-forms.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `settings-hub-2a.png` | after·浅色·竖屏·1.0 | 一级 5 入口（播放与字幕/手势与反馈/外观/数据与存储/关于） |
| `settings-playback-2a.png` | after·浅色·竖屏·1.0 | 播放与字幕页：紧凑数值行（默认倍速/双击跳转/长按倍速/字幕字号/延迟步长） |
| `settings-numeric-dialog-2a.png` | after·浅色·竖屏·1.0 | 数值编辑对话框：预填全选 + 聚焦显示范围提示 |
| `settings-numeric-error-2a.png` | after·浅色·竖屏·1.0 | 非法值：错误提示 + 不写入 |
| `settings-data-2a.png` | after·浅色·竖屏·1.0 | 数据与存储页：导入选项 + 用量/清理 |
| `settings-hub-dark-2a.png` | after·深色·竖屏·1.0 | 深色一级 |
| `settings-playback-font130-2a.png` | after·浅色·竖屏·1.3 | 1.3 倍字号紧凑行不截断 |

## 验证结论

- **F-18**：一级 5 入口、无单控件页面；旧路由重定向（路由单测 3 例 + 导航仪器化）。
- **F-26**：数值行紧凑（≥56dp），点击开对话框编辑；单屏可见 ≥6 项（截图 + 仪器化）。
- **F-29**：紧凑行默认无范围提示；对话框聚焦/出错才显示（仪器化断言 + 截图）。

## 自动化

- `SettingsSectionRoutingTest`：3 例。
- `DetailSettingsInstrumentedTest`：导航 + 数值对话框交互。
- 完整 `connectedDebugAndroidTest`：见 execution-log 十七。

## 限制

- 2.0 倍字号与横屏设置页未取证（阶段 4 全矩阵补齐）。
