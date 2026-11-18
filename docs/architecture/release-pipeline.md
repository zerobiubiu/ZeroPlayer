# 版本与发行流水线

状态：参考（现状文档，随流水线演进更新）。影响范围：标签、签名、GitHub Release 产物。

## 版本规则

- 本地默认 `versionName` 和 `versionCode` 的唯一事实来源是 [`app/build.gradle.kts`](../../app/build.gradle.kts)；发行流程文档不另存一份当前版本号。
- 发布时由流水线按标签覆盖，无需手改文件：`vX.Y.Z` → `versionName = X.Y.Z`，`versionCode = X*10000 + Y*100 + Z`。
- 因此 `versionCode` 必须随版本递增：`1.0.0 → 10000`、`1.0.1 → 10001`、`1.0.2 → 10002`、`1.1.0 → 10100`、`2.0.0 → 20000`。
- 本地调试包与标签包使用同一推导规则，避免安装时被判为降级。

## 触发方式

| 事件 | 工作流 | 结果 |
|---|---|---|
| 推送 `main`、发起 PR、手动触发 | [ci.yml](../../.github/workflows/ci.yml) | 单元测试、Lint、调试包构建；无论成功与否，收集各模块可用的 JVM 测试 HTML/JUnit XML 和 app Lint HTML，保留 14 天 |
| 推送 `vX.Y.Z` 标签 | [Release：准备草稿](../../.github/workflows/release.yml) | 用固定密钥构建 APK，经校验后把 APK 与摘要上传到新草稿；不公开 |
| 在**同一个标签**上手动运行 `Release` | 同一工作流的公开作业 | 不构建、不取签名密钥；重新下载四项草稿附件并验签、核对重链接材料，全部符合才公开 |

先推送 `main`，等该提交的 CI 成功，并备妥与 APK 内库对应的原生材料，再单独推送该提交的附注版本标签。手动运行只接受已有的 `vX.Y.Z` 标签，不在 `main` 上补建标签，不替换旧正式版本。

## 发行工作流步骤

1. 检出仓库（`fetch-depth: 0`，用于生成发行说明）。
2. 安装 JDK 21（Temurin）、Gradle 缓存与 Android SDK：`build-tools;36.0.0`、`platforms;android-37.0`（失败时回退 `platforms;android-37`）。
3. 解析标签得到版本号与 `versionCode`，格式不是 `X.Y.Z` 直接失败。
4. 构建步骤要求四项签名配置全部非空，否则立即失败。只在该步骤内将密钥还原为临时文件并向 Gradle 提供必要变量；步骤退出时移除临时密钥。**正式 Release 不回退到调试签名。** 本机缺签名配置时仍可构建使用调试签名的 release 变体，那不是正式发行包。
5. 以标签解析出的 `versionName` 和 `versionCode` 运行 `:app:assembleRelease`。
6. [正式 APK 门禁](../../tools/verify-release-apk.sh)对同一 APK 运行 Android SDK 的 `apksigner verify --verbose --print-certs` 和 `aapt2 dump badging`：只接受唯一签名者且证书 SHA-256 与下方指纹一致、包名为 `com.privateplayer.media`、版本名/码与标签一致、仅含 `arm64-v8a` 且不可调试的包。未通过不整理或上传附件。
7. 只对通过门禁的 APK 重命名并计算 SHA-256，以已存在的版本标签创建**草稿**，保存 APK、校验文件和自动发行说明。已有同标签草稿或正式版本时拒绝覆盖。
8. 维护者确认原生材料包来自该库的原始构建，并在独立目录验证重链接、记录[库与材料指纹](../../tools/mpv-static/relink-materials.json)。将材料包和同名摘要加入该草稿（不使用 `--clobber`），补入面向用户的发行说明。
9. 在该标签上执行 `gh workflow run release.yml --ref "$tag"`。独立公开作业不接触 APK 签名密钥，也不执行归档中的脚本。[四项材料核验](../../tools/verify-release-materials.py)检查文件名、上传状态、长度、GitHub 摘要和实际下载内容，逐一核对两份校验文件，并比对已重链接材料及 APK 中唯一 `lib/arm64-v8a/libplayer.so` 的固定长度/哈希。再次运行 APK 门禁，确认标签提交、草稿及其附件未变后，才将该草稿公开。缺项或不符时维持草稿。

## 签名密钥

发行包由固定密钥签名，后续版本才能覆盖安装。密钥只存在两处：本机备份与仓库 Secrets，**不入库**。

仓库 Secrets（Settings → Secrets and variables → Actions）：

| 名称 | 内容 |
|---|---|
| `ZEROPLAYER_KEYSTORE_BASE64` | `base64 -w0 release.jks` 的输出 |
| `ZEROPLAYER_STORE_PASSWORD` | 密钥库口令 |
| `ZEROPLAYER_KEY_ALIAS` | 密钥别名，例如 `zeroplayer` |
| `ZEROPLAYER_KEY_PASSWORD` | 密钥口令 |

生成新密钥的方式（替换口令后执行）：

```bash
keytool -genkeypair -v -keystore release.jks -alias zeroplayer \
  -keyalg RSA -keysize 4096 -validity 10000 \
  -storepass '<口令>' -keypass '<口令>' \
  -dname 'CN=ZeroPlayer, O=ZeroPlayer, C=CN'
base64 -w0 release.jks   # 写入 ZEROPLAYER_KEYSTORE_BASE64
```

密钥口令丢失后无法再发布可覆盖升级的版本，请离线备份 `release.jks` 与口令。本地构建发行包时用 `keystore.properties` 或同名环境变量指向这份密钥。

## 产物与校验

从 1.4.0 起，正常对外发行提供四项附件，普通安装只需第一项：

- `ZeroPlayer-<版本>-arm64-v8a.apk`：官方签名安装包；
- `ZeroPlayer-<版本>-arm64-v8a.apk.sha256`：APK 的 SHA-256；
- `ZeroPlayer-<版本>-native-relink-arm64-v8a.tar.xz`：对应源码、实际配方、许可、JNI 对象、静态库及重链接说明，不含完整 NDK；
- `ZeroPlayer-<版本>-native-relink-arm64-v8a.tar.xz.sha256`：原生材料的 SHA-256。

当前发行证书：`CN=ZeroPlayer, O=ZeroPlayer, C=CN`，证书 SHA-256 摘要
`d18d02ab458741b3b9cda763ff8c7771988cd6378681ac7777e8b71d0e1358cb`。签名指纹应与该值一致，否则不是本仓库发布的安装包。

在下载目录执行 `sha256sum -c 校验文件名`（使用下载的完整 `.sha256` 文件名），可分别核对 APK 或原生材料。GitHub 自动附加的项目源码 ZIP/TAR 不替代这些材料。开发者可用同一 APK 门禁复核签名与包身份：

```bash
# 在仓库根目录执行；APK 指向下载的文件，VERSION、VERSION_CODE 取该标签对应值。
bash tools/verify-release-apk.sh "$APK" "$ANDROID_HOME/build-tools/36.0.0" "$VERSION" "$VERSION_CODE"
```

## 约束

- 不做 ABI 拆分，也不上传 Google Play；单个通用 APK 含全部原生库。
- 流水线不跑设备测试：`connected*AndroidTest` 会卸载应用并清空私有数据，只在可丢弃模拟器上手动执行。
- 草稿保留 GitHub 自动生成的比较链接；公开前按 `docs/CHANGELOG.md` 补充面向用户的简体中文要点和本标签的版本记录入口。
