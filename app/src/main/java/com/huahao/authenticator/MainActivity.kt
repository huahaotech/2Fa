package com.huahao.authenticator

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.os.Build
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.ui.window.Dialog
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

val ComponentActivity.authDataStore by preferencesDataStore(name = "auth_store")

class MainActivity : ComponentActivity() {
    private lateinit var authStore: AuthStore
    private lateinit var requestCameraPermissionLauncher: ActivityResultLauncher<String>
    private var onPermissionChanged: (() -> Unit)? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        authStore = AuthStore(authDataStore)

        requestCameraPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            if (granted) {
                Toast.makeText(this, "相机权限已授权", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "请授予相机权限以扫描二维码", Toast.LENGTH_LONG).show()
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

                AuthenticatorApp(
                    authStore = authStore,
                    permissionUpdateTrigger = permissionUpdateTrigger,
                    onRequestCameraPermission = { requestCameraPermissionLauncher.launch(Manifest.permission.CAMERA) }
                )
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AuthenticatorApp(
    authStore: AuthStore,
    permissionUpdateTrigger: Int,
    onRequestCameraPermission: () -> Unit
) {
    val context = LocalContext.current
    val authEntries by authStore.authEntries.collectAsState(emptyList())
    var showAboutDialog by remember { mutableStateOf(false) }

    val cameraGranted by remember(permissionUpdateTrigger) {
        derivedStateOf {
            ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED
        }
    }

    Scaffold(
        modifier = Modifier.background(
            brush = Brush.verticalGradient(
                colors = listOf(
                    MaterialTheme.colorScheme.surface,
                    MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.3f)
                )
            )
        ),
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
                                imageVector = Icons.Filled.Security,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                "身份验证助手",
                                fontSize = 20.sp,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                "已添加 ${authEntries.size} 个账号",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                },
                actions = {
                    IconButton(onClick = { showAboutDialog = true }) {
                        Icon(Icons.Outlined.Info, contentDescription = "关于")
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.8f)
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = {
                    if (!cameraGranted) {
                        onRequestCameraPermission()
                    } else {
                        context.startActivity(Intent(context, ScanActivity::class.java))
                    }
                },
                containerColor = MaterialTheme.colorScheme.primary,
                contentColor = Color.White,
                elevation = FloatingActionButtonDefaults.elevation(8.dp)
            ) {
                Icon(Icons.Default.QrCodeScanner, contentDescription = "扫描")
            }
        }
    ) {
        Box(
                modifier = Modifier
                    .padding(it)
                    .fillMaxSize()
            ) {
                HomeTab(
                    authStore = authStore,
                    authEntries = authEntries,
                    cameraGranted = cameraGranted,
                    onRequestCameraPermission = onRequestCameraPermission
                )
            }
    }

    if (showAboutDialog) {
        AboutDialog(onDismiss = { showAboutDialog = false })
    }
}

@Composable
fun AboutDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val packageManager = context.packageManager
    val packageName = context.packageName
    val versionName = try {
        packageManager.getPackageInfo(packageName, 0).versionName
    } catch (e: Exception) {
        "1.0.0"
    }

    Dialog(onDismissRequest = onDismiss) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            shape = RoundedCornerShape(24.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier.padding(28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                Box(
                    modifier = Modifier
                        .size(80.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            brush = Brush.linearGradient(
                                colors = listOf(Color(0xFF667EEA), Color(0xFF764BA2))
                            )
                        ),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        Icons.Filled.Security,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(40.dp)
                    )
                }
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "身份验证助手",
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    "版本 $versionName",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(20.dp))
                Text(
                    "安全、高效的两步验证助手，支持 Google、GitHub、Steam 等多种平台的 TOTP 验证码生成。",
                    style = MaterialTheme.typography.bodyMedium,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "功能特性:",
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(8.dp))
                Column(horizontalAlignment = Alignment.Start) {
                    Text(
                        "• 扫描二维码快速添加验证码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• 实时动态生成 TOTP 验证码",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• 点击验证码一键复制到剪贴板",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        "• 导出二维码分享给其他设备",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                Spacer(modifier = Modifier.height(16.dp))
                HorizontalDivider()
                Spacer(modifier = Modifier.height(16.dp))
                Text(
                    "© 2026 华昊科技有限公司",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center
                )
                Spacer(modifier = Modifier.height(24.dp))
                Button(
                    onClick = onDismiss,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("确定")
                }
            }
        }
    }
}

@Composable
fun HomeTab(
    authStore: AuthStore,
    authEntries: List<AuthEntry>,
    cameraGranted: Boolean,
    onRequestCameraPermission: () -> Unit
) {
    if (authEntries.isEmpty()) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Box(
                modifier = Modifier
                    .size(120.dp)
                    .clip(RoundedCornerShape(24.dp))
                    .background(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.9f)
                    )
                    .wrapContentSize(Alignment.Center)
            ) {
                Icon(
                    Icons.Default.Security,
                    contentDescription = null,
                    modifier = Modifier.size(60.dp),
                    tint = MaterialTheme.colorScheme.primary
                )
            }
            Spacer(modifier = Modifier.height(32.dp))
            Text(
                text = "暂无验证码",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "点击右下角按钮扫描二维码添加",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            contentPadding = PaddingValues(vertical = 16.dp)
        ) {
            items(authEntries) {
                AuthCodeCard(entry = it, authStore = authStore)
            }
        }
    }
}



@Composable
fun AuthCodeCard(
    entry: AuthEntry,
    authStore: AuthStore
) {
    val context = LocalContext.current
    var showDeleteDialog by remember { mutableStateOf(false) }
    var showExportMenu by remember { mutableStateOf(false) }

    var currentTime by remember { mutableLongStateOf(System.currentTimeMillis() / 1000L) }
    LaunchedEffect(Unit) {
        while (true) {
            currentTime = System.currentTimeMillis() / 1000L
            delay(1000L)
        }
    }

    val code = remember(currentTime) {
        try {
            val timeStep = currentTime / entry.period
            TotpGenerator.generate(entry.secret, timeStep, entry.digits, entry.algorithm)
        } catch (e: Exception) {
            "------"
        }
    }

    val progress = remember(currentTime) {
        val timeStep = currentTime / entry.period
        val nextTimeStep = timeStep + 1
        val nextTime = nextTimeStep * entry.period
        val remainingTime = nextTime - currentTime
        remainingTime.toFloat() / entry.period.toFloat()
    }

    ModernCard(
        modifier = Modifier
            .fillMaxWidth()
            .clickable {
                val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                val clip = android.content.ClipData.newPlainText("验证码", code)
                clipboard.setPrimaryClip(clip)
                Toast.makeText(context, "已复制", Toast.LENGTH_SHORT).show()
            }
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(20.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = if (entry.issuer.isNotBlank()) entry.issuer else "未命名",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    if (entry.account.isNotBlank() && entry.issuer.isNotBlank()) {
                        Text(
                            text = entry.account,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }

                Box {
                    IconButton(
                        onClick = { showExportMenu = true },
                        modifier = Modifier.size(36.dp)
                    ) {
                        Icon(
                            Icons.Default.MoreVert,
                            contentDescription = "更多",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    DropdownMenu(
                        expanded = showExportMenu,
                        onDismissRequest = { showExportMenu = false }
                    ) {
                        DropdownMenuItem(
                            text = { Text("导出二维码") },
                            onClick = {
                                showExportMenu = false
                                val entryStr = "${entry.id}|${entry.issuer}|${entry.account}|${entry.secret}|${entry.algorithm}|${entry.digits}|${entry.period}"
                                val intent = Intent(context, ExportActivity::class.java).apply {
                                    putExtra("entry", entryStr)
                                }
                                context.startActivity(intent)
                            },
                            leadingIcon = {
                                Icon(Icons.Default.QrCode, contentDescription = null)
                            }
                        )
                        DropdownMenuItem(
                            text = { Text("删除") },
                            onClick = {
                                showExportMenu = false
                                showDeleteDialog = true
                            },
                            leadingIcon = {
                                Icon(
                                    Icons.Default.Delete,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.error
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = formatCode(code),
                    style = MaterialTheme.typography.headlineMedium,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 4.sp,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    text = "${(progress * 30).toInt()}s",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp)),
                color = MaterialTheme.colorScheme.primary,
                trackColor = MaterialTheme.colorScheme.surfaceVariant
            )
        }
    }

    if (showDeleteDialog) {
        ModernAlertDialog(
            onDismissRequest = { showDeleteDialog = false },
            title = "删除确认",
            content = { Text("确定要删除这个验证码吗？") },
            confirmButton = {
                Button(
                    onClick = {
                        showDeleteDialog = false
                        CoroutineScope(Dispatchers.IO).launch {
                            authStore.removeAuthEntry(entry.id)
                        }
                    },
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Text("删除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = false }) {
                    Text("取消")
                }
            }
        )
    }
}

@Composable
fun ModernCard(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit = {}
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(4.dp)
    ) {
        content()
    }
}

@Composable
fun ModernAlertDialog(
    onDismissRequest: () -> Unit,
    title: String,
    content: @Composable () -> Unit,
    confirmButton: @Composable () -> Unit,
    dismissButton: (@Composable () -> Unit)? = null
) {
    AlertDialog(
        onDismissRequest = onDismissRequest,
        title = { Text(title) },
        text = content,
        confirmButton = confirmButton,
        dismissButton = dismissButton,
        shape = RoundedCornerShape(16.dp)
    )
}

private fun formatCode(code: String): String {
    return if (code.length == 6) {
        "${code.substring(0, 3)} ${code.substring(3)}"
    } else if (code.length == 8) {
        "${code.substring(0, 4)} ${code.substring(4)}"
    } else {
        code
    }
}
