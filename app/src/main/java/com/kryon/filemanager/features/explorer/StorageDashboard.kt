package com.kryon.filemanager.features.explorer

import android.os.Environment
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
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryon.filemanager.core.FileItem
import com.kryon.filemanager.core.FileSystemProvider
import com.kryon.filemanager.features.network.pressScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.*

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StorageDashboard(
    onNavigateToPath: (String) -> Unit,
    onOpenFile: (FileItem) -> Unit,
    onTabSelected: (Int) -> Unit, // 0 = Home, 1 = Files, 2 = Storage, 3 = Settings
    searchQuery: String,
    onSearchQueryChanged: (String) -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    val context = LocalContext.current

    // Real Storage calculations
    var totalSpace by remember { mutableStateOf(1L) }
    var freeSpace by remember { mutableStateOf(0L) }
    var usedSpace by remember { mutableStateOf(0L) }

    // Scanned items
    var recentFiles by remember { mutableStateOf<List<FileItem>>(emptyList()) }
    var isScanning by remember { mutableStateOf(false) }

    // Read real system space on start & scan files
    LaunchedEffect(Unit) {
        val storageDir = Environment.getExternalStorageDirectory()
        totalSpace = storageDir.totalSpace
        freeSpace = storageDir.freeSpace
        usedSpace = totalSpace - freeSpace

        isScanning = true
        coroutineScope.launch(Dispatchers.IO) {
            try {
                val filesList = mutableListOf<File>()
                // Quick shallow scan for actual recent files
                scanFilesShallow(storageDir, filesList, maxCount = 100)

                val sortedRecent = filesList
                    .filter { !it.isDirectory && it.exists() }
                    .sortedByDescending { it.lastModified() }
                    .take(5)
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

    // Format bytes utility
    fun formatBytes(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        val digitGroups = (Math.log10(bytes.toDouble()) / Math.log10(1024.0)).toInt()
        return String.format(Locale.US, "%.1f %s", bytes / Math.pow(1024.0, digitGroups.toDouble()), units[digitGroups])
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF090B10))
            .padding(horizontal = 16.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        // Spacing at top to leave room for the title and floating search bar
        item {
            Spacer(modifier = Modifier.height(12.dp))
        }

        // --- TITLE ---
        item {
            Text(
                text = "Kryon",
                fontWeight = FontWeight.ExtraBold,
                color = Color.White,
                fontSize = 32.sp,
                fontFamily = FontFamily.SansSerif,
                modifier = Modifier.padding(vertical = 4.dp)
            )
        }

        // --- FLOATING GLASS SEARCH BAR WITH BLUR ---
        item {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(54.dp)
                    .background(Color(0x1F121722), RoundedCornerShape(26.dp))
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(26.dp))
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                contentAlignment = Alignment.CenterStart
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Search,
                        contentDescription = "Search",
                        tint = Color(0xFF3BA7FF),
                        modifier = Modifier.size(20.dp)
                    )
                    TextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChanged,
                        placeholder = {
                            Text(
                                "Search files and folders...",
                                color = Color(0xFFAEB7C6),
                                fontSize = 14.sp
                            )
                        },
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            disabledContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                            disabledIndicatorColor = Color.Transparent,
                            focusedTextColor = Color.White,
                            unfocusedTextColor = Color.White
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        }

        // --- BEAUTIFUL STORAGE CARD (Liquid Glassmorphism) ---
        item {
            val usedPercent = if (totalSpace > 0) usedSpace.toFloat() / totalSpace else 0f
            val animateProgress by animateFloatAsState(
                targetValue = usedPercent,
                animationSpec = tween(1500, easing = FastOutSlowInEasing),
                label = "storageArcProgress"
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF121722),
                                Color(0xFF1E2638)
                            )
                        ),
                        RoundedCornerShape(32.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(32.dp))
                    .padding(22.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1.2f)) {
                        Text(
                            "STORAGE INDEX",
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color(0xFF74D4FF)
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = formatBytes(usedSpace),
                            fontSize = 26.sp,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White
                        )
                        Text(
                            text = "of ${formatBytes(totalSpace)} occupied",
                            color = Color(0xFFAEB7C6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(14.dp))

                        // Storage trigger button
                        Button(
                            onClick = { onTabSelected(2) }, // Switch to Storage tab
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3BA7FF)),
                            shape = RoundedCornerShape(16.dp),
                            contentPadding = PaddingValues(horizontal = 14.dp, vertical = 6.dp)
                        ) {
                            Text(
                                "Storage Details",
                                fontSize = 12.sp,
                                color = Color.Black,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }

                    // Circular animated storage gauge
                    Box(
                        modifier = Modifier
                            .size(100.dp)
                            .weight(0.8f),
                        contentAlignment = Alignment.Center
                    ) {
                        Canvas(modifier = Modifier.size(88.dp)) {
                            // Empty Track
                            drawCircle(
                                color = Color.White.copy(alpha = 0.05f),
                                radius = size.width / 2,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                            // Progress arc with glowing gradient
                            drawArc(
                                color = Color(0xFF3BA7FF),
                                startAngle = -90f,
                                sweepAngle = animateProgress * 360f,
                                useCenter = false,
                                style = Stroke(width = 8.dp.toPx(), cap = StrokeCap.Round)
                            )
                        }

                        Column(horizontalAlignment = Alignment.CenterHorizontally) {
                            Text(
                                text = "${(usedPercent * 100).toInt()}%",
                                color = Color.White,
                                fontSize = 18.sp,
                                fontWeight = FontWeight.ExtraBold
                            )
                            Text(
                                text = "USED",
                                color = Color(0xFFAEB7C6),
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }
            }
        }

        // --- QUICK ACCESS SECTION ---
        item {
            Text(
                "QUICK ACCESS",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAEB7C6),
                modifier = Modifier.padding(start = 6.dp)
            )
        }

        item {
            val rootStorage = FileSystemProvider.getPrimaryStoragePath()
            val categories = listOf(
                CategoryItem("Images", Icons.Default.Image, Color(0xFF3BA7FF), "$rootStorage/DCIM"),
                CategoryItem("Videos", Icons.Default.Videocam, Color(0xFF74D4FF), "$rootStorage/Movies"),
                CategoryItem("Documents", Icons.Default.InsertDriveFile, Color(0xFF8A7CFF), "$rootStorage/Documents"),
                CategoryItem("Downloads", Icons.Default.Download, Color(0xFFE91E63), "$rootStorage/Download"),
                CategoryItem("Audio", Icons.Default.MusicNote, Color(0xFF00E5FF), "$rootStorage/Music"),
                CategoryItem("APK Files", Icons.Default.Android, Color(0xFF4CAF50), "$rootStorage/Download"),
                CategoryItem("Archives", Icons.Default.FolderZip, Color(0xFFFFB300), "$rootStorage/Download"),
                CategoryItem("Recent", Icons.Default.History, Color(0xFF7E57C2), rootStorage)
            )

            // Grid of categories (2 columns, 4 rows)
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                for (row in 0 until 4) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        val item1 = categories[row * 2]
                        val item2 = categories[row * 2 + 1]

                        CategoryCard(
                            item = item1,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                if (item1.title == "Recent") {
                                    onTabSelected(2) // open Storage analysis
                                } else {
                                    onNavigateToPath(item1.targetPath)
                                }
                            }
                        )

                        CategoryCard(
                            item = item2,
                            modifier = Modifier.weight(1f),
                            onClick = {
                                onNavigateToPath(item2.targetPath)
                            }
                        )
                    }
                }
            }
        }

        // --- RECENT FILES (Beautiful Floating Cards) ---
        item {
            Text(
                "RECENT FILES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAEB7C6),
                modifier = Modifier.padding(start = 6.dp, top = 8.dp)
            )
        }

        if (isScanning) {
            item {
                Box(modifier = Modifier.fillMaxWidth().height(100.dp)) {
                    CircularProgressIndicator(modifier = Modifier.align(Alignment.Center), color = Color(0xFF3BA7FF))
                }
            }
        } else if (recentFiles.isEmpty()) {
            item {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(100.dp)
                        .background(Color(0xFF121722), RoundedCornerShape(20.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        "No recently modified files found.",
                        color = Color(0xFFAEB7C6),
                        fontSize = 12.sp
                    )
                }
            }
        } else {
            items(recentFiles) { file ->
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .pressScale()
                        .clickable { onOpenFile(file) }
                        .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp)),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Box(
                            modifier = Modifier
                                .size(40.dp)
                                .background(Color(0x113BA7FF), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = when (file.extension.lowercase()) {
                                    "jpg", "jpeg", "png", "webp", "gif" -> Icons.Default.Image
                                    "mp4", "mkv", "avi" -> Icons.Default.Videocam
                                    "pdf", "txt", "doc", "docx" -> Icons.Default.Description
                                    "mp3", "wav", "m4a", "ogg" -> Icons.Default.MusicNote
                                    "apk" -> Icons.Default.Android
                                    "zip", "rar", "7z", "tar", "gz" -> Icons.Default.FolderZip
                                    else -> Icons.Default.InsertDriveFile
                                },
                                contentDescription = null,
                                tint = Color(0xFF3BA7FF),
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Spacer(modifier = Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = file.name,
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 13.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = file.path,
                                color = Color(0xFFAEB7C6),
                                fontSize = 10.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = formatBytes(file.size),
                            color = Color(0xFF74D4FF),
                            fontWeight = FontWeight.Bold,
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }

        item {
            Spacer(modifier = Modifier.height(30.dp))
        }
    }
}

data class CategoryItem(
    val title: String,
    val icon: ImageVector,
    val color: Color,
    val targetPath: String
)

@Composable
fun CategoryCard(
    item: CategoryItem,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    Card(
        modifier = modifier
            .height(72.dp)
            .pressScale()
            .clickable(onClick = onClick)
            .border(1.dp, Color.White.copy(alpha = 0.04f), RoundedCornerShape(20.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
        shape = RoundedCornerShape(20.dp)
    ) {
        Row(
            modifier = Modifier
                .fillMaxSize()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .background(item.color.copy(alpha = 0.1f), RoundedCornerShape(14.dp)),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = item.icon,
                    contentDescription = item.title,
                    tint = item.color,
                    modifier = Modifier.size(22.dp)
                )
            }
            Spacer(modifier = Modifier.width(10.dp))
            Column {
                Text(
                    text = item.title,
                    fontWeight = FontWeight.Bold,
                    color = Color.White,
                    fontSize = 13.sp
                )
                Text(
                    text = "Browse",
                    color = Color(0xFFAEB7C6),
                    fontSize = 10.sp
                )
            }
        }
    }
}

private fun scanFilesShallow(dir: File, result: MutableList<File>, maxCount: Int) {
    try {
        val queue = LinkedList<File>()
        queue.add(dir)

        var count = 0
        while (queue.isNotEmpty() && count < maxCount) {
            val current = queue.poll() ?: continue
            if (current.isDirectory) {
                val children = current.listFiles()
                if (children != null) {
                    for (child in children) {
                        if (child.name.startsWith(".")) continue
                        if (child.isFile) {
                            result.add(child)
                            count++
                        } else {
                            if (queue.size < 50) {
                                queue.add(child)
                            }
                        }
                    }
                }
            } else {
                result.add(current)
                count++
            }
        }
    } catch (e: Exception) {
        // Safe check
    }
}
