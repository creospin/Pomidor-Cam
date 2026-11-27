package com.example

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Assessment
import androidx.compose.material.icons.outlined.Computer
import androidx.compose.material.icons.outlined.Timer
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Assessment
import androidx.compose.material.icons.rounded.Computer
import androidx.compose.material.icons.rounded.Timer
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationRail
import androidx.compose.material3.NavigationRailItem
import androidx.compose.material3.NavigationRailItemDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Snackbar
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.example.i18n.AppLocaleHelper
import com.example.i18n.LocalIsRussian
import com.example.ui.PomidorViewModel
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.MonitorScreen
import com.example.ui.screens.PcBridgeScreen
import com.example.ui.screens.SettingsScreen
import com.example.ui.theme.BlueGlowSoft
import com.example.ui.theme.CharcoalSurface
import com.example.ui.theme.IceText
import com.example.ui.theme.MutedSlateText
import com.example.ui.theme.ObsidianBg
import com.example.ui.theme.PomidorProTheme
import com.example.ui.theme.SkyBlue
import com.example.ui.theme.SlateCard
import kotlinx.coroutines.launch

private sealed class PomidorRoute(
    val route: String,
    val labelRes: Int,
    val selectedIcon: ImageVector,
    val unselectedIcon: ImageVector,
    val testTag: String
) {
    data object Monitor : PomidorRoute(
        route = "monitor",
        labelRes = R.string.nav_monitor,
        selectedIcon = Icons.Rounded.Timer,
        unselectedIcon = Icons.Outlined.Timer,
        testTag = "nav_monitor"
    )

    data object Journal : PomidorRoute(
        route = "journal",
        labelRes = R.string.nav_journal,
        selectedIcon = Icons.Rounded.Assessment,
        unselectedIcon = Icons.Outlined.Assessment,
        testTag = "nav_journal"
    )

    data object PcBridge : PomidorRoute(
        route = "pc_bridge",
        labelRes = R.string.nav_pc_bridge,
        selectedIcon = Icons.Rounded.Computer,
        unselectedIcon = Icons.Outlined.Computer,
        testTag = "nav_pc_bridge"
    )

    data object Settings : PomidorRoute(
        route = "settings",
        labelRes = R.string.nav_settings,
        selectedIcon = Icons.Rounded.Tune,
        unselectedIcon = Icons.Outlined.Tune,
        testTag = "nav_settings"
    )
}

private val navRoutes = listOf(
    PomidorRoute.Monitor,
    PomidorRoute.Journal,
    PomidorRoute.PcBridge,
    PomidorRoute.Settings
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PomidorProTheme {
                PomidorProApp()
            }
        }
    }
}

@Composable
fun PomidorProApp(
    viewModel: PomidorViewModel = viewModel()
) {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination

    val settings by viewModel.settings.collectAsStateWithLifecycle()
    val availableCameras by viewModel.availableCameras.collectAsStateWithLifecycle()
    val monitorState by viewModel.monitorState.collectAsStateWithLifecycle()
    val selectedWorkday by viewModel.selectedWorkday.collectAsStateWithLifecycle()
    val availableWorkdays by viewModel.availableWorkdays.collectAsStateWithLifecycle()
    val workdaySessions by viewModel.workdaySessions.collectAsStateWithLifecycle()
    val workdaySummary by viewModel.workdaySummary.collectAsStateWithLifecycle()
    val actionBanner by viewModel.actionBanner.collectAsStateWithLifecycle()
    val isBusy by viewModel.isBusy.collectAsStateWithLifecycle()

    val snackbarHostState = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    val isRu = AppLocaleHelper.isRussian(settings.appLanguage)
    val localizedCtx = remember(context, settings.appLanguage) {
        AppLocaleHelper.localizedContext(context, settings.appLanguage)
    }

    LaunchedEffect(actionBanner) {
        val banner = actionBanner ?: return@LaunchedEffect
        snackbarHostState.showSnackbar(banner.message)
        viewModel.dismissBanner()
    }

    val navigateTo: (String) -> Unit = { route ->
        navController.navigate(route) {
            popUpTo(navController.graph.findStartDestination().id) {
                saveState = true
            }
            launchSingleTop = true
            restoreState = true
        }
    }

    CompositionLocalProvider(LocalIsRussian provides isRu) {
        BoxWithConstraints(
            modifier = Modifier
                .fillMaxSize()
                .background(ObsidianBg)
        ) {
            val useRail = maxWidth >= 600.dp

            Scaffold(
                containerColor = ObsidianBg,
                contentWindowInsets = WindowInsets.safeDrawing,
                snackbarHost = {
                    SnackbarHost(hostState = snackbarHostState) { data ->
                        Snackbar(
                            snackbarData = data,
                            containerColor = SlateCard,
                            contentColor = IceText,
                            actionColor = SkyBlue
                        )
                    }
                },
                bottomBar = {
                    if (!useRail) {
                        NavigationBar(
                            containerColor = CharcoalSurface,
                            contentColor = IceText
                        ) {
                            navRoutes.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                val label = localizedCtx.getString(item.labelRes)
                                NavigationBarItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    modifier = Modifier.testTag(item.testTag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = SkyBlue,
                                        selectedTextColor = SkyBlue,
                                        indicatorColor = BlueGlowSoft,
                                        unselectedIconColor = MutedSlateText,
                                        unselectedTextColor = MutedSlateText
                                    )
                                )
                            }
                        }
                    }
                }
            ) { innerPadding ->
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(innerPadding)
                ) {
                    if (useRail) {
                        NavigationRail(
                            containerColor = CharcoalSurface,
                            contentColor = IceText,
                            modifier = Modifier.fillMaxHeight()
                        ) {
                            navRoutes.forEach { item ->
                                val selected = currentDestination?.hierarchy?.any { it.route == item.route } == true
                                val label = localizedCtx.getString(item.labelRes)
                                NavigationRailItem(
                                    selected = selected,
                                    onClick = { navigateTo(item.route) },
                                    modifier = Modifier.testTag(item.testTag),
                                    icon = {
                                        Icon(
                                            imageVector = if (selected) item.selectedIcon else item.unselectedIcon,
                                            contentDescription = label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = label,
                                            style = MaterialTheme.typography.labelSmall
                                        )
                                    },
                                    colors = NavigationRailItemDefaults.colors(
                                        selectedIconColor = SkyBlue,
                                        selectedTextColor = SkyBlue,
                                        indicatorColor = BlueGlowSoft,
                                        unselectedIconColor = MutedSlateText,
                                        unselectedTextColor = MutedSlateText
                                    )
                                )
                            }
                        }
                    }

                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.TopCenter
                ) {
                    NavHost(
                        navController = navController,
                        startDestination = PomidorRoute.Monitor.route,
                        modifier = Modifier
                            .fillMaxSize()
                            .widthIn(max = 720.dp)
                    ) {
                        composable(PomidorRoute.Monitor.route) {
                            MonitorScreen(
                                state = monitorState,
                                settings = settings,
                                availableCameras = availableCameras,
                                onStartMonitoring = viewModel::startMonitoring,
                                onStopMonitoring = viewModel::stopMonitoring,
                                onSelectCameraOption = viewModel::selectCameraOption,
                                onCycleCamera = viewModel::cycleCameraOption,
                                onRefreshCameras = viewModel::refreshAvailableCameras,
                                onTogglePreview = viewModel::setShowLivePreview,
                                onTestTorch = viewModel::testTorchBlink,
                                onTestLanBreak = viewModel::testLanBreakSignal
                            )
                        }
                        composable(PomidorRoute.Journal.route) {
                            JournalScreen(
                                selectedWorkday = selectedWorkday,
                                availableWorkdays = availableWorkdays,
                                sessions = workdaySessions,
                                summary = workdaySummary,
                                settings = settings,
                                isBusy = isBusy,
                                onSelectWorkday = viewModel::selectWorkday,
                                onExportToPc = viewModel::exportSelectedWorkdayToPc,
                                onToggleAutoMorningExport = { enabled ->
                                    viewModel.setAutoMorningExport(enabled, settings.autoMorningExportHour)
                                },
                                onAutoMorningHourChange = { hour ->
                                    viewModel.setAutoMorningExport(settings.autoMorningExportEnabled, hour)
                                },
                                onRunAutoExportYesterdayNow = viewModel::triggerYesterdayAutoExportNow,
                                onBuildReportPayload = viewModel::buildCurrentReportPayload,
                                onSaveTextToUri = viewModel::writeTextToUri,
                                onShareFile = viewModel::createShareIntent,
                                onDeleteSession = viewModel::deleteSession
                            )
                        }
                        composable(PomidorRoute.PcBridge.route) {
                            PcBridgeScreen(
                                settings = settings,
                                lastLanStatus = monitorState.lastLanStatus,
                                pythonScript = viewModel.getPythonScript(isRu),
                                isBusy = isBusy,
                                onSaveEndpoint = viewModel::savePcEndpoint,
                                onPingPc = viewModel::pingPc,
                                onTestBreakOverlay = viewModel::testLanBreakSignal,
                                onSaveTextToUri = viewModel::writeTextToUri,
                                onShareFile = viewModel::createShareIntent,
                                onCopiedNotice = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                        composable(PomidorRoute.Settings.route) {
                            SettingsScreen(
                                settings = settings,
                                availableCameras = availableCameras,
                                torchCapability = viewModel.torchCapability,
                                computeTorchPulseMs = viewModel::computeTorchPulseMs,
                                isBusy = isBusy,
                                onLanguageChange = viewModel::setAppLanguage,
                                onImportModel = viewModel::importYolo26Model,
                                onClearModel = viewModel::clearCustomModel,
                                onDetectorModeChange = viewModel::setDetectorMode,
                                onConfidenceChange = viewModel::setConfidenceThreshold,
                                onFocusMinutesChange = viewModel::setFocusDurationMinutes,
                                onBreakMinutesChange = viewModel::setBreakDurationMinutes,
                                onCheckIntervalChange = viewModel::setCheckIntervalSeconds,
                                onMaxMissedChecksChange = viewModel::setMaxMissedChecks,
                                onWorkdayWindowChange = viewModel::setWorkdayWindow,
                                onDeviceProfileChange = viewModel::setDeviceProfile,
                                onSelectCameraOption = viewModel::selectCameraOption,
                                onRefreshCameras = viewModel::refreshAvailableCameras,
                                onTorchEnabledChange = viewModel::setTorchBlinkEnabled,
                                onTorchCountChange = viewModel::setTorchBlinkCount,
                                onTorchBrightnessChange = viewModel::setTorchBrightnessPercent,
                                onTestTorch = viewModel::testTorchBlink,
                                onCopiedNotice = { msg ->
                                    scope.launch { snackbarHostState.showSnackbar(msg) }
                                }
                            )
                        }
                    }
                }
            }
        }
    }
    }
}
