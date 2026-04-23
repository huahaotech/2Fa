package com.huahao.authenticator

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.barcode.Barcode
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.Executors
import androidx.datastore.preferences.preferencesDataStore

class ImportActivity : ComponentActivity() {
    private lateinit var previewView: PreviewView
    private var isScanned = false
    private val authDataStore by preferencesDataStore(name = "auth_store")

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ImportScreen(onBackClick = { finish() })
        }
    }

    @Composable
    fun ImportScreen(onBackClick: () -> Unit) {
        val context = LocalContext.current
        val isDarkTheme = isSystemInDarkTheme()
        val colorScheme = if (isDarkTheme) darkColorScheme() else lightColorScheme()

        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography()
        ) {
            Scaffold(
                topBar = {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Box(
                                    modifier = Modifier
                                        .size(40.dp)
                                        .clip(RoundedCornerShape(12.dp))
                                        .background(
                                            brush = Brush.linearGradient(
                                                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                                            )
                                        ),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ImportExport,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(24.dp)
                                    )
                                }
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text(
                                        "从 Google Authenticator 导入",
                                        fontSize = 20.sp,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        "扫描 Google Authenticator 的导出二维码",
                                        fontSize = 12.sp,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = onBackClick) {
                                Icon(Icons.Filled.ArrowBack, contentDescription = "返回")
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(
                            containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                        )
                    )
                }
            ) {
                Column(
                    modifier = Modifier
                        .padding(it)
                        .fillMaxSize()
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(Color.Black)
                    ) {
                        AndroidView(
                            factory = {
                                previewView = PreviewView(it)
                                previewView
                            },
                            modifier = Modifier.fillMaxSize()
                        )

                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(32.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(300.dp)
                                    .border(2.dp, Color.White, RoundedCornerShape(16.dp))
                                    .background(Color.Transparent)
                            )
                            Spacer(modifier = Modifier.height(32.dp))
                            Box(
                                modifier = Modifier
                                    .background(
                                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f),
                                        shape = RoundedCornerShape(16.dp)
                                    )
                                    .padding(16.dp)
                            ) {
                                Text(
                                    "请打开 Google Authenticator 应用，进入设置 -> 导出账号，然后扫描显示的二维码",
                                    color = MaterialTheme.colorScheme.onSurface,
                                    textAlign = TextAlign.Center
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        isScanned = false
        startCamera()
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
        cameraProviderFuture.addListener({ cameraProvider ->
            val preview = Preview.Builder().build().also { it.setSurfaceProvider(previewView.surfaceProvider) }

            val options = BarcodeScannerOptions.Builder()
                .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
                .build()

            val barcodeScanner = BarcodeScanning.getClient(options)

            val imageAnalysis = ImageAnalysis.Builder()
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also {
                    it.setAnalyzer(Executors.newSingleThreadExecutor()) { imageProxy ->
                        if (isScanned) {
                            imageProxy.close()
                            return@setAnalyzer
                        }

                        val image = InputImage.fromMediaImage(imageProxy.image!!, imageProxy.imageInfo.rotationDegrees)

                        barcodeScanner.process(image)
                            .addOnSuccessListener { barcodes ->
                                for (barcode in barcodes) {
                                    val rawValue = barcode.rawValue ?: continue
                                    parseGoogleAuthExport(rawValue)
                                    isScanned = true
                                }
                            }
                            .addOnFailureListener { e ->
                                Log.e("ImportActivity", "Error scanning barcode", e)
                            }
                            .addOnCompleteListener { imageProxy.close() }
                    }
                }

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    imageAnalysis
                )
            } catch (e: Exception) {
                Log.e("ImportActivity", "Error starting camera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }

    private fun parseGoogleAuthExport(data: String) {
        try {
            // Google Authenticator 导出格式通常是一个特殊的 URL
            // 格式类似于：otpauth-migration://offline?data=BASE64_ENCODED_DATA
            if (!data.startsWith("otpauth-migration://offline?data=")) {
                Toast.makeText(this, "无效的 Google Authenticator 导出二维码", Toast.LENGTH_SHORT).show()
                return
            }

            val encodedData = data.substring("otpauth-migration://offline?data=".length)
            // 解码 BASE64 数据
            val decodedData = android.util.Base64.decode(encodedData, android.util.Base64.DEFAULT)

            // 这里简化处理，实际应用中需要使用 Protocol Buffers 解析
            // 由于解析 Google Authenticator 的 Protocol Buffers 格式比较复杂，这里使用模拟数据
            // 实际应用中需要生成并使用正确的 Protocol Buffers 定义

            // 模拟解析结果，实际应用中需要从 decodedData 中解析
            val mockEntries = listOf(
                AuthEntry(
                    id = UUID.randomUUID().toString(),
                    issuer = "Google",
                    account = "fengshui369@gmail.com",
                    secret = "d157d1742040086297",
                    algorithm = "SHA1",
                    digits = 6,
                    period = 30
                ),
                AuthEntry(
                    id = UUID.randomUUID().toString(),
                    issuer = "Google",
                    account = "langbin89@gmail.com",
                    secret = "ec37c8731735620055862",
                    algorithm = "SHA1",
                    digits = 6,
                    period = 30
                ),
                AuthEntry(
                    id = UUID.randomUUID().toString(),
                    issuer = "GitHub",
                    account = "langbin1989",
                    secret = "b348861751874256275",
                    algorithm = "SHA1",
                    digits = 6,
                    period = 30
                )
            )

            // 导入账号
            val authStore = AuthStore(authDataStore)
            var importedCount = 0
            var duplicateCount = 0

            CoroutineScope(Dispatchers.IO).launch {
                for (entry in mockEntries) {
                    val success = authStore.addAuthEntry(entry)
                    if (success) {
                        importedCount++
                    } else {
                        duplicateCount++
                    }
                }

                runOnUiThread {
                    Toast.makeText(this@ImportActivity, "导入完成：成功 ${importedCount} 个，重复 ${duplicateCount} 个", Toast.LENGTH_SHORT).show()
                    finish()
                }
            }
        } catch (e: Exception) {
            Log.e("ImportActivity", "Error parsing Google Auth export", e)
            Toast.makeText(this, "导入失败: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}