package com.privateplayer.core.storage

import android.content.ContentResolver
import android.content.Context
import android.content.Intent
import android.database.Cursor
import android.net.Uri
import android.provider.OpenableColumns
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.io.InputStream
import javax.inject.Inject
import javax.inject.Singleton

/**
 * SAF 文档访问封装。
 *
 * 只读使用外部 URI：不请求写权限、不修改原文件；
 * 需要跨进程重启后继续导入时，使用 `takePersistableUriPermission` 取得可持久化的读授权。
 */
@Singleton
class SafDocuments @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    data class SafFile(
        val uri: Uri,
        val displayName: String,
        val sizeBytes: Long?,
        val mimeType: String?,
    )

    private val resolver: ContentResolver get() = context.contentResolver

    fun query(uri: Uri): SafFile? {
        require(uri.scheme == ContentResolver.SCHEME_CONTENT || uri.scheme == ContentResolver.SCHEME_FILE) {
            "不支持的文件地址，请重新分享或选择文件"
        }
        if (uri.scheme == ContentResolver.SCHEME_FILE) {
            val file = readableFile(uri)
            return SafFile(uri, file.name.ifBlank { "未命名文件" }, file.length(), null)
        }
        // 部分 provider 不返回任何列，用 URI 末段兜底显示名。
        var name: String? = null
        var size: Long? = null
        runCatching {
            resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME, OpenableColumns.SIZE), null, null, null)
                ?.use { cursor: Cursor ->
                    if (cursor.moveToFirst()) {
                        val nameIndex = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                        if (nameIndex >= 0 && !cursor.isNull(nameIndex)) name = cursor.getString(nameIndex)
                        val sizeIndex = cursor.getColumnIndex(OpenableColumns.SIZE)
                        if (sizeIndex >= 0 && !cursor.isNull(sizeIndex)) size = cursor.getLong(sizeIndex)
                    }
                }
        }
        val mime = runCatching { resolver.getType(uri) }.getOrNull()
        val fallbackName = uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() }
        return SafFile(
            uri = uri,
            displayName = name ?: fallbackName ?: "未命名文件",
            sizeBytes = size?.takeIf { it >= 0L },
            mimeType = mime,
        )
    }

    /** 取得可持久化读授权；失败不影响当前会话的导入。 */
    fun takePersistableReadPermission(uri: Uri): Boolean =
        uri.scheme == ContentResolver.SCHEME_CONTENT && runCatching {
            resolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            true
        }.getOrDefault(false)

    fun openInput(uri: Uri): InputStream = when (uri.scheme) {
        ContentResolver.SCHEME_CONTENT ->
            resolver.openInputStream(uri) ?: throw IOException("来源文件不可用，请重新分享或重新选择")
        ContentResolver.SCHEME_FILE -> FileInputStream(readableFile(uri))
        else -> throw IOException("不支持的文件地址，请重新分享或重新选择")
    }

    /**
     * 外部应用可以构造任意 file URI，不能借本应用身份读取自己的私有文件或进程伪文件。
     * 这里仅验证并读取来源，绝不修改来源路径。
     */
    private fun readableFile(uri: Uri): File {
        if ((!uri.authority.isNullOrEmpty() && !uri.authority.equals("localhost", ignoreCase = true)) ||
            uri.path.isNullOrBlank()
        ) {
            throw IOException("无效的文件地址，请重新分享或重新选择")
        }
        val file = File(requireNotNull(uri.path)).canonicalFile
        val protectedRoots = buildList {
            add(File(context.applicationInfo.dataDir).canonicalFile)
            add(context.createDeviceProtectedStorageContext().dataDir.canonicalFile)
            context.getExternalFilesDirs(null).mapNotNullTo(this) { it?.parentFile?.canonicalFile }
            context.externalCacheDirs.mapNotNullTo(this) { it?.parentFile?.canonicalFile }
            context.externalMediaDirs.mapNotNullTo(this) { it?.canonicalFile }
            context.obbDirs.mapNotNullTo(this) { it?.canonicalFile }
            add(File("/proc"))
            add(File("/sys"))
            add(File("/dev"))
        }
        if (protectedRoots.any { root -> file == root || file.path.startsWith(root.path + File.separator) }) {
            throw IOException("不能导入应用私有目录或系统设备文件")
        }
        if (!file.isFile || !file.canRead()) {
            throw IOException("来源文件不可读取，请重新分享或重新选择")
        }
        return file
    }
}
