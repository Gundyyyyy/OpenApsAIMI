package app.aaps.plugins.aps.openAPSAIMI.orchestration

import java.util.concurrent.locks.ReentrantLock

/**
 * Phase E: serialized ingress for AIMI [determine_basal]. Acquired/released from [AimiLoopTelemetry]
 * so non-local returns inside the inlined tick body remain valid.
 */
internal object AimiLoopGate {

    private val invocationLock = ReentrantLock()

    fun acquireExclusive() {
        invocationLock.lock()
    }

    fun releaseExclusive() {
        invocationLock.unlock()
    }
}
