package app.aaps.plugins.aps.openAPSAIMI.orchestration

import app.aaps.core.interfaces.aps.RT
import java.util.concurrent.atomic.AtomicLong
import kotlin.collections.ArrayDeque

/**
 * Phase A (observe-only): correlates a monotonic tick id with each [determine_basal] pass,
 * guarantees an end marker via [traceDetermineBasalTick] `finally`, and keeps a small in-memory
 * ring for debugging (no second watchdog; stall blackbox remains in the hormonitor exporter).
 */
object AimiLoopTelemetry {

    private const val RING_MAX = 128

    private val tickSeq = AtomicLong(0L)

    @Volatile
    internal var activeTickId: Long = 0L
        private set

    private val ring = ArrayDeque<String>()

    /**
     * Wraps one full AIMI determine_basal pass. Non-local returns from [block] still run `finally`
     * (Kotlin `inline` + `try`/`finally`), so tick end is always recorded.
     */
    internal inline fun traceDetermineBasalTick(
        wallClockMs: Long,
        noinline onTickEnd: ((tickId: Long, startedWallMs: Long, endedWallMs: Long) -> Unit)? = null,
        block: () -> RT
    ): RT {
        val id = tickSeq.incrementAndGet()
        val previousActive = activeTickId
        activeTickId = id
        appendRing("tick_start id=$id wall_ms=$wallClockMs")
        try {
            return block()
        } finally {
            val endedWallMs = System.currentTimeMillis()
            appendRing("tick_end id=$id wall_ms=$endedWallMs duration_ms=${endedWallMs - wallClockMs}")
            try {
                onTickEnd?.invoke(id, wallClockMs, endedWallMs)
            } catch (_: Throwable) {
                // Never break the loop on telemetry.
            }
            activeTickId = previousActive
        }
    }

    internal fun ringSnapshotTail(maxLines: Int = 32): List<String> {
        val cap = maxLines.coerceIn(1, RING_MAX)
        synchronized(ring) {
            if (ring.isEmpty()) return emptyList()
            return ring.takeLast(cap)
        }
    }

    private fun appendRing(line: String) {
        val stamped = "${System.currentTimeMillis()} $line"
        synchronized(ring) {
            ring.addLast(stamped)
            while (ring.size > RING_MAX) ring.removeFirst()
        }
    }
}
