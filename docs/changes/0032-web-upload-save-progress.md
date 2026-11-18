# 网页上传：发送进度不代表手机保存进度

状态：1.2.2（10202）兼容修复。影响范围：APK 自带的[网页上传队列](../../data/src/main/assets/web-upload/app.js)和[打包网页的设备回归](../../app/src/androidTest/java/com/privateplayer/media/WebUploadPageInstrumentedTest.kt)。不改上传协议、Android 服务、导入任务或数据库。

浏览器发送文件时，已发送字节和可用的总长度可以表示**传输**进度。收到任务号后，这个比例不再适用：[接收日志入队](../../data/src/main/java/com/privateplayer/data/webupload/WebUploadJournal.kt)时就把 `sizeBytes`、`bytesCopied` 同时设为完整暂存文件的长度；[导入任务](../../data/src/main/java/com/privateplayer/data/importer/ImportWorker.kt)对网页文件直接使用该暂存，不会第二次按字节复制。

因此，手机报告 `RUNNING` 时显示「正在保存」和无 `value` 属性的不定进度条，即使字节数已齐也不显示 100%；只有 `COMPLETED` 才显示「已保存」并隐藏进度条。[1.2.1 单项重试](0031-web-upload-retry-scope.md)的发送边界不变。

## 设备回归：实际 DOM 与受控任务状态

Windows、Android Studio JBR 25.0.4、API 37、Android System WebView 145.0.7632.218。使用已有系统镜像和硬件配置创建独占临时磁盘，`emulator-5588` 以 `-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save` 冷启动；客机媒体流也已静音。源码冻结后仅执行以下一次合并构建和一次整类仪器运行：

```powershell
$env:JAVA_HOME = "$env:ProgramFiles\Android\Android Studio\jbr"
$env:ANDROID_HOME = "$env:LOCALAPPDATA\Android\Sdk"
$adb = "$env:ANDROID_HOME\platform-tools\adb.exe"
.\gradlew.bat --offline --console=plain "-Pandroid.builder.sdkDownload=false" :app:assembleDebug :app:assembleDebugAndroidTest
& $adb -s emulator-5588 install -t app/build/outputs/apk/debug/app-debug.apk
& $adb -s emulator-5588 install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
& $adb -s emulator-5588 shell am instrument -w -r -e class com.privateplayer.media.WebUploadPageInstrumentedTest com.privateplayer.media.test/androidx.test.runner.AndroidJUnitRunner
```

Gradle 返回 `BUILD SUCCESSFUL`（exit 0）。目标 APK 内含 `assets/web-upload/{index.html,app.js,style.css}`，其中 `app.js` 与源码 SHA-256 同为 `4fa203235fc9b6ecadb460b20b76737d457bccdb9a94f42dbdfc957a9ecff625`。测试 APK 的 `assets/web-upload/` 条目数为 **0**：[夹具](../../app/src/androidTest/java/com/privateplayer/media/PackagedWebPageFixture.kt)通过目标 Activity 的 `assets` 加载打包页面，仅接口响应由测试控制。两次安装均返回 `Success`：

| 包 | `versionName` / `versionCode` | 设备 `pm path` 返回的 APK |
|---|---|---|
| `com.privateplayer.media` | `1.2.2` / `10202` | `/data/app/~~3VzVvzK4PtTEmEwfqWKasg==/com.privateplayer.media-Sn3B6TbWZO_D9JAuz0uaWQ==/base.apk` |
| `com.privateplayer.media.test` | `null` / `0` | `/data/app/~~LxZPsBXEVZt0c9Z378z-Dw==/com.privateplayer.media.test-JZKrOF6LWLgO3YzQP0jEqQ==/base.apk` |

新用例让任务接口先返回 `RUNNING`，再返回 `COMPLETED`，两次都是 `bytesCopied = sizeBytes = 1`。下表是同次设备运行的 `WebUploadProgress` 日志，数据来自实际队列行，而不是按源码填写的预期值。`value` 是 `progress.getAttribute('value')`，`null` 表示没有这个属性；“可见”取自 `getClientRects()`。

| 取样点 | 行 `data-state` / 状态文字 | 进度行 `hidden` | 进度条可见 | `value` | 行内百分比 |
|---|---|---|---|---|---|
| 浏览器开始发送 | `uploading` / 正在传输 | `false` | `true` | `"0"` | 传输进度 0% |
| 手机 `RUNNING`（1/1 B） | `importing` / 正在保存 | `false` | `true` | `null` | 无，也没有「已保存」 |
| 手机 `COMPLETED`（1/1 B） | `completed` / 已保存 | `true` | `false` | `null` | 无 |

`com.privateplayer.media.WebUploadPageInstrumentedTest` 的两项均收到 `INSTRUMENTATION_STATUS_CODE: 1`（开始）和 `0`（通过）：`savingWithAllBytesReceivedRemainsIndeterminateUntilCompleted`、`rowRetryUploadsOnlyRejectedFile`。原始结果为 `Time: 6.426`、`OK (2 tests)`、`INSTRUMENTATION_CODE: -1`；宿主 `adb` 进程退出码为 `0`。本机构建目录 `build/reports/web-upload-save-progress-1.2.2/88b5a16ee2a5/` 保留 `instrumentation.txt` 和 `webview-dom.txt`。

本次验证打包网页的渲染与重试边界，不是实际 LAN 传输或慢网测量：没有启动上传服务、建立转发或另开浏览器，没有重跑旧版门禁。仅关闭本次实例，确认其序列、进程和临时磁盘已移除；保留上述两个 debug 包。
