package com.clipforge.ai.domain.history

import android.graphics.Color
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.repository.TextOverlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class AddTextOverlayCommandTest {
    @Test
    fun `execute upserts exact text overlay snapshot`() = runBlocking {
        val repository = FakeTextOverlayRepository()
        val overlay = textOverlay(id = "text-1")

        AddTextOverlayCommand(repository, overlay).execute()

        assertEquals(listOf(overlay), repository.getTextOverlaysForProject(PROJECT_ID))
    }

    @Test
    fun `undo deletes exact text overlay id`() = runBlocking {
        val overlay = textOverlay(id = "text-1")
        val other = textOverlay(id = "text-2")
        val repository = FakeTextOverlayRepository(listOf(overlay, other))

        AddTextOverlayCommand(repository, overlay).undo()

        assertEquals(listOf(other), repository.getTextOverlaysForProject(PROJECT_ID))
        assertEquals(listOf("text-1"), repository.deletedIds)
    }

    @Test
    fun `redo restores same id without duplicate rows`() = runBlocking {
        val repository = FakeTextOverlayRepository()
        val registry = HistoryRegistry()
        val overlay = textOverlay(id = "text-1")

        registry.execute(AddTextOverlayCommand(repository, overlay))
        registry.undo()
        registry.redo()
        registry.redo()

        val rows = repository.getTextOverlaysForProject(PROJECT_ID)
        assertEquals(listOf("text-1"), rows.map { it.id })
        assertEquals(listOf(overlay), rows)
    }

    @Test
    fun `command does not mutate text overlay snapshot`() = runBlocking {
        val repository = FakeTextOverlayRepository()
        val overlay = textOverlay(
            id = "text-1",
            transform = OverlayTransform(
                xNorm = 0.25f,
                yNorm = 0.7f,
                scale = 1.4f,
                rotationDeg = 12f,
                alpha = 0.8f
            )
        )
        val original = overlay.copy()

        val command = AddTextOverlayCommand(repository, overlay)
        command.execute()
        command.undo()
        command.execute()

        assertEquals(original, overlay)
    }

    @Test
    fun `undo removes overlay from observed repository flow`() = runBlocking {
        val repository = FakeTextOverlayRepository()
        val registry = HistoryRegistry()
        val overlay = textOverlay(id = "text-1")

        registry.execute(AddTextOverlayCommand(repository, overlay))
        registry.undo()

        assertEquals(emptyList<TextOverlay>(), repository.observeTextOverlaysForProject(PROJECT_ID).first())
    }

    @Test
    fun `redo restores overlay to observed repository flow`() = runBlocking {
        val repository = FakeTextOverlayRepository()
        val registry = HistoryRegistry()
        val overlay = textOverlay(id = "text-1")

        registry.execute(AddTextOverlayCommand(repository, overlay))
        registry.undo()
        registry.redo()

        assertEquals(listOf(overlay), repository.observeTextOverlaysForProject(PROJECT_ID).first())
    }

    private class FakeTextOverlayRepository(
        initial: List<TextOverlay> = emptyList()
    ) : TextOverlayRepository {
        private val rows = MutableStateFlow(initial)
        val deletedIds = mutableListOf<String>()

        override suspend fun getTextOverlaysForProject(projectId: String): List<TextOverlay> =
            rows.value.filter { it.projectId == projectId }

        override fun observeTextOverlaysForProject(projectId: String): Flow<List<TextOverlay>> =
            MutableStateFlow(rows.value.filter { it.projectId == projectId })

        override suspend fun upsertTextOverlay(textOverlay: TextOverlay) {
            rows.value = rows.value.filterNot { it.id == textOverlay.id } + textOverlay
        }

        override suspend fun upsertTextOverlays(textOverlays: List<TextOverlay>) {
            rows.value = rows.value.filterNot { row -> textOverlays.any { it.id == row.id } } + textOverlays
        }

        override suspend fun deleteTextOverlay(id: String) {
            deletedIds += id
            rows.value = rows.value.filterNot { it.id == id }
        }

        override suspend fun deleteTextOverlaysForProject(projectId: String) {
            rows.value = rows.value.filterNot { it.projectId == projectId }
        }
    }

    private fun textOverlay(
        id: String,
        transform: OverlayTransform = OverlayTransform(
            xNorm = 0.5f,
            yNorm = 0.5f,
            scale = 1f,
            rotationDeg = 0f,
            alpha = 1f
        )
    ): TextOverlay =
        TextOverlay(
            id = id,
            projectId = PROJECT_ID,
            windowStartMs = 1_000L,
            windowEndMs = 4_000L,
            layer = OverlayLayer.USER,
            zIndex = 3,
            transform = transform,
            renderSpec = TextRenderSpec(
                text = "Caption",
                fontId = "default",
                fontSizeNorm = 0.08f,
                colorArgb = Color.WHITE,
                bgColorArgb = null,
                bold = false,
                italic = false,
                alignment = TextAlignment.CENTER
            )
        )

    private companion object {
        const val PROJECT_ID = "project"
    }
}
