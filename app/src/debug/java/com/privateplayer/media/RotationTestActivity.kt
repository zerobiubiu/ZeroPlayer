package com.privateplayer.media

import androidx.activity.ComponentActivity

/**
 * 测试宿主：与 MainActivity 一样自行处理方向/尺寸配置变更，
 * 旋转时不重建 Activity，Compose 层级得以保留（仅存在于 debug 测试包）。
 * 播放队列的真实播放测试也用它承载 Surface 与面板，避免引入第二个测试宿主。
 */
class RotationTestActivity : ComponentActivity()
