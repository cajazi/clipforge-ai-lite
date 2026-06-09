package com.clipforge.ai.core.transition

/**
 * UX grouping for the transition panel (categories / thumbnail sections).
 *
 * This is *data on the descriptor*, not new code paths — the panel renders sections by
 * grouping registered descriptors on their [TransitionCategory]. Adding a category here
 * never requires touching the render/export pipeline.
 *
 * [order] controls left-to-right / top-to-bottom section ordering in the panel.
 *
 * Phase A: type only. Not yet consumed by any UI.
 */
enum class TransitionCategory(val displayName: String, val order: Int) {
    DISSOLVE("Dissolve", 0),
    FADE("Fade", 1),
    MOTION("Motion", 2),
    BLUR("Blur", 3),
    WIPE("Wipe", 4),
    THREE_D("3D", 5),
    GLITCH("Glitch", 6);

    companion object {
        /** Categories in panel display order. */
        fun ordered(): List<TransitionCategory> = entries.sortedBy { it.order }
    }
}
