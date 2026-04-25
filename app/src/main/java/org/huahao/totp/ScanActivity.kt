package org.huahao.totp

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.util.Log
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.util.UUID
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class ScanActivity : ComponentActivity() {
    private lateinit var cameraExecutor: ExecutorService
    private lateinit var authStore: AuthStore
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private var onPermissionChanged: (() -> Unit)? = null
    private lateinit var previewView: PreviewView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        cameraExecutor = Executors.newSingleThreadExecutor()
        authStore = AuthStore(authDataStore)
        previewView = PreviewView(this)

        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "相机权限已授权", Toast.LENGTH_SHORT).show()
                startCamera()
            } else {
                Toast.makeText(this, "请授予相机权限以扫描二维码", Toast.LENGTH_LONG).show()
                finish()
            }
            onPermissionChanged?.invoke()
        }

        setContent {
            val colorScheme = lightColorScheme()
            var permissionUpdateTrigger by remember { mutableStateOf(0) }

            MaterialTheme(
                colorScheme = colorScheme,
                typography = Typography()
            ) {
                val activity = LocalContext.current as Activity
                SideEffect {
                    try {
                        WindowCompat.setDecorFitsSystemWindows(activity.window, false)
                        activity.window.statusBarColor = AndroidColor.TRANSPARENT
                        val controller = WindowInsetsControllerCompat(activity.window, activity.window.decorView)
                        controller.isAppearanceLightStatusBars = true
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            controller.isAppearanceLightNavigationBars = true
                        }
                    } catch (_: Throwable) {}
                }

                SideEffect {
                    onPermissionChanged = { permissionUpdateTrigger++ }
                }

                ScanScreen(
                    previewView = previewView,
                    onBackClick = { finish() },
                    onRequestCameraPermission = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder()
                .build()
                .also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

            val imageAnalyzer = ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(cameraExecutor, BarcodeAnalyzer { barcode ->
                        parseBarcode(barcode)
                        finish()
                    })
                }

            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA

            try {
                cameraProvider.unbindAll()
                cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageAnalyzer
                )
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }

        }, ContextCompat.getMainExecutor(this))
    }

    private fun parseBarcode(barcode: String) {
        // 检查是否是 Google 迁移格式
        if (barcode.startsWith("otpauth-migration://")) {
            parseGoogleMigration(barcode)
            return
        }
        
        // 普通 TOTP 格式
        parseStandardTotp(barcode)
    }
    
    private fun parseGoogleMigration(barcode: String) {
        val entries = GoogleMigrationParser.parse(barcode)
        
        if (entries.isEmpty()) {
            runOnUiThread {
                Toast.makeText(this@ScanActivity, "无法解析迁移格式", Toast.LENGTH_SHORT).show()
            }
            return
        }
        
        CoroutineScope(Dispatchers.IO).launch {
            var addedCount = 0
            entries.forEach { otpEntry ->
                val entry = AuthEntry(
                    id = UUID.randomUUID().toString(),
                    issuer = otpEntry.issuer,
                    account = otpEntry.name,
                    secret = otpEntry.secret,
                    algorithm = otpEntry.algorithm,
                    digits = otpEntry.digits,
                    period = otpEntry.period
                )
                
                if (authStore.addAuthEntry(entry)) {
                    addedCount++
                }
            }
            
            runOnUiThread {
                if (addedCount > 0) {
                    Toast.makeText(this@ScanActivity, "成功导入 $addedCount 个验证码", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ScanActivity, "没有导入新的验证码（可能已存在）", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }
    
    private fun parseStandardTotp(barcode: String) {
        // 使用正确的 URL 解码方式
        val decodedBarcode = try {
            java.net.URLDecoder.decode(barcode, "UTF-8")
        } catch (e: Exception) {
            barcode
        }
        
        val uri = decodedBarcode.replace("otpauth://totp/", "")
        val parts = uri.split("?")
        val label = try {
            java.net.URLDecoder.decode(parts[0], "UTF-8")
        } catch (e: Exception) {
            parts[0]
        }
        val params = parts[1].split("&")

        var issuer = ""
        var account: String
        var secret = ""
        var algorithm = "SHA1"
        var digits = 6
        var period = 30

        if (label.contains(":")) {
            val labelParts = label.split(":")
            issuer = labelParts[0]
            account = labelParts[1]
        } else {
            account = label
        }

        params.forEach { param ->
            val keyValue = param.split("=")
            if (keyValue.size >= 2) {
                val value = try {
                    java.net.URLDecoder.decode(keyValue[1], "UTF-8")
                } catch (e: Exception) {
                    keyValue[1]
                }
                when (keyValue[0]) {
                    "secret" -> secret = value
                    "issuer" -> issuer = value
                    "algorithm" -> algorithm = value
                    "digits" -> digits = value.toInt()
                    "period" -> period = value.toInt()
                }
            }
        }

        val entry = AuthEntry(
            id = UUID.randomUUID().toString(),
            issuer = issuer,
            account = account,
            secret = secret,
            algorithm = algorithm,
            digits = digits,
            period = period
        )

        CoroutineScope(Dispatchers.IO).launch {
            val success = authStore.addAuthEntry(entry)
            runOnUiThread {
                if (success) {
                    Toast.makeText(this@ScanActivity, "添加成功", Toast.LENGTH_SHORT).show()
                    finish()
                } else {
                    Toast.makeText(this@ScanActivity, "该验证码已存在", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(
            baseContext,
            it
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }

    companion object {
        private const val TAG = "ScanActivity"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS = arrayOf(
            android.Manifest.permission.CAMERA
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanScreen(
    previewView: PreviewView,
    onBackClick: () -> Unit,
    @Suppress("UNUSED_PARAMETER") onRequestCameraPermission: () -> Unit
) {
    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "扫描二维码",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBackClick) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "返回")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Transparent
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
                modifier = Modifier.weight(1f)
            ) {
                AndroidView(
                    factory = {
                        previewView
                    },
                    modifier = Modifier.fillMaxSize()
                )

                Column(
                    modifier = Modifier.align(Alignment.Center)
                ) {
                    Box(
                        modifier = Modifier
                            .size(280.dp)
                            .border(
                                width = 3.dp,
                                color = Color.White,
                                shape = RoundedCornerShape(24.dp)
                            ),
                        contentAlignment = Alignment.Center
                    ) {
                        val cornerShapes = listOf(
                            Alignment.TopStart to RoundedCornerShape(topStart = 16.dp),
                            Alignment.TopEnd to RoundedCornerShape(topEnd = 16.dp),
                            Alignment.BottomStart to RoundedCornerShape(bottomStart = 16.dp),
                            Alignment.BottomEnd to RoundedCornerShape(bottomEnd = 16.dp)
                        )
                        cornerShapes.forEach { (alignment, shape) ->
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .border(
                                        width = 4.dp,
                                        color = MaterialTheme.colorScheme.primary,
                                        shape = shape
                                    )
                                    .align(alignment)
                            )
                        }
                    }
                }
            }

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(32.dp),
                contentAlignment = Alignment.Center
            ) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = "将二维码对准扫描框",
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onBackground
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "系统将自动识别",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                        textAlign = TextAlign.Center
                    )
                }
            }
        }
    }
}

class BarcodeAnalyzer(private val onBarcodeDetected: (String) -> Unit) : ImageAnalysis.Analyzer {
    private val options = BarcodeScannerOptions.Builder()
        .setBarcodeFormats(Barcode.FORMAT_QR_CODE)
        .build()
    private val scanner = BarcodeScanning.getClient(options)
    private var isScanned = false

    override fun analyze(imageProxy: ImageProxy) {
        if (isScanned) {
            imageProxy.close()
            return
        }

        val mediaImage = imageProxy.image
        if (mediaImage != null) {
            val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
            scanner.process(image)
                .addOnSuccessListener { barcodes ->
                    for (barcode in barcodes) {
                        val rawValue = barcode.rawValue
                        if (rawValue != null && (rawValue.startsWith("otpauth://totp/") || rawValue.startsWith("otpauth-migration://"))) {
                            isScanned = true
                            onBarcodeDetected(rawValue)
                        }
                    }
                }
                .addOnFailureListener { e ->
                    Log.e("BarcodeAnalyzer", "Error scanning barcode", e)
                }
                .addOnCompleteListener {
                    imageProxy.close()
                }
        } else {
            imageProxy.close()
        }
    }
}
