# 启动图标使用原生矢量

状态：1.3.0（10300）变更记录。影响范围：Android 普通、圆形启动图标及原有的主题单色层。沿用 [0028 的桌面设计](0028-speed-picker-and-launcher-icon.md#桌面图标)，只替换安装包的图层载体，不重新设计 Logo。

## 保留的是手机上原来显示的图

- 用户提供的 `zeroplayer_white_3d_transparent.svg` 与[仓库原稿](../assets/zeroplayer-icon.svg)是同一份文件：均为 10,159 字节，SHA-256 为 `b6c17b5ee87e43b8a0e80610f67fa86b3e3302d81162e9564b41ed0ccfd62a2b`。本地只读解析得到 11 条 `path`、2 个 `clipPath`、2 个线性和 3 个径向渐变；无位图、滤镜、mask、脚本或外部资源引用。下载目录中的文件未修改。
- 0028 的桌面图不是把整张灰阶原稿栅格化。它取原稿的 `zp-silhouette`（外轮廓及三角播放孔）、`upper-ribbon`、`zp-bottom-clip`，用 `return-silk`、`upper-silk`、`lower-silk` 三条线性渐变画出折面。新图保留这三条渐变的方向、全部 10 个色标及偏移；不额外叠加原稿中的灰阶径向光或边线。
- 图层仍为 **108 × 108 dp**，原稿中 869 单位映射为 **58 dp**，光学锚点 `(607,624)` 落在 `(54,54)`，等价于 `translate(54 54) scale(58/869) translate(-607 -624)`。中央 72 dp 可见窗口、深紫底 `#20183D`、白色折带及透出背景的播放孔不变，圆角仍由桌面遮罩决定。

## 资源与维护

[清单](../../app/src/main/AndroidManifest.xml)继续指向 `mipmap/ic_launcher` 及 `mipmap/ic_launcher_round`。两份 `mipmap-anydpi-v26` 自适应定义均未改动：背景仍为原颜色，前景与单色分别解析为 [`ic_launcher_foreground.xml`](../../app/src/main/res/drawable/ic_launcher_foreground.xml) 和 [`ic_launcher_monochrome.xml`](../../app/src/main/res/drawable/ic_launcher_monochrome.xml)。最低版本为 API 26，没有可达的 API 25 及以下 legacy 入口。未新增启动代码、依赖或独立的通知图标，未改网页图标、README 预览和启动窗口背景。

[专用离线导出器](../../tools/render-launcher-icon.cjs)只提取上述三条路径，不加载或执行 SVG。`node tools/render-launcher-icon.cjs <临时输出根目录>` 可复算两份 XML；Gradle 打包已提交的原生资源，不运行此脚本。以前五种密度各一份的前景和单色 PNG 不再参与 Android 资源选择。

## 验证

- 基线为修改前的 **1.2.3** 调试 APK，52,849,539 字节，SHA-256 `bb61984518de7f40add607ebeab6971d03aa01ec6be376edf381044a600a2fb8`。从 APK 解出的 108 / 162 / 216 / 324 / 432 px 的前景与单色 PNG，与改动前源资源的解码 RGBA **10/10 逐像素一致**。
- 在 Windows x64 使用本机缓存的 resvg 2.6.2，以同一画布和 AA 规则分别重放旧导出器的几何/渐变、从新 XML 读取的几何/渐变。中央 72 dp 视窗的 **24、48、216、432 px** 以及上述五档完整图层，RGBA 均逐像素一致。全尺寸完整图层与旧包真实 PNG 跨渲染器比较，预乘 RGB 各通道平均绝对差为 **0.126–0.517 / 255**。这是桌面离线复算，不是 Android 渲染证明。

### 安装包与原生实绘

在同一 API 37、420 dpi 的独占模拟器上先绘制已安装的 1.2.3，再覆盖安装 1.3.0。通过 `PackageManager.getApplicationIcon()`、两个 `mipmap`、`AdaptiveIconDrawable.getForeground()/getMonochrome()` 取真实 Drawable，以原生 `Canvas` 画到透明的 24 / 48 / 216 / 432 px 位图。整图保持系统遮罩；独立图层使用 `(-S/4,-S/4,5S/4,5S/4)` 的边界，对准同一 72 dp 窗口。这是安装图标的系统渲染，**不是桌面截图**。

[安装图标新旧对照 PNG](../assets/launcher-vector-1.3.0/installed-comparison.png)：上排 1.2.3，下排 1.3.0。三列分别为 24 px（最近邻放大 4 倍）、48 px（3 倍）、216 px（原大小）。两排尺度、位置、系统遮罩相同。

| 画布 | 彩色外轮廓包围盒，旧 → 新（像素，含端点） | 墨量变化 | 重心偏移 Δx / Δy（px） | 纯透明播放孔，旧 → 新（像素数） |
|---:|---|---:|---:|---:|
| 24 | `(5,2)–(18,21)`，相同 | −1.26% | +0.103 / +0.018 | 39 → 33 |
| 48 | `(11,5)–(37,42)` → `(10,5)–(37,43)` | +2.46% | −0.001 / −0.018 | 151 → 151 |
| 216 | `(47,21)–(168,195)`，相同 | −0.11% | −0.019 / −0.001 | 3,234 → 3,511 |
| 432 | `(95,43)–(337,390)`，相同 | −0.39% | −0.049 / −0.004 | 12,916 → 14,338 |

外框以 alpha ≥ 128 取样；墨量为 `Σ(alpha/255)`，重心按 alpha 加权；纯透明孔只计内部相连的 alpha = 0 区域。216 px 按同一半透明阈值看播放孔，**前后均为 3,637 像素，内孔包围盒也完全相同**。外边和洞边的中间 alpha（16–239）像素由 1,597 降至 566；对照图中放大后的外缘较清楚，没有观察到新增整圈暗边、视觉变胖或折面漏底。24 / 48 / 216 / 432 px 带遮罩整图的预乘 RGB 平均绝对差分别为 4.588 / 1.856 / 1.107 / 1.449（满量程 255），不宣称两个栅格化管线逐像素相同。

48 px 安装图的 `(2,24)` 及孔中央 `(24,24)` 前后均为 `#20183D`；上折面 `(20,10)` 前后均为 `#FCFCFE`。左侧回折面 `(13,24)` 从 `#D1D5E2` 到 `#D0D4E1`，下折面 `(24,38)` 从 `#D4D8E5` 到 `#D5D9E5`。独立图层的孔中央四档均完全透明。

### 修正一处单色迁移瑕疵

第一次导出将单色层画成一次纯白剪影。Android 渐变前景在折面与抗锯齿剪影交界处有多次覆盖，单次白色填充不能复用其完整覆盖率：24 px 的 `(11,2)` 两层 alpha 为 180 与 117，48 px 的 `(37,19)` 为 191 与 127。这是**格式迁移**引起的问题，不是源稿瑕疵。中间版改成与彩色相同绘制顺序的三个白色折面，误差虽已很小，但仍非逐像素相同。

最终用同一个模板输出**完全相同的渐变、路径和裁剪**，只在单色版的 `<vector>` 根节点增加 `android:tint="#FFFFFFFF"`，以默认的 `SRC_IN` 在整图上着白，不另造一套单色绘制。

| 彩色与单色的 alpha 差（量程 255） | 24 px | 48 px |
|---|---:|---:|
| 修正前：最大 / 全图平均 | 63 / 4.396 | 64 / 2.205 |
| 中间版（纯白折面）：最大 / 全图平均 | 1 / 0.056 | 2 / 0.028 |
| 最终包（整图着色）：最大 / 全图平均 | **0 / 0** | **0 / 0** |

在最终安装包上，将未施加额外主题色的单色图与彩色前景对照，24 / 48 / 216 / 432 px 的 alpha 差异像素分别为 **0/576、0/2,304、0/46,656、0/186,624**，所有可见单色像素均为白色。四档普通、圆形与 `PackageManager` 图像逐像素一致，两入口单色也一致；修单色前后的彩色前景及整图 RGBA 一直相同。上方对照图已与最终安装样本逐字节核对。

另以两个新取出的单色 Drawable 在同一 48 px 画布作对照，一个沿用根白色，另一个调用 `setTint(0xFF368CAD)`；**2,304 个像素的 alpha 全部一致，633 个不透明像素全部变为 `#368CAD`**。证实主题能覆盖根着色，不把独立画布当成已开启主题图标的桌面。

### 打包和回归边界

- 图标定稿时覆盖安装并取样的 1.3.0 调试 APK：53,684,274 B，SHA-256 `835948b7406b39306cdff4947b35d74cb0284ef3b9aae7cb02ec2cb6244a6fb4`。`aapt2 dump xmltree <apk> --file AndroidManifest.xml`、`aapt2 dump resources <apk>` 及两个自适应 XML 确认普通/圆形仍指向原入口，共用同一底色和两份新图层。两份 `res/drawable/ic_launcher_*.xml` 的编译根节点均为 `vector`，单色根含 `tint=#ffffffff`。设备实际类型从两层 `BitmapDrawable` 变为两层 `VectorDrawable`。
- 旧包的 10 张启动 PNG 共占 60,125 B；最终包中两个 vector 与 AAPT 从各层内联提取的 **6 个渐变 XML**，共 8 个 ZIP 条目，压缩数据合计 **5,397 B**，不含资源表、ZIP 条目头及未变的自适应入口。没有 `ic_launcher_*.png/webp` 命中。不能用同版其他功能的 APK 总大小反推此项节省。
- 图层指纹独立于播放器代码：将这 8 项按 ZIP 路径排序，每项写入 `路径<TAB>未压缩条目的 SHA-256<LF>`（UTF-8、小写十六进制），合并文本的 SHA-256 为 `22d0f2d4090d6f04f26d4ce492f83d2729124d3eb3bb6115f3e3db7f29d551e7`。普通和圆形自适应入口的编译 XML 各自 SHA-256 均为 `be9e7aa05d9c984752f66c59eede49502e6a59b5ebfd84f4fd83e853f104bce0`。
- 后续合入 Media3 会话修补的 1.3.0 调试包为 53,684,274 B，SHA-256 `ede201105d8b6a8fb3555941f8a0fd09fa38700235348889208c91f709bc464a`。再次核对上述 8 项汇总及两个自适应入口的编译字节，指纹全部相同；沿用同一图标实绘证据，没有为无关的播放器修改重拍。
- 图标单项复现命令：`adb -s emulator-5594 shell am instrument -w -r -e class com.privateplayer.media.LauncherIconInstrumentedTest com.privateplayer.media.test/androidx.test.runner.AndroidJUnitRunner`。中间修正包单独执行 `OK (1 test)`；整图着色的安装包实际执行 [`installedAdaptiveIconsShareColorAndMonochromeArtwork`](../../app/src/androidTest/java/com/privateplayer/media/LauncherIconInstrumentedTest.kt)，方法返回 `INSTRUMENTATION_STATUS_CODE: 0`（`build/reports/speed-fan-1.3.0/device-delivery.log:9–15`）。这里只记录图标用例，不把它当作其它用例或全套测试均通过。
- 不声明验证过所有厂商桌面、真机、Android 8 或每一种主题色。代码仍由系统在目标尺寸栅格化；消除的是五档固定位图作为缩放输入的分辨率限制，不是取消抗锯齿本身。
