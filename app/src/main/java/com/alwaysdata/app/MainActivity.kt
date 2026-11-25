package com.alwaysdata.app

import android.Manifest
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val AppColorScheme = darkColorScheme(
    primary = Color(0xFF4ADE80),
    onPrimary = Color(0xFF052E16),
    secondary = Color(0xFF7DD3FC),
    background = Color(0xFF0B1220),
    surface = Color(0xFF131C2E),
    surfaceVariant = Color(0xFF1B2740),
    onBackground = Color(0xFFE8EEF7),
    onSurface = Color(0xFFE8EEF7)
)

class MainActivity : ComponentActivity() {
    private val permissionLauncher = registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) {}
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()
        setContent { MaterialTheme(colorScheme = AppColorScheme) { AlwaysDataScreen() } }
    }
    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.POST_NOTIFICATIONS)
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) != PackageManager.PERMISSION_GRANTED) needed.add(Manifest.permission.READ_PHONE_STATE)
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlwaysDataScreen() {
    val context = LocalContext.current
    var protectionEnabled by remember { mutableStateOf(Prefs.isProtectionEnabled(context)) }
    var dataOn by remember { mutableStateOf(MobileDataController.isMobileDataEnabled(context)) }
    var netInfo by remember { mutableStateOf(MobileDataController.networkInfo(context)) }
    var autoCount by remember { mutableStateOf(Prefs.getAutoEnableCount(context)) }
    var lastCheck by remember { mutableStateOf(Prefs.getLastCheck(context)) }
    var logs by remember { mutableStateOf(Prefs.getLog(context)) }
    var showOwnerDialog by remember { mutableStateOf(false) }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    var isDeviceOwner by remember { mutableStateOf(checkDeviceOwner(context)) }
    var isAdminActive by remember { mutableStateOf(DeviceAdminHelper.isAdminActive(context)) }
    val adminLauncher = androidx.activity.compose.rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { isAdminActive = DeviceAdminHelper.isAdminActive(context) }

    LaunchedEffect(Unit) {
        while (true) {
            protectionEnabled = Prefs.isProtectionEnabled(context)
            dataOn = MobileDataController.isMobileDataEnabled(context)
            netInfo = MobileDataController.networkInfo(context)
            autoCount = Prefs.getAutoEnableCount(context)
            lastCheck = Prefs.getLastCheck(context)
            logs = Prefs.getLog(context)
            batteryExempt = isBatteryExempt(context)
            isDeviceOwner = checkDeviceOwner(context)
            isAdminActive = DeviceAdminHelper.isAdminActive(context)
            delay(2000)
        }
    }
    fun setProtection(on: Boolean) {
        Prefs.setProtectionEnabled(context, on)
        Prefs.log(context, if (on) "protection on" else "protection off")
        if (on) {
            if (DeviceOwnerHelper.isManaged(context)) DeviceOwnerHelper.applyHardening(context)
            AlwaysDataService.start(context); CheckWorker.enqueue(context)
        } else {
            if (DeviceOwnerHelper.isManaged(context)) DeviceOwnerHelper.removeHardening(context)
            AlwaysDataService.stop(context)
        }
        protectionEnabled = on; isDeviceOwner = checkDeviceOwner(context)
    }

    Scaffold(
        containerColor = AppColorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("data", fontWeight = FontWeight.Bold, fontSize = 20.sp) },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = AppColorScheme.background)
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // status
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(12.dp).clip(CircleShape).background(if (dataOn) Color(0xFF4ADE80) else Color(0xFFF87171)))
                        Spacer(Modifier.width(8.dp))
                        Text(if (dataOn) "ON" else "OFF", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                        Spacer(Modifier.weight(1f))
                        Text(netInfo.operator + " " + netInfo.type, fontSize = 12.sp, color = Color(0xFF9FB3D1))
                    }
                }
            }
            // protection switch
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)) {
                Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Column(Modifier.weight(1f)) {
                        Text("keep data on", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        Text("auto re-enable if turned off", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                    }
                    Switch(
                        checked = protectionEnabled, onCheckedChange = { setProtection(it) },
                        colors = SwitchDefaults.colors(checkedTrackColor = Color(0xFF4ADE80), checkedThumbColor = Color(0xFF052E16))
                    )
                }
            }
            // battery
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.BatteryChargingFull, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("battery", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(if (batteryExempt) "ok" else "tap to allow background", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                    }
                    if (!batteryExempt) Button(onClick = { requestBatteryExemption(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80), contentColor = Color(0xFF052E16))) { Text("allow", fontSize = 12.sp) }
                    else Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4ADE80))
                }
            }
            // device admin
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = if (isAdminActive) Color(0xFF14331F) else AppColorScheme.surface)) {
                Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Security, null, modifier = Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Column(Modifier.weight(1f)) {
                        Text("device admin", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        Text(if (isAdminActive) "active" else "required for auto-on", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                    }
                    if (!isAdminActive) Button(onClick = { adminLauncher.launch(DeviceAdminHelper.requestEnableIntent(context)) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80), contentColor = Color(0xFF052E16))) { Text("enable", fontSize = 12.sp) }
                    else Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4ADE80))
                }
            }
            // stats
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("auto-ons", fontSize = 11.sp, color = Color(0xFF9FB3D1)); Text("$autoCount", fontWeight = FontWeight.Bold)
                    }
                }
                Card(Modifier.weight(1f), shape = RoundedCornerShape(12.dp), colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)) {
                    Column(Modifier.padding(12.dp)) {
                        Text("last check", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                        Text(if (lastCheck>0) SimpleDateFormat("HH:mm:ss", Locale.US).format(Date(lastCheck)) else "—", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                    }
                }
            }
            // hide
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.VisibilityOff, null, modifier = Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("hide", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Spacer(Modifier.weight(1f))
                        var confirm by remember { mutableStateOf(false) }
                        Button(onClick = { confirm = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155))) { Text("hide now", fontSize = 12.sp) }
                        if (confirm) {
                            AlertDialog(
                                onDismissRequest = { confirm = false },
                                containerColor = AppColorScheme.surface,
                                title = { Text("hide?") },
                                text = { Text("Icon will be hidden. To open again: Settings > Apps > data > Open", fontSize = 12.sp) },
                                confirmButton = { TextButton(onClick = { confirm=false; HideHelper.hide(context) }) { Text("yes") } },
                                dismissButton = { TextButton(onClick = { confirm=false }) { Text("no") } }
                            )
                        }
                    }
                }
            }
            // debug
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2336))) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("debug", fontWeight = FontWeight.Bold, fontSize = 12.sp, color = Color(0xFFFBBF24))
                        Spacer(Modifier.weight(1f))
                        OutlinedButton(onClick = {
                            val txt = MobileDataController.debugStatus(context)
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("debug", txt))
                            android.widget.Toast.makeText(context, "copied", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Text("copy", fontSize = 11.sp) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(MobileDataController.debugStatus(context), fontSize = 10.sp, color = Color(0xFFCBD5E1), lineHeight = 13.sp)
                    Spacer(Modifier.height(8.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Button(onClick = {
                            val before = MobileDataController.isMobileDataEnabled(context)
                            val ok = MobileDataController.setMobileDataEnabled(context, true)
                            android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                val after = MobileDataController.isMobileDataEnabled(context)
                                android.widget.Toast.makeText(context, "before=$before ok=$ok after=$after", android.widget.Toast.LENGTH_LONG).show()
                                logs = Prefs.getLog(context)
                            }, 1200)
                        }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color.Black)) { Text("test on", fontSize = 11.sp) }
                        OutlinedButton(onClick = { openInternetPanel(context) }) { Text("settings", fontSize = 11.sp) }
                        OutlinedButton(onClick = { showOwnerDialog = true }) { Text("owner", fontSize = 11.sp) }
                    }
                }
            }
            // logs
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)) {
                Column(Modifier.padding(12.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.History, null, modifier = Modifier.size(16.dp), tint = Color(0xFF7DD3FC))
                        Spacer(Modifier.width(6.dp)); Text("logs", fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    }
                    Spacer(Modifier.height(6.dp))
                    if (logs.isEmpty()) Text("empty", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                    else logs.take(10).forEach { Text(it, fontSize = 10.sp, color = Color(0xFFCBD5E1)) }
                }
            }
        }
    }
    if (showOwnerDialog) DeviceOwnerDialog(onDismiss = { showOwnerDialog = false })
}

@Composable
private fun DeviceOwnerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isOwner = DeviceOwnerHelper.isManaged(context)
    val mode = DeviceOwnerHelper.modeName(context)
    val cmdProfile = "adb shell dpm set-profile-owner com.alwaysdata.app/.MyDeviceAdminReceiver"
    val cmdDevice = "adb shell dpm set-device-owner com.alwaysdata.app/.MyDeviceAdminReceiver"
    val grant = "adb shell pm grant com.alwaysdata.app android.permission.WRITE_SECURE_SETTINGS"
    AlertDialog(
        onDismissRequest = onDismiss, containerColor = Color(0xFF131C2E),
        title = { Text(if (isOwner) "managed: $mode" else "owner setup", fontSize = 14.sp) },
        text = {
            Column {
                if (isOwner) {
                    Text("Active: $mode. Disable protection first to remove restrictions.", fontSize = 12.sp)
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = { try { DeviceOwnerHelper.applyHardening(context) } catch (_: Exception) {} }, modifier = Modifier.fillMaxWidth()) { Text("re-apply") }
                } else {
                    Text("For full lock:", fontSize = 12.sp, fontWeight = FontWeight.Bold); Spacer(Modifier.height(4.dp))
                    Text("No wipe (Xiaomi blocked — use grant below instead):", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cmdProfile, fontSize = 10.sp, color = Color(0xFF7DD3FC), modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { val c = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; c.setPrimaryClip(ClipData.newPlainText("a", cmdProfile)) }) { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Grant (works on Xiaomi, no wipe):", fontSize = 11.sp, color = Color(0xFF4ADE80), fontWeight = FontWeight.Bold)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(grant, fontSize = 10.sp, color = Color(0xFF4ADE80), modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { val c = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; c.setPrimaryClip(ClipData.newPlainText("a", grant)) }) { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp)) }
                    }
                    Spacer(Modifier.height(6.dp))
                    Text("Device Owner (needs factory reset):", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(cmdDevice, fontSize = 10.sp, color = Color(0xFF7DD3FC), modifier = Modifier.weight(1f))
                        androidx.compose.material3.IconButton(onClick = { val c = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager; c.setPrimaryClip(ClipData.newPlainText("a", cmdDevice)) }) { Icon(Icons.Filled.ContentCopy, null, modifier = Modifier.size(16.dp)) }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Icon(Icons.Filled.Close, null, modifier = Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("close") } }
    )
}

private fun isBatteryExempt(context: Context): Boolean = try { context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName) } catch (_: Exception) { false }
private fun checkDeviceOwner(context: Context): Boolean = try { DeviceOwnerHelper.isManaged(context) } catch (_: Exception) { false }
private fun requestBatteryExemption(context: Context) {
    try { context.startActivity(Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, Uri.parse("package:${context.packageName}"))) } catch (_: Exception) { try { context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)) } catch (_: Exception) {} }
}
private fun openInternetPanel(context: Context) {
    try { if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY)) else context.startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS)) } catch (_: Exception) { try { context.startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS)) } catch (_: Exception) {} }
}
