package com.kryon.filemanager.features.explorer

import android.content.Intent
import android.net.Uri
import android.os.StrictMode
import androidx.activity.compose.BackHandler
import androidx.compose.animation.*
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.scale
import kotlinx.coroutines.delay
import androidx.compose.animation.core.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import com.kryon.filemanager.core.AccessMode
import com.kryon.filemanager.core.FileItem
import com.kryon.filemanager.core.FileSystemProvider
import com.kryon.filemanager.features.archive.ArchiveManager
import com.kryon.filemanager.features.copilot.AiCopilotFab
import com.kryon.filemanager.features.copilot.AiCopilotPanel
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class ActivePane {
    A, B
}

enum class ExplorerDialog {
    NONE,
    CREATE_FILE,
    CREATE_FOLDER,
    RENAME,
    DELETE_CONFIRM,
    PROPERTIES,
    CHMOD,
    ZIP_COMPRESS,
    ZIP_EXTRACT,
    BATCH_RENAME,
    MEDIA_PLAYER
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalFoundationApi::class)
@Composable
fun ExplorerView(
    onOpenTextEditor: (String) -> Unit,
    onOpenHexEditor: (String) -> Unit,
    onOpenSqliteEditor: (String) -> Unit,
    onOpenApkTools: (String?) -> Unit,
    onOpenAdbSettings: () -> Unit,
    onOpenSecurityVault: () -> Unit,
    onOpenNetworkTools: () -> Unit,
    onOpenAutomationEngine: () -> Unit,
    onOpenProductivitySuite: () -> Unit,
    onOpenSmartSearch: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val configuration = LocalConfiguration.current
    val isLandscape = configuration.orientation == android.content.res.Configuration.ORIENTATION_LANDSCAPE
    val haptic = LocalHapticFeedback.current
    val context = LocalContext.current

    // Copilot sheet open state
    var isCopilotOpen by remember { mutableStateOf(false) }


    // Path State
    var pathA by remember { mutableStateOf(FileSystemProvider.getPrimaryStoragePath()) }
    var pathB by remember { mutableStateOf(FileSystemProvider.getPrimaryStoragePath()) }
    var activePane by remember { mutableStateOf(ActivePane.A) }

    // Files List State
    var filesA by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var filesB by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isLoading by remember { mutableStateOf(false) }

    // Selection State
    val selectedPaths = remember { mutableStateListOf<String>() }

    // Search and Sort
    var searchQuery by remember { mutableStateOf("") }
    var sortBy by remember { mutableStateOf("name") } // name, size, date

    // Dialog state
    var activeDialog by remember { mutableStateOf(ExplorerDialog.NONE) }
    var targetFile by remember { mutableStateOf<FileItem?>(null) }
    var dialogInputName by remember { mutableStateOf("") }
    var dialogInputChmod by remember { mutableStateOf("755") }

    // Batch Rename States
    var batchRenamePattern by remember { mutableStateOf("{name}_###") }
    var batchRenamePrefix by remember { mutableStateOf("") }
    var batchRenameSuffix by remember { mutableStateOf("") }
    var batchRenameFind by remember { mutableStateOf("") }
    var batchRenameReplace by remember { mutableStateOf("") }
    var isRegexEnabled by remember { mutableStateOf(false) }
    var numberingStart by remember { mutableStateOf(1) }
    var lastRenameList by remember { mutableStateOf<List<Pair<String, String>>?>(null) }

    // Smart Features UI Toggles
    var showCommandPalette by remember { mutableStateOf(false) }
    var showContextActionSheet by remember { mutableStateOf(false) }
    var showModeSelectionSheet by remember { mutableStateOf(false) }
    var showDashboard by remember { mutableStateOf(true) }

    // Operation logs
    var statusLog by remember { mutableStateOf("") }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    // Collect current mode reactively
    val currentAccessMode by FileSystemProvider.currentModeFlow.collectAsState()

    // Back stacks for Pane A and Pane B
    val backStackA = remember { mutableStateListOf<String>() }
    val backStackB = remember { mutableStateListOf<String>() }

    // Navigation functions
    val navigateToPathA: (String) -> Unit = { newPath ->
        if (pathA != newPath) {
            backStackA.add(pathA)
            pathA = newPath
        }
    }

    val navigateToPathB: (String) -> Unit = { newPath ->
        if (pathB != newPath) {
            backStackB.add(pathB)
            pathB = newPath
        }
    }

    // Intercept back presses to navigate backwards in the active pane's history
    BackHandler(enabled = !showDashboard) {
        if (activePane == ActivePane.A && backStackA.isNotEmpty()) {
            pathA = backStackA.removeAt(backStackA.size - 1)
        } else if (activePane == ActivePane.B && backStackB.isNotEmpty()) {
            pathB = backStackB.removeAt(backStackB.size - 1)
        } else {
            showDashboard = true
        }
    }

    // Function to reload active pane files
    val reloadFiles: () -> Unit = {
        coroutineScope.launch {
            isLoading = true
            try {
                filesA = FileSystemProvider.listFiles(pathA)
            } catch (e: Exception) { e.printStackTrace() }
            try {
                filesB = FileSystemProvider.listFiles(pathB)
            } catch (e: Exception) { e.printStackTrace() }
            isLoading = false
        }
    }

    // Load filesA on pathA or currentAccessMode change
    LaunchedEffect(pathA, currentAccessMode) {
        isLoading = true
        try {
            filesA = FileSystemProvider.listFiles(pathA)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    // Load filesB on pathB or currentAccessMode change
    LaunchedEffect(pathB, currentAccessMode) {
        isLoading = true
        try {
            filesB = FileSystemProvider.listFiles(pathB)
        } catch (e: Exception) {
            e.printStackTrace()
        } finally {
            isLoading = false
        }
    }

    val currentPath = if (activePane == ActivePane.A) pathA else pathB
    val currentFiles = if (activePane == ActivePane.A) filesA else filesB

    // Sort and Filter files based on search and sort-mode
    val filteredFiles = remember(currentFiles, searchQuery, sortBy) {
        val baseList = if (searchQuery.isEmpty()) {
            currentFiles
        } else {
            currentFiles.filter { it.name.contains(searchQuery, ignoreCase = true) }
        }
        
        when (sortBy) {
            "size" -> baseList.sortedWith(compareBy({ !it.isDirectory }, { -it.size }))
            "date" -> baseList.sortedWith(compareBy({ !it.isDirectory }, { -it.lastModified }))
            else -> baseList.sortedWith(compareBy({ !it.isDirectory }, { it.name.lowercase() }))
        }
    }

    // Command Palette actions list
    val commandList = remember {
        listOf(
            CommandPaletteItem(
                title = "Open ADB Wireless Pairing",
                description = "Configure wireless debugging pairing & pairing code",
                category = "ADB",
                icon = Icons.Default.SettingsInputAntenna,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenAdbSettings()
                }
            ),
            CommandPaletteItem(
                title = "App & APK Extractor",
                description = "Extract and analyze installed system applications",
                category = "Tools",
                icon = Icons.Default.Android,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    onOpenApkTools(null)
                }
            ),
            CommandPaletteItem(
                title = "Create New File",
                description = "Initialize an empty file in the active folder",
                category = "Operations",
                icon = Icons.Default.Create,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dialogInputName = ""
                    activeDialog = ExplorerDialog.CREATE_FILE
                }
            ),
            CommandPaletteItem(
                title = "Create New Folder",
                description = "Create a new folder in the active folder",
                category = "Operations",
                icon = Icons.Default.CreateNewFolder,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    dialogInputName = ""
                    activeDialog = ExplorerDialog.CREATE_FOLDER
                }
            ),
            CommandPaletteItem(
                title = "Switch to Standard (SAF) Mode",
                description = "Standard local file operations with Storage Access Framework",
                category = "Access Mode",
                icon = Icons.Default.Folder,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    FileSystemProvider.currentMode = AccessMode.LOCAL_SAF
                    reloadFiles()
                }
            ),
            CommandPaletteItem(
                title = "Switch to ROOT Access Mode",
                description = "Elevated system-wide superuser root access",
                category = "Access Mode",
                icon = Icons.Default.Shield,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (com.kryon.filemanager.core.ShellService.isRootAvailable()) {
                        FileSystemProvider.currentMode = AccessMode.ROOT
                        reloadFiles()
                    } else {
                        statusLog = "ROOT is unavailable on this device."
                    }
                }
            ),
            CommandPaletteItem(
                title = "Go to /Android/data",
                description = "Quick mount Android app data folder",
                category = "Navigation",
                icon = Icons.Default.CloudQueue,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = "${FileSystemProvider.getPrimaryStoragePath()}/Android/data"
                    if (activePane == ActivePane.A) pathA = target else pathB = target
                    showDashboard = false
                }
            ),
            CommandPaletteItem(
                title = "Go to /Android/obb",
                description = "Quick mount Android obb games folder",
                category = "Navigation",
                icon = Icons.Default.SdCard,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = "${FileSystemProvider.getPrimaryStoragePath()}/Android/obb"
                    if (activePane == ActivePane.A) pathA = target else pathB = target
                    showDashboard = false
                }
            ),
            CommandPaletteItem(
                title = "Go to Downloads",
                description = "Navigate directly to system Downloads folder",
                category = "Navigation",
                icon = Icons.Default.Download,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = "${FileSystemProvider.getPrimaryStoragePath()}/Download"
                    if (activePane == ActivePane.A) pathA = target else pathB = target
                    showDashboard = false
                }
            ),
            CommandPaletteItem(
                title = "Go to Internal Storage",
                description = "Navigate to standard primary emulator storage directory",
                category = "Navigation",
                icon = Icons.Default.FolderOpen,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    val target = FileSystemProvider.getPrimaryStoragePath()
                    if (activePane == ActivePane.A) pathA = target else pathB = target
                    showDashboard = false
                }
            ),
            CommandPaletteItem(
                title = "Go to System Root",
                description = "Navigate to primary system files partition root",
                category = "Navigation",
                icon = Icons.Default.Shield,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    if (activePane == ActivePane.A) pathA = "/" else pathB = "/"
                    showDashboard = false
                }
            ),
            CommandPaletteItem(
                title = "Show Dashboard / Home Screen",
                description = "Show main device storage statistics and shortcut bookmarks",
                category = "Navigation",
                icon = Icons.Default.Dashboard,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    showDashboard = true
                }
            ),
            CommandPaletteItem(
                title = "Sort by Name",
                description = "Sort folders and files in ascending alphabetical order",
                category = "Display",
                icon = Icons.Default.SortByAlpha,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sortBy = "name"
                }
            ),
            CommandPaletteItem(
                title = "Sort by Size",
                description = "Sort folders and files based on disk footprint size",
                category = "Display",
                icon = Icons.Default.Sort,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sortBy = "size"
                }
            ),
            CommandPaletteItem(
                title = "Sort by Date",
                description = "Sort folders and files based on last-modified timestamp",
                category = "Display",
                icon = Icons.Default.DateRange,
                action = {
                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                    sortBy = "date"
                }
            )
        )
    }

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            ModalDrawerSheet(
                modifier = Modifier.width(280.dp),
                drawerContainerColor = Color(0xFF101015)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Drawer Header
                    Text(
                        text = "KRYON MANAGER",
                        fontWeight = FontWeight.Bold,
                        fontSize = 20.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.padding(bottom = 24.dp)
                    )

                    // Access Mode Card
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 16.dp)
                            .clickable { showModeSelectionSheet = true },
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1E26))
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(text = "ACCESS MODE", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = when (currentAccessMode) {
                                    AccessMode.ROOT -> "ROOT ACCESS ENABLED"
                                    AccessMode.ADB -> "ADB WIRELESS SHELL"
                                    AccessMode.LOCAL_SAF -> "STANDARD LOCAL (SAF)"
                                },
                                fontWeight = FontWeight.Bold,
                                color = when (currentAccessMode) {
                                    AccessMode.ROOT -> Color(0xFF4CAF50)
                                    AccessMode.ADB -> Color(0xFF00E5FF)
                                    AccessMode.LOCAL_SAF -> Color(0xFFFFB300)
                                },
                                fontSize = 13.sp
                            )
                        }
                    }

                    Text(text = "QUICK LINKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Dashboard link
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Dashboard, contentDescription = null) },
                        label = { Text("Home Dashboard") },
                        selected = showDashboard,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            showDashboard = true
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Search, contentDescription = null) },
                        label = { Text("Smart Search 2.0") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenSmartSearch(currentPath)
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Lock, contentDescription = null) },
                        label = { Text("Security Vault") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenSecurityVault()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Wifi, contentDescription = null) },
                        label = { Text("Network Tools") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenNetworkTools()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Settings, contentDescription = null) },
                        label = { Text("Automation Engine") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenAutomationEngine()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Code, contentDescription = null) },
                        label = { Text("Productivity Suite") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenProductivitySuite()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )


                    // Developer Utilities
                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.Android, contentDescription = null) },
                        label = { Text("App & APK Extractor") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenApkTools(null)
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    NavigationDrawerItem(
                        icon = { Icon(Icons.Default.SettingsInputAntenna, contentDescription = null) },
                        label = { Text("ADB Wireless Pairing") },
                        selected = false,
                        onClick = {
                            coroutineScope.launch { drawerState.close() }
                            onOpenAdbSettings()
                        },
                        colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                    )

                    Spacer(modifier = Modifier.height(16.dp))
                    Text(text = "STORAGE BOOKMARKS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
                    Spacer(modifier = Modifier.height(8.dp))

                    // Bookmarks list
                    listOf(
                        "Internal Storage" to FileSystemProvider.getPrimaryStoragePath(),
                        "Download Directory" to "${FileSystemProvider.getPrimaryStoragePath()}/Download",
                        "Android data" to "${FileSystemProvider.getPrimaryStoragePath()}/Android/data",
                        "Android obb" to "${FileSystemProvider.getPrimaryStoragePath()}/Android/obb",
                        "System Root" to "/"
                    ).forEach { (label, path) ->
                        NavigationDrawerItem(
                            icon = { Icon(Icons.Default.Folder, contentDescription = null) },
                            label = { Text(label, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                            selected = !showDashboard && currentPath == path,
                            onClick = {
                                coroutineScope.launch { drawerState.close() }
                                if (activePane == ActivePane.A) pathA = path else pathB = path
                                showDashboard = false
                            },
                            colors = NavigationDrawerItemDefaults.colors(unselectedContainerColor = Color.Transparent)
                        )
                    }
                }
            }
        }
    ) {
        Scaffold(
            topBar = {
                Column(
                    modifier = Modifier.background(
                        Brush.verticalGradient(
                            colors = listOf(Color.Black.copy(alpha = 0.8f), Color.Transparent)
                        )
                    )
                ) {
                    TopAppBar(
                        title = {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = "Kryon",
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp
                                )
                                Spacer(modifier = Modifier.width(12.dp))
                                // Unified Mode Indicator Chip
                                AssistChip(
                                    onClick = { showModeSelectionSheet = true },
                                    label = {
                                        Text(
                                            text = when (currentAccessMode) {
                                                AccessMode.ROOT -> "Root"
                                                AccessMode.ADB -> "ADB Shell"
                                                AccessMode.LOCAL_SAF -> "SAF Mode"
                                            },
                                            fontSize = 10.sp,
                                            fontWeight = FontWeight.Bold,
                                            color = when (currentAccessMode) {
                                                AccessMode.ROOT -> Color(0xFF4CAF50)
                                                AccessMode.ADB -> Color(0xFF00E5FF)
                                                AccessMode.LOCAL_SAF -> Color(0xFFFFB300)
                                            }
                                        )
                                    },
                                    leadingIcon = {
                                        Icon(
                                            imageVector = when (currentAccessMode) {
                                                AccessMode.ROOT -> Icons.Default.Shield
                                                AccessMode.ADB -> Icons.Default.SettingsInputAntenna
                                                AccessMode.LOCAL_SAF -> Icons.Default.Folder
                                            },
                                            contentDescription = "Access Mode",
                                            tint = when (currentAccessMode) {
                                                AccessMode.ROOT -> Color(0xFF4CAF50)
                                                AccessMode.ADB -> Color(0xFF00E5FF)
                                                AccessMode.LOCAL_SAF -> Color(0xFFFFB300)
                                            },
                                            modifier = Modifier.size(14.dp)
                                        )
                                    },
                                    colors = AssistChipDefaults.assistChipColors(
                                        containerColor = Color.Black.copy(alpha = 0.4f),
                                        labelColor = Color.White
                                    ),
                                    border = BorderStroke(
                                        width = 1.dp,
                                        color = when (currentAccessMode) {
                                            AccessMode.ROOT -> Color(0xFF4CAF50).copy(alpha = 0.4f)
                                            AccessMode.ADB -> Color(0xFF00E5FF).copy(alpha = 0.4f)
                                            AccessMode.LOCAL_SAF -> Color(0xFFFFB300).copy(alpha = 0.4f)
                                        }
                                    )
                                )
                            }
                        },
                        navigationIcon = {
                            IconButton(onClick = { coroutineScope.launch { drawerState.open() } }) {
                                Icon(Icons.Default.Menu, contentDescription = "Menu")
                            }
                        },
                        actions = {
                            // Command Palette Launcher Action
                            IconButton(onClick = { showCommandPalette = true }) {
                                Icon(Icons.Default.FlashOn, contentDescription = "Command Palette Launcher", tint = Color(0xFF00E5FF))
                            }

                            // Dashboard / Files Toggle
                            IconButton(onClick = { showDashboard = !showDashboard }) {
                                Icon(
                                    imageVector = if (showDashboard) Icons.Default.FolderOpen else Icons.Default.Dashboard,
                                    contentDescription = "Toggle Dashboard / Files",
                                    tint = Color.White
                                )
                            }
                        },
                        colors = TopAppBarDefaults.topAppBarColors(containerColor = Color.Transparent)
                    )

                    // Breadcrumb navigation row
                    if (!showDashboard) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .background(Color.Black.copy(alpha = 0.3f))
                                .padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Path: ",
                                fontSize = 12.sp,
                                color = Color.Gray,
                                fontWeight = FontWeight.Bold
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = currentPath,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                fontFamily = FontFamily.Monospace,
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable {
                                        // Go up to parent folder on path click
                                        val parent = File(currentPath).parent
                                        if (parent != null) {
                                            if (activePane == ActivePane.A) navigateToPathA(parent) else navigateToPathB(parent)
                                        }
                                    }
                            )
                            if (currentPath != "/") {
                                IconButton(
                                    onClick = {
                                        val parent = File(currentPath).parent
                                        if (parent != null) {
                                            if (activePane == ActivePane.A) navigateToPathA(parent) else navigateToPathB(parent)
                                        }
                                    },
                                    modifier = Modifier.size(24.dp)
                                ) {
                                    Icon(
                                        Icons.Default.ArrowUpward,
                                        contentDescription = "Up",
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }

                        // Dual pane controller tabs for mobile portrait devices
                        if (!isLandscape) {
                            TabRow(
                                selectedTabIndex = if (activePane == ActivePane.A) 0 else 1,
                                containerColor = Color.Black.copy(alpha = 0.5f),
                                contentColor = MaterialTheme.colorScheme.primary
                            ) {
                                Tab(
                                    selected = activePane == ActivePane.A,
                                    onClick = { activePane = ActivePane.A },
                                    text = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("PANE A", fontWeight = FontWeight.Bold)
                                            Text(
                                                pathA.split("/").last().ifEmpty { "/" },
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                )
                                Tab(
                                    selected = activePane == ActivePane.B,
                                    onClick = { activePane = ActivePane.B },
                                    text = {
                                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                            Text("PANE B", fontWeight = FontWeight.Bold)
                                            Text(
                                                pathB.split("/").last().ifEmpty { "/" },
                                                fontSize = 10.sp,
                                                color = Color.Gray
                                            )
                                        }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        ) { innerPadding ->
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .background(Color(0xFF0A0A0E))
            ) {
                if (showDashboard) {
                    // Show Smart Storage Dashboard
                    StorageDashboard(
                        onNavigateToPath = { path ->
                            if (activePane == ActivePane.A) pathA = path else pathB = path
                            showDashboard = false
                        },
                        onOpenFile = { file ->
                            targetFile = file
                            showContextActionSheet = true
                        },
                        onOpenAdb = onOpenAdbSettings
                    )
                } else if (isLandscape) {
                    // SIDE-BY-SIDE DUAL PANE VIEW FOR TABLETS
                    Row(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(6.dp),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        // Pane A Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = 1.5.dp,
                                    color = if (activePane == ActivePane.A) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.DarkGray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(2.dp)
                        ) {
                            PaneList(
                                files = filesA,
                                path = pathA,
                                onPathChanged = { navigateToPathA(it) },
                                selectedPaths = selectedPaths,
                                onOpenTextEditor = onOpenTextEditor,
                                onOpenHexEditor = onOpenHexEditor,
                                onOpenSqliteEditor = onOpenSqliteEditor,
                                onOpenApkTools = onOpenApkTools,
                                onFileLongClicked = { item ->
                                    activePane = ActivePane.A
                                    targetFile = item
                                    showContextActionSheet = true
                                },
                                onFocused = { activePane = ActivePane.A },
                                onOpenMediaPlayer = { item ->
                                    targetFile = item
                                    activeDialog = ExplorerDialog.MEDIA_PLAYER
                                }
                            )
                        }

                        // Pane B Box
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .border(
                                    width = 1.5.dp,
                                    color = if (activePane == ActivePane.B) MaterialTheme.colorScheme.primary.copy(alpha = 0.8f) else Color.DarkGray.copy(alpha = 0.3f),
                                    shape = RoundedCornerShape(8.dp)
                                )
                                .padding(2.dp)
                        ) {
                            PaneList(
                                files = filesB,
                                path = pathB,
                                onPathChanged = { navigateToPathB(it) },
                                selectedPaths = selectedPaths,
                                onOpenTextEditor = onOpenTextEditor,
                                onOpenHexEditor = onOpenHexEditor,
                                onOpenSqliteEditor = onOpenSqliteEditor,
                                onOpenApkTools = onOpenApkTools,
                                onFileLongClicked = { item ->
                                    activePane = ActivePane.B
                                    targetFile = item
                                    showContextActionSheet = true
                                },
                                onFocused = { activePane = ActivePane.B },
                                onOpenMediaPlayer = { item ->
                                    targetFile = item
                                    activeDialog = ExplorerDialog.MEDIA_PLAYER
                                }
                            )
                        }
                    }
                } else {
                    // SINGLE PANE PORTRAIT VIEW
                    PaneList(
                        files = filteredFiles,
                        path = currentPath,
                        onPathChanged = { newPath ->
                            if (activePane == ActivePane.A) navigateToPathA(newPath) else navigateToPathB(newPath)
                        },
                        selectedPaths = selectedPaths,
                        onOpenTextEditor = onOpenTextEditor,
                        onOpenHexEditor = onOpenHexEditor,
                        onOpenSqliteEditor = onOpenSqliteEditor,
                        onOpenApkTools = onOpenApkTools,
                        onFileLongClicked = { item ->
                            targetFile = item
                            showContextActionSheet = true
                        },
                        onFocused = {},
                        onOpenMediaPlayer = { item ->
                            targetFile = item
                            activeDialog = ExplorerDialog.MEDIA_PLAYER
                        }
                    )
                }

                if (isLoading && !showDashboard) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.primary
                    )
                }

                // Batch Selection & Operations Floating Card Panel
                if (selectedPaths.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .fillMaxWidth()
                            .padding(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xEB14141A)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${selectedPaths.size} Selected",
                                color = MaterialTheme.colorScheme.primary,
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            
                            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                                // COPY
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        statusLog = "Copying files..."
                                        val targetDir = if (activePane == ActivePane.A) pathB else pathA
                                        for (src in selectedPaths) {
                                            val dest = "$targetDir/${src.split("/").last()}"
                                            FileSystemProvider.copy(src, dest)
                                        }
                                        selectedPaths.clear()
                                        reloadFiles()
                                        statusLog = "Copied successfully."
                                    }
                                }) {
                                    Icon(Icons.Default.ContentCopy, contentDescription = "Copy to opposite Pane")
                                }

                                // MOVE
                                IconButton(onClick = {
                                    coroutineScope.launch {
                                        statusLog = "Moving files..."
                                        val targetDir = if (activePane == ActivePane.A) pathB else pathA
                                        for (src in selectedPaths) {
                                            val dest = "$targetDir/${src.split("/").last()}"
                                            FileSystemProvider.move(src, dest)
                                        }
                                        selectedPaths.clear()
                                        reloadFiles()
                                        statusLog = "Moved successfully."
                                    }
                                }) {
                                    Icon(Icons.Default.ContentCut, contentDescription = "Move to opposite Pane")
                                }

                                // DELETE
                                IconButton(onClick = {
                                    activeDialog = ExplorerDialog.DELETE_CONFIRM
                                }) {
                                    Icon(Icons.Default.Delete, contentDescription = "Delete files", tint = Color.Red)
                                }

                                // ZIP ARCHIVE
                                IconButton(onClick = {
                                    activeDialog = ExplorerDialog.ZIP_COMPRESS
                                }) {
                                    Icon(Icons.Default.Archive, contentDescription = "Compress into zip")
                                }

                                // BATCH RENAME
                                IconButton(onClick = {
                                    activeDialog = ExplorerDialog.BATCH_RENAME
                                }) {
                                    Icon(Icons.Default.DriveFileRenameOutline, contentDescription = "Batch Rename selected files", tint = Color(0xFF00E5FF))
                                }

                                // Clear selection
                                TextButton(onClick = { selectedPaths.clear() }) {
                                    Text("Cancel")
                                }
                            }
                        }
                    }
                }

                // Undo Batch Rename Bar
                if (lastRenameList != null) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 150.dp, start = 16.dp, end = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1F1F29)),
                        border = BorderStroke(1.dp, Color(0xFFFFB300).copy(alpha = 0.4f))
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = "Batch Rename complete.", color = Color.White, fontSize = 13.sp)
                            TextButton(onClick = {
                                coroutineScope.launch {
                                    val listToUndo = lastRenameList
                                    if (listToUndo != null) {
                                        statusLog = "Undoing batch rename..."
                                        // Undo in reverse order
                                        for (pair in listToUndo.reversed()) {
                                            val currentPath = pair.second
                                            val originalPath = pair.first
                                            val originalName = originalPath.substringAfterLast('/')
                                            FileSystemProvider.rename(currentPath, originalName)
                                        }
                                        lastRenameList = null
                                        reloadFiles()
                                        statusLog = "Batch rename undone."
                                    }
                                }
                            }) {
                                Text("Undo", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                            }
                            IconButton(onClick = { lastRenameList = null }) {
                                Icon(Icons.Default.Close, contentDescription = "Dismiss", tint = Color.Gray, modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Logging output bar
                if (statusLog.isNotEmpty()) {
                    Card(
                        modifier = Modifier
                            .align(Alignment.BottomCenter)
                            .padding(bottom = 90.dp, start = 16.dp, end = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
                    ) {
                        Row(
                            modifier = Modifier.padding(12.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(text = statusLog, fontSize = 13.sp)
                            TextButton(onClick = { statusLog = "" }) {
                                Text("OK")
                            }
                        }
                    }
                }

                // AI Copilot FAB and slide-up overlay
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomEnd)
                        .padding(bottom = 80.dp)
                ) {
                    AiCopilotFab(onClick = { isCopilotOpen = true })
                }

                AiCopilotPanel(
                    isOpen = isCopilotOpen,
                    onClose = { isCopilotOpen = false },
                    currentPath = currentPath,
                    onRefreshFiles = { reloadFiles() }
                )
            }
        }


        // --- DIALOGS CONTROLLERS ---
        if (activeDialog != ExplorerDialog.NONE) {
            when (activeDialog) {
                ExplorerDialog.CREATE_FILE -> {
                    AlertDialog(
                        onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                        title = { Text("Create File") },
                        text = {
                            OutlinedTextField(
                                value = dialogInputName,
                                onValueChange = { dialogInputName = it },
                                label = { Text("File Name") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        FileSystemProvider.createFile(currentPath, dialogInputName)
                                        reloadFiles()
                                        activeDialog = ExplorerDialog.NONE
                                    }
                                }
                            ) { Text("Create") }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                        }
                    )
                }

                ExplorerDialog.CREATE_FOLDER -> {
                    AlertDialog(
                        onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                        title = { Text("Create Folder") },
                        text = {
                            OutlinedTextField(
                                value = dialogInputName,
                                onValueChange = { dialogInputName = it },
                                label = { Text("Folder Name") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        FileSystemProvider.createDirectory(currentPath, dialogInputName)
                                        reloadFiles()
                                        activeDialog = ExplorerDialog.NONE
                                    }
                                }
                            ) { Text("Create") }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                        }
                    )
                }

                ExplorerDialog.DELETE_CONFIRM -> {
                    AlertDialog(
                        onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                        title = { Text("Delete Files?") },
                        text = { Text("Are you sure you want to permanently delete these ${selectedPaths.size} files?") },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        for (p in selectedPaths) {
                                            FileSystemProvider.delete(p)
                                        }
                                        selectedPaths.clear()
                                        reloadFiles()
                                        activeDialog = ExplorerDialog.NONE
                                    }
                                },
                                colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                            ) { Text("Delete") }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                        }
                    )
                }

                ExplorerDialog.PROPERTIES -> {
                    val item = targetFile
                    if (item != null) {
                        AlertDialog(
                            onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                            title = { Text(item.name) },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text("Full Path: ${item.path}", fontSize = 13.sp)
                                    Text("Size: ${if (item.isDirectory) "Directory" else "${item.size / 1024} KB"}", fontSize = 13.sp)
                                    Text("Permissions: ${item.permissions}", fontSize = 13.sp)
                                    
                                    Spacer(modifier = Modifier.height(8.dp))
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                dialogInputName = item.name
                                                activeDialog = ExplorerDialog.RENAME
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text("Rename") }

                                        Button(
                                            onClick = {
                                                dialogInputChmod = "755"
                                                activeDialog = ExplorerDialog.CHMOD
                                            },
                                            modifier = Modifier.weight(1f),
                                            colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.secondary)
                                        ) { Text("Chmod") }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Close") }
                            }
                        )
                    }
                }

                ExplorerDialog.RENAME -> {
                    val item = targetFile
                    if (item != null) {
                        AlertDialog(
                            onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                            title = { Text("Rename File") },
                            text = {
                                OutlinedTextField(
                                    value = dialogInputName,
                                    onValueChange = { dialogInputName = it },
                                    label = { Text("New Name") },
                                    singleLine = true
                                )
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            FileSystemProvider.rename(item.path, dialogInputName)
                                            reloadFiles()
                                            activeDialog = ExplorerDialog.NONE
                                        }
                                    }
                                ) { Text("Apply") }
                            },
                            dismissButton = {
                                TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                            }
                        )
                    }
                }

                ExplorerDialog.BATCH_RENAME -> {
                    AlertDialog(
                        onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                        title = { Text("Batch Rename ${selectedPaths.size} Files", color = Color.White) },
                        text = {
                            Column(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .verticalScroll(rememberScrollState()),
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Text(
                                    "Use pattern {name} for original file name, ### for sequential numbering.",
                                    fontSize = 11.sp,
                                    color = Color.Gray
                                )
                                
                                OutlinedTextField(
                                    value = batchRenamePattern,
                                    onValueChange = { batchRenamePattern = it },
                                    label = { Text("Pattern") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    OutlinedTextField(
                                        value = batchRenamePrefix,
                                        onValueChange = { batchRenamePrefix = it },
                                        label = { Text("Prefix") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = batchRenameSuffix,
                                        onValueChange = { batchRenameSuffix = it },
                                        label = { Text("Suffix") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    OutlinedTextField(
                                        value = batchRenameFind,
                                        onValueChange = { batchRenameFind = it },
                                        label = { Text("Find Text") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OutlinedTextField(
                                        value = batchRenameReplace,
                                        onValueChange = { batchRenameReplace = it },
                                        label = { Text("Replace") },
                                        singleLine = true,
                                        modifier = Modifier.weight(1f)
                                    )
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        Checkbox(
                                            checked = isRegexEnabled,
                                            onCheckedChange = { isRegexEnabled = it }
                                        )
                                        Text("Use Regex", fontSize = 12.sp, color = Color.LightGray)
                                    }
                                    
                                    OutlinedTextField(
                                        value = numberingStart.toString(),
                                        onValueChange = { numberingStart = it.toIntOrNull() ?: 1 },
                                        label = { Text("Start Num") },
                                        singleLine = true,
                                        modifier = Modifier.width(100.dp)
                                    )
                                }

                                Divider(color = Color.White.copy(alpha = 0.12f))

                                Text(
                                    "LIVE PREVIEW:",
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color(0xFF00E5FF)
                                )

                                selectedPaths.forEachIndexed { index, path ->
                                    val originalName = path.substringAfterLast('/')
                                    
                                    var resultName = originalName
                                    if (batchRenameFind.isNotEmpty()) {
                                        resultName = if (isRegexEnabled) {
                                            try {
                                                resultName.replace(Regex(batchRenameFind), batchRenameReplace)
                                            } catch (e: Exception) {
                                                resultName
                                            }
                                        } else {
                                            resultName.replace(batchRenameFind, batchRenameReplace)
                                        }
                                    }
                                    if (batchRenamePattern.isNotEmpty()) {
                                        val ext = originalName.substringAfterLast('.', "")
                                        val baseName = originalName.substringBeforeLast('.')
                                        val formattedNumber = String.format("%03d", numberingStart + index)
                                        resultName = batchRenamePattern
                                            .replace("{name}", baseName)
                                            .replace("###", formattedNumber)
                                            .replace("{num}", formattedNumber)
                                        if (ext.isNotEmpty() && !resultName.contains(".")) {
                                            resultName = "$resultName.$ext"
                                        }
                                    }
                                    if (batchRenamePrefix.isNotEmpty() || batchRenameSuffix.isNotEmpty()) {
                                        val ext = resultName.substringAfterLast('.', "")
                                        val baseName = resultName.substringBeforeLast('.')
                                        resultName = "$batchRenamePrefix$baseName$batchRenameSuffix"
                                        if (ext.isNotEmpty()) {
                                            resultName = "$resultName.$ext"
                                        }
                                    }

                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .background(Color.White.copy(alpha = 0.03f), RoundedCornerShape(8.dp))
                                            .padding(8.dp)
                                    ) {
                                        Text(originalName, color = Color.Gray, fontSize = 11.sp)
                                        Icon(
                                            imageVector = Icons.Default.ArrowDownward,
                                            contentDescription = null,
                                            tint = Color(0xFFFFB300),
                                            modifier = Modifier.size(14.dp)
                                        )
                                        Text(resultName, color = Color(0xFF00E5FF), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                    }
                                }
                            }
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val renameHistory = mutableListOf<Pair<String, String>>()
                                        
                                        selectedPaths.forEachIndexed { index, path ->
                                            val originalName = path.substringAfterLast('/')
                                            val parentDir = path.substringBeforeLast('/')
                                            
                                            var resultName = originalName
                                            if (batchRenameFind.isNotEmpty()) {
                                                resultName = if (isRegexEnabled) {
                                                    try {
                                                        resultName.replace(Regex(batchRenameFind), batchRenameReplace)
                                                    } catch (e: Exception) {
                                                        resultName
                                                    }
                                                } else {
                                                    resultName.replace(batchRenameFind, batchRenameReplace)
                                                }
                                            }
                                            if (batchRenamePattern.isNotEmpty()) {
                                                val ext = originalName.substringAfterLast('.', "")
                                                val baseName = originalName.substringBeforeLast('.')
                                                val formattedNumber = String.format("%03d", numberingStart + index)
                                                resultName = batchRenamePattern
                                                    .replace("{name}", baseName)
                                                    .replace("###", formattedNumber)
                                                    .replace("{num}", formattedNumber)
                                                if (ext.isNotEmpty() && !resultName.contains(".")) {
                                                    resultName = "$resultName.$ext"
                                                }
                                            }
                                            if (batchRenamePrefix.isNotEmpty() || batchRenameSuffix.isNotEmpty()) {
                                                val ext = resultName.substringAfterLast('.', "")
                                                val baseName = resultName.substringBeforeLast('.')
                                                resultName = "$batchRenamePrefix$baseName$batchRenameSuffix"
                                                if (ext.isNotEmpty()) {
                                                    resultName = "$resultName.$ext"
                                                }
                                            }
                                            
                                            val newPath = "$parentDir/$resultName"
                                            if (path != newPath) {
                                                val ok = FileSystemProvider.rename(path, resultName)
                                                if (ok) {
                                                    renameHistory.add(Pair(path, newPath))
                                                }
                                            }
                                        }
                                        
                                        if (renameHistory.isNotEmpty()) {
                                            lastRenameList = renameHistory
                                            statusLog = "Renamed ${renameHistory.size} files successfully."
                                        }
                                        selectedPaths.clear()
                                        reloadFiles()
                                        activeDialog = ExplorerDialog.NONE
                                    }
                                }
                            ) { Text("Apply Batch") }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                        }
                    )
                }

                ExplorerDialog.MEDIA_PLAYER -> {
                    val item = targetFile
                    if (item != null) {
                        val mediaPlayer = remember { android.media.MediaPlayer() }
                        var isPlaying by remember { mutableStateOf(false) }
                        var currentPosition by remember { mutableStateOf(0) }
                        var duration by remember { mutableStateOf(0) }
                        var playbackSpeed by remember { mutableStateOf(1.0f) }
                        var isSpeedDropdownExpanded by remember { mutableStateOf(false) }
                        
                        LaunchedEffect(isPlaying) {
                            if (isPlaying) {
                                while (true) {
                                    try {
                                        currentPosition = mediaPlayer.currentPosition
                                    } catch (e: Exception) {}
                                    delay(250)
                                }
                            }
                        }
                        
                        DisposableEffect(item) {
                            try {
                                mediaPlayer.reset()
                                mediaPlayer.setDataSource(item.path)
                                mediaPlayer.prepare()
                                duration = mediaPlayer.duration
                                mediaPlayer.start()
                                isPlaying = true
                            } catch (e: Exception) {
                                android.widget.Toast.makeText(context, "Playback error: ${e.localizedMessage}", android.widget.Toast.LENGTH_SHORT).show()
                            }
                            
                            onDispose {
                                try {
                                    mediaPlayer.stop()
                                    mediaPlayer.release()
                                } catch (e: Exception) {}
                            }
                        }

                        AlertDialog(
                            onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                            title = {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(
                                        imageVector = if (item.isAudio) Icons.Default.MusicNote else Icons.Default.VideoLibrary,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF)
                                    )
                                    Text(
                                        text = if (item.isAudio) "Audio Player" else "Video Player",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 18.sp
                                    )
                                }
                            },
                            text = {
                                Column(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(16.dp)
                                ) {
                                    Text(
                                        text = item.name,
                                        fontWeight = FontWeight.SemiBold,
                                        color = Color.White,
                                        fontSize = 14.sp,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    
                                    val infiniteTransition = rememberInfiniteTransition(label = "visualizer")
                                    val scaleFactor by infiniteTransition.animateFloat(
                                        initialValue = 1f,
                                        targetValue = if (isPlaying) 1.25f else 1f,
                                        animationSpec = infiniteRepeatable(
                                            animation = tween(800, easing = FastOutSlowInEasing),
                                            repeatMode = RepeatMode.Reverse
                                        ),
                                        label = "visualizerScale"
                                    )
                                    
                                    Box(
                                        modifier = Modifier
                                            .size(110.dp)
                                            .scale(scaleFactor)
                                            .background(
                                                color = Color(0xFF00E5FF).copy(alpha = 0.1f),
                                                shape = CircleShape
                                            )
                                            .border(1.5.dp, Color(0xFF00E5FF).copy(alpha = 0.4f), CircleShape),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = if (item.isAudio) Icons.Default.Audiotrack else Icons.Default.PlayCircle,
                                            contentDescription = null,
                                            tint = Color(0xFF00E5FF),
                                            modifier = Modifier.size(48.dp)
                                        )
                                    }

                                    if (item.isVideo) {
                                        Text(
                                            "Playing background audio stream",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            fontWeight = FontWeight.Medium
                                        )
                                    }

                                    fun formatMillis(ms: Int): String {
                                        val totalSecs = ms / 1000
                                        val mins = totalSecs / 60
                                        val secs = totalSecs % 60
                                        return String.format("%02d:%02d", mins, secs)
                                    }

                                    Column(modifier = Modifier.fillMaxWidth()) {
                                        Slider(
                                            value = currentPosition.toFloat(),
                                            onValueChange = { newVal ->
                                                try {
                                                    mediaPlayer.seekTo(newVal.toInt())
                                                    currentPosition = newVal.toInt()
                                                } catch (e: Exception) {}
                                            },
                                            valueRange = 0f..(if (duration > 0) duration.toFloat() else 100f),
                                            colors = SliderDefaults.colors(
                                                activeTrackColor = Color(0xFF00E5FF),
                                                thumbColor = Color(0xFFFFB300)
                                            )
                                        )
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = formatMillis(currentPosition),
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                            Text(
                                                text = formatMillis(duration),
                                                color = Color.Gray,
                                                fontSize = 11.sp,
                                                fontFamily = FontFamily.Monospace
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceEvenly,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Box {
                                            TextButton(onClick = { isSpeedDropdownExpanded = true }) {
                                                Text("${playbackSpeed}x", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold)
                                            }
                                            DropdownMenu(
                                                expanded = isSpeedDropdownExpanded,
                                                onDismissRequest = { isSpeedDropdownExpanded = false }
                                            ) {
                                                listOf(0.5f, 1.0f, 1.5f, 2.0f).forEach { speed ->
                                                    DropdownMenuItem(
                                                        text = { Text("${speed}x") },
                                                        onClick = {
                                                            try {
                                                                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                                                                    val params = mediaPlayer.playbackParams
                                                                    params.speed = speed
                                                                    mediaPlayer.playbackParams = params
                                                                    playbackSpeed = speed
                                                                }
                                                            } catch (e: Exception) {}
                                                            isSpeedDropdownExpanded = false
                                                        }
                                                    )
                                                }
                                            }
                                        }

                                        FloatingActionButton(
                                            onClick = {
                                                try {
                                                    if (mediaPlayer.isPlaying) {
                                                        mediaPlayer.pause()
                                                        isPlaying = false
                                                    } else {
                                                        mediaPlayer.start()
                                                        isPlaying = true
                                                    }
                                                } catch (e: Exception) {}
                                            },
                                            containerColor = Color(0xFF00E5FF),
                                            contentColor = Color.Black,
                                            shape = CircleShape,
                                            modifier = Modifier.size(56.dp)
                                        ) {
                                            Icon(
                                                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                                                contentDescription = if (isPlaying) "Pause" else "Play",
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }

                                        IconButton(
                                            onClick = {
                                                try {
                                                    mediaPlayer.pause()
                                                    mediaPlayer.seekTo(0)
                                                    currentPosition = 0
                                                    isPlaying = false
                                                } catch (e: Exception) {}
                                            }
                                        ) {
                                            Icon(
                                                imageVector = Icons.Default.Stop,
                                                contentDescription = "Stop",
                                                tint = Color.Red,
                                                modifier = Modifier.size(28.dp)
                                            )
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = { activeDialog = ExplorerDialog.NONE }
                                ) {
                                    Text("Close Player")
                                }
                            }
                        )
                    }
                }

                ExplorerDialog.CHMOD -> {
                    val item = targetFile
                    if (item != null) {
                        AlertDialog(
                            onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                            title = { Text("Set Permissions") },
                            text = {
                                Column {
                                    Text("Octal representation (e.g. 755 or 644):", fontSize = 12.sp, color = Color.Gray)
                                    Spacer(modifier = Modifier.height(8.dp))
                                    OutlinedTextField(
                                        value = dialogInputChmod,
                                        onValueChange = { dialogInputChmod = it },
                                        label = { Text("Octal Permissions") },
                                        singleLine = true
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            FileSystemProvider.chmod(item.path, dialogInputChmod)
                                            reloadFiles()
                                            activeDialog = ExplorerDialog.NONE
                                        }
                                    }
                                ) { Text("Set Chmod") }
                            },
                            dismissButton = {
                                TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                            }
                        )
                    }
                }

                ExplorerDialog.ZIP_COMPRESS -> {
                    AlertDialog(
                        onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                        title = { Text("Compress to ZIP") },
                        text = {
                            OutlinedTextField(
                                value = dialogInputName,
                                onValueChange = { dialogInputName = it },
                                label = { Text("Archive Name (e.g. backup.zip)") },
                                singleLine = true
                            )
                        },
                        confirmButton = {
                            Button(
                                onClick = {
                                    val name = if (dialogInputName.endsWith(".zip")) dialogInputName else "$dialogInputName.zip"
                                    coroutineScope.launch {
                                        statusLog = "Compressing archive..."
                                        val destZip = "$currentPath/$name"
                                        for (src in selectedPaths) {
                                            ArchiveManager.compressToZip(src, destZip) { progress ->
                                                statusLog = progress
                                            }
                                        }
                                        selectedPaths.clear()
                                        reloadFiles()
                                        activeDialog = ExplorerDialog.NONE
                                        statusLog = "Archive created successfully."
                                    }
                                }
                            ) { Text("Compress") }
                        },
                        dismissButton = {
                            TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                        }
                    )
                }

                ExplorerDialog.ZIP_EXTRACT -> {
                    val item = targetFile
                    if (item != null) {
                        AlertDialog(
                            onDismissRequest = { activeDialog = ExplorerDialog.NONE },
                            title = { Text("Extract ZIP") },
                            text = { Text("Extract ZIP file content to current folder?") },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            statusLog = "Extracting files..."
                                            val destDir = currentPath + "/" + item.name.substringBeforeLast(".")
                                            val success = ArchiveManager.extractZip(item.path, destDir) { progress ->
                                                statusLog = progress
                                            }
                                            reloadFiles()
                                            activeDialog = ExplorerDialog.NONE
                                            statusLog = if (success) "Extracted successfully." else "Extraction failed."
                                        }
                                    }
                                ) { Text("Extract Here") }
                            },
                            dismissButton = {
                                TextButton(onClick = { activeDialog = ExplorerDialog.NONE }) { Text("Cancel") }
                            }
                        )
                    }
                }
                ExplorerDialog.NONE -> {}
            }
        }

        // --- COMMAND PALETTE OVERLAY Dialog ---
        if (showCommandPalette) {
            Dialog(onDismissRequest = { showCommandPalette = false }) {
                CommandPalette(
                    onDismiss = { showCommandPalette = false },
                    commands = commandList
                )
            }
        }

        // --- CONTEXT MENU SMART SUGGESTIONS OVERLAY ---
        if (showContextActionSheet) {
            val currentTarget = targetFile
            if (currentTarget != null) {
                Dialog(onDismissRequest = { showContextActionSheet = false }) {
                    ContextActionSheet(
                        fileItem = currentTarget,
                        onDismiss = { showContextActionSheet = false },
                        onOpenText = { path ->
                            onOpenTextEditor(path)
                            showContextActionSheet = false
                        },
                        onOpenHex = { path ->
                            onOpenHexEditor(path)
                            showContextActionSheet = false
                        },
                        onOpenSqlite = { path ->
                            onOpenSqliteEditor(path)
                            showContextActionSheet = false
                        },
                        onOpenApkTools = { path ->
                            onOpenApkTools(path)
                            showContextActionSheet = false
                        },
                        onExtractArchive = { item ->
                            targetFile = item
                            activeDialog = ExplorerDialog.ZIP_EXTRACT
                            showContextActionSheet = false
                        },
                        onCopy = { item ->
                            coroutineScope.launch {
                                statusLog = "Copying item..."
                                val targetDir = if (activePane == ActivePane.A) pathB else pathA
                                val dest = "$targetDir/${item.name}"
                                FileSystemProvider.copy(item.path, dest)
                                reloadFiles()
                                showContextActionSheet = false
                                statusLog = "Copied successfully."
                            }
                        },
                        onMove = { item ->
                            coroutineScope.launch {
                                statusLog = "Moving item..."
                                val targetDir = if (activePane == ActivePane.A) pathB else pathA
                                val dest = "$targetDir/${item.name}"
                                FileSystemProvider.move(item.path, dest)
                                reloadFiles()
                                showContextActionSheet = false
                                statusLog = "Moved successfully."
                            }
                        },
                        onRename = { item ->
                            targetFile = item
                            dialogInputName = item.name
                            activeDialog = ExplorerDialog.RENAME
                            showContextActionSheet = false
                        },
                        onDelete = { item ->
                            targetFile = item
                            selectedPaths.clear()
                            selectedPaths.add(item.path)
                            activeDialog = ExplorerDialog.DELETE_CONFIRM
                            showContextActionSheet = false
                        },
                        onChmod = { item ->
                            targetFile = item
                            dialogInputChmod = "755"
                            activeDialog = ExplorerDialog.CHMOD
                            showContextActionSheet = false
                        },
                        onProperties = { item ->
                            targetFile = item
                            activeDialog = ExplorerDialog.PROPERTIES
                            showContextActionSheet = false
                        },
                        onShare = { item ->
                            coroutineScope.launch {
                                try {
                                    val threadPolicy = StrictMode.allowThreadDiskWrites()
                                    try {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "*/*"
                                            putExtra(Intent.EXTRA_STREAM, Uri.fromFile(File(item.path)))
                                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share File"))
                                    } finally {
                                        StrictMode.setThreadPolicy(threadPolicy)
                                    }
                                } catch (e: Exception) {
                                    statusLog = "Share failed: ${e.message}"
                                }
                            }
                            showContextActionSheet = false
                        }
                    )
                }
            }
        }

        // --- MODE SELECTION DIALOG (Unified Switcher) ---
        if (showModeSelectionSheet) {
            AlertDialog(
                onDismissRequest = { showModeSelectionSheet = false },
                title = { Text("Storage Access Mode", fontWeight = FontWeight.Bold) },
                text = {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Select the storage provider mode. Elevated modes allow access to system folders.",
                            fontSize = 12.sp,
                            color = Color.LightGray,
                            modifier = Modifier.padding(bottom = 8.dp)
                        )
                        
                        // Mode 1: Standard Local SAF
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    FileSystemProvider.currentMode = AccessMode.LOCAL_SAF
                                    reloadFiles()
                                    showModeSelectionSheet = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentAccessMode == AccessMode.LOCAL_SAF) Color(0x22FFB300) else Color(0xFF1E1E26)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (currentAccessMode == AccessMode.LOCAL_SAF) Color(0xFFFFB300) else Color.Transparent
                            )
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Folder, contentDescription = null, tint = Color(0xFFFFB300))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Standard Local (SAF)", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Standard system-approved file access", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Mode 2: ADB Shell
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    FileSystemProvider.currentMode = AccessMode.ADB
                                    reloadFiles()
                                    showModeSelectionSheet = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentAccessMode == AccessMode.ADB) Color(0x2200E5FF) else Color(0xFF1E1E26)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (currentAccessMode == AccessMode.ADB) Color(0xFF00E5FF) else Color.Transparent
                            )
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.SettingsInputAntenna, contentDescription = null, tint = Color(0xFF00E5FF))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("ADB Wireless Shell", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Direct pairing loop for Android/data and obb", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }

                        // Mode 3: Root Access
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (com.kryon.filemanager.core.ShellService.isRootAvailable()) {
                                        FileSystemProvider.currentMode = AccessMode.ROOT
                                        reloadFiles()
                                    } else {
                                        statusLog = "Root is not available on this device"
                                    }
                                    showModeSelectionSheet = false
                                },
                            colors = CardDefaults.cardColors(
                                containerColor = if (currentAccessMode == AccessMode.ROOT) Color(0x224CAF50) else Color(0xFF1E1E26)
                            ),
                            border = BorderStroke(
                                width = 1.dp,
                                color = if (currentAccessMode == AccessMode.ROOT) Color(0xFF4CAF50) else Color.Transparent
                            )
                        ) {
                            Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Shield, contentDescription = null, tint = Color(0xFF4CAF50))
                                Spacer(modifier = Modifier.width(12.dp))
                                Column {
                                    Text("Superuser Root Access", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                    Text("Complete device access for rooted systems", fontSize = 11.sp, color = Color.Gray)
                                }
                            }
                        }
                    }
                },
                confirmButton = {
                    TextButton(onClick = { showModeSelectionSheet = false }) { Text("Close") }
                }
            )
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun PaneList(
    files: List<FileItem>,
    path: String,
    onPathChanged: (String) -> Unit,
    selectedPaths: MutableList<String>,
    onOpenTextEditor: (String) -> Unit,
    onOpenHexEditor: (String) -> Unit,
    onOpenSqliteEditor: (String) -> Unit,
    onOpenApkTools: (String?) -> Unit,
    onFileLongClicked: (FileItem) -> Unit,
    onFocused: () -> Unit,
    onOpenMediaPlayer: ((FileItem) -> Unit)? = null
) {
    if (files.isEmpty()) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp)
                .clickable { onFocused() }
        ) {
            Text(
                text = "Folder is empty or permission required",
                color = Color.DarkGray,
                modifier = Modifier.align(Alignment.Center)
            )
        }
    } else {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .clickable { onFocused() }
        ) {
            items(files) { item ->
                val isSelected = selectedPaths.contains(item.path)
                
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(if (isSelected) Color(0x3D00E5FF) else Color.Transparent)
                        .combinedClickable(
                            onClick = {
                                onFocused()
                                if (selectedPaths.isNotEmpty()) {
                                    // If selection mode is active, toggle select
                                    if (isSelected) selectedPaths.remove(item.path) else selectedPaths.add(item.path)
                                } else {
                                    // Regular click behaviour
                                    if (item.isDirectory) {
                                        onPathChanged(item.path)
                                    } else {
                                        // Open specific editors depending on files extension
                                        when {
                                            item.isAudio || item.isVideo -> {
                                                onOpenMediaPlayer?.invoke(item)
                                            }
                                            item.isText -> onOpenTextEditor(item.path)
                                            item.isDb -> onOpenSqliteEditor(item.path)
                                            item.isApk -> onOpenApkTools(item.path)
                                            item.isArchive -> {
                                                // Trigger extraction confirmation directly
                                                onFileLongClicked(item)
                                            }
                                            else -> onOpenHexEditor(item.path) // Fallback binary Hex editor
                                        }
                                    }
                                }
                            },
                            onLongClick = {
                                onFocused()
                                if (selectedPaths.contains(item.path)) {
                                    selectedPaths.remove(item.path)
                                } else {
                                    selectedPaths.add(item.path)
                                }
                            }
                        )
                        .padding(horizontal = 16.dp, vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Item Icon
                    val icon = when {
                        item.isDirectory -> Icons.Default.Folder
                        item.isText -> Icons.Default.Article
                        item.isDb -> Icons.Default.Storage
                        item.isApk -> Icons.Default.Android
                        item.isArchive -> Icons.Default.Archive
                        else -> Icons.Default.InsertDriveFile
                    }
                    val iconColor = when {
                        item.isDirectory -> MaterialTheme.colorScheme.primary
                        item.isText -> MaterialTheme.colorScheme.secondary
                        item.isDb -> Color(0xFF8BC34A)
                        item.isApk -> Color(0xFF4CAF50)
                        item.isArchive -> Color(0xFFFF9800)
                        else -> Color.LightGray
                    }

                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = iconColor,
                        modifier = Modifier.size(28.dp)
                    )

                    Spacer(modifier = Modifier.width(16.dp))

                    // Text Details
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = item.name,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            color = Color.White,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Text(
                                text = if (item.isDirectory) "Folder" else "${item.size / 1024} KB",
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
                            Text(
                                text = item.permissions,
                                fontSize = 11.sp,
                                color = Color.Gray,
                                fontFamily = FontFamily.Monospace
                            )
                        }
                    }

                    // Properties trigger action button
                    IconButton(
                        onClick = { onFileLongClicked(item) },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.MoreVert,
                            contentDescription = "Details",
                            tint = Color.Gray,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }

                Divider(color = Color(0x11FFFFFF), thickness = 0.5.dp)
            }
        }
    }
}
