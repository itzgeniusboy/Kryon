package com.kryon.filemanager.features.settings

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.kryon.filemanager.features.network.pressScale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsView(
    onBack: () -> Unit = {},
    showHiddenFiles: Boolean,
    onShowHiddenFilesChanged: (Boolean) -> Unit,
    recycleBinEnabled: Boolean,
    onRecycleBinChanged: (Boolean) -> Unit
) {
    val context = LocalContext.current
    var selectedAccentColor by remember { mutableStateOf(Color(0xFF3BA7FF)) }
    var selectedTheme by remember { mutableStateOf("Deep Obsidian") }
    var animationsEnabled by remember { mutableStateOf(true) }
    var securityLockEnabled by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }

    val accentColors = listOf(
        Color(0xFF3BA7FF), // Electric Blue
        Color(0xFF00E5FF), // Neon Cyan
        Color(0xFF8A7CFF), // Soft Violet
        Color(0xFFE91E63), // Pink Glo
        Color(0xFF4CAF50)  // Mint Green
    )

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Settings,
                            contentDescription = null,
                            tint = selectedAccentColor,
                            modifier = Modifier.padding(end = 8.dp)
                        )
                        Text(
                            "Kryon Settings",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 20.sp
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, contentDescription = "Back", tint = Color.White)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Color(0xFF090B10))
            )
        },
        containerColor = Color(0xFF090B10)
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .background(Color(0xFF090B10))
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // --- LARGE PROFILE CARD (Liquid Glassmorphism) ---
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(
                        Brush.linearGradient(
                            colors = listOf(
                                Color(0xFF121722),
                                Color(0xFF1E2638)
                            )
                        ),
                        RoundedCornerShape(28.dp)
                    )
                    .border(1.dp, Color.White.copy(alpha = 0.08f), RoundedCornerShape(28.dp))
                    .padding(24.dp)
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    // Profile Avatar with dynamic glow
                    Box(
                        modifier = Modifier
                            .size(72.dp)
                            .background(
                                Brush.radialGradient(
                                    colors = listOf(
                                        selectedAccentColor.copy(alpha = 0.4f),
                                        Color.Transparent
                                    )
                                ),
                                CircleShape
                            )
                            .border(2.dp, selectedAccentColor, CircleShape)
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .background(Color(0xFF090B10), CircleShape),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = selectedAccentColor,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Kryon Developer",
                            fontWeight = FontWeight.ExtraBold,
                            fontSize = 20.sp,
                            color = Color.White
                        )
                        Text(
                            text = "Premium Edition Active",
                            color = Color(0xFFAEB7C6),
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Icon(
                        imageVector = Icons.Default.WorkspacePremium,
                        contentDescription = "Premium Mode",
                        tint = Color(0xFFFFB300),
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            // --- APPEARANCE SECTION ---
            Text(
                "APPEARANCE & THEME",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAEB7C6),
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Theme row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Palette, contentDescription = null, tint = selectedAccentColor)
                            Text("Color Theme", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .background(Color.White.copy(alpha = 0.04f), RoundedCornerShape(12.dp))
                                .clickable {
                                    selectedTheme = if (selectedTheme == "Deep Obsidian") "Cosmic Purple" else "Deep Obsidian"
                                }
                                .padding(horizontal = 12.dp, vertical = 6.dp)
                        ) {
                            Text(selectedTheme, color = Color(0xFFAEB7C6), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                            Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(16.dp))
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.04f))

                    // Accent Color Row
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Primary Accent Color", color = Color.White, fontSize = 13.sp, fontWeight = FontWeight.Medium)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            accentColors.forEach { color ->
                                val isSelected = color == selectedAccentColor
                                Box(
                                    modifier = Modifier
                                        .size(36.dp)
                                        .clickable { selectedAccentColor = color }
                                        .background(
                                            color.copy(alpha = if (isSelected) 0.3f else 0.1f),
                                            CircleShape
                                        )
                                        .border(2.dp, if (isSelected) color else Color.Transparent, CircleShape),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(18.dp)
                                            .background(color, CircleShape)
                                    )
                                }
                            }
                        }
                    }

                    Divider(color = Color.White.copy(alpha = 0.04f))

                    // Animations toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Animation, contentDescription = null, tint = selectedAccentColor)
                            Column {
                                Text("Dynamic Transitions", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Apple-level 120Hz smooth fluid feel", color = Color(0xFFAEB7C6), fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = animationsEnabled,
                            onCheckedChange = { animationsEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = selectedAccentColor)
                        )
                    }
                }
            }

            // --- FILE PREFERENCES & INTEGRATIONS ---
            Text(
                "FILE & DIRECTORY RULES",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAEB7C6),
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Show Hidden Files
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.Visibility, contentDescription = null, tint = selectedAccentColor)
                            Column {
                                Text("Hidden Files", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Display configuration and dot files", color = Color(0xFFAEB7C6), fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = showHiddenFiles,
                            onCheckedChange = onShowHiddenFilesChanged,
                            colors = SwitchDefaults.colors(checkedThumbColor = selectedAccentColor)
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.04f))

                    // Safe Recycle Bin
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = selectedAccentColor)
                            Column {
                                Text("Recycle Bin Guard", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Cache deletions to protect accidental loss", color = Color(0xFFAEB7C6), fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = recycleBinEnabled,
                            onCheckedChange = onRecycleBinChanged,
                            colors = SwitchDefaults.colors(checkedThumbColor = selectedAccentColor)
                        )
                    }
                }
            }

            // --- PRIVACY & SECURITY ---
            Text(
                "PRIVACY & SECURITY",
                fontSize = 11.sp,
                fontWeight = FontWeight.Bold,
                color = Color(0xFFAEB7C6),
                modifier = Modifier.padding(start = 8.dp, top = 8.dp)
            )

            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF121722)),
                shape = RoundedCornerShape(24.dp),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.05f)),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    // Security Vault Lock toggle
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.LockOpen, contentDescription = null, tint = selectedAccentColor)
                            Column {
                                Text("Vault Biometric / Pin Lock", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                                Text("Protect files in security vault", color = Color(0xFFAEB7C6), fontSize = 10.sp)
                            }
                        }
                        Switch(
                            checked = securityLockEnabled,
                            onCheckedChange = { securityLockEnabled = it },
                            colors = SwitchDefaults.colors(checkedThumbColor = selectedAccentColor)
                        )
                    }

                    Divider(color = Color.White.copy(alpha = 0.04f))

                    // Privacy policy click
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { showPrivacyDialog = true },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                            Icon(Icons.Default.GppGood, contentDescription = null, tint = selectedAccentColor)
                            Text("Privacy Guard & Policy", color = Color.White, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
                        }
                        Icon(Icons.Default.ChevronRight, contentDescription = null, tint = Color.Gray, modifier = Modifier.size(20.dp))
                    }
                }
            }

            // --- BRAND INFO & CREDITS ---
            Card(
                colors = CardDefaults.cardColors(containerColor = Color(0xFF131118)),
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { showAboutDialog = true }
            ) {
                Row(
                    modifier = Modifier.padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = selectedAccentColor,
                        modifier = Modifier.size(32.dp)
                    )
                    Column {
                        Text(
                            "About Kryon Suite",
                            fontWeight = FontWeight.Bold,
                            color = Color.White,
                            fontSize = 14.sp
                        )
                        Text(
                            "Next-Gen Material 3 file workspace. Version 2.0.4 Premium.",
                            color = Color(0xFFAEB7C6),
                            fontSize = 11.sp
                        )
                    }
                }
            }
        }
    }

    // Privacy Dialog
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = { Text("Kryon Security & Privacy") },
            text = {
                Text(
                    "Kryon features complete offline operation with 0 trackers, logs, or external data collection. " +
                    "Your personal files, documents, pictures, and security vault entries are stored directly " +
                    "on-device using hardware AES-256 local keys.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Understood")
                }
            }
        )
    }

    // About Dialog
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = { Text("About Kryon File Manager") },
            text = {
                Text(
                    "Kryon represents the absolute peak of mobile file managers. Developed as a native Android " +
                    "application with Jetpack Compose, it blends extreme iOS visual fluidity and Google Material 3 " +
                    "dynamics. Packed with premium capabilities including an advanced smart-cleaning suite, foreground background audio, " +
                    "SQLite DB explorer, and ADB shell integration.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp
                )
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close")
                }
            }
        )
    }
}
