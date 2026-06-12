package com.clipforge.ai.core.effects

/**
 * What an effect item applies to.
 *
 * V13 foundation ships [GLOBAL] (screen effects) only; [CLIP] exists so persistence and
 * contracts need no migration when clip-scoped effects land (A' spec, deferred decision).
 */
enum class EffectScope {
    GLOBAL,
    CLIP
}
