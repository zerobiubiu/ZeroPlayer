# 阶段 1B 证据索引

播放器面板语义与沉浸式（F-16 / F-38 / F-17）的 before/after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi
- before 基线：`docs/ui-ux-audit/assets/player-*.png`
- 变更记录：[changes/0012-player-panels.md](../../../changes/0012-player-panels.md)

## after 截图

| 文件 | 状态 | 说明 |
|---|---|---|
| `player-speed-sheet-after.png` | after·浅色·竖屏·1.0 | 倍速面板：仅 1.0× 勾选+高亮；无状态栏 |
| `player-aspect-sheet-after.png` | after·浅色·竖屏·1.0 | 画面比例：仅“适应屏幕”勾选+高亮；无状态栏 |
| `player-audio-sheet-after.png` | after·浅色·竖屏·1.0 | 音轨：仅 eng 勾选+高亮，jpn 无勾；无状态栏 |
| `player-subtitle-sheet-after.png` | after·浅色·竖屏·1.0 | 字幕：仅“关闭字幕”勾选+高亮；无状态栏 |
| `player-queue-sheet-after.png` | after·浅色·竖屏·1.0 | 播放列表：上/下一集为动作无勾，模式仅当前项勾选；无状态栏 |
| `player-more-sheet-after.png` | after·浅色·竖屏·1.0 | 更多：无“播放引擎”行；音轨/比例带导航箭头；无状态栏 |
| `player-speed-sheet-dark.png` | after·深色·竖屏·1.0 | 深色下仅当前项勾选+高亮；无状态栏 |
| `player-speed-sheet-landscape.png` | after·深色·横屏·1.0 | 横屏面板仅当前项勾选+高亮；无状态栏 |
| `player-speed-sheet-font130.png` | after·浅色·竖屏·1.3 | 1.3 倍字号无截断；仅当前项勾选+高亮 |

## 验证结论

- **F-16**：五个面板均只有当前单选项显示勾选并高亮；动作/导航/状态项不显示勾选；读屏带 `Role.RadioButton` 语义（仪器化断言）。
- **F-38**：打开/关闭/重开任意面板，系统状态栏窗口 `isVisible=false`（dumpsys 断言 + 截图无电池/时间图标）；退出播放页恢复。
- **F-17**：更多面板不再包含“播放引擎”禁用行；引擎信息保留在 设置·关于。

## 自动化

- `:core:design:testDebugUnitTest`：8 用例（选中语义）。
- `PlayerSheetSemanticsInstrumentedTest`：6 用例。
- `PlayerSheetImmersiveInstrumentedTest`：4 用例（dumpsys 状态栏断言）。
- 完整 `connectedDebugAndroidTest`：见 execution-log 12.3。
