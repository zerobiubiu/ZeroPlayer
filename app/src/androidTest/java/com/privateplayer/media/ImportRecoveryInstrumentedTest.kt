package com.privateplayer.media

import android.Manifest
import android.os.Build
import android.util.Log
import androidx.lifecycle.ViewModelProvider
import androidx.room.Room
import androidx.test.core.app.ActivityScenario
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.filters.SdkSuppress
import androidx.test.platform.app.InstrumentationRegistry
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkInfo
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.privateplayer.core.database.PrivatePlayerDatabase
import com.privateplayer.core.database.entity.ImportTaskEntity
import com.privateplayer.core.storage.PrivateStorage
import com.privateplayer.data.importer.ImportWorker
import com.privateplayer.domain.repository.WebUploadStatus
import com.privateplayer.feature.importer.ImportViewModel
import java.net.InetAddress
import java.net.NetworkInterface
import java.net.Socket
import java.net.URI
import java.nio.file.Files
import java.util.UUID
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/** 在临时模拟器上用真实 Worker、Room 和设备自身的 HTTP 端点；只创建本次 UUID 的任务和文件。 */
@RunWith(AndroidJUnit4::class)
@SdkSuppress(minSdkVersion = 37)
class ImportRecoveryInstrumentedTest {
    @Test
    fun lostStagingRejectsOldWorkAndRetryWhileRestoringTheFolderSavesTheOriginalTask() = runBlocking<Unit> {
        val instrumentation = InstrumentationRegistry.getInstrumentation()
        val app = instrumentation.targetContext.applicationContext as PrivatePlayerApplication
        check(Build.HARDWARE.contains("ranchu") || Build.HARDWARE.contains("goldfish")) { "只在临时模拟器运行" }
        for (permission in listOf(Manifest.permission.ACCESS_LOCAL_NETWORK, Manifest.permission.POST_NOTIFICATIONS)) {
            instrumentation.uiAutomation.grantRuntimePermission(app.packageName, permission)
        }
        val payload = "恢复原文件夹后保存\n".toByteArray()
        val folder = "recovery-${UUID.randomUUID()}"
        fun upload(path: String, state: String, reason: String? = null): ImportTaskEntity {
            val id = UUID.randomUUID().toString()
            return ImportTaskEntity(id = id, sourceUri = "web-upload:$id", displayName = path.substringAfterLast('/'),
                mimeType = "text/plain", sizeBytes = payload.size.toLong(), targetFileName = path, folderId = null,
                tempRelativePath = "web-uploads/$id.part", state = state, bytesCopied = payload.size.toLong(),
                errorMessage = reason, mediaId = null, createdAt = 1L, updatedAt = 1L)
        }
        val lost = upload("$folder.txt", "QUEUED")
        val waiting = upload("$folder/原件.txt", "FAILED", "目标目录已不存在，暂存已保留，请恢复目录后重试")
        val storage = PrivateStorage(app)
        val stage = storage.absoluteFile(waiting.tempRelativePath)
        val target = storage.resolveMediaFile(waiting.targetFileName)
        check(Files.notExists(stage.toPath()) && Files.notExists(target.parentFile!!.toPath()))
        val wm = WorkManager.getInstance(app)
        val db = Room.databaseBuilder(app, PrivatePlayerDatabase::class.java, PrivatePlayerDatabase.NAME).build()
        val dao = db.importTaskDao()
        val directJobs = mutableListOf<UUID>()
        suspend fun runLostWorker(): WorkInfo {
            val request = OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(workDataOf(ImportWorker.KEY_TASK_ID to lost.id)).build()
            directJobs += request.id
            wm.enqueue(request).result.get(10, TimeUnit.SECONDS)
            return withTimeout(30_000) {
                wm.getWorkInfoByIdFlow(request.id).first { info -> info != null && (info.state.isFinished ||
                    (info.state == WorkInfo.State.ENQUEUED && info.runAttemptCount > 0)) }!!
            }
        }
        suspend fun awaitWaiting(state: String, reason: String? = null) = withTimeout(30_000) {
            var row = dao.findById(waiting.id)
            while (row == null || row.state != state || row.errorMessage != reason) {
                delay(50)
                row = dao.findById(waiting.id)
            }
            row
        }
        try {
            ActivityScenario.launch(MainActivity::class.java).use { activity ->
                lateinit var imports: ImportViewModel
                activity.onActivity {
                    imports = ViewModelProvider(it)[ImportViewModel::class.java]
                    imports.startWebUpload()
                }
                try {
                    val service = withTimeout(20_000) { imports.webUploadState.first {
                        it.status == WebUploadStatus.RUNNING || it.status == WebUploadStatus.ERROR
                    } }
                    assertEquals(service.message, WebUploadStatus.RUNNING, service.status)
                    val endpoint = URI(service.endpoints.first().url)
                    assertNotNull(NetworkInterface.getByInetAddress(InetAddress.getByName(endpoint.host)))
                    fun request(method: String, path: String, expected: Int): JSONObject = Socket(endpoint.host, endpoint.port).use { socket ->
                        socket.soTimeout = 10_000
                        socket.getOutputStream().write("$method $path HTTP/1.1\r\nHost: ${endpoint.authority}\r\nContent-Length: 0\r\nConnection: close\r\n\r\n".toByteArray())
                        val response = socket.getInputStream().bufferedReader().readText()
                        assertEquals(response, expected, response.substringBefore('\r').split(' ')[1].toInt())
                        JSONObject(response.substringAfter("\r\n\r\n"))
                    }
                    dao.insert(lost)
                    val failedWork = runLostWorker()
                    assertEquals(WorkInfo.State.FAILED, failedWork.state)
                    val failed = requireNotNull(dao.findById(lost.id))
                    assertEquals("FAILED", failed.state)
                    assertEquals("上传暂存文件已丢失，请从网页重新上传", failed.errorMessage)
                    val oldBackoff = runLostWorker()
                    assertEquals(WorkInfo.State.FAILED, oldBackoff.state)
                    assertEquals(failed, dao.findById(lost.id))
                    val lostStatus = request("GET", "/api/uploads/${lost.id}", 200)
                    assertFalse(lostStatus.getBoolean("canRetry"))
                    val denied = request("POST", "/api/uploads/${lost.id}/retry", 409)
                    assertTrue(denied.getString("error").contains("从网页重新上传"))
                    assertEquals(failed, dao.findById(lost.id))
                    assertTrue(wm.getWorkInfosForUniqueWork("import_${lost.id}").get(10, TimeUnit.SECONDS).isEmpty())
                    Log.i("ImportRecovery", "缺暂存：首次=${failedWork.state}，旧退避=${oldBackoff.state}，GET canRetry=false，POST 409，无新增工作，Room 未变")

                    check(stage.parentFile!!.isDirectory || stage.parentFile!!.mkdirs())
                    check(stage.createNewFile())
                    stage.writeBytes(payload)
                    dao.insert(waiting)
                    app.importRepository.retry(waiting.id)
                    awaitWaiting("FAILED", "目录不存在或不是目录")
                    assertFalse(target.parentFile!!.exists())
                    assertArrayEquals(payload, stage.readBytes())
                    val folderStatus = request("GET", "/api/uploads/${waiting.id}", 200)
                    assertTrue(folderStatus.getBoolean("canRetry"))
                    assertEquals("目标文件夹不可用，请按原路径恢复后重试。", folderStatus.getString("errorMessage"))
                    request("POST", "/api/folders?path=&name=$folder", 201)
                    val retried = request("POST", "/api/uploads/${waiting.id}/retry", 202)
                    assertEquals(waiting.id, retried.getString("taskId"))
                    awaitWaiting("COMPLETED")
                    assertArrayEquals(payload, target.readBytes())
                    assertEquals(failed, dao.findById(lost.id))
                    Log.i("ImportRecovery", "缺目录：未恢复时 FAILED 且暂存字节相同；原路径重建 201，原任务 POST 202 → COMPLETED，目标 ${target.length()} B 逐字节一致")
                } finally {
                    activity.onActivity { imports.stopWebUpload() }
                }
            }
        } finally {
            try {
                directJobs.forEach { wm.cancelWorkById(it).result.get(10, TimeUnit.SECONDS) }
                wm.cancelUniqueWork("import_${waiting.id}").result.get(10, TimeUnit.SECONDS)
                storage.withImportTaskLock(waiting.id) {
                    storage.withMediaTreeLock {
                        Files.deleteIfExists(stage.toPath())
                        Files.deleteIfExists(target.toPath())
                        Files.deleteIfExists(target.parentFile!!.toPath())
                        dao.deleteById(waiting.id)
                    }
                }
                dao.deleteById(lost.id)
            } finally {
                db.close()
            }
        }
    }
}
