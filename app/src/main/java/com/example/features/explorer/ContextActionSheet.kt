package com.example.features.explorer

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FileItem

@Composable
fun ContextActionSheet(
    fileItem: FileItem,
    onDismiss: () -> Unit,
    onOpenText: (String) -> Unit,
    onOpenHex: (String) -> Unit,
    onOpenSqlite: (String) -> Unit,
    onOpenApkTools: (String) -> Unit,
    onExtractArchive: (FileItem) -> Unit,
    onCopy: (FileItem) -> Unit,
    onMove: (FileItem) -> Unit,
    onRename: (FileItem) -> Unit,
    onDelete: (FileItem) -> Unit,
    onChmod: (FileItem) -> Unit,
    onProperties: (FileItem) -> Unit,
    onShare: (FileItem) -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .padding(16.dp)
            .border(1.dp, Color(0xFF00E5FF).copy(alpha = 0.3f), RoundedCornerShape(16.dp)),
        colors = CardDefaults.cardColors(containerColor = Color(0xFF0F0F13)),
        shape = RoundedCornerShape(16.dp)
    ) {
        Column(
            modifier = Modifier
                .padding(16.dp)
                .fillMaxWidth()
        ) {
            // Header
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(bottom = 12.dp)
            ) {
                Icon(
                    imageVector = when {
                        fileItem.isDirectory -> Icons.Default.Folder
                        fileItem.isText -> Icons.Default.Article
                        fileItem.isDb -> Icons.Default.Storage
                        fileItem.isApk -> Icons.Default.Android
                        fileItem.isArchive -> Icons.Default.Archive
                        else -> Icons.Default.InsertDriveFile
                    },
                    contentDescription = null,
                    tint = if (fileItem.isDirectory) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.secondary,
                    modifier = Modifier.size(32.dp)
                )
                Spacer(modifier = Modifier.width(12.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = fileItem.name,
                        fontWeight = FontWeight.Bold,
                        fontSize = 15.sp,
                        color = Color.White,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Text(
                        text = if (fileItem.isDirectory) "Folder" else "${fileItem.size / 1024} KB • ${fileItem.permissions}",
                        fontSize = 11.sp,
                        color = Color.Gray,
                        fontFamily = FontFamily.Monospace
                    )
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = Color.Gray)
                }
            }

            Divider(color = Color(0x22FFFFFF), thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))

            // SMART SUGGESTIONS SECTION
            Text(
                text = "SUGGESTED ACTIONS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFF00E5FF),
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Dynamic suggestions based on type
            val suggestedActions = remember(fileItem) {
                val list = mutableListOf<SmartAction>()
                when {
                    fileItem.isApk -> {
                        list.add(SmartAction("Analyze APK", Icons.Default.Troubleshoot, Color(0xFF00E5FF)) { onOpenApkTools(fileItem.path) })
                        list.add(SmartAction("Extract APK Resources", Icons.Default.Unarchive, Color(0xFFFFB300)) { onExtractArchive(fileItem) })
                    }
                    fileItem.isDb -> {
                        list.add(SmartAction("Open in SQLite Editor", Icons.Default.GridOn, Color(0xFF4CAF50)) { onOpenSqlite(fileItem.path) })
                    }
                    fileItem.isArchive -> {
                        list.add(SmartAction("Extract Archive", Icons.Default.Unarchive, Color(0xFFFF9800)) { onExtractArchive(fileItem) })
                    }
                    fileItem.isText -> {
                        list.add(SmartAction("Edit in Code Editor", Icons.Default.Edit, Color(0xFF00E5FF)) { onOpenText(fileItem.path) })
                    }
                    else -> {
                        if (!fileItem.isDirectory) {
                            list.add(SmartAction("Open in Hex Editor", Icons.Default.Memory, Color(0xFFFFB300)) { onOpenHex(fileItem.path) })
                        }
                    }
                }
                list
            }

            if (suggestedActions.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
                ) {
                    suggestedActions.forEach { action ->
                        Card(
                            modifier = Modifier
                                .weight(1f)
                                .clickable {
                                    action.onClick()
                                    onDismiss()
                                },
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF161622)),
                            border = BorderStroke(1.dp, action.accentColor.copy(alpha = 0.4f))
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                Icon(
                                    imageVector = action.icon,
                                    contentDescription = null,
                                    tint = action.accentColor,
                                    modifier = Modifier.size(24.dp)
                                )
                                Spacer(modifier = Modifier.height(4.dp))
                                Text(
                                    text = action.title,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = Color.White,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }
            } else {
                Text(
                    text = "No custom smart suggestions for this type. Standard folder actions are listed below.",
                    fontSize = 11.sp,
                    color = Color.Gray,
                    modifier = Modifier.padding(bottom = 12.dp)
                )
            }

            Divider(color = Color(0x11FFFFFF), thickness = 1.dp, modifier = Modifier.padding(bottom = 12.dp))

            // STANDARD ACTIONS
            Text(
                text = "STANDARD OPERATIONS",
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                color = Color.Gray,
                modifier = Modifier.padding(bottom = 8.dp)
            )

            // Grid of Standard Actions
            val stdActions = listOf(
                StdAction("Copy", Icons.Default.ContentCopy) { onCopy(fileItem); onDismiss() },
                StdAction("Move", Icons.Default.ContentCut) { onMove(fileItem); onDismiss() },
                StdAction("Rename", Icons.Default.DriveFileRenameOutline) { onRename(fileItem); onDismiss() },
                StdAction("Delete", Icons.Default.Delete, Color.Red) { onDelete(fileItem); onDismiss() },
                StdAction("Permissions", Icons.Default.Lock) { onChmod(fileItem); onDismiss() },
                StdAction("Properties", Icons.Default.Info) { onProperties(fileItem); onDismiss() },
                StdAction("Share", Icons.Default.Share) { onShare(fileItem); onDismiss() }
            )

            // Let's lay them out in a clean grid/list
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                stdActions.chunked(4).forEach { rowActions ->
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        rowActions.forEach { action ->
                            Card(
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { action.onClick() },
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF121217))
                            ) {
                                Column(
                                    modifier = Modifier.padding(8.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally
                                ) {
                                    Icon(
                                        imageVector = action.icon,
                                        contentDescription = null,
                                        tint = action.tint,
                                        modifier = Modifier.size(18.dp)
                                    )
                                    Spacer(modifier = Modifier.height(2.dp))
                                    Text(
                                        text = action.title,
                                        fontSize = 10.sp,
                                        color = Color.LightGray,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                        // Fill remaining spaces in the grid row
                        if (rowActions.size < 4) {
                            repeat(4 - rowActions.size) {
                                Spacer(modifier = Modifier.weight(1f))
                            }
                        }
                    }
                }
            }
        }
    }
}

data class SmartAction(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val accentColor: Color,
    val onClick: () -> Unit
)

data class StdAction(
    val title: String,
    val icon: androidx.compose.ui.graphics.vector.ImageVector,
    val tint: Color = Color.Gray,
    val onClick: () -> Unit
)
