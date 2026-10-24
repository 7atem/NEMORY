package com.vaultbrain.feature.lenstravel

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Locale

data class PublicHoliday(
    val date: String,
    val localName: String,
    val name: String,
    val countryCode: String,
    val isLongWeekend: Boolean,
    val dayOfWeek: String
)

object LiveHolidayClient {
    private val fallbackHolidays = listOf(
        PublicHoliday("2026-11-11", "Veterans Day", "Veterans Day", "US", false, "Wednesday"),
        PublicHoliday("2026-11-26", "Thanksgiving Day", "Thanksgiving Day", "US", true, "Thursday"),
        PublicHoliday("2026-12-25", "Christmas Day", "Christmas Day", "US", true, "Friday"),
        PublicHoliday("2027-01-01", "New Year's Day", "New Year's Day", "US", true, "Friday")
    )

    suspend fun fetchUpcomingHolidays(countryCode: String = "US"): List<PublicHoliday> = withContext(Dispatchers.IO) {
        try {
            val urlString = "https://date.nager.at/api/v3/NextPublicHolidays/$countryCode"
            val conn = (URL(urlString).openConnection() as HttpURLConnection).apply {
                connectTimeout = 5000
                readTimeout = 5000
                setRequestProperty("User-Agent", "Nemory-App/1.0")
            }

            if (conn.responseCode == 200) {
                val body = conn.inputStream.bufferedReader().use { it.readText() }
                val array = JSONArray(body)
                val list = mutableListOf<PublicHoliday>()
                val parser = SimpleDateFormat("yyyy-MM-dd", Locale.US)
                val dayFormatter = SimpleDateFormat("EEEE", Locale.US)
                val cal = Calendar.getInstance()

                for (i in 0 until minOf(array.length(), 5)) {
                    val item = array.optJSONObject(i) ?: continue
                    val dStr = item.optString("date", "")
                    val name = item.optString("name", "")
                    val local = item.optString("localName", "")
                    if (dStr.isEmpty() || name.isEmpty()) continue

                    var isLw = false
                    var dayName = "Day"
                    try {
                        val parsedDate = parser.parse(dStr)
                        if (parsedDate != null) {
                            dayName = dayFormatter.format(parsedDate)
                            cal.time = parsedDate
                            val dow = cal.get(Calendar.DAY_OF_WEEK)
                            // Calendar.MONDAY = 2, Calendar.FRIDAY = 6, Calendar.THURSDAY = 5
                            if (dow == Calendar.MONDAY || dow == Calendar.FRIDAY || dow == Calendar.THURSDAY) {
                                isLw = true
                            }
                        }
                    } catch (_: Exception) { }

                    list.add(
                        PublicHoliday(
                            date = dStr,
                            localName = local,
                            name = name,
                            countryCode = countryCode,
                            isLongWeekend = isLw,
                            dayOfWeek = dayName
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

        fallbackHolidays
    }
}
