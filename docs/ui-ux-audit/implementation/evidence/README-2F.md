# 阶段 2F 证据索引

Wi-Fi 权限恢复路径与统一复制反馈（F-13 / F-40）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/wifi-sheet-light.png`。
- 变更记录：[changes/0022-wifi-permission-copy-feedback.md](../../../changes/0022-wifi-permission-copy-feedback.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `wifi-copied-2f.png` | after·浅色·竖屏·1.0 | 复制后按钮“已复制” |
| `wifi-permission-2f.png` | after·浅色·竖屏·1.0 | 权限拒绝：文案+去设置按钮 |

## 验证结论

- **F-13**：拒绝后“去设置”跳转应用详情页，返回可重试（手测 + 截图）。
- **F-40**：复制后“已复制”2 秒恢复，全版本统一（手测 + 截图）。

## 自动化

- 完整 `connectedDebugAndroidTest`：见 execution-log 二十二。

## 限制

- 权限拒绝需 API 37 局域网权限构造；低版本权限模型差异需真机复核。
