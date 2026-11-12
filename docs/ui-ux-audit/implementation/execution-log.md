# 阶段 0 实施准备记录

- 阶段：0（准备与基线）
- 开始时间：2026-11-11 22:35 CST
- 结束时间：2026-11-11 22:45 CST
- 执行人：omp（自动代理）
- 边界：只读取/构建/检查环境，未修改业务源码、样式、Token、翻译或配置；未提交或推送 Git。本阶段新增文件仅 `execution-log.md` 与 `baseline-assets.sha256`。

## 一、基线状态

### 1.1 Git

| 项 | 值 |
|---|---|
| 分支 | `main` |
| 跟踪状态 | `main...origin/main [ahead 1]` |
| HEAD | `d9cf431 新增项目 AGENTS.md：Git 推送需授权、版本与验证规则` |
| 工作区 | 仅 `?? docs/ui-ux-audit/`；无已跟踪文件改动 |
| 未推送提交 | 1 个（`d9cf431`），按用户规则不推送 |

### 1.2 原始审计基线

| 项 | 值 |
|---|---|
| 审计目录 | `docs/ui-ux-audit/` |
| 文档 | README、summary、findings、pages、design-system、recommendations |
| 截图 | `assets/` 共 **36** 张，目录总计约 6.4 MiB |
| 完整性 | 本阶段未修改任何审计文档或截图；`baseline-assets.sha256` 记录 42 个文件（6 份文档 + 36 张截图）的 SHA-256 |

校验方式：PowerShell `Get-FileHash -Algorithm SHA256` 逐行比对，42/42 匹配。注意：本机 Git Bash 的 `sha256sum -c` 在 Windows 上会按文本模式重算二进制文件，36 张 PNG 会误报 `FAILED`；不要据此判断基线被改动。复核命令：

```powershell
Get-Content docs/ui-ux-audit/implementation/baseline-assets.sha256 | ForEach-Object {
  $h,$p = $_ -split '\s+',2
  if ((Get-FileHash -LiteralPath $p -Algorithm SHA256).Hash.ToLower() -ne $h) { "FAILED $p" }
}
```

### 1.3 构建产物

| 项 | 值 |
|---|---|
| APK | `app/build/outputs/apk/debug/app-debug.apk` |
| 大小 | 51,763,035 字节 |
| 包名/版本 | `com.privateplayer.media`，`versionCode=10001`，`versionName=1.0.1` |
| 原生库 | 仅 `arm64-v8a` |
| 构建时间 | 2026-11-11 20:55（本阶段增量构建为 UP-TO-DATE） |

## 二、工具链与命令

| 项 | 值 |
|---|---|
| JDK | JBR `openjdk 25.0.4`（`C:\Program Files\Android\Android Studio\jbr`） |
| Gradle Wrapper | 9.6.0 |
| AGP / KSP | 9.5.1 / 2.3.13（`gradle/libs.versions.toml`） |
| Android SDK | Platform `android-37.0`、Build-Tools `36.0.0` |
| 模拟器 | `Medium_Phone_API_37.0`（AVD 唯一） |
| 单元测试文件 | 12 个 Kotlin 测试文件（`src/test`） |
| 仪器化测试类 | 8 个（`app/src/androidTest`） |

### 命令清单

```bash
# 编译
./gradlew :app:assembleDebug

# 静态检查（根任务，覆盖全部模块）
./gradlew lintDebug

# JVM 单元测试（全部模块）
./gradlew testDebugUnitTest

# 仪器化/UI 测试（仅可丢弃模拟器；任务结束会卸载应用）
./gradlew :app:connectedDebugAndroidTest -PallowDestructiveConnectedTests=true

# 编译仪器化测试 APK（不运行）
./gradlew :app:assembleDebugAndroidTest
```

## 三、当前构建与测试结果

| 验证 | 命令 | 结果 |
|---|---|---|
| 编译 Debug | `:app:assembleDebug` | **成功**（UP-TO-DATE，17s 总流程） |
| 静态检查 | `lintDebug` | **成功**；14 个模块报告，**0 error**；`app` 报告 10 warning |
| JVM 单元测试 | `testDebugUnitTest` | **成功**；13 个结果 XML，**76 tests，2 skipped，0 failures/errors** |
| 仪器化测试编译 | `:app:assembleDebugAndroidTest` | **失败**（见 B-01） |
| 仪器化测试执行 | `connectedDebugAndroidTest` | **未执行**：无真机；且测试 APK 编译失败 |
| Release 构建 | `:app:assembleRelease` | 未执行（阶段 0 不需要） |

### B-01 仪器化测试编译失败（阻塞 UI/E2E 门禁）

`BUILD FAILED in 29s`，3 个测试文件、5 处错误：

1. `app/src/androidTest/java/com/privateplayer/media/DetailSettingsInstrumentedTest.kt:44`：`Unresolved reference 'SettingsScreen'`。
   - 原因：设置页已拆分为 `SettingsHubScreen` + `SettingsSectionScreen`，测试仍引用旧单页组件。
2. 同文件 `:223`：`SettingsScreen(viewModel = viewModel)` 调用点同样失效。
   - 额外冲突：该测试仍按“单页设置”滚动查找“显示/存储管理/关于”，与当前二级结构不一致，需重写而非仅改导入。
3. 同文件 `:246`：`DetailMediaRepository` 未实现 `MediaRepository.setSelectedSubtitleTrack`（字幕记忆新增的接口方法）。
4. `MpvPlaybackInstrumentedTest.kt:358`：`PlaybackServiceStarter { null }` 构造失败；该接口已从 `fun interface` 改为带 `start()/stop()` 的普通接口。
5. `PlaybackReleaseBarrierInstrumentedTest.kt:123`：同上。

影响：8 个仪器化测试类全部无法编译，Compose UI 测试与 E2E 门禁当前不可用。修复属于测试代码改动（不改变产品行为），建议作为阶段 1A 的前置任务 **B-01** 处理；阶段 0 不修改。

## 四、六项产品决策（正式记录）

| 编号 | 决策 | 对实施的影响 | 与现有代码/审计的关系 |
|---|---|---|---|
| D1 | UI 统一使用「文件夹」，技术路径/说明使用「目录」；「视频」「媒体」「文件」分别表达业务概念 | T-10 术语表按“上下文区分”而非“全局统一词”；首页/空状态/移动对话框等 UI 文案改为「文件夹」；文件信息、路径提示保留「目录」 | 当前 UI 大量使用「目录」；与审计 F-10“同一概念只留一种写法”不冲突，但需要明确“UI vs 技术说明”的边界，避免搜索/帮助文档混乱 |
| D2 | 当前版本完成中文 UI 字符串资源化，不强制英文翻译 | T-14 只建中文 `strings.xml` 与检查门禁；不建 `values-en`；资源 key 仍按可翻译方式命名 | 与审计建议一致；`stringResource` 从 0 起步，先覆盖阶段 1/2 页面 |
| D3 | 播放器展示当前队列，支持点击条目跳转；不扩展到媒体库级播放列表 | T-20 范围扩大：需要新增受控跳转 API；`PlaybackSession.playQueueIndex` 当前为 `private`，需评估暴露方式 | **与实施计划偏差**：计划默认“只展示、不跳转”，决策要求跳转；属于新增 API 的受控范围，不是 UI 库功能。需在阶段 1C 实现并补测试 |
| D4 | 详情页重命名/移动/删除/导出统一放顶栏更多菜单 | T-21 入口确定为顶栏“更多”；与审计建议一致 | 与现有导航一致（详情已有文件信息图标，可扩展） |
| D5 | 批量操作第一版只做删除和移动；分享/导出保留单项 | T-41 范围缩小；不实现批量导出/批量分享 | 避免 SAF 多文件导出的限制；与计划中“批量导出”表述冲突，以本决策为准 |
| D6 | 手机为主，平板/折叠屏保证基本可用，不承诺专门布局 | 验证矩阵中平板/折叠屏仅做基本可用检查，不作为必须的专门布局验收；不引入 `TwoPane` | 与审计“无响应式布局”一致；F-18 不扩展为平板优化 |

### 决策冲突与偏差报告

- **D3 与实施计划 T-20 偏差**：计划写“默认不做任意跳转，跳转需产品确认后单列”。现决策已确认跳转，阶段 1C 需新增 `playQueueIndex` 的受控公共入口，并补单元/仪器化测试；风险是队列索引与当前媒体切换的竞态，需在实现前补状态测试。
- **D5 与 T-41 描述偏差**：任务分解中“批量删除/移动/导出”需收敛为“删除/移动”；导出/分享仍走单文件菜单。
- 未发现代码或审计证据与 D1/D2/D4/D6 的实质冲突。

## 五、测试环境确认

### 5.1 已具备

| 项 | 状态与证据 |
|---|---|
| 模拟器 D1 | `Medium_Phone_API_37.0`，API 37，x86_64，1080×2400 / 420dpi，ARM64 转译；启动成功 |
| 竖屏/横屏 | 通过 `settings put system user_rotation 1` 验证 `ROTATION_90`，窗口 2400×1080；可恢复竖屏 |
| 字体 1.0/1.3/2.0 | `settings put system font_scale` 可设置并读取 1.0/1.3/2.0（已恢复 1.0） |
| 深色/浅色 | `cmd uimode night yes/no` 生效 |
| TalkBack | 系统镜像已安装 `com.google.android.marvin.talkback` 与 `talkbackoverlay` |
| SAF | `com.google.android.documentsui` 已安装；系统文件选择器可用 |
| 通知 | `dumpsys notification` 正常；模拟器支持通知/媒体会话 |
| 锁屏 | 模拟器支持（锁屏/解锁可脚本化）；本阶段未做锁屏控制验证 |
| 异形屏 | 6 个 cutout 模拟 overlay 可用（corner/double/hole/tall/waterfall 等） |
| 硬件键盘 | 内置 `qwerty2` 键盘；模拟器可使用宿主键盘 |
| 应用安装/启动 | Debug APK 安装成功，启动后版本 1.0.1 正常 |

### 5.2 缺失/未验证

| 项 | 状态 | 影响 |
|---|---|---|
| ARM64 真机 | **没有连接任何实体设备**（`adb devices` 为空） | F-27 横屏问题无法定性；按决策不得修改 Surface 链路；阶段 1F 阻塞 |
| API 26/29/34/36 环境 | 仅有 API 37 系统镜像，无其他 API AVD | 最低版本与前台服务/权限回归无法在本机完成；需下载镜像或使用真机 |
| 平板/折叠屏设备 | 无专门 AVD/真机；可用 `wm size/density` 模拟尺寸，但无法模拟折叠 | D6 仅能验证“基本可用”，不能替代真实折叠屏 |
| 蓝牙/耳机 | 模拟器 Bluetooth 为 ON 但无物理设备 | 实体蓝牙按键/音频链路无法验证 |
| SAF 云端目标 | DocumentsUI 可用，未确认 Drive 等云盘客户端 | 导出目标仅能验证本地目录；云盘需真机账号 |
| TalkBack 实际朗读 | 已确认包存在，未做操作验证 | 阶段 1 需补开关/面板/文件行朗读实测 |
| 横屏最终结论 | 无真机 | 见 F-27/阶段 1F |

## 六、未解决问题与阻塞

| 编号 | 类型 | 描述 | 影响阶段 | 处理建议 |
|---|---|---|---|---|
| B-01 | 测试基础设施（P0 门禁） | 8 个仪器化测试类无法编译（3 个文件、5 处错误） | 阶段 1 出口及以后所有 UI/E2E 门禁 | 阶段 1A 前修复测试代码：设置页测试改为二级结构、补 `setSelectedSubtitleTrack`、改造 `PlaybackServiceStarter` 构造；不改产品行为 |
| B-02 | 硬件 | 无 ARM64 真机 | 阶段 1F（F-27） | 需用户提供真机；在此之前只做模拟器记录，不修改 Surface 链路 |
| B-03 | 环境 | 缺 API 26/34/36 | 阶段 1/4 兼容性矩阵 | 下载对应系统镜像或提供真机；阶段 1A 不受阻 |
| B-04 | 环境 | 无折叠屏/平板设备 | 阶段 4（D6 基本可用） | 用 `wm size/density` 做尺寸冒烟；正式验收需设备或明确豁免 |
| B-05 | 硬件 | 无蓝牙耳机 | 阶段 4 通知/媒体按键 | 真机手测；模拟器不替代 |
| B-06 | 决策偏差 | D3 要求队列跳转，T-20 计划默认不跳转 | 阶段 1C | 阶段 1C 增加受控 API 与测试；实现前评估索引竞态 |

## 七、阶段 1A 实施条件

阶段 1A 对应 T-37（统一设置保存模型）与 B-01 前置修复。

| 条件 | 状态 |
|---|---|
| 产品决策已记录 | ✅ D1–D6 已记录，D3/D5 偏差已标注 |
| 编译/单测/Lint 绿 | ✅ 0 error；76 tests，2 skipped，0 failure |
| 测试设备 | ✅ AVD API 37 可启动、可安装、可旋转/改字号 |
| UI/E2E 测试门禁 | ❌ B-01：仪器化测试无法编译 |
| 真机 | ❌ 仅影响 F-27，不阻塞 T-37 |
| 审计基线 | ✅ 36 张截图 + SHA-256 清单 |
| Git | ✅ 未提交；`main` ahead 1，工作区仅审计目录未跟踪 |

**结论**：1A 的编码与手工验证可以开始；但在 B-01 修复前，不能用 Compose UI/仪器化测试作为 T-37 的验收证据，只能使用 JVM 单测 + 模拟器手测截图。建议把 B-01 作为 1A 的第一个小任务（仅测试代码），修复后再进入 T-37 的实现与门禁。

## 八、后续入口

1. **1A 前置**：修复 B-01（测试代码编译），确认 `connectedDebugAndroidTest` 至少能跑一个测试类。
2. **1A 主任务**：T-37 设置保存模型。
3. **并行**：T-38/T-16 播放器面板可在 AVD 上开发；T-27 等待真机。
4. 阶段 1 出口前需补齐：B-01、D3 的队列跳转 API、D5 的批量范围收敛。

## 九、变更与副作用声明

- 本阶段新增：`docs/ui-ux-audit/implementation/execution-log.md`、`baseline-assets.sha256`。
- 未修改：业务源码、测试源码、样式、Token、翻译、Gradle 配置、审计报告及 36 张截图。
- 未提交/推送 Git。
- 模拟器以 `-read-only` 启动，验证后已 `emu kill` 关闭；AVD 基础状态未被持久修改。

---

## 十、阶段 1A-0.5：MediaSessionLifecycle 测试契约更新

- 时间：2026-11-12
- 范围：仅 `app/src/androidTest/java/com/privateplayer/media/MediaSessionLifecycleInstrumentedTest.kt`
- 生产代码：未修改

### 10.1 原始失败原因

完整套件中 `serviceAndControllerSharePlaybackAcrossBackgroundAndActivityRecreation` 超时，断言为「退出页面后重开不重载」：真实控制器 `connected=false`，服务端已是新会话。

根因：1.0.1 起正式行为是「关闭播放页即停止服务」；测试仍假设返回文件树后服务继续、旧控制器保持连接。属于过时行为断言，不是编译或环境问题。

### 10.2 契约调整

| 场景 | 旧断言 | 新断言 |
|---|---|---|
| 返回文件树 | 服务继续运行 | 等待服务停止（`isServiceRunning=false`） |
| 旧控制器 | 保持连接并继续播放同一媒体 | 等待 `isConnected=false` 后释放 |
| 重新打开 | 同一会话不重载、进度不回退 | 等待新服务启动，重新 `connect`，新控制器播放同一媒体 |
| 断点 | 旧控制器位置连续 | 关闭前暂停并定位 8s，确认 DB 已保存；重开后新会话位置 ≥ 6.5s |
| 播放次数 | 重开后仍为 1 | 重开会话真实播放后为 2（第一段 + 重开各计一次） |
| 第一段结束 | playCount == 1 | playCount == 2 |
| 再次返回 | 服务继续 | 服务停止、旧控制器断开 |
| 损坏文件 | 复用旧控制器读取错误 | 停服后重新绑定新控制器，等待真实 `playerError` |

playCount 语义已核对：`PlaybackSession.persistProgress` 每次装载在 `hasPlayed` 后最多 `+1`，`playCountRecorded` 防止同一会话重复计数；因此“重开新会话”预期为 2，不是机械改数字。

### 10.3 稳定性处理

- 全部改为条件等待：服务运行状态、控制器连接状态、DB 断点、playCount。
- 关闭前先 `pause` 并 `seekTo(8s)`，等待 DB 保存后再返回，避免位置漂移。
- 重开时先等待服务运行，再建立新控制器；避免旧控制器断开与新服务启动竞态。
- 错误媒体路径直接绑定控制器，避免前台服务在错误后释放导致 `isServiceRunning` 误判。
- 未新增固定 Sleep。

### 10.4 验证结果

| 验证 | 结果 |
|---|---|
| `:app:assembleDebugAndroidTest` | 成功 |
| `MediaSessionLifecycleInstrumentedTest` 定向 | **1/1 通过** |
| 完整 `connectedDebugAndroidTest` | **31/31 通过，0 failure，0 error，0 skipped** |
| `testDebugUnitTest` | 76 tests，2 skipped，0 failure |
| `:app:assembleDebug` | 成功 |
| `lintDebug` | 0 error，app 10 warning |

### 10.5 阶段结论

- 仪器化套件已全绿；B-01 的编译阻塞与唯一失败断言均已关闭。
- 未修改生产代码/UI/Token/翻译/配置，未提交或推送 Git。
- **可以进入 F-37（设置保存模型）**。

---

## 十一、阶段 1A：F-37 设置保存模型统一

- 时间：2026-11-12
- 对应任务：T-37（`task-breakdown.md`）、问题 F-37（P1）
- 变更记录：[changes/0011-settings-immediate-save.md](../../changes/0011-settings-immediate-save.md)

### 11.1 决策

选定“即时保存”：同页开关与数值采用同一语义，删除逐行“保存”按钮。

数值提交时机取**键盘完成 / 失焦 / 离开页面**，不取“键入即写 + 防抖”。理由：数值是低频低值域设置，键入即写会在 `1.25` 这类中间态上产生无意义写入；防抖方案需额外维护键入态与取消语义，复杂度高于收益。写入保序由串行队列保证，规避 T-37 登记的“连续输入写放大 / 最后一次输入必须落盘”风险。

### 11.2 实施

| 文件 | 改动 |
|---|---|
| `feature/settings/.../SettingsInputs.kt` | `NumericSettingRow` 改为即时提交；删除“保存”按钮、`edited`/`onSave`；新增 `NumericEdit`/`resolveNumericEdit`；数值行加 testTag |
| `feature/settings/.../SettingsWriteQueue.kt` | 新增：串行写入队列 + 失败回调 |
| `feature/settings/.../SettingsViewModel.kt` | 写入改走队列；失败弹 Snackbar；版本号改用 `AppVersionNameProvider`；新增 `@ApplicationScope` |
| `feature/settings/.../SettingsModule.kt` | 新增：提供 `AppVersionNameProvider` |
| `feature/settings/.../SettingsScreen.kt` | 调用参数 `onSave` → `onCommit` |
| `feature/settings/build.gradle.kts` | 新增测试依赖 `kotlinx-coroutines-test` |
| `feature/settings/src/test/.../SettingNumberTest.kt` | +6 用例：提交判定 |
| `feature/settings/src/test/.../SettingsWriteQueueTest.kt` | +2 用例：写入保序与失败隔离 |
| `app/src/androidTest/.../DetailSettingsInstrumentedTest.kt` | +1 用例：即时保存端到端；更新 ViewModel 构造参数 |

### 11.3 验证结果

| 验证 | 结果 |
|---|---|
| `:feature:settings:testDebugUnitTest` | **11 tests，0 failure，0 error** |
| `testDebugUnitTest`（全模块） | **83 tests，2 skipped，0 failure**（原 76，净增 7） |
| `:app:assembleDebug` | 成功 |
| `:app:assembleDebugAndroidTest` | 成功 |
| `lintDebug` | **0 error**；`feature/settings` 0 warning；app 10 warning（与基线一致） |
| `DetailSettingsInstrumentedTest` 定向 | **3/3 通过**（含新增 `settingsNumericRowsSaveImmediatelyWithoutSaveButton`） |
| 完整 `connectedDebugAndroidTest` | 见 11.4 |

### 11.4 完整仪器化套件

完整 `connectedDebugAndroidTest`：**32/32 通过，0 failure，0 error，0 skipped**（原 31 项，新增 F-37 用例 1 项）。

分布：`DetailSettingsInstrumentedTest` 3、`FileTreeConsistencyInstrumentedTest` 20、`MpvPlaybackInstrumentedTest` 4，其余 5 个类各 1 项。

> 补记：新增用例首轮被上一轮手测残留的倍速值（2.5）打断——断言写死了“初始为 1.0”。已改为先写入确定的基线值 1 再断言，测试不再依赖设备残留状态；重跑完整套件 32/32 通过。

### 11.5 阶段结论

- F-37 的三项验收标准（数值修改后返回重进值保留、页面无“保存”按钮、非法值不写入且有提示）均由新增仪器化用例覆盖并通过。
- 未改动播放器、文件树、导入链路与设计 Token。
- 范围提示始终显示（F-29）、数值行密度（F-26）、文案资源化（F-14）仍属阶段 2/1E，未在本任务夹带。
- **可以进入 T-16/F-38/F-17（阶段 1B 播放器面板）。**

---

## 十二、阶段 1B：播放器面板语义与沉浸式（F-16 / F-38 / F-17）

- 时间：2026-11-12
- 对应任务：T-16、T-38、T-17
- 变更记录：[changes/0012-player-panels.md](../../changes/0012-player-panels.md)

### 12.1 根因与决策

- **F-38**：`ModalBottomSheet` 是独立对话框窗口，旧实现只隐藏 Activity 窗口的系统栏，对面板窗口无效。改为在面板内容里沿父视图链取 `DialogWindowProvider` 的窗口，单独隐藏其系统栏；幂等、不高频重复。
- **F-16**：`SheetAction` 无语义类型，所有行传同一 `Check`。引入 `SheetActionKind`（SingleChoice/Action/Navigation/Status）+ `selected`；仅当前单选项勾选+高亮+`Role.RadioButton`；导航带箭头；状态不可点击。
- **F-17**：删除“更多”面板的“播放引擎”禁用行；引擎信息保留在 设置·关于。
- 顺序按 1B-1(F-38)→1B-2(F-16)→1B-3(F-17) 执行；因同属 `Dialogs.kt`/`PlayerScreen.kt`，合并为一次改动提交。

### 12.2 验证结果

| 验证 | 结果 |
|---|---|
| `:core:design:testDebugUnitTest` | 8 tests，0 failure |
| `PlayerSheetSemanticsInstrumentedTest` | 6/6 通过 |
| `PlayerSheetImmersiveInstrumentedTest` | 4/4 通过（dumpsys 断言状态栏隐藏/恢复） |
| 实机五个面板 | 倍速/比例/音轨/字幕/队列仅当前项勾选+高亮；更多无技术行、导航带箭头；打开/关闭/重开状态栏均隐藏 |
| `:app:assembleDebug` / `lintDebug` / `testDebugUnitTest` | 见 12.3 |
| 完整 `connectedDebugAndroidTest` | 见 12.3 |

### 12.3 门禁与回归

| 验证 | 结果 |
|---|---|
| `:app:assembleDebug` | 成功 |
| `lintDebug` | 0 error（14 模块） |
| `testDebugUnitTest` | 92 tests，0 failure（原 84，+8） |
| 完整 `connectedDebugAndroidTest` | **42/42 通过，0 failure/error/skipped**（原 32，+10） |

播放器回归（含于完整套件）：播放/暂停/跳转、音轨/字幕切换、返回停服、重开断点续播、MediaSession 生命周期均通过；未修改 libmpv 核心与会话契约。
横屏/深色/1.3 字号已复拍（见 evidence/README-1B.md）；手势导航与 TalkBack 手测列为后续真机验证。

### 12.4 阶段结论

- F-16/F-38/F-17 三项验收标准分别由语义单测、沉浸式仪器化测试与实机截图覆盖并通过。
- 未提前实施 F-19/F-20；顶栏图标与队列渲染保持原样。
- 手势导航/厂商差异、横屏/深色/1.3 字号复拍与 TalkBack 手测列为后续验证。
- **阶段 1B 通过验收；不自动进入阶段 1C。**

---

## 十三、阶段 1C：播放器入口归位与真实播放队列（F-19 / F-20）

- 时间：2026-11-12
- 对应任务：T-19、T-20（含阶段 0 决策 D3 的受控跳转 API）
- 变更记录：[changes/0013-player-entries-queue.md](../../changes/0013-player-entries-queue.md)

### 13.1 根因与决策

- **F-19**：顶栏把“画面比例”画成旋转图标，旋转动作藏在“更多”。修复：比例入口改 `AspectRatio` 图标（描述“画面比例”）；旋转提为顶栏独立按钮（描述“旋转屏幕”），复用既有 `toggleOrientation()`；“更多”删除旋转行，全应用仅一个旋转入口。比例（Fit/Fill/裁剪）与屏幕方向严格区分。
- **F-20**：队列面板从未渲染 `state.queue`，且 `playQueueIndex` 私有无 UI 入口。修复：入口/面板更名“播放队列”；自定义 `PlaybackQueueSheet`（同字幕面板模式，复用 `ImmersiveSheetWindowEffect`）渲染队列、当前项高亮带勾、限高懒加载、打开/当前项变化定位、空队列 `EmptyState`；保留上一集/下一集与播放模式。
- **受控跳转 API**：新增 `QueueSelection.shouldAccept` 纯逻辑受理判定 + `PlaybackSession.playQueueItem(index)`（主线程同步判定，受理后经与 playNext/playPrevious 相同的 `transitionMutex` 切源门执行），逐层委托 Coordinator/ViewModel；不暴露可变 `queueIndex`。空队列/越界/重复目标/切换中连点均为无操作。MediaSession 生命周期契约不变。
- 队列条目标题取文件名（隐私），当前项以 `queueIndex` 判定；队列顺序始终为目录列举顺序，播放模式只影响“下一集”/自动连播选取。

### 13.2 测试

| 层 | 文件 | 用例数 |
|---|---|---|
| JVM 单测 | `feature/player/.../QueueSelectionTest.kt` | 10（空队列/越界/重复/陈旧/连点） |
| 仪器化 F-19 | `app/.../PlayerEntriesInstrumentedTest.kt` | 4 |
| 仪器化 F-20 | `app/.../PlayerQueueInstrumentedTest.kt` | 4（含真实 libmpv 沙箱跳转） |

旋转测试用 debug 宿主 `RotationTestActivity`（configChanges 同 MainActivity）；真实播放测试沿用 MpvPlayback 沙箱，Surface 与面板共用同一宿主，快速连点用切源门保证并发确定性。

### 13.3 门禁与回归

| 验证 | 结果 |
|---|---|
| `:app:assembleDebug` | 成功；aapt2 确认 versionCode=10004 / versionName=1.0.4，仅 arm64-v8a |
| `lintDebug`（14 模块） | 0 error |
| `testDebugUnitTest` | 102 tests，2 skipped，0 failure（原 92，+10） |
| 完整 `connectedDebugAndroidTest`（headless） | **50/50 通过，0 failure / 0 error / 0 skipped**（原 42，新增 F-19 4 例 + F-20 4 例） |

### 13.4 阶段结论

- F-19/F-20 验收标准由新增单测、仪器化测试与实机截图覆盖。
- 未改动 DB schema、libmpv 核心与 MediaSession 生命周期契约；未实施播放列表 CRUD/持久化/拖拽排序（不在 1C 范围）。
- 证据索引见 `evidence/README-1C.md`。
- **阶段 1C 通过验收；不自动进入阶段 1D。**
