# 0004 导入任务持久化 + WorkManager 恢复 + 字节级续传

状态：已执行
影响范围：`data`（ImportWorker / ImportRepositoryImpl）、`core:storage`、`core:database`

## 背景

视频文件常见几 GB 到几十 GB，导入时间长；应用被系统清理、用户切后台、进程被杀都可能发生在复制中途。需求要求：

- 大文件不能一次性读入内存；
- 导入中断后可恢复；
- 临时文件不得混入正式媒体目录；
- 原文件绝不被修改。

## 决策

1. **持久化任务**：`import_tasks` 表记录来源 URI、目标文件名、状态、已复制字节、错误信息。
2. **WorkManager 执行**：每个任务一个唯一工作名（`import_<taskId>`），`ExistingWorkPolicy.KEEP` 保证不并行重复。
3. **流式复制**：1 MiB 缓冲区，边复制边落库进度；结束 `fsync`。
4. **字节级续传**：恢复时按 `bytesCopied` 截断临时文件、跳过来源流对应字节继续。
5. **提交原子性**：只在完整复制 + 大小校验 + 元数据可解析后，用 `renameTo` 原子移入 `files/media`；失败保留临时文件并标记 `FAILED`。
6. **取消语义**：用户取消立即写库为 `CANCELED` 并取消 WorkManager；worker 在下一次进度检查停止并清理临时文件。
7. **启动对账**：应用启动时检查数据库中 `QUEUED/RUNNING` 的任务是否仍有对应 WorkManager 作业，缺失则重新调度。
8. **前台服务**：复制期间以前台通知展示进度（`dataSync` 类型，Android 14+ 必需）。

## 备选方案

| 方案 | 未采用原因 |
|---|---|
| 在 ViewModel 中直接复制 | 页面销毁即中断，无法跨进程恢复 |
| 前台 Service 手写 | 需要自己处理调度、重试、约束、进程重建，WorkManager 已覆盖 |
| 一次性 `Files.copy` | 无法感知进度、无法续传，异常时难以判断已复制字节 |

## 后果

- 复制过程与 UI 生命周期解耦；进程被杀后由 WorkManager 恢复。
- 数据库状态与 WorkManager 状态有双写风险，因此以数据库为准（worker 每批次校验状态，取消优先）。
- 需要在 Android 14+ 声明 `FOREGROUND_SERVICE_DATA_SYNC` 并移除 `androidx.startup` 默认初始化器，等 Hilt 注入 `HiltWorkerFactory`（见 troubleshooting/0003）。
- 若 SAF 授权在进程重启后失效，续传会失败并提示用户重新选择文件。
