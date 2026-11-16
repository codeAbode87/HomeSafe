package com.meticulouscreations.homesafe

import androidx.activity.result.ActivityResultCallback
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContract
import androidx.core.app.ActivityOptionsCompat
import androidx.fragment.app.FragmentActivity

/**
 * The window the app is in, for the parts of the graph that need one: a permission dialog, a
 * biometric prompt. The graph outlives any one Activity (it is kept across the recreation a font
 * size or language change causes, so the session and the navigation survive it), so it can't hold
 * the Activity it was made in; it holds this, and the Activity that is up [attach]es itself.
 *
 * Everything here is for the main thread.
 */
class ActivityAccess {
    /** The Activity that is up, or null between one being destroyed and the next being created. */
    var current: FragmentActivity? = null
        private set

    private val onAttached = mutableListOf<(FragmentActivity) -> Unit>()
    private val launchers = mutableListOf<RebindingLauncher<*, *>>()

    fun attach(activity: FragmentActivity) {
        current = activity
        // A copy: a listener may register another.
        onAttached.toList().forEach { it(activity) }
    }

    /** [activity] is being destroyed: nothing of it is held past this, and its launchers are let go. */
    fun detach(activity: FragmentActivity) {
        if (current !== activity) return
        current = null
        launchers.forEach { it.release() }
    }

    /** Runs [block] for the Activity that is up now, if any, and for each one that takes its place. */
    fun whenAttached(block: (FragmentActivity) -> Unit) {
        onAttached += block
        current?.let(block)
    }

    /**
     * A launcher that is registered again with each Activity (a registry belongs to one), so a
     * permission asked for after a recreation shows its dialog from the new window and the answer
     * comes back to [callback]. Registered on the registry directly, not through a lifecycle
     * owner: the graph is built too late in onCreate for the lifecycle-bound register(). Launching
     * with no Activity up is an [IllegalStateException], as launching a launcher that has been
     * unregistered would be.
     */
    fun <I, O> launcher(key: String, contract: ActivityResultContract<I, O>, callback: ActivityResultCallback<O>): ActivityResultLauncher<I> {
        val launcher = RebindingLauncher(key, contract, callback)
        launchers += launcher
        whenAttached(launcher::bind)
        return launcher
    }

    private class RebindingLauncher<I, O>(
        private val key: String,
        private val resultContract: ActivityResultContract<I, O>,
        private val callback: ActivityResultCallback<O>,
    ) : ActivityResultLauncher<I>() {
        private var inner: ActivityResultLauncher<I>? = null

        fun bind(activity: FragmentActivity) {
            inner?.unregister()
            inner = activity.activityResultRegistry.register(key, resultContract, callback)
        }

        fun release() {
            inner?.unregister()
            inner = null
        }

        override fun launch(input: I, options: ActivityOptionsCompat?) {
            val launcher = checkNotNull(inner) { "No Activity is up to launch from" }
            launcher.launch(input, options)
        }

        override fun unregister() = release()

        override val contract: ActivityResultContract<I, *> get() = resultContract
    }
}
