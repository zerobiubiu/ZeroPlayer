# 阶段 1E 证据索引

术语统一、文案资源层与 Wi-Fi 风险提示（F-10 / F-14 / F-09）的 after 截图与验证记录。

- 设备：模拟器 `Medium_Phone_API_37.0`（Android 17 / API 37，x86_64 + ARM64 转译），1080×2400 / 420dpi，**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）运行，`adb exec-out screencap` 取证
- before 基线：`docs/ui-ux-audit/assets/wifi-sheet-light.png`、`settings-导入.png`、`home-light.png`、`home-empty-dir.png`
- 变更记录：[changes/0015-copy-layer-terminology-wifi-risk.md](../../../changes/0015-copy-layer-terminology-wifi-risk.md)
- 取证约束：静态取证，全程不播放视频；客户机媒体流静音（stream 3 索引 0）。
- 取证方式：`uiautomator dump` 取真实坐标后整数点按；10 张截图全部逐张读图核验（2026-11-13）。风险块在浅色/深色/1.3 倍字号下均完整可读、位于启动按钮上方；设置·导入与 Wi-Fi 面板开关名逐字一致；首页空态/对话框均为“文件夹”。

## 页面与视觉矩阵

| 文件 | 状态 | 说明 |
|---|---|---|
| `home-light-1e.png` | after·浅色·竖屏·1.0 | 首页：标题/面包屑/行文案术语 |
| `home-import-menu-1e.png` | after·浅色·竖屏·1.0 | 导入菜单：从本机导入 / 通过 Wi-Fi 导入 |
| `wifi-sheet-light-1e.png` | after·浅色·竖屏·1.0 | Wi-Fi 面板：独立风险警示块 + 启动按钮 + 统一开关名 |
| `wifi-sheet-dark-1e.png` | after·深色·竖屏·1.0 | 深色下风险块可读性 |
| `home-new-folder-dialog-1e.png` | after·浅色·竖屏·1.0 | 新建文件夹对话框（术语） |
| `settings-hub-light-1e.png` | after·浅色·竖屏·1.0 | 设置一级（资源化文案） |
| `settings-import-light-1e.png` | after·浅色·竖屏·1.0 | 设置·导入：与 Wi-Fi 面板同名开关 |
| `settings-hub-dark-1e.png` | after·深色·竖屏·1.0 | 深色设置一级 |
| `home-font130-1e.png` | after·浅色·竖屏·1.3 | 1.3 倍字号首页 |
| `wifi-sheet-font130-1e.png` | after·浅色·竖屏·1.3 | 1.3 倍字号 Wi-Fi 面板（风险块不截断） |

## 验证结论

- **F-09**：风险块独立、图标+文字、紧邻启动按钮、重开仍在（仪器化断言 + 截图）；服务未加鉴权。
- **F-10**：容器称“文件夹”；两个导入开关在设置页与 Wi-Fi 面板同名同源（仪器化断言 + 门禁 + 截图）。
- **F-14**：UI 模块用户可见文案资源化；`CopyResourceGateTest` 3 例通过；`stringResource`/`Context.getString` 全覆盖目标页面。

## 自动化

- `CopyResourceGateTest`：3 例（硬编码白名单、术语黑名单、同源断言）。
- `HomeViewModelTest`：8 例（新消息/错误资源契约 + 2 新增）。
- `WifiRiskAndImportTermsInstrumentedTest`：3 例。
- 完整 `connectedDebugAndroidTest`：见 execution-log 十五。

## 限制

- 数据层错误原因文案未资源化（2D 处理）；TalkBack 朗读顺序需真机手测。
- 2.0 倍字号与横屏 Wi-Fi 面板未取证（阶段 4 全矩阵补齐）。
