package app.recipefork.app

import android.os.Build
import org.junit.runner.Description
import org.junit.runner.manipulation.Filter

/**
 * A device test that runs on the emulator alone and is left out on a phone — one that would lock the owner's phone,
 * say. Left out by [EmulatorOnlyFilter] before it runs, not skipped by an assumption, which the toolchain reports as a
 * failure (T-243).
 */
@Retention(AnnotationRetention.RUNTIME)
@Target(AnnotationTarget.CLASS, AnnotationTarget.FUNCTION)
annotation class EmulatorOnly(val because: String)

/**
 * Leaves out the tests marked [EmulatorOnly] on a device that is no emulator. Named to the test runner as its
 * `filter` argument (app/build.gradle.kts), which makes it by its constructor without arguments.
 */
class EmulatorOnlyFilter : Filter() {
    override fun shouldRun(description: Description): Boolean {
        if (isEmulator) return true
        val marked = description.getAnnotation(EmulatorOnly::class.java) != null ||
            description.testClass?.getAnnotation(EmulatorOnly::class.java) != null
        if (description.isTest) return !marked
        // A suite runs where any of its tests does.
        return description.children.isEmpty() || description.children.any { shouldRun(it) }
    }

    override fun describe() = "tests the emulator alone runs"

    companion object {
        /** Whether this device is the Android emulator: its virtual hardware names itself so. */
        val isEmulator: Boolean
            get() = Build.HARDWARE == "ranchu" || Build.HARDWARE == "goldfish" || Build.FINGERPRINT.startsWith("generic") ||
                Build.PRODUCT.startsWith("sdk")
    }
}
