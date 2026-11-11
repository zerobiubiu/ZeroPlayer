# 证据索引

F-37（设置保存模型统一）的 before/after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），分辨率 1080×2400
- 构建：截图取自 `versionName=1.0.1`、`versionCode=10001` 的调试包；本任务随后把版本递增到 1.0.2（设置保存模型为用户可见行为变更），截图所示 UI 与 1.0.2 一致
- before 基线：`docs/ui-ux-audit/assets/settings-播放.png`、`settings-字幕.png`

## 截图

| 文件 | 状态 | 说明 |
|---|---|---|
| `settings-playback-light.png` | after | 设置·播放，浅色，字号 1.0；数值行整行铺满，无“保存”按钮 |
| `settings-playback-dark.png` | after | 设置·播放，深色，字号 1.0 |
| `settings-playback-font130.png` | after | 设置·播放，浅色，字号 1.3；无截断与重叠 |
| `settings-subtitle-light.png` | after | 设置·字幕，浅色，字号 1.3；两个数值行均无“保存”按钮 |

## 手测记录

| 用例 | 步骤 | 观察结果 |
|---|---|---|
| 非法值不落库 | 设置·播放 → 默认倍速清空后输入 `9` → 键盘完成 | 行内显示红色“请输入0.25～4 倍”；返回一级摘要仍为 `1.09×`，未变成 `9×` |
| 合法值即时写入 | 默认倍速清空后输入 `2.5` → 键盘完成 | 无需点任何按钮；返回一级摘要立即变为 `自动续播 · 2.5× · 适应屏幕` |
| 应用重启后保持 | `am force-stop` 后重新启动 → 设置一级 | 摘要仍为 `自动续播 · 2.5× · 适应屏幕`，DataStore 已落盘 |
| 页面无“保存”按钮 | 设置·播放 / 设置·字幕 | 界面树中不存在文本“保存”的节点 |
| 快速连续修改 | 连续改同一数值项 | 由 `SettingsWriteQueue` 串行保序，最后一次修改最后落盘（单测 `SettingsWriteQueueTest` 覆盖） |

## 自动化验证

详见 [变更记录 0011](../../changes/0011-settings-immediate-save.md) 的「执行验证记录」。
