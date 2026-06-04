package com.clipforge.ai.presentation.subscription

import android.app.Activity
import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.billing.BillingState
import com.clipforge.ai.core.billing.SubscriptionPlan
import com.clipforge.ai.core.storage.UserPreferencesManager
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class SubscriptionUiState(
    val selectedPlan: String = "yearly",
    val isLoading: Boolean = false,
    val isPro: Boolean = false,
    val monthlyPrice: String = "$4.99",
    val yearlyPrice: String = "$29.99",
    val errorMessage: String? = null
)

class SubscriptionViewModel(application: Application) : AndroidViewModel(application) {

    private val app            = application as ClipForgeApp
    private val billingManager = app.billingManager
    private val prefsManager   = UserPreferencesManager(application)

    private val _uiState = MutableStateFlow(SubscriptionUiState())
    val uiState: StateFlow<SubscriptionUiState> = _uiState.asStateFlow()

    init {
        observeBillingState()
        observeUserPrefs()
    }

    private fun observeBillingState() {
        viewModelScope.launch {
            billingManager.billingState.collect { state ->
                val monthly = state.products.find { it.productId == SubscriptionPlan.PRO_MONTHLY_ID }
                val yearly  = state.products.find { it.productId == SubscriptionPlan.PRO_YEARLY_ID }
                _uiState.value = _uiState.value.copy(
                    isPro         = state.isPro,
                    isLoading     = false,
                    monthlyPrice  = monthly?.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.formattedPrice ?: "$4.99",
                    yearlyPrice   = yearly?.subscriptionOfferDetails?.firstOrNull()
                        ?.pricingPhases?.pricingPhaseList?.firstOrNull()
                        ?.formattedPrice ?: "$29.99",
                    errorMessage  = state.errorMessage
                )
                if (state.isPro) prefsManager.setIsPro(true)
            }
        }
    }

    private fun observeUserPrefs() {
        viewModelScope.launch {
            prefsManager.userPrefs.collect { prefs ->
                _uiState.value = _uiState.value.copy(isPro = prefs.isPro)
            }
        }
    }

    fun selectPlan(planId: String) {
        _uiState.value = _uiState.value.copy(selectedPlan = planId)
    }

    fun subscribe(activity: Activity) {
        val productId = when (_uiState.value.selectedPlan) {
            "monthly" -> SubscriptionPlan.PRO_MONTHLY_ID
            else      -> SubscriptionPlan.PRO_YEARLY_ID
        }
        _uiState.value = _uiState.value.copy(isLoading = true)
        billingManager.launchPurchaseFlow(activity, productId)
    }

    fun restorePurchases() {
        billingManager.querySubscriptions()
    }
}
