package com.clipforge.ai.presentation.timeline

import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.domain.history.AddTextOverlayCommand
import com.clipforge.ai.domain.history.HistoryRegistry
import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.repository.TextOverlayRepository
import com.clipforge.ai.domain.selection.SelectionController
import com.clipforge.ai.domain.selection.SelectionTarget
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TextToolUiStateTest {

    @Test
    fun `text toolbar opens from primary text tap and back returns to main toolbar`() {
        val state = TextToolUiState().openRow()

        assertEquals(TextToolPanel.Row, state.panel)
        assertEquals(TextToolPanel.Hidden, state.closeTool().panel)
    }

    @Test
    fun `add text action opens composer at playhead`() {
        val state = TextToolUiState().openRow().openComposer(startMs = 1_250L, overlayId = OVERLAY_ID)

        assertEquals(TextToolPanel.Composer, state.panel)
        assertEquals(1_250L, state.draftStartMs)
        assertEquals(OVERLAY_ID, state.draftOverlayId)
        assertEquals(DefaultTextOverlayTransform, state.draftTransform)
        assertFalse(state.confirmEnabled)
    }

    @Test
    fun `typed draft enables confirm and creates preview-only overlay`() {
        val state = TextToolUiState()
            .openComposer(startMs = 2_000L, overlayId = OVERLAY_ID)
            .updateDraftText("  Hello  ")

        val draft = createDraftTextOverlay(
            projectId = PROJECT_ID,
            state = state,
            totalDurationMs = 10_000L,
            zIndex = 4
        )

        assertTrue(state.confirmEnabled)
        assertNotNull(draft)
        requireNotNull(draft)
        assertEquals(OVERLAY_ID, draft.id)
        assertEquals("Hello", draft.renderSpec.text)
        assertEquals(2_000L, draft.windowStartMs)
        assertEquals(4, draft.zIndex)
    }

    @Test
    fun `blank draft does not create preview or committed overlay`() {
        val state = TextToolUiState()
            .openComposer(startMs = 0L, overlayId = OVERLAY_ID)
            .updateDraftText("   ")

        assertNull(createDraftTextOverlay(PROJECT_ID, state, totalDurationMs = 10_000L, zIndex = 0))
        assertNull(createCommittedTextOverlay(PROJECT_ID, state, totalDurationMs = 10_000L, zIndex = 0))
    }

    @Test
    fun `each composer open gets a fresh overlay id`() {
        val first = TextToolUiState().openComposer(startMs = 500L).draftOverlayId
        val second = TextToolUiState().openComposer(startMs = 500L).draftOverlayId

        assertNotNull(first)
        assertNotNull(second)
        assertNotEquals(first, second)
    }

    @Test
    fun `draft and committed overlay reuse the same per-open id`() {
        val state = TextToolUiState()
            .openComposer(startMs = 500L, overlayId = OVERLAY_ID)
            .updateDraftText("Title")

        val draft = createDraftTextOverlay(
            projectId = PROJECT_ID,
            state = state,
            totalDurationMs = 4_000L,
            zIndex = 2
        )
        val overlay = createCommittedTextOverlay(
            projectId = PROJECT_ID,
            state = state,
            totalDurationMs = 4_000L,
            zIndex = 2
        )

        assertNotNull(overlay)
        assertNotNull(draft)
        requireNotNull(draft)
        requireNotNull(overlay)
        assertEquals(OVERLAY_ID, draft.id)
        assertEquals(draft.id, overlay.id)
        assertEquals("Title", overlay.renderSpec.text)
        assertEquals(500L, overlay.windowStartMs)
        assertEquals(2, overlay.zIndex)
    }

    @Test
    fun `draft drag changes draft overlay position`() {
        val draggedTransform = OverlayTransform(
            xNorm = 0.28f,
            yNorm = 0.74f,
            scale = 1f,
            rotationDeg = 0f,
            alpha = 1f
        )
        val state = TextToolUiState()
            .openComposer(startMs = 500L, overlayId = OVERLAY_ID)
            .updateDraftText("Title")
            .updateDraftTransform(draggedTransform)

        val draft = createDraftTextOverlay(
            projectId = PROJECT_ID,
            state = state,
            totalDurationMs = 4_000L,
            zIndex = 2
        )

        assertNotNull(draft)
        requireNotNull(draft)
        assertEquals(draggedTransform, draft.transform)
    }

    @Test
    fun `confirm persists dragged draft position`() {
        val draggedTransform = moveTextOverlayTransformByPreviewDelta(
            transform = DefaultTextOverlayTransform,
            deltaXPx = 100f,
            deltaYPx = -80f,
            frameWidthPx = 1_000,
            frameHeightPx = 2_000
        )
        val state = TextToolUiState()
            .openComposer(startMs = 500L, overlayId = OVERLAY_ID)
            .updateDraftText("Title")
            .updateDraftTransform(draggedTransform)

        val overlay = createCommittedTextOverlay(
            projectId = PROJECT_ID,
            state = state,
            totalDurationMs = 4_000L,
            zIndex = 2
        )

        assertNotNull(overlay)
        requireNotNull(overlay)
        assertEquals(OVERLAY_ID, overlay.id)
        assertEquals(draggedTransform, overlay.transform)
    }

    @Test
    fun `after commit returns to text row and clears draft`() {
        val state = TextToolUiState()
            .openComposer(startMs = 500L, overlayId = OVERLAY_ID)
            .updateDraftText("Title")
            .afterCommit()

        assertEquals(TextToolPanel.Row, state.panel)
        assertEquals("", state.draftText)
        assertNull(state.draftOverlayId)
        assertEquals(DefaultTextOverlayTransform, state.draftTransform)
        assertFalse(state.confirmEnabled)
    }

    @Test
    fun `non add text row items stay visually enabled and route to coming soon`() {
        val enabled = textToolRowActions.filter { it.enabled }.map { it.label }

        assertEquals(textToolRowActions.map { it.label }, enabled)
        assertEquals(
            listOf("Auto Captions", "Stickers", "Draw", "Text template", "Text to audio", "Auto lyrics"),
            comingSoonTextToolLabels
        )
    }

    @Test
    fun `commit path adds lane overlay selects it and undo redo keeps one row`() = runBlocking {
        val state = TextToolUiState()
            .openComposer(startMs = 1_000L, overlayId = OVERLAY_ID)
            .updateDraftText("Caption")
        val overlay = requireNotNull(
            createCommittedTextOverlay(
                projectId = PROJECT_ID,
                state = state,
                totalDurationMs = 10_000L,
                zIndex = 0
            )
        )
        val repository = FakeTextOverlayRepository()
        val registry = HistoryRegistry()
        val selectionController = SelectionController()

        registry.execute(AddTextOverlayCommand(repository, overlay))
        selectionController.restore(SelectionTarget.TextOverlay(overlay.id).toSnapshot())

        assertEquals(listOf(overlay), repository.getTextOverlaysForProject(PROJECT_ID))
        assertEquals(overlay.id, selectedTextOverlayId(selectionController.current))

        registry.undo()
        assertEquals(emptyList<TextOverlay>(), repository.getTextOverlaysForProject(PROJECT_ID))

        registry.redo()
        assertEquals(listOf(overlay), repository.getTextOverlaysForProject(PROJECT_ID))
        assertEquals(OVERLAY_ID, repository.getTextOverlaysForProject(PROJECT_ID).single().id)
    }

    @Test
    fun `stable commit id does not duplicate overlay rows`() = runBlocking {
        val state = TextToolUiState()
            .openComposer(startMs = 1_000L, overlayId = OVERLAY_ID)
            .updateDraftText("Caption")
        val overlay = requireNotNull(
            createCommittedTextOverlay(
                projectId = PROJECT_ID,
                state = state,
                totalDurationMs = 10_000L,
                zIndex = 0
            )
        )
        val repository = FakeTextOverlayRepository()

        repository.upsertTextOverlay(overlay)
        repository.upsertTextOverlay(overlay)

        assertEquals(listOf(overlay), repository.getTextOverlaysForProject(PROJECT_ID))
    }

    private class FakeTextOverlayRepository : TextOverlayRepository {
        private val rows = MutableStateFlow(emptyList<TextOverlay>())

        override suspend fun getTextOverlaysForProject(projectId: String): List<TextOverlay> =
            rows.value.filter { it.projectId == projectId }

        override fun observeTextOverlaysForProject(projectId: String): Flow<List<TextOverlay>> =
            rows

        override suspend fun upsertTextOverlay(textOverlay: TextOverlay) {
            rows.value = rows.value.filterNot { it.id == textOverlay.id } + textOverlay
        }

        override suspend fun upsertTextOverlays(textOverlays: List<TextOverlay>) {
            rows.value = rows.value.filterNot { row -> textOverlays.any { it.id == row.id } } + textOverlays
        }

        override suspend fun deleteTextOverlay(id: String) {
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun deleteTextOverlaysForProject(projectId: String) {
            rows.value = rows.value.filterNot { it.projectId == projectId }
        }
    }

    private companion object {
        const val PROJECT_ID = "project"
        const val OVERLAY_ID = "8b5d9772-d083-42b5-b648-7755386a2c5e"
    }
}
