# 项目执行规则（ZeroPlayer）

通用规范沿用全局 `~/.omp/agent/AGENTS.md`。这里只写本项目特有的执行规则，说明、架构与验证细节放 `docs/`，不在此重复。

## Git

- **未经用户明确同意，只做本地 `git commit`**：不 `git push`、不打远程标签、不触发远端 CI 或 Release。
- 一个逻辑变更一个提交；提交信息用简体中文，写清做了什么、为什么。
- 不提交签名材料（`*.jks`、`keystore.properties`）、构建产物、`.markindex/`、本机 SDK 路径（`local.properties`）。

## 项目版本

- 唯一来源是 `app/build.gradle.kts` 的 `versionName` / `versionCode`；发布时由标签按 `-PversionName` / `-PversionCode` 覆盖。
- `versionCode = major × 10000 + minor × 100 + patch`，必须递增。
- 只在被发布程序的行为变化时升版本；纯文档或流水线改动不升。升版本同步更新 `docs/CHANGELOG.md`。
- 发行流程见 `docs/architecture/release-pipeline.md`。推送标签是对外动作，按上面的 Git 规则需先征得同意。

## 验证

- 日常验证：`./gradlew testDebugUnitTest lintDebug :app:assembleDebug`。需要 JAVA_HOME 指向 JDK 17+，SDK 需 Platform 37 与 Build-Tools 36.0.0。
- 静态检查跑根任务 `lintDebug`：只跑 `:app:lintDebug` 覆盖不到库模块自身的清单与 API 检查。
- 设备测试 `connected*AndroidTest` 结束时会卸载应用并清空私有数据，默认拦截；只在可丢弃模拟器上加 `-PallowDestructiveConnectedTests=true`。
- 只打包 `arm64-v8a`；x86 模拟器依赖 ARM 转译，硬件解码、异形屏缺口与震动手感需真机确认。

## 工程约束

- `core/playback/src/main/jniLibs/arm64-v8a/libplayer.so` 是第三方静态链接产物，不重建、不替换；来源、许可与重新链接材料见 `tools/mpv-static/NOTICE.txt`。
- UI 不直接访问数据库、文件系统或 MPV；播放统一走 `PlaybackEngine` 抽象，系统会话经 Media3 适配。
- `docs/` 是文档唯一入口：变更随源码一起写变更记录，新增文档回 `docs/README.md` 登记。
