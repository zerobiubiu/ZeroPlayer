package com.privateplayer.core.common.files

/** 目录内的文件名策略：清理非法字符、保持扩展名、生成不冲突的名称。 */

private val ILLEGAL_CHARS = charArrayOf('/', '\\', ':', '*', '?', '"', '<', '>', '|', '\u0000')
private const val MAX_BASE_LENGTH = 120

/** 去掉路径分隔符与非法字符，避免把用户输入的显示名写进真实文件名时越界。 */
fun sanitizeFileName(raw: String): String {
    val cleaned = buildString(raw.length) {
        raw.forEach { ch ->
            when {
                ch in ILLEGAL_CHARS -> append('_')
                ch.code < 0x20 -> Unit
                else -> append(ch)
            }
        }
    }.trim().trimEnd('.').trim()
    if (cleaned.isEmpty()) return "未命名"
    return if (cleaned.length > MAX_BASE_LENGTH) cleaned.take(MAX_BASE_LENGTH) else cleaned
}

/** 取文件扩展名（不含点），无扩展名时返回 null。 */
fun extensionOf(fileName: String): String? {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0 || dot == fileName.lastIndex) return null
    return fileName.substring(dot + 1).lowercase()
}

/** 去掉扩展名后的主名称。 */
fun baseNameOf(fileName: String): String {
    val dot = fileName.lastIndexOf('.')
    return if (dot <= 0) fileName else fileName.substring(0, dot)
}

/** 保留原始大小写的扩展名（不含点）。 */
private fun rawExtension(fileName: String): String? {
    val dot = fileName.lastIndexOf('.')
    if (dot <= 0 || dot == fileName.lastIndex) return null
    return fileName.substring(dot + 1)
}

/**
 * 在已有文件名集合中生成不冲突的文件名：
 * `电影.mp4` -> `电影 (1).mp4` -> `电影 (2).mp4`。
 */
fun uniqueFileName(desiredName: String, existing: Set<String>): String {
    val normalized = existing.mapTo(HashSet()) { it.lowercase() }
    if (desiredName.lowercase() !in normalized) return desiredName
    // 扩展名保留原始大小写：重新命名不应意外改变用户文件的外观。
    val ext = rawExtension(desiredName)?.let { ".$it" } ?: ""
    val base = baseNameOf(desiredName)
    var index = 1
    while (true) {
        val candidate = "$base ($index)$ext"
        if (candidate.lowercase() !in normalized) return candidate
        index++
    }
}

/** 改名时保留扩展名：`旧名.mp4` + `新名` -> `新名.mp4`。 */
fun withSameExtension(newBaseName: String, originalFileName: String): String {
    val ext = rawExtension(originalFileName) ?: return sanitizeFileName(newBaseName)
    return "${sanitizeFileName(newBaseName)}.$ext"
}
