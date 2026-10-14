package app.messages.utility

import android.app.Activity
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.viewbinding.ViewBinding

inline fun <T : ViewBinding> Activity.viewBinding(crossinline bindingInflater: (LayoutInflater) -> T) =
    lazy(LazyThreadSafetyMode.NONE) {
        bindingInflater.invoke(layoutInflater)
    }

inline fun <T : ViewBinding> ViewGroup.inflateBinding(
    crossinline bindingInflater: (LayoutInflater, ViewGroup?, Boolean) -> T,
    attachToParent: Boolean = false
): T {
    return bindingInflater.invoke(LayoutInflater.from(context), if (attachToParent) this else null, attachToParent)
}

