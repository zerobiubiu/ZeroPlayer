# 正式发行包必须通过签名和包身份校验

状态：已实施。本记录说明发布前的门禁，不代表 GitHub 上已经出现新版本。

影响范围：[Release 工作流](../../.github/workflows/release.yml)、[APK 核验脚本](../../tools/verify-release-apk.sh)及仓库忽略规则。不改变应用行为、版本号或现有签名密钥。

## 为什么必须拦截

原流程缺少密钥时只发出警告；Gradle 随后仍可能构建带调试签名的 APK。对 APK 运行签名验证只证明签名有效，不等于签名者是本项目；打印 `aapt2` 的包信息也不等于核对该包是否符合标签。调试签名不能作为已安装正式版的覆盖更新。

## 当前门禁

- 推版本标签与手动发行使用同一个构建步骤。四项签名配置缺少任意一项即退出；口令不写入跨步骤的环境文件，临时密钥只留在签名构建步骤内。普通本地 `assembleRelease` 的调试签名回退未改，不得把这类包当作正式发行。
- 对将要上传的同一份 APK，先用 SDK 的 `apksigner` 验签，并要求恰好一名签名者，证书 SHA-256 与[长期公开的发行指纹](../architecture/release-pipeline.md#产物与校验)一致。缺字段、额外签名者或指纹不符均失败。
- `aapt2 dump badging` 必须给出唯一预期包名 `com.privateplayer.media`、标签所对应的版本名和版本码、唯一原生架构 `arm64-v8a`；可调试包不通过。两项工具的退出状态均单独检查，不以搜索到输出行作为成功条件。
- 全部通过后才重命名 APK、计算 `.sha256` 并创建 Release。根 [.gitignore](../../.gitignore) 同时排除 `debug.keystore`；不改本机密钥和用户媒体。

## 发布前核对

在 Windows x64 上，`git ls-files` 对 `*.keystore`、`*.jks` 和签名配置的查询没有跟踪项。以 `git check-ignore -q --no-index app/src/debug.keystore` 检查一个示例路径，退出码为 0；无需生成或读取密钥文件。正式 APK 可在本机用同一脚本复核，命令见[发行流程](../architecture/release-pipeline.md#产物与校验)。

同一工作树、Android Studio JBR、Android SDK 37 / Build-Tools 36.0.0，以本机缓存执行：

```powershell
.\gradlew.bat --offline --console=plain -Pandroid.builder.sdkDownload=false testDebugUnitTest lintDebug :app:assembleDebug :app:assembleDebugAndroidTest :app:assembleRelease
```

- `BUILD SUCCESSFUL`，39 秒，1,271 项任务（240 执行、78 取缓存、953 已是最新）。14 个模块共 32 份 JUnit XML：186 项中 2 项因 Windows 符号链接条件跳过，无失败或错误。14 份模块 lint 报告合计 0 错误、14 警告；未将跳过或复用的结果记为新执行。
- 本机正式变体 APK 为 45,110,534 字节，SHA-256 `0ebec2c22561e41417e41aeeeeadf5f19eb7ef441e25bafa368cc5165dc340a5`。脚本对该包（1.4.0 / 10400）与下载的上一正式版（各用对应版本）均退出 0。以上是本机构建的摘要，不代替 GitHub 附件自带的校验文件。
- 调试签名包、使用错误预期版本的正式旧包均退出 1；非法版本参数退出 2。从工作流读取真实构建步骤，四次各留空一项必要配置，均退出 1、没有写临时密钥，也没有启动 Gradle；不需要提供真实口令。
- 在独立 API 37 模拟器上冷启动本机正式包，实走首页、设置中的播放进度与字幕页面。通过系统文件选择器导入合成 MKV 后，任务完成，播放器实际显示已播放 `00:02`、总长 `00:16` 及「暂停」按钮。这不是实体设备或线上 APK 的验证。
