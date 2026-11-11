# MediaSessionLifecycle 测试契约更新

状态：已执行。影响范围：仅 `app/src/androidTest/java/com/privateplayer/media/MediaSessionLifecycleInstrumentedTest.kt`。

## 背景

B-01 修复后完整仪器化套件为 30/31：唯一失败为 `serviceAndControllerSharePlaybackAcrossBackgroundAndActivityRecreation`，断言「退出页面后重开不重载」超时，真实控制器 `connected=false`。

根因是产品行为已变更：1.0.1 起关闭播放页会停止播放服务并释放会话；重开创建新会话并从断点恢复。测试仍按旧模型断言服务持续、控制器复用。属于测试契约过时，不是编译或环境问题。

## 契约调整

| 场景 | 旧断言 | 新断言 |
|---|---|---|
| 返回文件树 | 服务继续 | 服务停止（`isServiceRunning=false`） |
| 旧控制器 | 保持连接 | 断开后释放 |
| 重新打开 | 同一会话不重载 | 新服务 + 新控制器 + 同一媒体 |
| 断点 | 位置连续 | 关闭前暂停并定位 8s，保存后重开位置 ≥6.5s |
| playCount | 重开后仍 1 | 重开真实播放后 2 |
| 第一段结束 | 1 | 2 |
| 再次返回 | 服务继续 | 停止并断开控制器 |
| 损坏文件 | 旧控制器读错误 | 重新绑定控制器等待真实 `playerError` |

## playCount 语义

`PlaybackSession.persistProgress`：每次装载 `playCountRecorded` 初始为 false；`hasPlayed` 后首次保存 `playIncrement=1`，同会话后续保存为 0。关闭旧会话只保存进度不计数；重开新会话真实播放后再次 +1，因此预期 2，非机械改值。

## 稳定性

- 全部使用条件等待：服务状态、控制器连接、DB 断点、playCount。
- 关闭前暂停并 seek 到 8 秒，等待 DB 落盘后再返回。
- 重开先等服务运行，再建新控制器，避免断开/启动竞态。
- 错误媒体直接绑定控制器，避免错误后前台服务释放导致误判。
- 未增加固定 Sleep。

## 验证

| 验证 | 结果 |
|---|---|
| `:app:assembleDebugAndroidTest` | 成功 |
| 会话测试定向 | 1/1 通过 |
| 完整 `connectedDebugAndroidTest` | 31/31 通过，0 failure/error/skipped |
| `testDebugUnitTest` | 76 tests，2 skipped，0 failure |
| `:app:assembleDebug` | 成功 |
| `lintDebug` | 0 error，10 warning（app） |

## 边界

- 未修改生产代码、UI、Token、翻译或配置。
- 未提交/推送 Git。
- 该测试依赖真实 MediaSession 与锁屏模拟，仍只在可丢弃模拟器运行。
