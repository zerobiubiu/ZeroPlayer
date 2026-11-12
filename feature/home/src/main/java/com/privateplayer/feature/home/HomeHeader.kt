package com.privateplayer.feature.home

/**
 * 首页头部纯逻辑（F-01 标题去重 / F-24 面包屑）。
 *
 * 标题为当前文件夹名（根目录用调用方传入的根标签）；面包屑只含祖先路径，
 * 不含当前文件夹（当前文件夹已是标题），避免“文件/文件”重复。
 */

/** 当前目录的标题：根目录返回 [rootLabel]，否则返回最后一段文件夹名。 */
fun homeTitle(currentDirectory: String, rootLabel: String): String =
    if (currentDirectory.isEmpty()) rootLabel else currentDirectory.substringAfterLast('/')

/** 面包屑段（名称 to 路径）：根段 + 祖先，不含当前文件夹。 */
fun breadcrumbSegments(path: String, rootLabel: String): List<Pair<String, String>> {
    val parts = path.split('/').filter(String::isNotEmpty)
    return buildList {
        add(rootLabel to "")
        var current = ""
        parts.dropLast(1).forEach { part ->
            current = if (current.isEmpty()) part else "$current/$part"
            add(part to current)
        }
    }
}
