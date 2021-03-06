/*
 * Copyright 2020 Mamoe Technologies and contributors.
 *
 * 此源代码的使用受 GNU AFFERO GENERAL PUBLIC LICENSE version 3 许可证的约束, 可以在以下链接找到该许可证.
 * Use of this source code is governed by the GNU AGPLv3 license that can be found through the following link.
 *
 * https://github.com/mamoe/mirai/blob/master/LICENSE
 */

@file:Suppress("NOTHING_TO_INLINE", "unused")

package net.mamoe.mirai.console.setting

import kotlinx.serialization.KSerializer
import net.mamoe.mirai.console.setting.internal.SettingImpl
import net.mamoe.mirai.console.setting.internal.serialNameOrPropertyName
import net.mamoe.mirai.utils.MiraiExperimentalAPI
import kotlin.properties.ReadWriteProperty
import kotlin.reflect.KProperty
import kotlin.reflect.KProperty0
import kotlin.reflect.full.findAnnotation

/**
 * 在配置文件和图像界面中保存的名称.
 */
typealias SerialName = kotlinx.serialization.SerialName

/**
 * 在配置文件和图像界面中显示的说明.
 */
typealias Comment = net.mamoe.yamlkt.Comment

/**
 * 配置的基类. 所有配置必须拥有一个无参构造器, 以用于在 [MutableList] 与 [MutableMap] 中动态识别类型
 */
@Suppress("EXPOSED_SUPER_CLASS")
abstract class Setting : SettingImpl() {

    /**
     * 表示这个配置的嵌套对象, 自动绑定数据更新.
     */
    abstract inner class Inner : Setting() {
        internal lateinit var attachedValue: Value<*>
        override fun onElementChanged(value: Value<*>) {
            this@Setting.onElementChanged(attachedValue)
        }
    }

    data class PropertyInfo(
        val serialName: String,
        val annotations: List<Annotation>,
        val propertyOriginalName: String?
    )

    /**
     * 这个配置的名称, 仅对于顶层配置有效.
     */
    @MiraiExperimentalAPI
    open val serialName: String
        get() = this::class.findAnnotation<SerialName>()?.value
            ?: this::class.qualifiedName
            ?: error("Names should be assigned to anonymous classes manually by overriding serialName")


    // for Java only
    fun <T : Any> addProperty(
        propertyInfo: PropertyInfo,
        value: Value<*>
    ): Value<*> {
        if (built) error("The Setting is already serialized so it's structure is immutable.")
        valueList.add(value to propertyInfo)
        return value
    }

    /**
     * 获取这个属性的真实 [Value] 委托
     */
    @get:JvmSynthetic
    val <R : Any> KProperty0<R>.correspondingValue: Value<R>
        @Suppress("UNCHECKED_CAST")
        get() = findCorrespondingValue(this)
            ?: throw NoSuchElementException("No corresponding Value found for property $this")

    /**
     * 获取这个属性的真实 [Value] 委托
     */
    fun <R : Any> findCorrespondingValue(property: KProperty0<R>): Value<R>? {
        @Suppress("UNCHECKED_CAST")
        return this@Setting.valueList.firstOrNull { it.second.propertyOriginalName == property.name }?.first as Value<R>?
    }

    /**
     * 提供属性委托, 并添加这个对象的自动保存跟踪.
     */
    @JvmSynthetic
    operator fun <T : Any> Value<T>.provideDelegate(
        thisRef: Setting,
        property: KProperty<*>
    ): ReadWriteProperty<Setting, T> {
        if (built) error("The Setting is already serialized so it's structure is immutable.")
        valueList.add(this to PropertyInfo(property.serialNameOrPropertyName, property.annotations, property.name))
        return this
    }

    abstract override fun onElementChanged(value: Value<*>)

    override fun toString(): String = yamlForToString.stringify(this.serializer, this)
}

/**
 * 用于更新或保存这个 [Value] 的序列化器.
 */
@Suppress("UNCHECKED_CAST")
val <T : Setting> T.serializer: KSerializer<T>
    get() = kotlinSerializer as KSerializer<T>
