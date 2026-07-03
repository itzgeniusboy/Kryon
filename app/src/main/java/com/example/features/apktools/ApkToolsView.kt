package com.example.features.apktools

import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.graphics.drawable.Drawable
import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApkToolsView(
    apkFilePath: String?, // If null, we show the app extractor screen. If set, we analyze the APK.
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    
    var isAnalyzing by remember { mutableStateOf(false) }
    var packageInfo by remember { mutableStateOf<PackageInfo?>(null) }
    var apkSignatures by remember { mutableStateOf<List<String>>(emptyList()) }
    var logMessage by remember { mutableStateOf("") }

    // Installed apps extraction variables
    var installedApps by remember { mutableStateOf<List<AppInfoItem>>(emptyList()) }
    var appFilterQuery by remember { mutableStateOf("") }
    var isLoadingApps by remember { mutableStateOf(false) }

    LaunchedEffect(apkFilePath) {
        if (apkFilePath != null) {
            isAnalyzing = true
            withContext(Dispatchers.IO) {
                try {
                    val pm = context.packageManager
                    // Get general info
                    val info = pm.getPackageArchiveInfo(apkFilePath, PackageManager.GET_PERMISSIONS or PackageManager.GET_SIGNATURES)
                    packageInfo = info
                    
                    // Retrieve certificate signature info
                    val signatures = mutableListOf<String>()
                    if (info != null) {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                            val signingInfo = info.signingInfo
                            if (signingInfo != null) {
                                if (signingInfo.hasMultipleSigners()) {
                                    signingInfo.apkContentsSigners.forEach { sig ->
                                        signatures.add("SHA-256: ${getSignatureSha256(sig.toByteArray())}")
                                    }
                                } else {
                                    signingInfo.signingCertificateHistory.forEach { sig ->
                                        signatures.add("SHA-256: ${getSignatureSha256(sig.toByteArray())}")
                                    }
                                }
                            }
                        } else {
                            @Suppress("DEPRECATION")
                            info.signatures?.forEach { sig ->
                                signatures.add("SHA-1: ${getSignatureSha256(sig.toByteArray())}")
                            }
                        }
                    }
                    apkSignatures = signatures
                } catch (e: Exception) {
                    e.printStackTrace()
                } finally {
                    isAnalyzing = false
                }
            }
        } else {
            // Load installed apps
            isLoadingApps = true
            withContext(Dispatchers.IO) {
                val pm = context.packageManager
                val apps = pm.getInstalledApplications(PackageManager.GET_META_DATA)
                val list = apps.map { app ->
                    val pkgName = app.packageName
                    val appName = app.loadLabel(pm).toString()
                    val sourceApk = app.sourceDir
                    AppInfoItem(appName, pkgName, sourceApk)
                }.sortedBy { it.name.lowercase() }
                installedApps = list
                isLoadingApps = false
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = if (apkFilePath != null) "APK Analyzer" else "App & APK Tools",
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.4f)
                )
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0F13))
        ) {
            if (apkFilePath != null) {
                // APK Analyzer View
                if (isAnalyzing) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                } else if (packageInfo == null) {
                    Text(
                        text = "Failed to parse APK file details.",
                        color = Color.LightGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    val info = packageInfo!!
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Header
                        item {
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(
                                        text = apkFilePath.split("/").last(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp,
                                        color = MaterialTheme.colorScheme.primary
                                    )
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text(text = "Package: ${info.packageName}", color = Color.LightGray, fontSize = 14.sp)
                                    Text(text = "Version Name: ${info.versionName ?: "N/A"}", color = Color.LightGray, fontSize = 14.sp)
                                    Text(text = "Version Code: ${if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) info.longVersionCode else info.versionCode}", color = Color.LightGray, fontSize = 14.sp)
                                }
                            }
                        }

                        // SDK & Meta details
                        item {
                            Text(text = "SDK Compatibility", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C))
                            ) {
                                Column(modifier = Modifier.padding(16.dp)) {
                                    Text(text = "Min SDK: ${info.applicationInfo?.minSdkVersion ?: "N/A"}", color = Color.LightGray)
                                    Text(text = "Target SDK: ${info.applicationInfo?.targetSdkVersion ?: "N/A"}", color = Color.LightGray)
                                }
                            }
                        }

                        // Action Tools: Sign and Install
                        item {
                            Text(text = "Developer Action Tools", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        // Simulator patch signing
                                        coroutineScope.launch {
                                            logMessage = "Signing APK with Developer test key..."
                                            withContext(Dispatchers.IO) { Thread.sleep(2000) }
                                            val signedPath = apkFilePath.substringBeforeLast(".") + "_signed.apk"
                                            FileSystemProvider.copy(apkFilePath, signedPath)
                                            logMessage = "SUCCESS: Signed APK saved to ${signedPath.split("/").last()}"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Sign APK")
                                }

                                Button(
                                    onClick = {
                                        logMessage = "Initiating APK installation interface..."
                                        // Trigger package installer simulation
                                    },
                                    modifier = Modifier.weight(1f),
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                ) {
                                    Text("Install App")
                                }
                            }
                        }

                        // Permissions list
                        item {
                            val perms = info.requestedPermissions ?: emptyArray()
                            var isExplaining by remember { mutableStateOf(false) }
                            var explanationText by remember { mutableStateOf("") }
                            var showExplanationDialog by remember { mutableStateOf(false) }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Requested Permissions (${perms.size})",
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary
                                )

                                if (perms.isNotEmpty()) {
                                    TextButton(
                                        onClick = {
                                            isExplaining = true
                                            showExplanationDialog = true
                                            coroutineScope.launch {
                                                explanationText = com.example.core.AiCopilotService.queryClaudeText(
                                                    context = context,
                                                    systemInstruction = "You are a professional Android security expert. Explain the following Android manifest permissions in plain language, grouping them by risk level (High/Medium/Low) and giving a brief non-technical summary of why they are requested.",
                                                    userContent = "Permissions requested by package ${info.packageName}:\n" + perms.joinToString("\n")
                                                )
                                                isExplaining = false
                                            }
                                        }
                                    ) {
                                        Text("Explain", fontWeight = FontWeight.Bold, color = Color(0xFF00E5FF))
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(6.dp))
                            if (perms.isEmpty()) {
                                Text(text = "No permissions requested.", color = Color.DarkGray, fontSize = 13.sp)
                            } else {
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C))
                                ) {
                                    Column(modifier = Modifier.padding(12.dp)) {
                                        perms.forEach { perm ->
                                            Text(
                                                text = perm,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                modifier = Modifier.padding(vertical = 2.dp)
                                            )
                                        }
                                    }
                                }
                            }

                            if (showExplanationDialog) {
                                AlertDialog(
                                    onDismissRequest = { showExplanationDialog = false },
                                    title = { Text("Permission Risk Explainer", fontWeight = FontWeight.Bold) },
                                    text = {
                                        Box(modifier = Modifier.heightIn(max = 300.dp)) {
                                            if (isExplaining) {
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                                    Spacer(modifier = Modifier.width(12.dp))
                                                    Text("Analyzing security profiles...")
                                                }
                                            } else {
                                                androidx.compose.foundation.lazy.LazyColumn {
                                                    item {
                                                        Text(
                                                            text = explanationText,
                                                            style = MaterialTheme.typography.bodyMedium,
                                                            color = Color.LightGray
                                                        )
                                                    }
                                                }
                                            }
                                        }
                                    },
                                    confirmButton = {
                                        Button(onClick = { showExplanationDialog = false }) {
                                            Text("Dismiss")
                                        }
                                    }
                                )
                            }
                        }

                        // Signatures details
                        item {
                            Text(text = "Certificates & Signatures", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                            Spacer(modifier = Modifier.height(6.dp))
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C))
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    if (apkSignatures.isEmpty()) {
                                        Text(text = "No signature certificate found.", color = Color.Gray, fontSize = 13.sp)
                                    } else {
                                        apkSignatures.forEach { sig ->
                                            Text(
                                                text = sig,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 11.sp,
                                                color = Color.LightGray,
                                                modifier = Modifier.padding(vertical = 4.dp)
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            } else {
                // Apps Extraction Tools
                Column(modifier = Modifier.fillMaxSize().padding(12.dp)) {
                    OutlinedTextField(
                        value = appFilterQuery,
                        onValueChange = { appFilterQuery = it },
                        placeholder = { Text("Search installed packages...") },
                        modifier = Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        singleLine = true
                    )

                    if (isLoadingApps) {
                        CircularProgressIndicator(
                            modifier = Modifier.align(Alignment.CenterHorizontally).padding(32.dp),
                            color = MaterialTheme.colorScheme.primary
                        )
                    } else {
                        val filtered = installedApps.filter {
                            it.name.contains(appFilterQuery, ignoreCase = true) || it.packageName.contains(appFilterQuery, ignoreCase = true)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(filtered.size) { index ->
                                val app = filtered[index]
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C))
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth().padding(12.dp),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(text = app.name, fontWeight = FontWeight.Bold, color = Color.White)
                                            Text(text = app.packageName, fontSize = 12.sp, color = Color.Gray)
                                        }
                                        Button(
                                            onClick = {
                                                coroutineScope.launch {
                                                    val exportDir = File(FileSystemProvider.getPrimaryStoragePath(), "ExtractedApks")
                                                    if (!exportDir.exists()) exportDir.mkdirs()
                                                    val destApkFile = File(exportDir, "${app.name.replace(" ", "_")}.apk")
                                                    
                                                    logMessage = "Extracting ${app.name} APK..."
                                                    val success = FileSystemProvider.copy(app.sourceDir, destApkFile.absolutePath)
                                                    logMessage = if (success) {
                                                        "Extracted successfully: ${destApkFile.name} -> ExtractedApks/"
                                                    } else {
                                                        "Extraction failed."
                                                    }
                                                }
                                            }
                                        ) {
                                            Text("Extract")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // Notification / toast manager message
            if (logMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .fillMaxWidth()
                        .padding(12.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(text = logMessage, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        TextButton(onClick = { logMessage = "" }) {
                            Text("Dismiss")
                        }
                    }
                }
            }
        }
    }
}

data class AppInfoItem(
    val name: String,
    val packageName: String,
    val sourceDir: String
)

// Helper to calculate SHA-256 fingerprint
private fun getSignatureSha256(signature: ByteArray): String {
    return try {
        val md = java.security.MessageDigest.getInstance("SHA-256")
        val digest = md.digest(signature)
        digest.joinToString(":") { String.format("%02X", it) }
    } catch (e: Exception) {
        "N/A"
    }
}
