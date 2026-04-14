package app.aaps.plugins.main.general.dashboard

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import app.aaps.plugins.main.BuildConfig
import app.aaps.plugins.main.general.dashboard.viewmodel.AdjustmentCardState
import app.aaps.plugins.main.general.overview.notifications.NotificationStore

/**
 * UI state driven from [DashboardShellController] when the AIMI hero is pure Compose (no
 * [app.aaps.plugins.main.general.dashboard.views.CircleTopDashboardView]).
 */
class DashboardEmbeddedComposeState {
    data class GraphFreshnessConfig(
        val warningThresholdMinutes: Int = 5,
        val staleThresholdMinutes: Int = 15,
    )

    data class GraphComposeUiState(
        val rangeHours: Int = 6,
        val updateMessage: String = "",
        val freshnessConfig: GraphFreshnessConfig = GraphFreshnessConfig(),
        val attachLegacyGraphBackend: Boolean = BuildConfig.AIMI_DASHBOARD_LEGACY_GRAPH_FALLBACK,
    )

    data class GraphRenderInput(
        val rangeHours: Int = 6,
        val hasBgData: Boolean = false,
        val followLive: Boolean = true,
        /** When true, the user has panned the Compose graph into the past (not snapped to live right edge). */
        val graphPanActive: Boolean = false,
        val lastRefreshEpochMs: Long = 0L,
        val fromTimeEpochMs: Long = 0L,
        val toTimeEpochMs: Long = 0L,
        val nowEpochMs: Long = 0L,
        val targetLowMgdl: Double? = null,
        val targetHighMgdl: Double? = null,
        val points: List<GraphPoint> = emptyList(),
        val predictionPoints: List<GraphPoint> = emptyList(),
        val smbMarkers: List<SmbMarker> = emptyList(),
        val tbrMarkerEpochMs: List<Long> = emptyList(),
        val tbrSegments: List<TbrSegment> = emptyList(),
    )

    data class GraphPoint(
        val timestampEpochMs: Long,
        val value: Double,
    )

    data class SmbMarker(
        val timestampEpochMs: Long,
        val amountLabel: String,
    )

    /**
     * Temp basal segment in the visible graph window: horizontal extent encodes **duration**,
     * bar height encodes **relative delivery** (from scaled basal series, normalized in the window).
     */
    data class TbrSegment(
        val startEpochMs: Long,
        val endEpochMs: Long,
        /** 0..1 for bar height in the TBR lane (minimum visual floor applied in renderer). */
        val intensity01: Float,
    )

    data class GraphComposeCommands(
        val onSelectRange: ((Int) -> Unit)? = null,
        val onDoubleTap: (() -> Unit)? = null,
        val onLongPress: (() -> Unit)? = null,
        /** Horizontal pan: fraction of the current range width (positive = finger moved right → view older data). */
        val onGraphPanFromDragFraction: ((Float) -> Unit)? = null,
    )

    var contextIndicatorVisible by mutableStateOf(false)
    var adjustmentCardState by mutableStateOf<AdjustmentCardState?>(null)
    var notifications by mutableStateOf<List<NotificationStore.NotificationComposeItem>>(emptyList())
    var onOpenAdjustmentDetails by mutableStateOf<(() -> Unit)?>(null)
    var onRunLoopRequested by mutableStateOf<(() -> Unit)?>(null)
    var onDismissNotification by mutableStateOf<((Int) -> Unit)?>(null)
    var graphUiState by mutableStateOf(GraphComposeUiState())
    var graphRenderInput by mutableStateOf(GraphRenderInput())
    var graphCommands by mutableStateOf(GraphComposeCommands())

    /**
     * Horizontal pan for the Compose-only graph: shifts the visible window backward in time
     * (right edge moves left from live), clamped in [DashboardShellController] to loaded BG data.
     */
    var graphPanPastMs by mutableLongStateOf(0L)
        internal set

    var metricsPreferencesSync by mutableIntStateOf(0)
        private set

    internal fun requestMetricsPreferenceResync() {
        metricsPreferencesSync++
    }

    fun updateGraphRange(hours: Int) {
        graphUiState = graphUiState.copy(rangeHours = hours)
        graphRenderInput = graphRenderInput.copy(rangeHours = hours)
    }

    fun updateGraphMessage(message: String) {
        graphUiState = graphUiState.copy(updateMessage = message)
    }

    fun updateGraphFreshnessConfig(config: GraphFreshnessConfig) {
        graphUiState = graphUiState.copy(freshnessConfig = config)
    }

    fun updateAttachLegacyGraphBackend(enabled: Boolean) {
        graphUiState = graphUiState.copy(attachLegacyGraphBackend = enabled)
    }

    fun updateGraphCommands(commands: GraphComposeCommands) {
        graphCommands = commands
    }

    fun updateGraphRenderInput(input: GraphRenderInput) {
        graphRenderInput = input
    }

    fun resetGraphPan() {
        graphPanPastMs = 0L
    }

    internal fun accumulateGraphPanPastMs(deltaMs: Long) {
        graphPanPastMs = (graphPanPastMs + deltaMs).coerceAtLeast(0L)
    }

    internal fun clampGraphPanPastMs(maxPanPastMs: Long) {
        graphPanPastMs = graphPanPastMs.coerceIn(0L, maxPanPastMs.coerceAtLeast(0L))
    }
}
