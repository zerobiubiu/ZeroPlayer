# 阶段 2B 证据索引

首页头部去重与统一顶栏、目录行与移动路径（F-01 / F-24 / F-30 / F-23 / F-04）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/home-light.png`、`home-empty-dir.png`、`home-move-dialog.png`。
- 变更记录：[changes/0018-home-header-rows-move-path.md](../../../changes/0018-home-header-rows-move-path.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `home-root-2b.png` | after·浅色·竖屏·1.0 | 根目录：标题“文件”，无重复面包屑 |
| `home-subdir-2b.png` | after·浅色·竖屏·1.0 | 子目录：标题=文件夹名，面包屑只含祖先 |
| `home-rows-2b.png` | after·浅色·竖屏·1.0 | 目录行单行、文件行带大小 |
| `home-move-dialog-2b.png` | after·浅色·竖屏·1.0 | 移动对话框：根目录/真实路径 |
| `home-root-dark-2b.png` | after·深色·竖屏·1.0 | 深色首页头部 |
| `home-subdir-font130-2b.png` | after·浅色·竖屏·1.3 | 1.3 倍字号头部不截断 |

## 验证结论

- **F-01**：标题=当前文件夹名，面包屑只含祖先，无“文件/文件”重复（HomeHeaderTest 4 例 + 截图）。
- **F-24**：面包屑并入顶栏，祖先热区 ≥48dp，顶部开销降低（截图 + 仪器化导航回归）。
- **F-30**：首页用 AppTopBar，全应用一套顶栏（截图对比 + 回归）。
- **F-23**：目录行单行（截图）。
- **F-04**：移动对话框根目录/真实路径（截图）。

## 自动化

- `HomeHeaderTest`：4 例。
- 完整 `connectedDebugAndroidTest`：见 execution-log 十八。

## 限制

- 2.0 倍字号与横屏首页未取证（阶段 4 全矩阵补齐）。
