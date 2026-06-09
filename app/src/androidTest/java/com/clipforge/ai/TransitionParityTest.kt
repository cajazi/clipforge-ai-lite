package com.clipforge.ai

import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.core.transition.OverlayRendererParityHarness
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * Phase C dual-run parity gate. Drives [OverlayRendererParityHarness] over the real on-device
 * projects (2-clip + 1-transition each) and asserts every transition op's adapter output
 * matches the render-plan op (item count + A-tail clip window).
 *
 * This is a validation harness, not production code. It touches no app behavior: the registry
 * adapters are exercised in isolation, the live export path is untouched. Requires the named
 * projects to exist on the connected device.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransitionParityTest {

    private val projects = mapOf(
        "SLIDE_RIGHT" to "175b758e-07a5-4ff7-a1b7-b461a71b85cb",
        "FADE_DIP" to "193383c4-178b-4297-bf86-c834f7b00aab",
        "ZOOM_IN" to "b6134994-5545-4371-97bd-c3982e94767e",
        "WHIP_PAN_LEFT" to "bd2d30ba-d660-4239-89ba-67a5ff42e25d"
    )

    @Test
    fun adapters_match_render_plan_for_all_device_projects() = runBlocking {
        val context = ApplicationProvider.getApplicationContext<android.content.Context>()
        var totalTransitions = 0
        for ((family, projectId) in projects) {
            val results = OverlayRendererParityHarness.validate(context, projectId)
            totalTransitions += results.size
            results.forEach { r ->
                assertTrue(
                    "PARITY_FAIL $family op[${r.index}]=${r.family} emitted=${r.emittedItems}/${r.expectedItems} " +
                        "clip=[${r.firstItemClipStartMs}..${r.firstItemClipEndMs}] expected=[${r.expectedClipStartMs}..${r.expectedClipEndMs}] err=${r.error}",
                    r.pass
                )
            }
        }
        assertTrue("no transitions were validated", totalTransitions >= 1)
    }
}
