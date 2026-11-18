# Wi-Fi 文件传输：实际监听地址与上传流程

状态：当前实现。影响范围：`domain` 的网页服务状态、`data` 的局域网发现与 HTTP 服务、`feature:importer` 的手机面板、APK 内 `data/src/main/assets/web-upload/`。不改导入数据库、端口、鉴权策略或文件树删除协议。

## 如何使用

1. 在手机目标目录点「+」→「通过 Wi-Fi 导入」→「启动服务」，按系统提示授予局域网权限。将面板中与另一端所连网络相符的地址复制到浏览器。关闭面板不等于停止；结束后在面板或通知栏停止服务。
2. 网页先显示「保存到」的目录。进入其他文件夹后再选文件，已加入队列的项目保留各自原先的目标。电脑可拖放，手机可用系统选择器多选；按「上传 N 个文件」后依次发送。
3. 浏览器传完字节、手机返回任务号、文件最终保存，是三个不同事件。只有手机报告 `COMPLETED` 后才显示「已保存」；没有可量化的保存进度时不显示百分比。确认拒收且浏览器仍持有原文件时可「重新上传」；失联且没有任务号时不自动重传，以免重复。已接收的保存失败任务仍可「重试保存」或下载暂存文件。

电脑上上传和手机目录并排，窄屏先上传再浏览；空队列不显示取消、清理等无效操作。文件的下载保留直接入口，重命名、移动和删除位于各项「管理」内。删除确认仍使用点选时的路径、类型以及字符串形式的 `device`、`inode`，不把 64 位身份转成 JavaScript 数字，也不在确认时重读并替换快照。

## 联网边界

固定 `8765`，仅明文 HTTP，没有登录或访问令牌：能连接到所示地址的设备能读、写、删除私有文件树中的内容。只在可信网络临时启用。本次是启动时发现，不在后台持续监控接口；切换网络后需要重启服务。无公网通配或 IPv6 监听，也不要求位置、root 或额外系统网络配置。

- `LanAddressDiscovery.kt` 枚举 Android 报告的网络，从各自的 `NetworkCapabilities`、`LinkProperties` 提取地址，不要求是默认网络、已验证网络或能上互联网。只接受可用的站点本地或链路本地单播 IPv4；VPN 即使同时带底层 Wi-Fi transport 也不入选。已被不可信链路占用的接口或地址不会由热点回退再引入。
- Android 36 及以上可用公开的共享网络信息确认 Wi-Fi 下游。没有可用信息时，只接受已启用、非回环、非点到点、非虚拟且明显属于 SoftAP 的接口；不把独立的 `wlan0`、`eth0` 或任意私网地址猜作热点。不识别的旧系统/OEM 接口可能不显示。
- 每个地址独立尝试绑定；只有至少一个成功才恢复一次接收日志、启动会话。各入口共用请求池、文件树与传输状态。失败的地址不出现在面板；停止会话关闭已开启的监听和已接受的连接，但不取消已入队的保存任务。

## 代码位置

| 入口 | 职责 |
|---|---|
| `domain/.../WebUploadController.kt` | `WebUploadState.endpoints` 给出会话实际绑定结果（网络类型、接口名、可复制 URL），不是待尝试的地址列表 |
| `data/.../webupload/LanAddressDiscovery.kt`、`WebUploadListeners.kt` | 局域网资格与地址去重；逐地址绑定、接收连接、单监听失效和整体关闭 |
| `data/.../webupload/WebUploadServiceController.kt`、`WebUploadService.kt`、`WebUploadRequestHandler.kt` | 代次隔离、前台通知停止、业务请求及同一次恢复；各入口不重复创建导入状态 |
| `feature/importer/.../WifiImportSheet.kt`、`data/src/main/assets/web-upload/` | 手机启停、权限与复制反馈；浏览器文件选择、目录管理和队列 |

## 实施依据

- 沿用原生 Material 3 与网页现有紫色系；网页深色取自项目 `core:design` 的暗色令牌，不引入字体、框架、CDN 或构建依赖。网页保留严格 CSP、原生文件控件、DOM 文本节点和浏览器对话框。
- 本机缓存的 `modern-web-guidance` CLI 1.0.1 离线检索并读取了 `progress`、`forms`、`html`：无可靠百分比时按 “Omit `value` for an indeterminate state” 处理；文件选择保留真实 `input` 和可见 `label`（“always associate `<label>` with its input using `for` and `id`”）；逐文件管理使用原生折叠（“place `<summary>` as the *first* child of `<details>`”）。未安装其他工具。

## 打包网页与真实传输

Windows 上使用 OMP 配置的 Chrome DevTools、Android 17（API 37）隔离模拟器 `emulator-5584`。设备端在首页启动并授予系统局域网权限，浏览器通过仅本机可达的转发访问 APK 提供的网页，不使用静态预览或伪造接口核验上传。

- 设备同有 `wlan0`（Wi-Fi，`10.0.2.16`）和 `eth0`（系统标记为蜂窝，`10.0.2.15`）。面板仅发布 `http://10.0.2.16:8765/`；设备监听表仅 `.16:8765`，无 `.15` 或通配监听。可见 `eth0` 与 RFC 1918 地址本身并不代表以太网。
- 从网页创建 `WiFi 验收` 并打开；用同一次原生多选先传 `片段 +01.txt`，再传 `空白 00.srt`。两次 POST 均返回 202。列表为空时，两条队列记录均曾显示「已接收，等待保存」；随后队列变为「2 个已保存」，目录列出 24 B 与 0 B 的两个文件。
- 窄屏浏览进入 `临时目标`，选择 `校验 重试.txt`（31 B），返回上级并将暂空的目标文件夹改名，再点上传：服务真实返回 400「目录不存在或不是目录」，页面显示「重新上传」。恢复目录原名后点一次，无需重选文件；POST 返回 202，查询先返回 `QUEUED`，再返回 `COMPLETED`，两次 `bytesCopied` 均为 31。这一对响应验证了字节收齐并不等于已保存。
- [桌面实际页面：1280×800，浅色](../assets/wifi-upload-desktop.png)；[手机视口：375×812，深色](../assets/wifi-upload-mobile.png)。窄屏 `scrollWidth` 与视口均为 375 CSS px，选择控件高 48 px，仍在首屏；桌面 Tab 可聚焦原生文件输入，标签显示实线焦点环。切换移动视口引发页面重新加载后，两条已接收任务仍可从服务器恢复查询。
- 从应用私有树实读以下长度及 SHA-256，与 Windows 源文件逐个一致。[手机的实际监听地址](../ui-ux-audit/implementation/evidence/wifi-running-1.2.0.png)与[三项完成记录](../ui-ux-audit/implementation/evidence/wifi-received-1.2.0.png)分别留证。

| `files/media/WiFi 验收/` 下相对路径 | 字节 | SHA-256 |
|---|---:|---|
| `片段 +01.txt` | 24 | `36675be6f9c5c51755ea3964c5c0d84148314ed7cba1ff5efdc30b54e6841339` |
| `空白 00.srt` | 0 | `e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855` |
| `临时目标/校验 重试.txt` | 31 | `bb0b9dff393f615fd9b4f2a06a216b05d0d76713ff191bc760cdd53fd428af49` |

设备上的 Room 主库与 WAL 只复制到验收副本，校验副本哈希后查询 `import_tasks`：该测试目录恰有三条记录，均为 `COMPLETED`，`targetFileName` 指向表中各自路径；`sizeBytes = bytesCopied`（24、0、31），`errorMessage` 均为 `NULL`。

点「停止服务」后手机显示「服务已停止」，设备监听表已无 `.16:8765`。在宿主回环转发尚未拆除时，原标签点刷新得到 `net::ERR_EMPTY_RESPONSE`，显示「未连接」和明确的上次目录提示；未把宿主代理端口是否可连接当成手机仍在监听。随后关闭测试标签、移除宿主转发及客机回环中继，`adb forward --list` 为空。

## 定向回归与边界

`data:testDebugUnitTest` 的 Wi-Fi 包 26 项全部通过（无跳过）：`LanAddressDiscoveryTest` 5 项、`WebUploadListenersTest` 7 项，原请求解析、响应及删除身份协议 14 项。真实回环 socket 用例覆盖两个监听共用一次恢复、单地址端口冲突、全失败、恢复抛错、启动中停止、已接收连接与停止交错、单个监听丢失。

同一 API 37 隔离设备的 Wi-Fi 仪器用例 **8/8 通过**：`WifiTransferPanelInstrumentedTest` 3/3（多地址只反馈所选行、同地址重启清状态、大字记录、权限拒绝后去设置恢复）；`WifiRiskAndImportTermsInstrumentedTest` 4/4（独立风险提示、两个界面的同名导入偏好和双向保存）；`WebDeletePageInstrumentedTest` 1/1（打包 WebView 网页展开管理、保留原始 64 位身份、删除冲突不误报成功）。该 WebView 契约测试使用受控列表，与上面的真实 HTTP 传输验收分开。根 `lintDebug`、调试 APK 与 AndroidTest APK 构建均通过。

模拟器只实际验证一条合格 Wi-Fi 与一条应排除的蜂窝链路；多 LAN、VPN 混合、旧系统热点回退与部分绑定失败由 JVM 模型/回环测试覆盖。并未验证各 OEM 热点命名、物理双网卡可达性、真实路由器的客户端隔离或 API 26 实机。浏览器的手机尺寸截图来自受管 Chrome 的移动视口，不冒充实体手机浏览器截图。
