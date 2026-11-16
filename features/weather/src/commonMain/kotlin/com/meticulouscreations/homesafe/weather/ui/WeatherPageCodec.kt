package com.meticulouscreations.homesafe.weather.ui

import com.meticulouscreations.homesafe.weather.ski.domain.SkiResortRef

/** [WeatherPage] as the strings it is saved as across the Activity's recreation (see `rememberSavedPages`). */
internal object WeatherPageCodec {
    fun encode(page: WeatherPage): List<String> = when (page) {
        WeatherPage.Places -> listOf("places")
        WeatherPage.Settings -> listOf("settings")
        is WeatherPage.Alert -> listOf("alert", page.id)
        is WeatherPage.SkiResort -> listOf("skiResort", page.ref.slug, page.ref.regionSlug, page.ref.name)
        WeatherPage.SkiFind -> listOf("skiFind")
        is WeatherPage.SkiRegion -> listOf("skiRegion", page.slug)
        WeatherPage.SkiSetup -> listOf("skiSetup")
    }

    fun decode(parts: List<String>): WeatherPage? = when (parts.firstOrNull()) {
        "places" -> WeatherPage.Places.takeIf { parts.size == 1 }
        "settings" -> WeatherPage.Settings.takeIf { parts.size == 1 }
        "alert" -> if (parts.size == 2) WeatherPage.Alert(parts[1]) else null
        "skiResort" -> if (parts.size == 4) WeatherPage.SkiResort(SkiResortRef(parts[1], parts[2], parts[3])) else null
        "skiFind" -> WeatherPage.SkiFind.takeIf { parts.size == 1 }
        "skiRegion" -> if (parts.size == 2) WeatherPage.SkiRegion(parts[1]) else null
        "skiSetup" -> WeatherPage.SkiSetup.takeIf { parts.size == 1 }
        else -> null
    }
}
