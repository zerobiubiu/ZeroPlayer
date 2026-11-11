# 0003 先完成引擎抽象与 Mock，再验证 libmpv 后接入

状态：已执行
影响范围：`core:playback`、`feature:player`、后续 MPV 接入

## 背景

播放器需求覆盖大量交互：手势、倍速、字幕/音轨切换、比例、循环模式、记忆进度、自动下一集。若一开始就与 libmpv 的 JNI 绑定，任何原生构建问题都会阻塞全部 UI 工作；而 libmpv 在当前 Windows 宿主机无法按官方脚本构建（见 `architecture/mpv-integration-feasibility.md`）。

## 决策

1. 定义 `PlaybackEngine` 接口，覆盖加载/播放/跳转/倍速/音量/轨道/比例/Surface/释放。
2. 第一阶段实现 `MockPlaybackEngine`：用真实时钟推进播放进度，完整驱动播放器 UI 与状态机，但**不解码、不渲染**。
3. 播放器界面在 Mock 后端下显示封面与“预览引擎（未接入 libmpv）”角标，明确告知能力边界，不做伪成功。
4. 提供 `MpvNativeProbe`、`CodecCapabilityProbe` 与 `MpvProbeScreen`，把“libmpv 是否随包、设备硬解能力、Surface 生命周期、引擎链路”做成可重复验证的页面。

## 备选方案

| 方案 | 未采用原因 |
|---|---|
| 直接接入 libmpv | 原生构建在 Windows 不可行；会把 UI 进度与原生工具链强绑定 |
| 用 Media3/ExoPlayer 先顶上 | 与最终技术选型（MPV）不同，播放器交互与轨道模型会被 Media3 塑形，迁移成本高 |
| 只做静态设计稿 | 需求明确要求可运行应用与真实交互 |

## 后果

- 播放器 UI、手势、队列、进度、设置全部可独立验证；接入 libmpv 时只替换引擎实现与渲染层。
- Mock 下不能验证解码/硬解/字幕渲染，这些必须在原生库接入后回归。
- 引擎角标与验证页让“未完成的部分”始终对用户透明。
