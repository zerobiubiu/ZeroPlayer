package com.privateplayer.domain.repository

import com.privateplayer.domain.model.classifyImportError
import kotlinx.coroutines.flow.StateFlow

/** 局域网文件服务状态；停止后保留本次传输记录。 */
data class WebUploadState(
    val status: WebUploadStatus = WebUploadStatus.STOPPED,
    val endpoints: List<WebUploadEndpoint> = emptyList(),
    val message: String? = null,
    val initialPath: String = "",
    val transfers: List<WebTransferRecord> = emptyList(),
)

/** 当前会话已成功监听的具体地址，不包含通配地址或绑定失败的网卡。 */
data class WebUploadEndpoint(
    val kind: WebUploadNetwork,
    val interfaceName: String,
    val url: String,
)

enum class WebUploadNetwork { WIFI, ETHERNET, HOTSPOT }

/** 本次服务开启期间接收的文件及其真实导入状态。 */
data class WebTransferRecord(
    val id: String,
    val name: String,
    val sizeBytes: Long,
    val receivedBytes: Long,
    val state: WebTransferState,
    val message: String? = null,
    val taskId: String? = null,
) {
    val failureKind = if (taskId != null && state == WebTransferState.FAILED) classifyImportError(message, isWebUpload = true) else null
}

enum class WebTransferState { RECEIVING, IMPORTING, COMPLETED, FAILED, CANCELED }

enum class WebUploadStatus { STOPPED, STARTING, RUNNING, ERROR }

/** 服务由用户显式启动和停止；目录是网页初始显示位置。 */
interface WebUploadController {
    val state: StateFlow<WebUploadState>
    fun start(directory: String)
    fun stop()
}
