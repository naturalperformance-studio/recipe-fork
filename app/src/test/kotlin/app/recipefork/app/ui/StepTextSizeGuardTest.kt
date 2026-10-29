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
 * A11Y-003 over the theme file the owner edits (ADR 20261004): the card's step text on Cooking, the theme's
 * `headlineSmall`, stays larger than on View, where a step reads in body text, `bodyLarge` (T-314). An edit of
 * `app_theme.xml` that sets `theme_type_headlineSmall` no larger than `theme_type_bodyLarge` fails here rather than
 * quietly shrinking the steps on Cooking.
 */
@RunWith(AndroidJUnit4::class)
class StepTextSizeGuardTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private fun cookingOutgrowsView(resources: Resources): Boolean =
        appTypography(resources).let { it.headlineSmall.fontSize.value > it.bodyLarge.fontSize.value }

    @Test
    fun theThemeFileSetsTheCookingStepTextLargerThanTheViewOne() {
        assertTrue(
            "app_theme.xml: theme_type_headlineSmall (the steps on Cooking) must be larger than theme_type_bodyLarge (the steps on View)",
            cookingOutgrowsView(context.resources),
        )
    }

    @Test
    fun anEditSettingTheCookingStepTextNoLargerThanTheViewOneIsCaught() {
        val view = appTypography(context.resources).bodyLarge.fontSize.value.toInt()
        for (size in listOf(view, view - 2)) {
            val edited = editedThemeResources(context, sizes = mapOf(R.dimen.theme_type_headlineSmall to size))
            assertFalse("headlineSmall at $size sp, bodyLarge at $view sp", cookingOutgrowsView(edited))
        }
        assertTrue(cookingOutgrowsView(editedThemeResources(context, sizes = mapOf(R.dimen.theme_type_headlineSmall to view + 1))))
    }
}
