package com.vaultbrain.feature.lenstravel

data class TripPlaceItem(
    val id: String,
    val name: String,
    val category: String,
    val address: String,
    val latitude: Double,
    val longitude: Double,
    val etaMinutes: Int,
    val photoLabel: String
)

data class TripDocItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val category: String,
    val date: String? = null,
    val amount: String? = null,
    val refCode: String? = null,
    val originIata: String? = null,
    val destinationIata: String? = null,
    val seat: String? = null,
    val gate: String? = null,
    val terminal: String? = null,
    val boardingGroup: String? = null,
    val passengerName: String? = null
)

data class TripBinder(
    val id: String,
    val destination: String,
    val countryCode: String,
    val title: String,
    val dateRangeText: String,
    val isUpcoming: Boolean,
    val totalSpendFormatted: String,
    val flightItems: List<TripDocItem>,
    val lodgingItems: List<TripDocItem>,
    val identityItems: List<TripDocItem>,
    val places: List<TripPlaceItem>
)

object DefaultTripProvider {
    fun getSampleTrip(): TripBinder {
        return TripBinder(
            id = "trip-ny-2026",
            destination = "New York, USA",
            countryCode = "US",
            title = "New York Business & Travel",
            dateRangeText = "05 Nov – 12 Nov 2026",
            isUpcoming = true,
            totalSpendFormatted = "$1,280.45",
            flightItems = listOf(
                TripDocItem(
                    id = "flight-1",
                    title = "Emirates Flight EK 203",
                    subtitle = "Dubai (DXB) ➔ New York (JFK) • Seat 14A",
                    category = "Boarding Pass",
                    date = "05 Nov 2026",
                    amount = "$840.00",
                    refCode = "EK-203-JFK",
                    originIata = "DXB",
                    destinationIata = "JFK",
                    seat = "14A",
                    gate = "B22",
                    terminal = "3",
                    boardingGroup = "A",
                    passengerName = "EL SAYED / HATEM"
                ),
                TripDocItem(
                    id = "flight-2",
                    title = "Emirates Return EK 204",
                    subtitle = "New York (JFK) ➔ Dubai (DXB) • Seat 12B",
                    category = "Return E-Ticket",
                    date = "12 Nov 2026",
                    amount = null,
                    refCode = "EK-204-DXB",
                    originIata = "JFK",
                    destinationIata = "DXB",
                    seat = "12B",
                    gate = "A4",
                    terminal = "4",
                    boardingGroup = "B",
                    passengerName = "EL SAYED / HATEM"
                )
            ),
            lodgingItems = listOf(
                TripDocItem(
                    id = "hotel-1",
                    title = "The Manhattan Grand Hotel",
                    subtitle = "4 Nights • Deluxe King Suite • Midtown",
                    category = "Hotel Reservation",
                    date = "05 Nov – 09 Nov 2026",
                    amount = "$372.00",
                    refCode = "MGH-81920"
                )
            ),
            identityItems = listOf(
                TripDocItem(
                    id = "passport-1",
                    title = "Passport • Hatem El Sayed",
                    subtitle = "Valid until 2030 • Biometric Chip Verified",
                    category = "Passport",
                    refCode = "A9182374"
                ),
                TripDocItem(
                    id = "insurance-1",
                    title = "Allianz Worldwide Travel Protection",
                    subtitle = "Active Policy #ALZ-92810 • 24/7 Assistance",
                    category = "Insurance",
                    refCode = "ALZ-92810"
                )
            ),
            places = listOf(
                TripPlaceItem(
                    id = "place-hotel",
                    name = "The Manhattan Grand Hotel",
                    category = "Hotel",
                    address = "140 Park Ave, New York, NY 10017",
                    latitude = 40.7516,
                    longitude = -73.9772,
                    etaMinutes = 5,
                    photoLabel = "Midtown Manhattan"
                ),
                TripPlaceItem(
                    id = "place-jfk",
                    name = "JFK International Terminal 4",
                    category = "Airport",
                    address = "Queens, NY 11430",
                    latitude = 40.6413,
                    longitude = -73.7781,
                    etaMinutes = 42,
                    photoLabel = "Emirates Terminal"
                ),
                TripPlaceItem(
                    id = "place-park",
                    name = "Central Park & Bethesda Terrace",
                    category = "Attraction",
                    address = "Central Park, New York, NY 10024",
                    latitude = 40.7829,
                    longitude = -73.9654,
                    etaMinutes = 14,
                    photoLabel = "Iconic Promenade"
                ),
                TripPlaceItem(
                    id = "place-met",
                    name = "The Metropolitan Museum of Art",
                    category = "Culture",
                    address = "1000 5th Ave, New York, NY 10028",
                    latitude = 40.7794,
                    longitude = -73.9632,
                    etaMinutes = 18,
                    photoLabel = "World-Class Art"
                ),
                TripPlaceItem(
                    id = "place-times",
                    name = "Times Square & Broadway",
                    category = "Entertainment",
                    address = "Manhattan, NY 10036",
                    latitude = 40.7580,
                    longitude = -73.9855,
                    etaMinutes = 9,
                    photoLabel = "Theater District"
                )
            )
        )
    }
}
