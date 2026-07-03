package com.example.features.hexeditor

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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FileSystemProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HexEditorView(
    filePath: String,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var fileBytes by remember { mutableStateOf(ByteArray(0)) }
    var isLoading by remember { mutableStateOf(true) }
    var editingIndex by remember { mutableStateOf<Int?>(null) }
    var editingValue by remember { mutableStateOf("") }
    var showEditDialog by remember { mutableStateOf(false) }
    var logMessage by remember { mutableStateOf("") }

    // Load file bytes on enter
    LaunchedEffect(filePath) {
        isLoading = true
        fileBytes = FileSystemProvider.readFileBytes(filePath)
        isLoading = false
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Hex Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = filePath.split("/").last(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val success = FileSystemProvider.writeFileBytes(filePath, fileBytes)
                                logMessage = if (success) "File saved successfully!" else "Failed to save file."
                            }
                        }
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
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
                .background(Color(0xFF0D0D0F))
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else if (fileBytes.isEmpty()) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Text(
                        text = "File is empty or could not be read",
                        color = Color.LightGray,
                        fontSize = 16.sp
                    )
                }
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Column Headers
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.6f))
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "OFFSET",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.width(75.dp)
                        )
                        Text(
                            text = "HEX VALUES (16 BYTES)",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = Color.LightGray,
                            modifier = Modifier.weight(1f),
                            textAlign = TextAlign.Center
                        )
                        Text(
                            text = "TEXT",
                            fontFamily = FontFamily.Monospace,
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.width(60.dp),
                            textAlign = TextAlign.End
                        )
                    }

                    // Hex Rows
                    val rowCount = (fileBytes.size + 15) / 16
                    LazyColumn(
                        modifier = Modifier
                            .weight(1f)
                            .padding(horizontal = 8.dp)
                    ) {
                        items(rowCount) { rowIndex ->
                            HexRow(
                                rowIndex = rowIndex,
                                fileBytes = fileBytes,
                                onByteClicked = { byteIndex ->
                                    editingIndex = byteIndex
                                    editingValue = String.format("%02X", fileBytes[byteIndex])
                                    showEditDialog = true
                                }
                            )
                        }
                    }

                    // Notification toast-like log message
                    if (logMessage.isNotEmpty()) {
                        Card(
                            modifier = Modifier
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

        // Edit Byte Dialog
        if (showEditDialog && editingIndex != null) {
            AlertDialog(
                onDismissRequest = { showEditDialog = false },
                title = { Text("Edit Byte at Offset: 0x${String.format("%04X", editingIndex)}") },
                text = {
                    Column {
                        Text("Current value in HEX:", fontSize = 14.sp, color = Color.Gray)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = editingValue,
                            onValueChange = { input ->
                                val clean = input.filter { it.isDigit() || it.uppercaseChar() in 'A'..'F' }
                                if (clean.length <= 2) {
                                    editingValue = clean.uppercase()
                                }
                            },
                            label = { Text("Hex Value (00-FF)") },
                            singleLine = true,
                            textStyle = androidx.compose.ui.text.TextStyle(fontFamily = FontFamily.Monospace)
                        )
                    }
                },
                confirmButton = {
                    Button(
                        onClick = {
                            val byteVal = editingValue.toIntOrNull(16)?.toByte()
                            if (byteVal != null) {
                                val updated = fileBytes.copyOf()
                                updated[editingIndex!!] = byteVal
                                fileBytes = updated
                                showEditDialog = false
                            }
                        }
                    ) {
                        Text("Apply")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showEditDialog = false }) {
                        Text("Cancel")
                    }
                }
            )
        }
    }
}

@Composable
fun HexRow(
    rowIndex: Int,
    fileBytes: ByteArray,
    onByteClicked: (Int) -> Unit
) {
    val offset = rowIndex * 16
    val rowSize = (fileBytes.size - offset).coerceAtMost(16)

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Offset Label (e.g. 00000010)
        Text(
            text = String.format("%08X", offset),
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = Color.Gray,
            modifier = Modifier.width(75.dp)
        )

        // Hex columns (4 blocks of 4 bytes)
        Row(
            modifier = Modifier.weight(1f),
            horizontalArrangement = Arrangement.Center
        ) {
            for (i in 0 until 16) {
                if (i < rowSize) {
                    val byteIndex = offset + i
                    val b = fileBytes[byteIndex]
                    val hexStr = String.format("%02X", b)
                    
                    Text(
                        text = hexStr,
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier
                            .padding(horizontal = 2.dp)
                            .clip(RoundedCornerShape(3.dp))
                            .clickable { onByteClicked(byteIndex) }
                            .padding(2.dp)
                    )
                } else {
                    Text(
                        text = "  ",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 11.sp,
                        modifier = Modifier.padding(horizontal = 2.dp, vertical = 2.dp)
                    )
                }
                
                if (i % 4 == 3 && i < 15) {
                    Spacer(modifier = Modifier.width(6.dp))
                }
            }
        }

        // ASCII Column representation
        val asciiStr = remember(fileBytes, offset, rowSize) {
            val sb = StringBuilder()
            for (i in 0 until rowSize) {
                val b = fileBytes[offset + i].toInt()
                if (b in 32..126) {
                    sb.append(b.toChar())
                } else {
                    sb.append(".")
                }
            }
            sb.toString()
        }

        Text(
            text = asciiStr,
            fontFamily = FontFamily.Monospace,
            fontSize = 11.sp,
            color = MaterialTheme.colorScheme.secondary,
            modifier = Modifier.width(60.dp),
            textAlign = TextAlign.End,
            maxLines = 1
        )
    }
}
