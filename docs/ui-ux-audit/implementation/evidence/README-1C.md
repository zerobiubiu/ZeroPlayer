# 阶段 1C 证据索引

播放器入口归位与真实播放队列（F-19 / F-20）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window`）运行，`adb exec-out screencap` 取证
- before 基线：`docs/ui-ux-audit/assets/player-*.png`
- 变更记录：[changes/0013-player-entries-queue.md](../../../changes/0013-player-entries-queue.md)

## after 截图

| 文件 | 状态 | 说明 |
|---|---|---|
| `player-topbar-light.png` | after·浅色·竖屏·1.0 | 顶栏：返回、片名、画面比例、旋转屏幕、字幕、更多（比例/旋转分离） |
| `player-aspect-sheet-1c.png` | after·浅色·竖屏·1.0 | 点“画面比例”打开比例面板，仅当前项勾选+高亮 |
| `player-more-sheet-1c.png` | after·浅色·竖屏·1.0 | 更多：锁定/音轨/比例导航，无旋转行、无引擎行 |
| `player-queue-sheet-1c.png` | after·浅色·竖屏·1.0 | 播放队列：真实条目、当前项高亮带勾、非当前项无勾、上一集/下一集+模式 |
| `player-queue-scroll-1c.png` | after·浅色·竖屏·1.0 | 长队列滚动（限高懒加载） |
| `player-landscape-1c.png` | after·浅色·横屏·1.0 | 点“旋转屏幕”一次切到横屏 |
| `player-queue-landscape-1c.png` | after·浅色·横屏·1.0 | 横屏下播放队列面板 |
| `player-topbar-dark.png` | after·深色·竖屏·1.0 | 深色顶栏入口 |
| `player-queue-sheet-dark.png` | after·深色·竖屏·1.0 | 深色播放队列，当前项高亮 |
| `player-topbar-font130.png` | after·浅色·竖屏·1.3 | 1.3 倍字号顶栏无截断 |
| `player-queue-sheet-font130.png` | after·浅色·竖屏·1.3 | 1.3 倍字号播放队列 |

## 验证结论

- **F-19**：比例图标打开比例面板；旋转一次点击切换横竖屏；五个入口描述准确；更多面板无旋转/引擎行（仪器化断言 + 截图）。
- **F-20**：队列渲染真实条目、仅当前项高亮、点击跳转真实切源、顺序不变、快速连点只执行一次、空队列空状态、长队列滚动定位（仪器化断言 + 截图）。

## 自动化

- `feature/player:testDebugUnitTest`：QueueSelectionTest 10 例。
- `PlayerEntriesInstrumentedTest`：4 例。
- `PlayerQueueInstrumentedTest`：4 例（含真实 libmpv 沙箱）。
- 完整 `connectedDebugAndroidTest`：见 execution-log 13.3。
