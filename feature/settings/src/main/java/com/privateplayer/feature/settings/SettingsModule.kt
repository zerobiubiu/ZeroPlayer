package com.privateplayer.feature.settings

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent

/** 应用版本名提供者；设置页据此展示版本信息。 */
fun interface AppVersionNameProvider {
    fun versionName(): String
}

@Module
@InstallIn(SingletonComponent::class)
object SettingsModule {

    /**
     * 从安装包读取版本号。
     *
     * 读取失败时回退为占位符，避免设置页因包信息不可用而无法打开。
     */
    @Provides
    fun provideAppVersionNameProvider(@ApplicationContext context: Context): AppVersionNameProvider =
        AppVersionNameProvider {
            runCatching {
                context.packageManager.getPackageInfo(context.packageName, 0).versionName ?: "—"
            }.getOrDefault("—")
        }
}
