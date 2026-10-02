package app.recipefork.app.media

import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.Recipe

/** The recipe's cover photograph, if it holds one; a recipe holds one at most (MEDIA-001). */
val Recipe.cover: Media? get() = media.firstOrNull { it.attachment == MediaAttachment.Cover }
