/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

package net.mamoe.mirai.console.plugin.internal

import net.mamoe.mirai.console.MiraiConsole
import java.io.File
import java.net.URLClassLoader

internal class PluginsLoader(private val parentClassLoader: ClassLoader) {
    private val loggerName = "PluginsLoader"
    private val pluginLoaders = linkedMapOf<String, PluginClassLoader>()
    private val classesCache = mutableMapOf<String, Class<*>>()
    private val logger = MiraiConsole.newLogger(loggerName)

    /**
     * 清除所有插件加载器
     */
    fun clear() {
        val iterator = pluginLoaders.iterator()
        while (iterator.hasNext()) {
            val plugin = iterator.next()
            var cl = ""
            try {
                cl = plugin.value.toString()
                plugin.value.close()
                iterator.remove()
            } catch (e: Throwable) {
                logger.error("Plugin(${plugin.key}) can't not close its ClassLoader(${cl})", e)
            }
        }
        classesCache.clear()
    }

    /**
     * 移除单个插件加载器
     */
    fun remove(pluginName: String): Boolean {
        pluginLoaders[pluginName]?.close() ?: return false
        pluginLoaders.remove(pluginName)
        return true
    }

    fun loadPluginMainClassByJarFile(pluginName: String, mainClass: String, jarFile: File): Class<*> {
        try {
            if (!pluginLoaders.containsKey(pluginName)) {
                pluginLoaders[pluginName] =
                    PluginClassLoader(
                        pluginName,
                        jarFile,
                        this,
                        parentClassLoader
                    )
            }
            return pluginLoaders[pluginName]!!.loadClass(mainClass)
        } catch (e: ClassNotFoundException) {
            throw ClassNotFoundException(
                "PluginsClassLoader(${pluginName}) can't load this pluginMainClass:${mainClass}",
                e
            )
        } catch (e: Throwable) {
            throw Throwable("init or load class error", e)
        }
    }

    /**
     *  尝试加载插件的依赖,无则返回null
     */
    fun loadDependentClass(name: String): Class<*>? {
        var c: Class<*>? = null
        // 尝试从缓存中读取
        if (classesCache.containsKey(name)) {
            c = classesCache[name]
        }
        // 然后再交给插件的classloader来加载依赖
        if (c == null) {
            pluginLoaders.values.forEach {
                try {
                    c = it.findClass(name, false)
                    return@forEach
                } catch (e: ClassNotFoundException) {/*nothing*/
                }
            }
        }
        return c
    }

    fun addClassCache(name: String, clz: Class<*>) {
        synchronized(classesCache) {
            if (!classesCache.containsKey(name)) {
                classesCache[name] = clz
            }
        }
    }
}

internal class PluginClassLoader(
    private val pluginName: String,
    files: File,
    private val pluginsLoader: PluginsLoader,
    parent: ClassLoader
) {
    private val classesCache = mutableMapOf<String, Class<*>?>()
    private var classLoader: ClassLoader

    init {
        classLoader = try {
            //兼容Android
            val loaderClass = Class.forName("dalvik.system.PathClassLoader")
            loaderClass.getConstructor(String::class.java, ClassLoader::class.java)
                .newInstance(files.absolutePath, parent) as ClassLoader
        } catch (e: ClassNotFoundException) {
            URLClassLoader(arrayOf((files.toURI().toURL())), parent)
        }
    }

    fun loadClass(className: String): Class<*> = classLoader.loadClass(className)!!


    fun findClass(name: String, isSearchDependent: Boolean = true): Class<*>? {
        var clz: Class<*>? = null
        // 缓存中找
        if (classesCache.containsKey(name)) {

            return classesCache[name]
        }
        // 是否寻找依赖
        if (isSearchDependent) {
            clz = pluginsLoader.loadDependentClass(name)
        }
        // 好像没有findClass，直接load
        if (clz == null) {
            clz = classLoader.loadClass(name)
        }
        // 加入缓存
        if (clz != null) {
            pluginsLoader.addClassCache(name, clz)
        }
        // 加入缓存
        synchronized(classesCache) {
            classesCache[name] = clz
        }
        return clz
    }

    fun close() {
        if (classLoader is URLClassLoader) {
            (classLoader as URLClassLoader).close()
        }
        classesCache.clear()
    }
}
