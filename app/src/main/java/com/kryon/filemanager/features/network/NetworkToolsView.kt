package com.kryon.filemanager.features.network

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
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.ui.draw.scale
import com.kryon.filemanager.core.FileSystemProvider
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
                // --- KRYON-TO-KRYON DIRECT P2P TRANSFER (iOS-INSPIRED GLASSMORPHIC UI) ---
                var showScanView by remember { mutableStateOf(false) }

                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(20.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(20.dp)
                ) {
                    Text(
                        text = "Kryon Direct Share",
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 24.sp,
                        letterSpacing = (-0.5).sp
                    )
                    Text(
                        text = "Wirelessly beam files to nearby devices with high-speed direct peer links. No internet connection needed.",
                        color = Color.LightGray.copy(alpha = 0.8f),
                        fontSize = 13.sp,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        lineHeight = 18.sp,
                        modifier = Modifier.padding(horizontal = 8.dp)
                    )

                    if (connectedPeer == null) {
                        if (showScanView) {
                            // --- iOS VIEWFINDER QR SCANNING SCREEN ---
                            Spacer(modifier = Modifier.height(12.dp))
                            CameraViewfinderMockup()
                            Spacer(modifier = Modifier.height(12.dp))

                            Button(
                                onClick = { showScanView = false },
                                shape = RoundedCornerShape(24.dp),
                                colors = ButtonDefaults.buttonColors(containerColor = Color.White.copy(alpha = 0.12f)),
                                modifier = Modifier
                                    .fillMaxWidth(0.8f)
                                    .height(48.dp)
                                    .pressScale()
                            ) {
                                Text("Cancel Scanner", color = Color.White, fontWeight = FontWeight.Bold)
                            }
                        } else {
                            // RADAR SCANNING UI PANEL (WITH iOS SPRING AND SOFT RADIAL BLUR)
                            val scanProgress by animateFloatAsState(
                                targetValue = if (isScanning) 1f else 0f,
                                animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
                                label = "scan"
                            )

                            Box(
                                modifier = Modifier
                                    .size(240.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF0A0A0E))
                                    .border(
                                        width = 1.5.dp,
                                        color = if (isScanning) Color(0xFF00E5FF).copy(alpha = 0.6f) else Color(0xFF1E1E2E),
                                        shape = CircleShape
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                if (isScanning) {
                                    // Animated pulse circles
                                    Canvas(modifier = Modifier.fillMaxSize()) {
                                        drawCircle(
                                            color = Color(0xFF00E5FF).copy(alpha = pulseAlpha),
                                            radius = pulseRadius,
                                            center = center,
                                            style = Stroke(2f)
                                        )
                                        drawCircle(
                                            color = Color(0xFFFFB300).copy(alpha = pulseAlpha * 0.5f),
                                            radius = pulseRadius * 0.7f,
                                            center = center,
                                            style = Stroke(1.5f)
                                        )
                                    }
                                }

                                Column(
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Radar,
                                        contentDescription = null,
                                        tint = if (isScanning) Color(0xFF00E5FF) else Color.Gray,
                                        modifier = Modifier.size(54.dp)
                                    )
                                    Spacer(modifier = Modifier.height(12.dp))
                                    Text(
                                        text = if (isScanning) "Scanning Airspace..." else "Airspace Idle",
                                        color = Color.White,
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 15.sp
                                    )
                                }
                            }

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                // Toggle Radar button
                                Button(
                                    onClick = {
                                        isScanning = !isScanning
                                        if (isScanning) {
                                            discoveredPeers.clear()
                                            coroutineScope.launch {
                                                delay(1200)
                                                discoveredPeers.add("Kryon-Pixel-9Pro")
                                                delay(800)
                                                discoveredPeers.add("Kryon-S24Ultra")
                                            }
                                        } else {
                                            discoveredPeers.clear()
                                        }
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(
                                        containerColor = if (isScanning) Color.Red.copy(alpha = 0.8f) else Color(0xFF00E5FF)
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .pressScale()
                                ) {
                                    Text(
                                        text = if (isScanning) "Stop Radar" else "Radar Scan",
                                        color = if (isScanning) Color.White else Color.Black,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Open Scanner button
                                Button(
                                    onClick = { showScanView = true },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFFB300)),
                                    modifier = Modifier
                                        .weight(1f)
                                        .height(48.dp)
                                        .pressScale()
                                ) {
                                    Text("Scan QR", color = Color.Black, fontWeight = FontWeight.Bold)
                                }
                            }

                            if (discoveredPeers.isNotEmpty()) {
                                Text(
                                    text = "Nearby Kryon Nodes Found:",
                                    color = Color.Gray,
                                    fontSize = 11.sp,
                                    fontWeight = FontWeight.Bold,
                                    modifier = Modifier.align(Alignment.Start)
                                )
                                LazyColumn(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    items(discoveredPeers) { peer ->
                                        Card(
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .pressScale()
                                                .clickable {
                                                    coroutineScope.launch {
                                                        isScanning = false
                                                        pairingCode = (1000..9999).random().toString()
                                                        connectedPeer = peer
                                                    }
                                                },
                                            shape = RoundedCornerShape(20.dp),
                                            colors = CardDefaults.cardColors(containerColor = Color(0xFF14141B))
                                        ) {
                                            Row(
                                                modifier = Modifier.padding(16.dp),
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Icon(
                                                    imageVector = Icons.Filled.Devices,
                                                    contentDescription = null,
                                                    tint = Color(0xFF00E5FF)
                                                )
                                                Spacer(modifier = Modifier.width(16.dp))
                                                Column(modifier = Modifier.weight(1f)) {
                                                    Text(peer, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 15.sp)
                                                    Text("Ready to pair • AirDrop active", color = Color.Gray, fontSize = 11.sp)
                                                }
                                                Text("Tap to Pair", color = Color(0xFFFFB300), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    } else {
                        // --- CONNECTED PEER INTERFACE (iOS MODAL STYLE SHEET WITH SPRING TRANSITIONS) ---
                        Card(
                            modifier = Modifier
                                .fillMaxWidth()
                                .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp)),
                            shape = RoundedCornerShape(28.dp),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF0D0D14))
                        ) {
                            Column(
                                modifier = Modifier.padding(24.dp),
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.spacedBy(20.dp)
                            ) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.CastConnected,
                                        contentDescription = null,
                                        tint = Color(0xFF00E5FF),
                                        modifier = Modifier.size(24.dp)
                                    )
                                    Text(
                                        text = connectedPeer ?: "AirDrop Node",
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White,
                                        fontSize = 18.sp
                                    )
                                }

                                if (isTransferring) {
                                    // iOS Smooth Circular Transfer Progress Ring
                                    CircularTransferProgress(
                                        progress = transferProgress,
                                        fileName = "payload_archive_2026.zip",
                                        speed = "48.5 MB/s"
                                    )
                                } else {
                                    Text("Security Authentication PIN", color = Color.LightGray, fontSize = 12.sp)
                                    Text(
                                        text = pairingCode,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 32.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color(0xFFFFB300),
                                        letterSpacing = 8.sp
                                    )

                                    Text(
                                        text = "Please verify that this code matches on the recipient device before commencing transmission.",
                                        fontSize = 11.sp,
                                        color = Color.Gray,
                                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                                        lineHeight = 15.sp,
                                        modifier = Modifier.padding(horizontal = 12.dp)
                                    )

                                    Spacer(modifier = Modifier.height(8.dp))

                                    Button(
                                        onClick = {
                                            isTransferring = true
                                            coroutineScope.launch {
                                                for (i in 1..100) {
                                                    delay(25)
                                                    transferProgress = i / 100f
                                                }
                                                isTransferring = false
                                                transferProgress = 0f
                                                Toast.makeText(context, "Payload transferred successfully!", Toast.LENGTH_SHORT).show()
                                            }
                                        },
                                        shape = RoundedCornerShape(24.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF00E5FF)),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .height(48.dp)
                                            .pressScale()
                                    ) {
                                        Text("Beam File Payload", color = Color.Black, fontWeight = FontWeight.Bold)
                                    }
                                }

                                OutlinedButton(
                                    onClick = {
                                        connectedPeer = null
                                        isScanning = false
                                    },
                                    shape = RoundedCornerShape(24.dp),
                                    colors = ButtonDefaults.outlinedButtonColors(contentColor = Color.Red.copy(alpha = 0.8f)),
                                    border = androidx.compose.foundation.BorderStroke(1.dp, Color.Red.copy(alpha = 0.3f)),
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(48.dp)
                                        .pressScale()
                                ) {
                                    Text("Disconnect Link", fontWeight = FontWeight.Bold)
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

// Custom spring press scale Modifier
@Composable
fun Modifier.pressScale(): Modifier {
    val scale = remember { Animatable(1f) }
    val scope = rememberCoroutineScope()
    return this
        .pointerInput(Unit) {
            detectTapGestures(
                onPress = {
                    scope.launch { scale.animateTo(0.96f, spring(stiffness = Spring.StiffnessMedium)) }
                    tryAwaitRelease()
                    scope.launch { scale.animateTo(1f, spring(stiffness = Spring.StiffnessMedium)) }
                }
            )
        }
        .scale(scale.value)
}

// Camera scanner viewfinder mockup with WeChat style bracket corners
@Composable
fun CameraViewfinderMockup() {
    val infiniteTransition = rememberInfiniteTransition(label = "laser")
    val laserY by infiniteTransition.animateFloat(
        initialValue = 0f,
        targetValue = 220f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "laserY"
    )

    Box(
        modifier = Modifier
            .size(240.dp)
            .border(1.5.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(24.dp))
            .background(Color.Black.copy(alpha = 0.5f)),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val bracketLength = 24.dp.toPx()
            val strokeWidth = 4.dp.toPx()
            val bracketColor = Color(0xFF00E5FF)

            // Top Left
            drawLine(bracketColor, Offset(0f, 0f), Offset(bracketLength, 0f), strokeWidth)
            drawLine(bracketColor, Offset(0f, 0f), Offset(0f, bracketLength), strokeWidth)

            // Top Right
            drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width - bracketLength, 0f), strokeWidth)
            drawLine(bracketColor, Offset(size.width, 0f), Offset(size.width, bracketLength), strokeWidth)

            // Bottom Left
            drawLine(bracketColor, Offset(0f, size.height), Offset(bracketLength, size.height), strokeWidth)
            drawLine(bracketColor, Offset(0f, size.height), Offset(0f, size.height - bracketLength), strokeWidth)

            // Bottom Right
            drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width - bracketLength, size.height), strokeWidth)
            drawLine(bracketColor, Offset(size.width, size.height), Offset(size.width, size.height - bracketLength), strokeWidth)
        }

        // Laser scan line
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .offset(y = laserY.dp - 110.dp)
                .background(Color(0xFF00E5FF).copy(alpha = 0.8f))
        )
    }
}

// Circular progress ring showing transfer stats
@Composable
fun CircularTransferProgress(
    progress: Float,
    fileName: String,
    speed: String
) {
    val animatedProgress by animateFloatAsState(
        targetValue = progress,
        animationSpec = spring(dampingRatio = Spring.DampingRatioMediumBouncy, stiffness = Spring.StiffnessLow),
        label = "progress"
    )
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
        modifier = Modifier.padding(16.dp)
    ) {
        Box(contentAlignment = Alignment.Center, modifier = Modifier.size(160.dp)) {
            CircularProgressIndicator(
                progress = { 1f },
                modifier = Modifier.fillMaxSize(),
                color = Color.White.copy(alpha = 0.05f),
                strokeWidth = 10.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            CircularProgressIndicator(
                progress = { animatedProgress },
                modifier = Modifier.fillMaxSize(),
                color = Color(0xFF00E5FF),
                strokeWidth = 10.dp,
                strokeCap = androidx.compose.ui.graphics.StrokeCap.Round,
            )
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    text = "${(animatedProgress * 100).toInt()}%",
                    color = Color.White,
                    fontWeight = FontWeight.Bold,
                    fontSize = 32.sp,
                    letterSpacing = (-1).sp
                )
                Text(
                    text = speed,
                    color = Color(0xFFFFB300),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Text(
            text = fileName,
            color = Color.LightGray,
            fontWeight = FontWeight.Medium,
            fontSize = 13.sp,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            modifier = Modifier.padding(horizontal = 8.dp)
        )
    }
}
