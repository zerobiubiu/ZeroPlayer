# 版本与发行流水线

状态：参考（现状文档，随流水线演进更新）。影响范围：标签、签名、GitHub Release 产物。

## 版本规则

- 唯一事实来源是仓库根部的 `app/build.gradle.kts`：默认 `versionName = 1.0.0`、`versionCode = 10000`。
- 发布时由流水线按标签覆盖，无需手改文件：`vX.Y.Z` → `versionName = X.Y.Z`，`versionCode = X*10000 + Y*100 + Z`。
- 因此 `versionCode` 必须随版本递增：`1.0.0 → 10000`、`1.0.1 → 10001`、`1.1.0 → 10100`、`2.0.0 → 20000`。
- 本地调试包与标签包使用同一推导规则，避免安装时被判为降级。

## 触发方式

| 事件 | 工作流 | 结果 |
|---|---|---|
| 推送 `main`、发起 PR、手动触发 | [ci.yml](../../.github/workflows/ci.yml) | 单元测试、Lint、调试包构建，归档报告 |
| 推送 `vX.Y.Z` 标签 | [release.yml](../../.github/workflows/release.yml) | 构建并签名发行包，创建 GitHub Release |
| 手动触发 `Release` 并填写版本号 | release.yml | 补发指定版本；同名标签不存在时自动创建 |

```bash
git tag v1.0.1
git push origin v1.0.1
```

## 发行工作流步骤

1. 检出仓库（`fetch-depth: 0`，用于生成发行说明）。
2. 安装 JDK 21（Temurin）、Gradle 缓存与 Android SDK：`build-tools;36.0.0`、`platforms;android-37.0`（失败时回退 `platforms;android-37`）。
3. 解析标签得到版本号与 `versionCode`，格式不是 `X.Y.Z` 直接失败。
4. 从 Secrets 还原签名密钥到 `$RUNNER_TEMP/release.jks`，导出 `ZEROPLAYER_*` 环境变量供 Gradle 使用；未配置密钥时打印警告并回退调试签名。
5. `./gradlew :app:assembleRelease -PversionName=… -PversionCode=…`。
6. `apksigner verify --print-certs` 校验签名，`aapt2 dump badging` 核对包名与原生架构，重命名为 `ZeroPlayer-<版本>-arm64-v8a.apk` 并生成 `.sha256`。
7. 以标签名创建 Release，附上 APK、校验值和自动生成的发行说明。

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

每次发行包含：

- `ZeroPlayer-<版本>-arm64-v8a.apk`：签名发行包，仅含 `arm64-v8a` 原生库；
- `ZeroPlayer-<版本>-arm64-v8a.apk.sha256`：SHA-256 校验值。

当前发行证书：`CN=ZeroPlayer, O=ZeroPlayer, C=CN`，证书 SHA-256 摘要
`d18d02ab458741b3b9cda763ff8c7771988cd6378681ac7777e8b71d0e1358cb`。签名指纹应与该值一致，否则不是本仓库发布的安装包。

```bash
sha256sum -c ZeroPlayer-1.0.0-arm64-v8a.apk.sha256
apksigner verify --print-certs ZeroPlayer-1.0.0-arm64-v8a.apk   # 需要 build-tools
```

## 约束

- 不做 ABI 拆分，也不上传 Google Play；单个通用 APK 含全部原生库。
- 流水线不跑设备测试：`connected*AndroidTest` 会卸载应用并清空私有数据，只在可丢弃模拟器上手动执行。
- 发行说明由 GitHub 按提交生成；面向用户的版本要点写在 `docs/CHANGELOG.md`。
