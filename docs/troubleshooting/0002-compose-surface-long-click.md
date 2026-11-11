# 0002 Material3 `Surface(onClick)` 不支持长按

状态：已解决
影响范围：`core:design`

## 现象

媒体卡片/列表行需要“单击打开、长按多选”。直接使用：

```kotlin
Surface(onClick = onClick, onLongClick = onLongClick) { ... }
```

编译报错：`None of the following candidates is applicable`。

## 原因

Material3 的 `Surface` 可点击重载只有 `onClick`（以及 `selected` 变体），没有 `onLongClick` 参数。长按属于 Foundation 的组合手势。

## 解决

在 `core:design` 提供统一修饰符：

```kotlin
@OptIn(ExperimentalFoundationApi::class)
fun Modifier.cardInteraction(onClick: () -> Unit, onLongClick: () -> Unit): Modifier =
    this.combinedClickable(onClick = onClick, onLongClick = onLongClick)
```

卡片/行组件改用 `Surface(color = ..., modifier = modifier.cardInteraction(...))`，调用处需要 `@OptIn(ExperimentalFoundationApi::class)`。

## 验证

- 媒体库网格：长按进入多选、单击打开详情；
- 列表视图同样生效；
- 文件夹/播放列表行也复用该修饰符。

## 注意

- `combinedClickable` 目前标记为实验 API，升级 Compose 后需重新确认（若转正可移除 `@OptIn`）。
- 若同时需要水面波纹与自定义 indication，需显式传入 `interactionSource`/`indication`。
