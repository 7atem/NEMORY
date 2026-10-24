package com.vaultbrain.feature.lensmoney

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CurrencyRateItem(
    val code: String,
    val name: String,
    val rateAgainstUsd: Double
)

object LiveCurrencyClient {
    private val cachedRates = mutableMapOf(
        "EUR" to 0.893,
        "GBP" to 0.748,
        "AED" to 3.6725,
        "SAR" to 3.75,
        "JPY" to 156.8
    )

    suspend fun fetchRates(base: String = "USD"): List<CurrencyRateItem> = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://api.frankfurter.app/latest?from=$base&to=EUR,GBP,AED,SAR,JPY"
            val conn = URL(urlString).openConnection() as HttpURLConnection
            conn.connectTimeout = 4000
            conn.readTimeout = 4000
            conn.setRequestProperty("User-Agent", "Nemory-App/1.0")

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val ratesObj = JSONObject(body).optJSONObject("rates")
                if (ratesObj != null) {
                    val keys = ratesObj.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        cachedRates[k] = ratesObj.optDouble(k, cachedRates[k] ?: 1.0)
                    }
                }
            }
        } catch (_: Exception) {
            // Return cached rates
        }

        listOf(
            CurrencyRateItem("EUR", "Euro (€)", cachedRates["EUR"] ?: 0.893),
            CurrencyRateItem("AED", "UAE Dirham (AED)", cachedRates["AED"] ?: 3.6725),
            CurrencyRateItem("GBP", "British Pound (£)", cachedRates["GBP"] ?: 0.748),
            CurrencyRateItem("SAR", "Saudi Riyal (SAR)", cachedRates["SAR"] ?: 3.75),
            CurrencyRateItem("JPY", "Japanese Yen (¥)", cachedRates["JPY"] ?: 156.8)
        )
    }

    fun convert(amount: Double, from: String, to: String = "USD"): Double {
        if (from == to) return amount
        val fromRate = cachedRates[from] ?: 1.0
        val toRate = cachedRates[to] ?: 1.0
        return (amount / fromRate) * toRate
    }
}
