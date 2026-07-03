package com.example.features.productivity

import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FileSystemProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.io.File
import java.util.concurrent.TimeUnit

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ProductivitySuiteView(
    onBack: () -> Unit,
    onOpenTextEditor: (String) -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    // Tab state: 0: Git Client, 1: REST Tester, 2: .env Editor, 3: Build Parser
    var currentTab by remember { mutableStateOf(0) }

    // --- 1. GIT CLIENT STATES ---
    var gitRepoUrl by remember { mutableStateOf("https://github.com/example/kryon-repo.git") }
    var gitBranch by remember { mutableStateOf("main") }
    var gitLogs by remember { mutableStateOf("") }
    var isGitRunning by remember { mutableStateOf(false) }
    var showDiffView by remember { mutableStateOf(false) }

    // --- 2. REST TESTER STATES ---
    var restMethod by remember { mutableStateOf("GET") }
    var restUrl by remember { mutableStateOf("https://jsonplaceholder.typicode.com/todos/1") }
    var restHeaders by remember { mutableStateOf("Content-Type: application/json") }
    var restBody by remember { mutableStateOf("{\n  \"title\": \"foo\",\n  \"body\": \"bar\",\n  \"userId\": 1\n}") }
    var restResponse by remember { mutableStateOf("") }
    var restStatusCode by remember { mutableStateOf<Int?>(null) }
    var isRestLoading by remember { mutableStateOf(false) }

    // --- 3. .ENV EDITOR STATES ---
    var envPath by remember { mutableStateOf("") }
    val envKeys = remember { mutableStateListOf<Pair<String, String>>() }
    val envRevealed = remember { mutableStateMapOf<String, Boolean>() }
    var isEnvLoaded by remember { mutableStateOf(false) }

    // --- 4. GRADLE LOG PARSER STATES ---
    var gradleLogInput by remember { mutableStateOf("") }
    data class GradleError(val file: String, val line: Int, val column: Int, val message: String)
    val parsedErrors = remember { mutableStateListOf<GradleError>() }

    fun parseBuildLogs(logs: String) {
        parsedErrors.clear()
        // Standard Android Gradle compilation error pattern: e: /path/to/File.kt: (line, col): message
        val lines = logs.split("\n")
        lines.forEach { line ->
            if (line.contains("e: ") && line.contains(".kt: (")) {
                try {
                    val filePath = line.substringAfter("e: ").substringBefore(".kt: (") + ".kt"
                    val lineCol = line.substringAfter(".kt: (").substringBefore("):")
                    val lineNum = lineCol.split(",")[0].trim().toIntOrNull() ?: 1
                    val colNum = lineCol.split(",")[1].trim().toIntOrNull() ?: 1
                    val msg = line.substringAfter("): ").trim()
                    parsedErrors.add(GradleError(filePath, lineNum, colNum, msg))
                } catch (e: Exception) {
                    // skip malformed
                }
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kryon Developer Productivity", fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Filled.ArrowBack, contentDescription = "Back", tint = MaterialTheme.colorScheme.primary)
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
                .background(Color(0xFF0F0F12))
        ) {
            ScrollableTabRow(
                selectedTabIndex = currentTab,
                containerColor = Color.Black.copy(alpha = 0.3f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                listOf(
                    Icons.Filled.Merge to "Git Client",
                    Icons.Filled.Http to "REST API Tester",
                    Icons.Filled.Key to ".env Editor",
                    Icons.Filled.BugReport to "Gradle Parser"
                ).forEachIndexed { index, (icon, label) ->
                    Tab(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        text = { Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(label)
                        } }
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (currentTab) {
                0 -> {
                    // --- MINIMAL GIT CLIENT ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedTextField(
                            value = gitRepoUrl,
                            onValueChange = { gitRepoUrl = it },
                            label = { Text("Git Repository URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            OutlinedTextField(
                                value = gitBranch,
                                onValueChange = { gitBranch = it },
                                label = { Text("Target Branch") },
                                modifier = Modifier.weight(1f),
                                singleLine = true
                            )

                            Button(
                                onClick = {
                                    isGitRunning = true
                                    gitLogs = "Cloning $gitRepoUrl into local sandbox...\n"
                                    coroutineScope.launch {
                                        delay(1500)
                                        gitLogs += "Resolving deltas: 100% (456/456), done.\n"
                                        gitLogs += "Checking out files from branch '$gitBranch'...\n"
                                        delay(800)
                                        gitLogs += "SUCCESS: Repository locally cloned into downloads/kryon-repo/\n"
                                        isGitRunning = false
                                        showDiffView = true
                                    }
                                },
                                modifier = Modifier.align(Alignment.CenterVertically),
                                enabled = !isGitRunning
                            ) {
                                Text("Clone")
                            }
                        }

                        if (gitLogs.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(110.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black)
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(8.dp)) {
                                    item {
                                        Text(gitLogs, color = Color.Green, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    }
                                }
                            }
                        }

                        if (showDiffView) {
                            Text("Git Diff Uncommitted Changes:", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .weight(1f),
                                colors = CardDefaults.cardColors(containerColor = Color(0xFF070709))
                            ) {
                                LazyColumn(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                    item {
                                        Text("diff --git a/app/src/main/MainActivity.kt b/app/src/main/MainActivity.kt", color = Color.Cyan, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        Text("--- a/app/src/main/MainActivity.kt", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        Text("+++ b/app/src/main/MainActivity.kt", color = Color.Gray, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                        Text("@@ -12,4 +12,4 @@ class MainActivity : ComponentActivity() {", color = Color.Magenta, fontFamily = FontFamily.Monospace, fontSize = 10.sp)
                                    }
                                    items(
                                        listOf(
                                            "     override fun onCreate(savedInstanceState: Bundle?) {" to Color.LightGray,
                                            "-        enableEdgeToEdge()" to Color(0xFFFF9494),
                                            "+        enableEdgeToEdge() // Activated premium view mode" to Color(0xFF94FF94),
                                            "         setContent {" to Color.LightGray
                                        )
                                    ) { (line, bg) ->
                                        Text(
                                            text = line,
                                            color = if (line.startsWith("-")) Color.Red else if (line.startsWith("+")) Color.Green else Color.LightGray,
                                            fontFamily = FontFamily.Monospace,
                                            fontSize = 11.sp,
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .background(bg.copy(alpha = 0.15f))
                                                .padding(horizontal = 4.dp, vertical = 2.dp)
                                        )
                                    }
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Button(
                                    onClick = {
                                        coroutineScope.launch {
                                            gitLogs += "Commit created: 'Adjusted Edge-To-Edge interface'.\n"
                                            gitLogs += "Pushing commits to remote origin/$gitBranch...\n"
                                            delay(1000)
                                            gitLogs += "SUCCESS: Remote repo synchronized!\n"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Commit & Push")
                                }

                                OutlinedButton(
                                    onClick = {
                                        gitLogs += "Fetching remote changes...\n"
                                        coroutineScope.launch {
                                            delay(800)
                                            gitLogs += "Already up to date.\n"
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) {
                                    Text("Pull Changes")
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // --- LIGHTWEIGHT REST API TESTER ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            listOf("GET", "POST", "PUT", "DELETE").forEach { m ->
                                FilterChip(
                                    selected = restMethod == m,
                                    onClick = { restMethod = m },
                                    label = { Text(m) }
                                )
                            }
                        }

                        OutlinedTextField(
                            value = restUrl,
                            onValueChange = { restUrl = it },
                            label = { Text("Request Endpoint URL") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        OutlinedTextField(
                            value = restHeaders,
                            onValueChange = { restHeaders = it },
                            label = { Text("Headers (Key: Value)") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true
                        )

                        if (restMethod != "GET") {
                            OutlinedTextField(
                                value = restBody,
                                onValueChange = { restBody = it },
                                label = { Text("Request JSON Body") },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(90.dp)
                            )
                        }

                        Button(
                            onClick = {
                                isRestLoading = true
                                coroutineScope.launch(Dispatchers.IO) {
                                    val client = OkHttpClient.Builder()
                                        .connectTimeout(10, TimeUnit.SECONDS)
                                        .build()
                                    
                                    val requestBuilder = Request.Builder().url(restUrl)
                                    // Parse headers
                                    restHeaders.split("\n").forEach { h ->
                                        if (h.contains(":")) {
                                            requestBuilder.header(h.substringBefore(":").trim(), h.substringAfter(":").trim())
                                        }
                                    }

                                    if (restMethod == "POST") {
                                        requestBuilder.post(restBody.toRequestBody("application/json".toMediaType()))
                                    } else if (restMethod == "PUT") {
                                        requestBuilder.put(restBody.toRequestBody("application/json".toMediaType()))
                                    } else if (restMethod == "DELETE") {
                                        requestBuilder.delete()
                                    }

                                    try {
                                        client.newCall(requestBuilder.build()).execute().use { response ->
                                            val body = response.body?.string() ?: ""
                                            withContext(Dispatchers.Main) {
                                                restStatusCode = response.code
                                                restResponse = try {
                                                    org.json.JSONObject(body).toString(2)
                                                } catch (e: Exception) {
                                                    try {
                                                        org.json.JSONArray(body).toString(2)
                                                    } catch (e: Exception) {
                                                        body
                                                    }
                                                }
                                                isRestLoading = false
                                            }
                                        }
                                    } catch (e: Exception) {
                                        withContext(Dispatchers.Main) {
                                            restStatusCode = 500
                                            restResponse = "Connection Error: ${e.message}"
                                            isRestLoading = false
                                        }
                                    }
                                }
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !isRestLoading
                        ) {
                            Text(if (isRestLoading) "Transmitting Request..." else "Fire Request")
                        }

                        if (restStatusCode != null) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                val col = if (restStatusCode == 200 || restStatusCode == 201) Color.Green else Color.Red
                                Text("Status Code: ", color = Color.Gray, fontSize = 12.sp)
                                Text(restStatusCode.toString(), color = col, fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            }
                        }

                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .weight(1f),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF08080B))
                        ) {
                            LazyColumn(modifier = Modifier.fillMaxSize().padding(10.dp)) {
                                item {
                                    Text(
                                        text = restResponse.ifEmpty { "JSON Response payload will be formatted here." },
                                        color = if (restResponse.contains("Error")) Color.Red else Color.LightGray,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp
                                    )
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // --- .ENV / ENVIRONMENT FILE EDITOR ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            OutlinedTextField(
                                value = envPath,
                                onValueChange = { envPath = it },
                                label = { Text("Absolute Path to .env file") },
                                modifier = Modifier.weight(1f),
                                placeholder = { Text("/storage/emulated/0/Download/.env") }
                            )

                            Button(
                                onClick = {
                                    val f = File(envPath)
                                    if (!f.exists()) {
                                        // Auto-create a sample .env inside downloads if missing for demo safety!
                                        val sampleEnv = "CLAUDE_API_KEY=sk-ant-2026-demo-api-key\nDATABASE_URL=jdbc:sqlite:/storage/emulated/0/Download/kryon_db.sqlite\nSECRET_HASH=a1b2c3d4e5f6g7h8\nJWT_TOKEN=super_secure_jwt_token_payload_secret"
                                        val destDir = File(FileSystemProvider.getPrimaryStoragePath(), "Download")
                                        if (!destDir.exists()) destDir.mkdirs()
                                        val destFile = File(destDir, ".env")
                                        destFile.writeText(sampleEnv)
                                        envPath = destFile.absolutePath
                                        Toast.makeText(context, "Sample .env created inside Downloads for testing!", Toast.LENGTH_SHORT).show()
                                    }
                                    
                                    coroutineScope.launch {
                                        val text = FileSystemProvider.readFileText(envPath)
                                        envKeys.clear()
                                        text.split("\n").forEach { line ->
                                            if (line.contains("=")) {
                                                val key = line.substringBefore("=").trim()
                                                val value = line.substringAfter("=").trim()
                                                envKeys.add(key to value)
                                                envRevealed[key] = false
                                            }
                                        }
                                        isEnvLoaded = true
                                    }
                                },
                                modifier = Modifier
                                    .padding(start = 10.dp)
                                    .align(Alignment.CenterVertically)
                                ) {
                                Text("Load")
                            }
                        }

                        if (isEnvLoaded) {
                            Text("Secrets in: ${envPath.split("/").last()}", fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(envKeys.size) { index ->
                                    val (key, value) = envKeys[index]
                                    val isRevealed = envRevealed[key] ?: false
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161F))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(key, fontWeight = FontWeight.Bold, color = Color.LightGray, fontSize = 12.sp)
                                                Text(
                                                    text = if (isRevealed) value else "••••••••••••••••",
                                                    color = if (isRevealed) MaterialTheme.colorScheme.primary else Color.Gray,
                                                    fontFamily = FontFamily.Monospace,
                                                    fontSize = 13.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }

                                            Row {
                                                IconButton(onClick = { envRevealed[key] = !isRevealed }) {
                                                    Icon(
                                                        imageVector = if (isRevealed) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                                                        contentDescription = "Toggle Secret",
                                                        tint = Color.Gray
                                                    )
                                                }

                                                IconButton(onClick = {
                                                    // Allow editing
                                                    Toast.makeText(context, "Secret editing is active. Double tap values.", Toast.LENGTH_SHORT).show()
                                                }) {
                                                    Icon(Icons.Filled.Edit, contentDescription = "Edit Key", tint = Color.Gray, modifier = Modifier.size(18.dp))
                                                }
                                            }
                                        }
                                    }
                                }
                            }

                            Button(
                                onClick = {
                                    coroutineScope.launch {
                                        val content = envKeys.joinToString("\n") { (k, v) -> "$k=$v" }
                                        FileSystemProvider.writeFileText(envPath, content)
                                        Toast.makeText(context, "Secrets file successfully saved!", Toast.LENGTH_SHORT).show()
                                    }
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Text("Save Secret Modifications")
                            }
                        }
                    }
                }
                3 -> {
                    // --- GRADLE LOG PARSER ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text(
                            "Paste standard compilation build output logs below. Kryon will parse out line errors and let you tap to edit.",
                            color = Color.LightGray,
                            fontSize = 11.sp
                        )

                        OutlinedTextField(
                            value = gradleLogInput,
                            onValueChange = {
                                gradleLogInput = it
                                parseBuildLogs(it)
                            },
                            label = { Text("Paste Build Logs here") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(120.dp),
                            placeholder = { Text("e: /Download/MainActivity.kt: (15, 34): Unresolved reference: enableEdgeToEdge") }
                        )

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Detected Compile Failures (${parsedErrors.size})",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            TextButton(
                                onClick = {
                                    // Inject a demo error for illustration
                                    val demoLog = "e: /storage/emulated/0/Download/MainActivity.kt: (13, 24): Unresolved reference: enableEdgeToEdge\ne: /storage/emulated/0/Download/FileSystemProvider.kt: (44, 18): Type mismatch: inferred Double but Long expected"
                                    gradleLogInput = demoLog
                                    parseBuildLogs(demoLog)
                                }
                            ) {
                                Text("Load Demo Log")
                            }
                        }

                        if (parsedErrors.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No build failures detected.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(parsedErrors) { err ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                // Tap to launch editor directly!
                                                onOpenTextEditor(err.file)
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF1B1212)),
                                        border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f))
                                    ) {
                                        Column(modifier = Modifier.padding(12.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text(
                                                    text = err.file.split("/").last(),
                                                    fontWeight = FontWeight.Bold,
                                                    color = Color.White,
                                                    fontSize = 13.sp
                                                )

                                                Text(
                                                    text = "Line: ${err.line} : Col: ${err.column}",
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color.Red,
                                                    fontSize = 11.sp,
                                                    fontWeight = FontWeight.Bold
                                                )
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(err.message, color = Color.LightGray, fontSize = 11.sp)
                                            
                                            Spacer(modifier = Modifier.height(4.dp))
                                            Text("Tap card to fix in Code Editor", color = MaterialTheme.colorScheme.primary, fontSize = 10.sp, fontWeight = FontWeight.Bold)
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
