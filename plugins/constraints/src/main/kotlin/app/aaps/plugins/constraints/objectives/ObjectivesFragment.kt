package app.aaps.plugins.constraints.objectives

import android.annotation.SuppressLint
import android.graphics.Typeface
import android.os.Bundle
import android.os.Handler
import android.os.HandlerThread
import android.os.SystemClock
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSmoothScroller
import androidx.recyclerview.widget.RecyclerView
import app.aaps.core.data.ue.Action
import app.aaps.core.data.ue.Sources
import app.aaps.core.data.ue.ValueWithUnit
import app.aaps.core.interfaces.logging.AAPSLogger
import app.aaps.core.interfaces.logging.UserEntryLogger
import app.aaps.core.interfaces.receivers.ReceiverStatusStore
import app.aaps.core.interfaces.resources.ResourceHelper
import app.aaps.core.interfaces.rx.AapsSchedulers
import app.aaps.core.interfaces.rx.bus.RxBus
import app.aaps.core.interfaces.rx.events.EventNtpStatus
import app.aaps.core.interfaces.rx.events.EventSWUpdate
import app.aaps.core.interfaces.utils.DateUtil
import app.aaps.core.interfaces.utils.Fabric.FabricPrivacy
import app.aaps.core.ui.dialogs.OKDialog
import app.aaps.core.utils.HtmlHelper
import app.aaps.plugins.constraints.R
import app.aaps.plugins.constraints.databinding.ObjectivesFragmentBinding
import app.aaps.plugins.constraints.databinding.ObjectivesItemBinding
import app.aaps.plugins.constraints.objectives.activities.ObjectivesExamDialog
import app.aaps.plugins.constraints.objectives.dialogs.NtpProgressDialog
import app.aaps.plugins.constraints.objectives.events.EventObjectivesUpdateGui
import app.aaps.plugins.constraints.objectives.objectives.Objective
import app.aaps.plugins.constraints.objectives.objectives.Objective.ExamTask
import app.aaps.plugins.constraints.objectives.objectives.Objective.UITask
import dagger.android.support.DaggerFragment
import io.reactivex.rxjava3.disposables.CompositeDisposable
import io.reactivex.rxjava3.kotlin.plusAssign
import javax.inject.Inject

class ObjectivesFragment : DaggerFragment() {

    @Inject lateinit var rxBus: RxBus
    @Inject lateinit var aapsLogger: AAPSLogger
    @Inject lateinit var aapsSchedulers: AapsSchedulers
    @Inject lateinit var rh: ResourceHelper
    @Inject lateinit var fabricPrivacy: FabricPrivacy
    @Inject lateinit var objectivesPlugin: ObjectivesPlugin
    @Inject lateinit var receiverStatusStore: ReceiverStatusStore
    @Inject lateinit var dateUtil: DateUtil
    @Inject lateinit var sntpClient: SntpClient
    @Inject lateinit var uel: UserEntryLogger

    private val objectivesAdapter = ObjectivesAdapter()
    private val handler = Handler(HandlerThread(this::class.simpleName + "Handler").also { it.start() }.looper)

    private var disposable: CompositeDisposable = CompositeDisposable()

    private val objectiveUpdater = object : Runnable {
        override fun run() {
            handler.postDelayed(this, (60 * 1000).toLong())
            updateGUI()
        }
    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View {
        val binding = ObjectivesFragmentBinding.inflate(inflater, container, false)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = objectivesAdapter
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        disposable = CompositeDisposable()
        disposable += rxBus.register(EventObjectivesUpdateGui::class.java)
            .observeOn(aapsSchedulers.main())
            .subscribe { updateGUI() }
        disposable += rxBus.register(EventNtpStatus::class.java)
            .observeOn(aapsSchedulers.main())
            .subscribe {
                if (it.isSuccessful) {
                    OKDialog.show(requireActivity(), R.string.objectives_ntp_success)
                } else {
                    OKDialog.show(requireActivity(), R.string.objectives_ntp_fail)
                }
            }
        handler.post(objectiveUpdater)
    }

    override fun onPause() {
        super.onPause()
        disposable.dispose()
        handler.removeCallbacks(objectiveUpdater)
    }

    private fun updateGUI() {
        activity?.runOnUiThread {
            objectivesAdapter.notifyDataSetChanged()
        }
    }

    inner class ObjectivesAdapter : RecyclerView.Adapter<ObjectivesViewHolder>() {
        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ObjectivesViewHolder {
            val binding = ObjectivesItemBinding.inflate(LayoutInflater.from(parent.context), parent, false)
            return ObjectivesViewHolder(binding)
        }

        override fun onBindViewHolder(holder: ObjectivesViewHolder, position: Int) {
            holder.bind(objectivesPlugin.objectives[position], position)
        }

        override fun getItemCount(): Int = objectivesPlugin.objectives.size
    }

    inner class ObjectivesViewHolder(private val binding: ObjectivesItemBinding) : RecyclerView.ViewHolder(binding.root) {
        @SuppressLint("SetTextI18n")
        fun bind(objective: Objective, position: Int) {
            binding.objectiveNumber.text = (position + 1).toString()
            binding.objectiveTitle.setText(objective.objective)

            if (objective.isAccomplished) {
                binding.objectiveCard.setCardBackgroundColor(rh.gnc(R.color.isAccomplished))
            } else if (objective.isStarted) {
                binding.objectiveCard.setCardBackgroundColor(rh.gnc(R.color.objectivesBackground))
            } else {
                binding.objectiveCard.setCardBackgroundColor(rh.gnc(R.color.objectivesBackground))
            }

            binding.objectiveTasks.removeAllViews()
            for (task in objective.tasks) {
                val taskView = TextView(context)
                taskView.text = "• " + rh.gs(task.task)
                if (task.isCompleted()) {
                    taskView.setTypeface(null, Typeface.BOLD)
                    taskView.setTextColor(rh.gnc(R.color.isCompleted))
                }
                binding.objectiveTasks.addView(taskView)

                val progressView = TextView(context)
                progressView.text = "  " + task.progress
                binding.objectiveTasks.addView(progressView)
            }

            binding.objectiveButton.visibility = View.GONE
            if (!objective.isStarted) {
                if (objectivesPlugin.allPriorAccomplished(position)) {
                    binding.objectiveButton.visibility = View.VISIBLE
                    binding.objectiveButton.setText(R.string.objectives_start)
                    binding.objectiveButton.setOnClickListener {
                        objective.startedOn = dateUtil.now()
                        updateGUI()
                        uel.log(Action.OBJECTIVE_STARTED, position + 1, Sources.USER)
                    }
                }
            } else if (!objective.isAccomplished) {
                if (objective.isCompleted) {
                    binding.objectiveButton.visibility = View.VISIBLE
                    binding.objectiveButton.setText(R.string.objectives_finish)
                    binding.objectiveButton.setOnClickListener {
                        objective.accomplishedOn = dateUtil.now()
                        updateGUI()
                        uel.log(Action.OBJECTIVE_ACCOMPLISHED, position + 1, Sources.USER)
                    }
                }
            }
        }
    }
}
