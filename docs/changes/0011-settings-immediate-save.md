# 设置保存模型统一为即时生效

状态：已执行。影响范围：`feature/settings` 模块、`app/src/androidTest` 中的设置仪器化测试。

## 背景

UI/UX 审计问题 F-37（P1，已验证）：同一设置页里存在两种保存模型——`SwitchRow` 开关点一下即生效，`NumericSettingRow` 数值必须点行尾“保存”按钮。

具体表现：

- 数值行右侧“保存”按钮未编辑时为禁用态，只呈现为灰色文字，视觉上像普通说明文本；
- 用户不确定改动是否已生效；
- 离开页面未保存的修改直接丢失。

审计任务 `task-breakdown.md` 的 T-37 要求同页控件采用同一种保存语义，并选定“即时保存”：输入框在失焦、键盘 Done 或合法值变化后保存，删除每行“保存”按钮；非法值不写入并恢复上次值；保存失败要提示。

风险登记（`implementation-plan.md` 第四章）：连续输入会导致 DataStore 写放大，需防抖或提交时机控制，且要保证最后一次输入一定落盘。

## 方案

### 保存语义

统一为“输入完成即生效”，与同页开关一致：

| 场景 | 行为 |
|---|---|
| 键盘 Done（`ImeAction.Done`） | 合法值立即写入并收起焦点 |
| 失焦 | 合法值立即写入 |
| 离开页面（行被销毁） | 若仍有合法输入，补写一次 |
| 非法值 + 键盘 Done | 不写入，行内提示“请输入 0.25～4 倍” |
| 非法值 + 失焦 | 不写入，恢复为已保存值并说明原因 |

`NumericSettingRow` 删除行尾 `TextButton` 与 `contentDescription = "保存$title"` 语义，改用整行 `OutlinedTextField`；每个数值行加 `Modifier.testTag("setting-$title")` 供仪器化测试定位。

### 防抖与写入顺序

不采用“键入即写”的防抖模型，理由：数值是低频、低值域（整数秒、百分比、倍速）设置，键入即写会在 `1.25` 这种中间态上产生无意义写入，而防抖方案需要在键入过程中维护额外状态与取消语义，复杂度高于收益。

因此改为**提交时机控制**：

- 写入只在明确的完成时机发生（Done / 失焦 / 离开页面），连续键入不会放大写入；
- 行内保留 `lastCommitted`，与已保存值相同或与本次会话上次写入值相同时跳过，避免重复落盘。

写入顺序由 `SettingsWriteQueue` 保证：

- 所有设置写入进入同一个 `Channel`，由应用级作用域（`@ApplicationScope`）中的单个消费者串行执行；
- 连续提交按提交顺序执行，最后一次修改最后落盘，不会被先前的慢写入覆盖；
- 队列消费使用应用级作用域，离开设置页后仍在完成写入。

### 失败反馈

`SettingsWriteQueue` 捕获写入异常并回调 `SettingsViewModel`，通过既有的 `message` 流驱动 Snackbar 提示“设置保存失败：<原因>”。此前 `launchSetting` 用 `runCatching` 静默吞掉异常，失败时 UI 无任何反馈。

### 版本号读取

`SettingsViewModel` 原本直接持有 `Context` 读取 `packageManager`，使 ViewModel 无法在 JVM 单元测试中构造。改为注入 `AppVersionNameProvider`（`SettingsModule` 提供），保留原有读取失败回退为“—”的行为。

## 实施

| 文件 | 改动 |
|---|---|
| `feature/settings/.../SettingsInputs.kt` | `NumericSettingRow` 改为即时提交；删除“保存”按钮与 `edited`/`onSave` 状态；新增 `NumericEdit` 与 `resolveNumericEdit` 纯函数；数值行加 testTag |
| `feature/settings/.../SettingsWriteQueue.kt` | 新增：设置写入串行队列，顺序落盘 + 失败回调 |
| `feature/settings/.../SettingsViewModel.kt` | 写入改走队列；失败经 Snackbar 提示；版本号改用 `AppVersionNameProvider`；新增 `@ApplicationScope` 依赖 |
| `feature/settings/.../SettingsModule.kt` | 新增：提供 `AppVersionNameProvider` |
| `feature/settings/.../SettingsScreen.kt` | 数值行调用参数 `onSave` → `onCommit` |
| `feature/settings/build.gradle.kts` | 新增测试依赖 `kotlinx-coroutines-test` |
| `feature/settings/src/test/.../SettingNumberTest.kt` | 新增 6 个用例覆盖提交判定：合法值提交、非法值拒绝、与已保存值/上次写入值相同不重复写、连续修改最后一次生效、整数项边界 |
| `feature/settings/src/test/.../SettingsWriteQueueTest.kt` | 新增 2 个用例：连续提交按序写入且最后一次生效；写入失败被报告且不阻断后续写入 |
| `app/src/androidTest/.../DetailSettingsInstrumentedTest.kt` | 新增 `settingsNumericRowsSaveImmediatelyWithoutSaveButton`；更新 ViewModel 构造参数 |

## 执行验证记录

- 执行环境：Windows 11 10.0.26300；JDK JBR 25.0.4（`C:\Program Files\Android\Android Studio\jbr`）；Gradle 9.6.0；AGP 9.5.1；Kotlin 2.2.10；compileSdk 37；Build-Tools 36.0.0
- 验证命令与实际输出：

```bash
# 1. 设置模块编译 + 单元测试
./gradlew :feature:settings:compileDebugKotlin :feature:settings:testDebugUnitTest
# BUILD SUCCESSFUL in 13s
# SettingNumberTest: tests="9" skipped="0" failures="0" errors="0"
# SettingsWriteQueueTest: tests="2" skipped="0" failures="0" errors="0"

# 2. 应用编译 + 仪器化测试编译 + 全量 JVM 单测
./gradlew :app:assembleDebug :app:assembleDebugAndroidTest testDebugUnitTest
# 结果见下表

# 3. 静态检查
./gradlew lintDebug

# 4. 仪器化测试（可丢弃模拟器）
./gradlew :app:connectedDebugAndroidTest -PallowDestructiveConnectedTests=true
```

- 验证结论：

| 验证 | 结果 |
|---|---|
| `:feature:settings:testDebugUnitTest` | **11 tests，0 failure，0 error**（SettingNumberTest 9 + SettingsWriteQueueTest 2） |
| `testDebugUnitTest`（全模块） | **83 tests，2 skipped，0 failure，0 error**（原 76，净增 7） |
| `:app:assembleDebug` | 成功 |
| `:app:assembleDebugAndroidTest` | 成功 |
| `lintDebug` | **0 error**（14 个模块）；`feature/settings` 0 warning；app 10 warning（与基线一致） |
| `DetailSettingsInstrumentedTest` 定向 | **3/3 通过**，含新增 `settingsNumericRowsSaveImmediatelyWithoutSaveButton` |
| 完整 `connectedDebugAndroidTest` | **32/32 通过，0 failure，0 error，0 skipped**（模拟器 `Medium_Phone_API_37.0`，API 37，x86_64 + ARM64 转译） |

- 新增仪器化用例覆盖 F-37 的三项验收标准：

| 验收标准 | 断言 |
|---|---|
| 页面无“保存”按钮 | `onAllNodesWithText("保存", substring = true).assertCountEquals(0)` |
| 非法值不写入且有提示 | 输入 `9` 后断言行内出现“请输入0.25～4 倍”，且 `defaultSpeed` 仍为 `1.0` |
| 修改后返回重进值保留 | 输入 `2.5` → 键盘完成 → `defaultSpeed == 2.5f`；连续改 `3` → `== 3.0f`；返回一级再进入，字段仍显示 `3.0` |

- 手测记录（模拟器 `Medium_Phone_API_37.0`，API 37）：

| 用例 | 观察结果 |
|---|---|
| 非法值不落库 | 默认倍速输入 `9` → 行内红色“请输入0.25～4 倍”；返回一级摘要仍为 `1.09×` |
| 合法值即时写入 | 输入 `2.5` → 键盘完成 → 无需点任何按钮，一级摘要立即变为 `自动续播 · 2.5×` |
| 应用重启后保持 | `am force-stop` 后重启 → 摘要仍为 `2.5×`（DataStore 已落盘） |
| 页面无“保存”按钮 | 设置·播放与设置·字幕的界面树中均无文本“保存”的节点 |

- 截图证据：`docs/ui-ux-audit/implementation/evidence/`（浅色、深色、1.3 倍字号、字幕页），索引见同目录 `README.md`。

> 补记：新增用例初次运行时依赖“设备上倍速恰好是 1.0”，被上一轮手测残留的 2.5 打断。已改为先写入确定的基线值 1 再断言，后续断言不再依赖设备残留状态。重跑完整套件 32/32 通过。

## 版本

设置保存模型属用户可见行为变更，按项目版本规则递增补丁版本：`1.0.1 → 1.0.2`（`versionCode 10001 → 10002`），并同步 `docs/CHANGELOG.md`。
