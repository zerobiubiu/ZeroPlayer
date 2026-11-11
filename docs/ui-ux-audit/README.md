# UI/UX 设计审计报告（只审查，未实施修改）

审查时间：2026-11-11。审查对象：ZeroPlayer 1.0.1（`com.privateplayer.media`），commit `d9cf431` 之后的工作区（未提交）。审查方式：源码静态审查 + 模拟器实机操作 + 截图/界面树交叉验证。

**本次没有修改任何业务源码、样式、组件、设计 Token、翻译或配置；没有提交或推送 Git。** 本目录（`docs/ui-ux-audit/`）是唯一新增内容。

## 文件索引

| 文件 | 内容 |
|---|---|
| [summary.md](summary.md) | 总体评价、八个维度结论、问题统计与 P1 清单 |
| [findings.md](findings.md) | 41 项问题：编号、页面、组件、维度、证据、影响、建议、优先级、状态 |
| [pages.md](pages.md) | 11 个页面、8 类弹层、3 类状态逐项审查 |
| [design-system.md](design-system.md) | 字体/颜色/间距/圆角/组件/图标/响应式/文案资源 |
| [recommendations.md](recommendations.md) | 分阶段优化建议（R-01～R-16），未实施 |
| `assets/` | 36 张实机截图，文件名与正文引用一致 |

## 审查基线（重要）

任务指定的《前端内容与排版设计约束》**未找到**：仓库、`docs/`、`C:\Users\root\.agents\skills` 与 managed-skills 均无 `Content-First` / `Functional Minimalism` / 信息密度等条目。

本次采用替代基线：

- `ux-writing` 技能：Purposeful / Concise / Conversational / Clear，文案模式与无障碍写作。
- `web-design-guidelines`（Vercel Web Interface Guidelines 固定快照）：布局、排版、颜色、动效、可访问性。
- Material 3 与 WCAG 2.2 AA、Android 48dp 触控目标惯例。

**结论偏差**：若原文档对信息密度/文案必要性有更严格或不同的定义，部分 P2/P3 判断可能变化；拿到文档后可据此复评，无需重新跑一遍实机。

## 覆盖统计

- 页面 **11**：首页、视频详情、播放器、设置一级、设置二级 7 个（播放/字幕/手势/显示/导入/存储/关于）。
- 弹层/对话框 **8 类**：文件行菜单、新建目录、重命名、移动、删除、Wi-Fi 导入、文件信息、播放器 5 个面板（更多/字幕/倍速/播放列表/画面比例）。
- 状态：空目录、错误、加载（代码+瞬态）、锁定、深色、系统字号 1.3 倍。
- 组件：主题 Token 2 个文件、通用组件 5 个文件（Bars/Dialogs/States/MediaComponents/MediaCardData）、首页行与顶栏、播放器浮层、设置输入、详情行、Wi-Fi 面板、传输对话框。
- 截图 **36** 张；代码统计：中文硬编码 767 处、`stringResource` 0 次、`strings.xml` 仅 1 条、原始 `dp` 123 处 vs `Spacing` 100 处。

## 问题分布

| 优先级 | 数量 | 说明 |
|---|---|---|
| P0 | 0 | 未发现阻断核心任务或造成数据丢失的问题 |
| P1 | 10 | 保存模型、面板语义/状态栏、入口错位、详情缺操作、文案体系、安全提示、横屏待验证 |
| P2 | 24 | 一致性、层级、密度、组件与错误恢复 |
| P3 | 7 | 文案细节、死组件、图标/圆角 |

- 已验证 40 项；待验证 1 项（F-27 横屏撕裂，模拟器证据）。
- 按维度：UX Writing 14、Visual Hierarchy 3、Information Architecture 5、Layout 5、Information Density 2、Component Design 6、Accessibility 1、Interaction 5。

## 未覆盖与原因

| 项目 | 原因 |
|---|---|
| 导入任务对话框（有任务） | 需 SAF 选择器产生真实 URI 并等待复制 |
| 导出进度对话框 | 需跨应用目录选择与大文件导出 |
| 系统媒体通知/锁屏 | 本轮聚焦应用内 UI，已有独立验证记录 |
| 平板/折叠屏、横屏设置页 | 模拟器固定 1080×2400 / 420dpi，未切换尺寸 |
| 系统字号 2.0、显示大小最大 | 仅验证 1.3 倍 |
| 硬件键盘/焦点顺序 | 模拟器无物理键盘 |
| 横屏最终结论 | 截图复现撕裂，需真机排除 SwiftShader 因素 |

## 复现与证据

- 截图均在 Android 17（API 37）x86_64 模拟器、ARM64 转译、1080×2400/420dpi 下截取。
- 深色：`cmd uimode night yes`；字号：`settings put system font_scale 1.3`；错误态：对目录 `chmod 000` 后进入。
- 证据路径：正文中的 `assets/<文件名>.png` 与代码 `file:line`。
- 模拟器为 `-read-only` 启动，验收后已关闭；未改动 AVD 基础状态。

## 下一步

按 `recommendations.md` 的阶段顺序执行；每完成一项回填 `findings.md` 的状态列，并在 `docs/changes/` 记录变更。F-27 应先真机定性，再决定是否修复。
