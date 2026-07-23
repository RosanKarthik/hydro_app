package com.example

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.WaterDrop
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.receiver.WaterReminderReceiver
import com.example.ui.screen.HistoryScreen
import com.example.ui.screen.HomeScreen
import com.example.ui.screen.LogWaterBottomSheet
import com.example.ui.screen.OnboardingScreen
import com.example.ui.screen.SettingsScreen
import com.example.ui.theme.WaterReminderTheme
import com.example.ui.viewmodel.WaterViewModel
import com.example.worker.AutoCheckoutWorker
import kotlinx.coroutines.launch

enum class MainTab {
    TODAY,
    HISTORY,
    SETTINGS
}

class MainActivity : ComponentActivity() {

    private val viewModel: WaterViewModel by viewModels()

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Schedule Daily Auto-Checkout Worker (§6.2)
        AutoCheckoutWorker.scheduleDailyAutoCheckout(applicationContext)

        val openQuickLogFromNotification = intent?.getBooleanExtra(
            WaterReminderReceiver.EXTRA_OPEN_QUICK_LOG, false
        ) ?: false

        setContent {
            WaterReminderTheme {
                val context = LocalContext.current
                val scope = rememberCoroutineScope()

                // Request POST_NOTIFICATIONS permission on Android 13+
                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { isGranted -> }

                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        if (ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.POST_NOTIFICATIONS
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        }
                    }
                }

                val userProfile by viewModel.userProfile.collectAsState()
                val todayRecord by viewModel.todayRecord.collectAsState()
                val waterLogs by viewModel.todayWaterLogs.collectAsState()
                val recentHistory by viewModel.recentHistory.collectAsState()
                val manualOverrideActive by viewModel.manualOverrideActive.collectAsState()
                val fallbackCheckoutTime by viewModel.fallbackCheckoutTime.collectAsState()

                var currentTab by remember { mutableStateOf(MainTab.TODAY) }
                var showEditingProfileOnboarding by remember { mutableStateOf(false) }

                var showLogWaterSheet by remember { mutableStateOf(openQuickLogFromNotification) }
                val sheetState = rememberModalBottomSheetState()

                // Calculate streak
                val streakDays = remember(recentHistory) {
                    var streak = 0
                    for (record in recentHistory) {
                        if (record.totalConsumedMl >= record.targetMlForDay && record.targetMlForDay > 0) {
                            streak++
                        } else {
                            break
                        }
                    }
                    streak
                }

                val isProfileConfigured = userProfile != null

                if (!isProfileConfigured || showEditingProfileOnboarding) {
                    OnboardingScreen(
                        onCalculateTarget = { weight, activity, climate ->
                            viewModel.calculateBaseTargetMl(weight, activity, climate)
                        },
                        onSaveProfile = { height, weight, gender, activity, climate ->
                            viewModel.saveUserProfile(height, weight, gender, activity, climate) {
                                showEditingProfileOnboarding = false
                            }
                        }
                    )
                } else {
                    Scaffold(
                        modifier = Modifier.fillMaxSize(),
                        bottomBar = {
                            NavigationBar(
                                containerColor = MaterialTheme.colorScheme.surface,
                                modifier = Modifier.testTag("main_navigation_bar")
                            ) {
                                NavigationBarItem(
                                    selected = currentTab == MainTab.TODAY,
                                    onClick = { currentTab = MainTab.TODAY },
                                    icon = { Icon(imageVector = Icons.Default.WaterDrop, contentDescription = "Today") },
                                    label = { Text("Today", fontWeight = FontWeight.Bold) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_today")
                                )

                                NavigationBarItem(
                                    selected = currentTab == MainTab.HISTORY,
                                    onClick = { currentTab = MainTab.HISTORY },
                                    icon = { Icon(imageVector = Icons.Default.History, contentDescription = "History") },
                                    label = { Text("History", fontWeight = FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_history")
                                )

                                NavigationBarItem(
                                    selected = currentTab == MainTab.SETTINGS,
                                    onClick = { currentTab = MainTab.SETTINGS },
                                    icon = { Icon(imageVector = Icons.Default.Settings, contentDescription = "Settings") },
                                    label = { Text("Settings", fontWeight = FontWeight.Medium) },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = MaterialTheme.colorScheme.primary,
                                        selectedTextColor = MaterialTheme.colorScheme.primary
                                    ),
                                    modifier = Modifier.testTag("nav_tab_settings")
                                )
                            }
                        },
                        containerColor = MaterialTheme.colorScheme.background
                    ) { innerPadding ->
                        Crossfade(
                            targetState = currentTab,
                            label = "tab_crossfade",
                            modifier = Modifier.padding(innerPadding)
                        ) { tab ->
                            when (tab) {
                                MainTab.TODAY -> HomeScreen(
                                    todayRecord = todayRecord,
                                    waterLogs = waterLogs,
                                    streakDays = streakDays,
                                    onCheckIn = { viewModel.checkInToday() },
                                    onCheckOut = { viewModel.checkOutToday() },
                                    onLogQuickWater = { amountMl -> viewModel.logWater(amountMl) },
                                    onOpenCustomLogSheet = { showLogWaterSheet = true },
                                    onDeleteLog = { log -> viewModel.deleteWaterLog(log) },
                                    onOpenSettings = { currentTab = MainTab.SETTINGS }
                                )

                                MainTab.HISTORY -> HistoryScreen(
                                    recentHistory = recentHistory
                                )

                                MainTab.SETTINGS -> SettingsScreen(
                                    userProfile = userProfile,
                                    manualOverrideActive = manualOverrideActive,
                                    fallbackCheckoutTime = fallbackCheckoutTime,
                                    onUpdateTarget = { newTargetMl ->
                                        viewModel.updateAdaptiveTarget(newTargetMl)
                                    },
                                    onEditProfile = {
                                        showEditingProfileOnboarding = true
                                    },
                                    onSetManualOverrideActive = { isActive ->
                                        viewModel.setManualOverrideActive(isActive)
                                    },
                                    onUpdateFallbackCheckoutTime = { timeString ->
                                        viewModel.updateFallbackCheckoutTime(timeString)
                                    }
                                )
                            }
                        }

                        if (showLogWaterSheet) {
                            LogWaterBottomSheet(
                                onDismiss = {
                                    scope.launch { sheetState.hide() }.invokeOnCompletion {
                                        showLogWaterSheet = false
                                    }
                                },
                                onLogWater = { amountMl ->
                                    viewModel.logWater(amountMl)
                                },
                                sheetState = sheetState
                            )
                        }
                    }
                }
            }
        }
    }
}
