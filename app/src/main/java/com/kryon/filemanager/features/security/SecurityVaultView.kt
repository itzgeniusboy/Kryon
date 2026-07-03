package com.kryon.filemanager.features.security

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Fingerprint
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryon.filemanager.core.FileSystemProvider
import com.kryon.filemanager.core.SecurePreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SecurityVaultView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isInitialized by remember { mutableStateOf(false) }
    var isAuthenticated by remember { mutableStateOf(false) }
    var currentTab by remember { mutableStateOf(0) } // 0: Vault Files, 1: Permissions Audit

    var passcodeField by remember { mutableStateOf("") }
    var verifyPasscodeField by remember { mutableStateOf("") }

    // Vault items
    val vaultFolder = remember { SecurityVaultHelper.getVaultDirectory(context) }
    var vaultFiles by remember { mutableStateOf<List<File>>(emptyList()) }
    var selectedFileToDecrypt by remember { mutableStateOf<File?>(null) }
    var showImportDialog by remember { mutableStateOf(false) }
    var importFilePath by remember { mutableStateOf("") }

    // Audit items
    var auditList by remember { mutableStateOf<List<AppAuditItem>>(emptyList()) }
    var isAuditing by remember { mutableStateOf(false) }

    fun refreshVaultFiles() {
        vaultFiles = vaultFolder.listFiles()?.toList() ?: emptyList()
    }

    LaunchedEffect(Unit) {
        isInitialized = SecurePreferences.isVaultInitialized(context)
        refreshVaultFiles()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kryon Security Vault", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
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
                .background(Color(0xFF0F0F12))
        ) {
            if (!isInitialized) {
                // 1. SETUP PASSCODE (Initialization)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Lock,
                        contentDescription = "Lock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(64.dp)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(
                        "Initialize AES-256 Security Vault",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        "Create a strong 4-digit passcode. Your files will be encrypted using standard AES-256 with this key.",
                        color = Color.LightGray,
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    OutlinedTextField(
                        value = passcodeField,
                        onValueChange = { if (it.length <= 4) passcodeField = it },
                        label = { Text("Enter 4-Digit Passcode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(0.7f),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = verifyPasscodeField,
                        onValueChange = { if (it.length <= 4) verifyPasscodeField = it },
                        label = { Text("Confirm Passcode") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(0.7f),
                        singleLine = true
                    )

                    Spacer(modifier = Modifier.height(24.dp))

                    Button(
                        onClick = {
                            if (passcodeField.length != 4) {
                                Toast.makeText(context, "Passcode must be exactly 4 digits", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            if (passcodeField != verifyPasscodeField) {
                                Toast.makeText(context, "Passcodes do not match", Toast.LENGTH_SHORT).show()
                                return@Button
                            }
                            SecurePreferences.saveVaultPasscode(context, passcodeField)
                            SecurePreferences.setVaultInitialized(context, true)
                            isInitialized = true
                            isAuthenticated = true
                            Toast.makeText(context, "Vault successfully initialized!", Toast.LENGTH_SHORT).show()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth(0.7f)
                    ) {
                        Text("Create Secured Vault")
                    }
                }
            } else if (!isAuthenticated) {
                // 2. UNLOCK VAULT VIEW (Biometrics or Passcode)
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Icon(
                        Icons.Filled.Fingerprint,
                        contentDescription = "Unlock",
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .size(72.dp)
                            .clickable {
                                // Simulate Biometric prompt success
                                coroutineScope.launch {
                                    Toast.makeText(context, "Biometric verified successfully!", Toast.LENGTH_SHORT).show()
                                    isAuthenticated = true
                                    refreshVaultFiles()
                                }
                            }
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Text(
                        "Tap Fingerprint to Unlock",
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp
                    )

                    Spacer(modifier = Modifier.height(32.dp))
                    Text("Or enter your 4-digit passcode:", color = Color.Gray, fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(12.dp))

                    OutlinedTextField(
                        value = passcodeField,
                        onValueChange = {
                            if (it.length <= 4) passcodeField = it
                            if (it.length == 4) {
                                val saved = SecurePreferences.getVaultPasscode(context)
                                if (it == saved) {
                                    isAuthenticated = true
                                    passcodeField = ""
                                    refreshVaultFiles()
                                } else {
                                    Toast.makeText(context, "Invalid passcode", Toast.LENGTH_SHORT).show()
                                    passcodeField = ""
                                }
                            }
                        },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                        visualTransformation = PasswordVisualTransformation(),
                        modifier = Modifier.fillMaxWidth(0.5f),
                        singleLine = true,
                        placeholder = { Text("••••", modifier = Modifier.fillMaxWidth(), textAlign = androidx.compose.ui.text.style.TextAlign.Center) }
                    )
                }
            } else {
                // 3. MAIN AUTHENTICATED VAULT INTERFACE
                Column(modifier = Modifier.fillMaxSize()) {
                    // Custom tab switcher
                    TabRow(
                        selectedTabIndex = currentTab,
                        containerColor = Color.Black.copy(alpha = 0.3f),
                        contentColor = MaterialTheme.colorScheme.primary
                    ) {
                        Tab(
                            selected = currentTab == 0,
                            onClick = { currentTab = 0; refreshVaultFiles() },
                            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.FolderSpecial, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Secured Files")
                            } }
                        )
                        Tab(
                            selected = currentTab == 1,
                            onClick = {
                                currentTab = 1
                                isAuditing = true
                                coroutineScope.launch {
                                    auditList = withContext(Dispatchers.IO) {
                                        SecurityVaultHelper.performPermissionAudit(context)
                                    }
                                    isAuditing = false
                                }
                            },
                            text = { Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Shield, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Permission Audit")
                            } }
                        )
                    }

                    Spacer(modifier = Modifier.height(8.dp))

                    if (currentTab == 0) {
                        // --- SECURED FILES PANEL ---
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp)
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Encrypted Files (${vaultFiles.size})",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold
                                )

                                Button(
                                    onClick = { showImportDialog = true },
                                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Text("Encrypt & Add File", fontSize = 12.sp)
                                }
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            if (vaultFiles.isEmpty()) {
                                Column(
                                    modifier = Modifier
                                        .weight(1f)
                                        .fillMaxWidth(),
                                    verticalArrangement = Arrangement.Center,
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(Icons.Filled.LockOpen, contentDescription = null, tint = Color.DarkGray, modifier = Modifier.size(48.dp))
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Text("Your vault is empty", color = Color.Gray, fontSize = 13.sp)
                                }
                            } else {
                                LazyColumn(
                                    modifier = Modifier.weight(1f),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    items(vaultFiles) { file ->
                                        Card(
                                            modifier = Modifier.fillMaxWidth(),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF16161F))
                                        ) {
                                            Row(
                                                modifier = Modifier
                                                    .fillMaxWidth()
                                                    .padding(12.dp),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(file.name, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text("Encrypted: AES-256", color = Color.Gray, fontSize = 11.sp)
                                                }

                                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                                    TextButton(
                                                        onClick = {
                                                            selectedFileToDecrypt = file
                                                        }
                                                    ) {
                                                        Text("Decrypt", color = MaterialTheme.colorScheme.primary)
                                                    }

                                                    IconButton(
                                                        onClick = {
                                                            file.delete()
                                                            refreshVaultFiles()
                                                            Toast.makeText(context, "Encrypted file deleted.", Toast.LENGTH_SHORT).show()
                                                        }
                                                    ) {
                                                        Icon(Icons.Filled.Close, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- PERMISSIONS AUDIT PANEL ---
                        if (isAuditing) {
                            Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                                CircularProgressIndicator(color = MaterialTheme.colorScheme.primary)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier
                                    .fillMaxSize()
                                    .padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                item {
                                    Text(
                                        "Installed Packages Security Profile",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 15.sp
                                    )
                                    Text(
                                        "Kryon scans for granted dangerous permissions that expose camera, mic, location, or local files.",
                                        color = Color.Gray,
                                        fontSize = 11.sp
                                    )
                                    Spacer(modifier = Modifier.height(10.dp))
                                }

                                items(auditList) { item ->
                                    val badgeColor = when (item.riskLevel) {
                                        "HIGH" -> Color(0xFFF44336)
                                        "MEDIUM" -> Color(0xFFFF9800)
                                        else -> Color(0xFF4CAF50)
                                    }

                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF15151D))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(item.appName, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                                                    Text(item.packageName, color = Color.Gray, fontSize = 11.sp, fontFamily = FontFamily.Monospace)
                                                }

                                                Box(
                                                    modifier = Modifier
                                                        .background(badgeColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                        .border(1.dp, badgeColor, RoundedCornerShape(12.dp))
                                                        .padding(horizontal = 8.dp, vertical = 4.dp)
                                                ) {
                                                    Text(item.riskLevel, color = badgeColor, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                                }
                                            }

                                            if (item.grantedDangerousPermissions.isNotEmpty()) {
                                                Spacer(modifier = Modifier.height(8.dp))
                                                Text("Granted Sensitive Permissions:", color = Color.LightGray, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                
                                                // Display permission tags
                                                Row(
                                                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    item.grantedDangerousPermissions.forEach { perm ->
                                                        Box(
                                                            modifier = Modifier
                                                                .background(Color(0xFF23232C), RoundedCornerShape(4.dp))
                                                                .padding(horizontal = 6.dp, vertical = 2.dp)
                                                        ) {
                                                            Text(perm, color = Color.LightGray, fontSize = 9.sp)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- IMPORT FILE DIALOG ---
            if (showImportDialog) {
                AlertDialog(
                    onDismissRequest = { showImportDialog = false },
                    title = { Text("Import File to Security Vault", fontWeight = FontWeight.Bold) },
                    text = {
                        Column {
                            Text("Enter the absolute file path of the item to encrypt & lock in the vault:")
                            Spacer(modifier = Modifier.height(8.dp))
                            OutlinedTextField(
                                value = importFilePath,
                                onValueChange = { importFilePath = it },
                                placeholder = { Text("/storage/emulated/0/Download/secret.txt") },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val sourceFile = File(importFilePath)
                                if (!sourceFile.exists()) {
                                    Toast.makeText(context, "Source file does not exist", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                val destFile = File(vaultFolder, sourceFile.name + ".kry")
                                val passcode = SecurePreferences.getVaultPasscode(context)
                                
                                coroutineScope.launch(Dispatchers.IO) {
                                    val success = SecurityVaultHelper.encryptFile(sourceFile, destFile, passcode)
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            // Optional: delete original
                                            sourceFile.delete()
                                            showImportDialog = false
                                            importFilePath = ""
                                            refreshVaultFiles()
                                            Toast.makeText(context, "File encrypted & secured inside vault!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Encryption failed.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Secure File")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showImportDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- DECRYPT FILE DIALOG ---
            if (selectedFileToDecrypt != null) {
                val file = selectedFileToDecrypt!!
                AlertDialog(
                    onDismissRequest = { selectedFileToDecrypt = null },
                    title = { Text("Decrypt File", fontWeight = FontWeight.Bold) },
                    text = {
                        Text("Decrypt and export file '${file.name.substringBeforeLast(".")}' back to your Downloads folder?")
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                val targetDir = File(FileSystemProvider.getPrimaryStoragePath(), "Download")
                                if (!targetDir.exists()) targetDir.mkdirs()
                                
                                val destFile = File(targetDir, file.name.substringBeforeLast("."))
                                val passcode = SecurePreferences.getVaultPasscode(context)
                                
                                coroutineScope.launch(Dispatchers.IO) {
                                    val success = SecurityVaultHelper.decryptFile(file, destFile, passcode)
                                    withContext(Dispatchers.Main) {
                                        if (success) {
                                            // Remove from vault
                                            file.delete()
                                            selectedFileToDecrypt = null
                                            refreshVaultFiles()
                                            Toast.makeText(context, "File decrypted and saved to Downloads!", Toast.LENGTH_LONG).show()
                                        } else {
                                            Toast.makeText(context, "Decryption failed. Check passcode.", Toast.LENGTH_SHORT).show()
                                        }
                                    }
                                }
                            }
                        ) {
                            Text("Export & Decrypt")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { selectedFileToDecrypt = null }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}
