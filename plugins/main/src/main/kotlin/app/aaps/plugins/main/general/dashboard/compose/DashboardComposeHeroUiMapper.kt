package app.aaps.plugins.main.general.dashboard.compose

import android.content.Context
import android.util.TypedValue
import androidx.core.content.ContextCompat
import app.aaps.core.interfaces.rx.events.AdaptiveSmoothingQualityTier
import app.aaps.core.ui.compose.dashboard.GlucoseHeroUiState
import app.aaps.core.ui.views.GlucoseRingColorComputer
import app.aaps.plugins.main.general.dashboard.viewmodel.StatusCardState

/**
 * Builds [GlucoseHeroUiState] for the Compose dashboard hero — logic aligned with
 * [app.aaps.plugins.main.general.dashboard.views.CircleTopDashboardView] (ring, nose, telemetry arc).
 */
internal object DashboardComposeHeroUiMapper {

    fun buildHeroState(context: Context, state: StatusCardState): GlucoseHeroUiState? {
        val bgMgdl = state.glucoseMgdl ?: return null
        val arcP = telemetryArcProgress(state)
        val arcC = arcP?.let { telemetryArcColor(context, it) }
        val step1 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step1)
        val step2 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step2)
        val step3 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step3)
        val step4 = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_step4)
        val ringArgb = GlucoseRingColorComputer.compute(
            bgMgdl = bgMgdl,
            hypoMaxFromProfile = null,
            severeHypoMaxMgdl = 54f,
            hypoMaxMgdlAttr = 70f,
            useSteppedColors = true,
            step1MaxMgdl = 120f,
            step2MaxMgdl = 160f,
            step3MaxMgdl = 220f,
            stepColor1 = step1,
            stepColor2 = step2,
            stepColor3 = step3,
            stepColor4 = step4,
        )
        return GlucoseHeroUiState(
            mainText = state.glucoseText,
            subLeftText = state.deltaText,
            subRightText = state.timeAgo,
            noseAngleDeg = state.noseAngleDeg,
            ringColorArgb = ringArgb,
            centerTextColorArgb = state.glucoseColor,
            subTextColorArgb = resolveThemeColor(context, android.R.attr.textColorSecondary),
            surfaceColorArgb = ContextCompat.getColor(context, app.aaps.core.ui.R.color.glucose_ring_surface),
            telemetryProgress = arcP,
            telemetryColorArgb = arcC,
            strokeWidthDp = 4f,
        )
    }

    private fun telemetryArcProgress(state: StatusCardState): Float? {
        val rel = state.trajectoryRelevanceScore
        val health = state.aimiHealthScore
        val tierProxy = state.adaptiveSmoothingQualityTier?.let { tier ->
            when (tier) {
                AdaptiveSmoothingQualityTier.OK -> 0.88
                AdaptiveSmoothingQualityTier.UNCERTAIN -> 0.58
                AdaptiveSmoothingQualityTier.BAD -> 0.35
            }
        }
        val combined: Double? = when {
            rel != null && health != null -> 0.5 * (rel + health)
            rel != null -> rel
            health != null -> health
            tierProxy != null -> tierProxy
            else -> null
        }
        return combined?.toFloat()?.coerceIn(0f, 1f)
    }

    private fun telemetryArcColor(context: Context, progress: Float): Int {
        val resId = when {
            progress >= 0.72f -> app.aaps.core.ui.R.color.glucose_ring_step1
            progress >= 0.45f -> app.aaps.core.ui.R.color.glucose_ring_step2
            else -> app.aaps.core.ui.R.color.glucose_ring_step3
        }
        return ContextCompat.getColor(context, resId)
    }

    private fun resolveThemeColor(context: Context, attr: Int): Int {
        val tv = TypedValue()
        return if (context.theme.resolveAttribute(attr, tv, true)) tv.data else 0xFF888888.toInt()
    }
}
