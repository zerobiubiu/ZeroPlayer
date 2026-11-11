# 手势层盖住播放器按钮时点击失效

状态：已解决。影响范围：播放器页控件点击、手势识别层级。

## 现象

播放器界面控件正常显示，但点击顶部返回/字幕/更多、底部播放暂停与进度条都没有反应；双击视频区域、滑动调整进度却正常。`adb shell dumpsys window` 显示焦点仍是 `MainActivity`，没有残留对话框。

## 原因

`playerGestures` 的 `pointerInput` 挂在整个播放器根 `Box` 上，而控件是同一个 `Box` 的子节点。手势识别在拿到 DOWN 后立即 `consume()`，子节点虽然先收到事件，但在部分点击路径上（尤其与 `AnimatedVisibility` 动画同时进行时）会被父级消费影响，表现为“画面能识别手势，按钮不响应”。

把控件放在带手势识别修饰符的容器内部，等价于让“全屏手势”和“按钮点击”争抢同一组指针事件；视觉层级正确，不代表事件分发顺序正确。

## 处理

把手势识别下移到只包住视频画面的内层 `Box`，控件层作为兄弟节点叠加在上面：

```kotlin
Box(Modifier.fillMaxSize()) {
    Box(Modifier.fillMaxSize().playerGestures(...)) {
        PlayerVideoLayer(...)   // SurfaceView
        if (isLoading) LoadingIndicator()
    }
    BoxWithConstraints(Modifier.fillMaxSize()) {
        PlayerTopBar(...)
        PlayerBottomControls(...)
    }
}
```

手势 lambda 提前提取成局部 `handleGesture`，避免在两个修饰符之间复制大段逻辑。空白区域没有 pointerInput，事件自然落到底层视频手势，仍可在任意位置滑动或双击。

## 验证

- 修复前：点击暂停、字幕、更多均无响应（截图前后画面与图标不变）。
- 修复后：暂停图标切换为播放、字幕面板弹出并可切换 `zh.srt`、更多面板弹出；横向滑动与双击跳转保持正常。
- `dumpsys window` 焦点无对话框，排除弹窗遮挡。

## 结论

全屏手势修饰符不要套在包含可点击控件的容器上；把可交互控件放到手势层的兄弟层，或给控件区域单独做 `pointerInput` 拦截。判断“点击失效”时先看事件层级，不要先怀疑无障碍或坐标。
