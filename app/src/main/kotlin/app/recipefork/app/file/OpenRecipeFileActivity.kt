package app.recipefork.app.file

import android.app.Activity
import android.os.Bundle
import app.recipefork.app.paste.PasteIntents

/**
 * Where a recipe file (FLOW-064), a collection file (v0.36) or a saved book (FLOW-073) opened or shared from another
 * application arrives: it is handed on at once to [MainActivity][app.recipefork.app.MainActivity] in the application's
 * own task, never shown inside the sender's — a messenger starts the view for a result, which would keep it in the
 * messenger's task. A recipe's text shared to the application (T-280) is handed on to it the same way, to open the paste
 * screen with; a share holding neither — a file of another kind shared as text — is handed on to be told it is no
 * recipe file. It shows nothing itself.
 */
class OpenRecipeFileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            when {
                RecipeFileIntents.forward(this, intent) != null -> RecipeFileIntents.handOn(this, intent, ::startActivity)
                else -> (PasteIntents.forward(this, intent) ?: RecipeFileIntents.notOurs(this, intent))?.let(::startActivity)
            }
        } finally {
            finish()
        }
    }
}
