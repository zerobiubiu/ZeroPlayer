# 阶段 2G 证据索引

文件树多选与批量删除/移动（F-41）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/home-light.png`（无选择态）。
- 变更记录：[changes/0023-multi-select-batch.md](../../../changes/0023-multi-select-batch.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `home-selection-2g.png` | after·浅色·竖屏·1.0 | 多选态：SelectionTopBar 数量+全选/清除/移动/删除，选中行高亮 |
| `home-batch-delete-2g.png` | after·浅色·竖屏·1.0 | 批量删除二次确认（数量+不可撤销） |
| `home-batch-result-2g.png` | after·浅色·竖屏·1.0 | 部分失败结果 Snackbar（成功/失败数量） |

## 验证结论

- **F-41**：长按多选、全选/清除、批量删除/移动、部分失败保留失败项（批量单测 4 例 + 仪器化回归 + 截图）。
- 播放安全：批量删除/移动先停播放（PlaybackStopper），生命周期契约不变。

## 自动化

- `HomeViewModelTest` +4（批量）。
- 完整 `connectedDebugAndroidTest`：见 execution-log 二十三。

## 限制

- 大目录全选性能未专项压测；批量导出/分享不在本版（D5）。
