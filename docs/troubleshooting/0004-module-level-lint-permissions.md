# 模块级 Lint 只看本模块清单

状态：已解决。影响范围：`data` 等库模块的运行清单声明；Lint 在 CI 全量任务中报错。

## 现象

本机只跑 `:app:lintDebug` 时 0 错误；CI 执行根任务 `lintDebug` 时 `:data:lintDebug` 失败：

```text
WebUploadServiceController.kt:201: Error: Missing permissions required by
ConnectivityManager.getActiveNetwork: android.permission.ACCESS_NETWORK_STATE [MissingPermission]

ImportWorker.kt:305: Error: Missing dataSync foregroundServiceType in the
AndroidManifest.xml [SpecifyForegroundServiceType from androidx.work]
```

## 原因

`android.permission.ACCESS_NETWORK_STATE` 与 `androidx.work.impl.foreground.SystemForegroundService`
的 `foregroundServiceType="dataSync"` 只写在 `app/src/main/AndroidManifest.xml`。清单合并后运行期没有问题，
但 Lint 分析库模块时只读取该模块自身的清单，因此把代码用法判定为缺少声明。

`lintDebug` 在根项目执行会遍历所有模块；只跑 `:app:lintDebug` 覆盖不到库模块自身的检查，容易漏报。

## 处理

在 `data/src/main/AndroidManifest.xml` 声明本模块实际用到的能力，合并时与 app 的同名声明去重：

```xml
<uses-permission android:name="android.permission.ACCESS_NETWORK_STATE" />
<application>
    <service
        android:name="androidx.work.impl.foreground.SystemForegroundService"
        android:foregroundServiceType="dataSync"
        tools:node="merge" />
</application>
```

`tools:node="merge"` 必需，否则与 app 模块的服务声明冲突。

## 验证

- 本机 `.\gradlew.bat lintDebug`（根任务，全模块）：修复前 `Lint found 1 error`，补齐声明后 `BUILD SUCCESSFUL`。
- CI 的 `Lint` 步骤此后通过；`testDebugUnitTest` 与 `:app:assembleDebug` 在同一工作流中顺序执行。

## 结论

库模块一旦直接调用系统 API，就在自己的清单里声明依赖；本地验证静态检查时跑根任务而不是单模块任务。
