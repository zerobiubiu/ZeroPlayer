# 设置信息架构重组与数值表单紧凑化（阶段 2A）

状态：已执行。影响范围：`feature/settings` 一级分组与二级页面、数值设置行与编辑对话框；相关仪器化/单测；版本号。

## 背景

UI/UX 审计阶段 2A 修复三个问题：

- **F-18（P2）**：设置一级 7 个入口按页面切分，“显示”只有一个主题选项、“存储”是信息+清理，与多选项配置组同级；点击成本与内容量不匹配。
- **F-26（P2）**：数值设置行（OutlinedTextField + 行尾按钮）高约 140dp，与开关行差一倍，三个数值项占满整屏。
- **F-29（P2）**：“允许范围：…”辅助文案常驻显示，未编辑时也出现，真正的错误提示被淹没。

前置成果保持不变：F-37 即时保存 + SettingsWriteQueue、F-10/F-14 术语与文案资源层。

## F-18 一级按任务分组

一级入口从 7 个收敛为 **5 个**，无单控件页面：

| 入口 | 内容 | 旧入口归并 |
|---|---|---|
| 播放与字幕 | 播放行为/参数 + 字幕样式/同步 | playback + subtitle |
| 手势与反馈 | 手势开关 + 震动反馈 | gesture |
| 外观 | 主题模式 | display |
| 数据与存储 | 导入选项 + 用量统计/缓存清理 | import + storage |
| 关于 | 版本/引擎/许可 | about |

- `SettingsSection` 枚举改为 5 项；`fromId` 保留旧路由兼容：`display→appearance`、`import/storage→data`、`subtitle→playback`，深链/返回栈不崩溃。
- 二级页 `SettingsSectionScreen` 按新分组渲染对应内容块；路由 `settings/{section}` 不变。
- 一级摘要 `sectionDetail` 同步更新（播放与字幕显示续播/倍速/字号；数据与存储显示视频数与占用）。

## F-26 数值表单紧凑化

`NumericSettingRow` 改为**紧凑行**（标题 + 当前值 + 单位，`defaultMinSize(minHeight = 56.dp)`），点击打开 `NumericEditDialog` 编辑对话框：

- 对话框预填当前值并**全选**（`TextFieldValue` + `TextRange`），聚焦即弹出键盘；
- 键盘完成或“确定”写入合法值；非法值不写入并提示范围，对话框不关闭；
- 取消/关闭不写入；与已保存值/上次写入值相同不重复落盘（沿用 `resolveNumericEdit`）。

保存语义与 F-37 一致（输入完成即生效，无逐行“保存”按钮），写入仍经 `SettingsWriteQueue` 串行保序。

## F-29 范围提示按需显示

编辑对话框的 `supportingText` 仅在**聚焦或出错**时渲染：默认不显示“允许范围：…”，聚焦后显示，出错时显示错误文案（error 色）。紧凑行本身不显示范围提示。

## 测试

| 层 | 文件 | 用例 |
|---|---|---|
| JVM 单测 | `feature/settings/.../SettingsSectionRoutingTest.kt` | 3（五入口、旧路由重定向、新/未知路由解析） |
| 仪器化 | `DetailSettingsInstrumentedTest` | 更新导航断言（外观/数据与存储）；数值用例改为对话框交互（紧凑行无范围提示→点击开对话框→聚焦显示范围→非法不写入→合法即写→返回重进保留） |
| 仪器化 | `WifiRiskAndImportTermsInstrumentedTest` | 设置·导入开关名断言改用 `SettingsSection.DATA` |

## 执行验证记录

见 `docs/ui-ux-audit/implementation/execution-log.md` 阶段 2A 小节与 `evidence/README-2A.md`。

## 版本

用户可见信息架构与表单形态变更，按项目规则递增补丁版本：`1.0.6 → 1.0.7`（versionCode 10006 → 10007），同步 `docs/CHANGELOG.md`。

## 已知限制

- 数值编辑改为对话框后，原“离开页面补写未提交输入”的行为由对话框显式确认替代（取消即放弃），语义更明确；F-37 的“最后一次修改落盘”由确认/键盘完成保证。
- 平板/折叠屏仅基本可用（D6），未做专门布局。
