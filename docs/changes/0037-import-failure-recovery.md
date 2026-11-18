# 导入失败：区分重新上传与恢复文件夹

状态：已执行。以下验证绑定到列出的 APK 哈希。

影响范围：1.3.0 后的兼容修复，随 1.4.0（10400）合包；没有另行构建或发布 1.3.1。涉及导入任务、Wi-Fi 手机面板、后台保存和随包网页。不改数据库、上传请求格式、文件树校验或清理权限。

## 原因和恢复动作

1.3.0 的 [ImportWorker](../../data/src/main/java/com/privateplayer/data/importer/ImportWorker.kt) 把下面的异常正文直接保存到 `import_tasks.errorMessage`，没有独立原因码。任务对话框此前把三条都归为通用错误，只在「详情」说明真正原因；手机面板和网页均对所有带任务号的保存失败显示重试，仓库也只检查 `FAILED`。

| 条件 | 历史记录的确切正文 | 现在可做什么 |
|---|---|---|
| 已接收的网页任务没有待恢复的提交日志，且找不到已接收的普通暂存文件 | 上传暂存文件已丢失，请从网页重新上传 | **上传暂存文件已丢失，请从网页重新上传。** 原任务不能重试保存，也没有可下载的暂存。从网页选原文件、点上传会创建新任务，不会自动发送。 |
| `requireDirectory` 预检时原目标不是可用文件夹，尚未开始此次复制 | 目录不存在或不是目录 | **目标文件夹不可用，请按原路径恢复后重试。** 不声称已有暂存。 |
| 提交前持有树锁重新检查，原文件夹已经不存在或不再是文件夹 | 目标目录已不存在，暂存已保留，请恢复目录后重试 | 同上。已复制的数据不删除；原文件夹恢复后仍可重试原任务。 |

[领域分类](../../domain/src/main/java/com/privateplayer/domain/model/ImportError.kt)使用以上三条**完整**正文；丢暂存还须已确认为网页来源。没有按「不存在」「丢失」「暂存」泛化。已有空间不足、来源读取或授权失效、普通 I/O、取消、未知错误的原有分类保留。失败原因和下一步在[任务行](../../app/src/main/java/com/privateplayer/media/TransferDialogs.kt)可直接读到；重复的正文不再占一个详情按钮，独立且不含敏感信息的底层诊断仍可展开。沿用 Compose / Material 3、既有浅深色令牌、文字按钮及状态播报，不改页面布局或增加教程。

## 执行边界

- [导入仓库](../../data/src/main/java/com/privateplayer/data/repository/ImportRepositoryImpl.kt)在原任务锁、树锁内重读持久状态，拒绝已丢网页暂存的重试，不清错误、不入队；同一判断让 Worker 遇到这类新失败不退避、升级前已退避的旧工作在认领前退出。其他工作的认领条件、退避、进程恢复和取消机制不变。
- [Wi-Fi 面板](../../feature/importer/src/main/java/com/privateplayer/feature/importer/WifiImportSheet.kt)对已入队的传输使用同一分类。[任务查询](../../data/src/main/java/com/privateplayer/data/webupload/WebUploadRequestHandler.kt)增加 `canRetry` 布尔值；`POST /retry` 沿用同一仓库入口，拒绝时返回 409。失败文件下载已有打开文件失败时的拒绝逻辑，未重写。
- [网页](../../data/src/main/assets/web-upload/app.js)根据状态响应同时移除不可执行的「重试保存」和「下载已接收文件」。旧服务没有布尔字段时保留原兼容行为。接收确认后照旧释放浏览器 `File`；未接收、仍持有原件的单项重传和正常批量发送都没有改。
- 没有新增删除入口。本机「放弃并清理」保留二次确认、外部源文件不变；普通取消不代替清理，已完成记录仍可移除，网页暂存不交给本机清理。目标路径及符号链接限制不变，不自动补建文件夹，也不覆盖同名文件。

## 定向验证

- Windows x64，使用本机缓存。`:app:testDebugUnitTest --tests com.privateplayer.media.ImportErrorMappingTest` 的 JUnit XML：`tests="10" skipped="0" failures="0" errors="0"`。用例以 1.3.0 的三条真实原文为输入，检查文案资源和是否能重试；反例包括本机来源、提交日志、同名文件、正式目标身份变化、取消但仍保留暂存，以及含私有路径、URI、口令的原始诊断。
- `modern-web-guidance` 1.0.1 的本机缓存 `forms` 指引要求“announce AJAX submission results via `role="status"` or `aria-live`”：网页保留原状态播报、原生文件选择和显式上传，去掉的是不可执行的旧任务控件。离线 Impeccable 对改动的 `app.js` 返回 `[]`，这不是设备验证。

设备使用 `Medium_Phone_API_37.0` 的配置和已安装镜像，另外创建空白 AVD 目录，不复制用户盘。启动前无已连接设备；本次序列 `emulator-5592`，进程带 `-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save`，客机 API 37、`STREAM_MUSIC streamVolume:0`。未运行 `connected*` 或 `clean`。共包构建及播放器验证见 [0038](0038-compact-speed-fan.md)；导入的设备命令如下：

```powershell
$adb = "$env:LOCALAPPDATA\Android\Sdk\platform-tools\adb.exe"
$s = "emulator-5592"
& $adb -s $s install -r -t app/build/outputs/apk/debug/app-debug.apk
& $adb -s $s install -r -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
$classes = "ImportTasksDialogInstrumentedTest",
  "WifiTransferPanelInstrumentedTest#acceptedUploadCannotRetryMissingStagingButCanRetryMissingFolder",
  "WifiTransferPanelInstrumentedTest#stoppedSessionRetainsCompletedFailedAndSavingRecordsWithTheirActionsAtLargeFont",
  "WebUploadPageInstrumentedTest", "ImportRecoveryInstrumentedTest",
  "LocalDiscardInstrumentedTest#activeTasksAndRetainedWebUploadsCannotBeDiscardedOrRemoved"
$filter = ($classes | ForEach-Object { "com.privateplayer.media.$_" }) -join ","
& $adb -s $s shell am instrument -w -r -e class $filter com.privateplayer.media.test/androidx.test.runner.AndroidJUnitRunner
```

两次安装均返回 `Success`，设备读数为 `versionName=1.4.0`、`versionCode=10400`。安装前核对 SHA-256：

| APK | SHA-256 |
|---|---|
| `app/build/outputs/apk/debug/app-debug.apk` | `9f54791ca3c9173c5b10137a07bf86120e0aa73ceb2b6ce8045670ca08af2b4c` |
| `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk` | `b382400e4d81f2053b5731a21d2f27bd068f61c01134efd0f7cbfdbdaffaa8d2` |

`Time: 22.478`，`OK (11 tests)`，`INSTRUMENTATION_CODE: -1`。本机 `build/reports/import-recovery-1.4.0/` 保留设备、安装、仪器结果和 `worker-http.txt`。

| 验证层 | 实际结果 |
|---|---|
| [真实 Worker、Room、HTTP](../../app/src/androidTest/java/com/privateplayer/media/ImportRecoveryInstrumentedTest.kt) | 没有网页暂存的已接收任务实际运行后为 `FAILED`，没有自动退避。为同一已失败行再送入一个真实 WorkRequest，仍为 `FAILED`，Room 整行（含时间和原因）未改。GET 的 `canRetry=false`；直接 POST 原任务 `/retry` 返回 409，没有创建该任务的命名工作。 |
| 同一夹具：目标恢复 | 用旧版锁内失败原文及完整暂存构造历史行；在文件夹仍不存在时重试，真实 Worker 在复制前保存「目录不存在或不是目录」，未自动建目录，暂存逐字节未变。GET 的 `canRetry=true`；通过设备自身 HTTP 接口在原路径建文件夹（201），再重试原 ID（202），Room 变为 `COMPLETED`，正式文件 **28 B** 与写入暂存的内容逐字节一致。 |
| [任务对话框](../../app/src/androidTest/java/com/privateplayer/media/ImportTasksDialogInstrumentedTest.kt)、[Wi-Fi 手机面板](../../app/src/androidTest/java/com/privateplayer/media/WifiTransferPanelInstrumentedTest.kt) | 使用合成状态流驱动实际 Compose 控件。两表面都显示相应恢复方法；缺暂存没有重试，缺目录可点且只派发该任务 ID。任务列表没有重复详情，同名冲突仍可展开。本机放弃确认、失败清理不误移除、普通取消及已完成移除的回归通过；另用真实仓库验证有网页暂存时不能用本机清理或移除记录。 |
| [随包 WebView](../../app/src/androidTest/java/com/privateplayer/media/WebUploadPageInstrumentedTest.kt) | 加载目标 APK 的 HTML/JS，接口状态由用例控制。缺暂存的队列行没有「重试保存」或下载；重新选择同名文件没有发送，点上传才为新任务发送一次。可恢复行有重试/下载，点击只 POST 新任务的 `/retry`。原有单项发送边界、传输完成不等于保存完成两项也通过。 |

仅在这台独占模拟器和合成数据上验证；HTTP 用例连接的是客机自己实际发布的地址，未使用实体局域网或外部网络。锁内检查的历史兼容由精确原文、UI 与历史行验证，没有为制造竞态而在复制途中删除文件夹；也没有填满存储、撤销真实 SAF 文件授权或做真人读屏测试。导入验收完成后将同一模拟器移交给 0038 的验证流程，不涉及用户设备或下载目录。
