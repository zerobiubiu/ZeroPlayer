package com.privateplayer.media.copy

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * 用户可见文案门禁（F-14）。
 *
 * 区分三类字符串：
 * 1. 用户可见文案：必须进入模块 `strings.xml`，经 `stringResource` / `Context.getString` 引用；
 * 2. 内部字符串：日志、诊断、异常控制流、testTag —— 允许保留中文，但必须逐条登记在 [INTERNAL_ALLOWLIST]；
 * 3. 注释与 KDoc：不属于文案，扫描时跳过。
 *
 * 扫描范围为 UI 模块（feature 各模块、core/design、app 主代码）。数据层/引擎层的错误原因文案
 * 属于后续阶段迁移范围（见 docs/changes/0015 的剩余清单），不在本门禁范围内。
 *
 * 同时执行术语黑名单（F-10）：用户可见资源中不允许出现旧叫法。
 */
class CopyResourceGateTest {

    private val repoRoot: File = findRepoRoot()

    private fun findRepoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir") ?: ".").absoluteFile
        while (dir != null) {
            if (File(dir, "settings.gradle.kts").isFile && File(dir, "feature").isDirectory) return dir
            dir = dir.parentFile
        }
        error("未找到仓库根目录（settings.gradle.kts + feature/）")
    }

    /** UI 模块：用户可见文案必须资源化。 */
    private val uiModules = listOf(
        "feature/home/src/main",
        "feature/settings/src/main",
        "feature/importer/src/main",
        "feature/detail/src/main",
        "feature/player/src/main",
        "core/design/src/main",
        "app/src/main/java",
    )

    /**
     * 内部字符串白名单：文件相对路径 -> 允许的字面量静态文本集合。
     * 这些是日志/诊断/控制流/testTag，不是用户可见文案；新增必须登记并说明理由。
     * 模板插值（dollar + 花括号）会被剔除后按静态文本比对。
     */
    private val internalAllowlist = mapOf(
        "feature/player/src/main/java/com/privateplayer/feature/player/FocusPlaybackEngine.kt" to setOf(
            "播放服务已结束", // CancellationException 控制流，不展示给用户
        ),
        "feature/player/src/main/java/com/privateplayer/feature/player/PlaybackCoordinator.kt" to setOf(
            "上一个播放实例未能释放", // IllegalStateException 内部控制流；用户可见文案已资源化
        ),
        "feature/player/src/main/java/com/privateplayer/feature/player/PlaybackSession.kt" to setOf(
            // loadStage 仅用于 dumpsys 诊断输出，不进入 UI。
            "尚未装载", "读取播放设置", "读取媒体记录", "建立同目录队列", "解析私有媒体文件",
            "发现外挂及同目录字幕", "读取会话封面", "初始化唯一引擎", "装载媒体并取得音频焦点", "已提交原生装载",
        ),
        "feature/player/src/main/java/com/privateplayer/feature/player/PlayerScreen.kt" to setOf(
            "播放队列列表", // testTag，测试定位用
        ),
        "core/design/src/main/java/com/privateplayer/core/design/components/FileActionMenu.kt" to setOf(
            "文件菜单-", // testTag 前缀（后接资源解析出的标签）
        ),
        "core/design/src/main/java/com/privateplayer/core/design/components/FileDialogs.kt" to setOf(
            "名称输入", "名称确认", "移动确认", // testTag，测试定位用
        ),
        "app/src/main/java/com/privateplayer/media/playback/PlayerMediaSessionService.kt" to setOf(
            "ZeroPlayer 单引擎播放状态：", // dumpsys 诊断输出
        ),
        "app/src/main/java/com/privateplayer/media/ImportErrorMapping.kt" to setOf(
            // 错误分类的匹配关键词（内部逻辑，非用户可见文案）；用户可见文案经 messageRes 资源化。
            "空间", "来源", "重新选择", "重新分享", "取消", "读写", "磁盘",
        ),
    )

    /** 术语黑名单（F-10）：资源文件 -> 禁用子串列表。 */
    private val termBlacklist = mapOf(
        "feature/home/src/main/res/values/strings.xml" to listOf("新建目录", "此目录为空", "删除目录", "打开目录"),
        "feature/importer/src/main/res/values/strings.xml" to listOf("生成视频缩略图", "检测重复视频"),
        "core/design/src/main/res/values/strings.xml" to listOf("删除目录", "选择目标目录", "返回上级目录"),
        "feature/settings/src/main/res/values/strings.xml" to listOf("生成视频缩略图", "检测重复视频"),
    )

    private val cjk = Regex("[\\u4e00-\\u9fff]")
    private val stringLiteral = Regex("\"(?:\\\\.|[^\"\\\\])*\"")
    private val interpolation = Regex("\\$\\{[^}]*}")

    /** 去掉块注释、行注释与字符字面量，保留字符串字面量与行结构。 */
    private fun stripCommentsAndChars(source: String): String {
        val out = StringBuilder()
        var i = 0
        val n = source.length
        while (i < n) {
            val c = source[i]
            when {
                c == '/' && i + 1 < n && source[i + 1] == '*' -> {
                    var j = source.indexOf("*" + "/", i + 2)
                    j = if (j < 0) n else j + 2
                    repeat(source.substring(i, j).count { it == '\n' }) { out.append('\n') }
                    i = j
                }
                c == '/' && i + 1 < n && source[i + 1] == '/' -> {
                    var j = source.indexOf('\n', i)
                    j = if (j < 0) n else j
                    i = j
                }
                c == '\'' -> {
                    var j = i + 1
                    while (j < n) {
                        if (source[j] == '\\') { j += 2; continue }
                        if (source[j] == '\'') { j += 1; break }
                        j += 1
                    }
                    repeat(j - i) { out.append(' ') }
                    i = j
                }
                else -> { out.append(c); i += 1 }
            }
        }
        return out.toString()
    }

    @Test
    fun `UI 模块无未登记的中文硬编码`() {
        val violations = mutableListOf<String>()
        for (module in uiModules) {
            val base = File(repoRoot, module)
            if (!base.isDirectory) continue
            base.walkTopDown().filter { it.isFile && it.extension == "kt" }.sorted().forEach { file ->
                val rel = file.relativeTo(repoRoot).invariantSeparatorsPath
                val allowed = internalAllowlist[rel].orEmpty()
                val cleaned = stripCommentsAndChars(file.readText())
                cleaned.lineSequence().forEachIndexed { index, line ->
                    for (match in stringLiteral.findAll(line)) {
                        val inner = interpolation.replace(match.value.substring(1, match.value.length - 1), "")
                        if (!cjk.containsMatchIn(inner)) continue
                        if (inner in allowed) continue
                        if (allowed.any { it.endsWith("-") && inner.startsWith(it) }) continue
                        violations += "$rel:${index + 1}: 未资源化的中文字符串 ${match.value}"
                    }
                }
            }
        }
        assertTrue(
            "发现未登记的中文硬编码（用户可见文案必须进入 strings.xml；内部字符串须在白名单登记）：\n" +
                violations.joinToString("\n"),
            violations.isEmpty(),
        )
    }

    @Test
    fun `用户可见资源无术语黑名单命中`() {
        val violations = mutableListOf<String>()
        for ((rel, banned) in termBlacklist) {
            val file = File(repoRoot, rel)
            if (!file.isFile) continue
            file.readText().lineSequence().forEachIndexed { index, line ->
                for (term in banned) {
                    if (term in line) violations += "$rel:${index + 1}: 术语违规“$term”"
                }
            }
        }
        assertTrue("术语黑名单命中：\n" + violations.joinToString("\n"), violations.isEmpty())
    }

    @Test
    fun `同名导入设置在设置页与 Wi-Fi 面板共用同一资源`() {
        // F-10：两个开关的文案只在 core/design 定义一次，两个页面都引用它。
        val design = File(repoRoot, "core/design/src/main/res/values/strings.xml").readText()
        assertTrue(design.contains("name=\"design_import_thumbnails\""))
        assertTrue(design.contains("name=\"design_import_duplicates\""))

        val settings = File(repoRoot, "feature/settings/src/main/java/com/privateplayer/feature/settings/SettingsScreen.kt").readText()
        val wifi = File(repoRoot, "feature/importer/src/main/java/com/privateplayer/feature/importer/WifiImportSheet.kt").readText()
        for (key in listOf("design_import_thumbnails", "design_import_duplicates")) {
            assertTrue("设置页未引用共享资源 $key", key in settings)
            assertTrue("Wi-Fi 面板未引用共享资源 $key", key in wifi)
        }
        // 两个模块不得再各自定义同名开关文案。
        val settingsRes = File(repoRoot, "feature/settings/src/main/res/values/strings.xml").readText()
        val wifiRes = File(repoRoot, "feature/importer/src/main/res/values/strings.xml").readText()
        assertTrue("设置页资源重复定义缩略图开关", "导入时生成缩略图" !in settingsRes)
        assertTrue("Wi-Fi 资源重复定义缩略图开关", "导入时生成缩略图" !in wifiRes)
    }
}
