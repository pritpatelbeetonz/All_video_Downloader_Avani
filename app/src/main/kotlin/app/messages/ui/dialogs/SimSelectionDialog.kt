package app.messages.ui.dialogs

import android.app.Dialog
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.bottomsheet.BottomSheetDialogFragment
import app.messages.R
import app.messages.utility.SimInfo
import app.messages.utility.SimManager

class SimSelectionDialog(
    private val sims: List<SimInfo>,
    private val currentSubId: Long? = null,
    private val onSimSelected: (SimInfo) -> Unit
) : BottomSheetDialogFragment() {

    override fun getTheme(): Int = R.style.BottomSheetDialogTheme

    override fun onCreateDialog(savedInstanceState: Bundle?): Dialog {
        val dialog = super.onCreateDialog(savedInstanceState) as BottomSheetDialog
        dialog.setOnShowListener { dialogInterface ->
            val bottomSheet = (dialogInterface as BottomSheetDialog).findViewById<View>(com.google.android.material.R.id.design_bottom_sheet)
            bottomSheet?.setBackgroundResource(R.drawable.bg_bottom_sheet)
        }
        return dialog
    }

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        val view = inflater.inflate(R.layout.dialog_sim_selection, container, false)

        val simManager = SimManager(requireContext())
        val containerLayout = view.findViewById<LinearLayout>(R.id.simContainer)

        // Add SIM options
        sims.forEach { simInfo ->
            val simItem = createSimItem(simInfo, simManager)
            simItem.setOnClickListener {
                onSimSelected(simInfo)
                dismiss()
            }
            containerLayout.addView(simItem)
        }

        return view
    }

    private fun createSimItem(simInfo: SimInfo, simManager: SimManager): View {
        val view = LayoutInflater.from(context).inflate(R.layout.item_sim_option, null)
        val simNameText = view.findViewById<TextView>(R.id.tvSimName)
        val simCarrierText = view.findViewById<TextView>(R.id.tvSimCarrier)
        val simIndicator = view.findViewById<View>(R.id.viewSimIndicator)
        val simIcon = view.findViewById<android.widget.ImageView>(R.id.ivSimIcon)
        val selectedIcon = view.findViewById<android.widget.ImageView>(R.id.ivSelected)

        val slotIndex = simInfo.slotIndex
        simNameText.text = simManager.getSimDisplayText(slotIndex)
        simCarrierText.text = simInfo.carrierName
        
        val simColor = simManager.getSimColor(slotIndex)
        simIndicator.backgroundTintList = android.content.res.ColorStateList.valueOf(simColor)
        simIcon.imageTintList = android.content.res.ColorStateList.valueOf(simColor)

        if (currentSubId != null && simInfo.subscriptionId == currentSubId) {
            selectedIcon.visibility = View.VISIBLE
            selectedIcon.imageTintList = android.content.res.ColorStateList.valueOf(simColor)
        } else {
            selectedIcon.visibility = View.GONE
        }

        return view
    }
}
