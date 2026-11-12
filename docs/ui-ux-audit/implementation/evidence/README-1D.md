# 阶段 1D 证据索引

菜单分组与详情页文件操作（F-22 / F-21）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）运行，`adb exec-out screencap` 取证
- before 基线：`docs/ui-ux-audit/assets/home-row-menu.png`、`detail-light.png`
- 变更记录：[changes/0014-ui-menu-and-detail-actions.md](../../../changes/0014-ui-menu-and-detail-actions.md)
- 取证约束：静态取证，全程不播放视频；播放安全（删除/移动播放中媒体先停服、队列不残留）由仪器化测试证明，不另取播放态截图。

## 页面与视觉矩阵（已取证，14 张）

| 文件 | 状态 | 说明 |
|---|---|---|
| `home-light-1d.png` | after·浅色·竖屏·1.0 | 首页文件列表 |
| `home-row-menu-1d.png` | after·浅色·竖屏·1.0 | 长按分组菜单：信息/传输/管理/危险，删除独立置底 |
| `detail-light-1d.png` | after·浅色·竖屏·1.0 | 详情页顶栏：文件信息 + 更多 |
| `detail-more-menu-1d.png` | after·浅色·竖屏·1.0 | 详情更多菜单：导出/分享/重命名/移动/删除 |
| `detail-rename-dialog-1d.png` | after·浅色·竖屏·1.0 | 重命名对话框 |
| `detail-rename-error-1d.png` | after·浅色·竖屏·1.0 | 非法字符行内提示 + 确认禁用 |
| `detail-move-picker-1d.png` | after·浅色·竖屏·1.0 | 移动文件夹选择器 |
| `detail-delete-confirm-1d.png` | after·浅色·竖屏·1.0 | 删除确认（点名对象、不可撤销） |
| `detail-saf-export-1d.png` | after·浅色·竖屏·1.0 | 导出 SAF 选择器（ACTION_CREATE_DOCUMENT，DocumentsUI 本地 Download） |
| `home-dark-1d.png` / `home-row-menu-dark-1d.png` | after·深色·竖屏·1.0 | 深色首页与分组菜单 |
| `detail-dark-1d.png` | after·深色·竖屏·1.0 | 深色详情页 |
| `home-row-menu-font130-1d.png` | after·浅色·竖屏·1.3 | 1.3 倍字号首页分组菜单 |
| `detail-more-menu-font130-1d.png` | after·浅色·竖屏·1.3 | 1.3 倍字号详情更多菜单 |

## 验证结论

- **F-22**：菜单按信息/传输/管理/危险分组，删除独立置底 error 色并二次确认；长按与三点同源（仪器化断言 + 截图）。
- **F-21**：详情更多菜单五项操作可用；重命名/移动/删除与首页同仓库同结果；导出/分享复用根 SAF 回调；删除/移动正在播放媒体先停服、队列不残留（仪器化断言）。

## 自动化

- `core/design:testDebugUnitTest`：FileActionMenuGroupingTest 11 例。
- `FileMenuAndDetailActionsInstrumentedTest`：7 例。
- `FileTreeConsistencyInstrumentedTest`：23 例（原 20 + 3）。
- 完整 `connectedDebugAndroidTest`：见 execution-log 十四。

## 限制

- 模拟器 x86_64 + ARM64 转译；SAF 云端目标、实体蓝牙、厂商系统栏差异需真机。
- 导出成功取证使用 DocumentsUI 本地 Download 目录；云盘目标未验证。
- 按收尾时间压缩，以下变体未取证：深色详情更多菜单、2.0 倍字号全部变体、横屏全部变体、1.3 倍字号首页/详情整页（仅取菜单）。
- 播放态与操作结果态截图（播放中删除/移动、重命名/移动/导出成功提示、取消无副作用、服务停止 dumpsys）未取证：静态取证不播放视频，相应行为由仪器化测试覆盖（60/60）。