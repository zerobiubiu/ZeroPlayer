package com.privateplayer.feature.home

import com.privateplayer.feature.home.fileicons.materialFileIcon
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test

class MaterialFileIconTest {
    private fun icon(name: String, path: String = name, video: Boolean = false, light: Boolean = false): Int =
        materialFileIcon(name = name, path = path, isVideo = video, light = light)

    @Test
    fun commonImportedTypes() {
        val cases = mapOf(
            "片段.MKV" to R.drawable.home_mit_video,
            "音乐.flac" to R.drawable.home_mit_audio,
            "封面.JpG" to R.drawable.home_mit_image,
            "笔记.txt" to R.drawable.home_mit_document,
            "备份.tar.gz" to R.drawable.home_mit_zip,
            "资料.pdf" to R.drawable.home_mit_pdf,
            "讲义.docx" to R.drawable.home_mit_word,
            "清单.xlsx" to R.drawable.home_mit_table,
            "课件.pptx" to R.drawable.home_mit_powerpoint,
            "发布.apk" to R.drawable.home_mit_android,
            "发布.aab" to R.drawable.home_mit_android,
            "Main.kt" to R.drawable.home_mit_kotlin,
            "配置.yaml" to R.drawable.home_mit_yaml,
            "故事.json" to R.drawable.home_mit_json,
            "代码.🔥" to R.drawable.home_mit_mojo,
        )
        cases.forEach { (name, expected) -> assertEquals(name, expected, icon(name)) }
        listOf("srt", "ass", "SSA", "vtt", "sub", "ttml", "smi", "sami").forEach { suffix ->
            assertEquals(suffix, R.drawable.home_mit_subtitles, icon("电影.zh.$suffix"))
        }
        assertNotEquals(icon("资料.pdf"), icon("说明.txt"))
    }

    @Test
    fun namesAndCompoundExtensionsTakePriority() {
        assertEquals(R.drawable.home_mit_docker, icon("Dockerfile"))
        assertEquals(R.drawable.home_mit_nodejs, icon("PACKAGE.JSON"))
        assertEquals(R.drawable.home_mit_readme, icon("ReadMe.MD"))
        assertEquals(R.drawable.home_mit_git, icon(".gitignore"))
        assertEquals(R.drawable.home_mit_typescript_def, icon("点.很多.index.D.TS"))
        assertEquals(R.drawable.home_mit_typescript, icon("index.ts"))
        assertEquals(R.drawable.home_mit_settings, icon("project.sln.DotSettings.USER"))
        assertEquals(R.drawable.home_mit_xml, icon("样本.xml.dist.sample"))
    }

    @Test
    fun parentAndDisplayNameComeFromDifferentSources() {
        val legacy = "仓库/.GitHub/00000000-0000-4000-8000-000000000001.yml"
        assertEquals(R.drawable.home_mit_github_sponsors, icon("Funding.YML", path = legacy))
        assertEquals(R.drawable.home_mit_yaml, icon("Funding.YML", path = "not.github/FUNDING.yml"))
        assertEquals(R.drawable.home_mit_yaml, icon("Funding.YML", path = ".github/其他/FUNDING.yml"))
        assertEquals(R.drawable.home_mit_nodejs, icon("package.json", path = "legacy/uuid.json"))
    }

    @Test
    fun unknownAndExtensionlessVideoUseDifferentFallbacks() {
        assertEquals(R.drawable.home_mit_file, icon("说明"))
        assertEquals(R.drawable.home_mit_file, icon("archive.pdf.ZPUNKNOWN"))
        assertEquals(R.drawable.home_mit_file, icon("PDF"))
        assertEquals(R.drawable.home_mit_file, icon("trailing.pdf."))
        assertEquals(R.drawable.home_mit_video, icon("已索引的影片", path = "legacy/uuid", video = true))
        assertEquals(R.drawable.home_mit_video, icon("未知容器.zpunknown", video = true))
        assertEquals(R.drawable.home_mit_subtitles, icon("字幕.vtt", video = true))
    }

    @Test
    fun honorsLightThemeAndLocaleIndependentCase() {
        assertEquals(R.drawable.home_mit_toml_light, icon("config.TOML", light = true))
        assertEquals(R.drawable.home_mit_toml, icon("config.toml", light = false))
        assertEquals(R.drawable.home_mit_git, icon(".GITIGNORE", light = true))
        assertEquals(R.drawable.home_mit_tsil, icon("代码.Ц"))
    }
}
