package app.recipefork.app.media

import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe

/** The recipe's cover photograph, if it holds one; a recipe holds one at most (MEDIA-001). */
val Recipe.cover: Media? get() = media.firstOrNull { it.attachment == MediaAttachment.Cover }

/** The photograph of the step at [position], if it carries one; a step carries one at most (MEDIA-001). */
fun Recipe.stepPhoto(position: Int): Media? =
    media.firstOrNull { it.kind == MediaKind.PHOTO && it.attachment == MediaAttachment.ToStep(position) }
