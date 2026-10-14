package app.messages.ui.dialogs

import android.content.Context
import android.graphics.Color
import android.graphics.drawable.ColorDrawable
import android.view.LayoutInflater
import androidx.appcompat.app.AlertDialog
import app.messages.databinding.DialogDeleteAndExitBinding

object GeneralDialogs {

    fun showDeleteDialog(context: Context, onConfirm: () -> Unit) {
        val dialogBinding = DialogDeleteAndExitBinding.inflate(LayoutInflater.from(context))
        val builder = AlertDialog.Builder(context)
            .setView(dialogBinding.root)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        dialogBinding.btnCancel.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnDelete.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showStyledDeleteDialog(
        context: Context,
        message: String = "",
        onConfirm: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(app.messages.R.layout.dialog_delete_scheduled, null)
        val builder = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        if (message.isNotEmpty()) {
            dialogView.findViewById<android.widget.TextView>(app.messages.R.id.tvMessage)?.text = message
        }

        dialogView.findViewById<android.widget.Button>(app.messages.R.id.btnCancel)?.setOnClickListener { dialog.dismiss() }
        dialogView.findViewById<android.widget.Button>(app.messages.R.id.btnDelete)?.setOnClickListener {
            onConfirm()
            dialog.dismiss()
        }
        dialog.show()
    }

    fun showConfirmationDialog(
        context: Context,
        title: String,
        message: String,
        positiveBtnText: String,
        negativeBtnText: String = "Cancel",
        onPositiveClick: () -> Unit
    ) {
        val dialogView = LayoutInflater.from(context).inflate(app.messages.R.layout.dialog_confirmation, null)
        val builder = AlertDialog.Builder(context)
            .setView(dialogView)
            .setCancelable(true)

        val dialog = builder.create()
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))

        val tvTitle = dialogView.findViewById<android.widget.TextView>(app.messages.R.id.tvTitle)
        val tvMessage = dialogView.findViewById<android.widget.TextView>(app.messages.R.id.tvMessage)
        val btnPositive = dialogView.findViewById<android.widget.Button>(app.messages.R.id.btnPositive)
        val btnCancel = dialogView.findViewById<android.widget.Button>(app.messages.R.id.btnCancel)

        tvTitle.text = title
        tvMessage.text = message
        btnPositive.text = positiveBtnText
        btnCancel.text = negativeBtnText

        btnCancel.setOnClickListener { dialog.dismiss() }
        btnPositive.setOnClickListener {
            onPositiveClick()
            dialog.dismiss()
        }
        dialog.show()
    }
    fun showGroupNameDialog(
        context: Context,
        title: String,
        buttonText: String,
        initialName: String = "",
        onAction: (String) -> Unit
    ) {
        val dialog = com.google.android.material.bottomsheet.BottomSheetDialog(context, app.messages.R.style.BottomSheetDialogTheme)
        val dialogBinding = app.messages.databinding.DialogNewGroupBinding.inflate(LayoutInflater.from(context))
        dialog.setContentView(dialogBinding.root)

        dialogBinding.tvDialogTitle.text = title
        dialogBinding.btnCreateGroup.text = buttonText
        if (initialName.isNotEmpty()) {
            dialogBinding.etGroupName.setText(initialName)
            dialogBinding.etGroupName.setSelection(initialName.length)
        }

        dialogBinding.ivClose.setOnClickListener { dialog.dismiss() }
        dialogBinding.btnCreateGroup.setOnClickListener {
            val groupName = dialogBinding.etGroupName.text.toString().trim()
            onAction(groupName)
            dialog.dismiss()
        }

        dialog.show()
    }
}

