package com.meticulouscreations.homesafe.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.Saver
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.snapshots.SnapshotStateList

/**
 * A drawer app's stack of pushed pages, kept across the Activity's recreation (see
 * [SavedNavigation]): each page is [encode]d to a list of strings (its kind first, then its
 * arguments) and [decode]d back. A page that [encode] returns null for is left out, and whatever
 * lies above it with it. Anything not understood on the way back (an older or foreign save, a
 * page [decode] doesn't know) is no save at all, and the stack starts from [initial] instead, as
 * a fresh one does; so does any save made under another epoch (another process's).
 *
 * A save of an emptied stack is a save: a stack the user has popped back to the tabs stays that
 * way instead of being seeded from [initial] again.
 */
@Composable
fun <T : Any> rememberSavedPages(
    initial: List<T>,
    encode: (T) -> List<String>?,
    decode: (List<String>) -> T?,
): SnapshotStateList<T> {
    val epoch = LocalNavigationEpoch.current
    return rememberSaveable(epoch, saver = remember(epoch) { savedPagesSaver(epoch, encode, decode) }) {
        mutableStateListOf<T>().apply { addAll(initial) }
    }
}

fun <T : Any> savedPagesSaver(
    epoch: String,
    encode: (T) -> List<String>?,
    decode: (List<String>) -> T?,
): Saver<SnapshotStateList<T>, Any> = Saver(
    save = { stack -> SavedPages.save(epoch, stack, encode) },
    restore = { saved -> SavedPages.restore(saved, epoch, decode)?.let { pages -> mutableStateListOf<T>().apply { addAll(pages) } } },
)

/** What [rememberSavedPages] keeps, without Compose in the way. */
object SavedPages {
    fun <T : Any> save(epoch: String, pages: List<T>, encode: (T) -> List<String>?): List<Any> {
        val encoded = ArrayList<Any>(pages.size)
        for (page in pages) encoded.add(ArrayList(encode(page) ?: break))
        return SavedNavigation.pack(epoch, encoded)
    }

    /** The pages [save] was given (possibly none), or null for anything that isn't a current save under [epoch]. */
    fun <T : Any> restore(saved: Any?, epoch: String, decode: (List<String>) -> T?): List<T>? {
        val entries = SavedNavigation.unpack(saved, epoch) ?: return null
        val pages = ArrayList<T>(entries.size)
        for (entry in entries) {
            val parts = (entry as? List<*>)?.map { it as? String ?: return null } ?: return null
            pages.add(decode(parts) ?: return null)
        }
        return pages
    }
}
