package com.kryon.filemanager

import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.kryon.filemanager.adbshell.AdbSettingsView
import com.kryon.filemanager.features.apktools.ApkToolsView
import com.kryon.filemanager.features.database.SqliteEditorView
import com.kryon.filemanager.features.explorer.ExplorerView
import com.kryon.filemanager.features.hexeditor.HexEditorView
import com.kryon.filemanager.features.texteditor.TextEditorView
import com.kryon.filemanager.features.security.SecurityVaultView
import com.kryon.filemanager.features.network.NetworkToolsView
import com.kryon.filemanager.features.automation.AutomationEngineView
import com.kryon.filemanager.features.productivity.ProductivitySuiteView
import com.kryon.filemanager.features.search.SmartSearchView
import com.kryon.filemanager.ui.theme.MyApplicationTheme

sealed interface AppScreen {
    object Explorer : AppScreen
    data class TextEditor(val path: String) : AppScreen
    data class HexEditor(val path: String) : AppScreen
    data class SqliteEditor(val path: String) : AppScreen
    data class ApkTools(val path: String?) : AppScreen
    object AdbSettings : AppScreen
    object SecurityVault : AppScreen
    object NetworkTools : AppScreen
    object AutomationEngine : AppScreen
    data class ProductivitySuite(val initialPath: String = "") : AppScreen
    data class SmartSearch(val path: String) : AppScreen
}

class MainActivity : ComponentActivity() {
    private val storagePermissionCode = 1001

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Prompt storage permissions on launch
        checkAndRequestStoragePermissions()

        setContent {
            MyApplicationTheme {
                var currentScreen by remember { mutableStateOf<AppScreen>(AppScreen.Explorer) }

                // Dynamic Navigation System using simple, fast state transitions
                when (val screen = currentScreen) {
                    is AppScreen.Explorer -> {
                        ExplorerView(
                            onOpenTextEditor = { path -> currentScreen = AppScreen.TextEditor(path) },
                            onOpenHexEditor = { path -> currentScreen = AppScreen.HexEditor(path) },
                            onOpenSqliteEditor = { path -> currentScreen = AppScreen.SqliteEditor(path) },
                            onOpenApkTools = { path -> currentScreen = AppScreen.ApkTools(path) },
                            onOpenAdbSettings = { currentScreen = AppScreen.AdbSettings },
                            onOpenSecurityVault = { currentScreen = AppScreen.SecurityVault },
                            onOpenNetworkTools = { currentScreen = AppScreen.NetworkTools },
                            onOpenAutomationEngine = { currentScreen = AppScreen.AutomationEngine },
                            onOpenProductivitySuite = { currentScreen = AppScreen.ProductivitySuite() },
                            onOpenSmartSearch = { path -> currentScreen = AppScreen.SmartSearch(path) }
                        )
                    }
                    is AppScreen.TextEditor -> {
                        TextEditorView(
                            filePath = screen.path,
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.HexEditor -> {
                        HexEditorView(
                            filePath = screen.path,
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.SqliteEditor -> {
                        SqliteEditorView(
                            dbFilePath = screen.path,
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.ApkTools -> {
                        ApkToolsView(
                            apkFilePath = screen.path,
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.AdbSettings -> {
                        AdbSettingsView(
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.SecurityVault -> {
                        SecurityVaultView(
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.NetworkTools -> {
                        NetworkToolsView(
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.AutomationEngine -> {
                        AutomationEngineView(
                            onBack = { currentScreen = AppScreen.Explorer }
                        )
                    }
                    is AppScreen.ProductivitySuite -> {
                        ProductivitySuiteView(
                            onBack = { currentScreen = AppScreen.Explorer },
                            onOpenTextEditor = { path -> currentScreen = AppScreen.TextEditor(path) }
                        )
                    }
                    is AppScreen.SmartSearch -> {
                        SmartSearchView(
                            currentPath = screen.path,
                            onBack = { currentScreen = AppScreen.Explorer },
                            onOpenFile = { fileItem ->
                                when (fileItem.extension.lowercase()) {
                                    "txt", "log", "cfg", "env" -> currentScreen = AppScreen.TextEditor(fileItem.path)
                                    "apk" -> currentScreen = AppScreen.ApkTools(fileItem.path)
                                    "db", "sqlite" -> currentScreen = AppScreen.SqliteEditor(fileItem.path)
                                    else -> currentScreen = AppScreen.HexEditor(fileItem.path)
                                }
                            }
                        )
                    }
                }
            }
        }
    }


    private fun checkAndRequestStoragePermissions() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            // Android 11+ All Files Access
            if (!Environment.isExternalStorageManager()) {
                try {
                    val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                        data = Uri.parse("package:$packageName")
                    }
                    startActivity(intent)
                    Toast.makeText(this, "Please grant All Files Access to use Apex File Manager", Toast.LENGTH_LONG).show()
                } catch (e: Exception) {
                    val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                    startActivity(intent)
                }
            }
        } else {
            // Android 8-10 Legacy Permissions
            val readPerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.READ_EXTERNAL_STORAGE)
            val writePerm = ContextCompat.checkSelfPermission(this, android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            
            val listPermissionsNeeded = ArrayList<String>()
            if (readPerm != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(android.Manifest.permission.READ_EXTERNAL_STORAGE)
            }
            if (writePerm != PackageManager.PERMISSION_GRANTED) {
                listPermissionsNeeded.add(android.Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            
            if (listPermissionsNeeded.isNotEmpty()) {
                ActivityCompat.requestPermissions(this, listPermissionsNeeded.toTypedArray(), storagePermissionCode)
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == storagePermissionCode) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Toast.makeText(this, "Storage Permissions Granted", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this, "Storage Permissions Denied. Some folders won't load.", Toast.LENGTH_LONG).show()
            }
        }
    }
}
