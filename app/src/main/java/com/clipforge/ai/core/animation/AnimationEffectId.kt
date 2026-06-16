package com.clipforge.ai.core.animation

object AnimationEffectId {
    private const val PREFIX = "anim-"

    data class Parsed(
        val clipId: String,
        val role: AnimationRole
    )

    fun of(clipId: String, role: AnimationRole): String {
        require(clipId.isNotBlank()) { "clipId must not be blank" }
        return "$PREFIX$clipId-${role.name.lowercase()}"
    }

    fun parse(effectId: String): Parsed? {
        if (!effectId.startsWith(PREFIX)) return null
        val role = AnimationRole.entries.firstOrNull { effectId.endsWith("-${it.name.lowercase()}") } ?: return null
        val suffix = "-${role.name.lowercase()}"
        val clipId = effectId.removePrefix(PREFIX).removeSuffix(suffix)
        return clipId.takeIf { it.isNotBlank() }?.let { Parsed(it, role) }
    }
}
