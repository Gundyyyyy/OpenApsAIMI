package app.aaps.plugins.main.general.dashboard.compose

import androidx.compose.foundation.layout.Box
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.res.stringResource
import app.aaps.plugins.main.R
import app.aaps.plugins.main.general.dashboard.DashboardEmbeddedComposeState

@Composable
internal fun DashboardGraphComposeControls(
    composeState: DashboardEmbeddedComposeState,
    modifier: Modifier = Modifier,
) {
    val selected = composeState.graphUiState.rangeHours
    val onSelect = composeState.graphCommands.onSelectRange ?: return
    val ranges = listOf(6, 9, 12, 18, 24)
    var expanded by remember { mutableStateOf(false) }
    val selectedLabel = when (selected) {
        6 -> stringResource(R.string.graph_long_scale_6h)
        9 -> stringResource(R.string.graph_long_scale_9h)
        12 -> stringResource(R.string.graph_long_scale_12h)
        18 -> stringResource(R.string.graph_long_scale_18h)
        24 -> stringResource(R.string.graph_long_scale_24h)
        else -> "${selected}h"
    }
    Box(modifier = modifier) {
        OutlinedButton(
            onClick = { expanded = true },
        ) {
            Text(
                text = selectedLabel,
                style = MaterialTheme.typography.labelMedium,
                modifier = Modifier.weight(1f, fill = false),
            )
            Icon(
                imageVector = Icons.Filled.ExpandMore,
                contentDescription = null,
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false },
        ) {
            ranges.forEach { hours ->
                val label = when (hours) {
                    6 -> stringResource(R.string.graph_long_scale_6h)
                    9 -> stringResource(R.string.graph_long_scale_9h)
                    12 -> stringResource(R.string.graph_long_scale_12h)
                    18 -> stringResource(R.string.graph_long_scale_18h)
                    24 -> stringResource(R.string.graph_long_scale_24h)
                    else -> "${hours}h"
                }
                DropdownMenuItem(
                    text = { Text(text = label) },
                    onClick = {
                        expanded = false
                        onSelect(hours)
                    },
                )
            }
        }
    }
}
