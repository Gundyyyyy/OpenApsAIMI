package app.aaps.plugins.main.general.dashboard.compose

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import app.aaps.core.ui.toast.ToastUtils
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.DashboardEmbeddedComposeState
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

@Composable
internal fun DashboardGraphComposeCard(
    composeState: DashboardEmbeddedComposeState,
    attachLegacyGraphBackend: Boolean,
    /** When true, hides graph update line + range/live/follow + freshness (compact Simple mode). */
    hideDetailedGraphStatus: Boolean = false,
    expandGraphVertically: Boolean = false,
    graphContent: @Composable () -> Unit,
    modifier: Modifier = Modifier,
) {
    val cardInnerPaddingHorizontal = dimensionResource(R.dimen.dashboard_card_inner_padding_horizontal)
    val cardInnerPaddingVertical = dimensionResource(R.dimen.dashboard_card_inner_padding_vertical)
    val graphMinHeight = dimensionResource(R.dimen.dashboard_graph_height_min)
    val sectionSpacing = dimensionResource(R.dimen.dashboard_section_spacing)
    val graphCorner = dimensionResource(R.dimen.dashboard_card_corner_large)
    val context = LocalContext.current
    val density = LocalDensity.current
    val smbHitPx = remember(density) { with(density) { 36.dp.toPx() } }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .then(if (expandGraphVertically) Modifier.fillMaxHeight() else Modifier),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceContainer.copy(alpha = 0.52f),
        ),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (expandGraphVertically) Modifier.fillMaxHeight() else Modifier)
                .padding(horizontal = cardInnerPaddingHorizontal, vertical = cardInnerPaddingVertical),
        ) {
            Text(
                text = stringResource(R.string.graph_menu_divider_header),
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.onSurface,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = sectionSpacing),
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = sectionSpacing),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            modifier = Modifier
                                .width(14.dp)
                                .height(3.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                        )
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = stringResource(R.string.a11y_blood_glucose),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    if (composeState.graphRenderInput.predictionPoints.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(14.dp)
                                    .height(3.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.onSurface.copy(alpha = 0.78f)),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.prediction_shortname),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!attachLegacyGraphBackend && composeState.graphRenderInput.smbMarkers.isNotEmpty()) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = "▲",
                                fontSize = 10.sp,
                                color = MaterialTheme.colorScheme.error,
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.graph_legend_smb),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (!attachLegacyGraphBackend &&
                        (
                            composeState.graphRenderInput.tbrSegments.isNotEmpty() ||
                                composeState.graphRenderInput.tbrMarkerEpochMs.isNotEmpty()
                            )
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .width(2.dp)
                                    .height(12.dp)
                                    .background(MaterialTheme.colorScheme.tertiary),
                            )
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                text = stringResource(R.string.graph_legend_tbr),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    DashboardGraphComposeControls(
                        composeState = composeState,
                        modifier = Modifier.height(34.dp),
                    )
                }
            }
            val updateMessage = composeState.graphUiState.updateMessage
            if (!hideDetailedGraphStatus && updateMessage.isNotBlank()) {
                Text(
                    text = updateMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = sectionSpacing),
                )
            }
            val renderInput = composeState.graphRenderInput
            val statusUi = remember(
                renderInput,
                composeState.graphUiState.freshnessConfig.warningThresholdMinutes,
                composeState.graphUiState.freshnessConfig.staleThresholdMinutes,
            ) {
                GraphStatusPresenter.present(
                    renderInput = renderInput,
                    freshnessConfig = composeState.graphUiState.freshnessConfig,
                    nowEpochMs = System.currentTimeMillis(),
                )
            }
            if (!hideDetailedGraphStatus) {
                val renderSummary = buildString {
                    append(statusUi.summaryRangeHours)
                    append("h")
                    append(" \u2022 ")
                    append(stringResource(statusUi.dataStateRes))
                    append(" \u2022 ")
                    append(stringResource(statusUi.followStateRes))
                }
                val freshnessText = statusUi.freshnessMinutes?.let { mins ->
                    stringResource(statusUi.freshnessMessageRes, mins)
                } ?: stringResource(statusUi.freshnessMessageRes)
                val freshnessColor: Color = when (statusUi.freshnessLevel) {
                    GraphFreshnessLevel.FRESH -> MaterialTheme.colorScheme.primary
                    GraphFreshnessLevel.WARNING -> MaterialTheme.colorScheme.tertiary
                    GraphFreshnessLevel.STALE -> MaterialTheme.colorScheme.error
                    GraphFreshnessLevel.UNKNOWN -> MaterialTheme.colorScheme.onSurfaceVariant
                }
                Text(
                    text = renderSummary,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = 2.dp),
                )
                Text(
                    text = freshnessText,
                    style = MaterialTheme.typography.labelSmall,
                    color = freshnessColor,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(bottom = sectionSpacing),
                )
            } else {
                Spacer(modifier = Modifier.height(sectionSpacing))
            }
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .then(
                        if (expandGraphVertically) {
                            Modifier
                                .weight(1f)
                                .heightIn(min = graphMinHeight)
                        } else {
                            Modifier.heightIn(min = graphMinHeight)
                        },
                    )
                    .pointerInput(
                        composeState.graphCommands.onDoubleTap,
                        composeState.graphCommands.onLongPress,
                    ) {
                        detectTapGestures(
                            onDoubleTap = { composeState.graphCommands.onDoubleTap?.invoke() },
                            onLongPress = { composeState.graphCommands.onLongPress?.invoke() },
                        )
                    },
                verticalAlignment = Alignment.Bottom,
            ) {
                val yAxisLabels = remember(renderInput) {
                    graphYAxisLabels(renderInput)
                }
                Column(
                    modifier = Modifier
                        .width(44.dp)
                        .then(if (expandGraphVertically) Modifier.fillMaxHeight() else Modifier.height(graphMinHeight))
                        .padding(end = 6.dp, top = 4.dp, bottom = 4.dp),
                    verticalArrangement = Arrangement.SpaceBetween,
                ) {
                    yAxisLabels.forEach { label ->
                        Text(
                            text = label,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.82f),
                        )
                    }
                }
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .then(if (expandGraphVertically) Modifier.fillMaxHeight() else Modifier)
                        .clip(RoundedCornerShape(graphCorner))
                        .then(
                            if (!attachLegacyGraphBackend && composeState.graphCommands.onGraphPanFromDragFraction != null) {
                                Modifier.pointerInput(
                                    composeState.graphCommands.onGraphPanFromDragFraction,
                                    renderInput.rangeHours,
                                ) {
                                    detectHorizontalDragGestures { change, dragAmount ->
                                        change.consume()
                                        val w = size.width.coerceAtLeast(1).toFloat()
                                        composeState.graphCommands.onGraphPanFromDragFraction?.invoke(
                                            dragAmount / w,
                                        )
                                    }
                                }
                            } else {
                                Modifier
                            },
                        ),
                ) {
                    val smbTapModifier =
                        if (!attachLegacyGraphBackend && renderInput.smbMarkers.isNotEmpty()) {
                            Modifier.pointerInput(
                                renderInput.smbMarkers,
                                renderInput.fromTimeEpochMs,
                                renderInput.toTimeEpochMs,
                                renderInput.points,
                                renderInput.predictionPoints,
                                smbHitPx,
                            ) {
                                detectTapGestures { offset ->
                                    val marker = findNearestSmbByX(
                                        tapX = offset.x,
                                        widthPx = size.width.toFloat(),
                                        hitPx = smbHitPx,
                                        input = renderInput,
                                    )
                                    if (marker != null) {
                                        ToastUtils.infoToast(
                                            context,
                                            context.getString(R.string.dashboard_graph_smb_toast, marker.amountLabel),
                                        )
                                    }
                                }
                            }
                        } else {
                            Modifier
                        }
                    DashboardGraphComposeRenderer(
                        renderInput = renderInput,
                        modifier = Modifier
                            .fillMaxWidth()
                            .then(if (expandGraphVertically) Modifier.weight(1f) else Modifier.height(graphMinHeight))
                            .then(smbTapModifier),
                    )
                    val tickLabels = remember(renderInput.fromTimeEpochMs, renderInput.toTimeEpochMs) {
                        graphTickLabels(
                            fromTimeEpochMs = renderInput.fromTimeEpochMs,
                            toTimeEpochMs = renderInput.toTimeEpochMs,
                        )
                    }
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 6.dp)
                            .offset(y = (-2).dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        tickLabels.forEach { label ->
                            Text(
                                text = label,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.88f),
                            )
                        }
                    }
                    if (attachLegacyGraphBackend) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(1.dp)
                                .alpha(0f),
                        ) {
                            graphContent()
                        }
                    }
                }
            }
        }
    }
}

private fun findNearestSmbByX(
    tapX: Float,
    widthPx: Float,
    hitPx: Float,
    input: DashboardEmbeddedComposeState.GraphRenderInput,
): DashboardEmbeddedComposeState.SmbMarker? {
    if (input.smbMarkers.isEmpty() || widthPx <= 1f) return null
    val reservedFabInset = min(72f, widthPx * 0.16f)
    val plotRight = widthPx - reservedFabInset
    val plotLeft = 0f
    val plotWidth = (plotRight - plotLeft).coerceAtLeast(1f)
    val basePoints = if (input.points.isNotEmpty()) input.points else input.predictionPoints
    if (basePoints.isEmpty()) return null
    val minX = input.fromTimeEpochMs.takeIf { it > 0L } ?: basePoints.minOf { it.timestampEpochMs }
    val maxX = input.toTimeEpochMs.takeIf { it > minX } ?: basePoints.maxOf { it.timestampEpochMs }
    val xRange = max(1L, maxX - minX).toFloat()
    fun toCanvasX(epochMs: Long): Float =
        plotLeft + (((epochMs - minX) / xRange) * plotWidth)

    var best: DashboardEmbeddedComposeState.SmbMarker? = null
    var bestDist = Float.POSITIVE_INFINITY
    for (m in input.smbMarkers) {
        val cx = toCanvasX(m.timestampEpochMs)
        val d = abs(tapX - cx)
        if (d <= hitPx && d < bestDist) {
            bestDist = d
            best = m
        }
    }
    return best
}

private fun graphTickLabels(fromTimeEpochMs: Long, toTimeEpochMs: Long): List<String> {
    if (fromTimeEpochMs <= 0L || toTimeEpochMs <= fromTimeEpochMs) return emptyList()
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    val ticks = 4
    return (0..ticks).map { idx ->
        val ratio = idx.toDouble() / ticks.toDouble()
        val timestamp = fromTimeEpochMs + ((toTimeEpochMs - fromTimeEpochMs) * ratio).toLong()
        formatter.format(Date(timestamp))
    }
}

private fun graphYAxisLabels(renderInput: DashboardEmbeddedComposeState.GraphRenderInput): List<String> {
    val values = buildList {
        addAll(renderInput.points.map { it.value })
        addAll(renderInput.predictionPoints.map { it.value })
        renderInput.targetLowMgdl?.let { add(it) }
        renderInput.targetHighMgdl?.let { add(it) }
    }
    if (values.isEmpty()) return emptyList()
    val min = values.minOrNull() ?: return emptyList()
    val maxValue = values.maxOrNull() ?: return emptyList()
    val span = max(1.0, maxValue - min)
    val paddedMin = min - span * 0.12
    val paddedMax = maxValue + span * 0.12
    val ticks = 4
    return (0..ticks).map { idx ->
        val ratio = idx.toDouble() / ticks.toDouble()
        val value = paddedMax - ((paddedMax - paddedMin) * ratio)
        value.toInt().toString()
    }
}
