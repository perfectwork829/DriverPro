package com.driver.pro.utils

import com.driver.pro.network.City

fun isLondonLicenceCity(city: City): Boolean =
    city.name.trim().equals("London", ignoreCase = true)

fun londonLicenceCities(cities: List<City>): List<City> =
    cities.filter(::isLondonLicenceCity)

fun licenceCityLabel(city: City): String = city.name.trim()
