package com.clipforge.ai.data.repository

import android.graphics.Bitmap
import android.graphics.Color
import com.clipforge.ai.core.overlay.OverlayLayer
import com.clipforge.ai.core.overlay.OverlayTransform
import com.clipforge.ai.core.text.TextAlignment
import com.clipforge.ai.core.text.TextOverlayRasterizer
import com.clipforge.ai.core.text.TextRenderSpec
import com.clipforge.ai.domain.model.TextOverlay
import com.clipforge.ai.domain.repository.TextOverlayRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class TextOverlaySourceTest {

    @Test
    fun `source returns zIndex ascending`() = runBlocking {
        val source = TextOverlaySource(
            repository = FakeTextOverlayRepository(
                listOf(
                    textOverlay(id = "top", zIndex = 20),
                    textOverlay(id = "bottom", zIndex = 0),
                    textOverlay(id = "middle", zIndex = 10)
                )
            ),
            rasterizer = NoopRasterizer()
        )

        val loaded = source.load(PROJECT_ID)

        assertEquals(listOf("bottom", "middle", "top"), loaded.map { it.id })
    }

    @Test
    fun `source returns empty list when repository empty`() = runBlocking {
        val source = TextOverlaySource(FakeTextOverlayRepository(), NoopRasterizer())

        val loaded = source.load(PROJECT_ID)

        assertTrue(loaded.isEmpty())
    }

    private class FakeTextOverlayRepository(
        initial: List<TextOverlay> = emptyList()
    ) : TextOverlayRepository {
        private val rows = MutableStateFlow(initial)

        override suspend fun getTextOverlaysForProject(projectId: String): List<TextOverlay> =
            rows.value.filter { it.projectId == projectId }

        override fun observeTextOverlaysForProject(projectId: String): Flow<List<TextOverlay>> = rows

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

    private class NoopRasterizer : TextOverlayRasterizer {
        override fun rasterize(spec: TextRenderSpec, frameW: Int, frameH: Int): Bitmap =
            Bitmap.createBitmap(1, 1, Bitmap.Config.ARGB_8888)
    }

    private fun textOverlay(
        id: String,
        zIndex: Int,
        projectId: String = PROJECT_ID
    ): TextOverlay =
        TextOverlay(
            id = id,
            projectId = projectId,
            windowStartMs = 0L,
            windowEndMs = 1_000L,
            layer = OverlayLayer.USER,
            zIndex = zIndex,
            transform = OverlayTransform(
                xNorm = 0.5f,
                yNorm = 0.5f,
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
