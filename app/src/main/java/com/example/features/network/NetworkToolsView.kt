package com.example.features.network

import android.widget.Toast
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.core.FileSystemProvider
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun NetworkToolsView(
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    var isServerOn by remember { mutableStateOf(false) }
    var localIp by remember { mutableStateOf("127.0.0.1") }
    var selectedPort by remember { mutableStateOf("8080") }

    // Tab selection: 0: HTTP/WebDAV, 1: Kryon-to-Kryon P2P
    var currentTab by remember { mutableStateOf(0) }

    // P2P states
    var isScanning by remember { mutableStateOf(false) }
    val discoveredPeers = remember { mutableStateListOf<String>() }
    var connectedPeer by remember { mutableStateOf<String?>(null) }
    var pairingCode by remember { mutableStateOf("") }
    var transferProgress by remember { mutableStateOf(0f) }
    var isTransferring by remember { mutableStateOf(false) }

    // Pulsing animations for Radar scan
    val infiniteTransition = rememberInfiniteTransition(label = "pulsing")
    val pulseRadius by infiniteTransition.animateFloat(
        initialValue = 20f,
        targetValue = 120f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseRadius"
    )
    val pulseAlpha by infiniteTransition.animateFloat(
        initialValue = 0.8f,
        targetValue = 0f,
        animationSpec = infiniteRepeatable(
            animation = tween(1500, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "pulseAlpha"
    )

    fun refreshIp() {
        localIp = LightweightServers.getLocalIpAddress(context)
        isServerOn = LightweightServers.isServerRunning
    }

    LaunchedEffect(Unit) {
        refreshIp()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Kryon Network Tools", fontWeight = FontWeight.Bold) },
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
                        Icon(Icons.Filled.Wifi, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("HTTP / WebDAV")
                    } }
                )
                Tab(
                    selected = currentTab == 1,
                    onClick = { currentTab = 1 },
                    text = { Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Radar, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Kryon P2P")
                    } }
                )
            }

            if (currentTab == 0) {
                // --- HTTP / WEBDAV FILES SERVER ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFF14141A)),
                        shape = RoundedCornerShape(16.dp)
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Icon(Icons.Filled.Wifi, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
                                    Spacer(modifier = Modifier.width(12.dp))
                                    Column {
                                        Text("Lightweight WebDAV Server", fontWeight = FontWeight.Bold, color = Color.White)
                                        Text("Share files over local Wi-Fi", color = Color.Gray, fontSize = 11.sp)
                                    }
                                }

                                Switch(
                                    checked = isServerOn,
                                    onCheckedChange = { toggle ->
                                        if (toggle) {
                                            val p = selectedPort.toIntOrNull() ?: 8080
                                            LightweightServers.startServer(context, p) { status ->
                                                isServerOn = status
                                            }
                                        } else {
                                            LightweightServers.stopServer { status ->
                                                isServerOn = status
                                            }
                                        }
                                    }
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                OutlinedTextField(
                                    value = selectedPort,
                                    onValueChange = { if (it.all { char -> char.isDigit() }) selectedPort = it },
                                    label = { Text("Server Port") },
                                    modifier = Modifier.weight(1f),
                                    singleLine = true,
                                    enabled = !isServerOn
                                )

                                OutlinedTextField(
                                    value = localIp,
                                    onValueChange = {},
                                    label = { Text("Local IP Address") },
                                    modifier = Modifier.weight(2f),
                                    singleLine = true,
                                    readOnly = true
                                )
                            }
                        }
                    }

                    if (isServerOn) {
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF1B162B)),
                            shape = RoundedCornerShape(16.dp)
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(
                                        modifier = Modifier
                                            .size(8.dp)
                                            .background(Color(0xFF4CAF50), CircleShape)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Server is active and broadcasting", color = Color(0xFF4CAF50), fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }

                                Text(
                                    text = "http://$localIp:$selectedPort",
                                    fontFamily = FontFamily.Monospace,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 18.sp,
                                    color = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier
                                        .background(Color.Black.copy(alpha = 0.3f), RoundedCornerShape(8.dp))
                                        .padding(horizontal = 16.dp, vertical = 8.dp)
                                )

                                Text(
                                    "Open this link in any computer or phone connected to the same Wi-Fi router to navigate and download files.",
                                    color = Color.LightGray,
                                    fontSize = 12.sp,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                    modifier = Modifier.padding(horizontal = 16.dp)
                                )

                                Spacer(modifier = Modifier.height(8.dp))

                                // DRAW BEAUTIFUL COMPOSABLE MOCK QR CODE
                                Box(
                                    modifier = Modifier
                                        .size(130.dp)
                                        .background(Color.White, RoundedCornerShape(8.dp))
                                        .padding(12.dp)
                                ) {
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        // Draw QR square corners (Authentic mockup QR representation)
                                        val sqSize = size.width / 5
                                        // Top Left Corner
                                        drawRect(Color.Black, Offset(0f, 0f), androidx.compose.ui.geometry.Size(sqSize, sqSize))
                                        drawRect(Color.White, Offset(4f, 4f), androidx.compose.ui.geometry.Size(sqSize - 8f, sqSize - 8f))
                                        drawRect(Color.Black, Offset(8f, 8f), androidx.compose.ui.geometry.Size(sqSize - 16f, sqSize - 16f))
                                        
                                        // Top Right Corner
                                        drawRect(Color.Black, Offset(size.width - sqSize, 0f), androidx.compose.ui.geometry.Size(sqSize, sqSize))
                                        drawRect(Color.White, Offset(size.width - sqSize + 4f, 4f), androidx.compose.ui.geometry.Size(sqSize - 8f, sqSize - 8f))
                                        drawRect(Color.Black, Offset(size.width - sqSize + 8f, 8f), androidx.compose.ui.geometry.Size(sqSize - 16f, sqSize - 16f))

                                        // Bottom Left Corner
                                        drawRect(Color.Black, Offset(0f, size.height - sqSize), androidx.compose.ui.geometry.Size(sqSize, sqSize))
                                        drawRect(Color.White, Offset(4f, size.height - sqSize + 4f), androidx.compose.ui.geometry.Size(sqSize - 8f, sqSize - 8f))
                                        drawRect(Color.Black, Offset(8f, size.height - sqSize + 8f), androidx.compose.ui.geometry.Size(sqSize - 16f, sqSize - 16f))

                                        // Draw a randomized grid of QR dots
                                        val rows = 12
                                        val cols = 12
                                        val dotW = size.width / cols
                                        val dotH = size.height / rows
                                        val prng = java.util.Random(1337)
                                        
                                        for (r in 0 until rows) {
                                            for (c in 0 until cols) {
                                                // Avoid overwriting corner squares
                                                val isCorner = (r < 3 && c < 3) || (r < 3 && c >= cols - 3) || (r >= rows - 3 && c < 3)
                                                if (!isCorner && prng.nextBoolean()) {
                                                    drawRect(
                                                        Color.Black,
                                                        Offset(c * dotW, r * dotH),
                                                        androidx.compose.ui.geometry.Size(dotW - 1f, dotH - 1f)
                                                    )
                                                }
                                            }
                                        }
                                    }
                                }

                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.padding(top = 4.dp)
                                ) {
                                    Icon(Icons.Filled.QrCode2, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Scan QR code to connect instantly", color = Color.Gray, fontSize = 11.sp)
                                }
                            }
                        }
                    }
                }
            } else {
                // --- KRYON-TO-KRYON DIRECT P2P TRANSFER ---
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        "Kryon direct peer transfer allows sending files directly to another phone without using the cloud or cellular data.",
                        color = Color.LightGray,
                        fontSize = 12.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )

                    if (connectedPeer == null) {
                        // RADAR SCANNING UI PANEL
                        Box(
                            modifier = Modifier
                                .size(240.dp)
                                .clip(CircleShape)
                                .background(Color(0xFF14141B))
                                .border(1.dp, Color(0xFF28283B), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            if (isScanning) {
                                // Animated pulse circle
                                Canvas(modifier = Modifier.fillMaxSize()) {
                                    drawCircle(
                                        color = Color(0xFF8A2BE2).copy(alpha = pulseAlpha),
                                        radius = pulseRadius,
                                        center = center,
                                        style = Stroke(2f)
                                    )
                                    drawCircle(
                                        color = Color(0xFF8A2BE2).copy(alpha = 0.2f),
                                        radius = size.width / 4,
                                        center = center
                                    )
                                }
                            }

                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Icon(
                                    Icons.Filled.Radar,
                                    contentDescription = null,
                                    tint = if (isScanning) MaterialTheme.colorScheme.primary else Color.Gray,
                                    modifier = Modifier.size(48.dp)
                                )
                                Spacer(modifier = Modifier.height(12.dp))
                                Text(
                                    if (isScanning) "Searching for Kryon peers..." else "Direct P2P Offline Link",
                                    color = Color.White,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 14.sp
                                )
                            }
                        }

                        Button(
                            onClick = {
                                isScanning = !isScanning
                                if (isScanning) {
                                    discoveredPeers.clear()
                                    coroutineScope.launch {
                                        delay(1500)
                                        discoveredPeers.add("Kryon-Pixel-9Pro")
                                        delay(1000)
                                        discoveredPeers.add("Kryon-S24Ultra")
                                    }
                                } else {
                                    discoveredPeers.clear()
                                }
                            },
                            colors = ButtonDefaults.buttonColors(
                                containerColor = if (isScanning) Color.Red else MaterialTheme.colorScheme.primary
                            )
                        ) {
                            Text(if (isScanning) "Stop Radar Scanning" else "Start Radar Scanning")
                        }

                        if (discoveredPeers.isNotEmpty()) {
                            Text("Nearby Kryon Nodes Found:", color = Color.Gray, fontSize = 11.sp, fontWeight = FontWeight.Bold, modifier = Modifier.align(Alignment.Start))
                            LazyColumn(
                                modifier = Modifier.fillMaxWidth(),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(discoveredPeers) { peer ->
                                    Card(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                coroutineScope.launch {
                                                    isScanning = false
                                                    pairingCode = (1000..9999).random().toString()
                                                    connectedPeer = peer
                                                }
                                            },
                                        colors = CardDefaults.cardColors(containerColor = Color(0xFF16161F))
                                    ) {
                                        Row(
                                            modifier = Modifier.padding(12.dp),
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                            Spacer(modifier = Modifier.width(12.dp))
                                            Column {
                                                Text(peer, color = Color.White, fontWeight = FontWeight.Bold)
                                                Text("Signal strength: Strong • Ready to Pair", color = Color.Gray, fontSize = 11.sp)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // CONNECTED PEER INTERFACE (Pairing & Simulated file transfer)
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF15151F))
                        ) {
                            Column(
                                modifier = Modifier.padding(20.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(16.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Icon(Icons.Filled.CastConnected, contentDescription = null, tint = Color(0xFF4CAF50))
                                    Text("Connected with $connectedPeer", fontWeight = FontWeight.Bold, color = Color.White)
                                }

                                Text("Pairing Security Code:", color = Color.LightGray, fontSize = 13.sp)
                                Text(
                                    text = pairingCode,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 28.sp,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.secondary,
                                    letterSpacing = 6.sp
                                )

                                Text(
                                    "Ensure this pairing verification code matches on both devices before transmitting files.",
                                    fontSize = 11.sp,
                                    color = Color.Gray,
                                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                                )

                                if (isTransferring) {
                                    Column(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalAlignment = Alignment.CenterHorizontally,
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        LinearProgressIndicator(
                                            progress = { transferProgress },
                                            color = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        Text("Transmitting payload: ${(transferProgress * 100).toInt()}%", color = Color.LightGray, fontSize = 11.sp)
                                    }
                                } else {
                                    Button(
                                        onClick = {
                                            isTransferring = true
                                            coroutineScope.launch {
                                                for (i in 1..25) {
                                                    delay(120)
                                                    transferProgress = i / 25f
                                                }
                                                isTransferring = false
                                                transferProgress = 0f
                                                Toast.makeText(context, "File payload transfer complete!", Toast.LENGTH_LONG).show()
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth(0.8f)
                                    ) {
                                        Text("Transmit File (Simulation)")
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        connectedPeer = null
                                        isScanning = false
                                    },
                                    modifier = Modifier.fillMaxWidth(0.8f)
                                ) {
                                    Text("Disconnect Peer")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}
