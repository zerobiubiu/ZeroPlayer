# 文件树多选与批量删除/移动（阶段 2G）

状态：已执行。影响范围：`feature/home` 多选状态、SelectionTopBar 接线、批量操作与逐项结果；`core/design` SelectionTopBar 复用；相关单测；版本号。

## 背景

UI/UX 审计问题 F-41（P2）：文件树只能逐行操作，删除/移动多个文件需重复菜单操作。原始需求包含多选与批量。产品决策 D5：批量第一版只做**删除和移动**（不做批量导出/分享）。

## 实施

### 多选状态与顶栏

- 首页新增 `selectedPaths: Set<String>`；**长按进入多选**（选中该项），多选态单击切换选中、长按切换选中。
- 多选态顶栏切换为 `SelectionTopBar`（复用 core/design，F-31 保留）：显示已选数量、关闭（退出多选）、**全选 / 清除 / 移动 / 删除**。
- 返回键：多选态退出多选（不退出目录/应用）；普通态返回上级。
- 切换目录自动清空选择，避免跨目录误操作。
- 多选态行首显示选中圈（CheckCircle/Circle），选中行 secondaryContainer 高亮；行尾菜单隐藏（批量操作在顶栏）。

### 批量操作与逐项结果

- `HomeViewModel.deleteBatch / moveBatch`：逐项执行，**先停播放再动文件**（沿用 `PlaybackStopper`，MediaSession 生命周期契约不变）；串行 working 保护防重复提交。
- `BatchResult`：成功项路径 + 失败项（路径, 原因）；**不全成功/全失败一刀切**。
- 结果处理：成功项从选择移除，**失败项保留选择**并汇总；Snackbar 按 全成功/部分失败/全失败 显示数量（`HomeMessage.BatchDone` + `formatArgs`）。
- 批量删除二次确认（点名数量、不可撤销，ConfirmDialog destructive）；批量移动复用 MoveDialog 目录选择器（以首个选中项约束目标）。
- 列表同步：删除/移动后 `observeDirectory` 自动刷新，与单项路径一致。

## 测试

| 层 | 文件 | 用例 |
|---|---|---|
| JVM 单测 | `feature/home/.../HomeViewModelTest.kt` | +4（批量删除部分失败保留失败项、批量移动全成功、批量删除全失败、批量结果消息格式化） |
| 仪器化 | 既有首页用例 | 多选/批量回归（完整套件） |

## 执行验证记录

见 `docs/ui-ux-audit/implementation/execution-log.md` 阶段 2G 小节与 `evidence/README-2G.md`。

## 版本

用户可见新功能，按项目规则递增补丁版本：`1.0.12 → 1.0.13`（versionCode 10012 → 10013），同步 `docs/CHANGELOG.md`。

## 已知限制

- 批量移动以首个选中项约束目标目录（排除自身子树）；混合目录批量移动时其余项若目标冲突按逐项失败处理。
- 批量导出/分享不在本版范围（D5）。
- 大目录全选性能未专项压测（阶段 4）。
