package app.aaps.plugins.main.general.dashboard

import android.view.View
import android.widget.FrameLayout
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.viewinterop.AndroidView
import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.interfaces.Preferences
import app.aaps.core.ui.compose.AapsTheme
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.compose.DashboardCircleTopCompose
import app.aaps.plugins.main.general.dashboard.compose.DashboardGraphComposeCard
import app.aaps.plugins.main.general.dashboard.compose.DashboardHeroLayoutProfile
import app.aaps.plugins.main.general.dashboard.compose.DashboardNotificationsComposeList
import app.aaps.plugins.main.general.dashboard.viewmodel.OverviewViewModel
import app.aaps.plugins.main.general.dashboard.views.GlucoseGraphView

/**
 * AIMI dashboard column for the Compose main shell.
 *
 * **Simple mode (product)**: one-screen layout — no vertical scroll; the graph expands to fill
 * remaining height so glucose + actions + chart stay visible without scrolling.
 *
 * **Extended mode**: scrollable column for extra metrics and insights.
 */
@Composable
internal fun AimiDashboardComposeEmbedded(
    shellPostRoot: View,
    embeddedState: DashboardEmbeddedComposeState,
    preferences: Preferences,
    viewModel: OverviewViewModel,
    onShellBindingReady: (DashboardShellBinding) -> Unit,
    modifier: Modifier = Modifier,
) {
    // Flip to false once Compose graph renderer reaches full parity and runtime validation is green.
    val attachLegacyGraphBackendForCompose = embeddedState.graphUiState.attachLegacyGraphBackend
    var auditorHost by remember { mutableStateOf<FrameLayout?>(null) }
    var glucoseGraphView by remember { mutableStateOf<GlucoseGraphView?>(null) }

    LaunchedEffect(auditorHost, glucoseGraphView, attachLegacyGraphBackendForCompose) {
        val aud = auditorHost ?: return@LaunchedEffect
        val graph = if (attachLegacyGraphBackendForCompose) {
            glucoseGraphView ?: return@LaunchedEffect
        } else {
            null
        }
        onShellBindingReady(
            DashboardShellBinding.fromComposeEmbeddedColumn(
                shellPostRoot = shellPostRoot,
                auditorHost = aud,
                glucoseGraph = graph,
            ),
        )
    }

    AapsTheme {
        val bodyHorizontalPadding = dimensionResource(R.dimen.dashboard_scroll_padding_horizontal)
        val bodyVerticalPadding = dimensionResource(R.dimen.dashboard_scroll_padding_vertical)
        val blockSpacing = dimensionResource(R.dimen.dashboard_block_spacing)

        var extendedMetrics by remember {
            mutableStateOf(preferences.get(BooleanKey.OverviewDashboardExtendedMetrics))
        }
        LaunchedEffect(embeddedState.metricsPreferencesSync) {
            extendedMetrics = preferences.get(BooleanKey.OverviewDashboardExtendedMetrics)
        }

        val graphModifierBase = Modifier
            .fillMaxWidth()
            .padding(start = bodyHorizontalPadding, end = bodyHorizontalPadding)
        val graphMaxHeightSimple = dimensionResource(R.dimen.dashboard_graph_height_max_simple)

        val graphCard: @Composable (Modifier) -> Unit = { graphMod ->
            DashboardGraphComposeCard(
                composeState = embeddedState,
                attachLegacyGraphBackend = attachLegacyGraphBackendForCompose,
                hideDetailedGraphStatus = !extendedMetrics,
                expandGraphVertically = !extendedMetrics,
                modifier = graphMod,
                graphContent = {
                    if (attachLegacyGraphBackendForCompose) {
                        AndroidView(
                            factory = { ctx -> GlucoseGraphView(ctx) },
                            modifier = Modifier.fillMaxWidth(),
                            update = { v ->
                                if (glucoseGraphView !== v) glucoseGraphView = v
                            },
                        )
                    }
                },
            )
        }

        if (extendedMetrics) {
            Column(
                modifier = modifier
                    .fillMaxSize()
                    .verticalScroll(rememberScrollState()),
            ) {
                DashboardCircleTopCompose(
                    viewModel = viewModel,
                    embeddedState = embeddedState,
                    preferences = preferences,
                    onAuditorHostAttached = { fl ->
                        if (auditorHost !== fl) auditorHost = fl
                    },
                    layoutProfile = DashboardHeroLayoutProfile.Default,
                    modifier = Modifier.fillMaxWidth(),
                )
                DashboardNotificationsComposeList(
                    composeState = embeddedState,
                    compact = false,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = bodyHorizontalPadding, end = bodyHorizontalPadding)
                        .padding(top = bodyVerticalPadding, bottom = blockSpacing),
                )
                graphCard(
                    graphModifierBase.padding(bottom = blockSpacing),
                )
            }
        } else {
            Column(
                modifier = modifier.fillMaxSize(),
            ) {
                DashboardCircleTopCompose(
                    viewModel = viewModel,
                    embeddedState = embeddedState,
                    preferences = preferences,
                    onAuditorHostAttached = { fl ->
                        if (auditorHost !== fl) auditorHost = fl
                    },
                    layoutProfile = DashboardHeroLayoutProfile.SimpleOneScreen,
                    modifier = Modifier.fillMaxWidth(),
                )
                DashboardNotificationsComposeList(
                    composeState = embeddedState,
                    compact = true,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = bodyHorizontalPadding, end = bodyHorizontalPadding)
                        .padding(top = bodyVerticalPadding / 2, bottom = blockSpacing / 2),
                )
                graphCard(
                    graphModifierBase
                        .weight(1f)
                        .heightIn(max = graphMaxHeightSimple)
                        .padding(bottom = bodyVerticalPadding),
                )
            }
        }
    }
}
