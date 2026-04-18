package app.aaps.plugins.main.general.dashboard.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.draw.drawBehind
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import app.aaps.core.interfaces.overview.graph.SeriesType
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.DashboardEmbeddedComposeState
import androidx.compose.foundation.background
import app.aaps.ui.compose.overview.graphs.BgGraphCompose
import app.aaps.ui.compose.overview.graphs.DashboardActivityStripChart
import app.aaps.ui.compose.overview.graphs.DEFAULT_GRAPH_ZOOM_MINUTES
import app.aaps.ui.compose.overview.graphs.GraphViewModel
import app.aaps.ui.compose.overview.graphs.timestampToX
import app.aaps.ui.compose.overview.graphs.drawVicoChartBackdrop
import app.aaps.ui.compose.overview.graphs.vicoChartBackdropPalette
import com.patrykandpatrick.vico.compose.cartesian.Scroll
import com.patrykandpatrick.vico.compose.cartesian.Zoom
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoScrollState
import com.patrykandpatrick.vico.compose.cartesian.rememberVicoZoomState
import kotlin.math.max
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop

private const val VICO_SCROLL_LIVE_EDGE_EPSILON_PX = 36f

/**
 * Dashboard BG chart using the same Vico stack as Overview ([BgGraphCompose] + [GraphViewModel]).
 *
 * - Uses [graphRenderInput] window for X when valid so the chart matches the shell + SMB/TBR overlay.
 * - Always enables prediction series on the dashboard (same data as legacy Canvas card).
 * - Soft layered background (same spirit as [DashboardGraphComposeRenderer]) for a calmer product look.
 * - BG + prediction colours from AAPS theme via [BgGraphCompose] (no Material-only override).
 * - SMB + TBR are drawn inside [BgGraphCompose] (Vico line series + decoration) so they track scroll/zoom.
 * - When activity overlay is enabled, [DashboardActivityStripChart] shows activity, SMB, and TBR under the BG chart
 *   (activity on its own Y scale; SMB/TBR drawn for legibility).
 */
@OptIn(FlowPreview::class)
@Composable
internal fun DashboardBgGraphVico(
    graphViewModel: GraphViewModel,
    graphRenderInput: DashboardEmbeddedComposeState.GraphRenderInput,
    viewportResetGeneration: Int,
    /** When false, the user has panned away from the live edge — do not auto-scroll on new BG. */
    viewportFollowingLive: Boolean,
    onViewportFollowingLiveChanged: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberVicoScrollState(
        scrollEnabled = true,
        initialScroll = Scroll.Absolute.End,
    )
    val zoomState = rememberVicoZoomState(
        zoomEnabled = true,
        initialZoom = Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES),
    )
    val vmDerived by graphViewModel.derivedTimeRange.collectAsStateWithLifecycle()
    val effectiveTimeRange = remember(
        vmDerived,
        graphRenderInput.fromTimeEpochMs,
        graphRenderInput.toTimeEpochMs,
    ) {
        val f = graphRenderInput.fromTimeEpochMs
        val t = graphRenderInput.toTimeEpochMs
        if (f > 0L && t > f) f to t else vmDerived
    }

    val derivedTimeRange = effectiveTimeRange
    val nowTimestamp by graphViewModel.nowTimestamp.collectAsStateWithLifecycle()
    val savedConfig by graphViewModel.graphConfigFlow.collectAsStateWithLifecycle()
    val bgOverlays = remember(savedConfig.bgOverlays) {
        buildList {
            addAll(savedConfig.bgOverlays)
            if (SeriesType.PREDICTIONS !in this) {
                add(SeriesType.PREDICTIONS)
            }
        }
    }

    val maxScrollSeen = remember(viewportResetGeneration) {
        mutableFloatStateOf(Float.NEGATIVE_INFINITY)
    }

    LaunchedEffect(viewportResetGeneration) {
        if (viewportResetGeneration <= 0) return@LaunchedEffect
        maxScrollSeen.floatValue = Float.NEGATIVE_INFINITY
        zoomState.zoom(Zoom.x(DEFAULT_GRAPH_ZOOM_MINUTES))
        delay(10)
        scrollState.animateScroll(Scroll.Absolute.End)
        delay(390)
        maxScrollSeen.floatValue = max(maxScrollSeen.floatValue, scrollState.value)
        onViewportFollowingLiveChanged(true)
    }

    LaunchedEffect(scrollState, viewportResetGeneration) {
        snapshotFlow { scrollState.value }
            .debounce(40)
            .collect { scroll ->
                maxScrollSeen.floatValue = max(maxScrollSeen.floatValue, scroll)
                val atLiveEdge = scroll >= maxScrollSeen.floatValue - VICO_SCROLL_LIVE_EDGE_EPSILON_PX
                onViewportFollowingLiveChanged(atLiveEdge)
            }
    }

    val bgInfoState by graphViewModel.bgInfoState.collectAsStateWithLifecycle()
    val predictions by graphViewModel.predictionsFlow.collectAsStateWithLifecycle()
    var lastBgTimestamp by remember { mutableLongStateOf(0L) }

    LaunchedEffect(bgInfoState.bgInfo?.timestamp, viewportFollowingLive) {
        val newTimestamp = bgInfoState.bgInfo?.timestamp ?: return@LaunchedEffect
        val showPredictions = SeriesType.PREDICTIONS in bgOverlays
        if (viewportFollowingLive &&
            lastBgTimestamp != 0L &&
            newTimestamp > lastBgTimestamp
        ) {
            val timeRange = derivedTimeRange
            if (showPredictions && predictions.isNotEmpty() && timeRange != null) {
                val (minTimestamp, _) = timeRange
                val nowX = timestampToX(System.currentTimeMillis(), minTimestamp)
                scrollState.animateScroll(Scroll.Absolute.x(nowX + 120.0, bias = 1f))
            } else {
                scrollState.animateScroll(Scroll.Absolute.End)
            }
        }
        lastBgTimestamp = newTimestamp
    }

    LaunchedEffect(scrollState, zoomState) {
        snapshotFlow { scrollState.value to zoomState.value }
            .drop(1)
            .debounce(30)
            .collect { graphViewModel.onGraphInteraction() }
    }

    val scheme = MaterialTheme.colorScheme
    val vicoChartLook by graphViewModel.vicoChartLookFlow.collectAsStateWithLifecycle()
    val backdropPalette = remember(vicoChartLook.chartBackdropKey, scheme) {
        vicoChartBackdropPalette(vicoChartLook.chartBackdropKey, scheme)
    }

    Box(
        modifier = modifier.drawBehind { drawVicoChartBackdrop(backdropPalette) },
    ) {
        val showActivityStrip = SeriesType.ACTIVITY in bgOverlays
        Column(Modifier.fillMaxSize()) {
            BgGraphCompose(
                viewModel = graphViewModel,
                bgOverlays = bgOverlays,
                scrollState = scrollState,
                zoomState = zoomState,
                derivedTimeRange = derivedTimeRange,
                nowTimestamp = nowTimestamp,
                useMaterial3DashboardStyle = false,
                dashboardSmbMarkers = graphRenderInput.smbMarkers,
                dashboardTbrSegments = graphRenderInput.tbrSegments,
                dashboardTbrMarkerEpochMs = graphRenderInput.tbrMarkerEpochMs,
                lockStartAxisYFromZero = true,
                dashboardSoftTherapyVisuals = true,
                dashboardSplitActivityToStrip = showActivityStrip,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth(),
            )
            if (showActivityStrip) {
                val stripHeight = dimensionResource(R.dimen.dashboard_activity_strip_height)
                DashboardActivityStripChart(
                    viewModel = graphViewModel,
                    scrollState = scrollState,
                    zoomState = zoomState,
                    derivedTimeRange = derivedTimeRange,
                    nowTimestamp = nowTimestamp,
                    dashboardSmbMarkers = graphRenderInput.smbMarkers,
                    dashboardTbrSegments = graphRenderInput.tbrSegments,
                    dashboardTbrMarkerEpochMs = graphRenderInput.tbrMarkerEpochMs,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(stripHeight)
                        .clip(RoundedCornerShape(bottomStart = 6.dp, bottomEnd = 6.dp))
                        .background(MaterialTheme.colorScheme.surfaceContainerLow.copy(alpha = 0.35f)),
                )
            }
        }
    }
}
