# 0006 并行构建成功但设置页缺少 Hilt 绑定

状态：设置闪退已在 1.2.3（10203）修复；上游 KSP 修复尚未经本项目验证。
影响范围：`:feature:settings`、`:feature:player` 的 KSP 输出与调试 APK 中的 Hilt 注册。

## 本项目已证实什么

- 1.2.2 调试 APK 有 `SettingsViewModel` 及其注入构造，却缺少对应的 `KeyModule` 和 `BindsModule`；应用的 key 表和 provider 表都没有设置，播放器注册则正常。这与 [0001](0001-agp9-builtin-kotlin-traps.md) 的 Kotlin 元数据不兼容不同：旧包构建成功，缺件在打开设置时才暴露。
- 在启用并行的构建中，设置和播放器的 `kspDebugKotlin` 都留下过空输出树的缓存条目。条目能证明输出缺失，不能说明它最初为何为空。
- [1.2.3 交付记录（0033）](../changes/0033-settings-navigation-hilt-bindings.md)：两模块在 KSP 生成后校验非空的 key、binding 标记，Kotlin 编译也将两者列为必需输入。正确输出仍可在普通增量构建中复用；这两处校验不是对 KSP 并发竞争的修复。

## 与上游问题的关系

- [KSP #3286](https://github.com/google/ksp/issues/3286) 描述 KSP 2.3.13 在同一 JVM 并发执行时，处理器可能看不到依赖库的符号，因而漏生成注入代码。并行构建、生成物缺失这两个特征相符，但不能据此断定 1.2.2 命中了该竞态。
- [KSP 2.3.14 发行说明](https://github.com/google/ksp/releases/tag/2.3.14) 明确列出对 #3286 的修复：只在没有活跃的 KSP 执行时清理共享 `CoreJarFileSystem` 的 handlers。

## 维护建议（未执行）

- [版本目录](../../gradle/libs.versions.toml) 仍固定 KSP `2.3.13`；[Gradle 配置](../../gradle.properties) 中 `org.gradle.parallel=true` 允许模块并行，`ksp.incremental=false` 只关闭 KSP 增量处理，不是串行开关。
- 本机实际 Gradle 用户缓存的 `modules-2/files-2.1/com.google.devtools.ksp/symbol-processing-gradle-plugin` 下没有 `2.3.14`。本轮未获准下载或升级，也未做版本 A/B，不能宣称已根治。
- 下次获准更新依赖时，先确认 2.3.14 与现有 AGP、内置 Kotlin 兼容，保留[两模块的工件校验](../../build.gradle.kts)，优先在正常并行构建中验证，不扩大模块白名单，也不关闭全局并行。
- 验收时核对 APK 的 key 表和 provider 表，确认两张表都含设置与播放器，再运行[真实设置导航测试](../../app/src/androidTest/java/com/privateplayer/media/SettingsNavigationInstrumentedTest.kt)，覆盖从首页进入设置、打开二级页及返回后再入。
