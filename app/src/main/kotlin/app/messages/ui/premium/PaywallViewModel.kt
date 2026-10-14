package app.messages.ui.premium

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import app.messages.monetization.iap.BillingGateway
import app.messages.monetization.iap.EntitlementController
import app.messages.monetization.iap.PlusProduct
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

@HiltViewModel
class PaywallViewModel @Inject constructor(
    private val billing: BillingGateway,
    private val entitlement: EntitlementController,
) : ViewModel() {

    val products: List<PlusProduct> = billing.products
    val isPremium: StateFlow<Boolean> = entitlement.isPremium

    // Yearly preselected per §6.4.
    private val _selectedId = MutableStateFlow(products.first().id)
    val selectedId: StateFlow<String> = _selectedId

    private val _trialEnabled = MutableStateFlow(true)
    val trialEnabled: StateFlow<Boolean> = _trialEnabled

    private val _purchasing = MutableStateFlow(false)
    val purchasing: StateFlow<Boolean> = _purchasing

    val selectedProduct: PlusProduct get() = products.first { it.id == _selectedId.value }

    fun select(id: String) {
        _selectedId.value = id
    }

    fun setTrial(enabled: Boolean) {
        _trialEnabled.value = enabled
    }

    fun purchase(onComplete: () -> Unit) {
        if (_purchasing.value) return
        _purchasing.value = true
        viewModelScope.launch {
            val trial = _trialEnabled.value && selectedProduct.trialEligible
            billing.purchase(_selectedId.value, trial)
            _purchasing.value = false
            onComplete()
        }
    }

    fun restore() {
        viewModelScope.launch { billing.restore() }
    }
}
