package com.privateplayer.media

/** 应用级路由表。所有路由参数统一使用字符串，避免 NavType 与 ViewModel 解析不一致。 */
object Routes {
    const val HOME = "home"

    const val SETTINGS = "settings"

    const val SETTINGS_SECTION = "settings/{section}"
    fun settingsSection(section: String): String = "settings/$section"

    const val DETAIL = "detail/{mediaId}"
    fun detail(mediaId: Long): String = "detail/$mediaId"

    const val PLAYER = "player/{mediaId}"
    fun player(mediaId: Long): String = "player/$mediaId"

}
