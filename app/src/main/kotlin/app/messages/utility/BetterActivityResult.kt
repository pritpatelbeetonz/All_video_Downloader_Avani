package app.messages.utility

import android.content.Intent
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultCaller
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.activity.result.contract.ActivityResultContracts

class BetterActivityResult <I, O> private constructor(
    caller: ActivityResultCaller,
    contract: ActivityResultContract<I, O>,
    private var onActivityResult: ((O) -> Unit)?
) {
    private val launcher: ActivityResultLauncher<I> =
        caller.registerForActivityResult(contract) { result ->
            onActivityResult?.invoke(result)
        }

    fun launch(input: I, callback: ((O) -> Unit)? = null) {
        if (callback != null) {
            onActivityResult = callback
        }
        launcher.launch(input)
    }

    fun setOnActivityResult(callback: ((O) -> Unit)?) {
        onActivityResult = callback
    }

    companion object {
        fun <I, O> registerForActivityResult(
            caller: ActivityResultCaller,
            contract: ActivityResultContract<I, O>,
            onActivityResult: ((O) -> Unit)? = null
        ): BetterActivityResult<I, O> {
            return BetterActivityResult(caller, contract, onActivityResult)
        }

        fun registerActivityForResult(
            caller: ActivityResultCaller,
            onActivityResult: ((ActivityResult) -> Unit)? = null
        ): BetterActivityResult<Intent, ActivityResult> {
            return registerForActivityResult(caller, ActivityResultContracts.StartActivityForResult(), onActivityResult)
        }
    }
}