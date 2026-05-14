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
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.carinspector.ui.components.*
import com.carinspector.ui.theme.Clr

// ─────────────────────────────────────────────────────────────────────────────
// MODELS
// ─────────────────────────────────────────────────────────────────────────────

enum class ConnectStep { IDLE, SCANNING, FOUND, CONNECTING, CONNECTED, ERROR }

data class FoundDevice(
    val name: String,
    val address: String,
    val rssi: Int,          // signal strength -100..0
    val isKnown: Boolean,   // previously paired
)

data class ConnectUiState(
    val step: ConnectStep = ConnectStep.IDLE,
    val devices: List<FoundDevice> = emptyList(),
    val selectedDevice: FoundDevice? = null,
    val errorMsg: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun ConnectScreen(
    state: ConnectUiState = ConnectUiState(),
    onScan: () -> Unit = {},
    onDevicePick: (FoundDevice) -> Unit = {},
    onRetry: () -> Unit = {},
    onHelpClick: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Clr.Bg)
            .statusBarsPadding(),
    ) {
        Column(
            Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Spacer(Modifier.height(48.dp))

            // ── App name ──────────────────────────────────────────────────
            Text(
                "CarInspector",
                style = MaterialTheme.typography.headlineLarge,
                color = Clr.Teal,
            )
            Spacer(Modifier.height(4.dp))
            Text(
                "Диагностика за 1 кнопку",
                style = MaterialTheme.typography.bodyMedium,
                color = Clr.T3,
            )

            Spacer(Modifier.height(52.dp))

            // ── Central animation area ────────────────────────────────────
            Box(
                Modifier
                    .size(180.dp)
                    .align(Alignment.CenterHorizontally),
                Alignment.Center,
            ) {
                when (state.step) {
                    ConnectStep.IDLE, ConnectStep.FOUND -> BlePulse()
                    ConnectStep.SCANNING               -> BlePulseScanning()
                    ConnectStep.CONNECTING             -> ConnectingSpinner()
                    ConnectStep.CONNECTED              -> ConnectedCheck()
                    ConnectStep.ERROR                  -> ErrorIcon()
                }
            }

            Spacer(Modifier.height(28.dp))

            // ── Status text ───────────────────────────────────────────────
            AnimatedContent(
                targetState = state.step,
                transitionSpec = { fadeIn(tween(300)) togetherWith fadeOut(tween(200)) },
                label = "statusText",
            ) { step ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(
                        text = when (step) {
                            ConnectStep.IDLE       -> "Подключите OBD-адаптер"
                            ConnectStep.SCANNING   -> "Ищем адаптер…"
                            ConnectStep.FOUND      -> "Выберите адаптер"
                            ConnectStep.CONNECTING -> "Подключаемся…"
                            ConnectStep.CONNECTED  -> "Подключено!"
                            ConnectStep.ERROR      -> "Не удалось подключиться"
                        },
                        style = MaterialTheme.typography.headlineSmall,
                        color = Clr.T1,
                        textAlign = TextAlign.Center,
                    )
                    Spacer(Modifier.height(8.dp))
                    Text(
                        text = when (step) {
                            ConnectStep.IDLE       -> "Вставьте адаптер в разъём OBD-II\nпод рулём и включите зажигание"
                            ConnectStep.SCANNING   -> "Убедитесь, что Bluetooth включён"
                            ConnectStep.FOUND      -> "${state.devices.size} устройство(а) найдено"
                            ConnectStep.CONNECTING -> "Отправляем AT-команды инициализации"
                            ConnectStep.CONNECTED  -> "Получаем данные с датчиков"
                            ConnectStep.ERROR      -> state.errorMsg ?: "Проверьте питание адаптера"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = Clr.T3,
                        textAlign = TextAlign.Center,
                        lineHeight = 19.sp,
                    )
                }
            }

            Spacer(Modifier.height(32.dp))

            // ── Device list (FOUND state) ─────────────────────────────────
            AnimatedVisibility(visible = state.step == ConnectStep.FOUND && state.devices.isNotEmpty()) {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    state.devices.forEach { device ->
                        DeviceItem(device = device, onClick = { onDevicePick(device) })
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }

            Spacer(Modifier.weight(1f))

            // ── Primary action button ─────────────────────────────────────
            when (state.step) {
                ConnectStep.IDLE, ConnectStep.ERROR ->
                    PrimaryBtn(
                        text = if (state.step == ConnectStep.ERROR) "Попробовать снова" else "Найти адаптер",
                        onClick = if (state.step == ConnectStep.ERROR) onRetry else onScan,
                    )
                ConnectStep.CONNECTED ->
                    PrimaryBtn("Начать диагностику", onClick = onScan)
                else -> {}
            }

            Spacer(Modifier.height(12.dp))

            // ── Help link ─────────────────────────────────────────────────
            TextButton(onClick = onHelpClick) {
                Text(
                    "Что такое OBD-II?  →",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Clr.T3,
                )
            }

            Spacer(Modifier.height(32.dp))
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DEVICE LIST ITEM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun DeviceItem(device: FoundDevice, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Clr.CardSolid, RoundedCornerShape(14.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Signal bars
            SignalStrength(rssi = device.rssi)
            Spacer(Modifier.width(12.dp))
            Column {
                Text(device.name, style = MaterialTheme.typography.titleMedium, color = Clr.T1)
                Text(device.address, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
        }
        if (device.isKnown) {
            Box(
                Modifier
                    .background(Clr.TealDim, RoundedCornerShape(20.dp))
                    .padding(horizontal = 9.dp, vertical = 3.dp),
            ) {
                Text("знакомый", style = MaterialTheme.typography.labelSmall, color = Clr.Teal)
            }
        } else {
            Text("›", style = MaterialTheme.typography.headlineMedium, color = Clr.T3)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SIGNAL STRENGTH ICON
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SignalStrength(rssi: Int) {
    val bars = when {
        rssi >= -60 -> 4
        rssi >= -70 -> 3
        rssi >= -80 -> 2
        else        -> 1
    }
    Row(
        Modifier.width(22.dp),
        Arrangement.spacedBy(2.dp),
        Alignment.Bottom,
    ) {
        (1..4).forEach { i ->
            Box(
                Modifier
                    .width(4.dp)
                    .height((i * 5 + 2).dp)
                    .background(
                        if (i <= bars) Clr.Teal else Clr.Border,
                        RoundedCornerShape(1.dp),
                    ),
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ANIMATION VARIANTS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun BlePulseScanning() {
    val inf = rememberInfiniteTransition(label = "scan")
    val rotation by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(3000, easing = LinearEasing)),
        label = "rot",
    )
    Box(Modifier.size(148.dp), Alignment.Center) {
        BlePulse()
        androidx.compose.foundation.Canvas(
            Modifier
                .size(148.dp)
                .rotate(rotation),
        ) {
            drawArc(
                color = Clr.Teal.copy(alpha = 0.4f),
                startAngle = 0f,
                sweepAngle = 90f,
                useCenter = false,
                style = Stroke(width = 2.dp.toPx(), cap = StrokeCap.Round),
            )
        }
    }
}

@Composable
private fun ConnectingSpinner() {
    val inf = rememberInfiniteTransition(label = "conn")
    val angle by inf.animateFloat(
        0f, 360f,
        infiniteRepeatable(tween(900, easing = LinearEasing)),
        label = "a",
    )
    Box(Modifier.size(80.dp), Alignment.Center) {
        CircularProgressIndicator(
            modifier = Modifier.size(80.dp),
            color = Clr.Teal,
            trackColor = Clr.Border,
            strokeWidth = 3.dp,
        )
        Text("⟡", fontSize = 28.sp, color = Clr.Teal)
    }
}

@Composable
private fun ConnectedCheck() {
    val scale by animateFloatAsState(
        1f,
        spring(dampingRatio = Spring.DampingRatioMediumBouncy),
        label = "cs",
    )
    Box(
        Modifier
            .size(80.dp)
            .scale(scale)
            .background(Clr.GreenDim, CircleShape)
            .border(1.dp, Clr.Green.copy(alpha = 0.4f), CircleShape),
        Alignment.Center,
    ) {
        Text("✓", fontSize = 32.sp, color = Clr.Green)
    }
}

@Composable
private fun ErrorIcon() {
    Box(
        Modifier
            .size(80.dp)
            .background(Clr.RedDim, CircleShape)
            .border(1.dp, Clr.Red.copy(alpha = 0.4f), CircleShape),
        Alignment.Center,
    ) {
        Text("!", fontSize = 32.sp, color = Clr.Red)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OBD HELP BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OBDHelpSheet(onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Clr.Surface,
        tonalElevation = 0.dp,
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 8.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Clr.Border, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        Column(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp)
                .padding(bottom = 40.dp),
        ) {
            Text("Что такое OBD-II?", style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
            Spacer(Modifier.height(16.dp))

            val steps = listOf(
                "◎" to "OBD-II — стандартный диагностический разъём, который есть в каждом автомобиле с 1996 года",
                "◈" to "Он находится под рулём, чаще всего слева. Выглядит как трапециевидный 16-пиновый разъём",
                "⟡" to "Вставьте Bluetooth-адаптер ELM327 в этот разъём — он продаётся на Wildberries/Ozon за 500–1500 ₽",
                "✦" to "Включите зажигание (не обязательно заводить), откройте CarInspector и нажмите «Найти адаптер»",
            )
            steps.forEach { (icon, text) ->
                Row(
                    Modifier.padding(vertical = 10.dp),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(icon, fontSize = 16.sp, color = Clr.Teal, modifier = Modifier.padding(top = 1.dp))
                    Spacer(Modifier.width(14.dp))
                    Text(text, style = MaterialTheme.typography.bodyMedium, color = Clr.T2, lineHeight = 19.sp)
                }
                if (steps.last().second != text) AppDivider()
            }

            Spacer(Modifier.height(24.dp))
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Clr.AmberDim, RoundedCornerShape(12.dp))
                    .border(0.5.dp, Clr.Amber.copy(alpha = 0.25f), RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                Text(
                    "Рекомендуем адаптеры на базе чипа ELM327 с поддержкой Bluetooth 4.0 (BLE). Wi-Fi адаптеры тоже поддерживаются.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Clr.Amber,
                    lineHeight = 19.sp,
                )
            }
        }
    }
}
