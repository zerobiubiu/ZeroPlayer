package com.privateplayer.feature.player

import android.view.KeyEvent

/** 仅播放器显示期间由宿主分发系统音量键。 */
interface PlayerVolumeKeyHost {
    fun setPlayerVolumeKeyHandler(handler: ((KeyEvent) -> Boolean)?)
}
