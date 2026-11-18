# 关闭播放会话：先清理待执行命令，再公布取消

范围：1.4.0 发布前修复，涉及 [Media3 命令队列](../../core/playback/src/main/java/com/privateplayer/core/playback/session/MpvMedia3Player.kt)和[并发回归用例](../../core/playback/src/test/java/com/privateplayer/core/playback/session/SerialEngineCommandQueueTest.kt)。不改签名、存储、版本号或 libmpv。

## 远端实际失败

首次 [main CI](https://github.com/zerobiubiu/ZeroPlayer/actions/runs/44351049711) 在 `:core:playback:testDebugUnitTest` 报告 24 项中 1 项失败：`release cancels a command waiting for the shared gate`，`java.lang.AssertionError at SerialEngineCommandQueueTest.kt:176`。该行期望倍速票据丢弃回调已经执行一次；前一条「Future 已取消」断言没有失败。日志没有给出实际计数，且没有可下载的测试报告，不将某个计数或超时当成 CI 实测。

## 两种关闭路径

- 命令正在等待切源门时，旧代码先公布 Future 取消，再清理命令。Future 的等待者可以比取消监听更早恢复；即使监听采用直接执行器，等待者也不能据此断定票据已清理。
- 切源门也可能正常返回而不抛取消异常：队列已关闭时，门内检查正确地没有调用引擎，但旧队列随后仍以成功终结该命令。与此不同，队列仍开放、只是媒体已经切换的旧命令应继续作为无操作正常完成。

队列主动取消时，统一先用已有的一次性回调丢弃票据，再在 `finally` 中通知 Future 取消。不使已实际执行的动作变为取消，也不改客户端主动取消和媒体切换的原有语义。用于测试的锁和门闩不进入生产路径。

## 定向验证

Windows x64、Android Studio JBR、既有 SDK/Gradle 缓存；没有重新执行设备或无关根测试。对同一份新测试做了一次反证和一次修复后回归：

- 旧生产代码配新测试：`--offline :core:playback:testDebugUnitTest --tests '*SerialEngineCommandQueueTest'`，13 项中 2 项失败。卡住共享门时，丢弃回调内观察到 Future 已终结；让旧门正常返回时，等待者没有收到预期的 `CancellationException`。不是用延时或多次重跑制造失败。
- 修复后：`--offline :core:playback:testDebugUnitTest :app:assembleRelease` 一次成功。模块 2 份 JUnit XML，25/25 通过（含同一组 13/13 的队列测试），无失败、错误或跳过。受控门用例先执行新队列的命令，再让旧门返回；旧动作从未执行，新会话没有被旧队列阻塞。
- 本机重建正式变体为 45,110,534 字节，SHA-256 `e65311be7c73c9a85454c77a68bf29cebccbdae11c87f773300cb193db8b7178`；正式门禁退出 0，SDK 核对唯一官方签名、应用 ID、1.4.0 / 10400、仅 `arm64-v8a`、不可调试。该包替代 [0039](0039-official-release-verification.md) 中修复前的本地候选，不代表 GitHub 附件字节。
