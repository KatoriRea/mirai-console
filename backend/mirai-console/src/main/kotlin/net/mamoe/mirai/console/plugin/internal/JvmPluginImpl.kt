/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.plugin.internal

import kotlinx.atomicfu.locks.withLock
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import net.mamoe.mirai.console.MiraiConsole
import net.mamoe.mirai.console.plugin.Plugin
import net.mamoe.mirai.console.plugin.PluginManager
import net.mamoe.mirai.console.plugin.jvm.JvmPlugin
import net.mamoe.mirai.console.plugin.jvm.JvmPluginDescription
import net.mamoe.mirai.utils.MiraiLogger
import java.io.File
import java.util.concurrent.locks.ReentrantLock
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.EmptyCoroutineContext

internal val <T> T.job: Job where T : CoroutineScope, T : Plugin get() = this.coroutineContext[Job]!!

@PublishedApi
internal abstract class JvmPluginImpl(
    parentCoroutineContext: CoroutineContext
) : JvmPlugin,
    CoroutineScope {
    // region JvmPlugin
    /**
     * Initialized immediately after construction of [JvmPluginImpl] instance
     */
    @Suppress("PropertyName")
    internal lateinit var _description: JvmPluginDescription

    override val description: JvmPluginDescription get() = _description

    final override val logger: MiraiLogger by lazy {
        MiraiConsole.newLogger(
            this._description.name
        )
    }

    private var firstRun = true

    override val dataFolder: File by lazy {
        File(
            PluginManager.pluginsDataFolder,
            description.name
        ).apply { mkdir() }
    }

    internal fun internalOnDisable() {
        firstRun = false
        this.onDisable()
    }

    internal fun internalOnLoad() {
        this.onLoad()
    }

    internal fun internalOnEnable() {
        if (!firstRun) refreshCoroutineContext()
        this.onEnable()
    }

    // endregion

    // region CoroutineScope

    // for future use
    @Suppress("PropertyName")
    @JvmField
    internal var _intrinsicCoroutineContext: CoroutineContext =
        EmptyCoroutineContext

    @JvmField
    internal val coroutineContextInitializer = {
        CoroutineExceptionHandler { _, throwable -> logger.error(throwable) }
            .plus(parentCoroutineContext)
            .plus(SupervisorJob(parentCoroutineContext[Job])) + _intrinsicCoroutineContext
    }

    private fun refreshCoroutineContext(): CoroutineContext {
        return coroutineContextInitializer().also { _coroutineContext = it }
    }

    private val contextUpdateLock: ReentrantLock =
        ReentrantLock()
    private var _coroutineContext: CoroutineContext? = null
    final override val coroutineContext: CoroutineContext
        get() = _coroutineContext
            ?: contextUpdateLock.withLock { _coroutineContext ?: refreshCoroutineContext() }

    // endregion
}