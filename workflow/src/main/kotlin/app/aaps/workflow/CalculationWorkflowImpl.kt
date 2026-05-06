package app.aaps.workflow

import android.content.Context
import android.os.SystemClock
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequest
import androidx.work.WorkInfo
import androidx.work.WorkRequest
import androidx.work.WorkManager
import app.aaps.core.interfaces.iob.IobCobCalculator
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.LTag
import app.aaps.core.interfaces.overview.OverviewData
import app.aaps.core.interfaces.overview.graph.OverviewDataCache
import app.aaps.core.interfaces.plugin.ActivePlugin
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.workflow.CalculationSignalsEmitter
import app.aaps.core.interfaces.workflow.CalculationWorkflow
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.MAIN_CALCULATION
import app.aaps.core.interfaces.workflow.CalculationWorkflow.Companion.UPDATE_PREDICTIONS
import app.aaps.core.utils.receivers.DataWorkerStorage
import app.aaps.core.utils.worker.then
import app.aaps.workflow.iob.IobCobOref1Worker
import app.aaps.workflow.iob.IobCobOrefWorker
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Provider
import javax.inject.Singleton

@Singleton
class CalculationWorkflowImpl @Inject constructor(
    private val context: Context,
    private val aapsLogger: AAPSLogger,
    private val dateUtil: DateUtil,
    private val dataWorkerStorage: DataWorkerStorage,
    private val activePlugin: ActivePlugin,
    private val mainSignals: CalculationSignalsEmitter,
    // Lazy: breaks Dagger cycle OverviewDataCache → Loop → IobCobCalculator → CalculationWorkflow → OverviewDataCache.
    // Side methods that use mainCache run at runtime, never during construction.
    private val mainCacheProvider: Provider<OverviewDataCache>
) : CalculationWorkflow {

    private val mainCache: OverviewDataCache get() = mainCacheProvider.get()
    private companion object {
        const val STOP_CALCULATION_TIMEOUT_MS = 15_000L
        const val STOP_CALCULATION_POLL_MS = 100L
        const val TRACE_TOKEN = "traceId="
        const val TRACE_TAG_PREFIX = "calc-trace:"
    }

    private fun parseTraceTag(reason: String): String? {
        val marker = reason.indexOf(TRACE_TOKEN)
        if (marker < 0) return null
        val raw = reason.substring(marker + TRACE_TOKEN.length).trim()
        val traceId = raw.takeWhile { !it.isWhitespace() }
        if (traceId.isBlank()) return null
        return TRACE_TAG_PREFIX + traceId
    }

    private fun <B : WorkRequest.Builder<*, *>> B.withTraceTag(traceTag: String?): B {
        if (!traceTag.isNullOrBlank()) addTag(traceTag)
        return this
    }

    init {
        // Verify definition
        var sumPercent = 0
        for (pass in CalculationWorkflow.ProgressData.entries) sumPercent += pass.percentOfTotal
        require(sumPercent == 100)
    }

    override fun stopCalculation(job: String, from: String) {
        aapsLogger.debug(LTag.WORKER, "Stopping calculation thread: $from")
        val workManager = WorkManager.getInstance(context)
        val stopStartedAt = SystemClock.elapsedRealtime()
        workManager.cancelUniqueWork(job)
        while (SystemClock.elapsedRealtime() - stopStartedAt < STOP_CALCULATION_TIMEOUT_MS) {
            val workStatus = try {
                workManager
                    .getWorkInfosForUniqueWork(job)
                    .get(STOP_CALCULATION_POLL_MS, TimeUnit.MILLISECONDS)
            } catch (_: Exception) {
                emptyList()
            }
            val stillRunning = workStatus.any { it.state == WorkInfo.State.RUNNING }
            if (!stillRunning) {
                val elapsed = SystemClock.elapsedRealtime() - stopStartedAt
                aapsLogger.debug(LTag.WORKER, "Calculation thread stopped: $from (${elapsed}ms)")
                return
            }
            SystemClock.sleep(STOP_CALCULATION_POLL_MS)
        }
        val elapsed = SystemClock.elapsedRealtime() - stopStartedAt
        aapsLogger.warn(
            LTag.WORKER,
            "Stopping calculation timed out after ${elapsed}ms from=$from job=$job; continuing with replacement chain"
        )
    }

    override fun runCalculation(
        job: String,
        iobCobCalculator: IobCobCalculator,
        overviewData: OverviewData,
        cache: OverviewDataCache,
        signals: CalculationSignalsEmitter,
        reason: String,
        end: Long,
        bgDataReload: Boolean,
        triggeredByNewBG: Boolean
    ) {
        val traceTag = parseTraceTag(reason)
        aapsLogger.debug(
            LTag.WORKER,
            "Starting calculation worker: $reason to ${dateUtil.dateAndTimeAndSecondsString(end)}" +
                (traceTag?.let { " tag=$it" } ?: "")
        )

        WorkManager.getInstance(context)
            .beginUniqueWork(
                job, ExistingWorkPolicy.REPLACE,
                if (bgDataReload) OneTimeWorkRequest.Builder(LoadBgDataWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(LoadBgDataWorker.LoadBgData(iobCobCalculator, end))).build()
                else OneTimeWorkRequest.Builder(DummyWorker::class.java)
                    .withTraceTag(traceTag)
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBucketedDataWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData, cache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData, cache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(signals, CalculationWorkflow.ProgressData.DRAW_BG)))
                    .build()
            )
            .then(
                if (activePlugin.activeSensitivity.isOref1)
                    OneTimeWorkRequest.Builder(IobCobOref1Worker::class.java)
                        .withTraceTag(traceTag)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOref1Worker.IobCobOref1WorkerData(iobCobCalculator, signals, reason, end, job == MAIN_CALCULATION, triggeredByNewBG)))
                        .build()
                else
                    OneTimeWorkRequest.Builder(IobCobOrefWorker::class.java)
                        .withTraceTag(traceTag)
                        .setInputData(dataWorkerStorage.storeInputData(IobCobOrefWorker.IobCobOrefWorkerData(iobCobCalculator, signals, reason, end, job == MAIN_CALCULATION, triggeredByNewBG)))
                        .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareIobAutosensGraphDataWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareIobAutosensGraphDataWorker.PrepareIobAutosensData(iobCobCalculator, overviewData, cache, signals)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(signals, CalculationWorkflow.ProgressData.DRAW_IOB)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(InvokeLoopWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(InvokeLoopWorker.InvokeLoopData(triggeredByNewBG)))
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(UpdateWidgetWorker::class.java)
                    .withTraceTag(traceTag)
                    .build()
            )
            .then(
                runIf = job == MAIN_CALCULATION,
                OneTimeWorkRequest.Builder(PreparePredictionsWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(PreparePredictionsWorker.PreparePredictionsData(overviewData, cache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .withTraceTag(traceTag)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(signals, CalculationWorkflow.ProgressData.DRAW_FINAL)))
                    .build()
            )
            .enqueue()
    }

    override fun runOnReceivedPredictions(
        overviewData: OverviewData
    ) {
        aapsLogger.debug(LTag.WORKER, "Starting updateReceivedPredictions worker")

        WorkManager.getInstance(context)
            .beginUniqueWork(
                UPDATE_PREDICTIONS, ExistingWorkPolicy.REPLACE,
                OneTimeWorkRequest.Builder(PreparePredictionsWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PreparePredictionsWorker.PreparePredictionsData(overviewData, mainCache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(mainSignals, CalculationWorkflow.ProgressData.DRAW_FINAL)))
                    .build()
            )
            .enqueue()
    }

    override fun runOnScaleChanged(iobCobCalculator: IobCobCalculator, overviewData: OverviewData) {
        WorkManager.getInstance(context)
            .beginUniqueWork(
                MAIN_CALCULATION, ExistingWorkPolicy.APPEND,
                OneTimeWorkRequest.Builder(PrepareBucketedDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBucketedDataWorker.PrepareBucketedData(iobCobCalculator, overviewData, mainCache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(PrepareBgDataWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(PrepareBgDataWorker.PrepareBgData(iobCobCalculator, overviewData, mainCache)))
                    .build()
            )
            .then(
                OneTimeWorkRequest.Builder(UpdateGraphWorker::class.java)
                    .setInputData(dataWorkerStorage.storeInputData(UpdateGraphWorker.UpdateGraphData(mainSignals, CalculationWorkflow.ProgressData.DRAW_FINAL)))
                    .build()
            )
            .enqueue()
    }
}