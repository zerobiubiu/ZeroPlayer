# 空状态/不支持提示/校验文案/标点/行菜单噪声/图标圆角规则（阶段 3）

状态：已执行。影响范围：`feature/home` 空状态与不支持提示、行菜单噪声；`feature/detail`/`feature/player` 标点；`core/design` 圆角单一刻度；设计系统文档；版本号。

## 背景

UI/UX 审计阶段 3 收尾 P3 细节：F-02、F-03、F-05、F-11、F-28、F-35。

## F-02 空状态单一 CTA

空状态主 CTA 改为 **“导入文件”**（触发本机导入），不再与顶栏“新建文件夹”重复；文案“导入视频，或从右上角新建文件夹。”去掉“加号”歧义。新建文件夹仍可由顶栏进入。

## F-03 不支持提示含文件名

`HomeMessage.UnsupportedFile(name)` 携带文件名；文案“「%1$s」不是可播放的视频。可从操作菜单导出或分享。”点名文件并给出下一步，不再否定式开头。

## F-05 重命名校验文案

1E 已将 `nameError` 重构为 `nameErrorReason`，文案“名称不能包含 / 或 \”（可见字符示例），不再出现“空字符”术语；本阶段核验保留。

## F-11 标点与单位统一

- 字幕格式列表 `srt / ass / ssa / vtt` → `srt、ass、ssa、vtt`（顿号，中文语境）；
- 外挂字幕计数括号 `外挂字幕 (N)` → `外挂字幕（N）`（全角）；
- 延迟单位统一 `%d ms`（数字与单位间半角空格，含 +/- 按钮）。

## F-28 行尾菜单噪声

隐藏每行常驻的三点按钮（降低列表右侧噪声、释放文件名宽度）；操作菜单改由**读屏自定义操作**（`CustomAccessibilityAction`“打开操作菜单”）触发，触屏经详情页更多菜单管理。多选态行尾本就隐藏。菜单构建器与详情页同源不变。

## F-35 图标与圆角规则

- **图标规则**：导航/操作=Outlined，媒体传输控制（播放/暂停/快进/快退）=Filled；写入设计系统 §7.1。详情页 Filled.PlayArrow 属媒体控制，与顶栏 Outlined 并存为规则内有意区分。
- **圆角单一刻度**：`Radius`（thumbnail=12/card=16/sheet=28/pill=999）为唯一来源；`Shapes` 由 Radius 派生，消除原两套数值冲突。

## 测试

| 层 | 文件 | 用例 |
|---|---|---|
| JVM 单测 | `HomeViewModelTest` | 不支持提示含文件名断言更新 |
| 仪器化 | `FileMenuAndDetailActionsInstrumentedTest` | 菜单经读屏自定义操作打开（F-28）；长按多选（F-41） |
| 仪器化 | `DetailSettingsInstrumentedTest` | 外挂字幕全角括号断言更新（F-11） |

## 执行验证记录

见 `docs/ui-ux-audit/implementation/execution-log.md` 阶段 3 小节与 `evidence/README-3.md`。

## 版本

用户可见文案与交互细节变更，按项目规则递增补丁版本：`1.0.13 → 1.0.14`（versionCode 10013 → 10014），同步 `docs/CHANGELOG.md`。

## 已知限制

- F-28 隐藏三点后，触屏用户经详情页更多菜单管理文件（行菜单仅读屏自定义操作）；首次使用引导未加（P3，可后续）。
- 文件类型图标仍为通用 Description（视频/字幕/文本区分未做，审计建议项，非本阶段必须）。
