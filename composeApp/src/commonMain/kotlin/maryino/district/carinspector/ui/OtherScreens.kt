package com.carinspector.ui.screens

import androidx.compose.animation.*
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
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import com.carinspector.ui.components.*
import com.carinspector.ui.data.ParamStatus
import com.carinspector.ui.theme.Clr

// ═══════════════════════════════════════════════════════════════════════════════
// HistoryScreen
// ═══════════════════════════════════════════════════════════════════════════════

data class SessionSummary(
    val id: String,
    val date: String,
    val car: String,
    val score: Int,
    val issueCount: Int,
    val paramCount: Int,
    val locked: Boolean = false,
)

data class HistoryUiState(
    val sessions: List<SessionSummary> = emptyList(),
    val isSubscribed: Boolean = false,
    val freeLimit: Int = 3,
)

@Composable
fun HistoryScreen(
    state: HistoryUiState = mockHistoryState,
    onSessionClick: (SessionSummary) -> Unit = {},
    onUpgradeClick: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Clr.Bg)
            .statusBarsPadding(),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        Column(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(Clr.Border, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
                }
                .padding(horizontal = 20.dp, vertical = 16.dp),
        ) {
            Text("История", style = MaterialTheme.typography.headlineMedium, color = Clr.T1)
            Spacer(Modifier.height(2.dp))
            Text(
                "${state.sessions.count { !it.locked }} сессий · все диагностики",
                style = MaterialTheme.typography.bodySmall,
                color = Clr.T3,
            )
        }

        if (state.sessions.isEmpty()) {
            HistoryEmpty()
        } else {
            LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {

                // Trend chart (mini)
                item {
                    Spacer(Modifier.height(16.dp))
                    ScoreTrendCard(
                        sessions = state.sessions.filter { !it.locked },
                        modifier = Modifier.padding(horizontal = 20.dp),
                    )
                    Spacer(Modifier.height(24.dp))
                }

                // Sessions
                item { SectionLabel("Сессии") }
                item { Spacer(Modifier.height(8.dp)) }

                items(state.sessions, key = { it.id }) { session ->
                    if (session.locked) {
                        LockedSessionItem(onClick = onUpgradeClick)
                    } else {
                        SessionItem(session = session, onClick = { onSessionClick(session) })
                    }
                }

                // Upgrade banner
                if (!state.isSubscribed) {
                    item {
                        Spacer(Modifier.height(16.dp))
                        HistoryUpgradeBanner(
                            freeLimit = state.freeLimit,
                            onClick = onUpgradeClick,
                            modifier = Modifier.padding(horizontal = 20.dp),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun HistoryEmpty() {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("◷", fontSize = 40.sp, color = Clr.T3)
        Spacer(Modifier.height(16.dp))
        Text("Нет сохранённых сессий", style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
        Spacer(Modifier.height(6.dp))
        Text(
            "Подключите автомобиль и\nпройдите первую диагностику",
            style = MaterialTheme.typography.bodyMedium,
            color = Clr.T3,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

// ─── Score trend mini chart ────────────────────────────────────────────────

@Composable
private fun ScoreTrendCard(sessions: List<SessionSummary>, modifier: Modifier = Modifier) {
    if (sessions.size < 2) return
    val scores = sessions.reversed().map { it.score }
    val avg = scores.average().toInt()
    val trend = scores.last() - scores.first()

    Column(
        modifier
            .fillMaxWidth()
            .background(Clr.CardSolid, RoundedCornerShape(16.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text("Динамика состояния", style = MaterialTheme.typography.titleMedium, color = Clr.T1)
                Text("За последние ${scores.size} сессий", style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
            Row(
                Modifier
                    .background(
                        if (trend >= 0) Clr.GreenDim else Clr.RedDim,
                        RoundedCornerShape(20.dp),
                    )
                    .padding(horizontal = 10.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                Text(if (trend >= 0) "↑" else "↓", fontSize = 12.sp, color = if (trend >= 0) Clr.Green else Clr.Red)
                Text(
                    "${if (trend >= 0) "+" else ""}$trend пунктов",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (trend >= 0) Clr.Green else Clr.Red,
                )
            }
        }
        Spacer(Modifier.height(16.dp))

        // Mini bar chart
        val maxScore = 100
        Row(
            Modifier.fillMaxWidth().height(60.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.spacedBy(5.dp),
        ) {
            scores.forEach { score ->
                val fraction = score.toFloat() / maxScore
                val col = when { score >= 80 -> Clr.Green; score >= 60 -> Clr.Amber; else -> Clr.Red }
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxHeight(fraction)
                        .background(col.copy(alpha = 0.25f), RoundedCornerShape(4.dp, 4.dp, 2.dp, 2.dp))
                        .border(0.5.dp, col.copy(alpha = 0.4f), RoundedCornerShape(4.dp, 4.dp, 2.dp, 2.dp)),
                )
            }
        }
        Spacer(Modifier.height(6.dp))
        Text("Средний балл: $avg", style = MaterialTheme.typography.bodySmall, color = Clr.T3)
    }
}

// ─── Session items ─────────────────────────────────────────────────────────

@Composable
private fun SessionItem(session: SessionSummary, onClick: () -> Unit) {
    val scoreColor = when { session.score >= 80 -> Clr.Green; session.score >= 60 -> Clr.Amber; else -> Clr.Red }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // Score badge
        Box(
            Modifier
                .size(46.dp)
                .background(scoreColor.copy(alpha = 0.12f), RoundedCornerShape(13.dp))
                .border(0.5.dp, scoreColor.copy(alpha = 0.25f), RoundedCornerShape(13.dp)),
            Alignment.Center,
        ) {
            Text(
                "${session.score}",
                style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.W600),
                color = scoreColor,
            )
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(session.car, style = MaterialTheme.typography.titleMedium, color = Clr.T1)
            Spacer(Modifier.height(2.dp))
            Text(session.date, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (session.issueCount > 0) "${session.issueCount} пробл." else "всё ок",
                style = MaterialTheme.typography.labelLarge,
                color = if (session.issueCount > 0) Clr.Amber else Clr.Green,
            )
            Spacer(Modifier.height(2.dp))
            Text("${session.paramCount} параметров", style = MaterialTheme.typography.bodySmall, color = Clr.T3)
        }
    }
    AppDivider(Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun LockedSessionItem(onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            Modifier
                .size(46.dp)
                .background(Clr.Border, RoundedCornerShape(13.dp)),
            Alignment.Center,
        ) { Text("◫", fontSize = 18.sp, color = Clr.T3) }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text("Сессия скрыта", style = MaterialTheme.typography.titleMedium, color = Clr.T3)
            Text("Подписка открывает всю историю", style = MaterialTheme.typography.bodySmall, color = Clr.T3)
        }
        Box(
            Modifier
                .background(Clr.TealDim, RoundedCornerShape(20.dp))
                .padding(horizontal = 9.dp, vertical = 4.dp),
        ) {
            Text("Открыть →", style = MaterialTheme.typography.labelLarge, color = Clr.Teal)
        }
    }
    AppDivider(Modifier.padding(horizontal = 20.dp))
}

@Composable
private fun HistoryUpgradeBanner(freeLimit: Int, onClick: () -> Unit, modifier: Modifier = Modifier) {
    Row(
        modifier
            .fillMaxWidth()
            .background(Clr.TealGlow, RoundedCornerShape(14.dp))
            .border(0.5.dp, Clr.Teal.copy(alpha = 0.2f), RoundedCornerShape(14.dp))
            .clickable(onClick = onClick)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Text("◫", fontSize = 20.sp, color = Clr.Teal)
        Column(Modifier.weight(1f)) {
            Text("Безлимитная история", style = MaterialTheme.typography.titleMedium, color = Clr.T1)
            Text(
                "Без подписки сохраняются только $freeLimit последних сессии",
                style = MaterialTheme.typography.bodySmall,
                color = Clr.T3,
            )
        }
        Text("→", fontSize = 18.sp, color = Clr.Teal)
    }
}

private val mockHistoryState = HistoryUiState(
    isSubscribed = false,
    sessions = listOf(
        SessionSummary("1", "сегодня, 09:43",      "Toyota Camry 2019", 87, 2, 42),
        SessionSummary("2", "3 дня назад",          "Toyota Camry 2019", 91, 0, 42),
        SessionSummary("3", "2 недели назад",       "Toyota Camry 2019", 74, 4, 42),
        SessionSummary("4", "1 месяц назад",        "Toyota Camry 2019", 82, 1, 38, locked = true),
        SessionSummary("5", "1.5 месяца назад",     "Toyota Camry 2019", 79, 3, 40, locked = true),
    ),
)

// ═══════════════════════════════════════════════════════════════════════════════
// PaywallScreen
// ═══════════════════════════════════════════════════════════════════════════════

enum class PaywallTrigger { QUOTA_EXHAUSTED, HISTORY_LOCKED, PDF_EXPORT, UPGRADE }

data class PaywallUiState(
    val trigger: PaywallTrigger = PaywallTrigger.QUOTA_EXHAUSTED,
    val quotaUsed: Int = 3,
    val quotaLimit: Int = 3,
    val isPurchasing: Boolean = false,
)

@Composable
fun PaywallScreen(
    state: PaywallUiState = PaywallUiState(),
    onBuySingle: () -> Unit = {},
    onSubscribeMonthly: () -> Unit = {},
    onSubscribeYearly: () -> Unit = {},
    onRestore: () -> Unit = {},
    onDismiss: () -> Unit = {},
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(Clr.Bg)
            .statusBarsPadding()
            .padding(horizontal = 24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.height(16.dp))

        // Dismiss button
        Row(Modifier.fillMaxWidth(), Arrangement.End) {
            Box(
                Modifier
                    .size(32.dp)
                    .background(Clr.CardSolid, CircleShape)
                    .border(0.5.dp, Clr.Border, CircleShape)
                    .clickable(onClick = onDismiss),
                Alignment.Center,
            ) { Text("×", fontSize = 16.sp, color = Clr.T3) }
        }

        Spacer(Modifier.height(16.dp))

        // Icon + headline
        Box(
            Modifier
                .size(72.dp)
                .background(Clr.TealDim, RoundedCornerShape(20.dp))
                .border(0.5.dp, Clr.Teal.copy(alpha = 0.3f), RoundedCornerShape(20.dp)),
            Alignment.Center,
        ) { Text("✦", fontSize = 30.sp, color = Clr.Teal) }

        Spacer(Modifier.height(16.dp))

        Text(
            when (state.trigger) {
                PaywallTrigger.QUOTA_EXHAUSTED -> "Бесплатные анализы закончились"
                PaywallTrigger.HISTORY_LOCKED  -> "Откройте всю историю"
                PaywallTrigger.PDF_EXPORT      -> "Экспорт в PDF"
                PaywallTrigger.UPGRADE         -> "CarInspector Pro"
            },
            style = MaterialTheme.typography.headlineSmall,
            color = Clr.T1,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Text(
            when (state.trigger) {
                PaywallTrigger.QUOTA_EXHAUSTED ->
                    "Использовано ${state.quotaUsed} из ${state.quotaLimit} бесплатных анализов.\nКупите разово или оформите подписку."
                PaywallTrigger.HISTORY_LOCKED  ->
                    "Без подписки хранятся только 3 последние сессии."
                PaywallTrigger.PDF_EXPORT      ->
                    "Экспорт PDF входит в подписку\nили доступен разово."
                PaywallTrigger.UPGRADE         ->
                    "Безлимитные анализы, вся история, PDF-экспорт."
            },
            style = MaterialTheme.typography.bodyMedium,
            color = Clr.T3,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )

        Spacer(Modifier.height(28.dp))

        // Features list
        FeatureList()

        Spacer(Modifier.height(24.dp))

        // Plans
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            // Yearly (featured)
            PlanCard(
                name = "Годовая подписка",
                desc = "1 990 ₽ вместо 3 588 ₽  •  скидка 44%",
                price = "166 ₽",
                period = "в месяц",
                featured = true,
                badge = "выгоднее всего",
                onClick = onSubscribeYearly,
            )
            PlanCard(
                name = "Месячная подписка",
                desc = "Безлимит · история · PDF",
                price = "299 ₽",
                period = "в месяц",
                onClick = onSubscribeMonthly,
            )
            PlanCard(
                name = "Один анализ",
                desc = "Без подписки, разово",
                price = "79 ₽",
                period = "однократно",
                onClick = onBuySingle,
            )
        }

        Spacer(Modifier.height(16.dp))

        // Restore + disclaimer
        TextButton(onClick = onRestore) {
            Text(
                "Восстановить покупки",
                style = MaterialTheme.typography.bodyMedium,
                color = Clr.T3,
            )
        }
        Spacer(Modifier.height(4.dp))
        Text(
            "Без автоматического списания · Отмена в любой момент",
            style = MaterialTheme.typography.bodySmall,
            color = Clr.T3,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(24.dp))

        // Loading overlay
        if (state.isPurchasing) {
            Box(
                Modifier.fillMaxWidth().height(54.dp),
                Alignment.Center,
            ) {
                CircularProgressIndicator(color = Clr.Teal, modifier = Modifier.size(28.dp), strokeWidth = 2.5.dp)
            }
        }
    }
}

@Composable
private fun FeatureList() {
    val features = listOf(
        "✦" to "AI-анализ всех 42 параметров OBD",
        "◷" to "Полная история сессий и динамика",
        "⬇" to "Экспорт и пересылка PDF-отчёта",
        "◎" to "Расшифровка DTC кодов ошибок",
    )
    Column(
        Modifier
            .fillMaxWidth()
            .background(Clr.CardSolid, RoundedCornerShape(16.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(16.dp))
            .padding(horizontal = 16.dp, vertical = 4.dp),
    ) {
        features.forEachIndexed { i, (icon, text) ->
            Row(
                Modifier.padding(vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Text(icon, fontSize = 14.sp, color = Clr.Teal)
                Text(text, style = MaterialTheme.typography.bodyMedium, color = Clr.T1)
            }
            if (i < features.lastIndex) AppDivider()
        }
    }
}

// ═══════════════════════════════════════════════════════════════════════════════
// ProfileScreen
// ═══════════════════════════════════════════════════════════════════════════════

data class ProfileUiState(
    val isSignedIn: Boolean = true,
    val displayName: String = "Алексей",
    val email: String = "alex@mail.ru",
    val avatarInitial: String = "А",
    val carMake: String = "Toyota",
    val carModel: String = "Camry",
    val carYear: String = "2019",
    val vin: String = "JT2BF22K1W0123456",
    val subscriptionType: String = "Бесплатный",   // or "Месячная", "Годовая"
    val quotaUsed: Int = 1,
    val quotaLimit: Int = 3,
    val isSubscribed: Boolean = false,
    val appVersion: String = "1.0.0",
)

@Composable
fun ProfileScreen(
    state: ProfileUiState = ProfileUiState(),
    onSignIn: () -> Unit = {},
    onSignOut: () -> Unit = {},
    onCarEdit: () -> Unit = {},
    onUpgradeClick: () -> Unit = {},
    onPrivacyClick: () -> Unit = {},
    onSupportClick: () -> Unit = {},
) {
    LazyColumn(
        Modifier
            .fillMaxSize()
            .background(Clr.Bg)
            .statusBarsPadding(),
        contentPadding = PaddingValues(bottom = 36.dp),
    ) {
        // ── Header ────────────────────────────────────────────────────────
        item {
            Column(
                Modifier
                    .fillMaxWidth()
                    .drawBehind {
                        drawLine(Clr.Border, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
                    }
                    .padding(horizontal = 20.dp, vertical = 16.dp),
            ) {
                Text("Профиль", style = MaterialTheme.typography.headlineMedium, color = Clr.T1)
            }
        }

        // ── Account card ──────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(16.dp))
            AccountCard(
                state = state,
                onSignIn = onSignIn,
                onSignOut = onSignOut,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ── Subscription card ─────────────────────────────────────────────
        item {
            Spacer(Modifier.height(12.dp))
            SubscriptionCard(
                type = state.subscriptionType,
                isSubscribed = state.isSubscribed,
                quotaUsed = state.quotaUsed,
                quotaLimit = state.quotaLimit,
                onUpgrade = onUpgradeClick,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ── Car profile ───────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Мой автомобиль")
            Spacer(Modifier.height(8.dp))
            CarProfileCard(
                make = state.carMake,
                model = state.carModel,
                year = state.carYear,
                vin = state.vin,
                onEdit = onCarEdit,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
        }

        // ── Menu items ────────────────────────────────────────────────────
        item {
            Spacer(Modifier.height(20.dp))
            SectionLabel("Ещё")
            Spacer(Modifier.height(8.dp))
        }
        item {
            ProfileMenuItem(icon = "◎", label = "Поддержка", onClick = onSupportClick)
            AppDivider(Modifier.padding(horizontal = 20.dp))
            ProfileMenuItem(icon = "◫", label = "Политика конфиденциальности", onClick = onPrivacyClick)
            AppDivider(Modifier.padding(horizontal = 20.dp))
            ProfileMenuItem(icon = "⟡", label = "Версия приложения", trailing = "v${state.appVersion}", onClick = {})
        }

        // ── Sign out ──────────────────────────────────────────────────────
        if (state.isSignedIn) {
            item {
                Spacer(Modifier.height(24.dp))
                TextButton(
                    onClick = onSignOut,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 20.dp),
                ) {
                    Text("Выйти из аккаунта", style = MaterialTheme.typography.bodyMedium, color = Clr.Red)
                }
            }
        }
    }
}

// ─── Account card ──────────────────────────────────────────────────────────

@Composable
private fun AccountCard(state: ProfileUiState, onSignIn: () -> Unit, onSignOut: () -> Unit, modifier: Modifier = Modifier) {
    if (state.isSignedIn) {
        Row(
            modifier
                .fillMaxWidth()
                .background(Clr.CardSolid, RoundedCornerShape(16.dp))
                .border(0.5.dp, Clr.Border, RoundedCornerShape(16.dp))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            // Avatar
            Box(
                Modifier
                    .size(48.dp)
                    .background(Clr.TealDim, CircleShape)
                    .border(1.dp, Clr.Teal.copy(alpha = 0.3f), CircleShape),
                Alignment.Center,
            ) {
                Text(state.avatarInitial, style = MaterialTheme.typography.headlineSmall, color = Clr.Teal)
            }
            Column(Modifier.weight(1f)) {
                Text(state.displayName, style = MaterialTheme.typography.titleLarge, color = Clr.T1)
                Text(state.email, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
        }
    } else {
        Column(
            modifier
                .fillMaxWidth()
                .background(Clr.CardSolid, RoundedCornerShape(16.dp))
                .border(0.5.dp, Clr.Border, RoundedCornerShape(16.dp))
                .padding(20.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text("Войдите для синхронизации", style = MaterialTheme.typography.titleMedium, color = Clr.T1)
            Spacer(Modifier.height(4.dp))
            Text("История и анализы сохранятся в облаке", style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            Spacer(Modifier.height(14.dp))
            PrimaryBtn("Войти через Google / Apple", onClick = onSignIn)
        }
    }
}

// ─── Subscription card ─────────────────────────────────────────────────────

@Composable
private fun SubscriptionCard(
    type: String, isSubscribed: Boolean,
    quotaUsed: Int, quotaLimit: Int,
    onUpgrade: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(
                if (isSubscribed) Clr.TealGlow else Clr.CardSolid,
                RoundedCornerShape(16.dp),
            )
            .border(
                0.5.dp,
                if (isSubscribed) Clr.Teal.copy(alpha = 0.3f) else Clr.Border,
                RoundedCornerShape(16.dp),
            )
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(
                    if (isSubscribed) "✦ Pro подписка" else "Бесплатный тариф",
                    style = MaterialTheme.typography.titleLarge,
                    color = if (isSubscribed) Clr.Teal else Clr.T1,
                )
                Text(type, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
            if (!isSubscribed) {
                Box(
                    Modifier
                        .background(Clr.Teal, RoundedCornerShape(20.dp))
                        .clickable(onClick = onUpgrade)
                        .padding(horizontal = 14.dp, vertical = 7.dp),
                ) {
                    Text("Upgrade", style = MaterialTheme.typography.labelLarge, color = Clr.TOnAccent)
                }
            }
        }
        if (!isSubscribed) {
            Spacer(Modifier.height(12.dp))
            // Quota mini bar
            Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween) {
                Text("Анализов: $quotaUsed / $quotaLimit", style = MaterialTheme.typography.bodySmall, color = Clr.T3)
                Text("Осталось: ${quotaLimit - quotaUsed}", style = MaterialTheme.typography.bodySmall, color = Clr.Teal)
            }
            Spacer(Modifier.height(6.dp))
            val frac = quotaUsed.toFloat() / quotaLimit
            Box(Modifier.fillMaxWidth().height(4.dp).background(Clr.Border, RoundedCornerShape(2.dp))) {
                val animFrac by animateFloatAsState(frac, label = "q")
                Box(Modifier.fillMaxHeight().fillMaxWidth(animFrac).background(Clr.Teal, RoundedCornerShape(2.dp)))
            }
        }
    }
}

// ─── Car profile card ──────────────────────────────────────────────────────

@Composable
private fun CarProfileCard(
    make: String, model: String, year: String, vin: String,
    onEdit: () -> Unit, modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .background(Clr.CardSolid, RoundedCornerShape(16.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(16.dp))
            .padding(16.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text("$make $model · $year", style = MaterialTheme.typography.titleLarge, color = Clr.T1)
            Box(
                Modifier
                    .background(Clr.CardSolid, RoundedCornerShape(8.dp))
                    .border(0.5.dp, Clr.Border, RoundedCornerShape(8.dp))
                    .clickable(onClick = onEdit)
                    .padding(horizontal = 10.dp, vertical = 5.dp),
            ) {
                Text("Изменить", style = MaterialTheme.typography.labelLarge, color = Clr.T2)
            }
        }
        Spacer(Modifier.height(10.dp))
        AppDivider()
        Spacer(Modifier.height(10.dp))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("VIN", style = MaterialTheme.typography.labelMedium, color = Clr.T3)
            Text(vin, style = MaterialTheme.typography.bodySmall, color = Clr.T2, letterSpacing = 0.8.sp)
        }
        Spacer(Modifier.height(8.dp))
        Text(
            "Марка и модель влияют на нормы AI-анализа",
            style = MaterialTheme.typography.bodySmall,
            color = Clr.T3,
        )
    }
}

// ─── Generic menu item ─────────────────────────────────────────────────────

@Composable
private fun ProfileMenuItem(icon: String, label: String, trailing: String? = null, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        Text(icon, fontSize = 16.sp, color = Clr.T3)
        Text(label, style = MaterialTheme.typography.bodyLarge, color = Clr.T1, modifier = Modifier.weight(1f))
        Text(trailing ?: "›", style = MaterialTheme.typography.bodyMedium, color = Clr.T3)
    }
}
