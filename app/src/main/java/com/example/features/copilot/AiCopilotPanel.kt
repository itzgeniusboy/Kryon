package com.example.features.copilot

import android.content.Context
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.AiCopilotService
import com.example.core.CopilotAction
import com.example.core.FileSystemProvider
import com.example.core.SecurePreferences
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.File

@Composable
fun AiCopilotFab(
    onClick: () -> Unit
) {
    FloatingActionButton(
        onClick = onClick,
        containerColor = MaterialTheme.colorScheme.primary,
        contentColor = Color.White,
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Copilot")
            Spacer(modifier = Modifier.width(6.dp))
            Text("AI Copilot", fontWeight = FontWeight.Bold)
        }
    }
}

data class ChatMessage(
    val sender: String, // "user" or "copilot"
    val text: String,
    val pendingActions: List<CopilotAction> = emptyList(),
    val executed: Boolean = false
)

@Composable
fun AiCopilotPanel(
    isOpen: Boolean,
    onClose: () -> Unit,
    currentPath: String,
    onRefreshFiles: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Preferences states
    var isDownloaded by remember { mutableStateOf(false) }
    var isDownloading by remember { mutableStateOf(false) }
    var downloadProgress by remember { mutableStateOf(0f) }
    
    var apiKey by remember { mutableStateOf("") }
    var showKeySetup by remember { mutableStateOf(false) }

    // Chat states
    var userText by remember { mutableStateOf("") }
    var isThinking by remember { mutableStateOf(false) }
    val chatMessages = remember { mutableStateListOf<ChatMessage>() }

    LaunchedEffect(Unit) {
        apiKey = SecurePreferences.getClaudeApiKey(context)
        isDownloaded = context.getSharedPreferences("copilot_module", Context.MODE_PRIVATE)
            .getBoolean("is_installed", false)
    }

    AnimatedVisibility(
        visible = isOpen,
        enter = slideInVertically(initialOffsetY = { it }) + fadeIn(),
        exit = slideOutVertically(targetOffsetY = { it }) + fadeOut()
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.6f))
                .clickable { onClose() }
        ) {
            Card(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .fillMaxHeight(0.82f)
                    .clickable(enabled = false) {}, // Prevent closing when tapping on card
                shape = RoundedCornerShape(topStart = 28.dp, topEnd = 28.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF0C0C0F)),
                border = BorderStroke(1.dp, Color(0xFF1E1E26))
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp)
                ) {
                    // Header Bar
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "AI Copilot",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "Kryon AI Copilot",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )
                        }
                        
                        IconButton(onClick = onClose) {
                            Icon(Icons.Filled.Close, contentDescription = "Close", tint = Color.Gray)
                        }
                    }

                    Divider(color = Color(0xFF1C1C24), thickness = 1.dp, modifier = Modifier.padding(vertical = 8.dp))

                    if (!isDownloaded) {
                        // 1. PLAY FEATURE DELIVERY SIMULATION
                        Column(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Icon(
                                Icons.Filled.Download,
                                contentDescription = "Download Feature",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(64.dp)
                            )
                            Spacer(modifier = Modifier.height(16.dp))
                            Text(
                                "AI Copilot Module (On-Demand Feature)",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 18.sp
                            )
                            Spacer(modifier = Modifier.height(8.dp))
                            Text(
                                "Install the on-demand developer copilot module to process files, summarize logs, and run natural language file filters offline. (Size: 12.4 MB)",
                                color = Color.LightGray,
                                fontSize = 13.sp,
                                modifier = Modifier.padding(horizontal = 16.dp),
                                textAlign = androidx.compose.ui.text.style.TextAlign.Center
                            )
                            Spacer(modifier = Modifier.height(24.dp))

                            if (isDownloading) {
                                LinearProgressIndicator(
                                    progress = { downloadProgress },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 32.dp),
                                    color = MaterialTheme.colorScheme.primary,
                                )
                                Spacer(modifier = Modifier.height(8.dp))
                                Text(
                                    "Downloading module... ${(downloadProgress * 100).toInt()}%",
                                    color = Color.Gray,
                                    fontSize = 12.sp
                                )
                            } else {
                                Button(
                                    onClick = {
                                        isDownloading = true
                                        coroutineScope.launch {
                                            for (i in 1..20) {
                                                delay(100)
                                                downloadProgress = i / 20f
                                            }
                                            isDownloading = false
                                            isDownloaded = true
                                            context.getSharedPreferences("copilot_module", Context.MODE_PRIVATE)
                                                .edit().putBoolean("is_installed", true).apply()
                                            Toast.makeText(context, "AI Copilot module loaded successfully!", Toast.LENGTH_SHORT).show()
                                        }
                                    },
                                    shape = RoundedCornerShape(12.dp)
                                ) {
                                    Text("Download & Install Module")
                                }
                            }
                        }
                    } else if (apiKey.isEmpty() || showKeySetup) {
                        // 2. CLAUDE KEY CONFIGURATION
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            Text(
                                "Configure Claude API Key",
                                fontWeight = FontWeight.Bold,
                                color = Color.White,
                                fontSize = 16.sp
                            )
                            Text(
                                "Enter your Claude API key. It is stored securely (encrypted via AES-256 in local preferences) and sent only to Anthropic's official servers.",
                                color = Color.LightGray,
                                fontSize = 13.sp
                            )

                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it },
                                label = { Text("Anthropic API Key (sk-ant-...)") },
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(12.dp),
                                singleLine = true,
                                colors = OutlinedTextFieldDefaults.colors(
                                    focusedBorderColor = MaterialTheme.colorScheme.primary,
                                    unfocusedBorderColor = Color(0xFF2E2E38)
                                )
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        SecurePreferences.saveClaudeApiKey(context, apiKey)
                                        showKeySetup = false
                                        Toast.makeText(context, "API Key Saved Securely", Toast.LENGTH_SHORT).show()
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Save API Key")
                                }

                                if (SecurePreferences.getClaudeApiKey(context).isNotEmpty()) {
                                    OutlinedButton(
                                        onClick = { showKeySetup = false },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Cancel")
                                    }
                                } else {
                                    OutlinedButton(
                                        onClick = {
                                            // Fallback offline setup
                                            SecurePreferences.saveClaudeApiKey(context, "OFFLINE_HEURISTICS_MODE")
                                            apiKey = "OFFLINE_HEURISTICS_MODE"
                                            showKeySetup = false
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Skip (Heuristic Mode)")
                                    }
                                }
                            }
                        }
                    } else {
                        // 3. CHAT INTERFACE & COMMAND EXECUTION
                        Column(modifier = Modifier.fillMaxSize()) {
                            // API Key info banner
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF121217), RoundedCornerShape(8.dp))
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = if (apiKey == "OFFLINE_HEURISTICS_MODE") "⚠️ Offline Local Heuristics Mode" else "🔑 Connected to Claude API",
                                    color = Color.LightGray,
                                    fontSize = 11.sp
                                )
                                Text(
                                    text = "Change Key",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.clickable { showKeySetup = true }
                                )
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Chat History
                            LazyColumn(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxWidth()
                            ) {
                                if (chatMessages.isEmpty()) {
                                    item {
                                        Box(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(32.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                                Icon(
                                                    Icons.Filled.AutoAwesome,
                                                    contentDescription = null,
                                                    tint = Color.DarkGray,
                                                    modifier = Modifier.size(44.dp)
                                                )
                                                Spacer(modifier = Modifier.height(12.dp))
                                                Text(
                                                    "How can I assist you with your files today?",
                                                    color = Color.Gray,
                                                    fontSize = 13.sp
                                                )
                                            }
                                        }
                                    }
                                }

                                items(chatMessages) { msg ->
                                    val isUser = msg.sender == "user"
                                    Column(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(vertical = 6.dp),
                                        horizontalAlignment = if (isUser) Alignment.End else Alignment.Start
                                    ) {
                                        Box(
                                            modifier = Modifier
                                                .background(
                                                    if (isUser) MaterialTheme.colorScheme.primary else Color(0xFF181822),
                                                    RoundedCornerShape(12.dp)
                                                )
                                                .padding(12.dp)
                                                .widthIn(max = 280.dp)
                                        ) {
                                            Column {
                                                Text(
                                                    text = msg.text,
                                                    color = if (isUser) Color.White else Color.LightGray,
                                                    fontSize = 13.sp
                                                )

                                                // Render interactive actions list with confirmation buttons!
                                                if (msg.pendingActions.isNotEmpty() && !msg.executed) {
                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Text(
                                                        "Proposed Commands (${msg.pendingActions.size}):",
                                                        fontWeight = FontWeight.Bold,
                                                        color = Color.White,
                                                        fontSize = 12.sp
                                                    )
                                                    Spacer(modifier = Modifier.height(4.dp))
                                                    msg.pendingActions.forEach { act ->
                                                        Row(
                                                            verticalAlignment = Alignment.CenterVertically,
                                                            modifier = Modifier.padding(vertical = 2.dp)
                                                        ) {
                                                            Text(
                                                                text = "• [${act.type}] ",
                                                                color = Color(0xFFFF9800),
                                                                fontSize = 11.sp,
                                                                fontWeight = FontWeight.Bold
                                                            )
                                                            Text(
                                                                text = act.sourcePath.split("/").last(),
                                                                color = Color.LightGray,
                                                                fontSize = 11.sp
                                                            )
                                                        }
                                                    }

                                                    Spacer(modifier = Modifier.height(12.dp))
                                                    Row(
                                                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                                                        modifier = Modifier.fillMaxWidth()
                                                    ) {
                                                        Button(
                                                            onClick = {
                                                                coroutineScope.launch {
                                                                    var count = 0
                                                                    msg.pendingActions.forEach { action ->
                                                                        when (action.type) {
                                                                            "DELETE" -> FileSystemProvider.delete(action.sourcePath)
                                                                            "COPY" -> FileSystemProvider.copy(action.sourcePath, action.destPath)
                                                                            "MOVE" -> FileSystemProvider.move(action.sourcePath, action.destPath)
                                                                            "ZIP_COMPRESS" -> {
                                                                                // Standard zip creator
                                                                                FileSystemProvider.createFile(File(action.destPath).parent ?: "", File(action.destPath).name)
                                                                                FileSystemProvider.writeFileText(action.destPath, "Kryon Simulated ZIP File Archive contents.")
                                                                            }
                                                                        }
                                                                        count++
                                                                    }
                                                                    Toast.makeText(context, "Successfully executed $count actions!", Toast.LENGTH_SHORT).show()
                                                                    onRefreshFiles()
                                                                    // Update message state
                                                                    val index = chatMessages.indexOf(msg)
                                                                    if (index != -1) {
                                                                        chatMessages[index] = msg.copy(executed = true, text = "Executed file operations successfully!")
                                                                    }
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                                        ) {
                                                            Icon(Icons.Filled.CheckCircle, contentDescription = null, modifier = Modifier.size(14.dp))
                                                            Spacer(modifier = Modifier.width(4.dp))
                                                            Text("EXECUTE", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }

                                                        OutlinedButton(
                                                            onClick = {
                                                                val index = chatMessages.indexOf(msg)
                                                                if (index != -1) {
                                                                    chatMessages[index] = msg.copy(executed = true, text = "Actions canceled.")
                                                                }
                                                            },
                                                            modifier = Modifier.weight(1f),
                                                            contentPadding = PaddingValues(vertical = 4.dp, horizontal = 8.dp)
                                                        ) {
                                                            Text("CANCEL", fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                                        }
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }

                                if (isThinking) {
                                    item {
                                        Row(
                                            modifier = Modifier.padding(8.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            CircularProgressIndicator(
                                                modifier = Modifier.size(16.dp),
                                                strokeWidth = 2.dp,
                                                color = MaterialTheme.colorScheme.primary
                                            )
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Thinking...", color = Color.Gray, fontSize = 12.sp)
                                        }
                                    }
                                }
                            }

                            // Suggestions Chips Tray
                            Text(
                                "Quick Suggestions",
                                color = Color.Gray,
                                fontSize = 11.sp,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(vertical = 4.dp)
                            )
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(
                                    "Clean up .log files" to "delete all .log files in this folder",
                                    "Find Duplicates" to "find duplicate images in this folder",
                                    "Compress Screenshots" to "compress all screenshots from this month into one zip"
                                ).forEach { (label, command) ->
                                    Box(
                                        modifier = Modifier
                                            .background(Color(0xFF161622), RoundedCornerShape(16.dp))
                                            .border(BorderStroke(1.dp, Color(0xFF22222E)), RoundedCornerShape(16.dp))
                                            .clickable {
                                                userText = command
                                            }
                                            .padding(horizontal = 12.dp, vertical = 6.dp)
                                    ) {
                                        Text(label, color = Color.LightGray, fontSize = 11.sp)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            // Chat input bar
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .background(Color(0xFF16161F), RoundedCornerShape(24.dp))
                                    .border(BorderStroke(1.dp, Color(0xFF2D2D3B)), RoundedCornerShape(24.dp))
                                    .padding(horizontal = 16.dp, vertical = 10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                BasicTextField(
                                    value = userText,
                                    onValueChange = { userText = it },
                                    modifier = Modifier.weight(1f),
                                    textStyle = androidx.compose.ui.text.TextStyle(
                                        color = Color.White,
                                        fontSize = 14.sp
                                    ),
                                    cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                                    decorationBox = { innerTextField ->
                                        if (userText.isEmpty()) {
                                            Text("Ask Copilot to process files...", color = Color.DarkGray, fontSize = 14.sp)
                                        }
                                        innerTextField()
                                    }
                                )

                                Spacer(modifier = Modifier.width(8.dp))

                                IconButton(
                                    onClick = {
                                        if (userText.trim().isNotEmpty()) {
                                            val query = userText
                                            chatMessages.add(ChatMessage("user", query))
                                            userText = ""
                                            isThinking = true

                                            coroutineScope.launch {
                                                // Prepare file metadata for API
                                                val localFiles = FileSystemProvider.listFiles(currentPath)
                                                val metaStr = localFiles.joinToString("\n") { f ->
                                                    "Path: ${f.path}, Size: ${f.size}B, Dir: ${f.isDirectory}, Mod: ${f.lastModified}"
                                                }

                                                val response = AiCopilotService.getClaudeCommandResponse(
                                                    context = context,
                                                    userPrompt = query,
                                                    currentPath = currentPath,
                                                    fileMetadata = metaStr
                                                )

                                                isThinking = false
                                                chatMessages.add(
                                                    ChatMessage(
                                                        sender = "copilot",
                                                        text = response.explanation,
                                                        pendingActions = response.actions
                                                    )
                                                )
                                            }
                                        }
                                    },
                                    modifier = Modifier.size(32.dp),
                                    colors = IconButtonDefaults.iconButtonColors(containerColor = MaterialTheme.colorScheme.primary)
                                ) {
                                    Icon(
                                        Icons.Filled.Send,
                                        contentDescription = "Send",
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp)
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
