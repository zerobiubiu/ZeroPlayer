# 阶段 2C 证据索引

详情信息分组、轨道来源、码率与 chips 去重（F-25 / F-06 / F-07 / F-15）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/detail-light.png`、`detail-scrolled.png`、`detail-file-info.png`。
- 变更记录：[changes/0019-detail-info-grouping-bitrate.md](../../../changes/0019-detail-info-grouping-bitrate.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `detail-top-2c.png` | after·浅色·竖屏·1.0 | 首屏：标题+进度+播放，无重复 chips |
| `detail-groups-2c.png` | after·浅色·竖屏·1.0 | 信息表四组（文件/视频/音频/播放记录），码率 Mbps |
| `detail-file-info-2c.png` | after·浅色·竖屏·1.0 | 文件信息浮层：探测轨道数+来源 |
| `detail-groups-dark-2c.png` | after·深色·竖屏·1.0 | 深色分组 |
| `detail-groups-font130-2c.png` | after·浅色·竖屏·1.3 | 1.3 倍字号分组不截断 |

## 验证结论

- **F-25**：四组分组清晰（截图 + 仪器化回归）。
- **F-06**：主表无探测行，文件信息注明来源（截图）。
- **F-07**：码率 Mbps（formatBitrate 单测 + 截图）。
- **F-15**：首屏无重复 chips（截图）。

## 自动化

- `FormattersTest` +1（formatBitrate）。
- 完整 `connectedDebugAndroidTest`：见 execution-log 十九。

## 限制

- 2.0 倍字号与横屏详情未取证（阶段 4 全矩阵补齐）。
