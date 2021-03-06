/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.utils

import kotlinx.coroutines.*
import kotlinx.coroutines.future.future
import net.mamoe.mirai.console.plugin.jvm.JavaPlugin
import java.util.concurrent.Callable
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Future
import kotlin.coroutines.CoroutineContext


/**
 * 拥有生命周期管理的 Java 线程池.
 *
 * 在插件被 [卸载][JavaPlugin.onDisable] 时将会自动停止.
 *
 * @see JavaPlugin.scheduler 获取实例
 */
class JavaPluginScheduler internal constructor(parentCoroutineContext: CoroutineContext) : CoroutineScope {
    override val coroutineContext: CoroutineContext =
        parentCoroutineContext + SupervisorJob(parentCoroutineContext[Job])

    /**
     * 新增一个 Repeating Task (定时任务)
     *
     * 这个 Runnable 会被每 [intervalMs] 调用一次(不包含 [runnable] 执行时间)
     *
     * @see Future.cancel 取消这个任务
     */
    fun repeating(intervalMs: Long, runnable: Runnable): Future<Void?> {
        return this.future {
            while (isActive) {
                withContext(Dispatchers.IO) { runnable.run() }
                delay(intervalMs)
            }
            null
        }
    }

    /**
     * 新增一个 Delayed Task (延迟任务)
     *
     * 在延迟 [delayMillis] 后执行 [runnable]
     */
    fun delayed(delayMillis: Long, runnable: Runnable): CompletableFuture<Void?> {
        return future {
            delay(delayMillis)
            withContext(Dispatchers.IO) {
                runnable.run()
            }
            null
        }
    }

    /**
     * 新增一个 Delayed Task (延迟任务)
     *
     * 在延迟 [delayMillis] 后执行 [runnable]
     */
    fun <R> delayed(delayMillis: Long, runnable: Callable<R>): CompletableFuture<Void?> {
        return future {
            delay(delayMillis)
            withContext(Dispatchers.IO) { runnable.call() }
            null
        }
    }

    /**
     * 异步执行一个任务, 最终返回 [Future], 与 Java 使用方法无异, 但效率更高且可以在插件关闭时停止
     */
    fun <R> async(supplier: Callable<R>): Future<R> {
        return future {
            withContext(Dispatchers.IO) { supplier.call() }
        }
    }

    /**
     * 异步执行一个任务, 没有返回
     */
    fun async(runnable: Runnable): Future<Void?> {
        return future {
            withContext(Dispatchers.IO) { runnable.run() }
            null
        }
    }
}