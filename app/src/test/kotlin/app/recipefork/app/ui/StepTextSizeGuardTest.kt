package app.recipefork.app.ui

import android.content.Context
import android.content.res.Resources
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * A11Y-003 over the theme file the owner edits (ADR 20261004): the card's step text on View, the theme's `titleLarge`,
 * stays larger than body text, `bodyLarge`; and on Cooking, the theme's `headlineSmall`, larger than on View. An edit of
 * `app_theme.xml` that sets `theme_type_titleLarge` no larger than `theme_type_bodyLarge`, or `theme_type_headlineSmall`
 * no larger than `theme_type_titleLarge`, fails here rather than quietly shrinking the steps.
 */
@RunWith(AndroidJUnit4::class)
class StepTextSizeGuardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun stepTextOutgrowsBody(resources: Resources): Boolean =
        appTypography(resources).let { it.titleLarge.fontSize.value > it.bodyLarge.fontSize.value }

    private fun cookingOutgrowsView(resources: Resources): Boolean =
        appTypography(resources).let { it.headlineSmall.fontSize.value > it.titleLarge.fontSize.value }

    @Test
    fun theThemeFileSetsTheCookingStepTextLargerThanTheViewOne() {
        assertTrue(
            "app_theme.xml: theme_type_headlineSmall (the steps on Cooking) must be larger than theme_type_titleLarge (the steps on View)",
            cookingOutgrowsView(context.resources),
        )
    }

    @Test
    fun anEditSettingTheCookingStepTextNoLargerThanTheViewOneIsCaught() {
        val view = appTypography(context.resources).titleLarge.fontSize.value.toInt()
        for (size in listOf(view, view - 2)) {
            val edited = editedThemeResources(context, sizes = mapOf(R.dimen.theme_type_headlineSmall to size))
            assertFalse("headlineSmall at $size sp, titleLarge at $view sp", cookingOutgrowsView(edited))
        }
        assertTrue(cookingOutgrowsView(editedThemeResources(context, sizes = mapOf(R.dimen.theme_type_headlineSmall to view + 1))))
    }

    @Test
    fun theThemeFileSetsTheStepTextLargerThanBodyText() {
        assertTrue(
            "app_theme.xml: theme_type_titleLarge (the card's step text) must be larger than theme_type_bodyLarge (A11Y-003)",
            stepTextOutgrowsBody(context.resources),
        )
    }

    @Test
    fun anEditSettingTheStepTextNoLargerThanBodyTextIsCaught() {
        val body = appTypography(context.resources).bodyLarge.fontSize.value.toInt()
        for (size in listOf(body, body - 2)) {
            val edited = editedThemeResources(context, sizes = mapOf(R.dimen.theme_type_titleLarge to size))
            assertFalse("titleLarge at $size sp, bodyLarge at $body sp", stepTextOutgrowsBody(edited))
        }
        val larger = editedThemeResources(context, sizes = mapOf(R.dimen.theme_type_titleLarge to body + 1))
        assertTrue(stepTextOutgrowsBody(larger))
    }
}
