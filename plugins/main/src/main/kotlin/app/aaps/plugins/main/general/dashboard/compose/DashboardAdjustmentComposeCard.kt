package app.aaps.plugins.main.general.dashboard.compose

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.dimensionResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.core.text.HtmlCompat
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.DashboardEmbeddedComposeState

@Composable
internal fun DashboardAdjustmentComposeCard(
    composeState: DashboardEmbeddedComposeState,
    modifier: Modifier = Modifier,
) {
    val state = composeState.adjustmentCardState ?: return
    val cardInnerPaddingH = dimensionResource(R.dimen.dashboard_card_inner_padding_horizontal)
    val cardInnerPaddingV = dimensionResource(R.dimen.dashboard_card_inner_padding_vertical)
    val sectionSpacing = dimensionResource(R.dimen.dashboard_section_spacing)
    val chipPaddingV = dimensionResource(R.dimen.dashboard_chip_padding_vertical)
    val chipPaddingH = dimensionResource(R.dimen.dashboard_chip_padding_horizontal)
    val chipSpacing = dimensionResource(R.dimen.dashboard_chip_spacing)
    val pumpLinePlain = remember(state.pumpLine) {
        HtmlCompat.fromHtml(state.pumpLine, HtmlCompat.FROM_HTML_MODE_LEGACY).toString()
    }
    Card(
        modifier = modifier
            .fillMaxWidth()
            .clickable { composeState.onOpenAdjustmentDetails?.invoke() }
            .semantics {
                contentDescription = buildString {
                    append(state.glycemiaLine)
                    append(". ")
                    append(state.decisionLine)
                }
            },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = cardInnerPaddingH, vertical = cardInnerPaddingV),
            verticalArrangement = Arrangement.spacedBy(sectionSpacing),
        ) {
            Text(
                text = stringResource(R.string.dashboard_adjustments),
                style = MaterialTheme.typography.titleSmall,
            )
            Text(text = state.glycemiaLine, style = MaterialTheme.typography.bodyMedium)
            Text(text = state.predictionLine, style = MaterialTheme.typography.bodySmall)
            Text(text = state.iobActivityLine, style = MaterialTheme.typography.bodySmall)
            Text(text = state.decisionLine, style = MaterialTheme.typography.bodySmall)
            state.modeLine?.takeIf { it.isNotBlank() }?.let {
                Text(text = it, style = MaterialTheme.typography.bodySmall)
            }
            Text(
                text = pumpLinePlain,
                style = MaterialTheme.typography.bodySmall,
            )
            Text(text = state.safetyLine, style = MaterialTheme.typography.bodySmall)

            if (state.adjustments.isEmpty()) {
                Text(
                    text = stringResource(R.string.dashboard_no_adjustments),
                    style = MaterialTheme.typography.bodyMedium,
                )
            } else {
                state.adjustments.forEach { line ->
                    Text(
                        text = line,
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier
                            .fillMaxWidth()
                            .background(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.9f),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(8.dp),
                            )
                            .padding(horizontal = chipPaddingH, vertical = chipPaddingV)
                            .padding(bottom = chipSpacing),
                    )
                }
            }

            Button(
                onClick = { composeState.onRunLoopRequested?.invoke() },
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(stringResource(R.string.dashboard_run_loop))
            }
        }
    }
}
