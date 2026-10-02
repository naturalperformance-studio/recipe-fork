package app.recipefork.app.ui

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import app.recipefork.app.Mode
import app.recipefork.app.library.LibraryScreen
import app.recipefork.app.library.LibrarySession
import app.recipefork.app.recipe.RecipeRepository

/** The application's destinations. */
object Destinations {
    const val LIBRARY_ROUTE = "library"
}

/** The navigation host; the recipe book is where the application opens. */
@Composable
fun AppNavHost(
    session: LibrarySession,
    recipes: RecipeRepository,
    mode: Mode,
    modifier: Modifier = Modifier,
    navController: NavHostController = rememberNavController(),
) {
    NavHost(navController, startDestination = Destinations.LIBRARY_ROUTE, modifier = modifier) {
        composable(Destinations.LIBRARY_ROUTE) { LibraryScreen(session, recipes, mode) }
    }
}
