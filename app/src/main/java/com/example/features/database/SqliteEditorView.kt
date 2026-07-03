package com.example.features.database

import android.database.sqlite.SQLiteDatabase
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SqliteEditorView(
    dbFilePath: String,
    onBack: () -> Unit
) {
    var db by remember { mutableStateOf<SQLiteDatabase?>(null) }
    var tables by remember { mutableStateOf<List<String>>(emptyList()) }
    var selectedTable by remember { mutableStateOf("") }
    var queryInput by remember { mutableStateOf("") }
    var queryResultHeaders by remember { mutableStateOf<List<String>>(emptyList()) }
    var queryResultRows by remember { mutableStateOf<List<List<String>>>(emptyList()) }
    var statusMessage by remember { mutableStateOf("") }
    var errorMessage by remember { mutableStateOf("") }

    // Load database on start
    LaunchedEffect(dbFilePath) {
        try {
            db = SQLiteDatabase.openDatabase(dbFilePath, null, SQLiteDatabase.OPEN_READWRITE)
            statusMessage = "Database opened successfully."
            
            // Query tables
            val cursor = db?.rawQuery("SELECT name FROM sqlite_master WHERE type='table'", null)
            val tableList = mutableListOf<String>()
            cursor?.use {
                while (it.moveToNext()) {
                    tableList.add(it.getString(0))
                }
            }
            tables = tableList
            if (tableList.isNotEmpty()) {
                selectedTable = tableList.first()
            }
        } catch (e: Exception) {
            errorMessage = "Error opening database: ${e.message}"
        }
    }

    // Auto-query when selected table changes
    LaunchedEffect(selectedTable, db) {
        if (selectedTable.isNotEmpty() && db != null) {
            queryInput = "SELECT * FROM \"$selectedTable\" LIMIT 100;"
            executeQuery(db!!, queryInput) { headers, rows, err ->
                queryResultHeaders = headers
                queryResultRows = rows
                errorMessage = err
            }
        }
    }

    DisposableEffect(Unit) {
        onDispose {
            try {
                db?.close()
            } catch (e: Exception) {
                e.printStackTrace()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            text = "SQLite Database Editor",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = dbFilePath.split("/").last(),
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
                .padding(12.dp)
        ) {
            if (errorMessage.isNotEmpty()) {
                Card(
                    modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
                ) {
                    Text(
                        text = errorMessage,
                        color = MaterialTheme.colorScheme.onErrorContainer,
                        modifier = Modifier.padding(12.dp),
                        fontSize = 13.sp
                    )
                }
            }

            // Tables selector row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Table: ", color = Color.Gray, fontSize = 14.sp)
                Spacer(modifier = Modifier.width(8.dp))
                ScrollableTabRow(
                    selectedTabIndex = tables.indexOf(selectedTable).coerceAtLeast(0),
                    edgePadding = 0.dp,
                    containerColor = Color.Transparent,
                    indicator = {},
                    divider = {}
                ) {
                    tables.forEach { table ->
                        Tab(
                            selected = table == selectedTable,
                            onClick = { selectedTable = table },
                            text = {
                                Text(
                                    text = table,
                                    color = if (table == selectedTable) MaterialTheme.colorScheme.primary else Color.Gray,
                                    fontWeight = if (table == selectedTable) FontWeight.Bold else FontWeight.Normal
                                )
                            }
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            // Query Input Box
            OutlinedTextField(
                value = queryInput,
                onValueChange = { queryInput = it },
                label = { Text("SQL Query", color = Color.Gray) },
                modifier = Modifier.fillMaxWidth(),
                singleLine = false,
                maxLines = 3,
                textStyle = TextStyle(fontFamily = FontFamily.Monospace, fontSize = 13.sp),
                trailingIcon = {
                    Button(
                        onClick = {
                            val activeDb = db
                            if (activeDb != null) {
                                executeQuery(activeDb, queryInput) { headers, rows, err ->
                                    queryResultHeaders = headers
                                    queryResultRows = rows
                                    errorMessage = err
                                    if (err.isEmpty()) {
                                        statusMessage = "Executed successfully."
                                    }
                                }
                            }
                        },
                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                        modifier = Modifier.padding(end = 4.dp)
                    ) {
                        Text("Run", fontSize = 12.sp)
                    }
                }
            )

            Spacer(modifier = Modifier.height(12.dp))

            // Results Grid Label
            Text(
                text = "Query Results (${queryResultRows.size} rows)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.secondary,
                fontWeight = FontWeight.Bold
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Scrollable Grid Results
            Box(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp))
                    .background(Color.Black.copy(alpha = 0.3f))
                    .horizontalScroll(rememberScrollState())
            ) {
                if (queryResultHeaders.isEmpty()) {
                    Text(
                        text = "No results to display",
                        color = Color.DarkGray,
                        modifier = Modifier.align(Alignment.Center)
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.fillMaxSize()
                    ) {
                        // Header Row
                        item {
                            Row(
                                modifier = Modifier
                                    .background(Color.Black.copy(alpha = 0.5f))
                                    .padding(vertical = 8.dp)
                            ) {
                                queryResultHeaders.forEach { header ->
                                    Text(
                                        text = header,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier
                                            .width(120.dp)
                                            .padding(horizontal = 8.dp)
                                    )
                                }
                            }
                        }

                        // Data Rows
                        items(queryResultRows.size) { rowIndex ->
                            val row = queryResultRows[rowIndex]
                            Row(
                                modifier = Modifier
                                    .background(if (rowIndex % 2 == 0) Color.Transparent else Color(0xFF16161C))
                                    .padding(vertical = 8.dp)
                            ) {
                                row.forEach { cell ->
                                    Text(
                                        text = cell,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 12.sp,
                                        color = Color.LightGray,
                                        modifier = Modifier
                                            .width(120.dp)
                                            .padding(horizontal = 8.dp),
                                        maxLines = 1
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

private fun executeQuery(
    db: SQLiteDatabase,
    sql: String,
    onResult: (headers: List<String>, rows: List<List<String>>, error: String) -> Unit
) {
    try {
        if (sql.trim().uppercase().startsWith("SELECT") || sql.trim().uppercase().startsWith("PRAGMA")) {
            val cursor = db.rawQuery(sql, null)
            cursor.use { c ->
                val columnNames = c.columnNames.toList()
                val rows = mutableListOf<List<String>>()
                while (c.moveToNext()) {
                    val row = mutableListOf<String>()
                    for (i in 0 until c.columnCount) {
                        val valStr = try {
                            c.getString(i) ?: "NULL"
                        } catch (e: Exception) {
                            "[BLOB/BINARY]"
                        }
                        row.add(valStr)
                    }
                    rows.add(row)
                }
                onResult(columnNames, rows, "")
            }
        } else {
            // Exec SQL (Write / Create / Update / Delete)
            db.execSQL(sql)
            onResult(listOf("Status"), listOf(listOf("SQL executed successfully. Affected rows not queryable.")), "")
        }
    } catch (e: Exception) {
        onResult(emptyList(), emptyList(), e.message ?: "SQL execution error")
    }
}
