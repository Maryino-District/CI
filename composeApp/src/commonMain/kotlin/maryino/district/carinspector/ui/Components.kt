package com.carinspector.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.*
import com.carinspector.ui.data.*
import com.carinspector.ui.theme.Clr

// ─────────────────────────────────────────────────────────────────────────────
// BOTTOM NAVIGATION
// ─────────────────────────────────────────────────────────────────────────────

enum class NavTab(val label: String, val icon: String) {
    HOME("Главная", "◉"),
    METRICS("Данные", "≋"),
    REPORT("Анализ", "✦"),
    HISTORY("История", "◷"),
    PROFILE("Профиль", "◯"),
}

@Composable
fun BottomBar(selected: NavTab, onSelect: (NavTab) -> Unit) {
    Surface(color = Clr.Surface, tonalElevation = 0.dp) {
        Box(
            Modifier
                .fillMaxWidth()
                .drawBehind {
                    drawLine(Clr.Border, Offset(0f, 0f), Offset(size.width, 0f), 0.5.dp.toPx())
                }
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .navigationBarsPadding()
                    .padding(horizontal = 4.dp, vertical = 6.dp),
                horizontalArrangement = Arrangement.SpaceAround,
            ) {
                NavTab.values().forEach { tab ->
                    val active = tab == selected
                    Column(
                        Modifier
                            .clip(RoundedCornerShape(12.dp))
                            .clickable { onSelect(tab) }
                            .padding(horizontal = 14.dp, vertical = 7.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(3.dp),
                    ) {
                        Text(
                            tab.icon,
                            fontSize = if (active) 19.sp else 17.sp,
                            color = if (active) Clr.Teal else Clr.T3,
                        )
                        Text(
                            tab.label,
                            style = MaterialTheme.typography.labelSmall,
                            color = if (active) Clr.Teal else Clr.T3,
                        )
                    }
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SMALL STATUS CHIP
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StatusChip(status: ParamStatus, modifier: Modifier = Modifier) {
    Box(
        modifier
            .background(status.dimColor(), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp)
    ) {
        Text(
            status.label(),
            style = MaterialTheme.typography.labelSmall,
            color = status.color(),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SECTION LABEL
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier, trailing: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp),
        Arrangement.SpaceBetween, Alignment.CenterVertically,
    ) {
        Text(
            text.uppercase(),
            style = MaterialTheme.typography.labelMedium,
            color = Clr.T3,
            letterSpacing = 1.sp,
        )
        trailing?.invoke()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// DIVIDER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun AppDivider(modifier: Modifier = Modifier) =
    Box(modifier.fillMaxWidth().height(0.5.dp).background(Clr.Divider))

// ─────────────────────────────────────────────────────────────────────────────
// QUICK METRIC CARD  (2×2 grid on home screen)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun QuickMetricCard(param: OBDParam, modifier: Modifier = Modifier) {
    Column(
        modifier
            .background(Clr.CardSolid, RoundedCornerShape(16.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(16.dp))
            .padding(14.dp),
    ) {
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Text(param.short, style = MaterialTheme.typography.labelMedium, color = Clr.T3)
            Box(Modifier.size(6.dp).background(param.status.color(), CircleShape))
        }
        Spacer(Modifier.height(10.dp))
        if (param.value != null) {
            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    param.value.fmt(),
                    style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp),
                    color = Clr.T1,
                )
                if (param.unit.isNotEmpty()) {
                    Spacer(Modifier.width(3.dp))
                    Text(
                        param.unit,
                        style = MaterialTheme.typography.bodySmall,
                        color = Clr.T3,
                        modifier = Modifier.padding(bottom = 4.dp),
                    )
                }
            }
        } else {
            Text("—", style = MaterialTheme.typography.displaySmall.copy(fontSize = 26.sp), color = Clr.T3)
        }
        Spacer(Modifier.height(4.dp))
        Text(param.name, style = MaterialTheme.typography.bodySmall, color = Clr.T3, maxLines = 2, overflow = TextOverflow.Ellipsis)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// OBD DETAIL ROW  (metrics list item)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OBDDetailRow(param: OBDParam, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 13.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        // icon + name
        Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
            Box(
                Modifier
                    .size(36.dp)
                    .background(param.status.dimColor(), RoundedCornerShape(10.dp)),
                Alignment.Center,
            ) { Text(param.category.icon, fontSize = 15.sp) }
            Spacer(Modifier.width(12.dp))
            Column {
                Text(
                    param.name,
                    style = MaterialTheme.typography.titleMedium,
                    color = Clr.T1,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    if (param.normMin != null && param.normMax != null)
                        "Норма: ${param.normMin.fmt()}–${param.normMax.fmt()} ${param.unit}"
                    else "Информационный параметр",
                    style = MaterialTheme.typography.bodySmall,
                    color = Clr.T3,
                )
            }
        }
        Spacer(Modifier.width(10.dp))
        // value + status
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (param.value != null) "${param.value.fmt()} ${param.unit}".trim() else "—",
                style = MaterialTheme.typography.titleMedium,
                color = Clr.T1,
            )
            Spacer(Modifier.height(1.dp))
            Text(param.status.label(), style = MaterialTheme.typography.labelSmall, color = param.status.color())
        }
    }
    AppDivider(Modifier.padding(horizontal = 20.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// OBD PARAMETER FULL CARD  (bottom sheet detail)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun OBDParamDetailCard(param: OBDParam) {
    Column(
        Modifier
            .fillMaxWidth()
            .background(Clr.CardSolid, RoundedCornerShape(20.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(20.dp))
            .padding(20.dp),
    ) {
        // header
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.Top) {
            Column(Modifier.weight(1f)) {
                Text(param.name, style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
                Spacer(Modifier.height(2.dp))
                Text("PID: ${param.pid}  •  ${param.category.title}",
                    style = MaterialTheme.typography.labelSmall, color = Clr.T3)
            }
            StatusChip(param.status)
        }
        Spacer(Modifier.height(20.dp))

        // big value box
        Box(
            Modifier
                .fillMaxWidth()
                .background(Clr.SurfaceHigh, RoundedCornerShape(14.dp))
                .padding(vertical = 22.dp),
            Alignment.Center,
        ) {
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                if (param.value != null) {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            param.value.fmt(),
                            style = MaterialTheme.typography.displayMedium,
                            color = param.status.color(),
                        )
                        if (param.unit.isNotEmpty()) {
                            Spacer(Modifier.width(6.dp))
                            Text(
                                param.unit,
                                style = MaterialTheme.typography.headlineLarge,
                                color = param.status.color().copy(alpha = 0.55f),
                                modifier = Modifier.padding(bottom = 5.dp),
                            )
                        }
                    }
                } else {
                    Text("Нет данных", style = MaterialTheme.typography.headlineMedium, color = Clr.T3)
                }
                if (param.normMin != null && param.normMax != null) {
                    Spacer(Modifier.height(4.dp))
                    Text(
                        "Норма: ${param.normMin.fmt()} – ${param.normMax.fmt()} ${param.unit}",
                        style = MaterialTheme.typography.bodySmall,
                        color = Clr.T2,
                    )
                    Spacer(Modifier.height(14.dp))
                    NormRangeBar(param.value, param.normMin, param.normMax, param.status)
                }
            }
        }
        Spacer(Modifier.height(16.dp))

        // description
        Text("Что это такое", style = MaterialTheme.typography.labelMedium, color = Clr.T3)
        Spacer(Modifier.height(6.dp))
        Text(param.desc, style = MaterialTheme.typography.bodyMedium, color = Clr.T2, lineHeight = 19.sp)
        Spacer(Modifier.height(14.dp))

        // meaning box
        Box(
            Modifier
                .fillMaxWidth()
                .background(Clr.TealGlow, RoundedCornerShape(12.dp))
                .border(0.5.dp, Clr.Teal.copy(alpha = 0.18f), RoundedCornerShape(12.dp))
                .padding(14.dp),
        ) {
            Column {
                Text("Что это значит для вас", style = MaterialTheme.typography.labelMedium, color = Clr.Teal)
                Spacer(Modifier.height(5.dp))
                Text(param.meaning, style = MaterialTheme.typography.bodyMedium, color = Clr.T2, lineHeight = 19.sp)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// NORM RANGE BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun NormRangeBar(value: Float?, min: Float, max: Float, status: ParamStatus, modifier: Modifier = Modifier) {
    if (value == null) return
    val buf = (max - min) * 0.3f
    val lo = min - buf; val hi = max + buf
    val frac    = ((value - lo) / (hi - lo)).coerceIn(0f, 1f)
    val normSt  = (min - lo) / (hi - lo)
    val normEnd = (max - lo) / (hi - lo)
    Canvas(modifier.then(Modifier.fillMaxWidth(0.7f).height(6.dp))) {
        val w = size.width; val h = size.height; val r = h / 2
        drawRoundRect(Clr.Border, size = Size(w, h), cornerRadius = androidx.compose.ui.geometry.CornerRadius(r))
        drawRect(Clr.Green.copy(alpha = 0.22f), Offset(w * normSt, 0f), Size(w * (normEnd - normSt), h))
        val ix = (w * frac).coerceIn(r, w - r)
        drawCircle(status.color(), r * 1.6f, Offset(ix, h / 2f))
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PULSE ANIMATION  (BLE scan)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun BlePulse(modifier: Modifier = Modifier) {
    val inf = rememberInfiniteTransition(label = "ble")
    val p1 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, easing = FastOutSlowInEasing)), label = "p1")
    val p2 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, 500, easing = FastOutSlowInEasing)), label = "p2")
    val p3 by inf.animateFloat(0f, 1f, infiniteRepeatable(tween(2200, 1000, easing = FastOutSlowInEasing)), label = "p3")
    Box(modifier, Alignment.Center) {
        Canvas(Modifier.size(148.dp)) {
            val c = center; val maxR = size.minDimension / 2
            listOf(p1, p2, p3).forEach { p ->
                drawCircle(Clr.Teal.copy(alpha = (1f - p) * 0.22f), maxR * p, c)
            }
            drawCircle(Clr.Teal.copy(alpha = 0.12f), maxR * 0.52f, c)
            drawCircle(Clr.Teal, maxR * 0.36f, c)
        }
        Text("⟡", fontSize = 30.sp, color = Clr.TOnAccent)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HEALTH SCORE CIRCLE
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HealthScoreCircle(score: Int, size: Dp = 140.dp, modifier: Modifier = Modifier) {
    val anim by animateIntAsState(score, tween(1200, easing = FastOutSlowInEasing), label = "score")
    val col = when { score >= 80 -> Clr.Green; score >= 60 -> Clr.Amber; else -> Clr.Red }
    Box(modifier.size(size), Alignment.Center) {
        Canvas(Modifier.matchParentSize()) {
            val stroke = (size.toPx() * 0.058f)
            val r = this.size.minDimension / 2 - stroke / 2
            drawCircle(col.copy(alpha = 0.08f), r + stroke / 2)
            drawArc(Clr.Border, -210f, 240f, false, style = Stroke(stroke, cap = StrokeCap.Round))
            drawArc(col, -210f, 240f * (anim / 100f), false, style = Stroke(stroke, cap = StrokeCap.Round))
        }
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text("$anim", style = MaterialTheme.typography.displayMedium.copy(fontWeight = FontWeight.W200), color = col)
            Text("из 100", style = MaterialTheme.typography.labelMedium, color = Clr.T3)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// STREAMING TEXT  (typing animation)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun StreamingText(text: String, streaming: Boolean) {
    val dots by rememberInfiniteTransition(label = "dt").animateValue(
        0, 3, Int.VectorConverter, infiniteRepeatable(tween(500)), label = "d"
    )
    Column {
        Text(text, style = MaterialTheme.typography.bodyMedium, color = Clr.T2, lineHeight = 20.sp)
        if (streaming) {
            Spacer(Modifier.height(6.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                repeat(3) { i ->
                    Box(Modifier.size(5.dp).background(
                        if (i < dots) Clr.Teal else Clr.Border, CircleShape)
                    )
                }
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// ISSUE ITEM  (in AI report)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun IssueItem(title: String, body: String, severity: ParamStatus) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(severity.dimColor(), RoundedCornerShape(12.dp))
            .padding(14.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 3.dp).size(7.dp).background(severity.color(), CircleShape))
        Spacer(Modifier.width(10.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleMedium, color = Clr.T1)
            Spacer(Modifier.height(2.dp))
            Text(body, style = MaterialTheme.typography.bodySmall, color = Clr.T2, lineHeight = 16.sp)
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PLAN CARD  (paywall)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PlanCard(
    name: String, desc: String, price: String, period: String,
    featured: Boolean = false, badge: String? = null, onClick: () -> Unit,
) {
    val bColor = if (featured) Clr.Teal else Clr.Border
    val bWidth = if (featured) 1.5.dp else 0.5.dp
    Column(
        Modifier
            .fillMaxWidth()
            .background(if (featured) Clr.TealGlow else Clr.CardSolid, RoundedCornerShape(16.dp))
            .border(bWidth, bColor, RoundedCornerShape(16.dp))
            .clickable(onClick = onClick)
            .padding(16.dp),
    ) {
        if (badge != null) {
            Box(Modifier.background(Clr.TealDim, RoundedCornerShape(20.dp)).padding(horizontal = 10.dp, vertical = 3.dp)) {
                Text(badge, style = MaterialTheme.typography.labelSmall, color = Clr.Teal)
            }
            Spacer(Modifier.height(8.dp))
        }
        Row(Modifier.fillMaxWidth(), Arrangement.SpaceBetween, Alignment.CenterVertically) {
            Column {
                Text(name, style = MaterialTheme.typography.titleLarge, color = Clr.T1)
                Text(desc, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(price, style = MaterialTheme.typography.titleLarge, color = if (featured) Clr.Teal else Clr.T1)
                Text(period, style = MaterialTheme.typography.labelSmall, color = Clr.T3)
            }
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HISTORY ITEM
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun HistoryItem(date: String, car: String, score: Int, issues: Int, onClick: () -> Unit) {
    val col = when { score >= 80 -> Clr.Green; score >= 60 -> Clr.Amber; else -> Clr.Red }
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 20.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(44.dp).background(col.copy(alpha = 0.12f), RoundedCornerShape(12.dp)), Alignment.Center) {
            Text("$score", style = MaterialTheme.typography.titleLarge, color = col)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(car, style = MaterialTheme.typography.titleMedium, color = Clr.T1)
            Text(date, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
        }
        Text(
            if (issues > 0) "$issues проблем" else "всё ок",
            style = MaterialTheme.typography.labelSmall,
            color = if (issues > 0) Clr.Amber else Clr.Green,
        )
    }
    AppDivider(Modifier.padding(horizontal = 20.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// BUTTONS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
fun PrimaryBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier, enabled: Boolean = true) {
    Button(
        onClick, modifier.fillMaxWidth().height(54.dp), enabled = enabled,
        shape = RoundedCornerShape(16.dp),
        colors = ButtonDefaults.buttonColors(
            containerColor = Clr.Teal, contentColor = Clr.TOnAccent,
            disabledContainerColor = Clr.Border, disabledContentColor = Clr.T3,
        ),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}

@Composable
fun SecondaryBtn(text: String, onClick: () -> Unit, modifier: Modifier = Modifier) {
    OutlinedButton(
        onClick, modifier.fillMaxWidth().height(48.dp),
        shape = RoundedCornerShape(16.dp),
        border = BorderStroke(0.5.dp, Clr.Border),
        colors = ButtonDefaults.outlinedButtonColors(contentColor = Clr.T2),
    ) { Text(text, style = MaterialTheme.typography.titleMedium) }
}
