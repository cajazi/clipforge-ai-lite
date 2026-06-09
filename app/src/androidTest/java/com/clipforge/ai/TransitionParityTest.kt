package com.clipforge.ai

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.transition.SegmentContext
import com.clipforge.ai.core.transition.TransitionRegistrations
import com.clipforge.ai.core.transition.TransitionRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Phase C 6/6 closure — runtime validation of the DISSOLVE and FADE_WHITE adapter code paths.
 *
 * The other 4 families (FADE_BLACK / SLIDE / ZOOM / WHIP_PAN) were validated plan-driven via
 * OverlayRendererParityHarness against on-device projects. DISSOLVE and FADE_WHITE had no
 * on-device project, and the app purges hand-seeded unauthenticated projects on startup, so a
 * plan-driven run is not possible without an authenticated UI flow.
 *
 * This validates the two adapters directly with a SYNTHETIC SegmentContext over an imported
 * real media MP4 under files/media, exercising the actual runtime paths that differ from the other
 * families: DISSOLVE's default-ctor CrossfadeFrameCache + CrossfadeBitmapOverlay, and the
 * FADE_WHITE colorInt branch of DipToColorTransitionRenderer. It asserts emitted item count
 * and A-tail clip window, and (for DISSOLVE) that the frame cache actually built.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransitionParityTest {

    private val aTailStartMs = 1200L
    private val aEndMs = 2000L
    private val durationMs = 800L

    private fun ctxFor(path: String, params: Map<String, String>) = SegmentContext(
        context = ApplicationProvider.getApplicationContext(),
        outputWidthPx = 720,
        outputHeightPx = 1280,
        pathA = path,
        aTailStartMs = aTailStartMs,
        aEndMs = aEndMs,
        pathB = path,
        bHeadStartMs = 0L,
        durationMs = durationMs,
        compositionStartUs = 0L,
        params = params
    )

    private fun seedPath(): String {
        val ctx = ApplicationProvider.getApplicationContext<android.content.Context>()
        val mediaDir = File(ctx.filesDir, "media")
        val candidates = mediaDir
            .listFiles { file -> file.isFile && file.extension.equals("mp4", ignoreCase = true) }
            .orEmpty()
            .sortedBy { it.name }
        assertTrue(
            "seed media missing: no *.mp4 files under ${mediaDir.absolutePath}",
            candidates.isNotEmpty()
        )
        return candidates.first().absolutePath
    }

    @Test
    fun dissolve_adapter_emits_on_real_media() {
        TransitionRegistrations.registerBuiltIns()
        val renderer = TransitionRegistry.get(TransitionRegistrations.DISSOLVE)!!.renderer!!
        val cleanups = ArrayList<() -> Unit>()
        try {
            val items = renderer.emit(ctxFor(seedPath(), emptyMap())) { cleanups.add(it) }
            assertEquals("DISSOLVE should emit 1 item", 1, items.size)
            val clip = items[0].mediaItem.clippingConfiguration
            assertEquals(aTailStartMs, clip.startPositionMs)
            assertEquals(aEndMs, clip.endPositionMs)
            assertTrue("DISSOLVE must register cache cleanup (cache built)", cleanups.isNotEmpty())
        } finally {
            cleanups.forEach { runCatching { it() } }
        }
    }

    @Test
    fun fade_white_adapter_emits_two_items() {
        TransitionRegistrations.registerBuiltIns()
        val renderer = TransitionRegistry.get(TransitionRegistrations.FADE_WHITE)!!.renderer!!
        val cleanups = ArrayList<() -> Unit>()
        try {
            val params = mapOf(
                "halfDurationMs" to "400",
                "colorInt" to android.graphics.Color.WHITE.toString(),
                "bHeadEndMs" to "400"
            )
            val items = renderer.emit(ctxFor(seedPath(), params)) { cleanups.add(it) }
            assertEquals("FADE_WHITE (dip) should emit 2 items", 2, items.size)
            val clip = items[0].mediaItem.clippingConfiguration
            assertEquals(aTailStartMs, clip.startPositionMs)
            assertEquals(aEndMs, clip.endPositionMs)
        } finally {
            cleanups.forEach { runCatching { it() } }
        }
    }
}
