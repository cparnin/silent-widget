package com.silentwidget

import android.Manifest
import android.app.NotificationManager
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        maybeStartSyncService()
        setContent {
            val context = LocalContext.current
            val colors = if (isSystemInDarkTheme()) dynamicDarkColorScheme(context)
                         else dynamicLightColorScheme(context)
            MaterialTheme(colorScheme = colors) {
                Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
                    HomeScreen()
                }
            }
        }
    }
}

@Composable
private fun HomeScreen() {
    val context = LocalContext.current
    var dndGranted by remember { mutableStateOf(isDndAccessGranted(context)) }
    var notifGranted by remember { mutableStateOf(isNotificationPermissionGranted(context)) }

    // Re-check permissions whenever the screen resumes - i.e. exactly when the
    // user comes back from the Settings pages the buttons below open.
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                dndGranted = isDndAccessGranted(context)
                notifGranted = isNotificationPermissionGranted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val notifLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { notifGranted = it }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Silent Widget", style = MaterialTheme.typography.headlineMedium)
        Text(
            "Vertical three-position widget - tap Ring, Vibrate, or Silent. " +
            "Long-press your home screen → Widgets → Silent Widget to add it.",
            style = MaterialTheme.typography.bodyMedium
        )

        Spacer(Modifier.height(4.dp))

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (dndGranted) "✓ Do Not Disturb access granted"
                    else "Do Not Disturb access NOT granted",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "Required to switch ringer modes from a widget.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!dndGranted) {
                    Button(onClick = { openDndAccessSettings(context) }) { Text("Grant DND access") }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    if (notifGranted) "✓ Notifications allowed"
                    else "Notifications NOT allowed",
                    style = MaterialTheme.typography.titleMedium
                )
                Text(
                    "The widget needs a minimal background notification so it can listen " +
                    "for ringer-mode changes from your volume buttons. It's silent and " +
                    "lives in the “Silent” section of your notification shade.",
                    style = MaterialTheme.typography.bodySmall
                )
                if (!notifGranted && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                    Button(onClick = { notifLauncher.launch(Manifest.permission.POST_NOTIFICATIONS) }) {
                        Text("Allow notifications")
                    }
                }
            }
        }

        Card {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text("How to add the widget", style = MaterialTheme.typography.titleMedium)
                Text("1. Long-press an empty area of your home screen.")
                Text("2. Tap “Widgets”.")
                Text("3. Find “Silent Widget” and drag it to the home screen.")
                Text("4. Tap Ring / Vibrate / Silent to switch modes.")
            }
        }
    }
}

private fun isDndAccessGranted(context: Context): Boolean {
    val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
    return nm.isNotificationPolicyAccessGranted
}

private fun isNotificationPermissionGranted(context: Context): Boolean {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return true
    return ContextCompat.checkSelfPermission(
        context,
        Manifest.permission.POST_NOTIFICATIONS
    ) == PackageManager.PERMISSION_GRANTED
}

private fun openDndAccessSettings(context: Context) {
    val intent = Intent(Settings.ACTION_NOTIFICATION_POLICY_ACCESS_SETTINGS)
    context.startActivity(intent)
}

private fun ComponentActivity.maybeStartSyncService() {
    // Foreground services can only be started from a foreground context on Android 12+.
    // MainActivity is foreground, so this is safe - kicks off real-time volume-button sync.
    val widgetCount = AppWidgetManager.getInstance(this).getAppWidgetIds(
        ComponentName(this, RingerWidgetProvider::class.java)
    ).size
    if (widgetCount > 0) {
        RingerSyncService.start(this)
    }
}
