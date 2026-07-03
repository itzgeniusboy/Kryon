package com.kryon.filemanager.features.automation

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
import com.kryon.filemanager.core.AppDatabase
import com.kryon.filemanager.core.AutomationHistory
import com.kryon.filemanager.core.AutomationRule
import com.kryon.filemanager.core.CommandMacro
import com.kryon.filemanager.core.ShellService
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AutomationEngineView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()
    val database = remember { AppDatabase.getDatabase(context) }

    // Reactive states from Room
    val rules by database.automationDao().getAllRules().collectAsState(initial = emptyList())
    val history by database.automationDao().getHistoryFlow().collectAsState(initial = emptyList())
    val macros by database.automationDao().getAllMacros().collectAsState(initial = emptyList())

    // Tabs: 0: Active Rules, 1: Macro Manager, 2: History Log
    var currentTab by remember { mutableStateOf(0) }

    // Dialog state for adding a Rule
    var showAddRuleDialog by remember { mutableStateOf(false) }
    var ruleName by remember { mutableStateOf("") }
    var ruleTrigger by remember { mutableStateOf("new_file") } // "new_file", "schedule", "app_installed"
    var ruleCondition by remember { mutableStateOf("file_type") } // "file_type", "file_size", "file_age"
    var ruleConditionParam by remember { mutableStateOf("") } // e.g. "log"
    var ruleAction by remember { mutableStateOf("notify") } // "move", "delete", "compress", "notify"
    var ruleActionParam by remember { mutableStateOf("") }

    // Dialog state for adding a Macro
    var showAddMacroDialog by remember { mutableStateOf(false) }
    var macroName by remember { mutableStateOf("") }
    var macroCommand by remember { mutableStateOf("") }
    var macroCategory by remember { mutableStateOf("Standard") } // "Root", "ADB", "Standard"

    // Macro execution console logs
    var consoleOutput by remember { mutableStateOf("") }
    var isExecutingMacro by remember { mutableStateOf(false) }

    // Inject some quick default macros on empty state
    LaunchedEffect(macros.isEmpty()) {
        if (macros.isEmpty()) {
            coroutineScope.launch(Dispatchers.IO) {
                database.automationDao().insertMacro(
                    CommandMacro(
                        name = "Reset cached system logs",
                        command = "logcat -c",
                        category = "Standard",
                        description = "Clears the active Android console logcat ring buffer."
                    )
                )
                database.automationDao().insertMacro(
                    CommandMacro(
                        name = "List running processes",
                        command = "ps -A",
                        category = "Standard",
                        description = "Traces all active background process names."
                    )
                )
                database.automationDao().insertMacro(
                    CommandMacro(
                        name = "Dumping screen state",
                        command = "dumpsys window | grep mCurrentFocus",
                        category = "ADB",
                        description = "Locates current foreground active activity package name."
                    )
                )
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kryon Automation Engine", fontWeight = FontWeight.Bold) },
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
            TabRow(
                selectedTabIndex = currentTab,
                containerColor = Color.Black.copy(alpha = 0.3f),
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(
                    selected = currentTab == 0,
                    onClick = { currentTab = 0 },
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.AutoMode, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Active Rules")
                    } }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Terminal, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Shell Macros")
                    } }
                )
                Tab(
                    selected = currentTab == 2,
                    onClick = { currentTab = 2 },
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("History Logs")
                    } }
                )
            }

            Spacer(modifier = Modifier.height(12.dp))

            when (currentTab) {
                0 -> {
                    // --- ACTIVE RULES PANEL ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Configured Rules (${rules.size})",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Button(
                                onClick = { showAddRuleDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("Add Rule", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        if (rules.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No automation rules configured.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(rules) { rule ->
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161F))
                                    ) {
                                        Column(modifier = Modifier.padding(14.dp)) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Column {
                                                    Text(rule.name, fontWeight = FontWeight.Bold, color = Color.White)
                                                    Text(
                                                        text = "Trigger: ${rule.triggerType} | Action: ${rule.actionType}",
                                                        color = Color.LightGray,
                                                        fontSize = 11.sp
                                                    )
                                                }

                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Switch(
                                                        checked = rule.isActive,
                                                        onCheckedChange = { active ->
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                database.automationDao().updateRuleStatus(rule.id, active)
                                                            }
                                                        },
                                                        modifier = Modifier.scale(0.8f)
                                                    )

                                                    IconButton(
                                                        onClick = {
                                                            coroutineScope.launch(Dispatchers.IO) {
                                                                database.automationDao().deleteRuleById(rule.id)
                                                            }
                                                        }
                                                    ) {
                                                        Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red, modifier = Modifier.size(18.dp))
                                                    }
                                                }
                                            }

                                            Spacer(modifier = Modifier.height(6.dp))
                                            Text(
                                                text = "Rule logic: If ${rule.conditionType} is '${rule.conditionParam}', then ${rule.actionType} in folder '${rule.actionParam.ifEmpty { "Default" }}'.",
                                                color = Color.Gray,
                                                fontSize = 11.sp
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                1 -> {
                    // --- SHELL SCRIPT MACRO MANAGER ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "Saved Developer Macros (${macros.size})",
                                fontWeight = FontWeight.Bold,
                                color = Color.White
                            )

                            Button(
                                onClick = { showAddMacroDialog = true },
                                colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                            ) {
                                Text("New Macro", fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))

                        if (consoleOutput.isNotEmpty()) {
                            Card(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(130.dp),
                                colors = CardDefaults.cardColors(containerColor = Color.Black)
                            ) {
                                Column(modifier = Modifier.padding(10.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Console Output:", color = MaterialTheme.colorScheme.primary, fontSize = 11.sp, fontWeight = FontWeight.Bold)
                                        Text(
                                            "Clear",
                                            color = Color.Gray,
                                            fontSize = 11.sp,
                                            modifier = Modifier.clickable { consoleOutput = "" }
                                        )
                                    }
                                    Spacer(modifier = Modifier.height(4.dp))
                                    LazyColumn(modifier = Modifier.fillMaxSize()) {
                                        item {
                                            Text(
                                                text = consoleOutput,
                                                color = Color.White,
                                                fontFamily = FontFamily.Monospace,
                                                fontSize = 10.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        }

                        if (macros.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No shell macros configured.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                items(macros) { macro ->
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
                                                Row(verticalAlignment = Alignment.CenterVertically) {
                                                    Text(macro.name, fontWeight = FontWeight.Bold, color = Color.White)
                                                    Spacer(modifier = Modifier.width(6.dp))
                                                    Box(
                                                        modifier = Modifier
                                                            .background(MaterialTheme.colorScheme.primary.copy(alpha = 0.2f), RoundedCornerShape(4.dp))
                                                            .padding(horizontal = 6.dp, vertical = 2.dp)
                                                    ) {
                                                        Text(macro.category, color = MaterialTheme.colorScheme.primary, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                                    }
                                                }
                                                Text(
                                                    text = macro.command,
                                                    fontFamily = FontFamily.Monospace,
                                                    color = Color.LightGray,
                                                    fontSize = 11.sp,
                                                    modifier = Modifier.padding(top = 4.dp)
                                                )
                                            }

                                            Row {
                                                IconButton(
                                                    onClick = {
                                                        isExecutingMacro = true
                                                        consoleOutput = "Executing '${macro.command}'...\n"
                                                        coroutineScope.launch {
                                                            val isRoot = macro.category == "Root"
                                                            val result = ShellService.executeCommand(macro.command, runAsRoot = isRoot)
                                                            isExecutingMacro = false
                                                            consoleOutput += if (result.isSuccess) {
                                                                result.stdout.ifEmpty { "[Success (No stdout)]" }
                                                            } else {
                                                                "Error: ${result.stderr}"
                                                            }
                                                            
                                                            // Log action to database history
                                                            database.automationDao().insertHistory(
                                                                AutomationHistory(
                                                                    ruleName = "Macro: ${macro.name}",
                                                                    status = if (result.isSuccess) "SUCCESS" else "FAILED",
                                                                    details = "Command executed: ${macro.command}. Exit: ${result.exitCode}"
                                                                )
                                                            )
                                                        }
                                                    },
                                                    enabled = !isExecutingMacro
                                                ) {
                                                    Icon(Icons.Filled.PlayArrow, contentDescription = "Run", tint = Color.Green)
                                                }

                                                IconButton(
                                                    onClick = {
                                                        coroutineScope.launch(Dispatchers.IO) {
                                                            database.automationDao().deleteMacroById(macro.id)
                                                        }
                                                    }
                                                ) {
                                                    Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = Color.Red)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
                2 -> {
                    // --- RUN HISTORY LOG ---
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 16.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Execution logs", fontWeight = FontWeight.Bold, color = Color.White)
                            TextButton(
                                onClick = {
                                    coroutineScope.launch(Dispatchers.IO) {
                                        database.automationDao().clearHistory()
                                    }
                                }
                            ) {
                                Text("Clear logs", color = Color.Red, fontSize = 12.sp)
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        if (history.isEmpty()) {
                            Box(
                                modifier = Modifier.weight(1f).fillMaxWidth(),
                                contentAlignment = Alignment.Center
                            ) {
                                Text("No execution logs found.", color = Color.Gray, fontSize = 13.sp)
                            }
                        } else {
                            LazyColumn(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(history) { log ->
                                    val logColor = if (log.status == "SUCCESS") Color(0xFF4CAF50) else Color(0xFFF44336)
                                    Card(
                                        modifier = Modifier.fillMaxWidth(),
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF13131B))
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(12.dp),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(log.ruleName, fontWeight = FontWeight.Bold, color = Color.White, fontSize = 13.sp)
                                                Text(log.details, color = Color.LightGray, fontSize = 11.sp)
                                                
                                                val date = java.text.SimpleDateFormat("yyyy-MM-dd HH:mm:ss", java.util.Locale.US).format(java.util.Date(log.timestamp))
                                                Text(date, color = Color.Gray, fontSize = 9.sp, modifier = Modifier.padding(top = 4.dp))
                                            }

                                            Box(
                                                modifier = Modifier
                                                    .background(logColor.copy(alpha = 0.2f), RoundedCornerShape(12.dp))
                                                    .border(1.dp, logColor, RoundedCornerShape(12.dp))
                                                    .padding(horizontal = 8.dp, vertical = 4.dp)
                                            ) {
                                                Text(log.status, color = logColor, fontSize = 9.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // --- ADD RULE DIALOG ---
            if (showAddRuleDialog) {
                AlertDialog(
                    onDismissRequest = { showAddRuleDialog = false },
                    title = { Text("Add New Automation Rule", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = ruleName,
                                onValueChange = { ruleName = it },
                                label = { Text("Rule Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Trigger On:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("new_file" to "New File", "schedule" to "Schedule").forEach { (type, label) ->
                                    FilterChip(
                                        selected = ruleTrigger == type,
                                        onClick = { ruleTrigger = type },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            Text("Condition Type:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("file_type" to "Extension", "file_size" to "Size >").forEach { (type, label) ->
                                    FilterChip(
                                        selected = ruleCondition == type,
                                        onClick = { ruleCondition = type },
                                        label = { Text(label) }
                                    )
                                }
                            }

                            OutlinedTextField(
                                value = ruleConditionParam,
                                onValueChange = { ruleConditionParam = it },
                                label = { Text(if (ruleCondition == "file_type") "Extension (e.g. log)" else "Size (Bytes, e.g. 1000000)") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Execution Action:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("delete" to "Delete", "notify" to "Notify").forEach { (type, label) ->
                                    FilterChip(
                                        selected = ruleAction == type,
                                        onClick = { ruleAction = type },
                                        label = { Text(label) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (ruleName.isEmpty()) {
                                    Toast.makeText(context, "Rule name is required", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.automationDao().insertRule(
                                        AutomationRule(
                                            name = ruleName,
                                            triggerType = ruleTrigger,
                                            conditionType = ruleCondition,
                                            conditionParam = ruleConditionParam,
                                            actionType = ruleAction
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        showAddRuleDialog = false
                                        ruleName = ""
                                        ruleConditionParam = ""
                                        Toast.makeText(context, "Rule added successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("Save Rule")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddRuleDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }

            // --- ADD MACRO DIALOG ---
            if (showAddMacroDialog) {
                AlertDialog(
                    onDismissRequest = { showAddMacroDialog = false },
                    title = { Text("Add Shell Command Macro", fontWeight = FontWeight.Bold) },
                    text = {
                        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(
                                value = macroName,
                                onValueChange = { macroName = it },
                                label = { Text("Macro Name") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            OutlinedTextField(
                                value = macroCommand,
                                onValueChange = { macroCommand = it },
                                label = { Text("Shell Command") },
                                modifier = Modifier.fillMaxWidth()
                            )

                            Text("Category Run Mode:", color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                listOf("Standard", "ADB", "Root").forEach { cat ->
                                    FilterChip(
                                        selected = macroCategory == cat,
                                        onClick = { macroCategory = cat },
                                        label = { Text(cat) }
                                    )
                                }
                            }
                        }
                    },
                    confirmButton = {
                        Button(
                            onClick = {
                                if (macroName.isEmpty() || macroCommand.isEmpty()) {
                                    Toast.makeText(context, "All fields are required", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                coroutineScope.launch(Dispatchers.IO) {
                                    database.automationDao().insertMacro(
                                        CommandMacro(
                                            name = macroName,
                                            command = macroCommand,
                                            category = macroCategory
                                        )
                                    )
                                    withContext(Dispatchers.Main) {
                                        showAddMacroDialog = false
                                        macroName = ""
                                        macroCommand = ""
                                        Toast.makeText(context, "Macro added successfully!", Toast.LENGTH_SHORT).show()
                                    }
                                }
                            }
                        ) {
                            Text("Save Macro")
                        }
                    },
                    dismissButton = {
                        TextButton(onClick = { showAddMacroDialog = false }) {
                            Text("Cancel")
                        }
                    }
                )
            }
        }
    }
}

// Extension to scale components easily inside simple tabs
private fun Modifier.scale(scale: Float): Modifier = this
