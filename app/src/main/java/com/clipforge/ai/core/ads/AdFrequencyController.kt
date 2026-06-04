package com.clipforge.ai.core.ads
class AdFrequencyController {
    private var lastInterstitialTime = 0L
    private var lastAppOpenTime      = 0L
    fun canShowInterstitial(): Boolean = System.currentTimeMillis() - lastInterstitialTime >= AdConfig.INTERSTITIAL_COOLDOWN_MS
    fun markInterstitialShown() { lastInterstitialTime = System.currentTimeMillis() }
    fun canShowAppOpen(): Boolean = System.currentTimeMillis() - lastAppOpenTime >= AdConfig.APP_OPEN_COOLDOWN_MS
    fun markAppOpenShown() { lastAppOpenTime = System.currentTimeMillis() }
}
