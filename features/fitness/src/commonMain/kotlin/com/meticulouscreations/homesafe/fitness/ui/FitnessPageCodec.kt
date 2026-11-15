package com.meticulouscreations.homesafe.fitness.ui

/** [FitnessPage] as the strings it is saved as across the Activity's recreation (see `rememberSavedPages`). */
internal object FitnessPageCodec {
    fun encode(page: FitnessPage): List<String> = when (page) {
        FitnessPage.Workout -> listOf("workout")
        is FitnessPage.Lift -> listOf("lift", page.exerciseId)
        is FitnessPage.Edit -> listOf("edit", page.exerciseId)
        FitnessPage.Import -> listOf("import")
        FitnessPage.Heart -> listOf("heart")
        is FitnessPage.Session -> listOf("session", page.workoutId?.toString().orEmpty(), page.epochDay.toString())
        FitnessPage.Runs -> listOf("runs")
        is FitnessPage.Run -> listOf("run", page.id)
        FitnessPage.Recording -> listOf("recording")
    }

    fun decode(parts: List<String>): FitnessPage? = when (parts.firstOrNull()) {
        "workout" -> FitnessPage.Workout.takeIf { parts.size == 1 }
        "lift" -> if (parts.size == 2) FitnessPage.Lift(parts[1]) else null
        "edit" -> if (parts.size == 2) FitnessPage.Edit(parts[1]) else null
        "import" -> FitnessPage.Import.takeIf { parts.size == 1 }
        "heart" -> FitnessPage.Heart.takeIf { parts.size == 1 }
        "session" -> if (parts.size == 3) {
            val workoutId = if (parts[1].isEmpty()) null else parts[1].toLongOrNull() ?: return null
            parts[2].toLongOrNull()?.let { FitnessPage.Session(workoutId, it) }
        } else {
            null
        }
        "runs" -> FitnessPage.Runs.takeIf { parts.size == 1 }
        "run" -> if (parts.size == 2) FitnessPage.Run(parts[1]) else null
        "recording" -> FitnessPage.Recording.takeIf { parts.size == 1 }
        else -> null
    }
}
