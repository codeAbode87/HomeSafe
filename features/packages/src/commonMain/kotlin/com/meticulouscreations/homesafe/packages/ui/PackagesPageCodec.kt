package com.meticulouscreations.homesafe.packages.ui

/** [PackagesPage] as the strings it is saved as across the Activity's recreation (see `rememberSavedPages`). */
internal object PackagesPageCodec {
    fun encode(page: PackagesPage): List<String> = when (page) {
        is PackagesPage.Detail -> listOf("detail", page.id)
        is PackagesPage.Claim -> listOf("claim", page.id)
        PackagesPage.Add -> listOf("add")
        PackagesPage.Setup -> listOf("setup")
    }

    fun decode(parts: List<String>): PackagesPage? = when (parts.firstOrNull()) {
        "detail" -> if (parts.size == 2) PackagesPage.Detail(parts[1]) else null
        "claim" -> if (parts.size == 2) PackagesPage.Claim(parts[1]) else null
        "add" -> PackagesPage.Add.takeIf { parts.size == 1 }
        "setup" -> PackagesPage.Setup.takeIf { parts.size == 1 }
        else -> null
    }
}
