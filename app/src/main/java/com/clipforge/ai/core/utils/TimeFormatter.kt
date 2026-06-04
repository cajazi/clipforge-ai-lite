package com.clipforge.ai.core.utils
object TimeFormatter {
    fun formatMs(ms: Long): String { val t = ms / 1000; return "%02d:%02d".format(t / 60, t % 60) }
    fun formatMsLong(ms: Long): String { val t = ms / 1000; return "%02d:%02d:%02d".format(t / 3600, (t % 3600) / 60, t % 60) }
    fun formatSeconds(s: Long): String = formatMs(s * 1000)
}
