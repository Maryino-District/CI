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
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.carinspector.ui.components.*
import com.carinspector.ui.data.*
import com.carinspector.ui.theme.Clr

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────

enum class ReportPhase { LOADING, STREAMING, DONE, ERROR }

data class ReportIssue(
    val title: String,
    val body: String,
    val severity: ParamStatus,
    val action: String,   // short CTA e.g. "Проверить у механика"
)

data class ReportUiState(
    val phase: ReportPhase = ReportPhase.LOADING,
    val streamedText: String = "",
    val healthScore: Int = 0,
    val carName: String = "Toyota Camry 2019",
    val scannedAt: String = "сегодня, 09:43",
    val paramCount: Int = 42,
    val issues: List<ReportIssue> = emptyList(),
    val recommendations: List<String> = emptyList(),
    val errorMsg: String? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Preview
@Composable
fun ReportScreen(
    state: ReportUiState = mockReportState,
    onBack: () -> Unit = {},
    onExportPdf: () -> Unit = {},
    onShare: () -> Unit = {},
    onRetry: () -> Unit = {},
    onMetricsClick: () -> Unit = {},
) {
    Box(
        Modifier
            .fillMaxSize()
            .background(Clr.Bg)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Top bar ───────────────────────────────────────────────────
            ReportTopBar(
                carName = state.carName,
                date = state.scannedAt,
                phase = state.phase,
                onBack = onBack,
            )

            when (state.phase) {
                ReportPhase.LOADING   -> LoadingState()
                ReportPhase.ERROR     -> ErrorState(msg = state.errorMsg, onRetry = onRetry)
                ReportPhase.STREAMING,
                ReportPhase.DONE      -> ReportContent(
                    state = state,
                    onExportPdf = onExportPdf,
                    onShare = onShare,
                    onMetricsClick = onMetricsClick,
                )
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// TOP BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReportTopBar(
    carName: String,
    date: String,
    phase: ReportPhase,
    onBack: () -> Unit,
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
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(Clr.CardSolid, RoundedCornerShape(10.dp))
                    .border(0.5.dp, Clr.Border, RoundedCornerShape(10.dp))
                    .clickable(onClick = onBack),
                Alignment.Center,
            ) { Text("←", fontSize = 16.sp, color = Clr.T2) }
            Column {
                Text(carName, style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
                Text(date, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
        }
        if (phase == ReportPhase.STREAMING) {
            Row(
                Modifier
                    .background(Clr.TealDim, RoundedCornerShape(20.dp))
                    .padding(horizontal = 10.dp, vertical = 5.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                val inf = rememberInfiniteTransition(label = "ai")
                val alpha by inf.animateFloat(
                    0.3f, 1f,
                    infiniteRepeatable(tween(800), RepeatMode.Reverse),
                    label = "a",
                )
                Box(Modifier.size(6.dp).alpha(alpha).background(Clr.Teal, CircleShape))
                Text("AI анализирует", style = MaterialTheme.typography.labelLarge, color = Clr.Teal)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// LOADING STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun LoadingState() {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        // Animated rings
        val inf = rememberInfiniteTransition(label = "load")
        val rot by inf.animateFloat(
            0f, 360f,
            infiniteRepeatable(tween(1800, easing = LinearEasing)),
            label = "r",
        )
        Box(Modifier.size(80.dp), Alignment.Center) {
            androidx.compose.foundation.Canvas(Modifier.size(80.dp).rotate(rot)) {
                drawArc(
                    Brush.sweepGradient(listOf(Clr.Teal, Color.Transparent)),
                    startAngle = 0f, sweepAngle = 270f, useCenter = false,
                    style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round),
                )
            }
            Text("✦", fontSize = 26.sp, color = Clr.Teal)
        }

        Spacer(Modifier.height(28.dp))
        Text("Нейросеть анализирует", style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
        Spacer(Modifier.height(8.dp))

        val steps = listOf(
            "Считываем данные датчиков",
            "Выявляем корреляции",
            "Формируем рекомендации",
        )
        val step by inf.animateValue(
            0, steps.size - 1, Int.VectorConverter,
            infiniteRepeatable(tween(1200), RepeatMode.Restart),
            label = "s",
        )
        Text(
            steps[step],
            style = MaterialTheme.typography.bodyMedium,
            color = Clr.T3,
            textAlign = TextAlign.Center,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ERROR STATE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ErrorState(msg: String?, onRetry: () -> Unit) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(72.dp)
                .background(Clr.RedDim, CircleShape)
                .border(0.5.dp, Clr.Red.copy(alpha = 0.3f), CircleShape),
            Alignment.Center,
        ) { Text("!", fontSize = 28.sp, color = Clr.Red) }
        Spacer(Modifier.height(20.dp))
        Text("Не удалось получить анализ", style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
        Spacer(Modifier.height(8.dp))
        Text(
            msg ?: "Проверьте подключение к интернету",
            style = MaterialTheme.typography.bodyMedium,
            color = Clr.T3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(32.dp))
        PrimaryBtn("Попробовать снова", onClick = onRetry, modifier = Modifier.fillMaxWidth(0.6f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MAIN REPORT CONTENT
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ReportContent(
    state: ReportUiState,
    onExportPdf: () -> Unit,
    onShare: () -> Unit,
    onMetricsClick: () -> Unit,
) {
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(bottom = 36.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp),
    ) {

        // ── Score + summary card ──────────────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            ScoreAndSummaryCard(
                score = state.healthScore,
                text = state.streamedText,
                streaming = state.phase == ReportPhase.STREAMING,
                paramCount = state.paramCount,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ── Issues ────────────────────────────────────────────────────────
        if (state.issues.isNotEmpty() || state.phase == ReportPhase.DONE) {
            item {
                Spacer(Modifier.height(28.dp))
                SectionLabel(
                    text = "На что обратить внимание",
                    trailing = {
                        Text(
                            "${state.issues.size} пункта",
                            style = MaterialTheme.typography.labelLarge,
                            color = Clr.T3,
                        )
                    },
                )
                Spacer(Modifier.height(10.dp))
            }
            items(state.issues) { issue ->
                Column(Modifier.padding(horizontal = 20.dp)) {
                    IssueItem(title = issue.title, body = issue.body, severity = issue.severity)
                    Spacer(Modifier.height(8.dp))
                    // Action button inside issue
                    if (state.phase == ReportPhase.DONE) {
                        Box(
                            Modifier
                                .background(Clr.SurfaceHigh, RoundedCornerShape(0.dp, 0.dp, 10.dp, 10.dp))
                                .padding(horizontal = 14.dp, vertical = 8.dp),
                        ) {
                            Text(
                                "→ ${issue.action}",
                                style = MaterialTheme.typography.labelLarge,
                                color = Clr.T2,
                            )
                        }
                    }
                    Spacer(Modifier.height(8.dp))
                }
            }
        }

        // ── Recommendations ───────────────────────────────────────────────
        if (state.recommendations.isNotEmpty()) {
            item {
                Spacer(Modifier.height(16.dp))
                SectionLabel("Рекомендации")
                Spacer(Modifier.height(10.dp))
            }
            items(state.recommendations) { rec ->
                RecommendationItem(text = rec, modifier = Modifier.padding(horizontal = 20.dp))
                Spacer(Modifier.height(6.dp))
            }
        }

        // ── Export / share row ────────────────────────────────────────────
        item {
            Spacer(Modifier.height(28.dp))
            ExportRow(
                onExportPdf = onExportPdf,
                onShare = onShare,
                onMetricsClick = onMetricsClick,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ── Disclaimer ────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            Text(
                "Анализ сформирован ИИ на основе данных OBD-II. Для точной диагностики обратитесь в сервис.",
                style = MaterialTheme.typography.bodySmall,
                color = Clr.T3,
                textAlign = TextAlign.Center,
                modifier = Modifier.padding(horizontal = 32.dp),
                lineHeight = 15.sp,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SCORE + SUMMARY CARD
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ScoreAndSummaryCard(
    score: Int,
    text: String,
    streaming: Boolean,
    paramCount: Int,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                Brush.linearGradient(listOf(Color(0xFF0A1F1E), Color(0xFF071618))),
                RoundedCornerShape(20.dp),
            )
            .border(0.5.dp, Clr.Teal.copy(alpha = 0.25f), RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        // AI badge
        Row(
            Modifier
                .background(Clr.TealDim, RoundedCornerShape(20.dp))
                .padding(horizontal = 10.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            Text("✦", fontSize = 11.sp, color = Clr.Teal)
            Text("AI-анализ · $paramCount параметров", style = MaterialTheme.typography.labelLarge, color = Clr.Teal)
        }

        Spacer(Modifier.height(20.dp))

        // Score + text side by side (score only when done/streaming with score > 0)
        if (score > 0) {
            Row(
                Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(20.dp),
            ) {
                HealthScoreCircle(score = score, size = 110.dp)
                Column(Modifier.weight(1f)) {
                    val verdict = when {
                        score >= 80 -> "Автомобиль в хорошем состоянии"
                        score >= 60 -> "Требует внимания"
                        else        -> "Нужна диагностика в сервисе"
                    }
                    val verdictColor = when {
                        score >= 80 -> Clr.Green
                        score >= 60 -> Clr.Amber
                        else        -> Clr.Red
                    }
                    Text(verdict, style = MaterialTheme.typography.titleLarge, color = verdictColor)
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "на основе корреляционного\nанализа всех датчиков",
                        style = MaterialTheme.typography.bodySmall,
                        color = Clr.T3,
                        lineHeight = 15.sp,
                    )
                }
            }
            Spacer(Modifier.height(20.dp))
        }

        // Streaming / done summary text
        if (text.isNotEmpty() || streaming) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .background(Clr.SurfaceHigh, RoundedCornerShape(12.dp))
                    .padding(14.dp),
            ) {
                StreamingText(text = text, streaming = streaming)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// RECOMMENDATION ITEM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun RecommendationItem(text: String, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Clr.CardSolid, RoundedCornerShape(12.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Text("◎", fontSize = 13.sp, color = Clr.Teal, modifier = Modifier.padding(top = 1.dp))
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Clr.T2, lineHeight = 19.sp)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EXPORT / SHARE ROW
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ExportRow(
    onExportPdf: () -> Unit,
    onShare: () -> Unit,
    onMetricsClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            // PDF
            OutlinedButton(
                onClick = onExportPdf,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Clr.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Clr.T2),
            ) {
                Text("⬇ PDF", style = MaterialTheme.typography.titleSmall)
            }
            // Share
            OutlinedButton(
                onClick = onShare,
                modifier = Modifier.weight(1f).height(48.dp),
                shape = RoundedCornerShape(14.dp),
                border = BorderStroke(0.5.dp, Clr.Border),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Clr.T2),
            ) {
                Text("⤴ Поделиться", style = MaterialTheme.typography.titleSmall)
            }
        }
        // Full metrics button
        OutlinedButton(
            onClick = onMetricsClick,
            modifier = Modifier.fillMaxWidth().height(48.dp),
            shape = RoundedCornerShape(14.dp),
            border = BorderStroke(0.5.dp, Clr.Border),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = Clr.T2),
        ) {
            Text("Смотреть все параметры →", style = MaterialTheme.typography.titleSmall)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// MOCK DATA
// ─────────────────────────────────────────────────────────────────────────────

val mockReportState = ReportUiState(
    phase = ReportPhase.DONE,
    healthScore = 87,
    carName = "Toyota Camry 2019",
    scannedAt = "сегодня, 09:43",
    paramCount = 42,
    streamedText = "Двигатель работает стабильно, температурный режим в норме. " +
        "Топливная система функционирует корректно — коэффициент λ близок к 1.0. " +
        "Обнаружено снижение эффективности катализатора и незначительное падение " +
        "давления масла — некритично, но стоит отследить на следующем ТО.",
    issues = listOf(
        ReportIssue(
            "P0420 — снижена эффективность катализатора",
            "Датчик после катализатора показывает нестабильный сигнал. Катализатор работает на 87% от нормы.",
            ParamStatus.WARN,
            "Проверить на следующем ТО",
        ),
        ReportIssue(
            "Давление масла ниже нормы",
            "Текущее значение 1.8 бар при норме 2.0–5.0 бар. Возможно, уровень масла низкий.",
            ParamStatus.WARN,
            "Проверить уровень масла щупом",
        ),
        ReportIssue(
            "LTFT B1 на верхней границе",
            "Долгосрочная коррекция топлива +4.7% — пока в норме, но тенденция к обеднению смеси.",
            ParamStatus.OK,
            "Наблюдать динамику",
        ),
    ),
    recommendations = listOf(
        "Проверить уровень моторного масла щупом — возможно, нужно долить 0.3–0.5 л",
        "На следующем ТО попросить проверить катализатор и лямбда-зонд банка 1",
        "Заменить воздушный фильтр — повышенная нагрузка MAF при нормальном дросселе",
        "Плановая замена масла через ≈3 000 км согласно регламенту",
    ),
)
