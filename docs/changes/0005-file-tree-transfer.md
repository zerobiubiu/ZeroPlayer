# 私有文件树、传输与播放器交互

- 状态：已执行
- 影响范围：首页、文件操作、导入、网页服务、播放器交互
- 前置变更：[0004 ARM64 单架构](0004-arm64-only.md)
- 相关决策：[0006 私有文件树取代逻辑归档](../decisions/0006-private-file-tree.md)

## 背景

用户要求去掉独立媒体库、收藏与播放列表，把首页改为文件和目录；本机与网页导入均应服务同一文件树。网页传输需要支持任意格式、下载和管理；用户还要求系统打开/分享导入、文件导出/分享，以及改进滑动、字幕、倍速和音量反馈。

## 方案

- 保留应用 ID、旧数据库和既有文件；以 `files/media` 作为首页与网页共同使用的真实多级文件树。旧 UUID 文件只在展示时使用原名，不自动改名。
- 首页和设置为一级导航；点击视频播放，长按操作菜单进入详情或执行文件操作。首页加号提供本机选择器与 Wi-Fi 服务浮层。
- Wi-Fi 服务固定 8765 端口、根路径、无令牌或登录；网页与应用使用相同的文件树仓库，支持任意格式和空文件。服务期间任何能连接的设备均可修改或删除文件。
- 本机/分享使用只读 URI，网页使用流式接收；暂存与任务持久化、完整数据同步、同文件系统原子改名及提交身份日志保护大文件。原始文件不移动、不修改、不删除。
- 文件树操作同步数据库并记录操作日志；旧任务的缓存暂存和完成标记不被清缓存误删。同目录同名字幕用于播放，旧字幕保留。
- 播放器使用快扫步进、慢滑微调、虚拟进度与触觉反馈；字幕放在视频下侧，音量键只显示百分比。临时倍速请求移出主线程，正常播放界面不常驻显示引擎名称。
- 原生引擎预置 libmpv 自带的 `scaletempo2` 滤镜，避免 1× 与临时倍速切换时自动滤镜反复创建和销毁；代价是常速播放也经过该滤镜，真机功耗与听感仍需复核。
  固定源码中的 `filters/f_auto_filters.c` 在速度跨过 1× 时切换自动滤镜，`filters/f_output_chain.c` 优先使用用户显式指定的变速滤镜；这解释了预置方案的作用范围，并不能证明所有设备都无卡顿。

## 实施文件

- 导航和系统入口：`app/src/main/AndroidManifest.xml`、`MainActivity.kt`、`PrivatePlayerRoot.kt`、`Routes.kt`、`ExternalIntentViewModel.kt`、`ExternalOpenStore.kt`。
- 分享、导出及任务反馈：`FileTransferViewModel.kt`、`PrivateFileProvider.kt`、`TransferDialogs.kt`、`app/src/main/res/xml/share_paths.xml`。
- 文件树：`domain/.../FileTreeRepository.kt`、`data/.../repository/FileTreeRepositoryImpl.kt`、`data/.../repository/OtherRepositories.kt`、`core/database/.../MediaDao.kt`、`core/storage/.../PrivateStorage.kt`。
- 导入：`ImportRepositoryImpl.kt`、`ImportWorker.kt`、`ImportStaging.kt`、`ImportTaskDao.kt`、`ImportCopier.kt`、`SafDocuments.kt`、`feature/importer/.../ImportViewModel.kt`。
- 网页服务：`data/.../webupload/`、`data/src/main/assets/web-upload/`、`domain/.../WebUploadController.kt`、`feature/importer/.../WifiImportSheet.kt`。
- 页面：`feature/home/`、`feature/detail/`、`feature/settings/`；移除 `feature/library/` 和独立导入页、旧 MPV 验证页及对应导航。
- 播放交互：`feature/player/.../PlayerGestures.kt`、`PlayerScreen.kt`、`PlayerOverlays.kt`、滑动计算代码、`PlayerVolumeKeyHost.kt`，以及 `core/playback/.../MpvPlaybackEngine.kt`。
- 测试：`data/src/test/`、`feature/home/src/test/`、`feature/player/src/test/`，以及 `app/src/androidTest/` 中的文件树、续传、详情/设置和 libmpv 回归。
- 文档：`README.md`、`docs/README.md`、`docs/CHANGELOG.md`、`docs/architecture/`、`docs/decisions/0006-private-file-tree.md`。

## 后续验证建议与已知限制

- 在真实 ARM64 设备回归硬件解码、触觉反馈、不同字幕和长时间播放；模拟器转译与软解不能替代真机。
- 非持久化外部 URI 的读取授权若在尚未复制完成时失效，需要重新选择或重新分享；不能伪造已恢复的授权。
- 临时网页服务为 HTTP 明文且没有认证，传输结束后应立即停止，只在可信局域网使用。
- 文件树日志面向进程中断恢复；不依赖 Android 禁止的硬链接，也不会在提交时二次复制大文件。不能保证突然断电时目录项已经持久化。

## 执行验证记录

- 执行环境：Windows 11 x64；Android 17 / x86_64 只读模拟器，使用 ARM64 转译。设备测试不调用会卸载应用的 Gradle connected 任务。
- `node --check data/src/main/assets/web-upload/app.js`：退出码 0。
- 定向 JVM 测试报告：`core:common` 11 项、`data` 27 项、`feature:home` 6 项、`feature:player` 5 项、`feature:settings` 3 项；其中 `data` 的两个 Windows 符号链接用例被跳过，其余通过。符号链接边界另有 Android 仪器测试；另见会话变更中的 22 项播放适配测试。
- 首轮真实设备回归暴露 Android SELinux 拒绝 `{ link }`，随后移除硬链接方案；提交改为持锁的原子改名、dev/ino 身份日志及补偿。未跳过该失败场景。
- 音量键实际截图显示“音量 40%”，系统媒体音量为 6/15；未显示系统音量条。真实 libmpv 临时 2×→1× 回归通过，期间位置推进且视频帧持续变化；不能据此断言真机逐帧零卡顿。
- Android 文件树一致性回归 20 项通过，覆盖空文件、3 GB 稀疏文件改名且 inode 不变、提交前后中断、数据库回滚、同名异物、旧缓存暂存与路径边界。
- `ExternalImportInstrumentedTest`：`OK (1 test)`，16.42 秒；真实 `ACTION_VIEW` 先复制再播放，随后真实 `onNewIntent` 接收 `ACTION_SEND_MULTIPLE` 字幕和空文件，副本字节与原始 URI 内容一致。多 Intent 场景使用官方 Instrumentation 按 Activity 实例跟踪销毁，避免 ActivityScenario 按旧 Intent 过滤生命周期回调。
- HTTP 实测上传字幕、无扩展名与零字节文件，下载逐字节校验，创建目录、改名、移动、删除及越界拒绝均通过。浏览器实测选择字幕、上传、下载、重命名、目录选择移动和确认删除；390 px 窄屏无横向溢出。
- 超 2 GiB 实测：流式上传并流式下载 **2,147,483,649 字节**，SHA-256 均为 `1eca19bba205eab07444801f4cce130dff7a7e8f7ee526cbda8ed170c8fd215c`，端到端 66.691 秒；未将完整大文件读入内存。浮层记录显示“已完成 · 2.0 GiB”，释放测试文件后容量恢复；停止服务后原地址连接失败。
- 实际触屏：慢滑出现“目标 00:06 / +3 秒 / 微调”的虚拟条，底部实际进度仍为 00:03，抬手后才变为 00:06。100 ms 短扫使真实暂停中的 libmpv 位置由 3,667 ms 变为 13,667 ms，恰好前进 10 秒；底部进度条另可跳至 9,750 / 16,000 ms。
- `dumpsys vibrator_manager` 记录应用 `performHapticFeedback(constant=4)` 的 TICK 已完成；这证明系统触觉调用，不替代实体设备手感验证。同目录字幕发现及视频下侧黑边像素回归通过。
- 全部设备验收后，原有 `sample-a.mp4` 的 SHA-256 仍为 `9c6a35bcac5a76e1a86558bfb2aa1d080430057d0f5a178a5e849ef3eb6e1af9`，`sample-big.mp4` 仍为 `a184500b1781723a587f7f023f7aeaf2b4211b601fe83eb7624a594114d66f48`；未卸载被测应用或改写原有媒体。
