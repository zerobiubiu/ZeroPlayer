# 0001 AGP 9 内置 Kotlin 与 KSP / 第三方依赖元数据冲突

状态：已解决（长期记录）
影响范围：构建脚本、依赖版本

## 现象

1. 配置阶段失败：

```text
A problem occurred configuring project ':data'.
> com.android.builder.errors.EvalIssueException: Using kotlin.sourceSets DSL to add Kotlin sources is not allowed with built-in Kotlin.
  Kotlin source set 'debug' contains: [...ksp/debug/kotlin, ...ksp/debug/java]
```

2. 换成可用 KSP 之后，编译阶段失败：

```text
Module was compiled with an incompatible version of Kotlin.
The binary version of its metadata is 2.4.0, expected version is 2.2.0.
```

## 原因

- AGP 9 内置 Kotlin（KGP 2.2.10）后，KSP `2.2.10-2.0.2` 仍通过 `kotlin.sourceSets` 注册生成目录，与新 DSL 冲突。
- Coil `3.6.3` 传递依赖 `kotlin-stdlib 2.4.10`，而编译器为 2.2，读不了 2.4 元数据。

## 解决

1. 版本目录中把 KSP 改为 `2.3.13`（独立版本线，兼容 AGP 9 新 DSL）。
2. 根 `build.gradle.kts` 强制统一 stdlib 并排除旧 jdk7/jdk8 工件：

```kotlin
allprojects {
    configurations.configureEach {
        resolutionStrategy {
            force("org.jetbrains.kotlin:kotlin-stdlib:2.2.10")
            force("org.jetbrains.kotlin:kotlin-stdlib-common:2.2.10")
        }
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk7")
        exclude(group = "org.jetbrains.kotlin", module = "kotlin-stdlib-jdk8")
    }
}
```

3. 不要应用 `org.jetbrains.kotlin.android` 插件（AGP 9 会直接报错）。

## 验证

- `./gradlew :app:assembleDebug` 通过；
- `./gradlew :core:common:testDebugUnitTest` 通过（9 个用例）；
- 设备安装后 Hilt 生成类齐全（`HomeViewModel_Factory`、`Hilt_PrivatePlayerApplication` 等）。

## 注意

- 不要在没有 `clean --no-build-cache` 的情况下把“修复前的构建缓存”继续用于安装包：曾出现 KSP 生成类缺失、运行时 `NoSuchMethodException: HomeViewModel.<init>()`。若修改了 KSP/Hilt 相关配置，先清缓存再验证。
- 新增第三方依赖后，若出现元数据版本错误，优先检查其 stdlib 依赖版本。
