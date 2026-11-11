package com.privateplayer.core.storage

import android.content.Context
import android.system.ErrnoException
import android.system.Os
import com.privateplayer.core.common.files.baseNameOf
import com.privateplayer.core.common.files.extensionOf
import com.privateplayer.core.common.files.sanitizeFileName
import com.privateplayer.core.common.files.uniqueFileName
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.IOException
import java.nio.file.FileAlreadyExistsException
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.FileVisitResult
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.StandardCopyOption
import java.nio.file.attribute.BasicFileAttributes
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * 将相对路径限制在指定媒体根目录内。校验时不跟随根目录或任意路径分量的符号链接。
 * 返回的 File 不持有文件描述符，使用方仍需处理校验后的并发更改。
 */
fun resolveFileInMediaRoot(mediaRoot: File, path: String): File {
    require(!path.startsWith('/') && !File(path).isAbsolute && '\\' !in path && '\u0000' !in path) { "非法相对路径" }
    require(!WINDOWS_ABSOLUTE_PATH.containsMatchIn(path)) { "非法相对路径" }
    require(!Files.isSymbolicLink(mediaRoot.toPath())) { "媒体根目录不能是符号链接" }
    val root = mediaRoot.canonicalFile
    var current = root
    if (path.isNotEmpty()) {
        path.split('/').forEach { part ->
            require(part.isNotEmpty() && part != "." && part != "..") { "非法相对路径" }
            current = File(current, part)
            require(!Files.isSymbolicLink(current.toPath())) { "不允许访问符号链接" }
            require(current.canonicalFile.toPath().startsWith(root.toPath())) { "路径超出媒体目录" }
        }
    }
    return current
}

private val WINDOWS_ABSOLUTE_PATH = Regex("^[A-Za-z]:/")

/** 同一私有文件系统内的真实身份，路径改名后仍保持不变。 */
data class PrivateFileIdentity(val device: Long, val inode: Long)

/**
 * 调用方必须持有私有文件树锁；应用是该目录的唯一写进程。
 * ATOMIC_MOVE 仅做同文件系统重命名，不允许跨设备时退化成大文件复制。
 */
fun moveNoOverwrite(source: File, target: File) {
    if (Files.exists(target.toPath(), LinkOption.NOFOLLOW_LINKS)) {
        throw FileAlreadyExistsException(target.path)
    }
    Files.move(source.toPath(), target.toPath(), StandardCopyOption.ATOMIC_MOVE)
}

/**
 * 应用私有存储的唯一入口。
 *
 * 目录职责（与需求文档一致）：
 * ```
 * files/
 *   media/          正式文件，绝不放缓存
 *   subtitles/      外挂字幕，按媒体 id 分目录
 *   backups/        文件树操作日志及删除暂存
 *   import-staging/ 系统导入暂存
 *   web-uploads/    网页上传暂存
 *   import-commits/ 导入提交身份日志
 * cache/
 *   thumbnails/     缩略图缓存，可随时清理
 *   temporary/      兼容旧版本尚未完成的导入暂存
 * ```
 * 正式文件与新暂存均在 files；旧缓存暂存原位保留，不做迁移复制。
 */
@Singleton
class PrivateStorage @Inject constructor(
    @ApplicationContext context: Context,
) {
    private val filesRoot: File = context.filesDir
    private val cacheRoot: File = context.cacheDir
    private val mediaTreeMutex = Mutex()
    private val importTaskMutexes = Array(32) { Mutex() }

    val mediaRoot: File = File(filesRoot, MEDIA_DIR)
    val subtitlesRoot: File = File(filesRoot, SUBTITLES_DIR)
    val backupsRoot: File = File(filesRoot, BACKUPS_DIR)
    val thumbnailsRoot: File = File(cacheRoot, THUMBNAILS_DIR)
    val temporaryRoot: File = File(cacheRoot, TEMPORARY_DIR)

    /** 串行化正式文件提交和文件树改动，避免文件与数据库路径交错。 */
    suspend fun <T> withMediaTreeLock(block: suspend () -> T): T = mediaTreeMutex.withLock { block() }

    /** 唯一工作被取消后重试时，旧执行器退出前不能再次写同一暂存。 */
    suspend fun <T> withImportTaskLock(taskId: String, block: suspend () -> T): T =
        importTaskMutexes[(taskId.hashCode() and Int.MAX_VALUE) % importTaskMutexes.size].withLock { block() }

    /** 幂等创建目录；返回是否全部就绪。 */
    fun ensureLayout(): Boolean {
        val dirs = listOf(mediaRoot, subtitlesRoot, backupsRoot, thumbnailsRoot, temporaryRoot)
        return dirs.all { it.isDirectory || it.mkdirs() }
    }

    // ---------------------------------------------------------------- 相对路径

    fun absoluteFile(relativePath: String): File = File(filesRoot, relativePath)

    fun absoluteCacheFile(relativePath: String): File = File(cacheRoot, relativePath)

    fun exists(relativePath: String): Boolean = absoluteFile(relativePath).isFile

    fun sizeOf(relativePath: String): Long = absoluteFile(relativePath).length()

    /** 数据库路径相对于 filesDir；名称可包含媒体目录下的子目录。 */
    fun mediaRelativePath(fileName: String): String = "$MEDIA_DIR/$fileName"

    /** 校验单个真实名称，不进行清洗或改名。 */
    fun validateMediaName(name: String) {
        require(name.isNotBlank() && name != "." && name != "..") { "文件名不能为空或点路径" }
        require('/' !in name && '\\' !in name && '\u0000' !in name) { "文件名包含非法字符" }
    }

    /** 解析相对于 media 的路径，不允许越界或经过符号链接。 */
    fun resolveMediaFile(path: String): File = resolveFileInMediaRoot(mediaRoot, path)

    /** 校验暂存的真实位置；即使文件已被移走，也不允许日志指向正式文件或树外路径。 */
    fun resolveImportStagedFile(file: File): File {
        val path = file.toPath().toAbsolutePath().normalize()
        return resolveStagedInRoot(path, filesRoot, cache = false)
            ?: resolveStagedInRoot(path, cacheRoot, cache = true)
            ?: throw IllegalArgumentException("来源不在应用私有暂存目录内")
    }

    private fun resolveStagedInRoot(path: Path, root: File, cache: Boolean): File? {
        val base = if (path.startsWith(root.absoluteFile.toPath())) root.absoluteFile else root.canonicalFile
        if (!path.startsWith(base.toPath())) return null
        val relative = base.toPath().relativize(path).toString().replace(File.separatorChar, '/')
        val directory = relative.substringBefore('/')
        require('/' in relative && if (cache) directory == TEMPORARY_DIR else
            directory == "import-staging" || directory == "web-uploads") { "来源不在导入暂存目录内" }
        return resolveFileInMediaRoot(base, relative)
    }

    /** lstat 不跟随符号链接；不存在或无法确认身份时明确失败，不能猜测接管同名文件。 */
    fun fileIdentity(file: File): PrivateFileIdentity = try {
        val stat = Os.lstat(file.path)
        PrivateFileIdentity(stat.st_dev, stat.st_ino)
    } catch (error: ErrnoException) {
        throw IOException("无法读取文件身份：${file.name}", error)
    }

    fun hasFileIdentity(file: File, identity: PrivateFileIdentity): Boolean =
        Files.isRegularFile(file.toPath(), LinkOption.NOFOLLOW_LINKS) && fileIdentity(file) == identity

    fun tempRelativePath(taskId: String, extension: String?): String {
        val suffix = extension?.let { ".$it" } ?: ".part"
        return "$TEMPORARY_DIR/${sanitizeFileName(taskId)}$suffix"
    }

    fun thumbnailRelativePath(key: String): String = "$THUMBNAILS_DIR/${sanitizeFileName(key)}.jpg"

    fun subtitleRelativePath(mediaId: Long, fileName: String): String =
        "$SUBTITLES_DIR/$mediaId/${sanitizeFileName(fileName)}"

    // ---------------------------------------------------------------- 导入提交

    fun createTempFileFor(taskId: String, extension: String?): File {
        ensureLayout()
        val file = absoluteCacheFile(tempRelativePath(taskId, extension))
        file.parentFile?.mkdirs()
        return file
    }

    /** 现有文件名集合（只取 media 目录下的普通文件）。 */
    fun existingMediaFileNames(): Set<String> =
        mediaRoot.listFiles()?.filter { it.isFile }?.mapTo(HashSet()) { it.name } ?: emptySet()

    fun uniqueMediaName(desiredName: String): String =
        uniqueFileName(sanitizeFileName(desiredName), existingMediaFileNames())

    /**
     * 在已持有文件树锁的提交事务内，将暂存重命名到正式目录。
     * 调用方必须先持久化源身份与目标，再移动；数据库失败时按该身份恢复暂存。
     */
    @Throws(IOException::class)
    fun commitTempFileLocked(temp: File, targetRelativePath: String): File {
        val source = resolveImportStagedFile(temp)
        if (!Files.isRegularFile(source.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("暂存文件不存在或不是普通文件")
        }
        require(targetRelativePath.startsWith("$MEDIA_DIR/")) { "目标不在媒体目录内" }
        val relative = targetRelativePath.removePrefix("$MEDIA_DIR/")
        require(relative.isNotEmpty()) { "目标不能是媒体根目录" }
        val target = resolveMediaFile(relative)
        val parentPath = relative.substringBeforeLast('/', "")
        val parent = resolveMediaFile(parentPath)
        if (!Files.isDirectory(parent.toPath(), LinkOption.NOFOLLOW_LINKS)) {
            throw IOException("目标目录不存在")
        }
        moveNoOverwrite(source, target)
        return target
    }

    // ---------------------------------------------------------------- 变更与删除

    fun deleteSubtitle(absolutePath: String): Boolean {
        val file = File(absolutePath)
        return !file.exists() || file.delete()
    }

    /** 删除某个媒体的全部外挂字幕（删除媒体时调用）。 */
    fun deleteSubtitleDir(mediaId: Long): Boolean {
        val dir = File(subtitlesRoot, mediaId.toString())
        return !dir.exists() || dir.deleteRecursively()
    }

    /** 删除缩略图缓存文件（相对 cacheDir 的路径）。 */
    fun deleteCacheFile(relativePath: String?): Boolean {
        if (relativePath.isNullOrBlank()) return true
        val file = absoluteCacheFile(relativePath)
        return !file.exists() || file.delete()
    }

    // ---------------------------------------------------------------- 字幕

    fun listSubtitles(mediaId: Long): List<File> {
        val dir = File(subtitlesRoot, mediaId.toString())
        return dir.listFiles()?.filter { it.isFile && extensionOf(it.name) in SUBTITLE_EXTENSIONS }
            ?.sortedBy { it.name.lowercase() }
            ?: emptyList()
    }

    /** 复制一个字幕到 `subtitles/<mediaId>/`，返回相对路径。 */
    @Throws(IOException::class)
    fun saveSubtitle(mediaId: Long, fileName: String, input: java.io.InputStream): File {
        val dir = File(subtitlesRoot, mediaId.toString())
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("无法创建字幕目录")
        val existing = dir.listFiles()?.mapTo(HashSet()) { it.name } ?: HashSet()
        val unique = uniqueFileName(sanitizeFileName(fileName), existing)
        val target = File(dir, unique)
        target.outputStream().use { output ->
            input.copyTo(output, DEFAULT_BUFFER_SIZE)
            output.fd.sync()
        }
        return target
    }

    // ---------------------------------------------------------------- 统计与清理

    fun freeBytes(): Long = (mediaRoot.takeIf { it.exists() } ?: filesRoot).usableSpace.coerceAtLeast(0L)

    fun directoryBytes(dir: File): Long {
        if (!Files.exists(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) return 0L
        var total = 0L
        Files.walkFileTree(dir.toPath(), object : SimpleFileVisitor<Path>() {
            override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                if (attrs.isRegularFile) total += attrs.size()
                return FileVisitResult.CONTINUE
            }
            override fun visitFileFailed(file: Path, error: IOException): FileVisitResult {
                if (error is java.nio.file.NoSuchFileException) return FileVisitResult.CONTINUE
                throw error
            }
        })
        return total
    }

    /** 清空目录内容（保留目录本身），返回释放的字节数。 */
    fun clearDirectory(dir: File): Long {
        if (!Files.isDirectory(dir.toPath(), LinkOption.NOFOLLOW_LINKS)) return 0L
        var freed = 0L
        val children = dir.listFiles() ?: throw IOException("无法读取待清理目录")
        children.forEach { child ->
            freed += directoryBytes(child)
            Files.walkFileTree(child.toPath(), object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    Files.delete(file)
                    return FileVisitResult.CONTINUE
                }
                override fun postVisitDirectory(directory: Path, error: IOException?): FileVisitResult {
                    if (error != null) throw error
                    Files.delete(directory)
                    return FileVisitResult.CONTINUE
                }
            })
        }
        return freed
    }

    /** 扫描正式媒体目录中的全部文件（用于异常恢复时核对数据库记录）。 */
    fun listMediaFiles(): List<File> =
        mediaRoot.listFiles()?.filter { it.isFile }?.sortedBy { it.name } ?: emptyList()

    fun ensureMediaExists(relativePath: String): File? =
        absoluteFile(relativePath).takeIf { it.isFile }

    /** 展示用的完整路径（详情页“文件信息”）。 */
    fun displayPath(relativePath: String): String = absoluteFile(relativePath).absolutePath

    fun extensionOfMedia(fileName: String): String? = extensionOf(fileName)

    fun baseNameOfMedia(fileName: String): String = baseNameOf(fileName)

    companion object {
        const val MEDIA_DIR = "media"
        const val SUBTITLES_DIR = "subtitles"
        const val BACKUPS_DIR = "backups"
        const val THUMBNAILS_DIR = "thumbnails"
        const val TEMPORARY_DIR = "temporary"

        val SUBTITLE_EXTENSIONS = setOf("srt", "ass", "ssa", "vtt", "sub", "ttml")

        /** 流式复制缓冲区：1 MiB，兼顾吞吐与内存。 */
        const val COPY_BUFFER_SIZE = 1024 * 1024
    }
}
