package com.clipforge.ai.core.ads
import android.app.Activity
import android.content.Context
interface AdManager {
    fun initialize(context: Context)
    fun loadAppOpenAd(context: Context)
    fun showAppOpenAd(activity: Activity, onAdDismissed: () -> Unit)
    fun loadInterstitialAd(context: Context)
    fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit)
    fun loadRewardedAd(context: Context)
    fun showRewardedAd(activity: Activity, onVideoComplete: () -> Unit, onAdDismissed: () -> Unit)
}
