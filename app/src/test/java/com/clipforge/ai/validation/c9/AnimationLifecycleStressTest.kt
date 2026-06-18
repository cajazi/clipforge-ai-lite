package com.clipforge.ai.validation.c9

import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPropertyKeys
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.animation.AnimationWindowResolver
import com.clipforge.ai.core.animation.ClipAnimationRewriter
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.domain.history.ApplyComboAnimationCommand
import com.clipforge.ai.domain.history.ApplyInAnimationCommand
import com.clipforge.ai.domain.history.ApplyOutAnimationCommand
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.history.UndoableCommand
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.domain.model.EffectParamValue
import com.clipforge.ai.domain.repository.EffectRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

/**
 * C9.0 validation: randomized clip/animation lifecycle sequences must never corrupt animation
 * state. Oracles are the existing, unmodified [AnimationWindowResolver], [ClipAnimationRewriter],
 * [AnimationEffectId] and the production [com.clipforge.ai.domain.history] commands — this test
 * only drives them with randomized input and checks invariants, it does not change their behavior.
 */
class AnimationLifecycleStressTest {

    @Test
    fun `randomized operation sequences preserve animation invariants`() {
        repeat(NUM_SEEDS) { seedIndex ->
            runBlocking { Harness(seed = 1000L + seedIndex).run(OPS_PER_SEED) }
        }
    }

    private data class ClipState(
        val id: String,
        val startMs: Long,
        val endMs: Long,
        val incomingTransitionMs: Long,
        val outgoingTransitionMs: Long
    ) {
        val durationMs: Long get() = endMs - startMs
    }

    private data class Snapshot(
        val clips: List<ClipState>,
        val effects: List<EffectItem>
    )

    private class FakeEffectRepository(initial: List<EffectItem> = emptyList()) : EffectRepository {
        val effects = MutableStateFlow(initial)

        override suspend fun getEffectsForProject(projectId: String): List<EffectItem> =
            effects.value.filter { it.projectId == projectId }

        override fun observeEffectsForProject(projectId: String): Flow<List<EffectItem>> = effects

        override suspend fun upsertEffect(effect: EffectItem) {
            effects.value = effects.value.filterNot { it.id == effect.id } + effect
        }

        override suspend fun deleteEffect(id: String) {
            effects.value = effects.value.filterNot { it.id == id }
        }

        override suspend fun deleteEffectsForProject(projectId: String) {
            effects.value = effects.value.filterNot { it.projectId == projectId }
        }

        fun replaceAll(next: List<EffectItem>) {
            effects.value = next
        }
    }

    private inner class StateCommand(
        private val repository: FakeEffectRepository,
        private val before: Snapshot,
        private val after: Snapshot,
        private val applyClips: (List<ClipState>) -> Unit
    ) : UndoableCommand {
        override val label: String = "StructuralOp"

        override suspend fun execute() {
            repository.replaceAll(after.effects)
            applyClips(after.clips)
        }

        override suspend fun undo() {
            repository.replaceAll(before.effects)
            applyClips(before.clips)
        }
    }

    private inner class Harness(seed: Long) {
        private val random = Random(seed)
        private val repository = FakeEffectRepository()
        private val registry = HistoryRegistry()
        private var clips: List<ClipState> = listOf(
            ClipState("clip-0", 0L, 1_000L, 0L, 0L)
        )
        private val history = mutableListOf(Snapshot(clips, emptyList()))
        private var cursor = 0
        private var duplicateCounter = 0

        suspend fun run(opCount: Int) {
            repeat(opCount) {
                val op = Op.entries[random.nextInt(Op.entries.size)]
                runCatching { perform(op) }
                    .onFailure { throw AssertionError("seed-driven op=$op failed: ${it.message}", it) }
                assertInvariants("after op=$op")
            }
        }

        private suspend fun perform(op: Op) {
            when (op) {
                Op.APPLY_IN -> applyRole(AnimationRole.IN)
                Op.APPLY_OUT -> applyRole(AnimationRole.OUT)
                Op.APPLY_COMBO -> applyRole(AnimationRole.COMBO)
                Op.ADJUST_DURATION -> adjustDuration()
                Op.TRIM -> trim()
                Op.SPLIT -> split()
                Op.DUPLICATE -> duplicate()
                Op.REORDER -> reorder()
                Op.DELETE -> delete()
                Op.UNDO_N -> undoN()
                Op.REDO_N -> redoN()
            }
        }

        private fun randomClip(): ClipState = clips[random.nextInt(clips.size)]

        private suspend fun applyRole(role: AnimationRole) {
            val clip = randomClip()
            val durationMs = random.nextLong(50L, 400L)
            val window = AnimationWindowResolver.resolve(
                clipStartMs = clip.startMs,
                clipEndMs = clip.endMs,
                requestedDurationMs = durationMs,
                role = role,
                incomingTransitionDurationMs = clip.incomingTransitionMs,
                outgoingTransitionDurationMs = clip.outgoingTransitionMs
            ) ?: return

            val effect = EffectItem(
                id = AnimationEffectId.of(clip.id, role),
                projectId = PROJECT_ID,
                effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
                scope = EffectScope.CLIP,
                startMs = window.startMs,
                endMs = window.endMs,
                zOrder = 0,
                params = mapOf(AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(1f))
            )
            val command = when (role) {
                AnimationRole.IN -> ApplyInAnimationCommand(repository, PROJECT_ID, clip.id, effect)
                AnimationRole.OUT -> ApplyOutAnimationCommand(repository, PROJECT_ID, clip.id, effect)
                AnimationRole.COMBO -> ApplyComboAnimationCommand(repository, PROJECT_ID, clip.id, effect)
            }
            executeAndRecord(command)
        }

        private suspend fun adjustDuration() {
            val withAnimations = clips.filter { clip -> repository.effects.value.any { roleClipId(it) == clip.id } }
            if (withAnimations.isEmpty()) return
            val clip = withAnimations[random.nextInt(withAnimations.size)]
            val existingRole = repository.effects.value
                .mapNotNull { AnimationEffectId.parse(it.id) }
                .firstOrNull { it.clipId == clip.id }
                ?.role ?: return
            val durationMs = random.nextLong(30L, 500L)
            val window = AnimationWindowResolver.resolve(
                clipStartMs = clip.startMs,
                clipEndMs = clip.endMs,
                requestedDurationMs = durationMs,
                role = existingRole,
                incomingTransitionDurationMs = clip.incomingTransitionMs,
                outgoingTransitionDurationMs = clip.outgoingTransitionMs
            ) ?: return
            val effect = EffectItem(
                id = AnimationEffectId.of(clip.id, existingRole),
                projectId = PROJECT_ID,
                effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
                scope = EffectScope.CLIP,
                startMs = window.startMs,
                endMs = window.endMs,
                zOrder = 0,
                params = mapOf(AnimationPropertyKeys.OPACITY to EffectParamValue.Constant(0.5f))
            )
            val command = when (existingRole) {
                AnimationRole.IN -> ApplyInAnimationCommand(repository, PROJECT_ID, clip.id, effect)
                AnimationRole.OUT -> ApplyOutAnimationCommand(repository, PROJECT_ID, clip.id, effect)
                AnimationRole.COMBO -> ApplyComboAnimationCommand(repository, PROJECT_ID, clip.id, effect)
            }
            executeAndRecord(command)
        }

        private suspend fun trim() {
            val index = random.nextInt(clips.size)
            val clip = clips[index]
            val floor = clip.incomingTransitionMs + clip.outgoingTransitionMs + 30L
            if (clip.durationMs <= floor) return
            val shrink = random.nextLong(10L, (clip.durationMs - floor).coerceAtLeast(1L) + 1L)
            val nextClips = clips.toMutableList()
            nextClips[index] = clip.copy(endMs = clip.endMs - shrink)
            applyStructuralChange(reflow(nextClips))
        }

        private suspend fun split() {
            val candidates = clips.filter { it.durationMs >= 200L }
            if (candidates.isEmpty() || clips.size >= MAX_CLIPS) return
            val clip = candidates[random.nextInt(candidates.size)]
            val index = clips.indexOf(clip)
            val splitOffsetMs = clip.durationMs * random.nextLong(40L, 61L) / 100L
            val splitAbsMs = clip.startMs + splitOffsetMs
            val left = ClipState("${clip.id}-a", clip.startMs, splitAbsMs, clip.incomingTransitionMs, 0L)
            val right = ClipState("${clip.id}-b", splitAbsMs, clip.endMs, 0L, clip.outgoingTransitionMs)

            val nextClips = clips.toMutableList()
            nextClips.removeAt(index)
            nextClips.addAll(index, listOf(left, right))
            val reflowed = reflow(nextClips)

            val effectsAfterDelete = ClipAnimationRewriter.deleteClipAnimations(repository.effects.value, clip.id)
            applyStructuralChange(reflowed, preComputedEffects = effectsAfterDelete)
        }

        private suspend fun duplicate() {
            if (clips.size >= MAX_CLIPS) return
            val clip = randomClip()
            val index = clips.indexOf(clip)
            val newClipId = "${clip.id}-dup-${duplicateCounter++}"
            val duplicateClip = ClipState(newClipId, 0L, 0L, 0L, 0L)
            val nextClips = clips.toMutableList()
            nextClips.add(index + 1, duplicateClip)
            val reflowed = reflow(nextClips)
            val windows = toWindows(reflowed)
            val nextEffects = ClipAnimationRewriter.duplicateClipAnimations(
                effects = repository.effects.value,
                sourceClipId = clip.id,
                newClipId = newClipId,
                windows = windows
            )
            applyStructuralChange(reflowed, preComputedEffects = nextEffects)
        }

        private suspend fun reorder() {
            if (clips.size < 2) return
            val i = random.nextInt(clips.size)
            var j = random.nextInt(clips.size)
            if (j == i) j = (j + 1) % clips.size
            val nextClips = clips.toMutableList()
            val tmp = nextClips[i]
            nextClips[i] = nextClips[j]
            nextClips[j] = tmp
            applyStructuralChange(reflow(nextClips))
        }

        private suspend fun delete() {
            if (clips.size <= 1) return
            val index = random.nextInt(clips.size)
            val clip = clips[index]
            val nextClips = clips.toMutableList()
            nextClips.removeAt(index)
            val reflowed = reflow(nextClips)
            val effectsAfterDelete = ClipAnimationRewriter.deleteClipAnimations(repository.effects.value, clip.id)
            applyStructuralChange(reflowed, preComputedEffects = effectsAfterDelete)
        }

        private suspend fun applyStructuralChange(
            nextClips: List<ClipState>,
            preComputedEffects: List<EffectItem> = repository.effects.value
        ) {
            val windows = toWindows(nextClips)
            val nextEffects = ClipAnimationRewriter.resolveWindows(preComputedEffects, windows)
            val before = Snapshot(clips, repository.effects.value)
            val after = Snapshot(nextClips, nextEffects)
            val command = StateCommand(repository, before, after) { clips = it }
            executeAndRecord(command)
        }

        private suspend fun undoN() {
            val n = random.nextInt(1, 4)
            repeat(n) {
                if (!registry.state.value.canUndo) return
                val ok = registry.undo()
                if (!ok) return
                cursor--
                assertStateMatches("undo step")
            }
        }

        private suspend fun redoN() {
            val n = random.nextInt(1, 4)
            repeat(n) {
                if (!registry.state.value.canRedo) return
                val ok = registry.redo()
                if (!ok) return
                cursor++
                assertStateMatches("redo step")
            }
        }

        private suspend fun executeAndRecord(command: UndoableCommand) {
            registry.execute(command)
            cursor++
            while (history.size > cursor) history.removeAt(history.size - 1)
            history.add(Snapshot(clips, repository.effects.value))
        }

        private fun assertStateMatches(context: String) {
            val expected = history[cursor]
            assertEquals("$context: clips mismatch", expected.clips, clips)
            assertEquals(
                "$context: effects mismatch",
                expected.effects.sortedBy { it.id },
                repository.effects.value.sortedBy { it.id }
            )
        }

        private fun reflow(input: List<ClipState>): List<ClipState> {
            var cursorMs = 0L
            return input.map { clip ->
                val duration = clip.durationMs.takeIf { it > 0L } ?: DEFAULT_NEW_CLIP_DURATION_MS
                val start = cursorMs
                val end = start + duration
                cursorMs = end
                clip.copy(startMs = start, endMs = end)
            }
        }

        private fun toWindows(input: List<ClipState>): List<ClipAnimationRewriter.ClipWindow> =
            input.map {
                ClipAnimationRewriter.ClipWindow(
                    clipId = it.id,
                    startMs = it.startMs,
                    endMs = it.endMs,
                    incomingTransitionDurationMs = it.incomingTransitionMs,
                    outgoingTransitionDurationMs = it.outgoingTransitionMs
                )
            }

        private fun roleClipId(effect: EffectItem): String? = AnimationEffectId.parse(effect.id)?.clipId

        fun assertInvariants(context: String) {
            val effects = repository.effects.value
            val clipIds = clips.map { it.id }.toSet()
            assertEquals("$context: clip ids must be unique", clips.size, clipIds.size)
            assertEquals("$context: effect ids must be unique", effects.size, effects.map { it.id }.distinct().size)

            effects.forEach { effect ->
                val parsed = AnimationEffectId.parse(effect.id)
                assertNotNull("$context: effect id must parse as anim-* id: ${effect.id}", parsed)
                requireNotNull(parsed)

                // Invariant: deterministic ID round-trip.
                assertEquals(
                    "$context: id must round-trip deterministically",
                    effect.id,
                    AnimationEffectId.of(parsed.clipId, parsed.role)
                )

                // Invariant: no orphan anim-* effects (clipId must reference a live clip).
                val clip = clips.find { it.id == parsed.clipId }
                assertNotNull("$context: orphan animation for missing clip '${parsed.clipId}'", clip)
                requireNotNull(clip)

                // Invariant: window remains inside the clip's clean span.
                assertTrue(
                    "$context: window start before clean span start (${effect.startMs} < ${clip.startMs + clip.incomingTransitionMs})",
                    effect.startMs >= clip.startMs + clip.incomingTransitionMs
                )
                assertTrue(
                    "$context: window end after clean span end (${effect.endMs} > ${clip.endMs - clip.outgoingTransitionMs})",
                    effect.endMs <= clip.endMs - clip.outgoingTransitionMs
                )
                assertTrue("$context: window must be non-degenerate", effect.startMs <= effect.endMs)
            }
        }
    }

    private enum class Op {
        APPLY_IN, APPLY_OUT, APPLY_COMBO, ADJUST_DURATION,
        TRIM, SPLIT, DUPLICATE, REORDER, DELETE,
        UNDO_N, REDO_N
    }

    private companion object {
        const val PROJECT_ID = "c9-lifecycle-stress"
        const val NUM_SEEDS = 10
        const val OPS_PER_SEED = 50
        const val MAX_CLIPS = 8
        const val DEFAULT_NEW_CLIP_DURATION_MS = 600L
    }
}
