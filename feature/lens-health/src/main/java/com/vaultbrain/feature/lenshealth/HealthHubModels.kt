package com.vaultbrain.feature.lenshealth

data class HealthVitalsSnapshot(
    val steps: Int = 8420,
    val stepGoal: Int = 10000,
    val heartRate: Int = 68,
    val activeCalories: Int = 520,
    val sleepDurationFormatted: String = "7h 45m"
)

data class MedicationSubscription(
    val id: String,
    val name: String,
    val category: String,
    val doctorOrPharmacy: String,
    val dosageInstructions: String,
    val daysRemaining: Int,
    val totalCycleDays: Int,
    val pillsLeft: Int,
    val totalPills: Int,
    val rxNumber: String,
    var didTakeDoseToday: Boolean = false
)

data class EmergencyMedicalProfile(
    val bloodType: String = "O+",
    val allergies: String = "Penicillin, Peanuts",
    val emergencyContact: String = "Sarah El Sayed (+1 555-0192)",
    val insuranceProvider: String = "BlueCross BlueShield",
    val memberId: String = "BCBS-918204910"
)

object HealthHubDataProvider {
    fun getVitals(): HealthVitalsSnapshot = HealthVitalsSnapshot()

    fun getMedications(): List<MedicationSubscription> {
        return listOf(
            MedicationSubscription(
                id = "rx-1",
                name = "Atorvastatin 20mg",
                category = "Cardiology",
                doctorOrPharmacy = "Dr. Sarah Johnson • CVS #4912",
                dosageInstructions = "1 Tablet daily at bedtime",
                daysRemaining = 14,
                totalCycleDays = 30,
                pillsLeft = 14,
                totalPills = 30,
                rxNumber = "RX-8491029",
                didTakeDoseToday = true
            ),
            MedicationSubscription(
                id = "rx-2",
                name = "Vitamin D3 5,000 IU",
                category = "Supplements",
                doctorOrPharmacy = "Walgreens Pharmacy #102",
                dosageInstructions = "1 Softgel daily with meal",
                daysRemaining = 22,
                totalCycleDays = 60,
                pillsLeft = 45,
                totalPills = 60,
                rxNumber = "OTC-20948",
                didTakeDoseToday = false
            )
        )
    }

    fun getMedicalProfile(): EmergencyMedicalProfile = EmergencyMedicalProfile()
}
