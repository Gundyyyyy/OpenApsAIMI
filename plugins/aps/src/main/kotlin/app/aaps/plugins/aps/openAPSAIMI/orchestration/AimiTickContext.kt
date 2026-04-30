package app.aaps.plugins.aps.openAPSAIMI.orchestration

import app.aaps.core.interfaces.aps.AutosensResult
import app.aaps.core.interfaces.aps.CurrentTemp
import app.aaps.core.interfaces.aps.GlucoseStatusAIMI
import app.aaps.core.interfaces.aps.IobTotal
import app.aaps.core.interfaces.aps.MealData
import app.aaps.core.interfaces.aps.OapsProfileAimi
import app.aaps.core.interfaces.ui.UiInteraction

/**
 * Immutable snapshot of one determine_basal invocation inputs (Phase 2).
 * Stages will progressively read from this instead of threading 12 parameters.
 */
data class AimiTickContext(
    val glucoseStatus: GlucoseStatusAIMI,
    val currentTemp: CurrentTemp,
    val iobDataArray: Array<IobTotal>,
    val profile: OapsProfileAimi,
    val autosensData: AutosensResult,
    val mealData: MealData,
    val microBolusAllowed: Boolean,
    val currentTime: Long,
    val flatBGsDetected: Boolean,
    val dynIsfMode: Boolean,
    val uiInteraction: UiInteraction,
    val extraDebug: String
)
