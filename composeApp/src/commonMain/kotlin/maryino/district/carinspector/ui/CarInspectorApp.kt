package com.carinspector.ui

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import com.carinspector.ui.components.*
import com.carinspector.ui.screens.*
import com.carinspector.ui.theme.CarInspectorTheme

// ─────────────────────────────────────────────────────────────────────────────
// APP ENTRY POINT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun CarInspectorApp() {
    CarInspectorTheme {
        var currentTab by remember { mutableStateOf(NavTab.HOME) }
        var showConnect by remember { mutableStateOf(false) }
        var showReport by remember { mutableStateOf(false) }
        var showPaywall by remember { mutableStateOf(false) }

        // ── Fake home state (replace with real ViewModel) ─────────────────
        val homeState by remember {
            mutableStateOf(HomeUiState(connected = true, liveParams = com.carinspector.ui.data.OBD_PARAMS.take(8)))
        }
        val metricsState by remember { mutableStateOf(MetricsUiState()) }
        val reportState by remember { mutableStateOf(mockReportState) }
        val historyState by remember { mutableStateOf(HistoryUiState(sessions = listOf(
            SessionSummary("1", "сегодня, 09:43",  "Toyota Camry 2019", 87, 2, 42),
            SessionSummary("2", "3 дня назад",      "Toyota Camry 2019", 91, 0, 42),
            SessionSummary("3", "2 недели назад",   "Toyota Camry 2019", 74, 4, 42),
            SessionSummary("4", "1 месяц назад",    "Toyota Camry 2019", 82, 1, 38, locked = true),
        ))) }
        val profileState by remember { mutableStateOf(ProfileUiState()) }
        var metricsDetail by remember { mutableStateOf(metricsState) }

        // Full-screen flows take priority
        when {
            showConnect -> {
                ConnectScreen(
                    state = ConnectUiState(
                        step = ConnectStep.FOUND,
                        devices = listOf(
                            FoundDevice("ELM327 BLE", "AA:BB:CC:DD:EE:FF", -55, true),
                            FoundDevice("OBDII-BLE4", "11:22:33:44:55:66", -72, false),
                        )
                    ),
                    onScan = { showConnect = false },
                    onDevicePick = { showConnect = false },
                )
                return@CarInspectorTheme
            }
            showReport -> {
                ReportScreen(
                    state = reportState,
                    onBack = { showReport = false },
                    onMetricsClick = { showReport = false; currentTab = NavTab.METRICS },
                )
                return@CarInspectorTheme
            }
            showPaywall -> {
                PaywallScreen(
                    onDismiss = { showPaywall = false },
                    onBuySingle = { showPaywall = false },
                    onSubscribeMonthly = { showPaywall = false },
                    onSubscribeYearly = { showPaywall = false },
                )
                return@CarInspectorTheme
            }
        }

        // ── Main scaffold with bottom nav ─────────────────────────────────
        Scaffold(
            containerColor = com.carinspector.ui.theme.Clr.Bg,
            bottomBar = {
                BottomBar(selected = currentTab, onSelect = { currentTab = it })
            },
        ) { padding ->
            Box(Modifier.fillMaxSize().padding(padding)) {
                AnimatedContent(
                    targetState = currentTab,
                    transitionSpec = {
                        fadeIn(tween(220)) togetherWith fadeOut(tween(180))
                    },
                    label = "tab_transition",
                ) { tab ->
                    when (tab) {
                        NavTab.HOME -> HomeScreen(
                            state = homeState,
                            onAnalyzeClick = { showReport = true },
                            onMetricsClick = { currentTab = NavTab.METRICS },
                        )
                        NavTab.METRICS -> MetricsScreen(
                            state = metricsDetail,
                            onSearchChange = { q -> metricsDetail = metricsDetail.copy(search = q) },
                            onCategorySelect = { cat -> metricsDetail = metricsDetail.copy(selectedCategory = cat) },
                            onParamClick = { p -> metricsDetail = metricsDetail.copy(detailParam = p) },
                            onDetailDismiss = { metricsDetail = metricsDetail.copy(detailParam = null) },
                        )
                        NavTab.REPORT -> ReportScreen(
                            state = reportState,
                            onBack = { currentTab = NavTab.HOME },
                            onMetricsClick = { currentTab = NavTab.METRICS },
                        )
                        NavTab.HISTORY -> HistoryScreen(
                            state = historyState,
                            onUpgradeClick = { showPaywall = true },
                        )
                        NavTab.PROFILE -> ProfileScreen(
                            state = profileState,
                            onUpgradeClick = { showPaywall = true },
                        )
                    }
                }
            }
        }
    }
}
