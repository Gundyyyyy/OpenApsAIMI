package app.aaps.plugins.aps.openAPSAIMI.autodrive.estimator

import app.aaps.plugins.aps.openAPSAIMI.autodrive.models.AutoDriveState
import app.aaps.core.interfaces.logging.AAPSLogger
import com.google.common.truth.Truth.assertThat
import io.mockk.mockk
import org.junit.jupiter.api.Test

class ContinuousStateEstimatorHypoRecoveryTest {

    private val logger = mockk<AAPSLogger>(relaxed = true)

    @Test
    fun `post-hypo guard yields lower Ra than same state without guard on rising innovation`() {
        val base = AutoDriveState.createSafe(
            bg = 106.0,
            bgVelocity = 2.0,
            iob = 0.3,
            cob = 0.0,
            estimatedSI = 0.005,
            hour = 14,
            steps = 500,
            combinedDelta = 3.0,
            uamConfidence = 0.8
        )

        val damped = ContinuousStateEstimator(logger).apply {
            repeat(5) { updateAndPredict(base.copy(applyHypoRecoveryRaDampening = true)) }
        }.getLastRa()

        val normal = ContinuousStateEstimator(logger).apply {
            repeat(5) { updateAndPredict(base.copy(applyHypoRecoveryRaDampening = false)) }
        }.getLastRa()

        assertThat(normal - damped).isGreaterThan(0.01)
    }
}
