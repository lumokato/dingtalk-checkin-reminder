@file:OptIn(ExperimentalMaterial3Api::class)

package com.kanon.dingpunchguard

import android.Manifest
import android.app.AlarmManager
import android.content.ClipboardManager
import android.content.ComponentName
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Looper
import android.os.PowerManager
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccessTime
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Done
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.LocationOn
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.OpenInNew
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Divider
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.Typography
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.TextUnit
import androidx.compose.ui.unit.sp
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max

class MainActivity : ComponentActivity() {
    private lateinit var locationManager: LocationManager
    private var uiLocation: Location? = null

    private var selectedTab by mutableIntStateOf(0)
    private var form by mutableStateOf(SettingsForm())
    private var dashboard by mutableStateOf(DashboardState())
    private var parseStatus by mutableStateOf("")
    private var parsingAmap by mutableStateOf(false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannels(this)
        locationManager = getSystemService(LocationManager::class.java)
        Config.migrate(this)
        form = SettingsForm.from(this)
        updateLastKnownLocation()
        refreshDashboard()
        configureSystemBars()

        setContent {
            DingPunchTheme {
                DingPunchApp(
                    selectedTab = selectedTab,
                    onTabSelected = { selectedTab = it },
                    form = form,
                    dashboard = dashboard,
                    parseStatus = parseStatus,
                    parsingAmap = parsingAmap,
                    onFormChange = {
                        form = it
                        refreshDashboard(it)
                    },
                    onParseAmap = { parseClipboardAmapLink() },
                    onSave = { saveSettings(showToast = true) },
                    onRequestPermissions = { requestCorePermissions() },
                    onOpenLocationSettings = { openLocationPermissionSettings() },
                    onOpenNotificationListener = { openNotificationListenerSettings() },
                    onOpenUsageAccessSettings = { openUsageAccessSettings() },
                    onOpenAppNotificationSettings = { openAppNotificationSettings() },
                    onOpenAlertChannelSettings = { openAlertChannelSettings() },
                    onOpenFullScreenIntentSettings = { openFullScreenIntentSettings() },
                    onOpenMiuiPermissionSettings = { openMiuiPermissionSettings() },
                    onRequestExactAlarm = { requestExactAlarmIfNeeded() },
                    onOpenBatterySettings = { openBatterySettings() },
                    onOpenAppSettings = { openAppSettings() },
                    onRefreshLocation = { refreshCurrentLocation() },
                    onStartGuard = { startGuard() },
                    onStopGuard = { stopGuard() },
                    onOpenDingTalk = { requestDingTalkLaunch() },
                    onConfirmCheckIn = {
                        startGuardService(Config.ACTION_CONFIRM_CHECKIN)
                        refreshDashboard()
                    },
                    onConfirmCheckOut = {
                        startGuardService(Config.ACTION_CONFIRM_CHECKOUT)
                        refreshDashboard()
                    }
                )
            }
        }

        handleAutomationIntent(intent)
    }

    override fun onStart() {
        super.onStart()
        AppVisibility.activityStarted()
    }

    override fun onStop() {
        AppVisibility.activityStopped()
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        updateLastKnownLocation()
        refreshDashboard()
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleAutomationIntent(intent)
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        updateLastKnownLocation()
        refreshDashboard()
    }

    private fun configureSystemBars() {
        window.statusBarColor = AppColors.backgroundInt
        window.navigationBarColor = AppColors.surfaceInt
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            window.decorView.systemUiVisibility = android.view.View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR
        }
    }

    private fun handleAutomationIntent(intent: Intent?) {
        if (intent?.action == Config.ACTION_CLEAR_TODAY_STATE) {
            Config.clearTodayState(this)
            NotificationHelper.cancelAlert(this)
            TimeScheduler.scheduleAll(this)
            if (Config.isEnabled(this)) {
                startGuardService(Config.ACTION_REFRESH)
            }
            AppLog.i(this, "today punch state cleared from automation intent")
            Toast.makeText(this, "已清理今天打卡状态", Toast.LENGTH_SHORT).show()
            refreshDashboard()
            return
        }

        val link = amapInputFromIntent(intent)
        AppLog.i(
            this,
            "amap import intent action=${intent?.action} hasText=${!intent?.getStringExtra(Intent.EXTRA_TEXT).isNullOrBlank()} hasData=${!intent?.dataString.isNullOrBlank()} resolved=${link.isNotBlank()}"
        )
        if (link.isBlank()) {
            return
        }
        selectedTab = 1
        form = form.copy(amapLink = link)
        parseAmapLink(link)
    }

    private fun amapInputFromIntent(intent: Intent?): String {
        if (intent == null) {
            return ""
        }
        val candidates = listOfNotNull(
            intent.getStringExtra("amap_link"),
            intent.getStringExtra(Intent.EXTRA_TEXT),
            intent.getStringExtra(Intent.EXTRA_HTML_TEXT),
            intent.getCharSequenceExtra(Intent.EXTRA_PROCESS_TEXT)?.toString(),
            intent.getCharSequenceExtra(Intent.EXTRA_TITLE)?.toString(),
            intent.getStringExtra(Intent.EXTRA_SUBJECT),
            intent.dataString
        )
        return candidates.firstOrNull { it.isNotBlank() }?.trim().orEmpty()
    }

    private fun parseClipboardAmapLink() {
        val clipboard = getSystemService(ClipboardManager::class.java)
        val clip = clipboard?.primaryClip
        val input = if (clip != null && clip.itemCount > 0) {
            (0 until clip.itemCount)
                .asSequence()
                .map { clip.getItemAt(it).coerceToText(this)?.toString().orEmpty() }
                .firstOrNull { it.isNotBlank() }
                .orEmpty()
        } else {
            ""
        }
        if (input.isBlank()) {
            Toast.makeText(this, "剪贴板没有高德分享文本或链接", Toast.LENGTH_SHORT).show()
            return
        }
        form = form.copy(amapLink = input.trim())
        parseAmapLink(input)
    }

    private fun parseAmapLink(inputOverride: String? = null) {
        val input = (inputOverride ?: form.amapLink).trim()
        if (input.isEmpty()) {
            Toast.makeText(this, "没有可解析的高德链接", Toast.LENGTH_SHORT).show()
            return
        }

        parsingAmap = true
        parseStatus = "正在导入高德位置..."
        Toast.makeText(this, "正在导入高德位置", Toast.LENGTH_SHORT).show()
        Thread {
            try {
                val result = AmapLinkParser.parse(input)
                runOnUiThread {
                    form = form.copy(
                    placeName = result.name,
                    lat = String.format(Locale.US, "%.14f", result.lat),
                    lon = String.format(Locale.US, "%.14f", result.lon)
                )
                    parsingAmap = false
                    parseStatus = "导入成功，已填入地点和坐标"
                    updateLastKnownLocation()
                    refreshDashboard()
                    AppLog.i(this, "parse amap link succeeded")
                    Toast.makeText(this, "导入成功，已填入地点和坐标", Toast.LENGTH_LONG).show()
                }
            } catch (e: Exception) {
                AppLog.e(this, "parse amap link failed", e)
                runOnUiThread {
                    parsingAmap = false
                    parseStatus = "导入失败：${e.message}"
                    Toast.makeText(this, parseStatus, Toast.LENGTH_LONG).show()
                }
            }
        }.start()
    }

    private fun saveSettings(showToast: Boolean): Boolean {
        return try {
            val requestedAssumeOpenSuccess = form.assumeOpenSuccess
            val usageStatsGranted = ForegroundAppVerifier.hasUsageStatsAccess(this)
            val assumeOpenSuccess = requestedAssumeOpenSuccess && usageStatsGranted
            Config.saveUserSettings(
                this,
                form.placeName.ifBlank { "公司" },
                form.lat.toDoubleOrNull() ?: 0.0,
                form.lon.toDoubleOrNull() ?: 0.0,
                max(1, form.radius.toIntOrNull() ?: 300),
                form.checkInStart.ifBlank { "08:30" },
                max(0, form.lateMinutes.toIntOrNull() ?: 30),
                form.checkoutTime.ifBlank { "18:00" },
                max(1, form.reminderMinutes.toIntOrNull() ?: 2),
                max(30, form.checkoutGraceMinutes.toIntOrNull() ?: 180),
                form.requireLocationForCheckout,
                assumeOpenSuccess
            )
            form = SettingsForm.from(this).copy(amapLink = form.amapLink)
            TimeScheduler.scheduleAll(this)
            if (Config.isEnabled(this) && GuardService.isWindowActiveNow(this)) {
                AppLog.i(this, "settings saved during active window; starting immediate evaluation")
                startGuardService(Config.ACTION_REFRESH)
            } else {
                AppLog.i(this, "settings saved; enabled=${Config.isEnabled(this)} windowActive=${GuardService.isWindowActiveNow(this)}")
            }
            refreshDashboard()
            if (showToast) {
                val message = if (requestedAssumeOpenSuccess && !usageStatsGranted) {
                    selectedTab = 2
                    "设置已保存；自动记录已关闭，需先授权前台验证"
                } else {
                    "设置已保存"
                }
                Toast.makeText(this, message, Toast.LENGTH_LONG).show()
            }
            true
        } catch (e: Exception) {
            Toast.makeText(this, "设置保存失败：${e.message}", Toast.LENGTH_LONG).show()
            false
        }
    }

    private fun startGuard() {
        if (!saveSettings(showToast = false)) {
            return
        }
        Config.setEnabled(this, true)
        TimeScheduler.scheduleAll(this)
        startGuardService(Config.ACTION_START)
        refreshDashboard()
        Toast.makeText(this, "自动提醒已开启", Toast.LENGTH_SHORT).show()
    }

    private fun stopGuard() {
        Config.setEnabled(this, false)
        TimeScheduler.cancelAll(this)
        startGuardService(Config.ACTION_STOP)
        refreshDashboard()
        Toast.makeText(this, "自动提醒已暂停", Toast.LENGTH_SHORT).show()
    }

    private fun requestCorePermissions() {
        val permissions = mutableListOf<String>()
        if (checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_FINE_LOCATION)
        }
        if (checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            permissions.add(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            permissions.add(Manifest.permission.POST_NOTIFICATIONS)
        }

        when {
            permissions.isNotEmpty() -> requestPermissions(permissions.toTypedArray(), 100)
            !hasBackgroundLocationPermission() -> {
                Toast.makeText(
                    this,
                    "后台自动判断范围需要在系统设置里改成“${backgroundLocationOptionLabel()}”",
                    Toast.LENGTH_LONG
                ).show()
                openLocationPermissionSettings()
            }
        }
        requestExactAlarmIfNeeded()
        refreshDashboard()
    }

    private fun requestExactAlarmIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) {
            return
        }
        val alarmManager = getSystemService(AlarmManager::class.java)
        if (alarmManager.canScheduleExactAlarms()) {
            Toast.makeText(this, "准时提醒权限已可用", Toast.LENGTH_SHORT).show()
            return
        }
        try {
            val intent = Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM)
                .setData(Uri.parse("package:$packageName"))
            startActivity(intent)
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun startGuardService(action: String) {
        val intent = Intent(this, GuardService::class.java).setAction(action)
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            startForegroundService(intent)
        } else {
            startService(intent)
        }
    }

    private fun openBatterySettings() {
        try {
            val powerManager = getSystemService(PowerManager::class.java)
            if (powerManager != null && !powerManager.isIgnoringBatteryOptimizations(packageName)) {
                val intent = Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS)
                    .setData(Uri.parse("package:$packageName"))
                startActivity(intent)
                return
            }
            startActivity(Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS))
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
            .setData(Uri.parse("package:$packageName"))
        startActivity(intent)
    }

    private fun openLocationPermissionSettings() {
        if (
            Build.VERSION.SDK_INT == Build.VERSION_CODES.Q &&
            hasFineLocationPermission() &&
            !hasBackgroundLocationPermission()
        ) {
            requestPermissions(arrayOf(Manifest.permission.ACCESS_BACKGROUND_LOCATION), 101)
            return
        }

        if (openMiuiPermissionEditor()) {
            return
        }
        openAppSettings()
    }

    private fun openMiuiPermissionEditor(): Boolean {
        val explicit = Intent("miui.intent.action.APP_PERM_EDITOR")
        explicit.setClassName(
            "com.miui.securitycenter",
            "com.miui.permcenter.permissions.PermissionsEditorActivity"
        )
        explicit.putExtra("extra_pkgname", packageName)
        explicit.putExtra("package_name", packageName)
        if (tryStartActivity(explicit)) {
            return true
        }

        val implicit = Intent("miui.intent.action.APP_PERM_EDITOR")
        implicit.putExtra("extra_pkgname", packageName)
        implicit.putExtra("package_name", packageName)
        return tryStartActivity(implicit)
    }

    private fun tryStartActivity(intent: Intent): Boolean {
        return try {
            startActivity(intent)
            true
        } catch (_: Exception) {
            false
        }
    }

    private fun requestDingTalkLaunch() {
        AppLog.i(this, "DingTalk launch requested from activity through guard service")
        startGuardService(Config.ACTION_OPEN_DING)
    }

    private fun openNotificationListenerSettings() {
        try {
            startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS))
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openUsageAccessSettings() {
        try {
            startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
        } catch (_: Exception) {
            openAppSettings()
        }
    }

    private fun openOverlaySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            } catch (_: Exception) {
            }
        }
        if (openMiuiPermissionEditor()) {
            return
        }
        openAppSettings()
    }

    private fun openAppNotificationSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (tryStartActivity(
                    Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                )
            ) {
                return
            }
        }
        openAppSettings()
    }

    private fun openAlertChannelSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            if (tryStartActivity(
                    Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS)
                        .putExtra(Settings.EXTRA_APP_PACKAGE, packageName)
                        .putExtra(Settings.EXTRA_CHANNEL_ID, NotificationHelper.ALERT_CHANNEL_ID)
                )
            ) {
                return
            }
        }
        openAppNotificationSettings()
    }

    private fun openFullScreenIntentSettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            if (tryStartActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
            ) {
                return
            }
        }
        openAppNotificationSettings()
    }

    private fun openMiuiPermissionSettings() {
        if (openMiuiPermissionEditor()) {
            return
        }
        openAppSettings()
    }

    private fun openBackgroundLaunchSettings() {
        val status = BackgroundLaunchPermission.status(this)
        if (!status.notificationsEnabled) {
            openAppNotificationSettings()
            return
        }
        if (!status.alertChannelEnabled || !status.alertChannelHighPriority) {
            openAlertChannelSettings()
            return
        }
        if (!status.fullScreenIntentAllowed && Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            try {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_APP_USE_FULL_SCREEN_INTENT,
                        Uri.parse("package:$packageName")
                    )
                )
                return
            } catch (_: Exception) {
            }
        }
        if (openMiuiPermissionEditor()) {
            return
        }
        openAppSettings()
    }

    private fun refreshCurrentLocation() {
        if (!hasFineLocationPermission()) {
            Toast.makeText(this, "请先授权精确定位权限", Toast.LENGTH_SHORT).show()
            requestCorePermissions()
            return
        }
        updateLastKnownLocation()
        refreshDashboard()

        val provider = bestEnabledProvider()
        if (provider == null) {
            Toast.makeText(this, "系统定位未开启，无法重新检测位置", Toast.LENGTH_LONG).show()
            return
        }

        Toast.makeText(this, "正在刷新当前位置", Toast.LENGTH_SHORT).show()
        try {
            locationManager.requestSingleUpdate(
                provider,
                object : LocationListener {
                    override fun onLocationChanged(location: Location) {
                        uiLocation = location
                        refreshDashboard()
                        Toast.makeText(this@MainActivity, "距离已刷新", Toast.LENGTH_SHORT).show()
                    }

                    override fun onProviderEnabled(provider: String) = Unit
                    override fun onProviderDisabled(provider: String) = Unit
                    @Deprecated("Deprecated in Android framework")
                    override fun onStatusChanged(provider: String?, status: Int, extras: Bundle?) = Unit
                },
                Looper.getMainLooper()
            )
        } catch (e: SecurityException) {
            Toast.makeText(this, "定位权限不可用：${e.message}", Toast.LENGTH_LONG).show()
        } catch (e: Exception) {
            Toast.makeText(this, "刷新定位失败：${e.message}", Toast.LENGTH_LONG).show()
        }
    }

    private fun updateLastKnownLocation() {
        if (!::locationManager.isInitialized || !hasFineLocationPermission()) {
            return
        }
        try {
            val gps = locationManager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            val network = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER)
            val passive = locationManager.getLastKnownLocation(LocationManager.PASSIVE_PROVIDER)
            uiLocation = newer(uiLocation, newer(gps, newer(network, passive)))
        } catch (_: SecurityException) {
        } catch (_: Exception) {
        }
    }

    private fun refreshDashboard(currentForm: SettingsForm = form) {
        if (TimeScheduler.markExpiredCheckInIfNeeded(this)) {
            TimeScheduler.scheduleAll(this)
        }
        dashboard = buildDashboard(currentForm)
    }

    private fun buildDashboard(currentForm: SettingsForm): DashboardState {
        val targetName = currentForm.placeName.ifBlank { Config.placeName(this) }
        val targetGcjLat = currentForm.lat.toDoubleOrNull() ?: Config.gcjLat(this)
        val targetGcjLon = currentForm.lon.toDoubleOrNull() ?: Config.gcjLon(this)
        val radiusMeters = max(1, currentForm.radius.toIntOrNull() ?: Config.radiusMeters(this))
        val targetConfigured = abs(targetGcjLat) > 0.000001 || abs(targetGcjLon) > 0.000001
        val targetWgs = CoordinateKit.gcj02ToWgs84(targetGcjLat, targetGcjLon)
        val today = LocalDate.now()
        val dayStatus = ChinaWorkdayCalendar.status(today)
        val workdayToday = dayStatus.isWorkday()
        val distanceMeters = if (targetConfigured && hasFineLocationPermission() && uiLocation != null) {
            CoordinateKit.distanceMetersFromWgsToGcjTarget(
                uiLocation!!.latitude,
                uiLocation!!.longitude,
                targetGcjLat,
                targetGcjLon
            )
        } else {
            null
        }
        val insideTarget = distanceMeters != null && distanceMeters <= radiusMeters
        val exactAlarm = Build.VERSION.SDK_INT < Build.VERSION_CODES.S ||
            getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        val notificationGranted = Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED
        val usageStatsGranted = ForegroundAppVerifier.hasUsageStatsAccess(this)
        val backgroundLaunchStatus = BackgroundLaunchPermission.status(this)
        val dingTalkInstalled = packageManager.getLaunchIntentForPackage(Config.DINGTALK_PACKAGE) != null
        val checkoutDueMillis = TimeScheduler.checkoutDueMillis(this)
        val hasCheckIn = Config.hasCheckedInToday(this)
        val missedCheckIn = Config.hasMissedCheckInToday(this)
        val hasCheckOut = Config.hasCheckedOutToday(this)
        val warnings = mutableListOf<String>()

        if (!targetConfigured) {
            warnings.add("还没有配置目标地点，无法判断是否进入打卡范围。")
        }
        if (!hasFineLocationPermission()) {
            warnings.add("精确定位未授权，300 米范围判断不可靠。")
        }
        if (!hasBackgroundLocationPermission()) {
            warnings.add("后台定位未授权，锁屏或后台时可能无法及时判断范围。")
        }
        if (!notificationGranted) {
            warnings.add("通知权限未授权，强提醒和确认按钮可能不可见。")
        }
        if (!isDingTalkNotificationListenerEnabled()) {
            warnings.add("未开启成功通知识别，无法根据钉钉成功通知自动记录。")
        }
        if (Config.assumeDingTalkOpenMeansSuccess(this) && !usageStatsGranted) {
            warnings.add("前台兜底记录需要前台验证权限，否则不能确认钉钉是否真的打开。")
        }
        if (!exactAlarm) {
            warnings.add("准时提醒权限未授权，到点提醒可能延迟。")
        }
        if (!isIgnoringBatteryOptimizations()) {
            warnings.add("电池后台未放行，系统清理后提醒成功率会下降。")
        }
        if (!backgroundLaunchStatus.likelyAllowed()) {
            warnings.add("后台拉起未完全放行：${backgroundLaunchStatus.displayText()}。")
        }
        if (!dingTalkInstalled) {
            warnings.add("没有解析到钉钉启动入口，请确认钉钉已安装。")
        }
        val checkInTime = parseTime(currentForm.checkInStart, LocalTime.of(8, 30))
        val lateMinutes = max(0, currentForm.lateMinutes.toIntOrNull() ?: 30)
        val checkoutTime = parseTime(currentForm.checkoutTime, LocalTime.of(18, 0))
        val checkInWindowText = "${Config.format(checkInTime.minusMinutes(30))} - ${Config.format(checkInTime.plusMinutes(lateMinutes.toLong()))}"
        val nowDateTime = LocalDateTime.now()
        val checkInWindowStart = today.atTime(checkInTime).minusMinutes(30)
        val checkInWindowEnd = today.atTime(checkInTime).plusMinutes(lateMinutes.toLong())
        val checkInWindowActive = workdayToday &&
            !hasCheckIn &&
            !missedCheckIn &&
            !nowDateTime.isBefore(checkInWindowStart) &&
            !nowDateTime.isAfter(checkInWindowEnd)
        val normalCheckInMillis = LocalDate.now()
            .atTime(checkInTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val baseCheckoutMillis = LocalDate.now()
            .atTime(checkoutTime)
            .atZone(ZoneId.systemDefault())
            .toInstant()
            .toEpochMilli()
        val lateMillis = if (hasCheckIn) max(0L, Config.checkInMillis(this) - normalCheckInMillis) else 0L
        val plannedCheckOutText = formatMillis(baseCheckoutMillis + lateMillis)
        val actualCheckOutText = if (hasCheckOut) formatMillis(Config.checkOutMillis(this)) else null
        val checkoutGraceMillis = max(30, currentForm.checkoutGraceMinutes.toIntOrNull() ?: Config.checkoutGraceMinutes(this)) * 60_000L
        val nowMillis = System.currentTimeMillis()
        val checkoutWindowActive = workdayToday &&
            !hasCheckOut &&
            checkoutDueMillis > 0L &&
            nowMillis >= checkoutDueMillis &&
            nowMillis <= checkoutDueMillis + checkoutGraceMillis

        return DashboardState(
            enabled = Config.isEnabled(this),
            windowActive = checkInWindowActive || checkoutWindowActive,
            checkInWindowActive = checkInWindowActive,
            checkOutWindowActive = checkoutWindowActive,
            workdayToday = workdayToday,
            dayStatusLabel = dayStatus.label(),
            placeName = targetName,
            targetConfigured = targetConfigured,
            radiusMeters = radiusMeters,
            gcjText = String.format(Locale.US, "%.8f, %.8f", targetGcjLat, targetGcjLon),
            wgsText = String.format(Locale.US, "%.8f, %.8f", targetWgs[0], targetWgs[1]),
            currentLocationText = currentLocationSummary(),
            distanceText = distanceSummary(targetConfigured, distanceMeters, radiusMeters),
            distanceBrief = distanceBrief(targetConfigured, distanceMeters, radiusMeters),
            distanceMeters = distanceMeters,
            insideTarget = insideTarget,
            checkInTimeText = Config.format(checkInTime),
            checkInWindowText = checkInWindowText,
            plannedCheckOutText = plannedCheckOutText,
            checkInStatus = when {
                !workdayToday -> "今日休息"
                hasCheckIn -> formatMillis(Config.checkInMillis(this))
                missedCheckIn -> "上班未确认"
                else -> "今天未确认"
            },
            checkOutStatus = when {
                !workdayToday -> "今日休息"
                actualCheckOutText != null -> actualCheckOutText
                else -> "今天未确认"
            },
            checkoutDueText = checkoutDueText(checkoutDueMillis, actualCheckOutText, workdayToday),
            nextActionText = nextActionText(currentForm, checkoutDueMillis, hasCheckOut, workdayToday),
            locationPermissionText = locationPermissionSummary(),
            fineLocationGranted = hasFineLocationPermission(),
            backgroundLocationGranted = hasBackgroundLocationPermission(),
            notificationGranted = notificationGranted,
            notificationListenerEnabled = isDingTalkNotificationListenerEnabled(),
            usageStatsGranted = usageStatsGranted,
            notificationSwitchGranted = backgroundLaunchStatus.notificationsEnabled,
            alertChannelGranted = backgroundLaunchStatus.alertChannelEnabled && backgroundLaunchStatus.alertChannelHighPriority,
            alertChannelText = backgroundLaunchStatus.alertChannelText(),
            fullScreenIntentGranted = backgroundLaunchStatus.fullScreenIntentAllowed,
            miuiBackgroundPopupGranted = backgroundLaunchStatus.backgroundPopupAllowed(),
            miuiBackgroundPopupText = backgroundLaunchStatus.backgroundPopupText(),
            miuiShowOnLockScreenGranted = backgroundLaunchStatus.showOnLockScreenAllowed(),
            miuiShowOnLockScreenText = backgroundLaunchStatus.showOnLockScreenText(),
            backgroundLaunchGranted = backgroundLaunchStatus.likelyAllowed(),
            backgroundLaunchText = backgroundLaunchStatus.displayText(),
            exactAlarmAllowed = exactAlarm,
            batteryAllowed = isIgnoringBatteryOptimizations(),
            dingTalkInstalled = dingTalkInstalled,
            assumeOpenSuccess = Config.assumeDingTalkOpenMeansSuccess(this),
            warnings = warnings
        )
    }

    private fun currentLocationSummary(): String {
        if (!hasFineLocationPermission()) {
            return "未授权"
        }
        val location = uiLocation ?: return "暂无定位，点“刷新当前位置/距离”"
        return String.format(
            Locale.CHINA,
            "%.6f, %.6f（%s，%s）",
            location.latitude,
            location.longitude,
            location.provider,
            formatLocationAge(location)
        )
    }

    private fun distanceSummary(targetConfigured: Boolean, distanceMeters: Float?, _radiusMeters: Int): String {
        if (!targetConfigured) {
            return "未配置目标地点"
        }
        if (!hasFineLocationPermission()) {
            return "未授权精确定位，无法可靠计算"
        }
        if (distanceMeters == null) {
            return "暂无定位，点“刷新当前位置/距离”"
        }
        return String.format(
            Locale.CHINA,
            "约%.0f米",
            distanceMeters
        )
    }

    private fun distanceBrief(targetConfigured: Boolean, distanceMeters: Float?, _radiusMeters: Int): String {
        if (!targetConfigured) {
            return "未设置"
        }
        if (!hasFineLocationPermission()) {
            return "未授权"
        }
        if (distanceMeters == null) {
            return "等待定位"
        }
        return String.format(
            Locale.CHINA,
            "%.0f米",
            distanceMeters
        )
    }

    private fun checkoutDueText(
        checkoutDueMillis: Long,
        actualCheckOutText: String?,
        workdayToday: Boolean
    ): String {
        return when {
            !workdayToday -> "今日休息"
            actualCheckOutText != null -> actualCheckOutText
            checkoutDueMillis <= 0L -> "未计算"
            else -> formatMillis(checkoutDueMillis)
        }
    }

    private fun nextActionText(
        currentForm: SettingsForm,
        checkoutDueMillis: Long,
        hasCheckOut: Boolean,
        workdayToday: Boolean
    ): String {
        if (!workdayToday) {
            return "今日休息，不安排提醒"
        }
        if (!Config.isEnabled(this)) {
            return "自动提醒未开启"
        }
        val today = LocalDate.now()
        val now = LocalDateTime.now()
        val checkInTime = parseTime(currentForm.checkInStart, LocalTime.of(8, 30))
        val start = today.atTime(checkInTime).minusMinutes(30)
        val activeEnd = today.atTime(checkInTime).plusMinutes(max(0, currentForm.lateMinutes.toIntOrNull() ?: 30).toLong())
        if (!Config.hasCheckedInToday(this)) {
            return when {
                now.isBefore(start) -> "下次上班检测 ${formatLocalTime(start)}"
                !now.isAfter(activeEnd) -> "上班检测窗口进行中"
                checkoutDueMillis > 0L -> "未确认上班，仍会在 ${formatMillis(checkoutDueMillis)} 提醒下班"
                else -> "等待明天上班窗口"
            }
        }
        if (!hasCheckOut && checkoutDueMillis > 0L) {
            val stop = checkoutDueMillis + max(30, currentForm.checkoutGraceMinutes.toIntOrNull() ?: 180) * 60_000L
            val nowMillis = System.currentTimeMillis()
            return when {
                nowMillis < checkoutDueMillis -> "下班提醒 ${formatMillis(checkoutDueMillis)}"
                nowMillis <= stop -> "下班提醒窗口进行中"
                else -> "下班提醒窗口已结束"
            }
        }
        return "今日打卡已确认，等待明天"
    }

    private fun bestEnabledProvider(): String? {
        if (!::locationManager.isInitialized) {
            return null
        }
        return try {
            when {
                hasFineLocationPermission() && locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ->
                    LocationManager.GPS_PROVIDER
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) ->
                    LocationManager.NETWORK_PROVIDER
                else -> null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun formatLocationAge(location: Location): String {
        val time = location.time
        if (time <= 0L) {
            return "时间未知"
        }
        val ageSeconds = max(0L, (System.currentTimeMillis() - time) / 1000L)
        if (ageSeconds < 60L) {
            return "刚刚"
        }
        val ageMinutes = ageSeconds / 60L
        if (ageMinutes < 60L) {
            return "${ageMinutes}分钟前"
        }
        return "${ageMinutes / 60L}小时前"
    }

    private fun hasFineLocationPermission(): Boolean {
        return checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasAnyLocationPermission(): Boolean {
        return hasFineLocationPermission() ||
            checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun hasBackgroundLocationPermission(): Boolean {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.Q ||
            checkSelfPermission(Manifest.permission.ACCESS_BACKGROUND_LOCATION) == PackageManager.PERMISSION_GRANTED
    }

    private fun backgroundLocationOptionLabel(): String {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val label = packageManager.backgroundPermissionOptionLabel
            if (!label.isNullOrEmpty()) {
                return label.toString()
            }
        }
        return "始终允许"
    }

    private fun locationPermissionSummary(): String {
        return when {
            hasFineLocationPermission() -> "精确定位已授权"
            hasAnyLocationPermission() -> "仅大概位置，不适合300米范围判断"
            else -> "未授权"
        }
    }

    private fun isIgnoringBatteryOptimizations(): Boolean {
        val powerManager = getSystemService(PowerManager::class.java)
        return powerManager != null && powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    private fun isDingTalkNotificationListenerEnabled(): Boolean {
        val enabled = Settings.Secure.getString(contentResolver, "enabled_notification_listeners")
        if (enabled.isNullOrBlank()) {
            return false
        }
        val expected = ComponentName(this, DingTalkPunchObserver::class.java)
            .flattenToString()
            .lowercase(Locale.US)
        return enabled.lowercase(Locale.US).contains(expected)
    }

    private fun formatMillis(millis: Long): String {
        return DateTimeFormatter.ofPattern("HH:mm").format(
            LocalDateTime.ofInstant(Instant.ofEpochMilli(millis), ZoneId.systemDefault())
        )
    }

    private fun formatLocalTime(dateTime: LocalDateTime): String {
        return DateTimeFormatter.ofPattern("HH:mm").format(dateTime)
    }

    private fun parseTime(value: String, fallback: LocalTime): LocalTime {
        val patterns = listOf("HH:mm", "H:mm")
        for (pattern in patterns) {
            try {
                return LocalTime.parse(value, DateTimeFormatter.ofPattern(pattern))
            } catch (_: Exception) {
            }
        }
        val digits = value.filter { it.isDigit() }
        if (digits.length == 3 || digits.length == 4) {
            try {
                val hour = digits.dropLast(2).toInt()
                val minute = digits.takeLast(2).toInt()
                return LocalTime.of(hour, minute)
            } catch (_: Exception) {
            }
        }
        return fallback
    }

    companion object {
        private const val TAG = "CheckinReminder"

        private fun newer(a: Location?, b: Location?): Location? {
            if (a == null) return b
            if (b == null) return a
            return if (a.time >= b.time) a else b
        }
    }
}

private data class SettingsForm(
    val amapLink: String = "",
    val placeName: String = "",
    val lat: String = "",
    val lon: String = "",
    val radius: String = "300",
    val checkInStart: String = "08:30",
    val lateMinutes: String = "30",
    val checkoutTime: String = "18:00",
    val reminderMinutes: String = "2",
    val checkoutGraceMinutes: String = "180",
    val requireLocationForCheckout: Boolean = false,
    val assumeOpenSuccess: Boolean = false
) {
    companion object {
        fun from(context: android.content.Context): SettingsForm {
            return SettingsForm(
                placeName = Config.placeName(context),
                lat = String.format(Locale.US, "%.14f", Config.gcjLat(context)),
                lon = String.format(Locale.US, "%.14f", Config.gcjLon(context)),
                radius = Config.radiusMeters(context).toString(),
                checkInStart = Config.format(Config.checkInStart(context)),
                lateMinutes = Config.lateMinutes(context).toString(),
                checkoutTime = Config.format(Config.checkoutTime(context)),
                reminderMinutes = Config.reminderMinutes(context).toString(),
                checkoutGraceMinutes = Config.checkoutGraceMinutes(context).toString(),
                requireLocationForCheckout = Config.requireLocationForCheckout(context),
                assumeOpenSuccess = Config.assumeDingTalkOpenMeansSuccess(context)
            )
        }
    }
}

private data class DashboardState(
    val enabled: Boolean = false,
    val windowActive: Boolean = false,
    val checkInWindowActive: Boolean = false,
    val checkOutWindowActive: Boolean = false,
    val workdayToday: Boolean = true,
    val dayStatusLabel: String = "工作日",
    val placeName: String = "公司",
    val targetConfigured: Boolean = false,
    val radiusMeters: Int = 300,
    val gcjText: String = "0.00000000, 0.00000000",
    val wgsText: String = "0.00000000, 0.00000000",
    val currentLocationText: String = "暂无定位",
    val distanceText: String = "未计算",
    val distanceBrief: String = "未计算",
    val distanceMeters: Float? = null,
    val insideTarget: Boolean = false,
    val checkInTimeText: String = "08:30",
    val checkInWindowText: String = "08:30 - 09:00",
    val plannedCheckOutText: String = "18:00",
    val checkInStatus: String = "今天未确认",
    val checkOutStatus: String = "今天未确认",
    val checkoutDueText: String = "未计算",
    val nextActionText: String = "自动提醒未开启",
    val locationPermissionText: String = "未授权",
    val fineLocationGranted: Boolean = false,
    val backgroundLocationGranted: Boolean = false,
    val notificationGranted: Boolean = false,
    val notificationListenerEnabled: Boolean = false,
    val usageStatsGranted: Boolean = false,
    val notificationSwitchGranted: Boolean = false,
    val alertChannelGranted: Boolean = false,
    val alertChannelText: String = "未检测",
    val fullScreenIntentGranted: Boolean = false,
    val miuiBackgroundPopupGranted: Boolean = false,
    val miuiBackgroundPopupText: String = "未检测",
    val miuiShowOnLockScreenGranted: Boolean = false,
    val miuiShowOnLockScreenText: String = "未检测",
    val backgroundLaunchGranted: Boolean = false,
    val backgroundLaunchText: String = "未检测",
    val exactAlarmAllowed: Boolean = false,
    val batteryAllowed: Boolean = false,
    val dingTalkInstalled: Boolean = false,
    val assumeOpenSuccess: Boolean = false,
    val warnings: List<String> = emptyList()
)

private object AppColors {
    val Background = Color(0xFFF6F7F9)
    val Surface = Color(0xFFFFFFFF)
    val TextPrimary = Color(0xFF18212B)
    val TextSecondary = Color(0xFF5C6673)
    val Primary = Color(0xFF365F84)
    val Secondary = Color(0xFF6B7280)
    val Warning = Color(0xFFA16207)
    val Danger = Color(0xFFB42318)
    val Success = Color(0xFF426B8A)
    val Border = Color(0xFFDDE4EC)
    val Panel = Color(0xFFEAF0F6)
    val SoftBlue = Color(0xFFE9F1F8)
    const val backgroundInt: Int = 0xFFF6F7F9.toInt()
    const val surfaceInt: Int = 0xFFFFFFFF.toInt()
}

private val BaseTypography = Typography()
private val AppTypography = Typography(
    titleLarge = BaseTypography.titleLarge.copy(fontSize = 18.sp, lineHeight = 24.sp),
    titleMedium = BaseTypography.titleMedium.copy(fontSize = 16.sp, lineHeight = 22.sp),
    titleSmall = BaseTypography.titleSmall.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyLarge = BaseTypography.bodyLarge.copy(fontSize = 14.sp, lineHeight = 20.sp),
    bodyMedium = BaseTypography.bodyMedium.copy(fontSize = 13.sp, lineHeight = 18.sp),
    bodySmall = BaseTypography.bodySmall.copy(fontSize = 12.sp, lineHeight = 17.sp),
    labelLarge = BaseTypography.labelLarge.copy(fontSize = 12.sp, lineHeight = 16.sp),
    labelMedium = BaseTypography.labelMedium.copy(fontSize = 11.sp, lineHeight = 15.sp),
    labelSmall = BaseTypography.labelSmall.copy(fontSize = 10.sp, lineHeight = 14.sp)
)

@Composable
private fun DingPunchTheme(content: @Composable () -> Unit) {
    val scheme = lightColorScheme(
        primary = AppColors.Primary,
        secondary = AppColors.Secondary,
        tertiary = AppColors.Warning,
        background = AppColors.Background,
        surface = AppColors.Surface,
        onPrimary = Color.White,
        onSecondary = Color.White,
        onBackground = AppColors.TextPrimary,
        onSurface = AppColors.TextPrimary
    )
    MaterialTheme(colorScheme = scheme, typography = AppTypography, content = content)
}

@Composable
private fun DingPunchApp(
    selectedTab: Int,
    onTabSelected: (Int) -> Unit,
    form: SettingsForm,
    dashboard: DashboardState,
    parseStatus: String,
    parsingAmap: Boolean,
    onFormChange: (SettingsForm) -> Unit,
    onParseAmap: () -> Unit,
    onSave: () -> Unit,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenAlertChannelSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenMiuiPermissionSettings: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefreshLocation: () -> Unit,
    onStartGuard: () -> Unit,
    onStopGuard: () -> Unit,
    onOpenDingTalk: () -> Unit,
    onConfirmCheckIn: () -> Unit,
    onConfirmCheckOut: () -> Unit
) {
    val tabs = listOf(
        TabSpec("首页", Icons.Filled.Home),
        TabSpec("规则", Icons.Filled.Settings),
        TabSpec("权限", Icons.Filled.Security)
    )

    Scaffold(
        bottomBar = {
            NavigationBar(containerColor = AppColors.Surface) {
                tabs.forEachIndexed { index, tab ->
                    NavigationBarItem(
                        selected = selectedTab == index,
                        onClick = { onTabSelected(index) },
                        icon = { Icon(tab.icon, contentDescription = null, modifier = Modifier.size(28.dp)) },
                        label = {
                            Text(
                                tab.title,
                                fontSize = 16.sp,
                                lineHeight = 20.sp,
                                fontWeight = FontWeight.Normal
                            )
                        },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = AppColors.Primary,
                            selectedTextColor = AppColors.Primary,
                            indicatorColor = AppColors.SoftBlue
                        )
                    )
                }
            }
        },
        containerColor = AppColors.Background
    ) { padding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(AppColors.Background)
        ) {
            when (selectedTab) {
                0 -> OverviewScreen(
                    dashboard = dashboard,
                    onStartGuard = onStartGuard,
                    onOpenDingTalk = onOpenDingTalk,
                    onOpenRules = { onTabSelected(1) },
                    onOpenCheck = { onTabSelected(2) }
                )
                1 -> SettingsScreen(
                    form = form,
                    parseStatus = parseStatus,
                    parsingAmap = parsingAmap,
                    onFormChange = onFormChange,
                    onParseAmap = onParseAmap,
                    onSave = onSave,
                    onPauseReminders = onStopGuard,
                    enabled = dashboard.enabled
                )
                else -> PermissionsScreen(
                    dashboard = dashboard,
                    onRequestPermissions = onRequestPermissions,
                    onOpenLocationSettings = onOpenLocationSettings,
                    onOpenNotificationListener = onOpenNotificationListener,
                    onOpenUsageAccessSettings = onOpenUsageAccessSettings,
                    onOpenAppNotificationSettings = onOpenAppNotificationSettings,
                    onOpenAlertChannelSettings = onOpenAlertChannelSettings,
                    onOpenFullScreenIntentSettings = onOpenFullScreenIntentSettings,
                    onOpenMiuiPermissionSettings = onOpenMiuiPermissionSettings,
                    onRequestExactAlarm = onRequestExactAlarm,
                    onOpenBatterySettings = onOpenBatterySettings,
                    onOpenAppSettings = onOpenAppSettings,
                    onRefreshLocation = onRefreshLocation,
                    onOpenDingTalk = onOpenDingTalk
                )
            }
        }
    }
}

@Composable
private fun OverviewScreen(
    dashboard: DashboardState,
    onStartGuard: () -> Unit,
    onOpenDingTalk: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenCheck: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 360.dp || maxHeight < 650.dp
        val largeLayout = maxWidth >= 390.dp && maxHeight >= 780.dp
        val fontScale = LocalDensity.current.fontScale
        val scaledText = fontScale > 1.05f
        val horizontalPadding = if (compact) 16.dp else if (largeLayout) 20.dp else 18.dp
        val topPadding = if (compact) 26.dp else if (largeLayout) 54.dp else if (scaledText) 30.dp else 36.dp
        val blockSpacing = if (compact) 22.dp else if (largeLayout) 34.dp else if (scaledText) 20.dp else 30.dp
        val date = LocalDate.now().format(DateTimeFormatter.ofPattern("MM.dd"))
        val time = LocalTime.now().format(DateTimeFormatter.ofPattern("HH:mm"))
        val checkInDone = dashboard.workdayToday &&
            dashboard.checkInStatus != "今天未确认" &&
            dashboard.checkInStatus != "上班未确认"
        val checkOutDone = dashboard.workdayToday && dashboard.checkOutStatus != "今天未确认"
        val checkInTime = if (checkInDone) dashboard.checkInStatus else dashboard.checkInTimeText
        val checkOutTime = if (checkOutDone) {
            dashboard.checkOutStatus
        } else {
            dashboard.checkoutDueText
                .takeIf { dashboard.workdayToday && it != "未计算" }
                ?: dashboard.plannedCheckOutText
        }
        val checkInTone = when {
            !dashboard.workdayToday -> HomeTone.Pending
            checkInDone -> HomeTone.Done
            dashboard.checkInStatus == "上班未确认" -> HomeTone.Warning
            dashboard.checkInWindowActive -> HomeTone.Current
            else -> HomeTone.Warning
        }
        val checkOutTone = when {
            !dashboard.workdayToday -> HomeTone.Pending
            checkOutDone -> HomeTone.Done
            dashboard.checkOutWindowActive -> HomeTone.Current
            checkInDone || dashboard.checkInStatus == "上班未确认" -> HomeTone.Pending
            else -> HomeTone.Pending
        }
        val checkInState = when {
            !dashboard.workdayToday -> "不提醒"
            checkInDone -> "已完成"
            dashboard.checkInStatus == "上班未确认" -> "未完成"
            dashboard.checkInWindowActive -> "进行中"
            else -> "未完成"
        }
        val checkOutState = when {
            !dashboard.workdayToday -> "不提醒"
            checkOutDone -> "已完成"
            dashboard.checkOutWindowActive -> "进行中"
            checkInDone || dashboard.checkInStatus == "上班未确认" -> "下一步"
            else -> "未开始"
        }
        val locationTone = when {
            dashboard.insideTarget -> HomeTone.Current
            dashboard.distanceMeters != null -> HomeTone.Warning
            else -> HomeTone.Pending
        }

        Column(
            modifier = Modifier
                .align(Alignment.TopCenter)
                .widthIn(max = if (largeLayout) 500.dp else 460.dp)
                .fillMaxWidth()
                .padding(
                    start = horizontalPadding,
                    end = horizontalPadding,
                    top = topPadding
                ),
            verticalArrangement = Arrangement.spacedBy(blockSpacing)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "打卡进度",
                    color = AppColors.TextPrimary,
                    fontSize = if (largeLayout) 30.sp else 26.sp,
                    lineHeight = if (largeLayout) 36.sp else 32.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.weight(1f)
                )
                HomeSwitchPill(
                    enabled = dashboard.enabled,
                    large = largeLayout,
                    onClick = {
                        when {
                            !dashboard.targetConfigured -> onOpenRules()
                            dashboard.enabled -> onOpenDingTalk()
                            dashboard.warnings.isNotEmpty() -> onOpenCheck()
                            !dashboard.enabled -> onStartGuard()
                        }
                    }
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(if (largeLayout) 18.dp else 14.dp)
            ) {
                HomeSmallStatusCard(
                    label = "日期",
                    value = date,
                    meta = dashboard.dayStatusLabel,
                    tone = if (dashboard.workdayToday) HomeTone.Workday else HomeTone.Pending,
                    large = largeLayout,
                    scaledText = scaledText,
                    modifier = Modifier.weight(1f)
                )
                HomeSmallStatusCard(
                    label = "时间",
                    value = time,
                    meta = "当前时间",
                    tone = HomeTone.Neutral,
                    large = largeLayout,
                    scaledText = scaledText,
                    modifier = Modifier.weight(1f)
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(if (largeLayout) 16.dp else 14.dp)) {
                HomeCheckCapsule(
                    label = "上班",
                    time = checkInTime,
                    state = checkInState,
                    tone = checkInTone,
                    large = largeLayout,
                    scaledText = scaledText
                )
                HomeCheckCapsule(
                    label = "下班",
                    time = checkOutTime,
                    state = checkOutState,
                    tone = checkOutTone,
                    large = largeLayout,
                    scaledText = scaledText
                )
            }

            HomeLocationCapsule(
                placeName = if (dashboard.targetConfigured) dashboard.placeName else "未设置",
                distance = dashboard.distanceBrief,
                tone = locationTone,
                large = largeLayout,
                scaledText = scaledText
            )
        }
    }
}

private enum class HomeTone {
    Workday,
    Neutral,
    Done,
    Current,
    Warning,
    Pending
}

private fun HomeTone.background(): Color = when (this) {
    HomeTone.Workday -> Color(0xFFFFF3C7)
    HomeTone.Neutral -> Color.White.copy(alpha = 0.86f)
    HomeTone.Done -> Color(0xFFE7F4EF)
    HomeTone.Current -> Color(0xFFE5EEF8)
    HomeTone.Warning -> Color(0xFFFFE7E1)
    HomeTone.Pending -> Color(0xFFF1F4F7)
}

private fun HomeTone.shadow(): Color = when (this) {
    HomeTone.Workday -> Color(0x33B58118)
    HomeTone.Done -> Color(0x24328D68)
    HomeTone.Current -> Color(0x24365F84)
    HomeTone.Warning -> Color(0x22B42318)
    else -> Color(0x1A2D3A4C)
}

@Composable
private fun HomeSwitchPill(enabled: Boolean, large: Boolean, onClick: () -> Unit) {
    Button(
        onClick = onClick,
        shape = RoundedCornerShape(if (large) 22.dp else 18.dp),
        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = if (large) 24.dp else 20.dp, vertical = 0.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = if (enabled) AppColors.Primary else Color(0xFFE7ECF2),
            contentColor = if (enabled) Color.White else AppColors.TextSecondary
        ),
        modifier = Modifier.height(if (large) 44.dp else 38.dp)
    ) {
        Text(
            text = if (enabled) "钉钉" else "关闭",
            fontSize = if (large) 17.sp else 15.sp,
            lineHeight = if (large) 22.sp else 20.sp,
            fontWeight = FontWeight.Normal
        )
    }
}

@Composable
private fun HomeSmallStatusCard(
    label: String,
    value: String,
    meta: String,
    tone: HomeTone,
    large: Boolean,
    scaledText: Boolean,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.height(if (large) 152.dp else if (scaledText) 138.dp else 130.dp),
        shape = RoundedCornerShape(if (large) 32.dp else 28.dp),
        color = tone.background(),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Column(
            modifier = Modifier.padding(
                horizontal = if (large) 20.dp else 18.dp,
                vertical = if (large) 20.dp else if (scaledText) 16.dp else 18.dp
            ),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Text(
                text = label,
                color = AppColors.TextSecondary,
                fontSize = if (large) 16.sp else 14.sp,
                lineHeight = if (large) 21.sp else 18.sp,
                fontWeight = FontWeight.Normal
            )
            Column {
                Text(
                    text = value,
                    color = AppColors.TextPrimary,
                    fontSize = if (large) 48.sp else 42.sp,
                    lineHeight = if (large) 52.sp else if (scaledText) 44.sp else 46.sp,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = meta,
                    color = AppColors.TextSecondary,
                    fontSize = if (large) 16.sp else 14.sp,
                    lineHeight = if (large) 21.sp else if (scaledText) 17.sp else 18.sp,
                    fontWeight = FontWeight.Normal,
                    modifier = Modifier.padding(top = if (scaledText && !large) 2.dp else 4.dp)
                )
            }
        }
    }
}

@Composable
private fun HomeCheckCapsule(
    label: String,
    time: String,
    state: String,
    tone: HomeTone,
    large: Boolean,
    scaledText: Boolean
) {
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (large) 132.dp else if (scaledText) 118.dp else 112.dp),
        shape = RoundedCornerShape(if (large) 34.dp else 30.dp),
        color = tone.background(),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = if (large) 22.dp else 20.dp)
        ) {
            Text(
                text = label,
                color = AppColors.TextSecondary,
                fontSize = if (large) 18.sp else 16.sp,
                lineHeight = if (large) 24.sp else if (scaledText) 19.sp else 21.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.align(Alignment.CenterStart)
            )
            Text(
                text = time,
                color = AppColors.TextPrimary,
                fontSize = if (large) 48.sp else 42.sp,
                lineHeight = if (large) 52.sp else if (scaledText) 44.sp else 46.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.align(Alignment.Center)
            )
            Text(
                text = state,
                color = AppColors.TextSecondary,
                fontSize = if (large) 17.sp else 14.sp,
                lineHeight = if (large) 22.sp else if (scaledText) 17.sp else 18.sp,
                fontWeight = FontWeight.Normal,
                modifier = Modifier.align(Alignment.CenterEnd)
            )
        }
    }
}

@Composable
private fun HomeLocationCapsule(
    placeName: String,
    distance: String,
    tone: HomeTone,
    large: Boolean,
    scaledText: Boolean
) {
    val placeFontSize = homeLocationValueFontSize(placeName, large, scaledText)
    val distanceFontSize = homeLocationValueFontSize(distance, large, scaledText)
    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .height(if (large) 150.dp else if (scaledText) 136.dp else 124.dp),
        shape = RoundedCornerShape(if (large) 34.dp else 30.dp),
        color = tone.background(),
        shadowElevation = 0.dp,
        tonalElevation = 0.dp
    ) {
        Row(
            modifier = Modifier.padding(
                horizontal = if (large) 22.dp else 20.dp,
                vertical = if (large) 22.dp else if (scaledText) 16.dp else 18.dp
            ),
            horizontalArrangement = Arrangement.spacedBy(if (large) 22.dp else 18.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(
                modifier = Modifier.weight(1.32f),
                verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else if (scaledText) 8.dp else 10.dp)
            ) {
                Text(
                    text = "位置",
                    color = AppColors.TextSecondary,
                    fontSize = if (large) 17.sp else 14.sp,
                    lineHeight = if (large) 22.sp else if (scaledText) 17.sp else 18.sp,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = placeName,
                    color = AppColors.TextPrimary,
                    fontSize = placeFontSize,
                    lineHeight = homeLocationValueLineHeight(placeFontSize, large),
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Column(
                modifier = Modifier.weight(0.68f),
                verticalArrangement = Arrangement.spacedBy(if (large) 12.dp else if (scaledText) 8.dp else 10.dp)
            ) {
                Text(
                    text = "距离",
                    color = AppColors.TextSecondary,
                    fontSize = if (large) 17.sp else 14.sp,
                    lineHeight = if (large) 22.sp else if (scaledText) 17.sp else 18.sp,
                    fontWeight = FontWeight.Normal
                )
                Text(
                    text = distance,
                    color = AppColors.TextPrimary,
                    fontSize = distanceFontSize,
                    lineHeight = homeLocationValueLineHeight(distanceFontSize, large),
                    fontWeight = FontWeight.Normal,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun homeLocationValueFontSize(text: String, large: Boolean, scaledText: Boolean): TextUnit {
    val length = text.trim().length
    val size = when {
        large && length <= 5 -> 32
        large && length <= 7 -> 29
        large && length <= 10 -> 25
        large && length <= 14 -> 21
        large && length <= 18 -> 18
        large -> 15
        length <= 5 -> 26
        length <= 7 -> 23
        length <= 10 -> 19
        length <= 14 -> 16
        length <= 18 -> 14
        else -> 12
    }
    return (if (scaledText && !large) (size - 1).coerceAtLeast(17) else size).sp
}

private fun homeLocationValueLineHeight(fontSize: TextUnit, large: Boolean): TextUnit {
    return (fontSize.value + if (large) 6f else 5f).sp
}

@Composable
private fun HomeStatusGrid(dashboard: DashboardState, profile: HomeLayoutProfile) {
    val recognition = when {
        dashboard.notificationListenerEnabled -> "可自动记录"
        dashboard.assumeOpenSuccess && dashboard.usageStatsGranted -> "前台后记录"
        dashboard.dingTalkInstalled -> "可打开钉钉"
        else -> "未找到钉钉"
    }
    Column(verticalArrangement = Arrangement.spacedBy(profile.sectionSpacing)) {
        Row(horizontalArrangement = Arrangement.spacedBy(profile.sectionSpacing), modifier = Modifier.fillMaxWidth()) {
            HomeStatusTile(
                title = "上班",
                value = dashboard.checkInStatus,
                meta = dashboard.checkInWindowText,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(17.dp))
            }
            HomeStatusTile(
                title = "下班",
                value = dashboard.checkOutStatus,
                meta = dashboard.checkoutDueText,
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.AccessTime, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(17.dp))
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(profile.sectionSpacing), modifier = Modifier.fillMaxWidth()) {
            HomeStatusTile(
                title = "位置",
                value = dashboard.distanceBrief,
                meta = "${dashboard.radiusMeters} 米范围",
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.LocationOn, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(17.dp))
            }
            HomeStatusTile(
                title = "成功识别",
                value = recognition,
                meta = when {
                    dashboard.notificationListenerEnabled -> "钉钉通知确认"
                    dashboard.assumeOpenSuccess && dashboard.usageStatsGranted -> "验证前台后记录"
                    dashboard.assumeOpenSuccess -> "缺少前台验证"
                    else -> "通知或手动确认"
                },
                modifier = Modifier.weight(1f)
            ) {
                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = AppColors.Primary, modifier = Modifier.size(17.dp))
            }
        }
    }
}

@Composable
private fun HomeStatusTile(
    title: String,
    value: String,
    meta: String,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Surface(color = AppColors.SoftBlue, shape = RoundedCornerShape(8.dp), modifier = Modifier.size(32.dp)) {
                Box(contentAlignment = Alignment.Center) {
                    icon()
                }
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(title, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
                Text(
                    value,
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    meta,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }
    }
}

private fun homeLayoutProfile(maxWidth: Dp, maxHeight: Dp): HomeLayoutProfile {
    val compactWidth = maxWidth < 360.dp
    val compactHeight = maxHeight < 700.dp
    val dense = compactWidth || compactHeight
    return HomeLayoutProfile(
        compact = dense,
        screenHorizontalPadding = if (dense) 10.dp else 12.dp,
        screenVerticalPadding = if (dense) 8.dp else 10.dp,
        sectionSpacing = if (dense) 8.dp else 10.dp,
        cardPadding = if (dense) 10.dp else 14.dp,
        tilePadding = if (dense) 10.dp else 12.dp,
        summaryIconSize = if (dense) 34.dp else 40.dp,
        summaryIconInnerSize = if (dense) 19.dp else 22.dp,
        primaryButtonHeight = if (dense) 38.dp else 40.dp,
        showSummaryMetrics = !compactHeight,
        showSeparateStatusTiles = !compactHeight,
        warningCompact = dense,
        expandedHome = maxWidth >= 560.dp && maxHeight >= 900.dp
    )
}

@Composable
private fun TodaySummaryPanel(dashboard: DashboardState, profile: HomeLayoutProfile) {
    val tone = when {
        !dashboard.enabled -> AppColors.TextSecondary
        dashboard.warnings.isNotEmpty() -> AppColors.Warning
        else -> AppColors.Success
    }
    val title = when {
        !dashboard.targetConfigured -> "先设置地点和时间"
        !dashboard.enabled -> "自动提醒未开启"
        dashboard.warnings.isNotEmpty() -> "有项目会影响提醒"
        else -> "提醒已就绪"
    }
    val subtitle = when {
        dashboard.windowActive -> "当前在打卡提醒窗口"
        else -> dashboard.nextActionText
    }
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Panel
    ) {
        Column(Modifier.padding(profile.cardPadding), verticalArrangement = Arrangement.spacedBy(profile.sectionSpacing)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = tone.copy(alpha = 0.12f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.size(profile.summaryIconSize)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = if (dashboard.warnings.isEmpty() && dashboard.enabled) Icons.Filled.CheckCircle else Icons.Filled.Info,
                            contentDescription = null,
                            tint = tone,
                            modifier = Modifier.size(profile.summaryIconInnerSize)
                        )
                    }
                }
                Spacer(Modifier.width(if (profile.compact) 9.dp else 12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        text = title,
                        style = MaterialTheme.typography.titleMedium,
                        color = AppColors.TextPrimary,
                        fontWeight = FontWeight.SemiBold
                    )
                    Text(
                        text = subtitle,
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
                StatusPill(
                    text = if (dashboard.enabled) "已开启" else "未开启",
                    tone = tone
                )
            }
            if (profile.showSummaryMetrics) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                    SummaryMetric("地点", if (dashboard.targetConfigured) dashboard.placeName else "未设置", Modifier.weight(1f), profile)
                    SummaryMetric("位置", dashboard.distanceBrief, Modifier.weight(1f), profile)
                }
            }
        }
    }
}

@Composable
private fun SummaryMetric(label: String, value: String, modifier: Modifier = Modifier, profile: HomeLayoutProfile = HomeLayoutProfile()) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(10.dp),
        color = Color.White.copy(alpha = 0.72f)
    ) {
        Column(
            Modifier.padding(horizontal = profile.tilePadding, vertical = if (profile.compact) 8.dp else 10.dp),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
            Text(
                value,
                color = AppColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeActionSlot(
    dashboard: DashboardState,
    onStartGuard: () -> Unit,
    onOpenRules: () -> Unit,
    onOpenCheck: () -> Unit,
    height: Dp = 40.dp
) {
    when {
        !dashboard.targetConfigured -> PrimaryIconButton(
            text = "完善提醒规则",
            icon = Icons.Filled.Settings,
            modifier = Modifier.fillMaxWidth(),
            height = height,
            onClick = onOpenRules
        )
        !dashboard.enabled -> PrimaryIconButton(
            text = "开启自动提醒",
            icon = Icons.Filled.PlayArrow,
            modifier = Modifier.fillMaxWidth(),
            height = height,
            onClick = onStartGuard
        )
        dashboard.warnings.isNotEmpty() -> PrimaryIconButton(
            text = "处理影响项",
            icon = Icons.Filled.Security,
            modifier = Modifier.fillMaxWidth(),
            height = height,
            onClick = onOpenCheck
        )
    }
}

@Composable
private fun CompactStatusLine(dashboard: DashboardState, profile: HomeLayoutProfile) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(10.dp),
        color = AppColors.Surface
    ) {
        Row(
            modifier = Modifier.padding(horizontal = profile.tilePadding, vertical = 9.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            MetricBlock("上班", dashboard.checkInStatus, Modifier.weight(1f))
            MetricBlock("下班", dashboard.checkOutStatus, Modifier.weight(1f))
        }
    }
}

@Composable
private fun TodayPlanPanel(
    dashboard: DashboardState,
    profile: HomeLayoutProfile,
    modifier: Modifier = Modifier,
    fillHeight: Boolean = false
) {
    val checkoutMeta = when {
        dashboard.checkOutStatus != "今天未确认" -> dashboard.checkOutStatus
        (dashboard.checkInStatus == "今天未确认" || dashboard.checkInStatus == "上班未确认") &&
            dashboard.checkoutDueText != "未计算" -> "上班未确认也提醒"
        else -> dashboard.checkOutStatus
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface
    ) {
        Column(
            Modifier
                .then(if (fillHeight) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = profile.cardPadding, vertical = if (profile.compact) 10.dp else 14.dp),
            verticalArrangement = if (fillHeight) Arrangement.SpaceEvenly else Arrangement.spacedBy(if (profile.compact) 8.dp else 12.dp)
        ) {
            Text(
                "今日安排",
                color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            PlanRow("上班", dashboard.checkInWindowText, dashboard.checkInStatus, profile)
            PlanRow("下班", dashboard.checkoutDueText, checkoutMeta, profile)
            PlanRow("位置", dashboard.distanceBrief, "${dashboard.radiusMeters} 米范围", profile)
        }
    }
}

@Composable
private fun HomeProcessPanel(
    dashboard: DashboardState,
    profile: HomeLayoutProfile,
    modifier: Modifier = Modifier
) {
    val ruleStatus = if (dashboard.targetConfigured) "已设置" else "待设置"
    val reminderStatus = if (dashboard.enabled) "已开启" else "未开启"
    val dingStatus = when {
        dashboard.notificationListenerEnabled -> "可识别成功"
        dashboard.assumeOpenSuccess && dashboard.usageStatsGranted -> "可验证前台"
        dashboard.dingTalkInstalled -> "可打开钉钉"
        else -> "未找到钉钉"
    }
    Surface(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = AppColors.Surface
    ) {
        Column(
            modifier = Modifier
                .padding(horizontal = profile.cardPadding, vertical = if (profile.compact) 10.dp else 14.dp),
            verticalArrangement = Arrangement.spacedBy(if (profile.compact) 8.dp else 12.dp)
        ) {
            Text(
                "提醒流程",
                color = AppColors.TextPrimary,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold
            )
            ProcessStep(
                number = "1",
                title = "地点和时间",
                value = if (dashboard.targetConfigured) dashboard.placeName else "先设置规则",
                status = ruleStatus,
                tone = if (dashboard.targetConfigured) AppColors.Primary else AppColors.Warning
            )
            ProcessStep(
                number = "2",
                title = "到点提醒",
                value = dashboard.nextActionText,
                status = reminderStatus,
                tone = if (dashboard.enabled) AppColors.Primary else AppColors.TextSecondary
            )
            ProcessStep(
                number = "3",
                title = "打开钉钉",
                value = when {
                    dashboard.notificationListenerEnabled -> "成功通知后自动记录"
                    dashboard.assumeOpenSuccess && dashboard.usageStatsGranted -> "确认前台后自动记录"
                    else -> "提醒后手动确认"
                },
                status = dingStatus,
                tone = if (dashboard.dingTalkInstalled || dashboard.notificationListenerEnabled) AppColors.Primary else AppColors.Warning
            )
            Text(
                text = if (dashboard.warnings.isEmpty()) "当前没有明显阻塞项" else "${dashboard.warnings.size} 项需要在检查页处理",
                color = if (dashboard.warnings.isEmpty()) AppColors.TextSecondary else AppColors.Warning,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun HomeLocationPanel(dashboard: DashboardState) {
    InfoCard(title = "位置", icon = Icons.Filled.LocationOn) {
        StatusLine("目标地点", if (dashboard.targetConfigured) dashboard.placeName else "未配置")
        StatusLine("当前位置", dashboard.currentLocationText)
        StatusLine("距离", dashboard.distanceText)
        StatusLine("范围判断", if (dashboard.insideTarget) "范围内" else "未进入或未计算")
    }
}

@Composable
private fun HomeIssuePanel(dashboard: DashboardState) {
    InfoCard(title = "处理项", icon = Icons.Filled.Warning, accent = AppColors.Warning) {
        if (dashboard.warnings.isEmpty()) {
            StatusLine("状态", "暂无明显阻塞")
        } else {
            dashboard.warnings.take(3).forEach { warning ->
                IssueLine(warning)
            }
            if (dashboard.warnings.size > 3) {
                Text(
                    "还有 ${dashboard.warnings.size - 3} 项在检查页处理",
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(top = 4.dp)
                )
            }
        }
    }
}

@Composable
private fun IssueLine(text: String) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(16.dp))
        Spacer(Modifier.width(8.dp))
        Text(
            text,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun ProcessStep(
    number: String,
    title: String,
    value: String,
    status: String,
    tone: Color
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Surface(
            color = tone.copy(alpha = 0.12f),
            shape = RoundedCornerShape(50),
            modifier = Modifier.size(28.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    number,
                    color = tone,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
        Spacer(Modifier.width(9.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    title,
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    status,
                    color = tone,
                    style = MaterialTheme.typography.labelMedium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
            Text(
                value,
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun PlanRow(label: String, main: String, meta: String, profile: HomeLayoutProfile) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Text(
            label,
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(if (profile.compact) 38.dp else 46.dp)
        )
        if (profile.compact || profile.expandedHome) {
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    main,
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                Text(
                    meta,
                    color = AppColors.TextSecondary,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        } else {
            Text(
                main,
                color = AppColors.TextPrimary,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1.1f)
            )
            Spacer(Modifier.width(10.dp))
            Text(
                meta,
                color = AppColors.TextSecondary,
                style = MaterialTheme.typography.bodySmall,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(0.9f)
            )
        }
    }
}

@Composable
private fun WarningStrip(dashboard: DashboardState, profile: HomeLayoutProfile = HomeLayoutProfile()) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(12.dp),
        color = Color(0xFFFFF7ED)
    ) {
        Row(Modifier.padding(profile.cardPadding), verticalAlignment = Alignment.Top) {
            Icon(Icons.Filled.Warning, contentDescription = null, tint = AppColors.Warning, modifier = Modifier.size(if (profile.compact) 18.dp else 20.dp))
            Spacer(Modifier.width(if (profile.compact) 8.dp else 10.dp))
            Column(Modifier.weight(1f)) {
                Text(
                    if (profile.warningCompact) "有 ${dashboard.warnings.size} 项需要处理" else dashboard.warnings.first(),
                    color = AppColors.TextPrimary,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = if (profile.warningCompact) 1 else 2,
                    overflow = TextOverflow.Ellipsis
                )
                if (dashboard.warnings.size > 1 && !profile.warningCompact) {
                    Text(
                        "还有 ${dashboard.warnings.size - 1} 项在“检查”里处理",
                        color = AppColors.TextSecondary,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(top = 3.dp)
                    )
                }
            }
        }
    }
}

@Composable
private fun SettingsScreen(
    form: SettingsForm,
    parseStatus: String,
    parsingAmap: Boolean,
    onFormChange: (SettingsForm) -> Unit,
    onParseAmap: () -> Unit,
    onSave: () -> Unit,
    onPauseReminders: () -> Unit,
    enabled: Boolean
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 380.dp
        ScreenColumn(
            horizontalPadding = if (compact) 10.dp else 12.dp,
            verticalPadding = if (compact) 8.dp else 10.dp,
            itemSpacing = if (compact) 8.dp else 10.dp
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedIconButton(
                    text = if (parsingAmap) "导入中" else "解析备用链接",
                    icon = Icons.Filled.Link,
                    modifier = Modifier.weight(1.15f),
                    enabled = !parsingAmap,
                    onClick = onParseAmap
                )
                PrimaryIconButton(
                    text = "保存规则",
                    icon = Icons.Filled.Save,
                    modifier = Modifier.weight(0.85f),
                    onClick = onSave
                )
            }

            LocationImportPanel(
                form = form,
                parseStatus = parseStatus,
                compact = compact,
                onFormChange = onFormChange
            )

            InfoCard(title = "时间", icon = Icons.Filled.AccessTime) {
                ResponsivePair(
                    compact = compact,
                    first = { modifier ->
                    TimeTextField(
                        value = form.checkInStart,
                        label = "上班时间",
                        modifier = modifier,
                        onValueChange = { onFormChange(form.copy(checkInStart = it)) }
                    )
                },
                    second = { modifier ->
                    AppTextField(
                        value = form.lateMinutes,
                        label = "允许迟到（分钟）",
                        keyboardType = KeyboardType.Number,
                        modifier = modifier,
                        onValueChange = { onFormChange(form.copy(lateMinutes = it)) }
                    )
                })
                TimeTextField(
                    value = form.checkoutTime,
                    label = "下班时间",
                    onValueChange = { onFormChange(form.copy(checkoutTime = it)) }
                )
                ResponsivePair(
                    compact = compact,
                    first = { modifier ->
                    AppTextField(
                        value = form.reminderMinutes,
                        label = "重复提醒间隔",
                        keyboardType = KeyboardType.Number,
                        modifier = modifier,
                        onValueChange = { onFormChange(form.copy(reminderMinutes = it)) }
                    )
                },
                    second = { modifier ->
                    AppTextField(
                        value = form.checkoutGraceMinutes,
                        label = "下班提醒持续",
                        keyboardType = KeyboardType.Number,
                        modifier = modifier,
                        onValueChange = { onFormChange(form.copy(checkoutGraceMinutes = it)) }
                    )
                })
            }

            InfoCard(title = "策略", icon = Icons.Filled.Settings) {
                SwitchRow(
                    title = "下班也要求在范围内",
                    subtitle = "关闭时，到下班时间只要手机解锁就提醒并尝试打开钉钉。",
                    checked = form.requireLocationForCheckout,
                    onCheckedChange = { onFormChange(form.copy(requireLocationForCheckout = it)) }
                )
                Divider(color = AppColors.Border, modifier = Modifier.padding(vertical = 8.dp))
                SwitchRow(
                    title = "钉钉进入前台后自动记录",
                    subtitle = "需要“前台验证”权限。只能兜底确认钉钉已打开，不能替代钉钉成功通知。",
                    checked = form.assumeOpenSuccess,
                    warning = true,
                    onCheckedChange = { onFormChange(form.copy(assumeOpenSuccess = it)) }
                )
            }
            if (enabled) {
                OutlinedIconButton(
                    text = "暂停自动提醒",
                    icon = Icons.Filled.Stop,
                    modifier = Modifier.fillMaxWidth(),
                    onClick = onPauseReminders
                )
            }
        }
    }
}

@Composable
private fun LocationImportPanel(
    form: SettingsForm,
    parseStatus: String,
    compact: Boolean,
    onFormChange: (SettingsForm) -> Unit
) {
    InfoCard(title = "位置", icon = Icons.Filled.LocationOn) {
        if (parseStatus.isNotBlank()) {
            Text(
                text = parseStatus,
                color = if (parseStatus.startsWith("导入失败")) AppColors.Danger else AppColors.Primary,
                style = MaterialTheme.typography.bodySmall
            )
        }

        Divider(color = AppColors.Border, modifier = Modifier.padding(vertical = if (compact) 6.dp else 8.dp))

        ResponsivePair(
            compact = compact,
            spacing = 8.dp,
            firstWeight = 1.35f,
            secondWeight = 0.65f,
            first = { modifier ->
            AppTextField(
                value = form.placeName,
                label = "地点名称",
                modifier = modifier,
                onValueChange = { onFormChange(form.copy(placeName = it)) }
            )
        },
            second = { modifier ->
            AppTextField(
                value = form.radius,
                label = "半径 米",
                keyboardType = KeyboardType.Number,
                modifier = modifier,
                onValueChange = { onFormChange(form.copy(radius = it)) }
            )
        })

        ResponsivePair(
            compact = compact,
            spacing = 8.dp,
            first = { modifier ->
            AppTextField(
                value = form.lat,
                label = "纬度 GCJ-02",
                keyboardType = KeyboardType.Decimal,
                modifier = modifier,
                onValueChange = { onFormChange(form.copy(lat = it)) }
            )
        },
            second = { modifier ->
            AppTextField(
                value = form.lon,
                label = "经度 GCJ-02",
                keyboardType = KeyboardType.Decimal,
                modifier = modifier,
                onValueChange = { onFormChange(form.copy(lon = it)) }
            )
        })
    }
}

@Composable
private fun ResponsivePair(
    compact: Boolean,
    spacing: Dp = 10.dp,
    firstWeight: Float = 1f,
    secondWeight: Float = 1f,
    first: @Composable (Modifier) -> Unit,
    second: @Composable (Modifier) -> Unit
) {
    if (compact) {
        Column(verticalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
            first(Modifier.fillMaxWidth())
            second(Modifier.fillMaxWidth())
        }
    } else {
        Row(horizontalArrangement = Arrangement.spacedBy(spacing), modifier = Modifier.fillMaxWidth()) {
            first(Modifier.weight(firstWeight))
            second(Modifier.weight(secondWeight))
        }
    }
}

@Composable
private fun PermissionsScreen(
    dashboard: DashboardState,
    onRequestPermissions: () -> Unit,
    onOpenLocationSettings: () -> Unit,
    onOpenNotificationListener: () -> Unit,
    onOpenUsageAccessSettings: () -> Unit,
    onOpenAppNotificationSettings: () -> Unit,
    onOpenAlertChannelSettings: () -> Unit,
    onOpenFullScreenIntentSettings: () -> Unit,
    onOpenMiuiPermissionSettings: () -> Unit,
    onRequestExactAlarm: () -> Unit,
    onOpenBatterySettings: () -> Unit,
    onOpenAppSettings: () -> Unit,
    onRefreshLocation: () -> Unit,
    onOpenDingTalk: () -> Unit
) {
    BoxWithConstraints(Modifier.fillMaxSize()) {
        val compact = maxWidth < 380.dp
        ScreenColumn(
            horizontalPadding = if (compact) 10.dp else 12.dp,
            verticalPadding = if (compact) 8.dp else 10.dp,
            itemSpacing = if (compact) 8.dp else 10.dp
        ) {
            InfoCard(title = "检查项", icon = Icons.Filled.Security) {
                PermissionLine(
                    title = "精确定位",
                    subtitle = dashboard.locationPermissionText,
                    ok = dashboard.fineLocationGranted,
                    action = "处理",
                    onAction = onRequestPermissions
                )
                PermissionLine(
                    title = "后台定位",
                    subtitle = if (dashboard.backgroundLocationGranted) "已授权" else "需在系统设置中改成始终允许",
                    ok = dashboard.backgroundLocationGranted,
                    action = "处理",
                    onAction = onOpenLocationSettings
                )
                PermissionLine(
                    title = "通知权限",
                    subtitle = if (dashboard.notificationGranted) "已授权" else "强提醒需要通知权限",
                    ok = dashboard.notificationGranted,
                    action = "处理",
                    onAction = onRequestPermissions
                )
                PermissionLine(
                    title = "通知总开关",
                    subtitle = if (dashboard.notificationSwitchGranted) "已开启" else "系统通知被关闭",
                    ok = dashboard.notificationSwitchGranted,
                    action = "处理",
                    onAction = onOpenAppNotificationSettings
                )
                PermissionLine(
                    title = "强提醒渠道",
                    subtitle = dashboard.alertChannelText,
                    ok = dashboard.alertChannelGranted,
                    action = "处理",
                    onAction = onOpenAlertChannelSettings
                )
                PermissionLine(
                    title = "识别钉钉成功通知",
                    subtitle = if (dashboard.notificationListenerEnabled) "已启用" else "用于自动记录钉钉打卡成功",
                    ok = dashboard.notificationListenerEnabled,
                    action = "处理",
                    onAction = onOpenNotificationListener
                )
                PermissionLine(
                    title = "前台验证",
                    subtitle = if (dashboard.usageStatsGranted) "可验证钉钉是否真的打开" else "用于防止打开失败却自动记成功",
                    ok = dashboard.usageStatsGranted,
                    action = "处理",
                    onAction = onOpenUsageAccessSettings
                )
                PermissionLine(
                    title = "全屏通知",
                    subtitle = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                        if (dashboard.fullScreenIntentGranted) "已允许" else "未允许，后台拉起会被拦截"
                    } else {
                        "系统不需要单独授权"
                    },
                    ok = dashboard.fullScreenIntentGranted,
                    action = "处理",
                    onAction = onOpenFullScreenIntentSettings
                )
                PermissionLine(
                    title = "MIUI后台弹出",
                    subtitle = dashboard.miuiBackgroundPopupText,
                    ok = dashboard.miuiBackgroundPopupGranted,
                    action = "处理",
                    onAction = onOpenMiuiPermissionSettings
                )
                PermissionLine(
                    title = "MIUI锁屏显示",
                    subtitle = dashboard.miuiShowOnLockScreenText,
                    ok = dashboard.miuiShowOnLockScreenGranted,
                    action = "处理",
                    onAction = onOpenMiuiPermissionSettings
                )
                PermissionLine(
                    title = "准时提醒",
                    subtitle = if (dashboard.exactAlarmAllowed) "可用" else "到点提醒可能延迟",
                    ok = dashboard.exactAlarmAllowed,
                    action = "处理",
                    onAction = onRequestExactAlarm
                )
                PermissionLine(
                    title = "电池后台",
                    subtitle = if (dashboard.batteryAllowed) "已放行" else "建议加入电池优化白名单",
                    ok = dashboard.batteryAllowed,
                    action = "处理",
                    onAction = onOpenBatterySettings
                )
                PermissionLine(
                    title = "打开钉钉",
                    subtitle = if (dashboard.dingTalkInstalled) "可打开钉钉" else "未找到钉钉",
                    ok = dashboard.dingTalkInstalled,
                    action = "测试",
                    onAction = onOpenDingTalk
                )
            }
            ResponsivePair(
                compact = compact,
                first = { modifier ->
                PrimaryIconButton(
                    text = "请求必要权限",
                    icon = Icons.Filled.Refresh,
                    modifier = modifier,
                    onClick = onRequestPermissions
                )
            },
                second = { modifier ->
                OutlinedIconButton(
                    text = "重新检测位置",
                    icon = Icons.Filled.LocationOn,
                    modifier = modifier,
                    onClick = onRefreshLocation
                )
            })
            ResponsivePair(
                compact = compact,
                first = { modifier ->
                OutlinedIconButton(
                    text = "测试钉钉",
                    icon = Icons.Filled.OpenInNew,
                    modifier = modifier,
                    onClick = onOpenDingTalk
                )
            },
                second = { modifier ->
                OutlinedIconButton(
                    text = "应用设置",
                    icon = Icons.Filled.OpenInNew,
                    modifier = modifier,
                    onClick = onOpenAppSettings
                )
            })
            InfoCard(title = "可靠性判断", icon = Icons.Filled.Info) {
                StatusLine("目标地点", if (dashboard.targetConfigured) "已配置" else "未配置")
                StatusLine("成功识别", if (dashboard.notificationListenerEnabled) "可识别钉钉成功通知" else "只能提醒或手动确认")
                StatusLine(
                    "前台兜底记录",
                    when {
                        dashboard.assumeOpenSuccess && dashboard.usageStatsGranted -> "已开启"
                        dashboard.assumeOpenSuccess -> "缺少前台验证"
                        else -> "关闭"
                    }
                )
                StatusLine("当前风险项", if (dashboard.warnings.isEmpty()) "暂无明显阻塞" else "${dashboard.warnings.size} 项")
            }
        }
    }
}

@Composable
private fun ScreenColumn(
    horizontalPadding: Dp = 12.dp,
    verticalPadding: Dp = 10.dp,
    itemSpacing: Dp = 10.dp,
    content: @Composable ColumnScope.() -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = horizontalPadding, vertical = verticalPadding),
        verticalArrangement = Arrangement.spacedBy(itemSpacing),
        content = content
    )
}

@Composable
private fun InfoCard(
    title: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    accent: Color = AppColors.Primary,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Column(Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(title, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(10.dp))
            content()
        }
    }
}

@Composable
private fun TwoColumnInfo(
    leftTitle: String,
    leftValue: String,
    rightTitle: String,
    rightValue: String,
    profile: HomeLayoutProfile = HomeLayoutProfile()
) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
        InfoTile(leftTitle, leftValue, Modifier.weight(1f), profile)
        InfoTile(rightTitle, rightValue, Modifier.weight(1f), profile)
    }
}

@Composable
private fun InfoTile(
    title: String,
    value: String,
    modifier: Modifier = Modifier,
    profile: HomeLayoutProfile = HomeLayoutProfile()
) {
    Card(
        modifier = modifier,
        shape = RoundedCornerShape(8.dp),
        colors = CardDefaults.cardColors(containerColor = AppColors.Surface)
    ) {
        Column(Modifier.padding(profile.tilePadding), verticalArrangement = Arrangement.spacedBy(if (profile.compact) 3.dp else 5.dp)) {
            Text(title, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelLarge)
            Text(
                value,
                color = AppColors.TextPrimary,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun StatusLine(label: String, value: String, highlight: Boolean = false) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            label,
            color = AppColors.TextSecondary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.width(96.dp)
        )
        Text(
            value,
            color = if (highlight) AppColors.Success else AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            modifier = Modifier.weight(1f)
        )
    }
}

@Composable
private fun MetricBlock(label: String, value: String, modifier: Modifier = Modifier) {
    Column(modifier = modifier, verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(label, color = AppColors.TextSecondary, style = MaterialTheme.typography.labelMedium)
        Text(
            value,
            color = AppColors.TextPrimary,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.SemiBold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis
        )
    }
}

@Composable
private fun StatusPill(text: String, tone: Color) {
    Surface(color = tone.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(
            text = text,
            color = tone,
            style = MaterialTheme.typography.labelMedium,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp)
        )
    }
}

@Composable
private fun AppTextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    keyboardType: KeyboardType = KeyboardType.Text,
    onValueChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun TimeTextField(
    value: String,
    label: String,
    modifier: Modifier = Modifier,
    onValueChange: (String) -> Unit
) {
    var fieldValue by remember {
        mutableStateOf(TextFieldValue(value, TextRange(value.length)))
    }
    LaunchedEffect(value) {
        if (value != fieldValue.text) {
            fieldValue = TextFieldValue(value, TextRange(value.length))
        }
    }

    OutlinedTextField(
        value = fieldValue,
        onValueChange = { nextValue ->
            val formatted = TimeInputFormatter.format(
                nextValue.text,
                nextValue.selection.start,
                nextValue.selection.end
            )
            val nextFieldValue = TextFieldValue(
                text = formatted.text,
                selection = TextRange(formatted.selectionStart, formatted.selectionEnd)
            )
            fieldValue = nextFieldValue
            if (formatted.text != value) {
                onValueChange(formatted.text)
            }
        },
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Done),
        textStyle = MaterialTheme.typography.bodySmall,
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        shape = RoundedCornerShape(8.dp)
    )
}

@Composable
private fun SwitchRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    warning: Boolean = false,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.weight(1f)) {
            Text(title, color = if (warning) AppColors.Warning else AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}

@Composable
private fun PermissionLine(
    title: String,
    subtitle: String,
    ok: Boolean,
    action: String,
    onAction: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = if (ok) Icons.Filled.CheckCircle else Icons.Filled.Warning,
            contentDescription = null,
            tint = if (ok) AppColors.Success else AppColors.Warning,
            modifier = Modifier.size(22.dp)
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Text(title, color = AppColors.TextPrimary, fontWeight = FontWeight.SemiBold)
            Text(subtitle, color = AppColors.TextSecondary, style = MaterialTheme.typography.bodySmall)
        }
        TextButton(onClick = onAction) {
            Text(action)
        }
    }
}

@Composable
private fun PrimaryIconButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    danger: Boolean = false,
    height: Dp = 40.dp,
    onClick: () -> Unit
) {
    Button(
        onClick = onClick,
        modifier = modifier.height(height),
        shape = RoundedCornerShape(8.dp),
        colors = ButtonDefaults.buttonColors(containerColor = if (danger) AppColors.Danger else AppColors.Primary)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

@Composable
private fun OutlinedIconButton(
    text: String,
    icon: ImageVector,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        enabled = enabled,
        modifier = modifier.height(40.dp),
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(16.dp))
            Spacer(Modifier.width(7.dp))
            Text(text, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
    }
}

private data class TabSpec(val title: String, val icon: ImageVector)

private data class HomeLayoutProfile(
    val compact: Boolean = false,
    val screenHorizontalPadding: Dp = 12.dp,
    val screenVerticalPadding: Dp = 10.dp,
    val sectionSpacing: Dp = 10.dp,
    val cardPadding: Dp = 14.dp,
    val tilePadding: Dp = 12.dp,
    val summaryIconSize: Dp = 40.dp,
    val summaryIconInnerSize: Dp = 22.dp,
    val primaryButtonHeight: Dp = 40.dp,
    val showSummaryMetrics: Boolean = true,
    val showSeparateStatusTiles: Boolean = true,
    val warningCompact: Boolean = false,
    val expandedHome: Boolean = false
)
