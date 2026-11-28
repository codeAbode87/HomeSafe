package com.meticulouscreations.homesafe.charging.ui

import kotlin.test.Test
import kotlin.test.assertEquals

class AverageKwTest {
    @Test
    fun aChargesAveragePowerIsItsEnergyOverItsHours() {
        // The fake household's charges: 24 kWh over three hours at home, 40 kWh in half an hour at a fast charger.
        assertEquals(8.0, averageKw(kwh = 24.0, seconds = 3 * 3600L), 1e-9)
        assertEquals(80.0, averageKw(kwh = 40.0, seconds = 1800L), 1e-9)
    }

    @Test
    fun aChargeOfAFewMinutesIsNotReadAsHours() {
        // Dividing by minutes made this 0.5; six minutes at 30 kW is 3 kWh.
        assertEquals(30.0, averageKw(kwh = 3.0, seconds = 360L), 1e-9)
    }
}
