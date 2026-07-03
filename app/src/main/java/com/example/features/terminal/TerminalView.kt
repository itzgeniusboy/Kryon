package com.example.features.terminal

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.ShellService
import kotlinx.coroutines.launch

data class TerminalLine(
    val text: String,
    val type: LineType
)

enum class LineType {
    INPUT,
    STDOUT,
    STDERR,
    SYSTEM
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TerminalView(
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var currentInput by remember { mutableStateOf("") }
    val terminalLines = remember {
        mutableStateListOf(
            TerminalLine("Apex Terminal Emulator v1.0", LineType.SYSTEM),
            TerminalLine("Type commands and hit enter. Prefix with 'su' for root if supported.", LineType.SYSTEM)
        )
    }
    val listState = rememberLazyListState()

    // Auto-scroll to bottom of console logs on new entries
    LaunchedEffect(terminalLines.size) {
        if (terminalLines.isNotEmpty()) {
            listState.animateScrollToItem(terminalLines.size - 1)
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Terminal Emulator", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Text("←", fontSize = 24.sp, color = MaterialTheme.colorScheme.primary)
                    }
                },
                actions = {
                    var showExplainDialog by remember { mutableStateOf(false) }
                    var explanationText by remember { mutableStateOf("") }
                    var isExplaining by remember { mutableStateOf(false) }
                    val context = androidx.compose.ui.platform.LocalContext.current

                    val hasErrors = remember(terminalLines.size) {
                        terminalLines.any { it.type == LineType.STDERR }
                    }

                    if (hasErrors) {
                        TextButton(
                            onClick = {
                                isExplaining = true
                                showExplainDialog = true
                                coroutineScope.launch {
                                    val errLogs = terminalLines.filter { it.type == LineType.STDERR }.joinToString("\n") { it.text }
                                    explanationText = com.example.core.AiCopilotService.queryClaudeText(
                                        context = context,
                                        systemInstruction = "You are an expert system administrator and software engineer. Explain the following shell execution error in clear, plain language, and provide actionable resolution steps.",
                                        userContent = "Shell command stderr logs:\n$errLogs"
                                    )
                                    isExplaining = false
                                }
                            }
                        ) {
                            Text("EXPLAIN ERROR", fontWeight = FontWeight.Bold, color = Color(0xFFF44336))
                        }
                    }

                    TextButton(onClick = { terminalLines.clear() }) {
                        Text("CLEAR", color = MaterialTheme.colorScheme.primary)
                    }

                    if (showExplainDialog) {
                        AlertDialog(
                            onDismissRequest = { showExplainDialog = false },
                            title = { Text("AI Error Explainer", fontWeight = FontWeight.Bold) },
                            text = {
                                Box(modifier = Modifier.heightIn(max = 300.dp)) {
                                    if (isExplaining) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Explaining execution failure...")
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
                                Button(onClick = { showExplainDialog = false }) {
                                    Text("Dismiss")
                                }
                            }
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color.Black.copy(alpha = 0.4f)
                )
            )
        }
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF070709))
                .padding(12.dp)
        ) {
            // Log output view
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
            ) {
                items(terminalLines.size) { index ->
                    val line = terminalLines[index]
                    val color = when (line.type) {
                        LineType.INPUT -> MaterialTheme.colorScheme.primary
                        LineType.STDOUT -> Color.White
                        LineType.STDERR -> Color(0xFFF44336)
                        LineType.SYSTEM -> Color(0xFF8BC34A)
                    }
                    val prefix = when (line.type) {
                        LineType.INPUT -> "$ "
                        else -> ""
                    }
                    Text(
                        text = "$prefix${line.text}",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 13.sp,
                        color = color,
                        modifier = Modifier.padding(vertical = 2.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Command input row
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Color.Black.copy(alpha = 0.5f))
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "$ ",
                    fontFamily = FontFamily.Monospace,
                    fontSize = 14.sp,
                    color = MaterialTheme.colorScheme.primary,
                    fontWeight = FontWeight.Bold
                )
                
                Spacer(modifier = Modifier.width(6.dp))

                BasicTextField(
                    value = currentInput,
                    onValueChange = { currentInput = it },
                    modifier = Modifier.weight(1f),
                    textStyle = androidx.compose.ui.text.TextStyle(
                        fontFamily = FontFamily.Monospace,
                        fontSize = 14.sp,
                        color = Color.White
                    ),
                    keyboardOptions = KeyboardOptions(
                        imeAction = ImeAction.Send
                    ),
                    keyboardActions = KeyboardActions(
                        onSend = {
                            if (currentInput.trim().isNotEmpty()) {
                                val cmd = currentInput
                                terminalLines.add(TerminalLine(cmd, LineType.INPUT))
                                currentInput = ""
                                
                                coroutineScope.launch {
                                    val isRoot = cmd.startsWith("su ")
                                    val cleanCmd = if (isRoot) cmd.substring(3) else cmd
                                    
                                    terminalLines.add(TerminalLine("Executing...", LineType.SYSTEM))
                                    val result = ShellService.executeCommand(cleanCmd, runAsRoot = isRoot)
                                    
                                    // Remove "Executing..." placeholder
                                    if (terminalLines.last().text == "Executing...") {
                                        terminalLines.removeAt(terminalLines.size - 1)
                                    }

                                    if (result.stdout.isNotEmpty()) {
                                        terminalLines.add(TerminalLine(result.stdout, LineType.STDOUT))
                                    }
                                    if (result.stderr.isNotEmpty()) {
                                        terminalLines.add(TerminalLine(result.stderr, LineType.STDERR))
                                    }
                                    if (result.stdout.isEmpty() && result.stderr.isEmpty()) {
                                        terminalLines.add(TerminalLine("[Command returned no output. Exit code: ${result.exitCode}]", LineType.SYSTEM))
                                    }
                                }
                            }
                        }
                    ),
                    cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary)
                )
            }
        }
    }
}
