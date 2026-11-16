package com.meticulouscreations.homesafe.charging.ui

/** [ChargingPage] as the strings it is saved as across the Activity's recreation (see `rememberSavedPages`). */
internal object ChargingPageCodec {
    fun encode(page: ChargingPage): List<String> = when (page) {
        is ChargingPage.Charge -> listOf("charge", page.id.toString())
        is ChargingPage.Car -> listOf("car", page.id)
    }

    fun decode(parts: List<String>): ChargingPage? = when (parts.firstOrNull()) {
        "charge" -> if (parts.size == 2) parts[1].toLongOrNull()?.let { ChargingPage.Charge(it) } else null
        "car" -> if (parts.size == 2) ChargingPage.Car(parts[1]) else null
        else -> null
    }
}
