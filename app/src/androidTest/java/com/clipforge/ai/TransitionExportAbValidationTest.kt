package com.clipforge.ai

import android.content.Context
import android.media.MediaMetadataRetriever
import android.util.Log
import androidx.media3.common.util.UnstableApi
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.clipforge.ai.data.local.entity.MediaAssetEntity
import com.clipforge.ai.data.local.entity.ProjectEntity
import com.clipforge.ai.data.local.entity.TimelineItemEntity
import com.clipforge.ai.domain.model.RenderJobStatus
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/**
 * Device-only A/B export validator for the CrossfadeExecutor registry-dispatch flip.
 *
 * Run once with USE_REGISTRY_DISPATCH=false, then temporarily flip it true and run the
 * same test again. Restore the switch to false before shipping.
 */
@UnstableApi
@RunWith(AndroidJUnit4::class)
class TransitionExportAbValidationTest {

    private data class Case(val label: String, val type: String, val durationMs: Long = 500L)

    private val cases = listOf(
        Case("Dissolve", "DISSOLVE"),
        Case("Fade Black", "FADE_BLACK"),
        Case("Fade White", "FADE_WHITE"),
        Case("Slide Left", "SLIDE_LEFT"),
        Case("Slide Right", "SLIDE_RIGHT"),
        Case("Zoom In", "ZOOM_IN"),
        Case("Zoom Out", "ZOOM_OUT"),
        Case("Whip Pan Left", "WHIP_PAN_LEFT")
    )

    @Test
    fun export_matrix_completes_and_logs_metrics() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<ClipForgeApp>()
        val mediaPath = seedPath(app)
        val sourceDurationMs = readDurationMs(mediaPath)
        assertTrue("seed media too short: ${sourceDurationMs}ms", sourceDurationMs >= 1500L)
        val clipDurationMs = minOf(sourceDurationMs, 2500L)

        cases.forEach { case ->
            val projectId = "ab_validation_${case.type.lowercase()}"
            seedProject(app, projectId, mediaPath, clipDurationMs, case.type, case.durationMs)
            val result = runExport(app, projectId)
            assertEquals("${case.label} export status", RenderJobStatus.COMPLETED, result.status)
            assertTrue("${case.label} gallery visible", result.isGalleryVisible)
            assertNotNull("${case.label} public uri", result.publicUri)
            assertNotNull("${case.label} output path", result.outputUrl)
            val output = File(result.outputUrl!!)
            assertTrue("${case.label} output exists", output.exists())
            assertTrue("${case.label} output nonzero", output.length() > 0L)
            val exportedDurationMs = readDurationMs(output.absolutePath)
            assertTrue("${case.label} exported duration sane: $exportedDurationMs", exportedDurationMs > 0L)
            Log.d(
                TAG,
                "AB_RESULT transition=${case.type} label=${case.label} " +
                    "durationMs=$exportedDurationMs bytes=${output.length()} " +
                    "gallery=${result.isGalleryVisible} uri=${result.publicUri} " +
                    "output=${output.absolutePath} statusMessage=${result.statusMessage}"
            )
        }
    }

    private fun seedPath(context: Context): String {
        val mediaDir = File(context.filesDir, "media")
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

    private suspend fun seedProject(
        app: ClipForgeApp,
        projectId: String,
        mediaPath: String,
        clipDurationMs: Long,
        transitionType: String,
        transitionDurationMs: Long
    ) {
        val now = System.currentTimeMillis()
        val db = app.database
        db.projectDao().upsertProject(
            ProjectEntity(
                id = projectId,
                title = "AB $transitionType",
                aspectRatio = "RATIO_9_16",
                exportQuality = "QUALITY_720P",
                planType = "FREE",
                thumbnailUri = null,
                createdAt = now,
                updatedAt = now
            )
        )
        val assetA = "${projectId}_asset_a"
        val assetB = "${projectId}_asset_b"
        db.mediaAssetDao().upsertAll(
            listOf(
                MediaAssetEntity(assetA, projectId, "VIDEO", mediaPath, null, clipDurationMs, File(mediaPath).length(), "video/mp4", now),
                MediaAssetEntity(assetB, projectId, "VIDEO", mediaPath, null, clipDurationMs, File(mediaPath).length(), "video/mp4", now + 1L)
            )
        )
        db.timelineDao().deleteAllForProject(projectId)
        db.timelineDao().upsertAll(
            listOf(
                TimelineItemEntity(
                    id = "${projectId}_clip_a",
                    projectId = projectId,
                    mediaAssetId = assetA,
                    trackIndex = 0,
                    orderIndex = 0,
                    startMs = 0L,
                    endMs = clipDurationMs,
                    trimStartMs = 0L,
                    trimEndMs = clipDurationMs,
                    fitMode = "FIT",
                    transitionType = transitionType,
                    transitionDurationMs = transitionDurationMs,
                    volume = 1f,
                    opacity = 1f
                ),
                TimelineItemEntity(
                    id = "${projectId}_clip_b",
                    projectId = projectId,
                    mediaAssetId = assetB,
                    trackIndex = 0,
                    orderIndex = 1,
                    startMs = clipDurationMs,
                    endMs = clipDurationMs * 2L,
                    trimStartMs = 0L,
                    trimEndMs = clipDurationMs,
                    fitMode = "FIT",
                    transitionType = null,
                    transitionDurationMs = null,
                    volume = 1f,
                    opacity = 1f
                )
            )
        )
    }

    private fun runExport(app: ClipForgeApp, projectId: String): com.clipforge.ai.core.export.ExportManagerState {
        app.exportManager.startExport(projectId)
        val deadline = System.currentTimeMillis() + EXPORT_TIMEOUT_MS
        while (System.currentTimeMillis() < deadline) {
            val state = app.exportManager.state.value
            if (state.projectId == projectId && state.status in TERMINAL_STATUSES) {
                if (state.status == RenderJobStatus.FAILED) {
                    Log.e(TAG, "AB_FAILED transitionProject=$projectId error=${state.errorMessage}")
                }
                return state
            }
            Thread.sleep(POLL_MS)
        }
        error("Timed out waiting for export project=$projectId state=${app.exportManager.state.value}")
    }

    private fun readDurationMs(path: String): Long {
        val mmr = MediaMetadataRetriever()
        return try {
            mmr.setDataSource(path)
            mmr.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        } finally {
            runCatching { mmr.release() }
        }
    }

    private companion object {
        const val TAG = "AB_EXPORT_VALIDATION"
        const val EXPORT_TIMEOUT_MS = 240_000L
        const val POLL_MS = 500L
        val TERMINAL_STATUSES = setOf(
            RenderJobStatus.COMPLETED,
            RenderJobStatus.FAILED,
            RenderJobStatus.CANCELLED
        )
    }
}
