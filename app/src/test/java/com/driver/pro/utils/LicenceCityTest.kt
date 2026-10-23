package com.driver.pro.utils

import com.driver.pro.network.City
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class LicenceCityTest {

    @Test
    fun only_london_stays_on_the_licence_list() {
        val cities = listOf(
            City(1, "London", "city"),
            City(2, "Birmingham", "city"),
            City(3, "Manchester", "city"),
        )
        val london = londonLicenceCities(cities)
        assertEquals(1, london.size)
        assertTrue(isLondonLicenceCity(london.single()))
        assertEquals("London", licenceCityLabel(london.single()))
        assertFalse(isLondonLicenceCity(cities[1]))
    }
}
