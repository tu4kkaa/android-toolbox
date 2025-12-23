// MainActivity.kt
package com.example.toolbox

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts.RequestMultiplePermissions
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import com.example.toolbox.screens.FlashlightScreen
import com.example.toolbox.screens.LevelScreen
import com.example.toolbox.screens.SoundMeterScreen
import com.example.toolbox.ui.theme.ToolboxTheme

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            ToolboxTheme {
                ToolboxApp()
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ToolboxApp() {
    var current by rememberSaveable { mutableStateOf(AppDestinations.LEVEL) }
    val context = LocalContext.current

    // Request permissions at app start if not granted
    val permissionsLauncher = rememberLauncherForActivityResult(
        contract = RequestMultiplePermissions(),
        onResult = { /* no-op: screens also check permissions */ }
    )

    LaunchedEffect(Unit) {
        val needed = mutableListOf<String>()
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.RECORD_AUDIO)
        }
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA)
            != PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.CAMERA)
        }
        if (needed.isNotEmpty()) {
            permissionsLauncher.launch(needed.toTypedArray())
        }
    }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            NavigationBar {
                AppDestinations.entries.forEach { dest ->
                    NavigationBarItem(
                        icon = { Icon(dest.icon, contentDescription = dest.label) },
                        label = { Text(dest.label) },
                        selected = dest == current,
                        onClick = { current = dest }
                    )
                }
            }
        }
    ) { innerPadding ->
        when (current) {
            AppDestinations.LEVEL -> LevelScreen(Modifier.padding(innerPadding))
            AppDestinations.SOUNDMETER -> SoundMeterScreen(Modifier.padding(innerPadding))
            AppDestinations.FLASHLIGHT -> FlashlightScreen(Modifier.padding(innerPadding))
        }
    }
}

enum class AppDestinations(val label: String, val icon: androidx.compose.ui.graphics.vector.ImageVector) {
    LEVEL("Level", Icons.Default.Straighten),
    SOUNDMETER("Sound", Icons.Default.Mic),
    FLASHLIGHT("Flashlight", Icons.Default.FlashlightOn)
}
