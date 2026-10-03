# Added to the release rules for the releaseSmoke build only, never for the build the owner installs: what the test
# APK takes from the shrunk application without the application itself using it in that shape. The test APK leaves
# every library the application also holds to the application, and the smoke test calls the application's own
# classes; R8 would otherwise remove or inline those members out from under it.
#
# Nothing here keeps what R8 could break for the application by itself — the Room implementations, the serializers,
# the components of the manifest, the capture provider, Compose — and the application's classes are still renamed
# (allowobfuscation), so a lookup by name that the release rules miss fails here as it would on the phone.

# The test runner traces through androidx.tracing and waits through androidx.concurrent.futures.
-keep class androidx.tracing.** { *; }
-keep class androidx.concurrent.futures.** { *; }

# The test runner, JUnit and the smoke test call the Kotlin standard library and kotlinx.coroutines; neither is reached
# reflectively by the application.
-keep class kotlin.** { *; }
-keep class kotlinx.coroutines.** { *; }

# The application's surface the smoke test drives: the container, the book, photographs, export and import, timers,
# and the recipe it writes.
-keep,allowobfuscation class app.recipefork.app.RecipeForkApplication { *; }
-keep,allowobfuscation class app.recipefork.app.AppContainer { *; }
-keep,allowobfuscation class app.recipefork.app.book.BookName, app.recipefork.app.book.BookName$* { *; }
-keep,allowobfuscation class app.recipefork.app.book.ActiveBook { *; }
-keep,allowobfuscation class app.recipefork.app.book.BookDatabase$Companion { *; }
-keepclassmembers,allowobfuscation class app.recipefork.app.book.BookDatabase { public static ** Companion; }
-keep,allowobfuscation class app.recipefork.app.recipe.RecipeRepository { *; }
-keep,allowobfuscation class app.recipefork.app.media.PhotoStore { *; }
-keep,allowobfuscation class app.recipefork.app.media.PhotoOutcome, app.recipefork.app.media.PhotoOutcome$* { *; }
-keep,allowobfuscation class app.recipefork.app.file.RecipeExport { *; }
-keep,allowobfuscation class app.recipefork.app.file.RecipeImport, app.recipefork.app.file.RecipeImportKt { *; }
-keep,allowobfuscation class app.recipefork.app.file.ImportOutcome, app.recipefork.app.file.ImportOutcome$* { *; }
-keep,allowobfuscation class app.recipefork.app.timer.RunningTimers, app.recipefork.app.timer.RunningTimer { *; }
-keep,allowobfuscation class app.recipefork.app.timer.TimerKey, app.recipefork.app.timer.TimerRinging { *; }
-keep,allowobfuscation class app.recipefork.core.recipe.Recipe, app.recipefork.core.recipe.Recipe$Companion { *; }
-keep,allowobfuscation class app.recipefork.core.recipe.RecipeId, app.recipefork.core.recipe.RecipeId$Companion { *; }
-keep,allowobfuscation class app.recipefork.core.recipe.Ingredient, app.recipefork.core.recipe.IngredientReference { *; }
-keep,allowobfuscation class app.recipefork.core.recipe.Step, app.recipefork.core.recipe.Timer { *; }
-keep,allowobfuscation class app.recipefork.core.recipe.Media, app.recipefork.core.recipe.MediaAttachment, app.recipefork.core.recipe.MediaAttachment$* { *; }
-keep,allowobfuscation class app.recipefork.core.scaling.Rational, app.recipefork.core.scaling.Rational$Companion { *; }
# The smoke test closes the databases it opens itself; the application never closes one.
-keepclassmembers,allowobfuscation class androidx.room.RoomDatabase { public void close(); }
