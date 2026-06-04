package com.clipforge.ai.core.ads
import android.app.Activity
import android.content.Context
class GoogleAdManager(private val frequencyController: AdFrequencyController) : AdManager {
    override fun initialize(context: Context) { /* TODO: MobileAds.initialize(context) */ }
    override fun loadAppOpenAd(context: Context) { /* TODO */ }
    override fun showAppOpenAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (!frequencyController.canShowAppOpen()) { onAdDismissed(); return }
        onAdDismissed()
    }
    override fun loadInterstitialAd(context: Context) { /* TODO */ }
    override fun showInterstitialAd(activity: Activity, onAdDismissed: () -> Unit) {
        if (!frequencyController.canShowInterstitial()) { onAdDismissed(); return }
        onAdDismissed()
    }
    override fun loadRewardedAd(context: Context) { /* TODO */ }
    override fun showRewardedAd(activity: Activity, onVideoComplete: () -> Unit, onAdDismissed: () -> Unit) {
        onAdDismissed()
    }
}
