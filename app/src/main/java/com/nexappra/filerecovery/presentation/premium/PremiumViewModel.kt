package com.nexappra.filerecovery.presentation.premium

import android.app.Activity
import androidx.lifecycle.ViewModel
import com.nexappra.filerecovery.data.billing.PlayBillingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject

@HiltViewModel
class PremiumViewModel @Inject constructor(private val billing: PlayBillingRepository) : ViewModel() {
    val state = billing.state
    init { billing.refresh() }
    fun restore() = billing.refresh()
    fun purchase(activity: Activity, productId: String) = billing.purchase(activity, productId)
}
