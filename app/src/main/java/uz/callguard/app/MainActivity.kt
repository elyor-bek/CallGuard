package uz.callguard.app

import uz.callguard.app.R
import androidx.compose.ui.res.stringResource
import android.Manifest
import android.app.role.RoleManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.Surface
import uz.callguard.app.data.ThemeMode
import uz.callguard.app.ui.UiSettingsViewModel
import uz.callguard.app.ui.AppearanceAndSmsSettings
import androidx.activity.compose.BackHandler
import uz.callguard.app.ui.FeedsScreen
import androidx.activity.SystemBarStyle
import androidx.compose.runtime.SideEffect
import androidx.compose.ui.graphics.toArgb
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.OutlinedButton
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Security
import androidx.compose.material.icons.rounded.Phone
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import uz.callguard.app.ui.ProtectionRulesScreen
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.launch
import uz.callguard.app.data.BlockMode
import uz.callguard.app.data.SettingsRepository
import uz.callguard.app.ui.CallListScreen
import uz.callguard.app.ui.OnboardingScreen
import uz.callguard.app.ui.SpamNumbersScreen
import uz.callguard.app.ui.theme.CallGuardTheme

class MainActivity : AppCompatActivity() {

    private var isRoleGranted by mutableStateOf(false)
    private var roleAvailable by mutableStateOf(false)
    private var requestingRole by mutableStateOf(false)
    private var roleRequestError by mutableStateOf<String?>(null)
    private var canPostNotifications by mutableStateOf(false)
    private var requestingNotifications by mutableStateOf(false)
    private var notificationPermissionDenied by mutableStateOf(false)
    private var pendingRoleRequest = false
    private var isNotificationGranted by mutableStateOf(false)
    private var isOverlayGranted by mutableStateOf(false)
    private var isContactsGranted by mutableStateOf(false)
    private var isBatteryOptIgnored by mutableStateOf(false)

    private val requestRoleLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()
    ) {
        requestingRole = false
        checkAllPermissionsStatus()
        roleRequestError = if (isRoleGranted) null else getString(R.string.role_denied)
    }

    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        requestingNotifications = false
        notificationPermissionDenied = !granted
        checkAllPermissionsStatus()
        if (pendingRoleRequest) {
            pendingRoleRequest = false
            requestCallScreeningRole()
        }
    }

    private val permissionRequestLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.forEach { (perm, granted) ->
            Log.d("CallGuard", "Permission $perm granted: $granted")
        }
        checkAllPermissionsStatus()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        checkAllPermissionsStatus()

        setContent {
            val uiSettings: UiSettingsViewModel = viewModel()
            val uiPreferences by uiSettings.preferences.collectAsStateWithLifecycle()
            val selected = uiPreferences
            LaunchedEffect(selected?.language) {
                if (selected != null) {
                    val target = LocaleListCompat.forLanguageTags(selected.language.tag)
                    if (AppCompatDelegate.getApplicationLocales() != target) {
                        AppCompatDelegate.setApplicationLocales(target)
                    }
                }
            }
            val dark = when (selected?.theme) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                else -> isSystemInDarkTheme()
            }
            CallGuardTheme(darkTheme = dark) {
                val barColor = MaterialTheme.colorScheme.surface.toArgb()
                SideEffect {
                    enableEdgeToEdge(
                        statusBarStyle = SystemBarStyle.auto(barColor, barColor) { dark },
                        navigationBarStyle = SystemBarStyle.auto(barColor, barColor) { dark }
                    )
                }
                if (selected == null) {
                    Surface(Modifier.fillMaxSize()) { }
                    return@CallGuardTheme
                }
                val scope = rememberCoroutineScope()
                val settingsRepository = remember { SettingsRepository(applicationContext) }
                var currentBlockMode by remember { mutableStateOf(BlockMode.WARN) }
                var currentTab by rememberSaveable { mutableStateOf(0) }
                var showFeeds by rememberSaveable { mutableStateOf(false) }
                BackHandler(enabled = currentTab == 3 && showFeeds) { showFeeds = false }
                LaunchedEffect(currentTab) { if (currentTab != 3) showFeeds = false }

                LaunchedEffect(Unit) {
                    currentBlockMode = settingsRepository.getBlockMode()
                }

                var previousRole by rememberSaveable { mutableStateOf(isRoleGranted) }
                LaunchedEffect(isRoleGranted) {
                    if (isRoleGranted && !previousRole) currentTab = 0
                    previousRole = isRoleGranted
                }
                val showOnboarding = !isRoleGranted

                if (showOnboarding) {
                    OnboardingScreen(
                        onRequestRole = { requestProtectionSetup() },
                        overlayGranted = isOverlayGranted,
                        onRequestOverlay = { requestOverlayPermission() },
                        roleAvailable = roleAvailable,
                        requestingRole = requestingRole || requestingNotifications,
                        notificationsGranted = canPostNotifications,
                        notificationsDenied = notificationPermissionDenied,
                        onRequestNotifications = { requestNotifications() },
                        errorMessage = roleRequestError
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar {
                                NavigationBarItem(
                                    selected = currentTab == 0,
                                    onClick = { currentTab = 0 },
                                    label = { Text(getString(R.string.nav_history)) },
                                    icon = { Icon(Icons.Rounded.Phone, contentDescription = null) }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 1,
                                    onClick = { currentTab = 1 },
                                    label = { Text(getString(R.string.nav_spam)) },
                                    icon = { Icon(Icons.Rounded.Security, contentDescription = null) }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 2,
                                    onClick = { currentTab = 2 },
                                    label = { Text(getString(R.string.rules)) },
                                    icon = { Icon(Icons.Rounded.Tune, contentDescription = null) }
                                )
                                NavigationBarItem(
                                    selected = currentTab == 3,
                                    onClick = { currentTab = 3 },
                                    label = { Text(getString(R.string.settings)) },
                                    icon = { Icon(Icons.Rounded.Settings, contentDescription = null) }
                                )
                            }
                        }
                    ) { innerPadding ->
                        Box(modifier = Modifier.padding(innerPadding)) {
                            when (currentTab) {
                                0 -> CallListScreen()
                                1 -> SpamNumbersScreen()
                                2 -> ProtectionRulesScreen()
                                3 -> if (showFeeds) FeedsScreen(onBack = { showFeeds = false }) else SettingsScreen(
                                    onOpenFeeds = { showFeeds = true },
                                    uiSettings = uiSettings,
                                    notificationAccess = isNotificationGranted,
                                    onRequestNotificationAccess = { startActivity(Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)) },
                                    currentBlockMode = currentBlockMode,
                                    notificationsGranted = canPostNotifications,
                                    onRequestNotifications = { requestNotifications() },
                                    overlayGranted = isOverlayGranted,
                                    onRequestOverlay = { requestOverlayPermission() },
                                    isContactsGranted = isContactsGranted,
                                    onRequestContactsPermission = { requestContactsPermission() },
                                    isBatteryOptIgnored = isBatteryOptIgnored,
                                    onRequestBatteryOpt = { requestIgnoreBatteryOptimizations() },
                                    onBlockModeSelected = { mode ->
                                        currentBlockMode = mode
                                        scope.launch { settingsRepository.setBlockMode(mode) }
                                    }
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        checkAllPermissionsStatus()
    }

    private fun checkAllPermissionsStatus() {
        isRoleGranted = isCallScreeningRoleGranted()
        roleAvailable = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            getSystemService(RoleManager::class.java)?.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING) == true
        canPostNotifications = (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED) &&
            NotificationManagerCompat.from(this).areNotificationsEnabled()
        isNotificationGranted = isNotificationAccessGranted()
        isOverlayGranted = Settings.canDrawOverlays(this)
        isContactsGranted = ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED

        val powerManager = getSystemService(Context.POWER_SERVICE) as PowerManager
        isBatteryOptIgnored = powerManager.isIgnoringBatteryOptimizations(packageName)
    }

    fun isCallScreeningRoleGranted(): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val roleManager = getSystemService(RoleManager::class.java)
            return roleManager?.isRoleHeld(RoleManager.ROLE_CALL_SCREENING) == true
        }
        return true
    }

    fun requestCallScreeningRole() {
        if (requestingRole || Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return
        val roleManager = getSystemService(RoleManager::class.java)
        if (roleManager == null || !roleManager.isRoleAvailable(RoleManager.ROLE_CALL_SCREENING)) {
            roleRequestError = getString(R.string.role_missing)
            return
        }
        if (roleManager.isRoleHeld(RoleManager.ROLE_CALL_SCREENING)) {
            checkAllPermissionsStatus()
            return
        }
        try {
            roleRequestError = null
            requestingRole = true
            // Public SDK equivalent of ACTION_REQUEST_ROLE + EXTRA_ROLE_NAME, routed to the system permission controller.
            requestRoleLauncher.launch(roleManager.createRequestRoleIntent(RoleManager.ROLE_CALL_SCREENING))
        } catch (error: Exception) {
            requestingRole = false
            roleRequestError = getString(R.string.role_error)
            Log.e("CallGuard", "Ошибка запроса роли фильтрации звонков", error)
        }
    }

    private fun requestProtectionSetup() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionDenied &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            pendingRoleRequest = true
            requestNotifications()
        } else {
            requestCallScreeningRole()
        }
    }

    private fun requestNotifications() {
        if (requestingNotifications) return
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !notificationPermissionDenied &&
            ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestingNotifications = true
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        } else {
            startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, packageName))
        }
    }

    private fun requestOverlayPermission() {
        startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
    }

    private fun requestContactsPermission() {
        permissionRequestLauncher.launch(arrayOf(Manifest.permission.READ_CONTACTS))
    }

    private fun isNotificationAccessGranted(): Boolean {
        val enabledPackages = NotificationManagerCompat.getEnabledListenerPackages(this)
        return packageName in enabledPackages
    }

    private fun requestIgnoreBatteryOptimizations() {
        try {
            val intent = Intent(Settings.ACTION_IGNORE_BATTERY_OPTIMIZATION_SETTINGS)
            startActivity(intent)
        } catch (e: Exception) {
            Log.e("CallGuard", "Ошибка открытия настроек оптимизации батареи", e)
        }
    }
}

@Composable
fun SettingsScreen(
    onOpenFeeds: () -> Unit,
    uiSettings: UiSettingsViewModel,
    notificationAccess: Boolean,
    onRequestNotificationAccess: () -> Unit,
    currentBlockMode: BlockMode,
    notificationsGranted: Boolean,
    onRequestNotifications: () -> Unit,
    overlayGranted: Boolean,
    onRequestOverlay: () -> Unit,
    isContactsGranted: Boolean,
    onRequestContactsPermission: () -> Unit,
    isBatteryOptIgnored: Boolean,
    onRequestBatteryOpt: () -> Unit,
    onBlockModeSelected: (BlockMode) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Text(
            text = stringResource(R.string.protection_settings),
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(bottom = 16.dp)
        )

        Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.feeds_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(R.string.feeds_settings_hint), style = MaterialTheme.typography.bodyMedium)
                OutlinedButton(onClick = onOpenFeeds) { Text(stringResource(R.string.feeds_manage)) }
            }
        }

        AppearanceAndSmsSettings(uiSettings, notificationAccess, onRequestNotificationAccess)

        Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.overlay_permission_title), style = MaterialTheme.typography.titleMedium)
                Text(stringResource(if (overlayGranted) R.string.overlay_permission_granted else R.string.overlay_permission_hint))
                if (!overlayGranted) OutlinedButton(onClick = onRequestOverlay) { Text(stringResource(R.string.overlay_permission_allow)) }
            }
        }

        Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.silent_notifications), style = MaterialTheme.typography.titleMedium)
                Text(if (notificationsGranted) stringResource(R.string.notifications_enabled)
                    else stringResource(R.string.notifications_disabled))
                OutlinedButton(onClick = onRequestNotifications) {
                    Text(if (notificationsGranted) stringResource(R.string.notification_settings) else stringResource(R.string.notification_allow))
                }
            }
        }

        Card(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.contacts_background), style = MaterialTheme.typography.titleMedium)
                Text(if (isContactsGranted) stringResource(R.string.contacts_allowed)
                    else stringResource(R.string.contacts_hint),
                    style = MaterialTheme.typography.bodyMedium)
                if (!isContactsGranted) Button(onClick = onRequestContactsPermission) { Text(stringResource(R.string.contacts_allow)) }
                if (!isBatteryOptIgnored) OutlinedButton(onClick = onRequestBatteryOpt) { Text(stringResource(R.string.background_settings)) }
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = stringResource(R.string.heuristic_behavior),
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = stringResource(R.string.heuristic_behavior_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp, bottom = 12.dp)
                )


            }
        }
    }
}
