package com.clipforge.ai.domain.model

import com.clipforge.ai.core.billing.PlanType

data class Project(
    val id: String,
    val title: String,
    val aspectRatio: AspectRatio,
    val exportQuality: ExportQuality,
    val createdAt: Long,
    val updatedAt: Long,
    val planType: PlanType          = PlanType.FREE,
    val thumbnailUri: String?       = null,
    val projectType: ProjectType    = ProjectType.MANUAL,
    val autoEditSettings: AutoEditSettings? = null
) {
    init {
        // AUTO_EDIT projects must have settings
        require(projectType != ProjectType.AUTO_EDIT || autoEditSettings != null) {
            "AUTO_EDIT projects must have autoEditSettings"
        }
    }
}

enum class AspectRatio(val label: String, val widthRatio: Int, val heightRatio: Int) {
    RATIO_9_16("9:16",  9, 16),
    RATIO_16_9("16:9", 16,  9),
    RATIO_1_1 ("1:1",   1,  1),
    RATIO_4_5 ("4:5",   4,  5)
}

enum class ExportQuality(val label: String, val height: Int) {
    QUALITY_720P ("720p",  720),
    QUALITY_1080P("1080p", 1080)
}
