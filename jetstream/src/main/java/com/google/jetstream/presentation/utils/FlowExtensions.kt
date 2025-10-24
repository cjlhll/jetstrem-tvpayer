package com.google.jetstream.presentation.utils

import kotlinx.coroutines.TimeoutCancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withTimeout

/**
 * 等待Flow满足指定条件，类似Promise的行为
 * 
 * @param timeoutMillis 最大等待时间（毫秒），超时返回false
 * @param predicate 条件判断函数
 * @return 是否在超时前满足条件
 * 
 * 示例:
 * ```kotlin
 * val success = dataFlow.awaitUntil(1000) { it.isNotEmpty() }
 * if (success) {
 *     // 数据已准备好
 * } else {
 *     // 超时
 * }
 * ```
 */
suspend fun <T> Flow<T>.awaitUntil(
    timeoutMillis: Long = 5000L,
    predicate: (T) -> Boolean
): Boolean {
    return try {
        withTimeout(timeoutMillis) {
            // 等待第一个满足条件的值
            first(predicate)
            true
        }
    } catch (e: TimeoutCancellationException) {
        false
    }
}

/**
 * 等待Flow变为非空列表
 * 
 * @param timeoutMillis 最大等待时间（毫秒）
 * @return 是否在超时前数据加载完成
 */
suspend fun <T> Flow<List<T>>.awaitNonEmpty(timeoutMillis: Long = 5000L): Boolean {
    return awaitUntil(timeoutMillis) { it.isNotEmpty() }
}

/**
 * 等待Flow满足条件并获取值（类似Promise.then）
 * 
 * @param timeoutMillis 最大等待时间（毫秒）
 * @param predicate 条件判断函数
 * @return 满足条件的值，超时返回null
 */
suspend fun <T> Flow<T>.awaitValue(
    timeoutMillis: Long = 5000L,
    predicate: (T) -> Boolean = { true }
): T? {
    return try {
        withTimeout(timeoutMillis) {
            first(predicate)
        }
    } catch (e: TimeoutCancellationException) {
        null
    }
}
