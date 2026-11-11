# 0005 工具链：AGP 9.5 / 内置 Kotlin / KSP 2.3 / compileSdk 37

状态：已执行
影响范围：构建脚本、Gradle、全部模块

## 背景

开发机的 Android Studio 版本对应 AGP 9.5，SDK 仅安装了 API 37 平台与 build-tools 36.0.0；官方文档说明 Compose 1.12 起要求 `compileSdk 37` 与 AGP 9。AGP 9 起 Kotlin 支持内置于 AGP（默认 KGP 2.2.10），不再需要 `kotlin-android` 插件。

## 决策

1. AGP `9.5.1` + Gradle `9.6.0` + `compileSdk/targetSdk 37` + `minSdk 26`。
2. 不应用 `kotlin-android` 插件；由 AGP 内置 Kotlin 编译 Kotlin 源码。
3. Compose 编译器插件仍使用 `org.jetbrains.kotlin.plugin.compose`，版本与内置 KGP 对齐（2.2.10）。
4. KSP 使用 `2.3.13`（独立版本线）：AGP 捆绑的 `2.2.10-2.0.2` 在 AGP 9 新 DSL 下会报 `kotlin.sourceSets ... is not allowed with built-in Kotlin`。
5. 统一把 `kotlin-stdlib` 固定为 2.2.10，并排除 `kotlin-stdlib-jdk7/jdk8`：Coil 3.6 会引入 stdlib 2.4.10，其元数据版本超出 2.2 编译器可读范围。
6. JDK 使用 Android Studio 的 JBR（25），构建脚本通过 `JAVA_HOME` 指定。

## 备选方案

| 方案 | 未采用原因 |
|---|---|
| 降级到 AGP 8.x | 与已装 SDK（仅 API 37 平台）和 Compose 1.12 不兼容 |
| 关闭内置 Kotlin（`android.builtInKotlin=false`） | AGP 10 将移除该选项；且需同时关闭新 DSL，后续迁移成本更高 |
| 升级 KGP 到 2.4 | 需通过 buildscript classpath 覆盖 AGP 依赖，未经官方文档验证的组合风险更高 |
| 使用 `kotlin-kapt` | 与内置 Kotlin 不兼容，官方建议迁移 KSP |

## 后果

- 构建体系与 Android Studio 自带模板一致，未来升级 AGP 更平滑。
- 受内置 Kotlin 2.2.10 限制，依赖库若使用更高语言特性会被 stdlib 固定策略暴露；新增依赖时需验证元数据兼容性（已记录在 troubleshooting/0001）。
- KSP 版本与 KGP 解耦，升级时需同时验证 Room/Hilt 代码生成是否正常。
