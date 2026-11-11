# 设计体系审查

审查对象：`core/design` 的主题、组件与各功能模块的本地样式。结论：**设计 Token 已经存在且质量不错，但覆盖不完整、执行不一致**——一半的尺寸仍用原始 `dp`，播放器完全独立，组件存在重复与死代码。

代码统计（`feature/` + `app/src/main` + `core/design`）：

- `Spacing.*` 引用 100 次；原始 `xx.dp` 字面量 123 处。
- 中文界面字符串 767 处；`stringResource(` 0 次；`strings.xml` 只有 `app_name`。
- 字体样式定义 12 个，实际使用 8 个；2 个（displaySmall、headlineMedium）零引用。

---

## 1. 字体体系

| 样式 | 字号/行高 | 字重 | 使用次数 | 评价 |
|---|---|---|---|---|
| displaySmall | 34/40 | SemiBold | 0 | 定义未用 |
| headlineMedium | 28/34 | SemiBold | 0 | 定义未用 |
| headlineSmall | 24/30 | SemiBold | 2 | 详情标题、Wi-Fi 标题 |
| titleLarge | 20/26 | SemiBold | 1 | 顶栏 |
| titleMedium | 16/22 | SemiBold | 11 | 分组标题、行标题 |
| titleSmall | 14/20 | Medium | 9 | 播放器标题、任务名 |
| bodyLarge | 16/24 | Normal | 6 | 设置行标题 |
| bodyMedium | 14/20 | Normal | 14 | 正文 |
| bodySmall | 12/16 | Normal | 26 | 辅助说明 |
| labelLarge | 14/20 | Medium | 7 | 按钮、时间 |
| labelMedium | 12/16 | Medium | 3 | 小标签 |
| labelSmall | 11/14 | Medium | 5 | 角标、字幕来源 |

问题：

- **字号跨度大但使用集中**：24/20/16/14/12/11 六级，其中 16/14/12 承担 90% 文本；24 与 20 仅两处。层级可用，但 11sp 只用于标签，属于下限。
- **未使用样式**：`displaySmall`、`headlineMedium` 从未引用（`Theme.kt:58,65`），增加维护噪音。
- **字重滥用风险低**：SemiBold 仅用于标题，Medium 用于 label，正文 Normal，符合规则。
- **无品牌字体**：`FontFamily.Default`，中文使用系统字体；对“私人影院”定位缺少识别度（非缺陷，属风格选择）。
- **字号缩放**：系统字体 1.3 倍实测无截断（`home-font130.png`、`settings-hub-font130.png`、`settings-playback-font130.png`）；2.0 倍未验证。

建议：删除未用样式；把 11sp 明确限定为“角标/时间戳”并在设计系统文档中写死；考虑为标题引入一款中文字体（若产品需要品牌感）。

---

## 2. 颜色体系

- 明色：primary `#5B4BCE`、background `#FDF8FF`、secondaryContainer `#E6E0F9`、tertiary `#7C5800`、error `#BA1A1A`，容器色 `#F7F2FA/#F1ECF4/#EBE6EE`。
- 暗色：primary `#C8BFFF`、background `#131218`、容器 `#1B1A20/#1F1E24/#2A2830`。
- 播放器独立色板：`PlayerText` 纯白、`PlayerTextSecondary` 72% 白、`PlayerAccent #4A84FF`（`PlayerOverlays.kt:70-74`），不参与主题。

实测对比度（WCAG）：

| 组合 | 对比度 | 结论 |
|---|---|---|
| onSurfaceVariant / background（明） | 8.84:1 | AAA |
| primary / background（明） | 6.00:1 | AA |
| error / background（明） | 6.17:1 | AA |
| onSurfaceVariant / background（暗） | 11.04:1 | AAA |
| primary / background（暗） | 10.96:1 | AAA |
| 播放器次级文字 / 黑 | 10.59:1 | AAA |
| 播放器强调色 / 黑（非文本） | 6.04:1 | 图形 ≥3:1 通过 |
| 白字 / 播放器强调色 | 3.48:1 | 仅用于图形/轨道，非文本 |

评价：**没有发现对比度不足**。问题在一致性：

- 播放器强调色与应用主色（紫）分属两套，蓝色仅出现在播放器（`player-portrait.png` vs `settings-hub-light.png`）。这是可接受的产品选择，但缺少书面规则，后续页面容易各自发明强调色。
- 播放器的渐变遮罩、黑色半透明面板不在主题 Token 中，属于“硬编码颜色”。
- `PlaceholderPalettes`（`Color.kt:130`）为缩略图占位渐变，仍被 `MediaThumbnail` 使用；若未来恢复网格列表可复用。

建议：建立 `PlayerPalette` 独立 Token 并从主题派生背景/文字；把强调色写入设计系统，明确“应用内容用主色、播放器媒体控制用蓝色”的规则。

---

## 3. 间距体系

- Token：`xxs=2, xs=4, sm=8, md=12, lg=16, xl=24, xxl=32, xxxl=48`，另有 `screenHorizontal=16`、`contentBottom=96`。
- 使用：`sm(30) > screenHorizontal(21) > md(17) > lg(10) > xs(9) > xl(7) > contentBottom(6)`；`xxs/xxl/xxxl` 无引用。
- 原始 dp：123 处，其中播放器 16 种不同值（10/12/14/16/20/22/26/28dp…）。

问题：

- **90% 的版面遵循 8/12/16/24 四级**，但播放器、缩略图角标、错误条等使用 6/10/14/18/20/22/26/28 等非刻度值。
- **同一语义多种值**：页面横向边距有时用 `Spacing.screenHorizontal(16)`，播放器用 20dp，对话框用 M3 默认 24dp；卡片内部有时 12/16/20dp。
- `xxl/xxxl` 定义未用，`contentBottom=96` 与底部导航实际高度强耦合（导航高度变化会留白过多）。
- 播放器横屏底栏 `top=28dp`、按钮间距 `26dp` 属于随手值（`PlayerOverlays.kt:191,239`）。

建议：把间距刻度扩展为 4/8/12/16/24/32 并强制使用；播放器单独定义 `PlayerSpacing` 但同样基于刻度；删除未用 Token。

---

## 4. 圆角与边框

- `Radius`：`thumbnail=12, card=16, sheet=28, pill=999`（`Theme.kt:36`）。
- `Shapes`：`extraSmall=6, small=10, medium=16, large=22, extraLarge=30`（`Theme.kt:43`）。
- 实际使用：卡片 16、对话框 M3 默认 28、底部面板 28、缩略图 12、按钮胶囊 999、进度条 50、错误条 12、播放器面板 12。

问题：**两套圆角语义并存且数值冲突**（card=16 与 medium=16 重复；sheet=28 与 extraLarge=30 接近；进度条 50 是任意值）。组件作者无法判断该用 `Radius` 还是 `Shapes`。

建议：确定单一来源：`Shapes` 作为组件默认，`Radius` 仅用于非 M3 自定义块（缩略图、胶囊），并在文档中给出“何时用哪个”。

边框：几乎不使用描边，依赖容器色分层；输入框错误态使用主题 error，符合规范。

---

## 5. 阴影与层级

- 无自定义 `shadow`；全部使用 M3 色调容器（`surfaceContainerLow/High`）表达层级。
- 播放器用黑色渐变与半透明面板代替 elevation，与 Material 3 的“夜间媒体”场景一致。
- 问题：`surfaceContainerLow` 与 `background` 的明度差很小（明色 `#F7F2FA` vs `#FDF8FF`），在低亮度屏幕上卡片边界可能不明显；暗色同理（`#1B1A20` vs `#131218`）。属可接受范围，未收到对比问题。

---

## 6. 组件规范

### 6.1 清单与状态

| 组件 | 位置 | 使用面 | 状态完整性 | 备注 |
|---|---|---|---|---|
| `AppTopBar` | Bars.kt | 详情、设置全部 | 有返回/操作 | 首页未使用（F-30） |
| `SectionHeader` | Bars.kt | 详情 | 有右侧动作 | 未用于设置 |
| `SettingSection` | Bars.kt | 设置全部 | — | 仅标题+内容 |
| `InfoRow` | Bars.kt | 详情、存储、关于 | — | 假设“短值”，长路径破坏节奏 |
| `SwitchRow` | Dialogs.kt | 设置、Wi-Fi | 开/关 | 双目标（F-33） |
| `ClickableSettingRow` | Dialogs.kt | **无引用** | — | 死组件（F-31） |
| `NumericSettingRow` | SettingsInputs.kt | 设置 | 正常/错误/禁用 | 保存模型问题（F-37） |
| `SettingsDropdownRow` | SettingsInputs.kt | 设置 | 展开/收起 | 与 hub 箭头不一致 |
| `ActionSheet` | Dialogs.kt | 播放器 5 个面板 | 启用/禁用/危险 | 勾选语义被滥用（F-16） |
| `ConfirmDialog` | Dialogs.kt | 详情删字幕 | 危险/普通 | 首页未复用（F-32） |
| `EmptyState` | States.kt | 首页 | 有/无动作 | 结构完整 |
| `ErrorState` | States.kt | 首页 | 仅重试 | 缺标题与第二动作（F-39） |
| `LoadingState` | States.kt | 首页、详情 | — | 播放器不用 |
| `SelectionTopBar` | Bars.kt | **无引用** | — | 为多选准备但未接线（F-41） |
| `FilterChipRow` | Dialogs.kt | **无引用** | — | 死组件 |
| `PrimaryActionButton` | Dialogs.kt | **无引用** | — | 死组件 |
| `MediaThumbnail` | MediaComponents.kt | 详情 | 有图/占位/进度/收藏 | 设计良好 |
| `MediaCardData` | MediaCardData.kt | **无引用** | — | 旧列表模型 |
| `FileTreeRow` | feature/home | 首页 | 启用/禁用 | 本地组件，合理 |
| `PlayerOverlays` 组件 | feature/player | 播放器 | 有 loading/禁用 | 完全本地，未复用 Token |

### 6.2 主要不一致

1. **两套顶栏**（F-30）。
2. **两套删除对话框**（F-32）。
3. **五处死组件/模型**：`FilterChipRow`、`ClickableSettingRow`、`PrimaryActionButton`、`MediaCardData/toCardData`、`SelectionTopBar`（定义存在、无调用）。
4. **选中语义不统一**：播放器面板用勾选图标表示“所有行”；设置下拉用文字值；设置一级用 chevron；`ClickableSettingRow` 用主色文字。
5. **表单模型不统一**：开关即时、数值手动保存。
6. **按钮尺寸**：`Button` 默认 40/48dp、`OutlinedButton` 默认、播放器自绘 32/44/56dp、卡片入口 72dp；缺少统一的最小触控保证（F-36）。

---

## 7. 图标体系

- 来源统一：`androidx.compose.material.icons` + `material-icons-extended`；无第三方图标库。
- 描述：交互图标均提供 `contentDescription`（返回、菜单、播放控制等）；装饰图标为 `null`。
- 问题：
  - Filled/Outlined 混用：详情页 `Filled.PlayArrow` + `Outlined.Info`；播放器 Filled 传输键 + Outlined 顶栏；`Check` 被当作装饰图标。
  - 语义错位：`ScreenRotation` 打开画面比例（F-19）。
  - 文件类型仅用 `Description` 通用文档图标，视频/字幕/文本无法区分（`FileTreeRow.kt:56`）。
  - 面板模式项全部用 `Repeat`，四种模式（顺序/单曲/列表/随机）图标相同。
- 建议：约定 Filled 仅用于媒体传输（播放/暂停/快进），其余用 Outlined；为视频/字幕/文本提供三种图标；模式项使用各自图标（Repeat/RepeatOne/Shuffle）。

---

## 8. 响应式与适配

- 无 `WindowSizeClass` 或多列布局；首页单列列表、详情固定 16:9、设置全宽行。
- 播放器支持横屏，进入时设置 `layoutInDisplayCutoutMode=ALWAYS/SHORT_EDGES`，顶/底控件使用 `safeDrawing` 避让；视频 Surface 全屏（代码验证，缺口真机未验证）。
- 测试：1080×2400 / 420dpi；字体 1.3 倍通过；深色通过；横屏播放出现撕裂（F-27 待验证）。
- 未覆盖：平板、折叠屏、横屏设置页、字体 2.0、多窗口。
- 建议：明确“手机竖屏优先”的产品范围；若支持平板，首页/设置需要 `TwoPane` 或最大宽度约束（当前全宽行在 >600dp 会过长）。

---

## 9. 文案资源与本地化

- 无 `stringResource`、无多语言目录、767 处中文字面量（F-14）。
- 术语与标点问题见 F-10、F-11。
- 建议：建立文案资源与术语表；补充 `values-en` 前先确认产品是否需要英文；用 lint 规则/CI 阻止新增硬编码。

---

## 10. 已通过的检查（供后续回归）

- 深浅色文本对比度全部 ≥4.5:1（实测见上表）。
- 交互图标具备可访问名称；文件行提供 `onClickLabel`/`onLongClickLabel`。
- 空/错/载状态均存在；空状态包含图标、标题、说明、动作。
- 危险操作（删除文件/目录/字幕）有确认对话框，删除按钮使用 error 色且文案说明不可撤销。
- 加载/错误状态使用 `liveRegion`，动态内容可被读屏感知。
- 字体 1.3 倍无截断、无重叠。
- 播放器手势与控件已分层，控件点击不会被手势吞掉（本会话修复后验证）。
