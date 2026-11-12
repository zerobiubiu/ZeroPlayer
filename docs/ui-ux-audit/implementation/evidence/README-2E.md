# 阶段 2E 证据索引

组件收敛（F-33 / F-34 / F-36 / F-31，F-32 核验）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/settings-播放.png`、`player-portrait.png`、`player-locked.png`。
- 变更记录：[changes/0021-component-convergence.md](../../../changes/0021-component-convergence.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `settings-switch-2e.png` | after·浅色·竖屏·1.0 | 开关行单目标（视觉不变） |
| `player-controls-2e.png` | after·深色·竖屏·1.0 | 播放器控件：传输键/速度胶囊 ≥48dp |
| `player-locked-2e.png` | after·深色·竖屏·1.0 | 锁定提示热区 ≥48dp |

## 验证结论

- **F-33**：SwitchRow 单目标（仪器化语义回归 + 截图）。
- **F-34**：PlayerTokens 单一来源（编译 + 截图，深色语境保留）。
- **F-36**：触控 ≥48dp（截图 + 仪器化 bounds）。
- **F-31**：死组件删除，全量编译通过；SelectionTopBar 保留。
- **F-32**：DeleteDialog 单实现核验。

## 自动化

- 完整 `connectedDebugAndroidTest`：见 execution-log 二十一。

## 限制

- TalkBack 单目标朗读顺序需真机手测；播放器 Token 仅统一来源与关键尺寸。
