package com.privateplayer.media

import android.content.Context
import dagger.hilt.android.qualifiers.ApplicationContext
import java.io.IOException
import javax.inject.Inject
import javax.inject.Singleton

/** 外部打开请求独立持久化，进程被系统回收时仍可关联导入任务及播放目标。 */
@Singleton
class ExternalOpenStore @Inject constructor(@ApplicationContext context: Context) {
    data class State(val taskId: String?, val uri: String?, val mimeType: String?, val readyMediaId: Long?)

    private val preferences = context.getSharedPreferences("external_open", Context.MODE_PRIVATE)

    fun read(): State = State(
        taskId = preferences.getString("task_id", null),
        uri = preferences.getString("uri", null),
        mimeType = preferences.getString("mime_type", null),
        readyMediaId = if (preferences.contains("media_id")) preferences.getLong("media_id", 0L) else null,
    )

    fun pending(taskId: String, uri: String, mimeType: String?) {
        val edit = preferences.edit().clear().putString("task_id", taskId).putString("uri", uri)
        if (mimeType != null) edit.putString("mime_type", mimeType)
        if (!edit.commit()) throw IOException("无法保存外部打开请求")
    }

    fun ready(mediaId: Long) {
        if (!preferences.edit().remove("task_id").putLong("media_id", mediaId).commit()) {
            throw IOException("无法保存待播放的媒体记录")
        }
    }

    fun clear() {
        if (!preferences.edit().clear().commit()) throw IOException("无法清除外部打开请求")
    }
}
