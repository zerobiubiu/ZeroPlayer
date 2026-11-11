# 播放退出、字幕记忆与设置二级页面

状态：已执行。影响范围：播放器生命周期、字幕选择持久化、播放页 UI 与异形屏适配、设置页结构、手势参数、数据库 v2。

## 背景

1. 打开视频后返回列表，播放仍在后台继续出声；用户认为“关闭视频”应结束播放。
2. 每次打开同一视频都要重新选字幕。
3. 设置页是单页长列表，项目多、难定位；动态取色与整体视觉不一致，需要移除。
4. 横向滑动进度有固定 30 秒上限，长视频大范围跳转不便；震动反馈无法关闭。
5. 播放器界面需要贴近参考图重排，并适配异形屏：画面可铺满，控件必须避开缺口。

## 方案

### 1. 关闭播放页即结束播放

- `PlaybackServiceStarter` 增加 `stop()`；应用层用 `ACTION_CLOSE_PLAYBACK` 启动请求，服务执行 `closePlayback()`：先释放 MediaSession 与适配器，再让协调器释放引擎（保存进度），最后撤销前台通知并 `stopSelf()`。
- `PlaybackCoordinator.closeFromPlayerUi()` 只在存在活动会话时发请求，避免空启动服务。
- `PlayerScreen` 注册 `BackHandler`，并把顶部返回按钮指向同一个 `closePlayer`：先 `viewModel.closePlayback()` 再 `popBackStack()`。
- 按主页键退到桌面不触发返回，后台播放与系统媒体会话保持原样。

### 2. 记住字幕选择

- 媒体表新增 `selectedSubtitleTrackId`：null 表示没有偏好，具体轨道 ID 表示上次选择，`off` 表示用户主动关闭字幕。
- 数据库版本 1 → 2，`DatabaseModule` 增加 `MIGRATION_1_2`（`ALTER TABLE media ADD COLUMN selectedSubtitleTrackId TEXT`），保留既有数据。
- `PlaybackSession` 首次收到非空字幕轨时按记录恢复；轨道已不存在则保持文件默认。用户切换字幕时写回媒体表（失败只提示，不打断播放）。
- 领域模型与映射同步新增字段；DAO 新增 `setSelectedSubtitleTrack`，仓库暴露 `setSelectedSubtitleTrack`。

### 3. 播放页 UI 与异形屏

- 顶部：返回、片名、画面比例、字幕（CC）、更多。更多面板收纳锁定、音轨、画面比例、旋转与引擎信息。
- 底部：`当前 / 总时长` 与剩余时间、进度条（白色滑块 + 蓝色已播）、倍速胶囊、快退、播放/暂停、快进、播放列表。
- 播放列表面板提供上一集/下一集与四种播放模式；播放/暂停图标取代原中央控件。
- 进入播放页时把 `layoutInDisplayCutoutMode` 设为 `ALWAYS`（API 30+）/`SHORT_EDGES`（API 28–29），离开时还原；顶部与底部控制条用 `WindowInsets.safeDrawing` 只避让按钮区域，视频 Surface 仍铺满整屏。

### 4. 进度滑动与震动

- 删除固定 30 秒上限。跳幅 = 位移 × 档位系数，档位由手势峰值速度决定：每 0.25 dp/ms 提升一档，最高 16×；显示刻度为 1/2/5/10/20 秒，结果对齐到刻度。
- 峰值速度由 `PlayerScreen` 在拖拽期间累计（`seekPeakVelocity`），保证手指停顿不会让预览回退。
- `GesturePreferences.hapticsEnabled`（默认开）控制 `performHapticFeedback`；设置页新增对应开关。
- 移除旧“快滑窗口 + 定时补发预览”逻辑，手势只按当前位移与峰值速度计算。

### 5. 设置页二级化

- 一级 `SettingsHubScreen`：播放、字幕、手势、显示、导入、存储、关于七个入口，右侧显示当前摘要。
- 二级 `SettingsSectionScreen`：顶部带返回，只渲染该类型的选项；路由 `settings/{section}`。
- 显示页移除动态取色，仅保留主题模式；手势页新增震动开关；字幕页提示“会记住每个视频的字幕”。

## 实施

| 文件 | 变更 |
|---|---|
| `core/database/.../MediaEntities.kt`、`PrivatePlayerDatabase.kt`、`DatabaseModule.kt`、`dao/MediaDao.kt` | 新增字幕列、版本 2 与迁移、写入查询 |
| `domain/.../Media.kt`、`model/Settings.kt`、`repository/Repositories.kt` | 新增字段、震动偏好、仓库方法 |
| `data/.../Mappers.kt`、`MediaRepositoryImpl.kt` | 字段映射与字幕写入 |
| `core/datastore/.../PreferencesSettingsRepository.kt` | 移除动态取色；新增震动读写 |
| `core/design/.../theme/Theme.kt` | 移除动态取色参数 |
| `feature/player/.../PlaybackSession.kt`、`PlayerViewModel.kt`、`PlaybackCoordinator.kt` | 关闭播放、字幕记忆 |
| `app/.../PlayerMediaSessionService.kt` | 关闭播放命令与释放顺序 |
| `feature/player/.../PlayerScreen.kt`、`PlayerOverlays.kt`、`PlayerGestures.kt`、`SeekGestureCalculation.kt` | 返回拦截、异形屏、新控件、手势重构 |
| `feature/settings/.../SettingsScreen.kt`、`SettingsViewModel.kt` | 分组二级页面、震动开关 |
| `app/.../Routes.kt`、`PrivatePlayerRoot.kt` | 设置二级路由 |
| `app/build.gradle.kts`、`docs/CHANGELOG.md` 等 | 版本 1.0.1 与文档 |

## 执行验证记录

### 执行环境

Windows 11 x64；Android 17 / x86_64 模拟器（`-read-only`，ARM64 转译）；JVM 单元测试。

### 验证结果

- `:feature:player:testDebugUnitTest --tests SeekGestureCalculationTest`：6 个用例通过（慢滑精准、速度分档、取消 30 秒上限、边界限制、零位移、无效输入）。
- 模拟器安装后：
  - 设置一级显示 7 个分组入口与实时摘要；进入「手势」「显示」二级页后返回能正确回到一级，底部导航随之恢复；显示页只剩主题模式，动态取色已移除。
  - 播放 `sample-a` 中选中外挂字幕 `zh.srt`，关闭播放页后重新打开，字幕面板显示 `zh.srt` 仍为选中状态，画面直接显示字幕，无需再次选择。
  - 播放页返回后 `dumpsys activity services` 中 `PlayerMediaSessionService` 记录为 0，`dumpsys media_session` 不再出现本应用会话；界面回到文件列表，说明引擎、会话与通知都已释放。
  - 同一距离（400 px）的横向滑动：慢滑（2 s）位置从 365,956 ms 前进约 25 秒；快滑（150 ms）前进约 300 秒，证明跳幅随速度放大且已不存在 30 秒上限。
  - 播放页控件（暂停、字幕、更多、进度条）点击生效；此前手势层盖在控件之上导致按钮点不动的问题已通过把手势限定在视频层修复。
  - 震动开关：开启时一次滑动在 `dumpsys vibrator_manager` 新增 `performHapticFeedback(constant=4)` 的 TICK 记录 22 条；关闭后同样滑动不再新增，一级页摘要同步显示「震动关」。
- 已知限制：模拟器没有异形屏与线性马达，缺口避让与震动手感需真机确认；手势速度按 `input swipe` 的合成节奏取样，真手指的加速度分布可能略有差异。

### 发行

- main 的 CI 运行 `43024794219` 通过；`v1.0.1` 标签触发 Release 运行 `43025416115`，创建 [v1.0.1](https://github.com/zerobiubiu/ZeroPlayer/releases/tag/v1.0.1)。
- 产物 `ZeroPlayer-1.0.1-arm64-v8a.apk`：SHA-256 `624619ba7cb3f4c23c5f3261e093630917b8331bf7070c9e1f7ca99f94c707f6`，与随附 `.sha256` 一致；`aapt2 dump badging` 为 `versionCode='10001' versionName='1.0.1'`、`native-code: 'arm64-v8a'`；签名证书仍是 `CN=ZeroPlayer`、SHA-256 `d18d02ab…1358cb`，可直接覆盖升级 1.0.0。
