package com.carinspector.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.carinspector.ui.components.*
import com.carinspector.ui.data.*
import com.carinspector.ui.theme.Clr

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────

data class HomeUiState(
    val connected: Boolean = true,
    val deviceName: String = "ELM327 BLE",
    val carName: String = "Toyota Camry 2019",
    val vin: String = "JT2BF22K1W0123456",
    val quotaUsed: Int = 1,
    val quotaLimit: Int = 3,
    val isSubscribed: Boolean = false,
    val liveParams: List<OBDParam> = emptyList(),
    val dtcCount: Int = 2,
    val lastAnalysisScore: Int? = 87,
)

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HomeScreen(
    state: HomeUiState = HomeUiState(liveParams = quickParams),
    onAnalyzeClick: () -> Unit = {},
    onMetricsClick: () -> Unit = {},
    onParamClick: (OBDParam) -> Unit = {},
    onDisconnect: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Clr.Bg)
            .statusBarsPadding(),
    ) {
        // ── Topbar ────────────────────────────────────────────────────────
        HomeTopBar(
            carName = state.carName,
            deviceName = state.deviceName,
            connected = state.connected,
            onDisconnect = onDisconnect,
        )

        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 24.dp),
        ) {
            // ── Connection status banner ──────────────────────────────────
            if (!state.connected) {
                item { DisconnectedBanner() }
            }

            // ── DTC alerts strip ─────────────────────────────────────────
            if (state.dtcCount > 0) {
                item {
                    DtcAlertStrip(count = state.dtcCount, onClick = onMetricsClick)
                }
            }

            // ── AI Analyse card (main CTA) ────────────────────────────────
            item {
                Spacer(Modifier.height(20.dp))
                AnalyseCard(
                    quotaUsed = state.quotaUsed,
                    quotaLimit = state.quotaLimit,
                    isSubscribed = state.isSubscribed,
                    lastScore = state.lastAnalysisScore,
                    onClick = onAnalyzeClick,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }

            // ── Live metrics 2×2 grid ─────────────────────────────────────
            item { Spacer(Modifier.height(28.dp)) }
            item {
                SectionLabel(
                    text = "Живые данные",
                    trailing = {
                        TextButton(onClick = onMetricsClick, contentPadding = PaddingValues(0.dp)) {
                            Text("Все →", style = MaterialTheme.typography.labelLarge, color = Clr.Teal)
                        }
                    },
                )
            }
            item { Spacer(Modifier.height(10.dp)) }
            item {
                MetricsGrid(params = state.liveParams.take(8), onParamClick = onParamClick)
            }

            // ── Status summary ────────────────────────────────────────────
            item { Spacer(Modifier.height(28.dp)) }
            item { SectionLabel("Общий статус") }
            item { Spacer(Modifier.height(10.dp)) }
            item {
                StatusSummaryRow(
                    ok   = okCount,
                    warn = warnCount,
                    bad  = badCount,
                    modifier = Modifier.padding(horizontal = 20.dp),
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun HomeTopBar(
    carName: String,
    deviceName: String,
    connected: Boolean,
    onDisconnect: () -> Unit,
) {
    Row(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(Clr.Border, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
        Arrangement.SpaceBetween,
        Alignment.CenterVertically,
    ) {
        Column {
            Text("CarInspector", style = MaterialTheme.typography.labelMedium, color = Clr.Teal, letterSpacing = 1.sp)
            Spacer(Modifier.height(1.dp))
            Text(carName, style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
        }
        // Connection pill
        Row(
            Modifier
                .background(
                    if (connected) Clr.GreenDim else Clr.RedDim,
                    RoundedCornerShape(20.dp),
                )
                .clickable(onClick = onDisconnect)
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            // Blinking dot
            val inf = rememberInfiniteTransition(label = "dot")
            val alpha by inf.animateFloat(
                0.3f, 1f,
                infiniteRepeatable(tween(1000), RepeatMode.Reverse),
                label = "a",
            )
            Box(
                Modifier
                    .size(6.dp)
                    .alpha(if (connected) alpha else 1f)
                    .background(if (connected) Clr.Green else Clr.Red, CircleShape),
            )
            Text(
                if (connected) deviceName else "Откл.",
                style = MaterialTheme.typography.labelLarge,
                color = if (connected) Clr.Green else Clr.Red,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DISCONNECTED BANNER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DisconnectedBanner() {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Clr.RedDim)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("!", fontSize = 16.sp, color = Clr.Red)
        Text("Соединение потеряно. Проверьте адаптер.", style = MaterialTheme.typography.bodyMedium, color = Clr.Red)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DTC ALERT STRIP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DtcAlertStrip(count: Int, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .background(Clr.AmberDim)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text("⚠", fontSize = 14.sp, color = Clr.Amber)
            Text(
                "Обнаружено $count кода ошибок DTC",
                style = MaterialTheme.typography.bodyMedium,
                color = Clr.Amber,
            )
        }
        Text("Смотреть →", style = MaterialTheme.typography.labelLarge, color = Clr.Amber)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ANALYSE CARD  (main CTA)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun AnalyseCard(
    quotaUsed: Int,
    quotaLimit: Int,
    isSubscribed: Boolean,
    lastScore: Int?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val quotaLeft = quotaLimit - quotaUsed
    val exhausted = !isSubscribed && quotaLeft <= 0

    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(
                    listOf(Color(0xFF0A1F1E), Color(0xFF071618)),
                ),
                RoundedCornerShape(20.dp),
            )
            .border(
                0.5.dp,
                Clr.Teal.copy(alpha = if (exhausted) 0.15f else 0.3f),
                RoundedCornerShape(20.dp),
            )
            .padding(20.dp),
    ) {
        // Header row
        Row(
            Modifier.fillMaxWidth(),
            Arrangement.SpaceBetween,
            Alignment.CenterVertically,
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                Box(
                    Modifier
                        .background(Clr.TealDim, RoundedCornerShape(8.dp))
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                ) {
                    Text("✦ AI", style = MaterialTheme.typography.labelLarge, color = Clr.Teal)
                }
                Text("Анализ автомобиля", style = MaterialTheme.typography.titleLarge, color = Clr.T1)
            }
            // Last score pill
            if (lastScore != null) {
                val scoreColor = when { lastScore >= 80 -> Clr.Green; lastScore >= 60 -> Clr.Amber; else -> Clr.Red }
                Box(
                    Modifier
                        .background(scoreColor.copy(alpha = 0.12f), RoundedCornerShape(20.dp))
                        .padding(horizontal = 10.dp, vertical = 4.dp),
                ) {
                    Text(
                        "Последний: $lastScore",
                        style = MaterialTheme.typography.labelLarge,
                        color = scoreColor,
                    )
                }
            }
        }

        Spacer(Modifier.height(12.dp))
        Text(
            "Нейросеть проанализирует все ${OBD_PARAMS.size} параметров\nи объяснит состояние вашего авто простым языком",
            style = MaterialTheme.typography.bodyMedium,
            color = Clr.T2,
            lineHeight = 19.sp,
        )

        Spacer(Modifier.height(16.dp))

        // Quota indicator
        if (!isSubscribed) {
            QuotaBar(used = quotaUsed, limit = quotaLimit)
            Spacer(Modifier.height(16.dp))
        }

        // CTA button
        Button(
            onClick = onClick,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            shape = RoundedCornerShape(14.dp),
            enabled = !exhausted,
            colors = ButtonDefaults.buttonColors(
                containerColor = Clr.Teal,
                contentColor = Clr.TOnAccent,
                disabledContainerColor = Clr.Border,
                disabledContentColor = Clr.T3,
            ),
        ) {
            Text(
                if (exhausted) "Купить анализ — 79 ₽" else "Получить AI-анализ",
                style = MaterialTheme.typography.titleMedium,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUOTA BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun QuotaBar(used: Int, limit: Int) {
    val fraction = (used.toFloat() / limit).coerceIn(0f, 1f)
    val col = when {
        fraction < 0.6f -> Clr.Teal
        fraction < 1f   -> Clr.Amber
        else            -> Clr.Red
    }
    Column {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
            Text(
                "Использовано анализов: $used из $limit",
                style = MaterialTheme.typography.bodySmall,
                color = Clr.T3,
            )
            Text(
                "Осталось: ${limit - used}",
                style = MaterialTheme.typography.bodySmall,
                color = col,
            )
        }
        Spacer(Modifier.height(6.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .height(4.dp)
                .background(Clr.Border, RoundedCornerShape(2.dp)),
        ) {
            val animFrac by animateFloatAsState(fraction, tween(800), label = "q")
            Box(
                Modifier
                    .fillMaxHeight()
                    .fillMaxWidth(animFrac)
                    .background(col, RoundedCornerShape(2.dp)),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// METRICS GRID  (2 columns)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricsGrid(params: List<OBDParam>, onParamClick: (OBDParam) -> Unit) {
    val rows = params.chunked(2)
    Column(
        Modifier.padding(horizontal = 20.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        rows.forEach { row ->
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                row.forEach { param ->
                    QuickMetricCard(
                        param = param,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onParamClick(param) },
                    )
                }
                // odd last item filler
                if (row.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STATUS SUMMARY ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun StatusSummaryRow(ok: Int, warn: Int, bad: Int, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        listOf(
            Triple(ok,   Clr.Green, "в норме"),
            Triple(warn, Clr.Amber, "внимание"),
            Triple(bad,  Clr.Red,   "ошибок"),
        ).forEach { (count, color, label) ->
            Column(
                Modifier
                    .weight(1f)
                    .background(color.copy(alpha = 0.08f), RoundedCornerShape(14.dp))
                    .border(0.5.dp, color.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
                    .padding(14.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    "$count",
                    style = MaterialTheme.typography.displaySmall.copy(
                        fontSize = 28.sp,
                        fontWeight = FontWeight.W300,
                    ),
                    color = color,
                )
                Spacer(Modifier.height(2.dp))
                Text(label, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// QUICK PARAMS FOR PREVIEW / HOME GRID  (8 most important)
// ─────────────────────────────────────────────────────────────────────────────

private val quickParams: List<OBDParam> get() = OBD_PARAMS.filter { param ->
    param.pid in listOf("0105", "010C", "0142", "015E", "010D", "0104", "015C", "012F")
}
