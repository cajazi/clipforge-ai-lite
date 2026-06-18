@file:androidx.annotation.OptIn(markerClass = [androidx.media3.common.util.UnstableApi::class])

package com.clipforge.ai.validation.c9

import android.content.Context
import androidx.media3.exoplayer.ExoPlayer
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.clipforge.ai.ClipForgeApp
import com.clipforge.ai.core.animation.AnimationEffectId
import com.clipforge.ai.core.animation.AnimationPresetType
import com.clipforge.ai.core.animation.AnimationPresets
import com.clipforge.ai.core.animation.AnimationRole
import com.clipforge.ai.core.animation.AnimationTargetType
import com.clipforge.ai.core.animation.AnimationTrackMapper
import com.clipforge.ai.core.effects.AnimationEffectRegistrations
import com.clipforge.ai.core.effects.EffectScope
import com.clipforge.ai.core.effects.ExportEffectRegistry
import com.clipforge.ai.core.player.EffectPreviewController
import com.clipforge.ai.core.player.EffectPreviewPlan
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.domain.history.CommitClipAnimationDraftCommand
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.model.EffectItem
import com.clipforge.ai.validation.c9.support.RepositorySpy
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

/**
 * C9.0: validates the draft-confirm contract end to end using the real production pieces
 * ([EffectPreviewController], [CommitClipAnimationDraftCommand], [HistoryRegistry]) wrapped with
 * [RepositorySpy] - zero repository writes while drafting, exactly one history entry + one write
 * on confirm, zero writes on cancel, and preview/persisted structural parity across confirm.
 */
@RunWith(AndroidJUnit4::class)
class AnimationDraftWorkflowTest {

    private lateinit var player: ExoPlayer
    private lateinit var scope: CoroutineScope

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        InstrumentationRegistry.getInstrumentation().runOnMainSync {
            player = ExoPlayer.Builder(context).build()
        }
        scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    }

    @After
    fun tearDown() {
        scope.cancel()
        InstrumentationRegistry.getInstrumentation().runOnMainSync { player.release() }
    }

    @Test
    fun draft_preview_zero_writes_confirm_writes_once_cancel_writes_none() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val projectId = "c9_draft_${System.currentTimeMillis()}"
        seedEmptyProject(app, projectId)

        val spy = RepositorySpy(app.effectRepository)
        val controller = EffectPreviewController(
            player = player,
            repository = spy,
            scope = scope,
            registry = ExportEffectRegistry.registry,
            logger = {},
            videoEffectsApplier = {}
        )
        controller.bind(projectId)
        delay(350L)
        spy.resetCounts()

        val clipId = "${projectId}_clip1"
        val draftItem = inAnimationItem(projectId, clipId)

        controller.beginAnimationDraft(clipId)
        controller.updateAnimationDraftItems(clipId, listOf(draftItem))
        delay(100L)
        assertEquals("no repository writes while drafting", 0, spy.writeCount)

        val history = HistoryRegistry()
        history.execute(CommitClipAnimationDraftCommand(spy, projectId, clipId, emptyList(), listOf(draftItem)))
        assertEquals("confirm creates exactly one history entry", 1, history.state.value.undoCount)
        assertEquals("confirm performs exactly one write", 1, spy.writeCount)

        val persistedAfterConfirm = spy.getEffectsForProject(projectId).filter { it.id == draftItem.id }
        assertEquals(1, persistedAfterConfirm.size)

        val previewStructuralKeys = EffectPreviewPlan.build(listOf(draftItem), ExportEffectRegistry.registry).structuralKeys
        val persistedStructuralKeys =
            EffectPreviewPlan.build(persistedAfterConfirm, ExportEffectRegistry.registry).structuralKeys
        assertEquals("preview unchanged across confirm", previewStructuralKeys, persistedStructuralKeys)

        controller.endAnimationDraft()
        delay(100L)

        spy.resetCounts()
        val clipId2 = "${projectId}_clip2"
        val cancelledItem = inAnimationItem(projectId, clipId2)
        controller.beginAnimationDraft(clipId2)
        controller.updateAnimationDraftItems(clipId2, listOf(cancelledItem))
        delay(100L)
        controller.endAnimationDraft()
        delay(100L)

        assertEquals("cancel (end draft without commit) performs zero writes", 0, spy.writeCount)
        val afterCancel = spy.getEffectsForProject(projectId).filter { it.id == cancelledItem.id }
        assertTrue("cancel must not persist draft items", afterCancel.isEmpty())

        controller.release()
    }

    private fun inAnimationItem(projectId: String, clipId: String): EffectItem {
        val preset = AnimationPresets.all.first { it.presetType == AnimationPresetType.IN }
        return EffectItem(
            id = AnimationEffectId.of(clipId, AnimationRole.IN),
            projectId = projectId,
            effectId = AnimationEffectRegistrations.TRANSFORM_ANIMATION,
            scope = EffectScope.CLIP,
            startMs = 0L,
            endMs = preset.defaultDurationUs / 1_000L,
            zOrder = 0,
            params = AnimationTrackMapper.toParams(preset.toTrack(AnimationTargetType.CLIP))
        )
    }

    private suspend fun seedEmptyProject(app: ClipForgeApp, projectId: String) {
        val now = System.currentTimeMillis()
        app.database.projectDao().upsertProject(
            ProjectEntity(
                id = projectId,
                title = "C9 $projectId",
                aspectRatio = "RATIO_9_16",
                exportQuality = "QUALITY_720P",
                planType = "FREE",
                thumbnailUri = null,
                createdAt = now,
                updatedAt = now
            )
        )
        app.database.effectItemDao().deleteForProject(projectId)
    }
}
