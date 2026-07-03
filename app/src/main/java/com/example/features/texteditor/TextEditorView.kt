package com.example.features.texteditor

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.platform.LocalContext
import com.example.core.FileSystemProvider
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TextEditorView(
    filePath: String,
    onBack: () -> Unit
) {
    val coroutineScope = rememberCoroutineScope()
    var textContent by remember { mutableStateOf("") }
    var isLoading by remember { mutableStateOf(true) }
    var logMessage by remember { mutableStateOf("") }
    val extension = remember(filePath) { filePath.split(".").lastOrNull()?.lowercase() ?: "" }

    // Read file on enter
    LaunchedEffect(filePath) {
        isLoading = true
        textContent = FileSystemProvider.readFileText(filePath)
        isLoading = false
    }

    // Highlighting parser
    val highlightedText = remember(textContent, extension) {
        parseSyntaxHighlighting(textContent, extension)
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "Code Editor",
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
                    var showSummaryDialog by remember { mutableStateOf(false) }
                    var summaryText by remember { mutableStateOf("") }
                    var isSummarizing by remember { mutableStateOf(false) }
                    val context = LocalContext.current

                    TextButton(
                        onClick = {
                            isSummarizing = true
                            showSummaryDialog = true
                            coroutineScope.launch {
                                summaryText = com.example.core.AiCopilotService.queryClaudeText(
                                    context = context,
                                    systemInstruction = "You are a professional software architect. Summarize the following source code or text file, highlighting its key classes, architecture patterns, potential optimizations, and general purpose.",
                                    userContent = "File content to summarize:\n$textContent"
                                )
                                isSummarizing = false
                            }
                        }
                    ) {
                        Text("SUMMARIZE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.secondary)
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    TextButton(
                        onClick = {
                            coroutineScope.launch {
                                val success = FileSystemProvider.writeFileText(filePath, textContent)
                                logMessage = if (success) "File saved successfully!" else "Failed to save file."
                            }
                        }
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    }

                    if (showSummaryDialog) {
                        AlertDialog(
                            onDismissRequest = { showSummaryDialog = false },
                            title = { Text("AI Code Summary", fontWeight = FontWeight.Bold) },
                            text = {
                                Box(modifier = Modifier.heightIn(max = 300.dp)) {
                                    if (isSummarizing) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            CircularProgressIndicator(modifier = Modifier.size(24.dp))
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Text("Summarizing file contents...")
                                        }
                                    } else {
                                        androidx.compose.foundation.lazy.LazyColumn {
                                            item {
                                                Text(
                                                    text = summaryText,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    color = Color.LightGray
                                                )
                                            }
                                        }
                                    }
                                }
                            },
                            confirmButton = {
                                Button(onClick = { showSummaryDialog = false }) {
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
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF0F0F11))
        ) {
            if (isLoading) {
                CircularProgressIndicator(
                    modifier = Modifier.align(Alignment.Center),
                    color = MaterialTheme.colorScheme.primary
                )
            } else {
                Column(modifier = Modifier.fillMaxSize()) {
                    // Quick edit tools
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(Color.Black.copy(alpha = 0.5f))
                            .padding(8.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        listOf("{", "}", "(", ")", "[", "]", ";", "=", "\"", "<", ">", "/").forEach { char ->
                            Box(
                                modifier = Modifier
                                    .background(Color(0xFF232329), RoundedCornerShape(4.dp))
                                    .clickable { textContent += char }
                                    .padding(horizontal = 10.dp, vertical = 6.dp)
                            ) {
                                Text(
                                    text = char,
                                    color = Color.LightGray,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                            }
                        }
                    }

                    // Raw editor view
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth()
                            .verticalScroll(rememberScrollState())
                            .padding(12.dp)
                    ) {
                        // Custom syntax highlighted text view with custom editing
                        BasicTextField(
                            value = textContent,
                            onValueChange = { textContent = it },
                            modifier = Modifier.fillMaxWidth(),
                            textStyle = TextStyle(
                                fontFamily = FontFamily.Monospace,
                                fontSize = 13.sp,
                                color = Color.White
                            ),
                            cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
                            decorationBox = { innerTextField ->
                                Box(modifier = Modifier.fillMaxWidth()) {
                                    if (textContent.isEmpty()) {
                                        Text(
                                            text = "Type content here...",
                                            color = Color.DarkGray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 13.sp
                                        )
                                    }
                                    // We overlay the annotated string representation so the text is fully colorized!
                                    Text(
                                        text = highlightedText,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 13.sp,
                                        style = TextStyle(color = Color.Transparent) // Highlighted text is drawn below, while real text is typed
                                    )
                                    innerTextField()
                                }
                            }
                        )
                    }

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
    }
}

// Custom simple regex syntax highlighter for standard files
fun parseSyntaxHighlighting(text: String, extension: String): AnnotatedString {
    return buildAnnotatedString {
        append(text)
        
        // Define color tokens
        val keywordColor = Color(0xFFE5C07B) // Amber
        val funKeywordColor = Color(0xFF61AFEF) // Blue
        val stringColor = Color(0xFF98C379) // Green
        val commentColor = Color(0xFF5C6370) // Gray
        val tagColor = Color(0xFFE06C75) // Red/Pink

        // 1. Highlight standard keywords
        val keywords = listOf(
            "class", "interface", "val", "var", "import", "package", "return", "if",
            "else", "while", "for", "private", "public", "protected", "override", "object",
            "throw", "try", "catch", "finally", "null", "true", "false", "void", "static"
        )
        for (kw in keywords) {
            var index = text.indexOf(kw)
            while (index >= 0) {
                // Ensure word boundary
                val before = if (index > 0) text[index - 1] else ' '
                val after = if (index + kw.length < text.length) text[index + kw.length] else ' '
                if (!before.isLetterOrDigit() && !after.isLetterOrDigit()) {
                    addStyle(
                        style = SpanStyle(color = keywordColor, fontWeight = FontWeight.Bold),
                        start = index,
                        end = index + kw.length
                    )
                }
                index = text.indexOf(kw, index + 1)
            }
        }

        // 2. Highlight functions
        var index = text.indexOf("fun ")
        while (index >= 0) {
            addStyle(
                style = SpanStyle(color = funKeywordColor, fontWeight = FontWeight.Bold),
                start = index,
                end = index + 3
            )
            index = text.indexOf("fun ", index + 1)
        }

        // 3. Highlight comments (// and /* */)
        var commentIndex = text.indexOf("//")
        while (commentIndex >= 0) {
            val endOfLine = text.indexOf("\n", commentIndex)
            val end = if (endOfLine != -1) endOfLine else text.length
            addStyle(
                style = SpanStyle(color = commentColor),
                start = commentIndex,
                end = end
            )
            commentIndex = text.indexOf("//", end)
        }

        // 4. Highlight strings
        var stringIndex = text.indexOf("\"")
        while (stringIndex >= 0) {
            val endOfString = text.indexOf("\"", stringIndex + 1)
            if (endOfString != -1) {
                addStyle(
                    style = SpanStyle(color = stringColor),
                    start = stringIndex,
                    end = endOfString + 1
                )
                stringIndex = text.indexOf("\"", endOfString + 1)
            } else {
                break
            }
        }
    }
}
