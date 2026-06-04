package com.clipforge.ai.core.utils
import java.text.DecimalFormat
object FileSizeFormatter {
    private val df = DecimalFormat("#.##")
    fun format(bytes: Long): String = when {
        bytes >= 1_073_741_824L -> "${df.format(bytes / 1_073_741_824.0)} GB"
        bytes >= 1_048_576L     -> "${df.format(bytes / 1_048_576.0)} MB"
        bytes >= 1_024L         -> "${df.format(bytes / 1_024.0)} KB"
        else                    -> "$bytes B"
    }
}
