package com.privateplayer.feature.home.fileicons

import androidx.annotation.DrawableRes
import com.privateplayer.feature.home.R
import java.util.Locale

/** 专名（包括父目录限定）优先，再从最长复合后缀向后匹配；不检查磁盘或文件内容。 */
@DrawableRes
internal fun materialFileIcon(name: String, path: String, isVideo: Boolean, light: Boolean): Int {
    val key = name.lowercase(Locale.ROOT)
    val named = MaterialIconAssociations.named(key, path, light)
    if (named != 0) return named

    // 只需看发行包中最长后缀的窗口；多点文件名也不会创建任意多段字符串。
    var dot = -1
    var parts = 0
    for (i in key.lastIndex downTo maxOf(0, key.lastIndex - MaterialIconAssociations.MAX_SUFFIX_LENGTH)) {
        if (key[i] == '.') {
            dot = i
            if (++parts == MaterialIconAssociations.MAX_SUFFIX_PARTS) break
        }
    }
    while (dot >= 0 && dot < key.lastIndex) {
        val icon = MaterialIconAssociations.extension(key.substring(dot + 1), light)
        if (icon != 0) return icon
        dot = key.indexOf('.', dot + 1)
    }
    return if (isVideo) R.drawable.home_mit_video else R.drawable.home_mit_file
}

/** 扩展的限定规则只看直接父目录。叶名由上面的展示名提供，而不是旧媒体的物理 UUID。 */
internal fun hasIconParent(path: String, parent: String): Boolean {
    val end = path.lastIndexOf('/')
    val start = end - parent.length
    return start >= 0 && (start == 0 || path[start - 1] == '/') &&
        path.regionMatches(start, parent, 0, parent.length, ignoreCase = true)
}
