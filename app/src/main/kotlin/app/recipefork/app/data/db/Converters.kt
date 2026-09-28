package app.recipefork.app.data.db

import androidx.room.TypeConverter
import app.recipefork.core.scaling.Rational
import java.math.BigInteger
import java.time.Instant

/** Column forms shared by the device's databases. */
class Converters {
    @TypeConverter
    fun instantToMillis(value: Instant?): Long? = value?.toEpochMilli()

    @TypeConverter
    fun millisToInstant(value: Long?): Instant? = value?.let(Instant::ofEpochMilli)

    /** A quantity is held exactly, as `n` or `n/d`: nothing is lost to a floating point. */
    @TypeConverter
    fun rationalToText(value: Rational?): String? = value?.toString()

    @TypeConverter
    fun textToRational(value: String?): Rational? = value?.let {
        val parts = it.split('/')
        Rational.of(BigInteger(parts[0]), if (parts.size == 2) BigInteger(parts[1]) else BigInteger.ONE)
    }
}
