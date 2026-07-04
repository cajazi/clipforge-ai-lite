package com.clipforge.ai.domain.selection

sealed class SelectionTarget {
    data object None : SelectionTarget()

    data class Clip(val id: String) : SelectionTarget() {
        init {
            require(id.isNotBlank()) { "Clip selection id must not be blank" }
        }
    }

    data class Effect(val id: String) : SelectionTarget() {
        init {
            require(id.isNotBlank()) { "Effect selection id must not be blank" }
        }
    }

    data class TextOverlay(val id: String) : SelectionTarget() {
        init {
            require(id.isNotBlank()) { "Text overlay selection id must not be blank" }
        }
    }

    val clipId: String?
        get() = (this as? Clip)?.id

    val effectId: String?
        get() = (this as? Effect)?.id

    val textOverlayId: String?
        get() = (this as? TextOverlay)?.id

    fun toSnapshot(): SelectionSnapshot = when (this) {
        is Clip -> SelectionSnapshot(type = SelectionSnapshot.Type.CLIP, id = id)
        is Effect -> SelectionSnapshot(type = SelectionSnapshot.Type.EFFECT, id = id)
        is TextOverlay -> SelectionSnapshot(type = SelectionSnapshot.Type.TEXT_OVERLAY, id = id)
        None -> SelectionSnapshot(type = SelectionSnapshot.Type.NONE, id = null)
    }

    companion object {
        fun fromSnapshot(snapshot: SelectionSnapshot): SelectionTarget = when (snapshot.type) {
            SelectionSnapshot.Type.NONE -> None
            SelectionSnapshot.Type.CLIP -> Clip(requireNotNull(snapshot.id) { "Clip selection snapshot requires an id" })
            SelectionSnapshot.Type.EFFECT -> Effect(requireNotNull(snapshot.id) { "Effect selection snapshot requires an id" })
            SelectionSnapshot.Type.TEXT_OVERLAY -> TextOverlay(
                requireNotNull(snapshot.id) { "Text overlay selection snapshot requires an id" }
            )
        }
    }
}

data class SelectionSnapshot(
    val type: Type,
    val id: String?
) {
    enum class Type {
        NONE,
        CLIP,
        EFFECT,
        TEXT_OVERLAY
    }
}
