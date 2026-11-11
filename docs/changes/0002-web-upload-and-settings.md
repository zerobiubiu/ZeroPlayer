# 0002 局域网网页上传与设置页输入

状态：已执行  
影响范围：导入、私有存储、设置页面、Android 权限与 APK 打包  
版本：0.2.0（versionCode 2）

## 背景

在现有 SAF 导入之外，允许用户手动启动服务，在同一局域网的浏览器中上传视频；网页和服务必须随 APK 打包。设置页的枚举项改为下拉菜单，数字项改为手动输入。

## 方案

- `WebUploadController` 暴露服务状态；导入页提供启动、停止、地址复制和错误反馈。服务以用户显式启动的 `dataSync` 前台服务运行，不自动重启。
- 服务绑定当前非蜂窝网络或热点的私有 IPv4 地址，随机选择端口；每次启动生成 128 位随机令牌，放在访问 URL 的 fragment 中，请求时由网页放入 `X-Upload-Token`。同时验证 Host、Origin，不开放跨源访问。
- 网页、脚本、样式存入 APK 的 `assets/web-upload/`，没有 CDN、外部字体、额外原生库或运行时资源下载。网页按用户选择顺序上传，分开显示文件传输进度和实际导入状态。
- 使用固定 1 MiB 缓冲流式接收大文件，校验文件扩展名、64 位长度及剩余空间，先写 `files/web-uploads/<任务 ID>.part` 并同步到磁盘，再插入持久化的 Room 任务并交给 WorkManager。
- 已接收的文件复用现有重复检测、媒体元数据、缩略图和媒体库入库流程，不再复制一遍大文件。任务提交阶段以事务协调取消和入库，重启时能识别已移动的任务文件并恢复。
- 当前网络传输连接中断时清理未完成文件，需在网页重新上传；已接收且进入队列的任务可在进程重启后恢复。服务重新开启前，旧链接与令牌不可用。
- 设置页的主题、网格密度、画面比例、字幕背景及颜色使用 Material 3 `DropdownMenuItem`；默认倍速、长按倍速、跳转秒数、字幕字号及延迟步长使用页面内数字输入框，点击保存或键盘完成后持久化。输入越界时显示原因并禁用保存。
- Android 17 / targetSdk 37 需在用户启动服务时请求 `ACCESS_LOCAL_NETWORK`，拒绝授权时展示可重试的错误信息。
- `gradle.properties` 关闭 KSP 增量处理：此前一次增量构建成功但 APK 的 Hilt 聚合树缺少页面 ViewModel，安装后启动崩溃；干净重建后验证绑定及运行。

## 实施文件

- `app/src/main/AndroidManifest.xml`、`app/build.gradle.kts`、`gradle.properties`：局域网及前台服务权限、服务声明、版本与构建配置。
- `domain/src/main/java/com/privateplayer/domain/repository/WebUploadController.kt`：服务状态和操作契约。
- `data/src/main/java/com/privateplayer/data/webupload/`：HTTP 请求解析、上传服务器、前台服务及状态控制器。
- `data/src/main/assets/web-upload/{index.html,app.js,style.css}`：APK 内静态网页。
- `data/src/main/java/com/privateplayer/data/importer/{ImportStaging,ImportWorker}.kt`、`data/src/main/java/com/privateplayer/data/repository/ImportRepositoryImpl.kt`、`data/src/main/java/com/privateplayer/data/di/DataModule.kt`：网页暂存、原有导入队列与依赖注入。
- `core/database/src/main/java/com/privateplayer/core/database/dao/{ImportTaskDao,MediaDao}.kt`：任务状态条件更新及提交恢复查询。
- `feature/importer/src/main/java/com/privateplayer/feature/importer/{ImportScreen,ImportViewModel,WebUploadPanel}.kt`：服务面板、权限请求和状态显示。
- `feature/settings/src/main/java/com/privateplayer/feature/settings/{SettingsScreen,SettingsInputs}.kt`：下拉菜单及数字输入。
- `data/src/test/`、`feature/settings/src/test/` 及对应模块 `build.gradle.kts`：协议边界和数值校验测试。
- `README.md`、`docs/README.md`、`docs/CHANGELOG.md`、`docs/architecture/{overview,storage-and-import,feature-status}.md` 和本文：使用说明与变更记录。

## 执行验证记录

### 环境

- Windows 11 10.0.26300 x64；Android Studio JBR 25.0.4；Gradle 9.6.0 / AGP 9.5.1。
- Android 模拟器：Android 17、API 37、x86_64。
- 浏览器通过本机到模拟器的测试转发访问应用实际 HTTP 服务；未验证两台物理设备之间的网络。

### 命令与实际结果

| 项目 | 验证命令或操作 | 实际输出与结论 |
|---|---|---|
| 构建和测试 | `gradlew :feature:settings:testDebugUnitTest :data:testDebugUnitTest :app:assembleDebug --no-build-cache` | `BUILD SUCCESSFUL`；HTTP 协议 5 项、数字边界 3 项，0 失败 |
| 静态资源 | 检查 APK 的 `assets/web-upload/` | 包含 `index.html`、`app.js`、`style.css`；网页资源无外部 HTTP 地址 |
| 授权 | 首次启动时拒绝，再次点击并允许局域网权限 | 拒绝时页面显示授权失败及重试；允许后状态为运行中 |
| 网页 | 打开应用服务的真实网页，选取视频并点击开始 | 浏览器显示 `传输进度 100%`、`导入成功`，无网页错误 |
| 安全 | 向服务发无令牌、跨源及非视频扩展名请求 | 分别返回 HTTP 403、403、400；停止服务后连接失败；重启后旧令牌返回 403 |
| 大文件 | 流式上传 123,871,592 字节视频 | HTTP 202，任务最终 `COMPLETED` |
| 中断和取消 | 主动断开部分上传；低电量约束下取消已接收排队任务 | 未完成传输临时文件清理；排队任务变为 `CANCELED`，私有暂存为空 |
| 进程恢复 | 低电量约束保持已接收任务为 `QUEUED`，强停应用、恢复电量后启动 | 任务变为 `COMPLETED`，`bytesCopied = sizeBytes = 42100`；网页服务保持停止 |
| 重复文件 | 再次上传同一内容 | `CANCELED`，提示已存在相同文件 |
| 设置选择 | 下拉选择画面比例 `16:9`，再恢复原值 | 菜单展示真实选项并即时更新，未出现勾选式底部清单 |
| 数字输入 | 输入越界 `9`、自定义倍速 `1.35`、跳转秒数 `17` | 越界显示提示且禁用保存；`1.35` 重启后保留；`17` 保存成功；测试后恢复原值 |
| 清理 | 使用应用自身删除验收视频及其导入记录 | 仅删除本次创建的数据；模拟器保留原有媒体与记录 |

### 已知限制与后续验证建议

1. 网页传输不加密，令牌也可能被同网监听者截获；仅在可信局域网开启，用后立即停止，不分享地址。
2. 网络传输断开、页面刷新或服务关闭时，未完成的上传需重新开始；已收到完整文件并持久化的导入任务可恢复。
3. 当前一次仅接收一个文件，网页多选按顺序处理。后台处理尚未完成时不应关闭手机电源或清理应用数据。
4. Android 15 及以上的 `dataSync` 前台服务在应用处于后台时受系统时限和电量管理约束；服务被系统终止后需用户重新启动。
5. 已验证 Android 17 模拟器及本机浏览器经测试转发的端到端流程；尚需在至少一台 ARM 真机和同一 Wi-Fi 的另一台物理设备上验证，包括热点、AP 客户端隔离和厂商省电策略。
6. MPV 真实解码仍未接入，本次不改变播放后端。
7. APK 的 `lib/arm64-v8a/` 仍包含既有 AndroidX 的 `libandroidx.graphics.path.so` 和 `libdatastore_shared_counter.so`，这些文件随 APK 打包，不需另行安装，但严格的“APK 内完全没有动态库”尚未实现；移除它们需先验证 Compose/DataStore 的各版本兼容性，不能直接排除后宣称可用。
