package com.clipforge.ai

/**
 * Broad device-only marker: the instrumented test requires a real GPU and/or hardware video codec
 * stack and is NOT reliable on the GitHub software emulator.
 *
 * This covers any test that depends on real hardware GL/codec behaviour, including:
 *  - export tests (also marked [RequiresGpuExport], which stays export/performance-specific), and
 *  - preview-wiring tests that pass on physical hardware (confirmed on Samsung SM-A165F) but produce
 *    stable zero-effect / empty-effect output on the emulator's swiftshader GPU.
 *
 * CI excludes these from the emulator run via the AndroidJUnitRunner filter:
 *   -Pandroid.testInstrumentationRunnerArguments.notAnnotation=com.clipforge.ai.RequiresRealGpu
 *
 * They are NOT excluded on a real device, so they still gate hardware-backed behaviour.
 * To run ONLY these on a connected device:
 *   ./gradlew connectedDebugAndroidTest \
 *     -Pandroid.testInstrumentationRunnerArguments.annotation=com.clipforge.ai.RequiresRealGpu
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class RequiresRealGpu
