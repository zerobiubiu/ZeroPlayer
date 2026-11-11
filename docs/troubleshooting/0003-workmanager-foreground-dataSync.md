# 0003 WorkManager 导入在 Android 14+ 的前台服务与 Hilt 初始化顺序

状态：已解决（记录两次真实故障）
影响范围：`app` 清单、`data`（ImportWorker）、Application

## 故障一：Worker 无法创建

现象（logcat）：

```text
WM-WorkerFactory: Could not instantiate com.privateplayer.data.importer.ImportWorker
WM-WorkerFactory: java.lang.NoSuchMethodException: ImportWorker.<init> [Context, WorkerParameters]
WM-WorkerWrapper: Worker result FAILURE
```

任务在 UI 上一直停留在“排队中”，数据库状态未更新。

### 原因

`androidx.startup` 的 `InitializationProvider` 在 `Application.onCreate()` **之前**初始化 WorkManager，此时 Hilt 还没有把 `HiltWorkerFactory` 注入到 Application 字段，WorkManager 使用了默认工厂，无法创建 `@HiltWorker`。

### 解决

在 `AndroidManifest.xml` 移除默认初始化器，让 WorkManager 在首次 `getInstance` 时按 `Configuration.Provider` 初始化（此时 Hilt 注入已完成）：

```xml
<provider
    android:name="androidx.startup.InitializationProvider"
    android:authorities="${applicationId}.androidx-startup"
    android:exported="false"
    tools:node="remove" />
```

`PrivatePlayerApplication` 实现 `Configuration.Provider`，把 `HiltWorkerFactory` 注入到 `workManagerConfiguration`。

## 故障二：Android 14+ 前台服务类型缺失

若在前台服务启动时未声明类型，会抛 `ForegroundServiceStartNotAllowedException` / `MissingForegroundServiceTypeException`，导入被系统终止。

### 解决

1. 清单声明权限：

```xml
<uses-permission android:name="android.permission.FOREGROUND_SERVICE" />
<uses-permission android:name="android.permission.FOREGROUND_SERVICE_DATA_SYNC" />
```

2. 合并 WorkManager 前台服务声明类型：

```xml
<service
    android:name="androidx.work.impl.foreground.SystemForegroundService"
    android:foregroundServiceType="dataSync"
    tools:node="merge" />
```

3. Worker 使用带类型的 `ForegroundInfo`：

```kotlin
ForegroundInfo(id, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
```

4. Android 13+ 申请 `POST_NOTIFICATIONS`（拒绝只会导致通知不可见，不影响导入）。

## 验证

- 导入 2 个小文件 + 1 个 385 MB 文件，全部 `COMPLETED`，数据库 `bytesCopied` 与磁盘大小一致；
- 导入损坏文件：状态 `FAILED` 且提示“无法解析视频信息”，重试后仍失败，移除后临时文件被清理；
- 前台通知在导入期间出现（受系统通知权限影响）。

## 备注

- 移除 `InitializationProvider` 也会禁用其他 Startup 初始化器（如 ProfileInstaller），对本项目无功能影响；若未来引入依赖 Startup 的库，需要显式初始化。
