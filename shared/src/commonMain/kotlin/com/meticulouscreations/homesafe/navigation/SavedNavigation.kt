package com.meticulouscreations.homesafe.navigation

import androidx.compose.runtime.staticCompositionLocalOf

/**
 * Names the session the saved navigation belongs to. The Activity is recreated for a change it
 * doesn't handle itself (a font size, a language), and the position the user was at is saved and
 * restored across it; the process dying is the other way a position comes back, and then the
 * session (the sign-in, the graph) does not. A position saved under one epoch is restored only
 * under the same one, so a new process shows the sign-in screen as it always has.
 *
 * Android gives each graph its own; elsewhere the saved state never outlives the process, so
 * the default, which always matches, is right.
 */
val LocalNavigationEpoch = staticCompositionLocalOf { "" }

/**
 * What the saved navigation looks like to the platform's saved-state store: a list of strings and
 * lists of strings only (which every one of them can hold, with no Parcelable route classes),
 * headed by a [VERSION] and the epoch. Anything else is unknown data, and [unpack] gives none of
 * it back, so a malformed, older or foreign save falls back to the default stack.
 */
object SavedNavigation {
    /** Bumped when a saved shape changes in a way an older reader would misread. */
    const val VERSION = "1"

    fun pack(epoch: String, parts: List<Any>): List<Any> = ArrayList<Any>(parts.size + 2).apply {
        add(VERSION)
        add(epoch)
        addAll(parts)
    }

    /** The parts [pack] was given, or null for anything that isn't a current save under [epoch]. */
    fun unpack(saved: Any?, epoch: String): List<Any>? {
        val list = saved as? List<*> ?: return null
        if (list.size < 2 || list[0] != VERSION || list[1] != epoch) return null
        return list.drop(2).map { it ?: return null }
    }
}
