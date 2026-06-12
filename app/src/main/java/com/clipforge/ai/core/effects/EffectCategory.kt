package com.clipforge.ai.core.effects

/** Picker grouping for effects, mirroring CapCut's effect categories. */
enum class EffectCategory(val displayName: String, val order: Int) {
    TRENDY("Trendy", 0),
    RETRO("Retro", 1),
    BLUR("Blur", 2),
    DISTORT("Distort", 3),
    BLING("Bling", 4);

    companion object {
        /** Categories in panel display order. */
        val ordered: List<EffectCategory> get() = entries.sortedBy { it.order }
    }
}
