# 0001 多模块 + Compose + Hilt + Room + DataStore 的工程形态

状态：已执行
影响范围：全工程

## 背景

需要从零建立一个可运行、可扩展的 Android 私人影音应用，功能域包括媒体库、导入、详情、播放器、设置，且后续要接入 libmpv 原生渲染。若采用单模块或 `Activity + Fragment` 结构，UI、数据库、文件系统、播放引擎会迅速耦合，后期无法替换播放实现。

## 决策

1. 单 Activity + Jetpack Compose + Navigation Compose；播放器是独立全屏路由。
2. 15 个 Gradle 模块：`app`、6 个 `core:*`、`domain`、`data`、6 个 `feature:*`。
3. 依赖方向固定为 `feature → core/domain`、`data → core*/domain`，禁止反向。
4. Hilt 负责装配：仓库接口在 `data` 绑定实现，设置接口在 `core:datastore` 绑定，存储接口在 `core:storage` 绑定。
5. 状态一律 `ViewModel + StateFlow<UiState>`；Compose 只读状态、只发意图。
6. 依赖版本集中在 `gradle/libs.versions.toml`。

## 备选方案

| 方案 | 未采用原因 |
|---|---|
| 单模块 | 播放引擎、存储、数据库互相可见，无法保证"UI 不直接依赖 MPV/文件系统" |
| 每个页面一个模块 + 共享 `core` 过多 | 模块过度拆分，构建与导航成本高于收益 |
| 引入 Convention Plugin（build-logic） | AGP 9 新 DSL 与内置 Kotlin 下第三方构建逻辑兼容风险高；当前每模块 build 文件足够短 |

## 后果

- 构建时间随模块数增加，但可并行（已开启 Gradle parallel/caching/config cache）。
- 每个 feature 自带 build 文件模板，新增页面成本固定且可复制。
- 播放引擎、存储、数据库的实现可独立替换；已在 `docs/architecture/overview.md` 给出依赖检查表。
