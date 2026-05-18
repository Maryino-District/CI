package com.carinspector.ui.screens

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.foundation.text.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.*
import androidx.compose.ui.focus.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.*
import com.carinspector.ui.components.*
import com.carinspector.ui.data.*
import com.carinspector.ui.theme.Clr

// ─────────────────────────────────────────────────────────────────────────────
// UI STATE
// ─────────────────────────────────────────────────────────────────────────────

data class MetricsUiState(
    val params: List<OBDParam> = OBD_PARAMS,
    val search: TextFieldValue = TextFieldValue(""),
    val selectedCategory: ParamCategory? = null,
    val detailParam: OBDParam? = null,
)

// ─────────────────────────────────────────────────────────────────────────────
// SCREEN
// ─────────────────────────────────────────────────────────────────────────────

@Preview
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MetricsScreen(
    state: MetricsUiState = MetricsUiState(),
    onSearchChange: (TextFieldValue) -> Unit = {},
    onCategorySelect: (ParamCategory?) -> Unit = {},
    onParamClick: (OBDParam) -> Unit = {},
    onDetailDismiss: () -> Unit = {},
) {
    // Filtered params
    val filtered = remember(state.search, state.selectedCategory, state.params) {
        state.params
            .let { list ->
                val q = state.search.text.trim().lowercase()
                if (q.isEmpty()) list else list.filter {
                    it.name.lowercase().contains(q) ||
                    it.short.lowercase().contains(q) ||
                    it.pid.lowercase().contains(q)
                }
            }
            .let { list ->
                if (state.selectedCategory != null)
                    list.filter { it.category == state.selectedCategory }
                else list
            }
    }

    Box(
        Modifier
            .fillMaxSize()
            .background(Clr.Bg)
            .statusBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {

            // ── Header ────────────────────────────────────────────────────
            MetricsHeader()

            // ── Search bar ────────────────────────────────────────────────
            SearchBar(
                value = state.search,
                onValueChange = onSearchChange,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 12.dp),
            )

            // ── Category filter chips ─────────────────────────────────────
            CategoryChips(
                selected = state.selectedCategory,
                onSelect = onCategorySelect,
            )

            Spacer(Modifier.height(4.dp))

            // ── Param list ────────────────────────────────────────────────
            if (filtered.isEmpty()) {
                EmptySearch(query = state.search.text)
            } else {
                ParamList(
                    params = filtered,
                    groupByCategory = state.selectedCategory == null && state.search.text.isBlank(),
                    onParamClick = onParamClick,
                )
            }
        }

        // ── Detail bottom sheet ───────────────────────────────────────────
        if (state.detailParam != null) {
            ParamDetailSheet(
                param = state.detailParam,
                onDismiss = onDetailDismiss,
            )
        }
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// HEADER
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun MetricsHeader() {
    Column(
        Modifier
            .fillMaxWidth()
            .drawBehind {
                drawLine(Clr.Border, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
            }
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Text("Параметры", style = MaterialTheme.typography.headlineMedium, color = Clr.T1)
        Spacer(Modifier.height(2.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            StatPill("${OBD_PARAMS.size} всего", Clr.T3)
            StatPill("$okCount в норме", Clr.Green)
            if (warnCount > 0) StatPill("$warnCount внимание", Clr.Amber)
            if (badCount  > 0) StatPill("$badCount ошибок",   Clr.Red)
        }
    }
}

@Composable
private fun StatPill(text: String, color: Color) {
    Box(
        Modifier
            .background(color.copy(alpha = 0.1f), RoundedCornerShape(20.dp))
            .padding(horizontal = 9.dp, vertical = 3.dp),
    ) {
        Text(text, style = MaterialTheme.typography.labelLarge, color = color)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// SEARCH BAR
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun SearchBar(value: TextFieldValue, onValueChange: (TextFieldValue) -> Unit, modifier: Modifier = Modifier) {
    BasicTextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        singleLine = true,
        textStyle = MaterialTheme.typography.bodyLarge.copy(color = Clr.T1),
        cursorBrush = SolidColor(Clr.Teal),
        decorationBox = { inner ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .background(Clr.CardSolid, RoundedCornerShape(13.dp))
                    .border(0.5.dp, Clr.Border, RoundedCornerShape(13.dp))
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Text("⌕", fontSize = 17.sp, color = Clr.T3)
                Box(Modifier.weight(1f)) {
                    if (value.text.isEmpty()) {
                        Text("Поиск по имени, PID, коду…", style = MaterialTheme.typography.bodyLarge, color = Clr.T3)
                    }
                    inner()
                }
                if (value.text.isNotEmpty()) {
                    Box(
                        Modifier
                            .size(18.dp)
                            .background(Clr.T3.copy(alpha = 0.15f), CircleShape)
                            .clickable { onValueChange(TextFieldValue("")) },
                        Alignment.Center,
                    ) { Text("×", fontSize = 13.sp, color = Clr.T3) }
                }
            }
        },
    )
}

// ─────────────────────────────────────────────────────────────────────────────
// CATEGORY CHIPS
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun CategoryChips(selected: ParamCategory?, onSelect: (ParamCategory?) -> Unit) {
    val categories = listOf(null) + ParamCategory.values().toList()
    LazyRow(
        contentPadding = PaddingValues(horizontal = 20.dp),
        horizontalArrangement = Arrangement.spacedBy(7.dp),
    ) {
        items(categories) { cat ->
            val isSelected = cat == selected
            val label = if (cat == null) "Все" else "${cat.icon} ${cat.title}"
            val count = if (cat == null) OBD_PARAMS.size else (paramsByCategory[cat]?.size ?: 0)
            Box(
                Modifier
                    .background(
                        if (isSelected) Clr.Teal else Clr.CardSolid,
                        RoundedCornerShape(20.dp),
                    )
                    .border(
                        0.5.dp,
                        if (isSelected) Clr.Teal else Clr.Border,
                        RoundedCornerShape(20.dp),
                    )
                    .clickable { onSelect(cat) }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            ) {
                Text(
                    "$label  $count",
                    style = MaterialTheme.typography.labelLarge,
                    color = if (isSelected) Clr.TOnAccent else Clr.T2,
                )
            }
        }
    }
    Spacer(Modifier.height(12.dp))
}

// ─────────────────────────────────────────────────────────────────────────────
// PARAM LIST  (grouped or flat)
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun ParamList(
    params: List<OBDParam>,
    groupByCategory: Boolean,
    onParamClick: (OBDParam) -> Unit,
) {
    if (groupByCategory) {
        // Grouped by category with sticky headers
        val grouped = params.groupBy { it.category }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            grouped.forEach { (cat, catParams) ->
                stickyHeader(key = cat.name) {
                    CategoryStickyHeader(cat = cat, count = catParams.size)
                }
                items(catParams, key = { it.pid }) { param ->
                    OBDDetailRow(param = param, onClick = { onParamClick(param) })
                }
            }
        }
    } else {
        // Flat list for search / single category
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            items(params, key = { it.pid }) { param ->
                OBDDetailRow(param = param, onClick = { onParamClick(param) })
            }
        }
    }
}

@Composable
private fun CategoryStickyHeader(cat: ParamCategory, count: Int) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Clr.Bg)
            .drawBehind {
                drawLine(Clr.Border, Offset(0f, size.height), Offset(size.width, size.height), 0.5.dp.toPx())
            }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(cat.icon, fontSize = 15.sp)
            Text(cat.title, style = MaterialTheme.typography.labelLarge, color = Clr.T2, letterSpacing = 0.5.sp)
        }
        Text("$count", style = MaterialTheme.typography.labelMedium, color = Clr.T3)
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// EMPTY SEARCH
// ─────────────────────────────────────────────────────────────────────────────

@Composable
private fun EmptySearch(query: String) {
    Column(
        Modifier.fillMaxSize().padding(40.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Text("◎", fontSize = 36.sp, color = Clr.T3)
        Spacer(Modifier.height(16.dp))
        Text("Ничего не найдено", style = MaterialTheme.typography.headlineSmall, color = Clr.T1)
        Spacer(Modifier.height(6.dp))
        Text(
            "Нет параметра с именем «$query»\nПопробуйте PID или короткое название",
            style = MaterialTheme.typography.bodyMedium,
            color = Clr.T3,
            textAlign = TextAlign.Center,
            lineHeight = 19.sp,
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
// PARAM DETAIL BOTTOM SHEET
// ─────────────────────────────────────────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ParamDetailSheet(param: OBDParam, onDismiss: () -> Unit) {
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        containerColor = Clr.Surface,
        tonalElevation = 0.dp,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        dragHandle = {
            Box(
                Modifier
                    .padding(top = 12.dp, bottom = 6.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(Clr.Border, RoundedCornerShape(2.dp)),
            )
        },
    ) {
        LazyColumn(
            Modifier.fillMaxWidth(),
            contentPadding = PaddingValues(start = 20.dp, end = 20.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { OBDParamDetailCard(param = param) }

            // Related params (same category, different param)
            val related = OBD_PARAMS
                .filter { it.category == param.category && it.pid != param.pid }
                .take(3)

            if (related.isNotEmpty()) {
                item {
                    Spacer(Modifier.height(24.dp))
                    SectionLabel("Похожие параметры")
                    Spacer(Modifier.height(10.dp))
                }
                items(related) { rel ->
                    RelatedParamRow(rel)
                    Spacer(Modifier.height(6.dp))
                }
            }
        }
    }
}

@Composable
private fun RelatedParamRow(param: OBDParam) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Clr.CardSolid, RoundedCornerShape(12.dp))
            .border(0.5.dp, Clr.Border, RoundedCornerShape(12.dp))
            .padding(12.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Box(
                Modifier.size(30.dp).background(param.status.dimColor(), RoundedCornerShape(8.dp)),
                Alignment.Center,
            ) { Text(param.category.icon, fontSize = 13.sp) }
            Column {
                Text(param.name, style = MaterialTheme.typography.titleSmall, color = Clr.T1)
                Text(param.short, style = MaterialTheme.typography.bodySmall, color = Clr.T3)
            }
        }
        Column(horizontalAlignment = Alignment.End) {
            Text(
                if (param.value != null) "${param.value.fmt()} ${param.unit}".trim() else "—",
                style = MaterialTheme.typography.titleSmall,
                color = Clr.T1,
            )
            Text(param.status.label(), style = MaterialTheme.typography.labelSmall, color = param.status.color())
        }
    }
}
