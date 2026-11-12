# 阶段 2D 证据索引

关于页技术信息归位、导入通知列表化与错误态恢复（F-08 / F-12 / F-39）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。
- before 基线：`docs/ui-ux-audit/assets/settings-关于.png`、`home-error-state.png`。
- 变更记录：[changes/0020-about-import-notices-error-state.md](../../../changes/0020-about-import-notices-error-state.md)
- 取证约束：静态取证，不播放视频；客户机媒体流静音（stream 3 索引 0）。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `settings-about-2d.png` | after·浅色·竖屏·1.0 | 关于页：版本+引擎名+开源许可按钮，无 .so/开发状态 |
| `settings-licenses-2d.png` | after·浅色·竖屏·1.0 | 开源许可对话框（随包许可文本） |
| `home-error-2d.png` | after·浅色·竖屏·1.0 | 错误态：标题+事实文案+返回上级+重试（chmod 000 构造） |
| `import-tasks-2d.png` | after·浅色·竖屏·1.0 | 导入通知逐条 + 失败可行动文案 + 详情折叠 |

## 验证结论

- **F-08**：关于页无 .so/assets/开发状态；许可可访问（截图 + 仪器化回归）。
- **F-12**：通知逐条；失败可行动文案 + 原始错误折叠（classifyImportError 单测 6 例 + 截图）。
- **F-39**：错误态标题+返回上级；chmod 000 不误报已删除（截图 + 仪器化）。

## 自动化

- `ImportErrorMappingTest` 6 例；`CopyResourceGateTest` 白名单登记。
- 完整 `connectedDebugAndroidTest`：见 execution-log 二十。

## 限制

- 导入失败截图需构造真实失败任务（来源失效/空间不足），静态取证以错误态与关于页为主；失败映射由单测覆盖。
