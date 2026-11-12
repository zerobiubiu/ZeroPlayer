# 阶段 3 证据索引

空状态/不支持提示/标点/行菜单噪声/图标圆角规则（F-02 / F-03 / F-05 / F-11 / F-28 / F-35）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/home-empty-dir.png`、`home-unsupported-snackbar.png`、`home-light.png`、`detail-light.png`。
- 变更记录：[changes/0024-copy-details-icon-radius.md](../../../changes/0024-copy-details-icon-radius.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `home-empty-3.png` | after·浅色·竖屏·1.0 | 空状态单一“导入文件”CTA |
| `home-unsupported-3.png` | after·浅色·竖屏·1.0 | 不支持提示含文件名 |
| `home-rows-3.png` | after·浅色·竖屏·1.0 | 行尾三点已隐藏（噪声降低） |
| `detail-subtitles-3.png` | after·浅色·竖屏·1.0 | 外挂字幕全角括号、格式顿号 |

## 验证结论

- **F-02**：空状态单一 CTA（截图）。
- **F-03**：不支持提示含文件名（单测 + 截图）。
- **F-05**：校验文案无“空字符”（1E 核验）。
- **F-11**：顿号/全角括号/单位空格（截图 + 仪器化断言）。
- **F-28**：行尾三点隐藏，菜单经读屏自定义操作（仪器化 + 截图）。
- **F-35**：圆角单一刻度、图标规则（设计系统文档 + 编译）。

## 自动化

- 完整 `connectedDebugAndroidTest`：见 execution-log 二十四。

## 限制

- 文件类型图标区分（视频/字幕/文本）未做（审计建议项，非必须）。
