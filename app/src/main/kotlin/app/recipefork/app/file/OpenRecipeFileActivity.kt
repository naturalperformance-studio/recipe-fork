package app.recipefork.app.file

import android.app.Activity
import android.os.Bundle

/**
 * Where a recipe file opened or shared from another application arrives (FLOW-064): it is handed on at once to
 * [MainActivity][app.recipefork.app.MainActivity] in the application's own task, never shown inside the sender's — a
 * messenger starts the view for a result, which would keep it in the messenger's task. It shows nothing itself.
 */
class OpenRecipeFileActivity : Activity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        RecipeFileIntents.forward(this, intent)?.let(::startActivity)
        finish()
    }
}
