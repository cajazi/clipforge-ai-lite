package com.clipforge.ai

/**
 * Marks an instrumented test that requires a real GPU and/or hardware video codec to run — i.e. it
 * drives an actual export (GL effects + MediaCodec/Transformer encode) or real video playback/decode.
 *
 * These tests PASS on physical hardware (confirmed on Samsung SM-A165F) but FAIL on the GitHub
 * software emulator, which has no usable GPU/hardware codec stack (`Failed to find ColorBuffer`,
 * `cannot use nvidia cuvid decoder`, process crash).
 *
 * CI excludes them from the emulator run via the AndroidJUnitRunner filter:
 *   -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.clipforge.ai.RequiresGpuExport
 *
 * They are NOT excluded locally / on a real device, so they still gate hardware-backed export logic.
 * To run ONLY these on a connected device:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.annotation=com.clipforge.ai.RequiresGpuExport
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresGpuExport
