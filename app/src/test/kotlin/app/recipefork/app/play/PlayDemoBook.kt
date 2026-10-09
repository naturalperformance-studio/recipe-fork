package app.recipefork.app.play

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import app.recipefork.app.book.BookName
import app.recipefork.app.media.PhotoStore
import app.recipefork.core.recipe.Ingredient
import app.recipefork.core.recipe.IngredientGroup
import app.recipefork.core.recipe.IngredientReference
import app.recipefork.core.recipe.Media
import app.recipefork.core.recipe.MediaAttachment
import app.recipefork.core.recipe.MediaKind
import app.recipefork.core.recipe.MediaVisibility
import app.recipefork.core.recipe.QuantityUnit
import app.recipefork.core.recipe.Recipe
import app.recipefork.core.recipe.RecipeLanguage
import app.recipefork.core.recipe.Step
import app.recipefork.core.recipe.Timer
import app.recipefork.core.recipe.UnitType
import app.recipefork.core.scaling.Rational
import java.io.File
import java.time.Instant
import kotlin.time.Duration
import kotlin.time.Duration.Companion.minutes
import kotlin.time.Duration.Companion.seconds

/**
 * The demo book the store screenshots show: the same six home recipes in English and in Russian, written for them, their
 * ingredients the shipped dictionary's entries, with covers and a step photo drawn here — plain shapes in food colours,
 * nobody's photograph. The book lists the traybake, with its cover, first and the two without a cover after it, so that
 * its first screen shows three titles beneath a cover. The traybake is the recipe the card, its steps and the editor are
 * shown on: two ingredient groups — the first one short, so that the editor shows both on one screen — notes,
 * references in its steps and timers.
 */
class PlayDemoBook(val language: RecipeLanguage) {
    private val en = language == RecipeLanguage.EN
    private fun t(english: String, russian: String) = if (en) english else russian

    /** The recipes in the order they are written: the last one written stands first in the book. */
    val recipes: List<DemoRecipe> by lazy { listOf(pancakes(), soup(), salad(), pie(), pasta(), traybake()) }

    /** The recipe the card and the editor are shown on, the book's first. */
    val traybake: Recipe get() = recipes.last().recipe

    /** The traybake's step whose timer runs in the screenshots, and the ingredients marked done meanwhile: the marinade. */
    val runningStep = 2
    val doneIngredients = listOf(0, 1)

    /** Writes every recipe's pictures into the [filesDirectory]'s book and the recipes as they name them. */
    fun pictures(filesDirectory: File) {
        val store = PhotoStore(filesDirectory)
        store.directory(BookName.Pool).mkdirs()
        for (demo in recipes) for ((mediaId, picture) in demo.pictures) {
            val bitmap = Bitmap.createBitmap(PICTURE_WIDTH, PICTURE_HEIGHT, Bitmap.Config.ARGB_8888)
            picture(Canvas(bitmap))
            store.file(BookName.Pool, mediaId).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it) }
            bitmap.recycle()
        }
        // The first decoding sets up the native graphics' JNI classes; done here, on the test's thread, since on a
        // background thread — where the screens decode their pictures — it aborts the process (java/nio/ShortBuffer).
        recipes.first { it.pictures.isNotEmpty() }.pictures.first().let { (mediaId, _) ->
            BitmapFactory.decodeFile(store.file(BookName.Pool, mediaId).path)?.recycle()
        }
    }

    private fun traybake() = demo(
        t("Chicken and potato traybake", "Курица с картофелем в духовке"),
        tags = setOf(MAIN_COURSE, DINNER),
        servings = 4,
        groups = listOf(IngredientGroup(t("Marinade", "Маринад"), 0), IngredientGroup(t("Tray", "На противень"), 2)),
        ingredients = listOf(
            household(OLIVE_OIL, 3, QuantityUnit.TABLESPOON),
            household(SWEET_PAPRIKA, 2, QuantityUnit.TEASPOON, note = t("smoked or sweet", "копчёная или сладкая")),
            grams(CHICKEN_THIGHS, 800, note = t("bone in, skin on", "на кости, с кожей")),
            grams(POTATO, 1000, note = t("in wedges", "дольками")),
            piece(RED_ONION, 1, divisible = true, note = t("in thick rings", "толстыми кольцами")),
            piece(LEMON, 1, divisible = true, note = t("sliced", "кружками")),
            piece(GARLIC, 4, divisible = false, note = t("whole cloves, unpeeled", "целыми зубчиками, в шелухе")),
            toTaste(SALT),
        ),
        steps = listOf(
            step(t("Stir {0} of olive oil and {1} of paprika together in a large bowl.", "Смешайте в большой миске {0} масла и {1} паприки.")),
            step(t("Coat {2} of chicken and {3} of potatoes in the marinade and spread them over the tray in one layer.", "Перемешайте {2} курицы и {3} картофеля с маринадом и разложите на противне в один слой.")),
            step(
                t("Tuck the onion, the lemon and the garlic in between, season with salt and roast at 200 °C until the skin is golden and crisp.", "Разложите между ними лук, лимон и чеснок, посолите и запекайте при 200 °C, пока кожа не станет золотистой и хрустящей."),
                Timer(40.minutes, t("Oven", "Духовка")),
            ),
            step(t("Let it rest, then squeeze the roasted lemon over the top.", "Дайте постоять и выжмите сверху запечённый лимон."), Timer(5.minutes)),
        ),
        cover = ::traybakePicture,
        stepPhotos = mapOf(1 to ::trayPicture),
    )

    private fun pancakes() = demo(
        t("Kefir pancakes", "Оладьи на кефире"),
        tags = setOf(BREAKFAST, PANCAKES),
        servings = 3,
        ingredients = listOf(
            grams(KEFIR, 500),
            piece(EGG, 2, divisible = false),
            grams(FLOUR, 250),
            household(SUGAR, 2, QuantityUnit.TABLESPOON),
            household(BAKING_SODA, 1, QuantityUnit.TEASPOON),
        ),
        steps = listOf(
            step(t("Whisk {0} of kefir with the eggs and the sugar.", "Взбейте {0} кефира с яйцами и сахаром.")),
            step(t("Fold in the flour and the soda; let the batter stand.", "Вмешайте муку и соду, дайте тесту постоять."), Timer(10.minutes)),
            step(t("Fry spoonfuls in a little oil until golden on both sides.", "Выкладывайте тесто ложкой и жарьте на небольшом количестве масла до румяности с двух сторон.")),
        ),
        cover = ::pancakesPicture,
    )

    private fun soup() = demo(
        t("Roasted tomato soup", "Суп из печёных томатов"),
        tags = setOf(SOUP, VEGETARIAN),
        servings = 4,
        ingredients = listOf(
            grams(TOMATO, 1000),
            piece(ONION, 1, divisible = true),
            millilitres(VEGETABLE_STOCK, 500),
            household(CREAM, 4, QuantityUnit.TABLESPOON),
        ),
        steps = listOf(
            step(t("Roast the tomatoes and the onion until soft.", "Запеките помидоры и лук до мягкости."), Timer(30.minutes)),
            step(t("Blend with {2} of hot stock and stir in the cream.", "Пробейте блендером с {2} горячего бульона и вмешайте сливки.")),
        ),
        cover = ::soupPicture,
    )

    private fun salad() = demo(
        t("Greek salad", "Греческий салат"),
        tags = setOf(SALAD, QUICK, VEGETARIAN),
        servings = 2,
        ingredients = listOf(
            piece(TOMATO, 3, divisible = true),
            piece(CUCUMBER, 1, divisible = true),
            grams(FETA, 150),
        ),
        steps = listOf(step(t("Chop, toss and top with the feta.", "Нарежьте, перемешайте и выложите сверху фету."))),
        cover = ::saladPicture,
    )

    private fun pie() = demo(
        t("Apple sharlotka", "Шарлотка с яблоками"),
        tags = setOf(DESSERT, BAKING),
        servings = 8,
        ingredients = listOf(
            piece(APPLE, 4, divisible = true),
            piece(EGG, 3, divisible = false),
            grams(SUGAR, 150),
            grams(FLOUR, 150),
        ),
        steps = listOf(
            step(t("Beat the eggs with the sugar, fold in the flour and pour over the apples.", "Взбейте яйца с сахаром, вмешайте муку и залейте яблоки.")),
            step(t("Bake at 180 °C.", "Выпекайте при 180 °C."), Timer(40.minutes)),
        ),
        cover = null,
    )

    private fun pasta() = demo(
        t("Weeknight tomato pasta", "Паста с томатным соусом"),
        tags = setOf(PASTA, QUICK),
        servings = 2,
        ingredients = listOf(
            grams(SPAGHETTI, 200),
            grams(CANNED_TOMATOES, 400),
            piece(GARLIC, 2, divisible = false, note = t("cloves", "зубчики")),
        ),
        steps = listOf(
            step(t("Cook {0} of spaghetti in salted water.", "Отварите {0} спагетти в подсоленной воде."), Timer(9.minutes)),
            step(t("Simmer the tomatoes with the garlic and toss with the pasta.", "Потушите томаты с чесноком и смешайте с пастой.")),
        ),
        cover = null,
    )

    private var written = 0

    private fun demo(
        title: String,
        tags: Set<Long>,
        servings: Int,
        ingredients: List<Ingredient>,
        steps: List<Step>,
        cover: ((Canvas) -> Unit)?,
        groups: List<IngredientGroup> = emptyList(),
        stepPhotos: Map<Int, (Canvas) -> Unit> = emptyMap(),
    ): DemoRecipe {
        val at = START.plusSeconds(3600L * written++)
        val pictures = mutableListOf<Pair<String, (Canvas) -> Unit>>()
        val media = mutableListOf<Media>()
        fun picture(attachment: MediaAttachment, draw: (Canvas) -> Unit) {
            val id = "demo-${language.name.lowercase()}-$written-${media.size}"
            pictures += id to draw
            media += Media(id, null, MediaKind.PHOTO, attachment, MediaVisibility.PRIVATE)
        }
        cover?.let { picture(MediaAttachment.Cover, it) }
        stepPhotos.forEach { (position, draw) -> picture(MediaAttachment.ToStep(position), draw) }
        val recipe = Recipe.create(at, owner = null).copy(
            title = title,
            tags = tags,
            language = language,
            servings = servings,
            ingredients = ingredients.mapIndexed { position, it -> it.copy(position = position) },
            steps = steps.mapIndexed { position, it -> it.copy(position = position) },
            media = media,
            ingredientGroups = groups,
        )
        return DemoRecipe(recipe, pictures)
    }

    /** A recipe and the pictures it names, by their media identities. */
    class DemoRecipe(val recipe: Recipe, val pictures: List<Pair<String, (Canvas) -> Unit>>)

    companion object {
        private val START: Instant = Instant.parse("2026-10-01T09:00:00Z")
        const val PICTURE_WIDTH = 1200
        const val PICTURE_HEIGHT = 900

        // Tags and ingredient entries of the dictionaries the application ships (assets/dictionaries.json).
        private const val BREAKFAST = 300L
        private const val DINNER = 303L
        private const val SOUP = 309L
        private const val SALAD = 312L
        private const val MAIN_COURSE = 313L
        private const val DESSERT = 315L
        private const val BAKING = 316L
        private const val PANCAKES = 322L
        private const val PASTA = 324L
        private const val VEGETARIAN = 400L
        private const val QUICK = 700L
        private const val POTATO = 1000L
        private const val ONION = 1100L
        private const val RED_ONION = 1102L
        private const val GARLIC = 1120L
        private const val TOMATO = 1300L
        private const val CUCUMBER = 1320L
        private const val LEMON = 2000L
        private const val APPLE = 2100L
        private const val CHICKEN_THIGHS = 3503L
        private const val EGG = 4400L
        private const val CREAM = 4480L
        private const val KEFIR = 4520L
        private const val FETA = 4726L
        private const val FLOUR = 5000L
        private const val SPAGHETTI = 5401L
        private const val SUGAR = 6100L
        private const val BAKING_SODA = 6301L
        private const val OLIVE_OIL = 6704L
        private const val SALT = 7000L
        private const val SWEET_PAPRIKA = 7151L
        private const val CANNED_TOMATOES = 8503L
        private const val VEGETABLE_STOCK = 8603L

        private fun grams(entry: Long, grams: Long, note: String? = null) = exact(entry, grams, QuantityUnit.GRAM, note)

        private fun millilitres(entry: Long, millilitres: Long) = exact(entry, millilitres, QuantityUnit.MILLILITRE, null)

        private fun exact(entry: Long, amount: Long, unit: QuantityUnit, note: String?) =
            Ingredient(0, entry, unitType = UnitType.EXACT, unit = unit, quantity = Rational.of(amount), note = note)

        private fun household(entry: Long, count: Long, unit: QuantityUnit, note: String? = null) =
            Ingredient(0, entry, unitType = UnitType.HOUSEHOLD, unit = unit, quantity = Rational.of(count), note = note)

        private fun piece(entry: Long, count: Long, divisible: Boolean, note: String? = null) =
            Ingredient(0, entry, unitType = UnitType.PIECE, unit = QuantityUnit.PIECE, quantity = Rational.of(count), divisible = divisible, note = note)

        private fun toTaste(entry: Long) = Ingredient(0, entry, unitType = UnitType.TASTE_UNMEASURED, unit = null, quantity = null)

        /** A step whose `{n}` marks stand for references to the ingredient at position n, read as its quantity. */
        private fun step(text: String, timer: Timer? = null): Step {
            val references = mutableListOf<IngredientReference>()
            val plain = StringBuilder()
            var at = 0
            for (match in Regex("\\{(\\d+)\\}").findAll(text)) {
                plain.append(text, at, match.range.first)
                references += IngredientReference(match.groupValues[1].toInt(), plain.length)
                at = match.range.last + 1
            }
            plain.append(text.substring(at))
            return Step(0, plain.toString(), references, timer = timer)
        }

        // The pictures: a table seen from above and what is served on it, in plain shapes.

        private fun paint(colour: Int, style: Paint.Style = Paint.Style.FILL, stroke: Float = 0f) =
            Paint(Paint.ANTI_ALIAS_FLAG).apply { color = colour; this.style = style; strokeWidth = stroke; strokeCap = Paint.Cap.ROUND }

        /** A wooden table with a plate on it, the plate's middle at the picture's. */
        private fun table(canvas: Canvas, wood: Int = 0xFFB98A5E.toInt(), plate: Float = 380f) {
            canvas.drawColor(wood)
            val grain = paint(0x22000000, Paint.Style.STROKE, 6f)
            for (y in 40 until PICTURE_HEIGHT step 110) canvas.drawLine(0f, y.toFloat(), PICTURE_WIDTH.toFloat(), y + 24f, grain)
            canvas.drawCircle(CX + 14f, CY + 18f, plate, paint(0x33000000))
            canvas.drawCircle(CX, CY, plate, paint(0xFFF7F3EC.toInt()))
            canvas.drawCircle(CX, CY, plate * 0.78f, paint(0xFFEDE6DA.toInt()))
        }

        private const val CX = PICTURE_WIDTH / 2f
        private const val CY = PICTURE_HEIGHT / 2f

        private fun traybakePicture(canvas: Canvas) {
            canvas.drawColor(0xFF8C6A4F.toInt())
            val tray = RectF(110f, 90f, 1090f, 810f)
            canvas.drawRoundRect(RectF(tray.left + 16, tray.top + 20, tray.right + 16, tray.bottom + 20), 60f, 60f, paint(0x44000000))
            canvas.drawRoundRect(tray, 60f, 60f, paint(0xFF5B5F66.toInt()))
            canvas.drawRoundRect(RectF(150f, 130f, 1050f, 770f), 40f, 40f, paint(0xFF3E4147.toInt()))
            val potato = paint(0xFFE3B04B.toInt())
            val edge = paint(0xFFB9772A.toInt())
            for ((x, y) in listOf(260f to 220f, 470f to 640f, 820f to 240f, 960f to 600f, 300f to 560f, 640f to 420f, 900f to 420f)) {
                canvas.drawOval(RectF(x - 70, y - 42, x + 70, y + 42), potato)
                canvas.drawOval(RectF(x - 70, y + 10, x + 70, y + 42), edge)
            }
            val skin = paint(0xFFC9772E.toInt())
            val crisp = paint(0xFF9E4F1B.toInt())
            for ((x, y) in listOf(450f to 260f, 700f to 640f, 260f to 400f, 760f to 300f)) {
                canvas.drawOval(RectF(x - 110, y - 80, x + 110, y + 80), skin)
                canvas.drawOval(RectF(x - 60, y - 40, x + 40, y + 20), crisp)
            }
            val lemon = paint(0xFFF4D03F.toInt())
            val pith = paint(0xFFFFF4C2.toInt())
            for ((x, y) in listOf(580f to 180f, 1000f to 330f, 180f to 700f)) {
                canvas.drawCircle(x, y, 46f, lemon)
                canvas.drawCircle(x, y, 34f, pith)
            }
            val onion = paint(0xFF8E3B6B.toInt(), Paint.Style.STROKE, 12f)
            for ((x, y) in listOf(560f to 500f, 380f to 720f, 1000f to 720f)) canvas.drawCircle(x, y, 38f, onion)
            val herb = paint(0xFF4E7D32.toInt())
            for (i in 0 until 40) canvas.drawCircle(180f + (i * 197) % 860, 160f + (i * 131) % 590, 7f, herb)
        }

        private fun trayPicture(canvas: Canvas) {
            canvas.drawColor(0xFFDCD3C8.toInt())
            val tray = RectF(70f, 70f, 1130f, 830f)
            canvas.drawRoundRect(tray, 50f, 50f, paint(0xFF5B5F66.toInt()))
            canvas.drawRoundRect(RectF(110f, 110f, 1090f, 790f), 30f, 30f, paint(0xFF3E4147.toInt()))
            val potato = paint(0xFFF1D9A0.toInt())
            for (row in 0 until 4) for (column in 0 until 6) {
                val x = 210f + column * 160 + (row % 2) * 50
                val y = 200f + row * 165
                canvas.drawOval(RectF(x - 64, y - 38, x + 64, y + 38), potato)
            }
            val chicken = paint(0xFFF0B9A0.toInt())
            for ((x, y) in listOf(330f to 300f, 640f to 460f, 900f to 300f, 470f to 640f, 850f to 640f)) {
                canvas.drawOval(RectF(x - 100, y - 70, x + 100, y + 70), chicken)
            }
            val paprika = paint(0xFFB33A1E.toInt())
            for (i in 0 until 90) canvas.drawCircle(150f + (i * 211) % 900, 150f + (i * 157) % 620, 5f, paprika)
        }

        private fun pancakesPicture(canvas: Canvas) {
            table(canvas, wood = 0xFFC79B6D.toInt())
            val cake = paint(0xFFE9B35F.toInt())
            val side = paint(0xFFC7863A.toInt())
            for (i in 4 downTo 0) {
                val y = CY + 60 - i * 34f
                canvas.drawOval(RectF(CX - 230, y - 120, CX + 230, y + 120), side)
                canvas.drawOval(RectF(CX - 230, y - 130, CX + 230, y + 104), cake)
            }
            canvas.drawOval(RectF(CX - 120, CY - 150, CX + 120, CY - 40), paint(0xFFF8E7A1.toInt()))
            val berry = paint(0xFF3B3F8F.toInt())
            val red = paint(0xFFC8243A.toInt())
            for ((x, y) in listOf(-60f to -120f, 40f to -90f, -10f to -170f, 90f to -150f)) canvas.drawCircle(CX + x, CY + y, 26f, berry)
            for ((x, y) in listOf(-300f to 220f, 290f to 210f, 250f to -250f)) canvas.drawCircle(CX + x, CY + y, 34f, red)
        }

        private fun soupPicture(canvas: Canvas) {
            table(canvas, wood = 0xFF6F5946.toInt(), plate = 400f)
            canvas.drawCircle(CX, CY, 300f, paint(0xFFFFFFFF.toInt()))
            canvas.drawCircle(CX, CY, 262f, paint(0xFFD8461F.toInt()))
            val swirl = Path().apply {
                moveTo(CX - 120, CY)
                cubicTo(CX - 120, CY - 110, CX + 100, CY - 110, CX + 100, CY)
                cubicTo(CX + 100, CY + 70, CX - 40, CY + 70, CX - 40, CY + 10)
            }
            canvas.drawPath(swirl, paint(0xFFFBEFE2.toInt(), Paint.Style.STROKE, 22f))
            val basil = paint(0xFF3F7D2C.toInt())
            for ((x, y) in listOf(60f to -120f, 110f to -80f, -150f to 90f)) canvas.drawOval(RectF(CX + x - 34, CY + y - 18, CX + x + 34, CY + y + 18), basil)
            val bread = paint(0xFFD9A55B.toInt())
            canvas.drawRoundRect(RectF(CX + 360, CY + 200, CX + 560, CY + 330), 40f, 40f, bread)
        }

        private fun saladPicture(canvas: Canvas) {
            table(canvas, wood = 0xFFD2B48C.toInt())
            val cucumber = paint(0xFF9CC46B.toInt())
            val cucumberSkin = paint(0xFF3E6B2A.toInt())
            val tomato = paint(0xFFD8352A.toInt())
            val olive = paint(0xFF2E2A33.toInt())
            val feta = paint(0xFFFFFDF5.toInt())
            val onion = paint(0xFFB57BA6.toInt(), Paint.Style.STROKE, 10f)
            for (i in 0 until 9) {
                val x = CX - 200 + (i * 137) % 400
                val y = CY - 190 + (i * 89) % 380
                canvas.drawCircle(x, y, 52f, cucumberSkin)
                canvas.drawCircle(x, y, 44f, cucumber)
            }
            for (i in 0 until 8) {
                val x = CX - 210 + (i * 173) % 420
                val y = CY - 170 + (i * 113) % 360
                canvas.drawArc(RectF(x - 60, y - 60, x + 60, y + 60), (i * 47f) % 360, 200f, true, tomato)
            }
            for (i in 0 until 6) canvas.drawCircle(CX - 160 + (i * 71) % 320, CY - 150 + (i * 127) % 300, 22f, olive)
            for (i in 0 until 5) canvas.drawCircle(CX - 120 + i * 60f, CY - 60 + (i % 2) * 120f, 34f, onion)
            canvas.drawRoundRect(RectF(CX - 90, CY - 70, CX + 90, CY + 50), 12f, 12f, feta)
        }
    }
}

/** How long a running timer has been on in the screenshots: well into it, not just started. */
val RUN_FOR: Duration = 12.minutes + 37.seconds
