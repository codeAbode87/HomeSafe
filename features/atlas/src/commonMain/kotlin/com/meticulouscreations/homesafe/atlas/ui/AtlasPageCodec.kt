package com.meticulouscreations.homesafe.atlas.ui

/** [AtlasPage] as the strings it is saved as across the Activity's recreation (see `rememberSavedPages`). */
internal object AtlasPageCodec {
    fun encode(page: AtlasPage): List<String> = when (page) {
        is AtlasPage.Day -> listOf("day", page.day.toString())
        is AtlasPage.PlaceDetail -> listOf("place", page.placeKey)
        is AtlasPage.TripDetail -> listOf("trip", page.start.toString())
        AtlasPage.Sync -> listOf("sync")
    }

    fun decode(parts: List<String>): AtlasPage? = when (parts.firstOrNull()) {
        "day" -> if (parts.size == 2) parts[1].toIntOrNull()?.let { AtlasPage.Day(it) } else null
        "place" -> if (parts.size == 2) AtlasPage.PlaceDetail(parts[1]) else null
        "trip" -> if (parts.size == 2) parts[1].toLongOrNull()?.let { AtlasPage.TripDetail(it) } else null
        "sync" -> AtlasPage.Sync.takeIf { parts.size == 1 }
        else -> null
    }
}
