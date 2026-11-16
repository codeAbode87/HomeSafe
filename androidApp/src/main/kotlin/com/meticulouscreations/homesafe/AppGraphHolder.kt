package com.meticulouscreations.homesafe

import android.content.Context
import androidx.lifecycle.ViewModel
import com.meticulouscreations.homesafe.di.AppGraph
import com.meticulouscreations.homesafe.di.createAppGraph
import com.meticulouscreations.homesafe.tracing.traceSection
import kotlin.random.Random

/**
 * The app graph for as long as the Activity's task is, not the Activity instance: a view model
 * outlives the recreation a font size, a language or a layout direction change causes, so the
 * graph, with the session in its cookie jar, the services it started and the view models that
 * were made from it, stay the one set. A graph per `onCreate` left the retained view models on
 * the old graph while the new Activity's services ran on a new one with no session.
 *
 * It is cleared when the Activity finishes for good (and with the process), as a graph per
 * Activity used to be dropped in `onDestroy`.
 *
 * [activities] is what the graph asks for a window with, in place of an Activity it would hold
 * (see [ActivityAccess]). [navigationEpoch] names this graph, and so this session, for the saved
 * navigation: a position saved by another process is not this session's to restore.
 */
internal class AppGraphHolder(applicationContext: Context) : ViewModel() {
    val activities = ActivityAccess()

    val navigationEpoch: String = Random.nextLong().toString(radix = 36)

    val graph: AppGraph = traceSection("createAppGraph") {
        createAppGraph(platformContext = PlatformContext(applicationContext, activities))
    }

    override fun onCleared() {
        // The graph's coroutine scope isn't cancelled, so its detection poller must not outlive
        // the graph's use: a later launch builds a new graph, and this one's would run beside it.
        graph.detectionAlertService.stop()
    }
}
