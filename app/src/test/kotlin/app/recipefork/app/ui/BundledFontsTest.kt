package app.recipefork.app.ui

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import app.recipefork.app.R
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import java.nio.ByteBuffer

/**
 * T-297: the four fonts the type roles use ship in `res/font/` as static instances (Literata and Golos Text, Regular
 * and Medium) that draw Russian and English and the signs recipes use, and the SIL OFL 1.1 text of each family ships in
 * `assets/licenses/`.
 */
@RunWith(AndroidJUnit4::class)
class BundledFontsTest {
    private val context = ApplicationProvider.getApplicationContext<Context>()

    private class FontFile(bytes: ByteArray) {
        private val data = ByteBuffer.wrap(bytes)
        val tables: Map<String, Int> = (0 until data.getShort(4).toInt()).associate { i ->
            val record = 12 + 16 * i
            String(bytes, record, 4, Charsets.US_ASCII) to data.getInt(record + 8)
        }

        val weight: Int get() = data.getShort(tables.getValue("OS/2") + 4).toInt()

        /** Every code point the font's Unicode character map (format 4 or 12) maps to a glyph other than .notdef. */
        val codePoints: Set<Int> by lazy {
            val cmap = tables.getValue("cmap")
            val subtables = (0 until (data.getShort(cmap + 2).toInt() and 0xffff)).map { i ->
                val record = cmap + 4 + 8 * i
                Triple(data.getShort(record).toInt(), data.getShort(record + 2).toInt(), cmap + data.getInt(record + 4))
            }
            val unicode = subtables.filter { (platform, encoding, _) -> platform == 0 || (platform == 3 && (encoding == 1 || encoding == 10)) }
            unicode.flatMap { (_, _, at) -> read(at) }.toSet()
        }

        private fun read(at: Int): List<Int> = when (data.getShort(at).toInt()) {
            4 -> {
                val segments = (data.getShort(at + 6).toInt() and 0xffff) / 2
                val ends = at + 14
                val starts = ends + 2 * segments + 2
                val deltas = starts + 2 * segments
                val offsets = deltas + 2 * segments
                (0 until segments).flatMap { s ->
                    val end = data.getShort(ends + 2 * s).toInt() and 0xffff
                    val start = data.getShort(starts + 2 * s).toInt() and 0xffff
                    val delta = data.getShort(deltas + 2 * s).toInt()
                    val offset = data.getShort(offsets + 2 * s).toInt() and 0xffff
                    (start..end).filter { c ->
                        if (c == 0xffff) return@filter false
                        val glyph = if (offset == 0) (c + delta) and 0xffff else {
                            val g = data.getShort(offsets + 2 * s + offset + 2 * (c - start)).toInt() and 0xffff
                            if (g == 0) 0 else (g + delta) and 0xffff
                        }
                        glyph != 0
                    }
                }
            }
            12 -> (0 until data.getInt(at + 12)).flatMap { g ->
                val group = at + 16 + 12 * g
                (data.getInt(group)..data.getInt(group + 4)).toList()
            }
            else -> emptyList()
        }
    }

    private fun font(id: Int) = FontFile(context.resources.openRawResource(id).use { it.readBytes() })

    private val fonts = mapOf(
        "literata_regular" to (R.font.literata_regular to 400),
        "literata_medium" to (R.font.literata_medium to 500),
        "golos_text_regular" to (R.font.golos_text_regular to 400),
        "golos_text_medium" to (R.font.golos_text_medium to 500),
    )

    @Test
    fun eachFontIsAStaticInstanceOfItsWeight() {
        fonts.forEach { (name, file) ->
            val (id, weight) = file
            val font = font(id)
            assertFalse("$name is static, not a variable font", "fvar" in font.tables)
            assertEquals("$name's weight", weight, font.weight)
        }
    }

    @Test
    fun eachFontDrawsRussianEnglishAndTheSignsRecipesUse() {
        val latin = ('A'..'Z') + ('a'..'z') + ('0'..'9')
        val russian = ('А'..'я') + 'Ё' + 'ё'
        val signs = "½¼¾⅓⅔↑↓≈·«»—–№×".toList()
        fonts.forEach { (name, file) ->
            val drawn = font(file.first).codePoints
            val missing = (latin + russian).filter { it.code !in drawn }
            assertTrue("$name lacks $missing", missing.isEmpty())
            // Golos Text itself draws no ⅓ or ⅔ (google/fonts' GolosText[wght].ttf): there the platform's font stands in.
            val notInTheFamily = if (name.startsWith("golos")) setOf('⅓', '⅔') else emptySet()
            val lacking = signs.filter { it.code !in drawn && it !in notInTheFamily }
            assertTrue("$name lacks the signs $lacking", lacking.isEmpty())
            // The subset keeps Latin and Cyrillic and the signs, not the whole of Unicode the families draw.
            assertTrue("$name keeps nothing beyond its subset", drawn.none { it in 0x0500..0x1fff || it in 0x0370..0x03ff })
        }
    }

    @Test
    fun theOflTextOfBothFamiliesShipsWithTheApp() {
        mapOf("Literata-OFL.txt" to "The Literata Project Authors", "GolosText-OFL.txt" to "The Golos Text Project Authors")
            .forEach { (file, authors) ->
                val text = context.assets.open("licenses/$file").use { it.readBytes().toString(Charsets.UTF_8) }
                assertTrue("$file names its authors", text.contains(authors))
                assertTrue("$file is the SIL Open Font License 1.1", text.contains("SIL OPEN FONT LICENSE Version 1.1"))
            }
    }
}
