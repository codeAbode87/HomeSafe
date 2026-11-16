package com.meticulouscreations.homesafe.finance.ui

/** [FinanceDetail] as the strings it is saved as across the Activity's recreation (see `rememberSavedPages`). */
internal object FinancePageCodec {
    fun encode(page: FinanceDetail): List<String> = when (page) {
        is FinanceDetail.QuotePage -> listOf("quote", page.symbol)
        is FinanceDetail.IndicatorPage -> listOf("indicator", page.id)
        FinanceDetail.Connections -> listOf("connections")
        FinanceDetail.Glossary -> listOf("glossary")
        FinanceDetail.SheetSync -> listOf("sheetSync")
        FinanceDetail.BankSync -> listOf("bankSync")
        FinanceDetail.BudgetSettings -> listOf("budgetSettings")
        FinanceDetail.ChartSettings -> listOf("chartSettings")
        FinanceDetail.TypedBalances -> listOf("typedBalances")
        is FinanceDetail.WalletPage -> listOf("wallet", page.topic.name, page.focus?.name.orEmpty())
    }

    fun decode(parts: List<String>): FinanceDetail? = when (parts.firstOrNull()) {
        "quote" -> if (parts.size == 2) FinanceDetail.QuotePage(parts[1]) else null

        "indicator" -> if (parts.size == 2) FinanceDetail.IndicatorPage(parts[1]) else null

        "connections" -> FinanceDetail.Connections.takeIf { parts.size == 1 }

        "glossary" -> FinanceDetail.Glossary.takeIf { parts.size == 1 }

        "sheetSync" -> FinanceDetail.SheetSync.takeIf { parts.size == 1 }

        "bankSync" -> FinanceDetail.BankSync.takeIf { parts.size == 1 }

        "budgetSettings" -> FinanceDetail.BudgetSettings.takeIf { parts.size == 1 }

        "chartSettings" -> FinanceDetail.ChartSettings.takeIf { parts.size == 1 }

        "typedBalances" -> FinanceDetail.TypedBalances.takeIf { parts.size == 1 }

        "wallet" -> if (parts.size == 3) {
            val topic = WalletTopic.entries.firstOrNull { it.name == parts[1] }
            val focus = if (parts[2].isEmpty()) null else WalletSection.entries.firstOrNull { it.name == parts[2] } ?: return null
            topic?.let { FinanceDetail.WalletPage(it, focus) }
        } else {
            null
        }

        else -> null
    }
}
