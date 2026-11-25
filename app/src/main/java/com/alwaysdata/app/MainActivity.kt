package com.alwaysdata.app

import android.Manifest
import android.app.admin.DevicePolicyManager
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
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.runtime.CompositionLocalProvider
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
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.LayoutDirection
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

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { /* granted or not, the service keeps working; user can grant later */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNeededPermissions()
        setContent {
            CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
                MaterialTheme(colorScheme = AppColorScheme) {
                    AlwaysDataScreen()
                }
            }
        }
    }

    private fun requestNeededPermissions() {
        val needed = mutableListOf<String>()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) !=
                PackageManager.PERMISSION_GRANTED
            ) {
                needed.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_PHONE_STATE) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            needed.add(Manifest.permission.READ_PHONE_STATE)
        }
        if (needed.isNotEmpty()) permissionLauncher.launch(needed.toTypedArray())
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AlwaysDataScreen() {
    val context = LocalContext.current

    var protectionEnabled by remember { mutableStateOf(Prefs.isProtectionEnabled(context)) }
    var dataOn by remember { mutableStateOf(MobileDataController.isMobileDataEnabled(context)) }
    var netInfo by remember {
        mutableStateOf(MobileDataController.networkInfo(context))
    }
    var autoCount by remember { mutableStateOf(Prefs.getAutoEnableCount(context)) }
    var lastCheck by remember { mutableStateOf(Prefs.getLastCheck(context)) }
    var logs by remember { mutableStateOf(Prefs.getLog(context)) }
    var showOwnerDialog by remember { mutableStateOf(false) }
    var batteryExempt by remember { mutableStateOf(isBatteryExempt(context)) }
    var isDeviceOwner by remember { mutableStateOf(checkDeviceOwner(context)) }

    var isAdminActive by remember { mutableStateOf(DeviceAdminHelper.isAdminActive(context)) }

    val adminLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) { isAdminActive = DeviceAdminHelper.isAdminActive(context) }

    // Live refresh every 2 seconds (mirrors the service poll).
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
        Prefs.log(
            context,
            if (on) "حفاظت همیشه‌روشن فعال شد" else "حفاظت همیشه‌روشن خاموش شد"
        )
        if (on) {
            if (DeviceOwnerHelper.isManaged(context)) DeviceOwnerHelper.applyHardening(context)
            AlwaysDataService.start(context)
            CheckWorker.enqueue(context)
        } else {
            if (DeviceOwnerHelper.isManaged(context)) DeviceOwnerHelper.removeHardening(context)
            AlwaysDataService.stop(context)
        }
        protectionEnabled = on
        isDeviceOwner = checkDeviceOwner(context)
    }

    Scaffold(
        containerColor = AppColorScheme.background,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(
                            "دیتای همیشه روشن",
                            fontWeight = FontWeight.Bold,
                            fontSize = 20.sp
                        )
                        Text(
                            "اینترنت موبایل را همیشه روشن نگه می‌دارد",
                            fontSize = 12.sp,
                            color = Color(0xFF9FB3D1)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = AppColorScheme.background
                )
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            // ------------------------------------------------ status card
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .clip(CircleShape)
                                .background(if (dataOn) Color(0xFF4ADE80) else Color(0xFFF87171))
                        )
                        Spacer(Modifier.width(10.dp))
                        Text(
                            if (dataOn) "دیتای موبایل روشن است" else "دیتای موبایل خاموش است",
                            fontWeight = FontWeight.Bold,
                            fontSize = 18.sp
                        )
                    }
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                        InfoChip("اپراتور", netInfo.operator)
                        InfoChip("شبکه", netInfo.type)
                        InfoChip("سیگنال", netInfo.signal)
                    }
                    if (protectionEnabled && !dataOn) {
                        Spacer(Modifier.height(12.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                Icons.Filled.Refresh,
                                contentDescription = null,
                                tint = Color(0xFFFBBF24),
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                "در حال تلاش برای روشن کردن خودکار…",
                                color = Color(0xFFFBBF24),
                                fontSize = 13.sp
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------ protection switch
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            "حفاظت همیشه‌روشن",
                            fontWeight = FontWeight.Bold,
                            fontSize = 17.sp
                        )
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "وقتی فعال است، اگر دیتا به هر دلیلی قطع شود (حتی اگر خودت خاموشش کنی) در چند ثانیه خودش دوباره روشن می‌شود و تا وقتی از داخل همین برنامه خاموشش نکنی روشن می‌ماند.",
                            fontSize = 13.sp,
                            color = Color(0xFF9FB3D1)
                        )
                    }
                    Spacer(Modifier.width(12.dp))
                    Switch(
                        checked = protectionEnabled,
                        onCheckedChange = { setProtection(it) },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = Color(0xFF052E16),
                            checkedTrackColor = Color(0xFF4ADE80),
                            uncheckedThumbColor = Color(0xFF9FB3D1),
                            uncheckedTrackColor = Color(0xFF334155)
                        )
                    )
                }
            }

            // ------------------------------------------------ warning (system blocked auto-toggle)
            if (protectionEnabled && !dataOn && autoCount > 3) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A0A))
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.Warning,
                            contentDescription = null,
                            tint = Color(0xFFFBBF24)
                        )
                        Spacer(Modifier.width(10.dp))
                        Column {
                            Text(
                                "سیستم جلوی روشن‌کردن خودکار را گرفته است",
                                fontWeight = FontWeight.Bold,
                                fontSize = 14.sp
                            )
                            Text(
                                "در اندرویدهای جدید، روشن‌کردن برنامه‌ای دیتا محدود شده. برای قفل کامل، راهنمای «قفل کامل (Device Owner)» را دنبال کن یا دکمه زیر را بزن و دیتا را دستی روشن کن.",
                                fontSize = 12.sp,
                                color = Color(0xFFE7D8B0)
                            )
                            Spacer(Modifier.height(8.dp))
                            OutlinedButton(onClick = { openInternetPanel(context) }) {
                                Text("باز کردن پنل اینترنت")
                            }
                        }
                    }
                }
            }

            // ------------------------------------------------ stats
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "روشن‌سازی خودکار",
                    value = "$autoCount بار"
                )
                StatCard(
                    modifier = Modifier.weight(1f),
                    title = "آخرین بررسی",
                    value = if (lastCheck > 0) {
                        SimpleDateFormat("HH:mm:ss", Locale("fa")).format(Date(lastCheck))
                    } else {
                        "—"
                    }
                )
            }

            // ------------------------------------------------ actions
            Text(
                "تنظیمات لازم برای کارکرد همیشه‌روشن",
                fontWeight = FontWeight.Bold,
                fontSize = 15.sp
            )

            ActionRow(
                icon = { Icon(Icons.Filled.BatteryChargingFull, contentDescription = null) },
                title = "معافیت از بهینه‌سازی باتری",
                subtitle = if (batteryExempt) "انجام شده ✓ — سیستم برنامه را نمی‌بندد" else "لازم است تا برنامه در پس‌زمینه کشته نشود",
                buttonText = if (batteryExempt) "انجام شده" else "فعال‌سازی",
                enabled = !batteryExempt,
                onClick = { requestBatteryExemption(context) }
            )

            ActionRow(
                icon = { Icon(Icons.Filled.Notifications, contentDescription = null) },
                title = "اعلان دائمی سرویس",
                subtitle = "اعلان «دیتای همیشه روشن فعال است» باید دیده شود تا سرویس زنده بماند",
                buttonText = "بررسی اعلان‌ها",
                enabled = true,
                onClick = {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                        val intent = Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                            putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                        }
                        context.startActivity(intent)
                    }
                }
            )

            // ------------------------------------------------ Device Admin: فقط روشن کردن
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(
                    containerColor = if (isAdminActive) Color(0xFF14331F) else AppColorScheme.surface
                )
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(44.dp).clip(CircleShape).background(AppColorScheme.surfaceVariant),
                            contentAlignment = Alignment.Center
                        ) { Icon(Icons.Filled.Security, contentDescription = null) }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("Device Admin — روشن کردن خودکار دیتا", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text(
                                if (isAdminActive) "فعاله ✓ — اگه دیتا خاموش شد خودکار روشن می‌شه (بدون قفل صفحه)"
                                else "با یک تایید، هر وقت دیتا خاموش شد خودکار روشن می‌شه",
                                fontSize = 12.sp, color = Color(0xFF9FB3D1)
                            )
                        }
                        if (!isAdminActive) {
                            Button(
                                onClick = { adminLauncher.launch(DeviceAdminHelper.requestEnableIntent(context)) },
                                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4ADE80), contentColor = Color(0xFF052E16))
                            ) { Text("فعال‌سازی") }
                        } else {
                            Icon(Icons.Filled.CheckCircle, null, tint = Color(0xFF4ADE80), modifier = Modifier.size(22.dp))
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("بدون قفل صفحه — فقط دیتا خاموش شد، برنامه با Device Admin دوباره روشنش می‌کنه. بدون ریست، بدون پاک شدن.", fontSize = 12.sp, color = Color(0xFF9FB3D1))
                }
            }

            ActionRow(
                icon = { Icon(Icons.Filled.Security, contentDescription = null) },
                title = "قفل سوییچ (Profile/Device Owner) — اختیاری و قوی‌تر",
                subtitle = if (isDeviceOwner) "فعاله ✓ — سوییچ تنظیمات خاکستری شده" else "بدون پاک شدن هم می‌شه: با ADB سوییچ رو قفل می‌کنه (اختیاری)",
                buttonText = if (isDeviceOwner) "مدیریت" else "راهنما",
                enabled = true,
                onClick = { showOwnerDialog = true }
            )

            if (isDeviceOwner) {
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(16.dp),
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF14331F))
                ) {
                    Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = Color(0xFF4ADE80), modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("حالت سخت فعاله — برای لغو، اول سوییچ بالا رو خاموش کن بعد مدیریت رو بردار", fontSize = 12.sp, color = Color(0xFFBBF7D0))
                    }
                }
            }

            ActionRow(
                icon = { Icon(Icons.Filled.PowerSettingsNew, contentDescription = null) },
                title = "کاشی تنظیمات سریع",
                subtitle = "بعد از افزودن کاشی، وضعیت حفاظت را از نوار بالا می‌بینی و با یک لمس تغییرش می‌دهی",
                buttonText = "راهنما",
                enabled = true,
                onClick = { openQuickSettingsEdit(context) }
            )

            // ------------------------------------------------ hide app button
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)
            ) {
                Column(Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(Modifier.size(44.dp).clip(CircleShape).background(AppColorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                            Icon(Icons.Filled.Close, contentDescription = null)
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text("مخفی کردن برنامه", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                            Text("بعد از تست، آیکون از صفحه اصلی میره کنار — کسی نمی‌بینه نصب شده", fontSize = 12.sp, color = Color(0xFF9FB3D1))
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("سرویس همچنان کار می‌کنه و دیتا روشن می‌مونه. برای برگردوندن: تنظیمات → برنامه‌ها → دیتای همیشه روشن → باز کردن. یا با ADB: adb shell cmd package set-component-enabled com.alwaysdata.app/com.alwaysdata.app.MainActivity enabled", fontSize = 11.sp, color = Color(0xFF9FB3D1))
                    Spacer(Modifier.height(10.dp))
                    var showHideConfirm by remember { mutableStateOf(false) }
                    Button(
                        onClick = { showHideConfirm = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF334155), contentColor = Color.White)
                    ) { Text("مخفی کردن الان") }
                    if (showHideConfirm) {
                        AlertDialog(
                            onDismissRequest = { showHideConfirm = false },
                            containerColor = AppColorScheme.surface,
                            title = { Text("مخفی شود؟") },
                            text = { Text("آیکون از لانچر حذف می‌شه ولی برنامه پاک نمی‌شه و در پس‌زمینه دیتا رو روشن نگه می‌داره. مطمئنی؟\n\nبرگردوندن: تنظیمات → برنامه‌ها → دیتای همیشه روشن → باز کردن", fontSize = 13.sp) },
                            confirmButton = {
                                TextButton(onClick = {
                                    showHideConfirm = false
                                    HideHelper.hide(context)
                                }) { Text("بله، مخفی کن") }
                            },
                            dismissButton = { TextButton(onClick = { showHideConfirm = false }) { Text("انصراف") } }
                        )
                    }
                }
            }

            // ------------------------------------------------ DEBUG
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF1A2336))
            ) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text("🛠 دیباگ — ببینیم چرا روشن نمی‌شه", fontWeight = FontWeight.Bold, fontSize = 14.sp, color = Color(0xFFFBBF24))
                        Spacer(Modifier.weight(1f))
                        IconButton(onClick = {
                            val txt = MobileDataController.debugStatus(context)
                            val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            cb.setPrimaryClip(ClipData.newPlainText("debug", txt))
                            android.widget.Toast.makeText(context, "کپی شد", android.widget.Toast.LENGTH_SHORT).show()
                        }) { Icon(Icons.Filled.ContentCopy, null, tint = Color(0xFF9FB3D1), modifier = Modifier.size(18.dp)) }
                    }
                    Spacer(Modifier.height(8.dp))
                    // هشدارهای هوشمند بر اساس گوشی خودت
                    val dbgLower = MobileDataController.debugStatus(context)
                    if (dbgLower.contains("Device/Profile Owner = معمولی")) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A2A0A)), shape = RoundedCornerShape(10.dp)) {
                            Column(Modifier.padding(10.dp)) {
                                Text("⚠️ روی Xiaomi اندروید 16، Device Admin به تنهایی دیتا رو روشن نمی‌کنه!", fontSize = 12.sp, color = Color(0xFFFBBF24), fontWeight = FontWeight.Bold)
                                Text("گوشیت Profile Owner می‌خواد — بدون پاک شدن حافظه.", fontSize = 11.sp, color = Color(0xFFE7D8B0))
                                Spacer(Modifier.height(6.dp))
                                Button(onClick = { showOwnerDialog = true }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color(0xFF0B1220))) { Text("راهنمای بدون پاک شدن", fontSize = 12.sp) }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (!batteryExempt) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF3A1A1A)), shape = RoundedCornerShape(10.dp)) {
                            Row(Modifier.padding(10.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.Warning, null, tint = Color(0xFFF87171), modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Column(Modifier.weight(1f)) {
                                    Text("باتری: سیستم برنامه رو می‌بنده!", fontSize = 12.sp, color = Color(0xFFF87171), fontWeight = FontWeight.Bold)
                                    Text("برای زنده موندن سرویس اینو بزن", fontSize = 11.sp, color = Color(0xFFCBD5E1))
                                }
                                Button(onClick = { requestBatteryExemption(context) }, colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFF87171))) { Text("فعال") }
                            }
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    if (dbgLower.contains("TelephonyManager.setDataEnabled: ✓ موفق") && dbgLower.contains("isDataEnabled = false")) {
                        Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E1F)), shape = RoundedCornerShape(10.dp)) {
                            Text("نکته: گوشی می‌گه \"موفق\" ولی واقعا روشن نکرد — این باگ HyperOS شیائومی روی اندروید 16 هست. بدون Profile Owner درست نمی‌شه.", fontSize = 11.sp, color = Color(0xFF4ADE80), modifier = Modifier.padding(10.dp))
                        }
                        Spacer(Modifier.height(8.dp))
                    }
                    Text(MobileDataController.debugStatus(context), fontSize = 11.sp, color = Color(0xFFCBD5E1), lineHeight = 15.sp)
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = {
                                val before = MobileDataController.isMobileDataEnabled(context)
                                val ok = MobileDataController.setMobileDataEnabled(context, true)
                                android.os.Handler(android.os.Looper.getMainLooper()).postDelayed({
                                    val after = MobileDataController.isMobileDataEnabled(context)
                                    val msg = if (after) "✓ واقعا روشن شد!" else "✗ هنوز خاموشه — باید Profile Owner کنی"
                                    android.widget.Toast.makeText(context, "قبل=$before ادعا=${if (ok) "OK" else "FAIL"} بعد=$after → $msg", android.widget.Toast.LENGTH_LONG).show()
                                    logs = Prefs.getLog(context)
                                }, 1300)
                            },
                            modifier = Modifier.weight(1f),
                            colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFFBBF24), contentColor = Color(0xFF0B1220))
                        ) { Text("تست روشن کردن الان", fontSize = 12.sp) }
                        OutlinedButton(onClick = { openInternetPanel(context) }, modifier = Modifier.weight(1f)) {
                            Text("باز کردن تنظیمات اینترنت", fontSize = 11.sp)
                        }
                    }
                    Text("حالت فعلی: Device Admin به تنهایی روی Xiaomi اندروید 16 کافی نیست — باید Profile Owner (بدون پاک شدن) فعال کنی.", fontSize = 11.sp, color = Color(0xFF9FB3D1), modifier = Modifier.padding(top=8.dp))
                }
            }

            // ------------------------------------------------ log
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)
            ) {
                Column(modifier = Modifier.padding(20.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.History,
                            contentDescription = null,
                            tint = Color(0xFF7DD3FC),
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text("رویدادهای اخیر", fontWeight = FontWeight.Bold, fontSize = 15.sp)
                    }
                    Spacer(Modifier.height(10.dp))
                    if (logs.isEmpty()) {
                        Text(
                            "هنوز رویدادی ثبت نشده. حفاظت را فعال کن تا اینجا ببینی چه زمانی دیتا خودکار روشن شده.",
                            fontSize = 13.sp,
                            color = Color(0xFF9FB3D1)
                        )
                    } else {
                        logs.take(12).forEach { line ->
                            Text(
                                line,
                                fontSize = 12.sp,
                                color = Color(0xFFCBD5E1),
                                modifier = Modifier.padding(vertical = 2.dp)
                            )
                        }
                    }
                }
            }

            // ------------------------------------------------ honest note
            Row(
                modifier = Modifier.padding(horizontal = 4.dp),
                verticalAlignment = Alignment.Top
            ) {
                Icon(
                    Icons.Filled.Info,
                    contentDescription = null,
                    tint = Color(0xFF7DD3FC),
                    modifier = Modifier.size(18.dp)
                )
                Spacer(Modifier.width(8.dp))
                Text(
                    "نکته صادقانه: اندروید به هیچ برنامه‌ای اجازه نمی‌دهد ۱۰۰٪ جلوی خاموش‌کردن دیتا را بگیرد (مثلاً حالت هواپیما، برداشتن سیم‌کارت یا ریست کارخانه همیشه ممکن است). این برنامه هر تلاشی برای خاموش‌کردن را در چند ثانیه خنثی می‌کند؛ با «قفل کامل» و معافیت باتری، عملاً همیشه روشن می‌ماند.",
                    fontSize = 12.sp,
                    color = Color(0xFF9FB3D1)
                )
            }
        }
    }

    if (showOwnerDialog) {
        DeviceOwnerDialog(onDismiss = { showOwnerDialog = false })
    }
}

@Composable
private fun InfoChip(label: String, value: String) {
    Column {
        Text(label, fontSize = 11.sp, color = Color(0xFF9FB3D1))
        Text(value, fontSize = 14.sp, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
private fun StatCard(modifier: Modifier = Modifier, title: String, value: String) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Text(title, fontSize = 12.sp, color = Color(0xFF9FB3D1))
            Spacer(Modifier.height(4.dp))
            Text(value, fontSize = 16.sp, fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun ActionRow(
    icon: @Composable () -> Unit,
    title: String,
    subtitle: String,
    buttonText: String,
    enabled: Boolean,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = AppColorScheme.surface)
    ) {
        Row(
            modifier = Modifier.padding(16.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(CircleShape)
                    .background(AppColorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                icon()
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(title, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Text(subtitle, fontSize = 12.sp, color = Color(0xFF9FB3D1))
            }
            Spacer(Modifier.width(8.dp))
            if (enabled) {
                Button(
                    onClick = onClick,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = Color(0xFF4ADE80),
                        contentColor = Color(0xFF052E16)
                    )
                ) { Text(buttonText) }
            } else {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        Icons.Filled.CheckCircle,
                        contentDescription = null,
                        tint = Color(0xFF4ADE80),
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(buttonText, fontSize = 12.sp, color = Color(0xFF4ADE80))
                }
            }
        }
    }
}

@Composable
private fun DeviceOwnerDialog(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val isOwner = DeviceOwnerHelper.isManaged(context)
    val mode = DeviceOwnerHelper.modeName(context)
    val commandProfile = "adb shell dpm set-profile-owner com.alwaysdata.app/.MyDeviceAdminReceiver"
    val commandDevice = "adb shell dpm set-device-owner com.alwaysdata.app/.MyDeviceAdminReceiver"
    val removeCommand = "adb shell dpm remove-active-admin com.alwaysdata.app/.MyDeviceAdminReceiver"
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AppColorScheme.surface,
        title = { Text(if (isOwner) "مدیریت قفل ($mode فعاله ✓)" else "قفل سوییچ دیتا — بدون پاک شدن حافظه", fontWeight = FontWeight.Bold) },
        text = {
            Column {
                if (isOwner) {
                    Text(
                        "✓ $mode فعال است. الان این قفل‌ها روشن هستند:\n" +
                            "• سوییچ دیتای موبایل در تنظیمات قفل (خاکستری) شده\n" +
                            "• ریست شبکه، حالت امن، حذف برنامه و ساخت کاربر جدید بسته شده\n" +
                            "• بدون برداشتن مدیریت، حذف نمی‌شه\n\n" +
                            "برای لغو: اول سوییچ «حفاظت همیشه‌روشن» رو خاموش کن (خودکار قفل‌ها برداشته می‌شن)، بعد اگر خواستی با این دستور مدیریت رو بردار:",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(removeCommand, fontSize = 10.sp, color = Color(0xFFF87171), modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val clipboard = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            clipboard.setPrimaryClip(ClipData.newPlainText("adb", removeCommand))
                        }) { Icon(Icons.Filled.ContentCopy, contentDescription = "کپی") }
                    }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton(onClick = {
                        try { DeviceOwnerHelper.applyHardening(context) } catch (_: Exception) {}
                    }, modifier = Modifier.fillMaxWidth()) { Text("اعمال مجدد همه قفل‌ها") }
                } else {
                    Text(
                        "بدون مدیریت، اندروید اجازه قفل کردن سوییچ دیتا رو نمی‌ده — فقط هر ۲ ثانیه دوباره روشن می‌کنیم و راحت دور زده می‌شه.\n\n" +
                            "خبر خوب: لازم نیست حافظه رو پاک کنی! همون قفل خاکستری سوییچ با Profile Owner هم می‌شه و نیازی به Factory Reset نداره:",
                        fontSize = 13.sp
                    )
                    Spacer(Modifier.height(8.dp))
                    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFF1B2E1F)), shape = RoundedCornerShape(12.dp)) {
                        Column(Modifier.padding(12.dp)) {
                            Text("⭐ پیشنهاد من — بدون پاک شدن (Profile Owner)", fontWeight = FontWeight.Bold, fontSize = 13.sp, color = Color(0xFF4ADE80))
                            Text("چیزی پاک نمی‌شه، فقط با یک کابل USB:", fontSize = 12.sp, color = Color(0xFF9FB3D1))
                            Spacer(Modifier.height(6.dp))
                            Text("۱) اشکال‌زدایی USB رو روشن کن\n۲) گوشی رو به کامپیوتر وصل کن\n۳) این دستور رو اجرا کن:", fontSize = 12.sp)
                            Spacer(Modifier.height(6.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(commandProfile, fontSize = 11.sp, color = Color(0xFF7DD3FC), modifier = Modifier.weight(1f))
                                IconButton(onClick = {
                                    val c = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                                    c.setPrimaryClip(ClipData.newPlainText("adb", commandProfile))
                                }) { Icon(Icons.Filled.ContentCopy, contentDescription = "کپی") }
                            }
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Text("اگر این دستور خطای «already has profile owner» داد یعنی قبلا یه مدیریت دیگه فعاله — باید اول اونو برداری.\n\nگزینه دوم (قوی‌تر ولی با پاک شدن):", fontSize = 12.sp, color = Color(0xFF9FB3D1))
                    Spacer(Modifier.height(6.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(commandDevice, fontSize = 10.sp, color = Color(0xFF7DD3FC), modifier = Modifier.weight(1f))
                        IconButton(onClick = {
                            val c = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                            c.setPrimaryClip(ClipData.newPlainText("adb", commandDevice))
                        }) { Icon(Icons.Filled.ContentCopy, contentDescription = "کپی") }
                    }
                    Text("فقط وقتی نیازه که بخوای جلوی Factory Reset از داخل تنظیمات هم گرفته بشه. این یکی حتما Factory Reset می‌خواد.", fontSize = 11.sp, color = Color(0xFFFBBF24))
                    Spacer(Modifier.height(6.dp))
                    Text(
                        "بعد از هر دو، USB Debugging رو خاموش کن. ریست سخت‌افزاری با دکمه‌ها رو هیچ روشی نمی‌بنده — اندروید عمدا باز گذاشته — ولی با قفل FRP بعد از ریست بدون رمز گوگل بالا نمیاد.",
                        fontSize = 11.sp, color = Color(0xFF9FB3D1)
                    )
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Close, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(4.dp))
                    Text("بستن")
                }
            }
        }
    )
}

// ------------------------------------------------------------------ helpers

private fun isBatteryExempt(context: Context): Boolean {
    return try {
        val pm = context.getSystemService(PowerManager::class.java)
        pm.isIgnoringBatteryOptimizations(context.packageName)
    } catch (_: Exception) {
        false
    }
}

private fun checkDeviceOwner(context: Context): Boolean {
    return try { DeviceOwnerHelper.isManaged(context) } catch (_: Exception) { false }
}

private fun requestBatteryExemption(context: Context) {
    try {
        val intent = Intent(
            Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
            Uri.parse("package:${context.packageName}")
        )
        context.startActivity(intent)
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

private fun openInternetPanel(context: Context) {
    try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
        } else {
            context.startActivity(Intent(Settings.ACTION_NETWORK_OPERATOR_SETTINGS))
        }
    } catch (_: Exception) {
        try {
            context.startActivity(Intent(Settings.ACTION_DATA_USAGE_SETTINGS))
        } catch (_: Exception) {
        }
    }
}

private fun openQuickSettingsEdit(context: Context) {
    // There is no public Settings.ACTION_QUICK_SETTINGS constant; the
    // Quick Settings edit screen has no stable public Intent. Fall back
    // to the main Settings screen and the user can long-press the tile
    // area to edit.
    try {
        context.startActivity(Intent(Settings.ACTION_SETTINGS))
    } catch (_: Exception) {
    }
}
