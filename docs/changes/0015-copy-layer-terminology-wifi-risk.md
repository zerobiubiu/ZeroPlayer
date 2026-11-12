# 术语统一、文案资源层与 Wi-Fi 风险提示（阶段 1E）

状态：已执行。影响范围：`core/design`、`domain`、`feature/home`、`feature/settings`、`feature/importer`、`feature/detail`、`feature/player`、`app` 的用户可见文案与消息模型；新增文案门禁 JVM 测试；版本号。

## 背景

UI/UX 审计阶段 1E 修复三个问题：

- **F-10（P1）**：同一设置三套叫法（缩略图/查重），用户可见容器“目录/文件夹”混用。
- **F-14（P1）**：全项目中文硬编码、0 次 `stringResource`、无 i18n 层。
- **F-09（P1）**：Wi-Fi 服务“无鉴权、同网可读写”的风险埋在长段落中间，无独立警示。

产品决策（阶段 0 已确认）：术语按上下文区分——用户可见容器称“文件夹”，技术路径称“目录”；“视频”指可播放内容，“媒体”指库/资源总称，“文件”指通用对象。i18n 只做中文资源化，不建 `values-en`。

前置阶段成果保持不变：F-37（设置即时保存 + SettingsWriteQueue）、F-16/F-17/F-38（播放器面板）、F-19/F-20（入口归位与真实队列）、F-22/F-21（菜单分组与详情操作）、F-32（删除确认已统一为 ConfirmDialog）。

## F-10 术语统一

| 概念 | 统一后 | 落点 |
|---|---|---|
| 用户可见容器 | 文件夹 | 首页“新建文件夹/此文件夹为空/返回上级文件夹”、移动对话框“选择目标文件夹/返回上级文件夹”、删除确认“删除文件夹？”、文件行副标题“文件夹” |
| 技术路径 | 目录 | 文件信息“相对路径/绝对路径”、Wi-Fi“初始目录”、数据层路径说明保留 |
| 缩略图开关 | 导入时生成缩略图 | `core/design` 单一资源 `design_import_thumbnails`，设置页与 Wi-Fi 面板共同引用 |
| 查重开关 | 检测疑似重复视频 | `core/design` 单一资源 `design_import_duplicates`，两处共同引用 |
| 播放模式 | 文件夹循环 | 原“目录循环”改为“文件夹循环”（用户可见） |

两个导入开关的文案只在 `core/design` 定义一次；设置页与 Wi-Fi 面板都引用同一资源，门禁测试断言两处引用且不允许各自重复定义。

## F-14 文案资源层

### 资源归属

按模块建立 `res/values/strings.xml`（中文，key 按可翻译方式命名）：

| 模块 | 内容 |
|---|---|
| `core/design` | 共享组件自身文案：取消/返回/重试/加载中、名称校验、删除/移动对话框、多选顶栏、文件菜单动作、选中状态、两个导入开关 |
| `domain` | 枚举选项文案（主题/画面比例/字幕背景/字幕颜色），设置与播放器共用同一来源 |
| `feature/home` | 首页标题/面包屑/空态/行标签/对话框标题/全部操作反馈 |
| `feature/settings` | 一级入口与摘要、七个二级页全部文案、数值输入提示、写入反馈 |
| `feature/importer` | Wi-Fi 面板全部文案（含风险块）、导入通知 |
| `feature/detail` | 详情页信息表/字幕区/文件信息/全部操作反馈 |
| `feature/player` | 顶栏/底栏/手势反馈/五个面板/播放错误 |
| `app` | 底部导航、导入任务对话框、导出进度、传输反馈、外部打开、播放服务 |

### 共享组件与逻辑层的分工

- 共享组件（`AppTopBar`、`EmptyState`、`ErrorState`、`ConfirmDialog`、`NameDialog`、`MoveDialog`、`DeleteDialog`、`FileActionMenuContent` 等）自身的通用文案用 `core/design` 资源；功能级文案仍由调用方以参数传入，不引用 feature 资源。
- `FileAction` 枚举改为 `labelRes`（资源 ID），菜单渲染与 testTag 都从资源解析，保证名称全应用唯一。
- `domain` 的 `ThemeMode`/`VideoAspect`/`SubtitleBackground`/`SubtitleTextColor` 与 `feature/player` 的 `PlayMode` 改为 `labelRes`，删除各界面重复的私有 label 扩展（设置、播放器、字幕面板原本各有一套叫法，如“半透明背景/半透明”）。
- ViewModel 不再持有中文消息：`HomeMessage`（首页）、`SettingsMessage`（设置）、`DetailMessage`（详情）为“资源 + 参数”的纯数据模型，界面用 `Context.getString` 解析；播放器/服务/传输层经注入的 `Context.getString` 取资源。错误原因中来自数据层/引擎的 `error.message` 保持原文透传（其资源化属剩余范围）。
- `nameError` 重构为纯函数 `nameErrorReason`（Blank/DotPath/InvalidCharacter），文案由界面按原因取资源；校验规则不变，单测改为断言原因分类。

### 门禁（区分用户可见 vs 内部字符串）

新增 `app/src/test/.../copy/CopyResourceGateTest.kt`（随 `testDebugUnitTest` 运行，无新增运行时依赖）：

1. **硬编码扫描**：扫描 UI 模块（feature 各模块、core/design、app 主代码）Kotlin 字符串字面量中的中文；跳过注释/KDoc/字符字面量，剔除模板插值后按静态文本比对。内部字符串（日志、dumpsys 诊断、异常控制流、testTag）逐条登记在白名单并注明理由；未登记即失败。
2. **术语黑名单**：用户可见资源中禁止出现“新建目录/此目录为空/删除目录/打开目录/选择目标目录/返回上级目录/生成视频缩略图/检测重复视频”等旧叫法。
3. **同源断言**：两个导入开关只在 `core/design` 定义，设置页与 Wi-Fi 面板均引用，且两模块资源不得重复定义。

### 迁移统计

- 迁移前：UI 模块中文硬编码约 400 处（全仓约 710 处），`stringResource` 0 次。
- 迁移后：UI 模块用户可见文案全部资源化；`stringResource`/`Context.getString` 覆盖首页、设置、播放器、详情、Wi-Fi、导入/导出对话框、底部导航。
- 剩余（不在本阶段范围，原因如下）：
  - `data`/`core/storage`/`core/playback`/`domain` 部分错误原因与 `OperationOutcome.Failure` 文案（约 200 处）：经 UI 透传展示，属 F-12/F-39 错误分类阶段（2D）统一处理；引擎日志/诊断（`MpvPlaybackEngine` 等）为内部字符串，不迁移。
  - `PlaybackSession.loadStage`、服务 dumpsys 输出、testTag：内部字符串，已登记白名单。
  - `domain` 的 `MediaSort`/`MediaCategory`/`GridDensity` label：旧媒体库遗留，无 UI 调用点，随 F-31 死代码清理处置。

## F-09 Wi-Fi 风险提示

先核实服务实现：`WebUploadHttpServer` 全部路由（列表/下载/上传/新建/重命名/移动/删除）无任何鉴权、令牌或登录；`WebUploadServiceController.localAddress()` 绑定站点本地地址（wlan/ap/eth），即同一局域网设备可达。风险属实，按审计建议只改提示，不加鉴权/协议。

`WifiImportSheet` 新增独立警示块 `WifiRiskNotice`：

- 位置：状态/地址/消息之后、启动按钮之前，常驻（服务运行中也显示）；关闭面板重开仍显示。
- 形式：`errorContainer`/`onErrorContainer` 容器 + `WarningAmber` 图标 + 两行文字，图标+文字不依赖颜色，浅/深色均可读（主题容器色，对比度随主题既有 AA 结论）。
- 文案（ux-writing：事实+建议，无营销/恐吓）：
  - 标题：同一网络内的设备可读写你的文件
  - 说明：服务没有登录或访问令牌。仅在可信网络使用。
- 原长段落只保留操作说明（上传/下载/管理、传完停服、关面板不停服），风险句不再混在其中。

## 测试

| 层 | 文件 | 用例 |
|---|---|---|
| JVM 门禁 | `app/.../copy/CopyResourceGateTest.kt` | 3（硬编码白名单扫描、术语黑名单、导入开关同源） |
| JVM 单测 | `feature/home/.../HomeViewModelTest.kt` | 更新为新消息/错误资源契约，+2（新建成功只反馈结果、消息资源映射完整唯一） |
| JVM 单测 | `core/design/.../FileActionMenuGroupingTest.kt` | 名称校验改为原因分类断言（规则不变） |
| 仪器化 | `app/.../WifiRiskAndImportTermsInstrumentedTest.kt` | 3（风险块独立/近按钮/重开仍在；Wi-Fi 面板开关名；设置·导入开关名一致） |

既有仪器化测试仅更新构造参数（`MediaDetailViewModel` 新增 `Context`）与因术语变化而失效的文本断言，未削弱断言。

## 执行验证记录

- 执行环境：Windows 11；JBR（`C:\Program Files\Android\Android Studio\jbr`）；Gradle 9.6.0；AGP 9.5.1；compileSdk 37；模拟器 `Medium_Phone_API_37.0`（API 37，x86_64 + ARM64 转译），**headless**（`-no-window -no-snapshot-save -no-boot-anim -no-audio`，进程命令行已取证）。

| 验证 | 结果 |
|---|---|
| `:app:assembleDebug` | 成功；aapt2 确认 versionCode=10006 / versionName=1.0.6，仅 arm64-v8a |
| `lintDebug`（14 模块） | 0 error；修复 LocalContextGetResourceValueCall（组合期 stringResource）与 LoadingState 参数顺序 |
| `testDebugUnitTest`（全模块） | 118 tests，2 skipped，0 failure / 0 error（原 113，+5） |
| 完整 `connectedDebugAndroidTest`（headless） | 63/63 通过，0 failure / 0 error / 0 skipped（原 60，+3） |

详见 `docs/ui-ux-audit/implementation/execution-log.md` 阶段 1E 小节与 `evidence/README-1E.md`。

## 版本

用户可见文案与风险提示属行为变更，按项目规则递增补丁版本：`1.0.5 → 1.0.6`（versionCode 10005 → 10006），同步 `docs/CHANGELOG.md`。

## 已知限制

- 数据层/引擎错误原因文案尚未资源化（剩余清单见上），在 2D 错误分类阶段统一处理。
- 门禁白名单是“内部字符串”的显式登记，新增内部中文必须登记理由；用户可见文案新增必须进资源。
- 模拟器为 x86_64 + ARM64 转译；Wi-Fi 风险块与术语的实机观感以模拟器截图为准，真机朗读顺序需 TalkBack 手测。
