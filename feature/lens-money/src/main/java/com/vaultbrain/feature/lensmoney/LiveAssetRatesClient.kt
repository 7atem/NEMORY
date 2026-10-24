package com.vaultbrain.feature.lensmoney

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

data class CryptoAssetRate(
    val symbol: String,
    val name: String,
    val priceUsd: Double,
    val priceAed: Double,
    val change24h: Double
)

object LiveAssetRatesClient {
    private val fallbackAssets = listOf(
        CryptoAssetRate("BTC", "Bitcoin", 80490.0, 295600.0, 1.45),
        CryptoAssetRate("ETH", "Ethereum", 2620.0, 9620.0, -0.62),
        CryptoAssetRate("SOL", "Solana", 178.50, 655.50, 3.20)
    )

    suspend fun fetchAssetRates(): List<CryptoAssetRate> = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://api.coingecko.com/api/v3/simple/price?ids=bitcoin,ethereum,solana&vs_currencies=usd,aed&include_24hr_change=true"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Nemory-App/1.0")
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val root = JSONObject(body)
                val list = mutableListOf<CryptoAssetRate>()

                val btc = root.optJSONObject("bitcoin")
                if (btc != null) {
                    list.add(
                        CryptoAssetRate(
                            symbol = "BTC",
                            name = "Bitcoin",
                            priceUsd = btc.optDouble("usd", 80490.0),
                            priceAed = btc.optDouble("aed", 295600.0),
                            change24h = btc.optDouble("usd_24h_change", 1.45)
                        )
                    )
                }

                val eth = root.optJSONObject("ethereum")
                if (eth != null) {
                    list.add(
                        CryptoAssetRate(
                            symbol = "ETH",
                            name = "Ethereum",
                            priceUsd = eth.optDouble("usd", 2620.0),
                            priceAed = eth.optDouble("aed", 9620.0),
                            change24h = eth.optDouble("usd_24h_change", -0.62)
                        )
                    )
                }

                val sol = root.optJSONObject("solana")
                if (sol != null) {
                    list.add(
                        CryptoAssetRate(
                            symbol = "SOL",
                            name = "Solana",
                            priceUsd = sol.optDouble("usd", 178.50),
                            priceAed = sol.optDouble("aed", 655.50),
                            change24h = sol.optDouble("usd_24h_change", 3.20)
                        )
                    )
                }

                if (list.isNotEmpty()) {
                    return@withContext list
                }
            }
        } catch (_: Exception) {
            // Fallback
        }

        fallbackAssets
    }
}
