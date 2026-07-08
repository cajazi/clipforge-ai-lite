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
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test

class MoveTextOverlayCommandTest {

    @Test
    fun `execute upserts moved transform without changing overlay identity`() = runBlocking {
        val before = textOverlay(id = "text-1", xNorm = 0.5f, yNorm = 0.5f)
        val after = before.copy(transform = before.transform.copy(xNorm = 0.2f, yNorm = 0.8f))
        val repository = FakeTextOverlayRepository(listOf(before))

        MoveTextOverlayCommand(repository, before, after).execute()

        assertEquals(listOf(after), repository.getTextOverlaysForProject(PROJECT_ID))
        assertEquals(listOf("text-1"), repository.getTextOverlaysForProject(PROJECT_ID).map { it.id })
    }

    @Test
    fun `undo restores previous transform`() = runBlocking {
        val before = textOverlay(id = "text-1", xNorm = 0.5f, yNorm = 0.5f)
        val after = before.copy(transform = before.transform.copy(xNorm = 0.2f, yNorm = 0.8f))
        val repository = FakeTextOverlayRepository(listOf(after))

        MoveTextOverlayCommand(repository, before, after).undo()

        assertEquals(listOf(before), repository.getTextOverlaysForProject(PROJECT_ID))
    }

    @Test
    fun `redo restores moved transform without duplicate rows`() = runBlocking {
        val before = textOverlay(id = "text-1", xNorm = 0.5f, yNorm = 0.5f)
        val after = before.copy(transform = before.transform.copy(xNorm = 0.2f, yNorm = 0.8f))
        val repository = FakeTextOverlayRepository(listOf(before))
        val registry = HistoryRegistry()

        registry.execute(MoveTextOverlayCommand(repository, before, after))
        registry.undo()
        registry.redo()
        registry.redo()

        assertEquals(listOf(after), repository.getTextOverlaysForProject(PROJECT_ID))
    }

    private class FakeTextOverlayRepository(
        initial: List<TextOverlay> = emptyList()
    ) : TextOverlayRepository {
        private val rows = MutableStateFlow(initial)

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

    private fun textOverlay(id: String, xNorm: Float, yNorm: Float): TextOverlay =
        TextOverlay(
            id = id,
            projectId = PROJECT_ID,
            windowStartMs = 0L,
            windowEndMs = 3_000L,
            layer = OverlayLayer.USER,
            zIndex = 0,
            transform = OverlayTransform(
                xNorm = xNorm,
                yNorm = yNorm,
                scale = 1f,
                rotationDeg = 0f,
                alpha = 1f
            ),
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
