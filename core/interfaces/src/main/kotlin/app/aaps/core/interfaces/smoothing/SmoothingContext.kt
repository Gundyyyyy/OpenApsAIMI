package app.aaps.core.interfaces.smoothing

/**
 * Optional hints for [Smoothing.smooth] (e.g. IOB already computed outside a critical section).
 */
data class SmoothingContext(
    /**
     * Total IOB from bolus + temp basals (converted extended included), insulin units.
     * When set, adaptive smoothing can use this for heuristics without calling the calculator inside [smooth].
     */
    val cachedTotalIobUnits: Double? = null
) {
    companion object {
        val NONE = SmoothingContext()
    }
}
