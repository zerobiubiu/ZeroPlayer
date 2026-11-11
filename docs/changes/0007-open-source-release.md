# 开源发布：图标、许可证与标签驱动的发行流水线

状态：已执行。影响范围：应用图标资源、仓库根文档、`.github/workflows`、`app/build.gradle.kts` 的版本与签名配置、公开仓库 `zerobiubiu/ZeroPlayer`。

## 背景

工程此前只在本机使用：没有版本控制、没有发行签名、图标为最初绘制的环形零与播放三角，也没有任何自动化。开源到 GitHub 后需要满足三件事：仓库可直接构建、安装包可被用户校验并覆盖升级、每次发布不依赖手工打包。

同时存在两个硬约束：

- APK 内 `libplayer.so` 静态链接 libmpv/FFmpeg（LGPL 等），分发时必须随附许可证与重新链接材料，应用层许可证必须与之相容；
- 应用私有数据、`sample-*.mp4` 等本机测试资料不能进入仓库。

## 方案

### 1. 许可证

应用代码采用 **GPL-3.0-only**：静态链接 LGPL 播放栈后再以单一 `.so` 分发，强 copyleft 是解释成本最低的相容选择，也与 mpv 生态的常见做法一致。第三方组件义务单独写在 `THIRD_PARTY_NOTICES.md`，逐项条件仍以 `tools/mpv-static/NOTICE.txt` 与各上游许可正文为准。

### 2. 图标

用户提供的 `zeroplayer_white_3d_transparent.svg`（白色立体丝带零形 + 播放负空间）作为唯一图形来源：

- 用浏览器 Canvas 以 1024×1024 栅格化，按 alpha 包围盒把图形缩放到自适应图标 108dp 画布的 66%，保证落在 72dp 安全区内；
- 输出 `drawable-{m,h,xh,xxh,xxxh}dpi/ic_launcher_foreground.png`（108/162/216/324/432 px）；
- 用 SVG 中 `zp-silhouette` 复合路径（evenodd）单独渲染白色遮罩，按 70% 缩放输出 `ic_launcher_monochrome.png`，供 Android 13+ 主题图标着色；
- 删除原 `drawable/ic_launcher_foreground.xml`、`ic_launcher_monochrome.xml`；`mipmap-anydpi-v26` 的自适应图标定义与深色底 `#20183D` 保持不变。

矢量源与 512px 预览归档在 `docs/assets/zeroplayer-icon.svg`、`docs/assets/zeroplayer-icon.png`。

### 3. 版本与签名

- 版本默认值仍在 `app/build.gradle.kts`，流水线用 `-PversionName` / `-PversionCode` 覆盖；`versionCode = major×10000 + minor×100 + patch`，与默认 `1.0.0 → 10000` 对齐，避免覆盖安装被判降级。
- 发行签名材料只来自 `keystore.properties`（本机，已 gitignore）或 `ZEROPLAYER_*` 环境变量（CI Secrets）；都没有时发行包回退调试签名，保证任意分支都能出包。
- 发行密钥 `release.jks`（RSA 4096，别名 `zeroplayer`）保存在本机 `%USERPROFILE%\.zeroplayer\`，Base64 与口令写入仓库 Secrets。

### 4. 流水线

- `ci.yml`：`main` 推送、PR、手动触发。安装 SDK 37 与 build-tools 36，执行 `testDebugUnitTest`、`lintDebug`、`:app:assembleDebug`，归档 Lint 与测试报告。
- `release.yml`：推送 `vX.Y.Z` 标签或手动指定版本触发。解析版本 → 还原签名 → `:app:assembleRelease` → `apksigner verify` 与 `aapt2 dump badging` 校验 → 重命名并生成 `.sha256` → 创建 GitHub Release。
- 两个工作流都使用运行器自带的 `cmdline-tools` 调 `sdkmanager`，不使用 `android-actions/setup-android`（其 `sdkmanager tools` 在新仓库上已不可用）。

### 5. 仓库整理

- 新增 `LICENSE`（GPL-3.0 全文）、`THIRD_PARTY_NOTICES.md`、`.gitattributes`；`.gitignore` 增加 `.markindex/`、`*.jks`、`keystore.properties`、`*.log`。
- 重写 `README.md`：定位、能力、截图、安装校验、构建、发布流程、目录结构、存储布局、限制与许可证。
- 删除只有构建产物、无源码且未被 `settings.gradle.kts` 引入的 `feature/library` 目录。
- 新增 [发行流水线](../architecture/release-pipeline.md) 现状文档，并在 `docs/README.md` 登记。
- `docs/CHANGELOG.md` 的 1.0.0 段补充开源、图标与流水线条目。

## 实施

| 文件 | 变更 |
|---|---|
| `app/src/main/res/drawable-*/ic_launcher_foreground.png`、`ic_launcher_monochrome.png` | 新增，5 个密度共 10 张，替代原矢量图标 |
| `app/src/main/res/drawable/ic_launcher_foreground.xml`、`ic_launcher_monochrome.xml` | 删除 |
| `app/build.gradle.kts` | 版本覆盖、`signingConfigs.release`、发行回退调试签名 |
| `.github/workflows/ci.yml`、`release.yml` | 新增 |
| `README.md`、`LICENSE`、`THIRD_PARTY_NOTICES.md`、`.gitattributes`、`.gitignore` | 新增或重写 |
| `docs/architecture/release-pipeline.md`、`docs/README.md`、`docs/CHANGELOG.md` | 新增与更新 |
| `docs/assets/zeroplayer-icon.svg`、`zeroplayer-icon.png`、`app-home.png`、`app-player.png` | 新增 |
| `feature/library/` | 删除（仅含构建产物） |

## 执行验证记录

### 执行环境

Windows 11 x64；Android 17 / x86_64 模拟器（`-read-only`，ARM64 转译）；GitHub Actions `ubuntu-latest`；JDK 21（CI）/ JBR 25（本机）。

### 本机

- 图标替换后 `:app:assembleDebug` `BUILD SUCCESSFUL`；安装到模拟器后桌面图标显示新的白色丝带标记（截图见下文限制）。
- 自适应图标合成预览（深色底 + 主题单色着色）确认图形居中、无裁切，单色层为实心遮罩且播放负空间正确。
- `git ls-files` 与远端递归树均为 276 个文件，体积 31.53 MiB，最大文件 `core/playback/src/main/jniLibs/arm64-v8a/libplayer.so`（25.7 MiB）。
- 暂存区 diff 级扫描未发现令牌、私钥或真实口令；`local.properties`、`.markindex/`、`build/`、`keystore.properties` 均被忽略。

### GitHub

- 仓库 `zerobiubiu/ZeroPlayer` 为 public，默认分支 `main`，描述与主题已设置。
- 首次提交 `76b01c1` 推送成功；随后两次修正流水线（`setup-android` 的 `tools` 包失败、`yes | sdkmanager --licenses` 管道退出码）与一次 `gradlew` 可执行权限修正。
- 发行 Secrets：`ZEROPLAYER_KEYSTORE_BASE64`、`ZEROPLAYER_STORE_PASSWORD`、`ZEROPLAYER_KEY_ALIAS`、`ZEROPLAYER_KEY_PASSWORD` 已写入。
- `v1.0.0` 标签触发 Release 工作流，CI 工作流在同一提交上运行。

### 结论与限制

- 图标与构建已在本机验证；实体 ARM 设备上的启动图标、主题图标与硬解仍需真机确认。
- 首次开源提交含完整历史起点，没有引入旧工程未提交内容。
