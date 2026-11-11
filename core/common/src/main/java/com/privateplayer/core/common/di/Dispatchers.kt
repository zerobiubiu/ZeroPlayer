package com.privateplayer.core.common.di

import javax.inject.Qualifier

/** 主线程调度器限定符。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class MainDispatcher

/** IO 线程调度器限定符（磁盘、文件复制）。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class IoDispatcher

/** CPU 密集型调度器限定符（元数据解析、哈希）。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class DefaultDispatcher
