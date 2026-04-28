package app.aaps.plugins.aps.openAPSAIMI.pkpd

import app.aaps.core.keys.BooleanKey
import app.aaps.core.keys.DoubleKey
import app.aaps.core.keys.interfaces.Preferences
import io.mockk.every
import io.mockk.mockk
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

class PkPdIntegrationTest {

    private val preferences: Preferences = mockk(relaxed = true)
    private val integration = PkPdIntegration(preferences)

    @Test
    fun `test computeRuntime disabled`() {
        every { preferences.get(BooleanKey.OApsAIMIPkpdEnabled) } returns false
        
        val result = integration.computeRuntime(
            epochMillis = 1000,
            bg = 100.0,
            deltaMgDlPer5 = 0.0,
            iobU = 1.0,
            carbsActiveG = 0.0,
            windowMin = 60,
            exerciseFlag = false,
            profileIsf = 50.0,
            tdd24h = 40.0
        )
        assertNull(result)
    }

    @Test
    fun `test computeRuntime enabled`() {
        every { preferences.get(BooleanKey.OApsAIMIPkpdEnabled) } returns true
        mockPkpdDefaults()
        
        val result = integration.computeRuntime(
            epochMillis = 1000,
            bg = 100.0,
            deltaMgDlPer5 = 0.0,
            iobU = 1.0,
            carbsActiveG = 0.0,
            windowMin = 60,
            exerciseFlag = false,
            profileIsf = 50.0,
            tdd24h = 40.0
        )
        assertNotNull(result)
    }

    @Test
    fun `aggregated stage does not reset to pre-onset in smb chain`() {
        every { preferences.get(BooleanKey.OApsAIMIPkpdEnabled) } returns true
        mockPkpdDefaults()
        integration.setRecentBolusSamples(
            listOf(
                PkpdBolusSample(ageMin = 25.0, units = 1.0),
                PkpdBolusSample(ageMin = 20.0, units = 1.0),
                PkpdBolusSample(ageMin = 15.0, units = 1.0),
                PkpdBolusSample(ageMin = 10.0, units = 1.0),
                PkpdBolusSample(ageMin = 5.0, units = 1.0),
            )
        )

        val result = integration.computeRuntime(
            epochMillis = 1000,
            bg = 180.0,
            deltaMgDlPer5 = 8.0,
            iobU = 5.0,
            carbsActiveG = 20.0,
            windowMin = 5,
            exerciseFlag = false,
            profileIsf = 50.0,
            tdd24h = 40.0
        )

        assertNotNull(result)
        assertNotEquals(InsulinActivityStage.PRE_ONSET, result?.activity?.stage)
    }

    @Test
    fun `single recent bolus keeps pre-onset stage`() {
        every { preferences.get(BooleanKey.OApsAIMIPkpdEnabled) } returns true
        mockPkpdDefaults()
        integration.setRecentBolusSamples(listOf(PkpdBolusSample(ageMin = 5.0, units = 1.0)))

        val result = integration.computeRuntime(
            epochMillis = 1000,
            bg = 120.0,
            deltaMgDlPer5 = 1.0,
            iobU = 1.0,
            carbsActiveG = 0.0,
            windowMin = 5,
            exerciseFlag = false,
            profileIsf = 50.0,
            tdd24h = 40.0
        )

        assertNotNull(result)
        assertEquals(InsulinActivityStage.PRE_ONSET, result?.activity?.stage)
    }

    private fun mockPkpdDefaults() {
        every { preferences.get(DoubleKey.OApsAIMIPkpdStateDiaH) } returns 6.0
        every { preferences.get(DoubleKey.OApsAIMIPkpdStatePeakMin) } returns 55.0
        every { preferences.get(DoubleKey.OApsAIMIPkpdBoundsDiaMinH) } returns 5.0
        every { preferences.get(DoubleKey.OApsAIMIPkpdBoundsDiaMaxH) } returns 8.0
        every { preferences.get(DoubleKey.OApsAIMIPkpdBoundsPeakMinMin) } returns 35.0
        every { preferences.get(DoubleKey.OApsAIMIPkpdBoundsPeakMinMax) } returns 95.0
        every { preferences.get(DoubleKey.OApsAIMIPkpdMaxDiaChangePerDayH) } returns 0.5
        every { preferences.get(DoubleKey.OApsAIMIPkpdMaxPeakChangePerDayMin) } returns 5.0
        every { preferences.get(DoubleKey.OApsAIMIIsfFusionMinFactor) } returns 0.7
        every { preferences.get(DoubleKey.OApsAIMIIsfFusionMaxFactor) } returns 1.5
        every { preferences.get(DoubleKey.OApsAIMIIsfFusionMaxChangePerTick) } returns 0.2
        every { preferences.get(DoubleKey.OApsAIMISmbTailThreshold) } returns 1.0
        every { preferences.get(DoubleKey.OApsAIMISmbTailDamping) } returns 0.85
        every { preferences.get(DoubleKey.OApsAIMISmbExerciseDamping) } returns 0.8
        every { preferences.get(DoubleKey.OApsAIMISmbLateFatDamping) } returns 0.8
    }
}
