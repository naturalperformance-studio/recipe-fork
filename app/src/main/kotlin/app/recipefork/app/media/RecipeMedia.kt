package app.recipefork.app.media

import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.Recipe

/** The recipe's cover photograph, if it holds one; a recipe holds one at most (MEDIA-001). */
val Recipe.cover: Media? get() = media.firstOrNull { it.attachment == MediaAttachment.Cover }

/** The photograph of the step at [position], if the one item it carries is a photograph (MEDIA-001). */
fun Recipe.stepPhoto(position: Int): Media? = stepMedia(position)?.takeIf { it.kind == MediaKind.PHOTO }

/** The media item of the step at [position] — a photograph or a clip — if it carries one; it carries one at most (MEDIA-001). */
fun Recipe.stepMedia(position: Int): Media? = media.firstOrNull { it.attachment == MediaAttachment.ToStep(position) }
