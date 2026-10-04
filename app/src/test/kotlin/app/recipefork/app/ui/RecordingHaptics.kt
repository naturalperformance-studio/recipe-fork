package app.recipefork.app.ui

import java.util.Collections

/** Touch feedback that records each haptic asked for, in order, instead of giving it. */
class RecordingHaptics : Haptics {
    private val given = Collections.synchronizedList(mutableListOf<Haptic>())

    /** The haptics given so far, in order. */
    val all: List<Haptic> get() = synchronized(given) { given.toList() }

    override fun perform(haptic: Haptic) {
        given += haptic
    }

    fun clear() = given.clear()
}
