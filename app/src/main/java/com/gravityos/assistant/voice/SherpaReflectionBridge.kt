package com.gravityos.assistant.voice

import android.content.res.AssetManager

object SherpaReflectionBridge {

    fun isSherpaAarPresent(): Boolean {
        return try {
            Class.forName("com.k2fsa.sherpa.onnx.OfflineTts")
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun newInstance(className: String): Any? {
        return try {
            val clazz = Class.forName(className)
            val ctor = clazz.getDeclaredConstructor()
            ctor.isAccessible = true
            ctor.newInstance()
        } catch (_: Throwable) {
            null
        }
    }

    fun createWithAssetManager(className: String, config: Any): Any? {
        return try {
            val clazz = Class.forName(className)
            val ctor = clazz.constructors.firstOrNull { c ->
                val params = c.parameterTypes
                params.size == 2 &&
                    params[0] == AssetManager::class.java &&
                    params[1].isAssignableFrom(config.javaClass)
            } ?: return null
            ctor.newInstance(null, config)
        } catch (_: Throwable) {
            null
        }
    }

    fun setProperty(target: Any, propName: String, value: Any): Boolean {
        val cap = propName.replaceFirstChar { it.uppercaseChar() }
        val setterName = "set$cap"
        val methods = target.javaClass.methods.filter { it.name == setterName && it.parameterTypes.size == 1 }

        for (m in methods) {
            if (isTypeCompatible(m.parameterTypes[0], value)) {
                return try {
                    m.invoke(target, value)
                    true
                } catch (_: Throwable) {
                    false
                }
            }
        }

        // Fallback to direct field assignment
        return try {
            val field = target.javaClass.getDeclaredField(propName)
            field.isAccessible = true
            field.set(target, value)
            true
        } catch (_: Throwable) {
            false
        }
    }

    fun getProperty(target: Any, propName: String): Any? {
        val cap = propName.replaceFirstChar { it.uppercaseChar() }
        val getterName = "get$cap"
        try {
            val method = target.javaClass.methods.firstOrNull {
                it.name == getterName && it.parameterTypes.isEmpty()
            }
            if (method != null) return method.invoke(target)
        } catch (_: Throwable) {
        }

        return try {
            val field = target.javaClass.getDeclaredField(propName)
            field.isAccessible = true
            field.get(target)
        } catch (_: Throwable) {
            null
        }
    }

    fun invoke(target: Any, methodName: String, vararg args: Any?): Any? {
        val candidates = target.javaClass.methods.filter {
            it.name == methodName && it.parameterTypes.size == args.size
        }

        for (method in candidates) {
            val params = method.parameterTypes
            var matches = true
            for (i in args.indices) {
                val arg = args[i]
                if (arg == null) {
                    if (params[i].isPrimitive) {
                        matches = false
                        break
                    }
                } else if (!isTypeCompatible(params[i], arg)) {
                    matches = false
                    break
                }
            }
            if (matches) {
                return try {
                    method.invoke(target, *args)
                } catch (_: Throwable) {
                    null
                }
            }
        }
        return null
    }

    private fun isTypeCompatible(paramType: Class<*>, value: Any): Boolean {
        if (paramType.isAssignableFrom(value.javaClass)) return true
        return when (paramType) {
            Int::class.javaPrimitiveType -> value is Int
            Float::class.javaPrimitiveType -> value is Float
            Double::class.javaPrimitiveType -> value is Double
            Boolean::class.javaPrimitiveType -> value is Boolean
            Long::class.javaPrimitiveType -> value is Long
            else -> false
        }
    }
}
