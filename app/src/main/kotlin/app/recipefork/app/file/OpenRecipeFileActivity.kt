package app.recipefork.app.file

import android.app.Activity
import android.os.Bundle

/**
 * Where a recipe file (FLOW-064), a collection file (v0.36) or a saved book (FLOW-073) opened or shared from another
 * application arrives: it is handed on at once to [MainActivity][app.recipefork.app.MainActivity] in the application's
 * own task, never shown inside the sender's — a messenger starts the view for a result, which would keep it in the
 * messenger's task. It shows nothing itself.
 */
class OpenRecipeFileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        try {
            RecipeFileIntents.handOn(this, intent, ::startActivity)
        } finally {
            finish()
        }
    }
}
