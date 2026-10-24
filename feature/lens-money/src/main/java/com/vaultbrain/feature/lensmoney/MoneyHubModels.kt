package com.vaultbrain.feature.lensmoney

data class ExpenseCategoryItem(
    val id: String,
    val name: String,
    val totalFormatted: String,
    val amount: Double,
    val percentage: Float,
    val colorHex: Long
)

data class WarrantyCardItem(
    val id: String,
    val title: String,
    val issuer: String,
    val category: String,
    val daysRemaining: Int,
    val totalDays: Int,
    val progress: Float,
    val expirationFormatted: String,
    val policyNumber: String,
    val isUrgent: Boolean
)

object SpendingAnalyticsProvider {
    fun getCategories(): List<ExpenseCategoryItem> {
        return listOf(
            ExpenseCategoryItem(
                id = "cat-travel",
                name = "Travel & Flights",
                totalFormatted = "$520.00",
                amount = 520.00,
                percentage = 0.36f,
                colorHex = 0xFF2563EB
            ),
            ExpenseCategoryItem(
                id = "cat-groceries",
                name = "Groceries & Markets",
                totalFormatted = "$412.30",
                amount = 412.30,
                percentage = 0.29f,
                colorHex = 0xFF16A34A
            ),
            ExpenseCategoryItem(
                id = "cat-utilities",
                name = "Home & Utilities",
                totalFormatted = "$245.45",
                amount = 245.45,
                percentage = 0.17f,
                colorHex = 0xFFEA580C
            ),
            ExpenseCategoryItem(
                id = "cat-dining",
                name = "Dining & Cafes",
                totalFormatted = "$138.75",
                amount = 138.75,
                percentage = 0.10f,
                colorHex = 0xFF9333EA
            ),
            ExpenseCategoryItem(
                id = "cat-health",
                name = "Health & Pharmacy",
                totalFormatted = "$104.00",
                amount = 104.00,
                percentage = 0.08f,
                colorHex = 0xFFE11D48
            )
        )
    }

    fun getWarranties(): List<WarrantyCardItem> {
        return listOf(
            WarrantyCardItem(
                id = "war-1",
                title = "Apple MacBook Pro 16\"",
                issuer = "AppleCare+ Coverage",
                category = "Electronics",
                daysRemaining = 214,
                totalDays = 365,
                progress = 0.58f,
                expirationFormatted = "28 May 2027",
                policyNumber = "AC-918204-MBP",
                isUrgent = false
            ),
            WarrantyCardItem(
                id = "war-2",
                title = "Sony WH-1000XM5 Headphones",
                issuer = "Sony Limited Warranty",
                category = "Audio",
                daysRemaining = 42,
                totalDays = 365,
                progress = 0.88f,
                expirationFormatted = "04 Dec 2026",
                policyNumber = "SNY-581902",
                isUrgent = true
            ),
            WarrantyCardItem(
                id = "war-3",
                title = "Dyson V15 Detect Vacuum",
                issuer = "Dyson 2-Year Guarantee",
                category = "Appliances",
                daysRemaining = 320,
                totalDays = 730,
                progress = 0.56f,
                expirationFormatted = "10 Sep 2027",
                policyNumber = "DYS-401924",
                isUrgent = false
            )
        )
    }
}
