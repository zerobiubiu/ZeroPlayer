package com.privateplayer.feature.home

/**
 * 批量操作逐项结果（F-41）：不“全成功/全失败”一刀切。
 *
 * 成功项从选择中移除；失败项保留选择并汇总原因，禁止静默跳过。
 */
data class BatchResult(
    val succeeded: List<String>,
    val failed: List<Pair<String, String>>,
) {
    val total: Int get() = succeeded.size + failed.size
    val allSucceeded: Boolean get() = failed.isEmpty()
    val allFailed: Boolean get() = succeeded.isEmpty() && failed.isNotEmpty()
}
