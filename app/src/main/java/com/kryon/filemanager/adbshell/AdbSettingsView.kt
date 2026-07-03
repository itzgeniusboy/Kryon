package com.kryon.filemanager.adbshell

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AdbSettingsView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val status by AdbManager.status.collectAsState()
    val logs by AdbManager.logs.collectAsState()

    var ipAddress by remember { mutableStateOf("127.0.0.1") }
    var pairingPort by remember { mutableStateOf("37219") }
    var pairingCode by remember { mutableStateOf("") }
    
    var servicePort by remember { mutableStateOf("41515") }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Wireless Debugging", fontWeight = FontWeight.Bold) },
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
                .background(Color(0xFF0F0F13))
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Step 1: Guide Info
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF16161C))
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        text = "GUIDE: ACCESS Android/data & Android/obb",
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.primary,
                        fontSize = 14.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = "1. Toggle ON \"Developer Options\" and \"Wireless Debugging\" in System Settings.\n" +
                               "2. Tap \"Pair device with pairing code\" inside Wireless Debugging.\n" +
                               "3. Enter the IP, Pairing Port, and Pairing Code below.",
                        fontSize = 12.sp,
                        color = Color.LightGray,
                        lineHeight = 18.sp
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    Button(
                        onClick = { AdbManager.launchDeveloperSettings(context) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Open Developer Options")
                    }
                }
            }

            // Connection Status Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(text = "Status: ", fontSize = 14.sp, color = Color.Gray)
                Text(
                    text = status.name,
                    fontWeight = FontWeight.Bold,
                    color = when (status) {
                        AdbConnectionStatus.CONNECTED -> Color(0xFF4CAF50)
                        AdbConnectionStatus.PAIRED -> MaterialTheme.colorScheme.secondary
                        AdbConnectionStatus.FAILED -> Color.Red
                        else -> Color.LightGray
                    },
                    fontSize = 14.sp
                )
            }

            // Tabs for Pairing and Connecting
            var selectedTab by remember { mutableStateOf(0) }
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = MaterialTheme.colorScheme.primary
            ) {
                Tab(selected = selectedTab == 0, onClick = { selectedTab = 0 }) {
                    Text("1. Pair Device", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
                Tab(selected = selectedTab == 1, onClick = { selectedTab = 1 }) {
                    Text("2. Connect Shell", modifier = Modifier.padding(12.dp), fontWeight = FontWeight.Bold)
                }
            }

            if (selectedTab == 0) {
                // STEP 1: PAIRING SECTION
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("IP Address") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = pairingPort,
                            onValueChange = { pairingPort = it },
                            label = { Text("Pairing Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    OutlinedTextField(
                        value = pairingCode,
                        onValueChange = { pairingCode = it },
                        label = { Text("6-Digit Pairing Code") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    Button(
                        onClick = {
                            val portInt = pairingPort.toIntOrNull() ?: 0
                            AdbManager.startPairing(ipAddress, portInt, pairingCode)
                        },
                        modifier = Modifier.fillMaxWidth(),
                        enabled = pairingCode.isNotEmpty() && status != AdbConnectionStatus.PAIRING
                    ) {
                        Text("Pair Device")
                    }
                }
            } else {
                // STEP 2: CONNECTING SECTION
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = ipAddress,
                            onValueChange = { ipAddress = it },
                            label = { Text("IP Address") },
                            modifier = Modifier.weight(1.5f),
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = servicePort,
                            onValueChange = { servicePort = it },
                            label = { Text("Service Port") },
                            modifier = Modifier.weight(1f),
                            singleLine = true
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Button(
                            onClick = {
                                val portInt = servicePort.toIntOrNull() ?: 0
                                AdbManager.startConnection(ipAddress, portInt)
                            },
                            modifier = Modifier.weight(1f),
                            enabled = status != AdbConnectionStatus.CONNECTING && status != AdbConnectionStatus.CONNECTED
                        ) {
                            Text("Connect Daemon")
                        }

                        Button(
                            onClick = { AdbManager.disconnect() },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color.DarkGray),
                            enabled = status == AdbConnectionStatus.CONNECTED
                        ) {
                            Text("Disconnect")
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(8.dp))

            // Console outputs
            Text(text = "CONNECTION CONSOLE LOGS", fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color.Gray)
            Card(
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .border(1.dp, Color.DarkGray, RoundedCornerShape(8.dp)),
                colors = CardDefaults.cardColors(containerColor = Color.Black.copy(alpha = 0.5f))
            ) {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    items(logs) { log ->
                        Text(
                            text = log,
                            fontFamily = FontFamily.Monospace,
                            fontSize = 11.sp,
                            color = Color(0xFF00FF00) // classic green logger
                        )
                    }
                }
            }
        }
    }
}
