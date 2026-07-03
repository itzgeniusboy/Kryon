package com.example.features.explorer

import android.os.Environment
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FileItem
import com.example.core.FileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun StorageDashboard(
    onNavigateToPath: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenAdb: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    
    // Real Storage calculations
    var totalSpace by remember { mutableStateOf(1L) }
    var freeSpace by remember { mutableStateOf(0L) }
    var usedSpace by remember { mutableStateOf(0L) }
    
    // Scanned items
    var largeFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var recentFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Read real system space on start
    LaunchedEffect(Unit) {
        val storageDir = Environment.getExternalStorageDirectory()
        totalSpace = storageDir.totalSpace
        freeSpace = storageDir.freeSpace
        usedSpace = totalSpace - freeSpace
        
        // Scan for actual large and recent files in primary storage in IO thread
        isScanning = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val filesList = mutableListOf<File>()
                scanFilesRecursively(storageDir, filesList, maxDepth = 3)
                
                // Sort for large files
                val sortedLarge = filesList
                    .filter { !it.isDirectory }
                    .sortedByDescending { it.length() }
                    .take(4)
                    .map { f ->
                        FileItem(
                            path = f.absolutePath,
                            name = f.name,
                            size = f.length(),
                            isDirectory = false,
                            lastModified = f.lastModified(),
                            extension = f.extension
                        )
                    }
                
                // Sort for recently modified files
                val sortedRecent = filesList
                    .filter { !it.isDirectory }
                    .sortedByDescending { it.lastModified() }
                    .take(4)
                    .map { f ->
                        FileItem(
                            path = f.absolutePath,
                            name = f.name,
                            size = f.length(),
                            isDirectory = false,
                            lastModified = f.lastModified(),
                            extension = f.extension
                        )
                    }

                withContext(Dispatchers.Main) {
                    largeFiles = sortedLarge
                    recentFiles = sortedRecent
                    isScanning = false
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    isScanning = false
                }
            }
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF09090C))
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Welcome Card
        item {
            Column(modifier = Modifier.padding(bottom = 4.dp)) {
                Text(
                    text = "Kryon Tool Suite",
                    fontWeight = FontWeight.ExtraBold,
                    color = Color.White,
                    fontSize = 24.sp
                )
                Text(
                    text = "All-in-one developer workspace",
                    color = Color.Gray,
                    fontSize = 12.sp
                )
            }
        }

        // Storage Usage Segmented Progress Bar & Legend
        item {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.2f), RoundedCornerShape(12.dp)),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F13))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "STORAGE CAPACITY",
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp,
                            color = Color.Gray
                        )
                        Text(
                            text = "${usedSpace / 1024 / 1024 / 1024} GB / ${totalSpace / 1024 / 1024 / 1024} GB Used",
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp,
                            color = Color(0xFF00E5FF)
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Custom Multi-Segment Progress bar using Canvas
                    val usedRatio = usedSpace.toFloat() / totalSpace.coerceAtLeast(1)
                    val appsRatio = (usedRatio * 0.4f).coerceAtLeast(0.05f)
                    val mediaRatio = (usedRatio * 0.35f).coerceAtLeast(0.05f)
                    val docsRatio = (usedRatio * 0.15f).coerceAtLeast(0.03f)
                    val otherRatio = (usedRatio - appsRatio - mediaRatio - docsRatio).coerceAtLeast(0.02f)

                    Canvas(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(10.dp)
                    ) {
                        val width = size.width
                        val height = size.height
                        val corner = CornerRadius(5.dp.toPx(), 5.dp.toPx())

                        // Background Track
                        drawRoundRect(
                            color = Color(0xFF1E1E24),
                            size = Size(width, height),
                            cornerRadius = corner
                        )

                        var startOffset = 0f

                        // 1. Apps (Green)
                        val appsW = width * appsRatio
                        drawRoundRect(
                            color = Color(0xFF4CAF50),
                            topLeft = Offset(startOffset, 0f),
                            size = Size(appsW, height),
                            cornerRadius = corner
                        )
                        startOffset += appsW

                        // 2. Media (Cyan)
                        val mediaW = width * mediaRatio
                        drawRoundRect(
                            color = Color(0xFF00E5FF),
                            topLeft = Offset(startOffset, 0f),
                            size = Size(mediaW, height),
                            cornerRadius = corner
                        )
                        startOffset += mediaW

                        // 3. Documents (Amber)
                        val docsW = width * docsRatio
                        drawRoundRect(
                            color = Color(0xFFFFB300),
                            topLeft = Offset(startOffset, 0f),
                            size = Size(docsW, height),
                            cornerRadius = corner
                        )
                        startOffset += docsW

                        // 4. Cache / System (Purple)
                        val otherW = width * otherRatio
                        drawRoundRect(
                            color = Color(0xFF7E57C2),
                            topLeft = Offset(startOffset, 0f),
                            size = Size(otherW, height),
                            cornerRadius = corner
                        )
                    }

                    Spacer(modifier = Modifier.height(12.dp))

                    // Color Legend Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        LegendItem("Apps", Color(0xFF4CAF50))
                        LegendItem("Media", Color(0xFF00E5FF))
                        LegendItem("Documents", Color(0xFFFFB300))
                        LegendItem("Other/Cache", Color(0xFF7E57C2))
                    }
                }
            }
        }

        // Quick Access Bookmark Grid
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "QUICK MOUNTING PATHS",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BookmarkCard(
                        title = "Android data",
                        path = "${FileSystemProvider.getPrimaryStoragePath()}/Android/data",
                        icon = Icons.Default.CloudQueue,
                        color = Color(0xFF00E5FF),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToPath
                    )
                    BookmarkCard(
                        title = "Android obb",
                        path = "${FileSystemProvider.getPrimaryStoragePath()}/Android/obb",
                        icon = Icons.Default.SdCard,
                        color = Color(0xFFFFB300),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToPath
                    )
                }

                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    BookmarkCard(
                        title = "Downloads",
                        path = "${FileSystemProvider.getPrimaryStoragePath()}/Download",
                        icon = Icons.Default.Download,
                        color = Color(0xFF4CAF50),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToPath
                    )
                    BookmarkCard(
                        title = "Root Path",
                        path = "/",
                        icon = Icons.Default.Shield,
                        color = Color(0xFFE91E63),
                        modifier = Modifier.weight(1f),
                        onClick = onNavigateToPath
                    )
                }
            }
        }

        // Quick Tools Toolbar Shortcuts
        item {
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF14141A))
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(12.dp),
                    horizontalArrangement = Arrangement.SpaceAround
                ) {
                    QuickToolItem("Terminal", Icons.Default.Terminal, onOpenTerminal)
                    QuickToolItem("ADB Wireless", Icons.Default.SettingsInputAntenna, onOpenAdb)
                    QuickToolItem("Local Files", Icons.Default.FolderOpen) { onNavigateToPath(FileSystemProvider.getPrimaryStoragePath()) }
                }
            }
        }

        // Recently Scanned Files / Large Files split tabs
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "SMART FILE EXPLORER (LARGEST FILES)",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF00E5FF))
                    }
                } else if (largeFiles.isEmpty()) {
                    Text("No large files found. Scan directories to index storage.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F13))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            largeFiles.forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenFile(file) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.InsertDriveFile,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = file.path,
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(
                                        text = "${file.size / 1024 / 1024} MB",
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 11.sp,
                                        color = Color(0xFFFFB300)
                                    )
                                }
                                Divider(color = Color(0x11FFFFFF))
                            }
                        }
                    }
                }
            }
        }

        // Recently Modified Files
        item {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "RECENTLY ACCESSED OR MODIFIED",
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    color = Color.Gray
                )

                if (isScanning) {
                    Box(modifier = Modifier.fillMaxWidth().height(80.dp)) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFFFFB300))
                    }
                } else if (recentFiles.isEmpty()) {
                    Text("No recent files found in primary cache.", fontSize = 12.sp, color = Color.Gray)
                } else {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F13))
                    ) {
                        Column(modifier = Modifier.padding(8.dp)) {
                            recentFiles.forEach { file ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { onOpenFile(file) }
                                        .padding(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.History,
                                        contentDescription = null,
                                        tint = Color(0xFFFFB300),
                                        modifier = Modifier.size(20.dp)
                                    )
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = file.name,
                                            fontWeight = FontWeight.Bold,
                                            color = Color.White,
                                            fontSize = 12.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = file.path,
                                            color = Color.Gray,
                                            fontSize = 9.sp,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                                Divider(color = Color(0x11FFFFFF))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun LegendItem(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .background(color, RoundedCornerShape(2.dp))
        )
        Spacer(modifier = Modifier.width(4.dp))
        Text(text = label, fontSize = 10.sp, color = Color.LightGray)
    }
}

@Composable
fun BookmarkCard(
    title: String,
    path: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    color: Color,
    modifier: Modifier = Modifier,
    onClick: (String) -> Unit
) {
    Card(
        modifier = modifier
            .clickable { onClick(path) }
            .border(1.dp, Color(0x11FFFFFF), RoundedCornerShape(8.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF111116))
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = color,
                modifier = Modifier.size(20.dp)
            )
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = title,
                    fontWeight = FontWeight.Bold,
                    fontSize = 12.sp,
                    color = Color.White
                )
                Text(
                    text = path.split("/").last().ifEmpty { "/" },
                    fontSize = 9.sp,
                    color = Color.Gray,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

@Composable
fun QuickToolItem(
    label: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    onClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(8.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = Color(0xFF00E5FF),
            modifier = Modifier.size(24.dp)
        )
        Spacer(modifier = Modifier.height(4.dp))
        Text(text = label, fontSize = 11.sp, color = Color.LightGray)
    }
}

// Scans storage folders recursively for files to build dashboard shortcuts
private fun scanFilesRecursively(dir: File, result: MutableList<File>, maxDepth: Int, currentDepth: Int = 0) {
    if (currentDepth > maxDepth) return
    try {
        val files = dir.listFiles() ?: return
        for (f in files) {
            if (f.name.startsWith(".")) continue
            if (f.isDirectory) {
                // Scan directories recursively
                scanFilesRecursively(f, result, maxDepth, currentDepth + 1)
            } else {
                result.add(f)
            }
        }
    } catch (e: Exception) {
        // Ignore permission or file exceptions
    }
}
