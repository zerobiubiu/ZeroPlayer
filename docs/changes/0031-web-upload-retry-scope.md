# 网页上传：重新上传只发送所点文件

状态：1.2.1（10201）兼容修复。影响范围：APK 自带的[网页上传队列](../../data/src/main/assets/web-upload/app.js)和对应 WebView 回归。未改 Android 上传服务、HTTP 接口、局域网权限、Room schema 或页面样式。

## 修复边界

之前，A 上传被拒后，选入 B 但不点批量上传，再点 A 的「重新上传」，B 也会被发送。这是用户报告的问题；没有重跑旧包。[0030](0030-wifi-upload-and-interfaces.md) 曾验证 A 因目标目录改名而收到 400、恢复后重试成功，当时没有另一个待传文件。

现在，行内「重新上传」只发送该行持有的原文件，沿用该行选定的目标目录。批量按钮只发送**点击时**已待传的文件，按原顺序处理；尚未轮到就被移除的文件不发送，中途新选的文件留到下一批。设备已接收、有任务号的「重试保存」不是重新上传。

## 真实服务：B 何时才被发送

Windows 上从已安装的 API 37 镜像创建独占临时磁盘和 `emulator-5586`，使用 `-read-only -no-window -no-audio -no-snapshot-load -no-snapshot-save`。在冷装的应用中点「通过 Wi-Fi 导入」→「启动服务」，接受系统授权；设备确认 `ACCESS_LOCAL_NETWORK` 已授予，实际监听 `wlan0` 的 `10.0.2.16:8765`。OMP 受管 Chrome 用全新的隔离页面，经宿主 `127.0.0.1:61245` 和客机 `127.0.0.1:19831` 访问这个地址。没有将应用改绑回环或对外通配地址，也没有用静态页或假接口代替服务。

A 为 `retry-A-XiNobp.txt`，选择时位于 `RetrySuite-XiNobp/Target-A-XiNobp/`；B 为 `retry-B-XiNobp.txt`，在上一级 `RetrySuite-XiNobp/` 选择。以下只统计文件上传 `POST /api/uploads`，不混入创建、改名、目录读取或任务轮询请求。

| 操作 | 本步新增上传请求（Chrome reqid） | 浏览器及设备实测 |
|---|---|---|
| 选 A，返回上一级，把 A 的空目标改名，再点批量上传 | `#16` A → **400** | A 显示「上传失败」「目录不存在或不是目录」，保留「重新上传」。 |
| 恢复目录原名，选 B，**不点批量上传** | 无 | A 仍是失败行；B 显示「等待上传」，按钮为「上传 1 个文件」。 |
| 只点 A 行的「重新上传」 | `#19` A → **202**；B **0** 次 | A 使用原目录；`GET #21` 返回 `COMPLETED`（47/47 B）。页面为「1 个待上传 · 1 个已保存」，批量按钮仍为「上传 1 个文件」。私有树仅有 A，Room 中 A **1** 条、B **0** 条。 |
| 明确点「上传 1 个文件」 | `#22` B → **202**，这是 B 的**首次且唯一**一次 POST | `GET #24` 返回 `COMPLETED`（58/58 B），页面显示「2 个已保存」。私有树恰有 A、B 两个文件；Room 各 **1** 条，A 的任务 ID 未变。 |

两次检查均从应用私有树实读文件，与宿主原文件逐字节比较；`media.db` 及 `media.db-wal` 在复制前后和验收副本上的 SHA-256 均匹配，两次副本的 `PRAGMA integrity_check` 都是 `ok`。按文件名查 `import_tasks`，已完成行的 `targetFileName` 与所选目录相符、`sizeBytes = bytesCopied`、`errorMessage IS NULL`。没有把进度达到 100% 或仅收到 202 当作保存成功。

| 文件 | 字节 | 源文件与手机文件共同的 SHA-256 |
|---|---:|---|
| A | 47 | `c90d11de6ef278757966a781d6a280f5a08cdbad2c798fc257b0a0de0255cf2d` |
| B | 58 | `7e3c5e1d96c405866597fefdd628c28fac372f89d0a004bea55787cd298cd2e0` |

[本次实测记录（JSON）](../ui-ux-audit/implementation/evidence/web-upload-retry-1.2.1.json)保留了 Chrome 请求、任务查询、两个时点的私有树、Room 记录及副本哈希。`build/reports/web-upload-retry-1.2.1/XiNobp/` 另留两次 `*-network.txt`、`*-browser.json`、`*-storage.json`、打包和仪器原始日志。

## 定向构建与设备回归

使用本机 Android Studio JBR、Android SDK 和 Gradle 缓存离线构建。生产源冻结后打目标 APK，测试源冻结后打配套测试 APK；首次 PowerShell 未把 `-Pandroid.builder.sdkDownload=false` 当作一个参数，Gradle 在选择任务时失败。加引号后使用以下命令成功，无须改工程或重新生成上轮的播放器 Hilt/KSP 绑定。

```powershell
.\gradlew.bat --offline --console=plain "-Pandroid.builder.sdkDownload=false" :app:assembleDebug
.\gradlew.bat --offline --console=plain "-Pandroid.builder.sdkDownload=false" :app:assembleDebugAndroidTest
adb -s emulator-5586 install -t app/build/outputs/apk/debug/app-debug.apk
adb -s emulator-5586 install -t app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk
adb -s emulator-5586 shell am instrument -w -r -e class 'com.privateplayer.media.WebUploadPageInstrumentedTest#rowRetryUploadsOnlyRejectedFile,com.privateplayer.media.WebDeletePageInstrumentedTest' com.privateplayer.media.test/androidx.test.runner.AndroidJUnitRunner
```

- `app/build/outputs/apk/debug/app-debug.apk`：52,849,575 B；安装版确认 `1.2.1/10201`。源码、APK 的 `assets/web-upload/app.js` 和真实页面 GET 返回的脚本均为 36,174 B、SHA-256 `7ad7f6c7ea8080c1023138c3d4652b6d70066d71307f61c19dd3d1ac99bf96be`。
- `app/build/outputs/apk/androidTest/debug/app-debug-androidTest.apk`：2,808,573 B；内含这次回归逻辑。两只已安装 APK 的 SHA-256 均与各自的构建产物一致。WebView 从**目标 APK** 的 `activity.assets` 读取正式 HTML/JS，不在测试 APK 复制生产资源。
- 两项设备回归均返回状态 0：**`OK (2 tests)`，4.805 秒**。[单项重试用例](../../app/src/androidTest/java/com/privateplayer/media/WebUploadPageInstrumentedTest.kt)用受控 400/202 捕获 WebView 的实际请求，另覆盖重试期间选文件、批量期间移除未发项和追加下一批文件。[已有删除用例](../../app/src/androidTest/java/com/privateplayer/media/WebDeletePageInstrumentedTest.kt)仍通过，覆盖共用打包网页夹具后的大整数身份与删除冲突。两项均不充当上面真实 HTTP/Room 验收的替身。

本次仅验收这一处浏览器行为；未重跑 1.2.0 的 164 项 JVM、全量仪器或 Lint，未在实体手机或外部局域网复验。未提交、推送、打标签或发版。

收尾时手机显示「服务已停止」，客机的 `8765`、`19831` 均无监听；关闭本次测试页并撤除专用转发后，宿主回环连接返回 `ECONNREFUSED`。只关闭本次 `emulator-5586`，其序列和两个启动进程均已消失；临时磁盘、源夹具和 Room 副本已删除。保留上述两只调试 APK 和验收记录。
