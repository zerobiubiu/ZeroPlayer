# 文档中心

本目录是项目文档的唯一入口：索引、规范、要求都在这里。

## 一、文档索引

版本变更：[CHANGELOG.md](CHANGELOG.md)。

### architecture/（现状文档，随系统演进更新）

| 文档 | 内容 |
|---|---|
| [overview.md](architecture/overview.md) | 工程架构：模块划分、依赖方向、数据流、导航结构 |
| [storage-and-import.md](architecture/storage-and-import.md) | 私有存储布局、流式导入、断点续传、WorkManager 恢复、失败语义 |
| [playback-abstraction.md](architecture/playback-abstraction.md) | 播放引擎抽象、libmpv 适配、生命周期、打包与能力边界 |
| [mpv-integration-feasibility.md](architecture/mpv-integration-feasibility.md) | MPV / libmpv 集成可行性验证报告（含证据与结论） |
| [feature-status.md](architecture/feature-status.md) | 功能完成度、已知限制、下一阶段清单 |
| [release-pipeline.md](architecture/release-pipeline.md) | 版本规则、标签触发发行、签名密钥管理与产物校验 |

### decisions/（架构决策记录）

| 编号 | 决策 |
|---|---|
| [0001](decisions/0001-modular-compose-architecture.md) | 多模块 + Compose + Hilt + Room + DataStore 的工程形态 |
| [0002](decisions/0002-logical-folders.md) | 旧逻辑文件夹方案；已由 [0006](decisions/0006-private-file-tree.md) 替代 |
| [0003](decisions/0003-mock-playback-first.md) | 先完成引擎抽象与 Mock，再验证 libmpv 后接入 |
| [0004](decisions/0004-import-resume-with-workmanager.md) | 导入任务持久化 + WorkManager 恢复 + 字节级续传 |
| [0005](decisions/0005-toolchain-agp9-builtin-kotlin.md) | 工具链：AGP 9.5 / 内置 Kotlin / KSP 2.3 / compileSdk 37 |
| [0006](decisions/0006-private-file-tree.md) | 私有文件树取代逻辑归档；保留旧数据 |

### changes/（不可变变更记录，定稿后不重写）

| 编号 | 变更 |
|---|---|
| [0001](changes/0001-initial-implementation.md) | 第一阶段实现：工程、设计系统、核心页面、数据层、导入链路、播放抽象、MPV 验证页 |
| [0002](changes/0002-web-upload-and-settings.md) | 局域网网页上传、静态资源打包及设置页选择与数值输入 |
| [0003](changes/0003-libmpv-playback.md) | libmpv 接入、ZeroPlayer 更名与图标、容量单位修正及验证 |
| [0004](changes/0004-arm64-only.md) | ARM64 单架构打包、体积变化及兼容性验证 |
| [0005](changes/0005-file-tree-transfer.md) | 私有文件树、任意文件传输、系统分享和播放器交互 |
| [0006](changes/0006-media3-session.md) | Media3 系统会话、后台播放与 libmpv 单实例生命周期，含系统控制中心和锁屏实景证据 |
| [0007](changes/0007-open-source-release.md) | 开源发布：新图标、GPL-3.0、标签驱动的 CI/CD 发行流水线与仓库配置 |
| [0008](changes/0008-player-ui-and-settings.md) | 关闭播放页即结束播放、字幕记忆、播放页重排与异形屏、速度自适应滑动、设置二级页面 |
| [0009](changes/0009-instrumentation-test-compile-fix.md) | 修复 B-01 仪器化测试编译：设置二级页测试、字幕接口替身、服务启动器构造 |
| [0010](changes/0010-media-session-test-contract.md) | 更新 MediaSessionLifecycle 测试契约：关闭停服、重开新会话、断点与 playCount |
| [0011](changes/0011-settings-immediate-save.md) | 设置保存模型统一为即时生效：删除逐行“保存”按钮、非法值不落库、写入串行保序 |
| [0012](changes/0012-player-panels.md) | 播放器面板语义与沉浸式：单选/动作/导航/状态区分、面板窗口隐藏系统栏、移除技术行 |
| [0013](changes/0013-player-entries-queue.md) | 播放器入口归位与真实播放队列：顶栏比例/旋转分离、播放队列渲染与受控跳转 |

### troubleshooting/（长期可复现的问题）

| 编号 | 问题 |
|---|---|
| [0001](troubleshooting/0001-agp9-builtin-kotlin-traps.md) | AGP 9 内置 Kotlin 与 KSP / 第三方依赖元数据冲突 |
| [0002](troubleshooting/0002-compose-surface-long-click.md) | Material3 `Surface(onClick)` 不支持长按，需 combinedClickable |
| [0003](troubleshooting/0003-workmanager-foreground-dataSync.md) | WorkManager 前台导入在 Android 14+ 需要 dataSync 服务类型 |
| [0004](troubleshooting/0004-module-level-lint-permissions.md) | 模块级 Lint 只看本模块清单，权限与服务类型需在库模块声明 |
| [0005](troubleshooting/0005-gesture-layer-blocks-buttons.md) | 全屏手势层盖住播放器按钮时，点击事件会被手势识别消费 |

## 二、文档规范

- 类型由目录体现：`architecture/` 现状、`decisions/` 决策、`changes/` 变更、`troubleshooting/` 排查。
- 编号只在目录内局部递增；现状文档使用固定文件名。
- 现状文档可更新；变更文档一旦定稿不再改写，后续变化另建文档。
- 每份文档头部写明状态与影响范围；涉及代码的给出路径。
- 源码改动必须随行写变更文档；新增文档回本索引登记。

## 三、要求

- 验证必须写清：执行环境、验证命令、实际结果、结论与残余风险。
- 文档与注释统一使用简体中文；上游代码/协议名称保留原文。
- 不把分析、日志、临时结论塞进 `AGENTS.md`；规则进 `AGENTS.md`，细节进 `docs/`。
