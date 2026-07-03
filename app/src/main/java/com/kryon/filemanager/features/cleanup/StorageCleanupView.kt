package com.kryon.filemanager.features.cleanup

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryon.filemanager.core.FileSystemProvider
import com.kryon.filemanager.features.network.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

// Data representation for a cleanable file
data class CleanableFile(
    val file: File,
    val name: String,
    val path: String,
    val size: Long,
    val type: CleanupType,
    var isSelected: Boolean = true,
    val extraInfo: String? = null
)

enum class CleanupType {
    LARGE_FILE,
    DUPLICATE,
    OLD_APK,
    CACHE_TEMP
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun StorageCleanupView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Scanning & state variables
    var isScanning by remember { mutableStateOf(false) }
    var scanProgress by remember { mutableStateOf(0f) }
    var currentScanningPath by remember { mutableStateOf("") }
    
    // File results
    val largeFiles = remember { mutableStateListOf<CleanableFile>() }
    val duplicateFiles = remember { mutableStateListOf<CleanableFile>() }
    val oldApks = remember { mutableStateListOf<CleanableFile>() }
    val cacheFiles = remember { mutableStateListOf<CleanableFile>() }

    // Categories toggles
    var expandLargeFiles by remember { mutableStateOf(false) }
    var expandDuplicates by remember { mutableStateOf(false) }
    var expandApks by remember { mutableStateOf(false) }
    var expandCache by remember { mutableStateOf(false) }

    // Dialog state
    var showConfirmDialog by remember { mutableStateOf(false) }

    // Large file threshold selector (50MB, 100MB, 200MB)
    var largeFileThresholdMB by remember { mutableStateOf(50L) }
    var isThresholdDropdownExpanded by remember { mutableStateOf(false) }

    // Calculated stats
    val totalLargeSize = largeFiles.filter { it.isSelected }.sumOf { it.size }
    val totalDuplicateSize = duplicateFiles.filter { it.isSelected }.sumOf { it.size }
    val totalApkSize = oldApks.filter { it.isSelected }.sumOf { it.size }
    val totalCacheSize = cacheFiles.filter { it.isSelected }.sumOf { it.size }
    val totalCleanableSize = totalLargeSize + totalDuplicateSize + totalApkSize + totalCacheSize

    // Format bytes utility
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.2f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    // High speed recursively scan storage coroutine
    fun startStorageScan() {
        coroutineScope.launch {
            isScanning = true
            scanProgress = 0f
            largeFiles.clear()
            duplicateFiles.clear()
            oldApks.clear()
            cacheFiles.clear()

            withContext(Dispatchers.IO) {
                val rootPath = FileSystemProvider.getPrimaryStoragePath()
                val rootDir = File(rootPath)
                if (!rootDir.exists() || !rootDir.canRead()) {
                    return@withContext
                }

                val allFilesList = mutableListOf<File>()
                val sizeMap = mutableMapOf<Long, MutableList<File>>()
                var scannedCount = 0

                // Standard system folders to scan safely
                val targetFolders = listOf("Download", "Documents", "DCIM", "Pictures", "Music", "Movies", "Android/data")
                val filesToScan = mutableListOf<File>()

                // Collect files to scan safely and avoid infinite recursion/permission blocks
                for (folderName in targetFolders) {
                    val folder = File(rootDir, folderName)
                    if (folder.exists() && folder.canRead()) {
                        filesToScan.add(folder)
                    }
                }
                // Add any loose files in root
                rootDir.listFiles()?.filter { it.isFile }?.forEach { filesToScan.add(it) }

                val queue = LinkedList<File>()
                queue.addAll(filesToScan)

                while (queue.isNotEmpty() && scannedCount < 15000) {
                    val current = queue.poll() ?: continue
                    if (current.isDirectory) {
                        val children = current.listFiles()
                        if (children != null) {
                            for (child in children) {
                                if (child.isDirectory) {
                                    // Limit deep scans of Android/data to keep execution under a few seconds
                                    if (!child.absolutePath.contains("Android/data") || queue.size < 50) {
                                        queue.add(child)
                                    }
                                } else {
                                    queue.add(child)
                                }
                            }
                        }
                    } else {
                        scannedCount++
                        allFilesList.add(current)
                        currentScanningPath = current.name

                        // Map size for duplicate checks
                        val len = current.length()
                        if (len > 0) {
                            val list = sizeMap.getOrPut(len) { mutableListOf() }
                            list.add(current)
                        }

                        // Feed scanning percentage
                        scanProgress = (scannedCount / 15000f).coerceAtMost(1f)
                        if (scannedCount % 350 == 0) {
                            delay(10) // Small yield for UI updates
                        }
                    }
                }

                // Process categories
                for (file in allFilesList) {
                    val name = file.name
                    val path = file.absolutePath
                    val size = file.length()
                    val ext = file.extension.lowercase()

                    // 1. Unused/Old APK files
                    if (ext == "apk") {
                        oldApks.add(
                            CleanableFile(
                                file = file,
                                name = name,
                                path = path,
                                size = size,
                                type = CleanupType.OLD_APK,
                                extraInfo = "Downloaded installer pack"
                            )
                        )
                    }
                    // 2. Cache / Logs
                    else if (ext in listOf("tmp", "temp", "cache", "log") || path.contains("cache", ignoreCase = true) || path.contains("temp", ignoreCase = true)) {
                        cacheFiles.add(
                            CleanableFile(
                                file = file,
                                name = name,
                                path = path,
                                size = size,
                                type = CleanupType.CACHE_TEMP,
                                extraInfo = if (ext == "log") "System activity log" else "Temporary application cache"
                            )
                        )
                    }
                    // 3. Large Files (> Threshold)
                    else if (size >= largeFileThresholdMB * 1024 * 1024) {
                        largeFiles.add(
                            CleanableFile(
                                file = file,
                                name = name,
                                path = path,
                                size = size,
                                type = CleanupType.LARGE_FILE,
                                extraInfo = "Occupies significant system space"
                            )
                        )
                    }
                }

                // 4. Duplicate Files detection
                sizeMap.filter { it.value.size > 1 }.forEach { (_, files) ->
                    // Group duplicates by file name + size (highly reliable match)
                    val groupedByName = files.groupBy { it.name }
                    groupedByName.filter { it.value.size > 1 }.forEach { (_, dupList) ->
                        // Add only subsequent duplicates to delete (safeguarding the original file)
                        dupList.forEachIndexed { index, file ->
                            duplicateFiles.add(
                                CleanableFile(
                                    file = file,
                                    name = file.name,
                                    path = file.absolutePath,
                                    size = file.length(),
                                    type = CleanupType.DUPLICATE,
                                    isSelected = index > 0, // Keep original unselected by default, select copy for deletion
                                    extraInfo = if (index == 0) "Original file (Keep)" else "Duplicate copy (Safe to delete)"
                                )
                            )
                        }
                    }
                }
            }

            isScanning = false
            currentScanningPath = "Scanning completed."
            Toast.makeText(context, "Storage scan completed! Classified ${largeFiles.size + duplicateFiles.size + oldApks.size + cacheFiles.size} items.", Toast.LENGTH_SHORT).show()
        }
    }

    // Batch cleaning process
    fun cleanSelectedFiles() {
        coroutineScope.launch {
            var deletedCount = 0
            var reclaimedSpace = 0L

            withContext(Dispatchers.IO) {
                val allToDelete = (largeFiles + duplicateFiles + oldApks + cacheFiles).filter { it.isSelected }
                allToDelete.forEach { cleanable ->
                    try {
                        if (cleanable.file.exists() && cleanable.file.delete()) {
                            deletedCount++
                            reclaimedSpace += cleanable.size
                        }
                    } catch (e: Exception) {
                        e.printStackTrace()
                    }
                }

                // Update original UI lists
                largeFiles.removeAll { it.isSelected }
                duplicateFiles.removeAll { it.isSelected }
                oldApks.removeAll { it.isSelected }
                cacheFiles.removeAll { it.isSelected }
            }

            showConfirmDialog = false
            Toast.makeText(context, "Successfully cleaned $deletedCount files! Reclaimed ${formatBytes(reclaimedSpace)}.", Toast.LENGTH_LONG).show()
        }
    }

    // Auto-scan on entry
    LaunchedEffect(Unit) {
        startStorageScan()
    }

    Scaffold(
        topBar = {
            @OptIn(ExperimentalMaterial3Api::class)
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.CleaningServices,
                            contentDescription = null,
                            tint = Color(0xFF00E5FF),
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Storage Cleanup Suggestions",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF0F0F15)
                )
            )
        },
        containerColor = Color(0xFF0A0A0F)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0A0A0F))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            
            // --- HEADER METRIC CONTAINER (Frosted Dark visual cards with Neon ring) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color(0xFF14141E), RoundedCornerShape(16.dp))
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(16.dp))
                    .padding(20.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.3f)) {
                        Text(
                            "Reclaimable Space",
                            color = Color.LightGray,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatBytes(totalCleanableSize),
                            color = if (totalCleanableSize > 0) Color(0xFF00E5FF) else Color.White,
                            fontSize = 28.sp,
                            fontWeight = FontWeight.ExtraBold,
                            fontFamily = FontFamily.SansSerif
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            "Select junk and unneeded files from categories below to safely wipe.",
                            color = Color.Gray,
                            fontSize = 11.sp,
                            lineHeight = 16.sp
                        )
                    }

                    // Circular Space categorization ring
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .weight(0.7f),
                        contentAlignment = Alignment.Center
                    ) {
                        val animatedAngle by animateFloatAsState(
                            targetValue = if (isScanning) 360f else 270f,
                            animationSpec = infiniteRepeatable(
                                animation = tween(2000, easing = LinearEasing),
                                repeatMode = RepeatMode.Restart
                            ),
                            label = "scanPulse"
                        )

                        Canvas(modifier = Modifier.size(80.dp)) {
                            // Background ring
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = size.width / 2,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Categorized space sweep representation
                            drawArc(
                                color = Color(0xFF00E5FF),
                                startAngle = -90f,
                                sweepAngle = if (isScanning) animatedAngle else 240f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            drawArc(
                                color = Color(0xFFFFB300),
                                startAngle = 150f,
                                sweepAngle = 70f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Icon(
                                imageVector = if (isScanning) Icons.Default.Radar else Icons.Default.DoneAll,
                                contentDescription = null,
                                tint = if (isScanning) Color(0xFF00E5FF) else Color(0xFFFFB300),
                                modifier = Modifier.size(24.dp)
                            )
                            Text(
                                text = if (isScanning) "${(scanProgress * 100).toInt()}%" else "Ready",
                                color = Color.White,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }

            // Scanning Status / Progress indicator
            if (isScanning) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1414)),
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(12.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            color = Color.Red,
                            strokeWidth = 2.dp
                        )
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Deep Scanning Storage Folders...", color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Text(
                                text = currentScanningPath,
                                color = Color.LightGray,
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                }
            }

            // --- CONTROLS ROW ---
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Configurable large file size threshold dropdown
                Box {
                    Button(
                        onClick = { isThresholdDropdownExpanded = true },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E1E2E)),
                        shape = RoundedCornerShape(12.dp),
                        contentPadding = PaddingValues(horizontal = 14.dp, vertical = 8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Large files: > $largeFileThresholdMB MB", color = Color.LightGray, fontSize = 12.sp)
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.LightGray, modifier = Modifier.size(16.dp))
                        }
                    }
                    DropdownMenu(
                        expanded = isThresholdDropdownExpanded,
                        onDismissRequest = { isThresholdDropdownExpanded = false }
                    ) {
                        listOf(20L, 50L, 100L, 200L, 500L).forEach { mb ->
                            DropdownMenuItem(
                                text = { Text("$mb MB") },
                                onClick = {
                                    largeFileThresholdMB = mb
                                    isThresholdDropdownExpanded = false
                                    startStorageScan() // Rescan
                                }
                            )
                        }
                    }
                }

                // Rescan manual button
                IconButton(
                    onClick = { startStorageScan() },
                    modifier = Modifier
                        .background(Color(0xFF1E1E2E), CircleShape)
                        .size(40.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = "Rescan", tint = Color.White)
                }
            }

            // --- INTERACTIVE CLEANUP CATEGORIES LIST ---
            LazyColumn(
                modifier = Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                // 1. Unused / Duplicate Files Category
                item {
                    CleanupCategoryCard(
                        title = "Duplicate Files",
                        subtitle = "Grouped matching copies (keeping originals)",
                        count = duplicateFiles.size,
                        totalSize = formatBytes(duplicateFiles.sumOf { if (it.isSelected) it.size else 0L }),
                        icon = Icons.Default.CopyAll,
                        accentColor = Color(0xFF00E5FF),
                        isExpanded = expandDuplicates,
                        onToggleExpand = { expandDuplicates = !expandDuplicates },
                        files = duplicateFiles
                    )
                }

                // 2. Unused / Installer APKs
                item {
                    CleanupCategoryCard(
                        title = "Downloaded APK Installers",
                        subtitle = "Standalone application package files",
                        count = oldApks.size,
                        totalSize = formatBytes(oldApks.sumOf { if (it.isSelected) it.size else 0L }),
                        icon = Icons.Default.Android,
                        accentColor = Color(0xFF4CAF50),
                        isExpanded = expandApks,
                        onToggleExpand = { expandApks = !expandApks },
                        files = oldApks
                    )
                }

                // 3. Cache & Temporary files
                item {
                    CleanupCategoryCard(
                        title = "Cache & Temporary Files",
                        subtitle = "Logs, cached entries and tmp buffers",
                        count = cacheFiles.size,
                        totalSize = formatBytes(cacheFiles.sumOf { if (it.isSelected) it.size else 0L }),
                        icon = Icons.Default.DeleteSweep,
                        accentColor = Color(0xFFFF9800),
                        isExpanded = expandCache,
                        onToggleExpand = { expandCache = !expandCache },
                        files = cacheFiles
                    )
                }

                // 4. Large Files Category
                item {
                    CleanupCategoryCard(
                        title = "Large Files",
                        subtitle = "Single files exceeding $largeFileThresholdMB MB size",
                        count = largeFiles.size,
                        totalSize = formatBytes(largeFiles.sumOf { if (it.isSelected) it.size else 0L }),
                        icon = Icons.Default.InsertDriveFile,
                        accentColor = Color(0xFFE91E63),
                        isExpanded = expandLargeFiles,
                        onToggleExpand = { expandLargeFiles = !expandLargeFiles },
                        files = largeFiles
                    )
                }
            }

            // --- BOTTOM SAFE CLEANUP ACTION FOOTER ---
            Button(
                onClick = {
                    if (totalCleanableSize > 0) {
                        showConfirmDialog = true
                    } else {
                        Toast.makeText(context, "Nothing selected to delete.", Toast.LENGTH_SHORT).show()
                    }
                },
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (totalCleanableSize > 0) Color(0xFF00E5FF) else Color.White.copy(alpha = 0.12f)
                ),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .pressScale()
                    .testTag("clean_action_button"),
                enabled = !isScanning
            ) {
                Icon(
                    imageVector = Icons.Default.DeleteSweep,
                    contentDescription = null,
                    tint = if (totalCleanableSize > 0) Color.Black else Color.Gray,
                    modifier = Modifier.padding(end = 8.dp)
                )
                Text(
                    text = "Clean ${formatBytes(totalCleanableSize)} Selected Junk",
                    fontWeight = FontWeight.Bold,
                    fontSize = 16.sp,
                    color = if (totalCleanableSize > 0) Color.Black else Color.Gray
                )
            }
        }
    }

    // --- SAFE RECOMMENDATION CONFIRMATION DIALOG ---
    if (showConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Warning,
                        contentDescription = null,
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.padding(end = 8.dp)
                    )
                    Text("Confirm Permanent Deletion")
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        "Are you sure you want to permanently delete the selected files? This action cannot be undone.",
                        fontSize = 14.sp
                    )
                    
                    // Display specific recommendations
                    Card(
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1E1B15)),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            Text("Safe Cleaning Tips:", color = Color(0xFFFFB300), fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text("• Deleting duplicates automatically retains the original files safely.", color = Color.LightGray, fontSize = 11.sp)
                            Text("• Verify standalone APK installers are no longer needed before cleaning.", color = Color.LightGray, fontSize = 11.sp)
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = { cleanSelectedFiles() },
                    colors = ButtonDefaults.buttonColors(containerColor = Color.Red)
                ) {
                    Text("Delete Permanently", color = Color.White)
                }
            },
            dismissButton = {
                TextButton(onClick = { showConfirmDialog = false }) {
                    Text("Cancel")
                }
            }
        )
    }
}

@Composable
fun CleanupCategoryCard(
    title: String,
    subtitle: String,
    count: Int,
    totalSize: String,
    icon: ImageVector,
    accentColor: Color,
    isExpanded: Boolean,
    onToggleExpand: () -> Unit,
    files: List<CleanableFile>
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF12121A)),
        border = BorderStroke(1.dp, if (isExpanded) accentColor.copy(alpha = 0.4f) else Color.White.copy(alpha = 0.05f))
    ) {
        Column(modifier = Modifier.fillMaxWidth()) {
            // Expandable Row Header
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onToggleExpand() }
                    .padding(16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .background(accentColor.copy(alpha = 0.1f), CircleShape),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(imageVector = icon, contentDescription = null, tint = accentColor, modifier = Modifier.size(20.dp))
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(title, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 14.sp)
                        Text(subtitle, color = Color.Gray, fontSize = 11.sp)
                    }
                }
                
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Column(horizontalAlignment = Alignment.End) {
                        Text(totalSize, color = accentColor, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("$count items", color = Color.Gray, fontSize = 10.sp)
                    }
                    Icon(
                        imageVector = if (isExpanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                        contentDescription = null,
                        tint = Color.LightGray
                    )
                }
            }

            // Expanded files detail lists
            AnimatedVisibility(
                visible = isExpanded,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut()
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(Color(0xFF0D0D12))
                        .padding(horizontal = 12.dp, vertical = 8.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (files.isEmpty()) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            Text("No items found under this category.", color = Color.Gray, fontSize = 12.sp)
                        }
                    } else {
                        files.forEach { cleanable ->
                            var selected by remember { mutableStateOf(cleanable.isSelected) }
                            
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF14141E), RoundedCornerShape(8.dp))
                                    .clickable {
                                        cleanable.isSelected = !cleanable.isSelected
                                        selected = cleanable.isSelected
                                    }
                                    .padding(8.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Checkbox(
                                        checked = selected,
                                        onCheckedChange = { isChecked ->
                                            cleanable.isSelected = isChecked
                                            selected = isChecked
                                        },
                                        colors = CheckboxDefaults.colors(checkedColor = accentColor)
                                    )
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = cleanable.name,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = cleanable.path,
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        if (cleanable.extraInfo != null) {
                                            Text(
                                                text = cleanable.extraInfo,
                                                color = accentColor.copy(alpha = 0.8f),
                                                fontSize = 9.sp,
                                                fontWeight = FontWeight.Medium
                                            )
                                        }
                                    }
                                }
                                Text(
                                    text = String.format(Locale.US, "%.1f MB", cleanable.size / (1024f * 1024f)),
                                    color = Color.LightGray,
                                    fontSize = 11.sp,
                                    fontFamily = FontFamily.Monospace,
                                    modifier = Modifier.padding(start = 8.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
