package com.vaultbrain.core.ai.heuristics

import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.shared.domain.LensId
import org.junit.Test

class DatasetRunnerTest {
    @Test
    fun runDataset() {
        val datasets = mapOf(
            "money_receipt_us" to "WALMART STORE #502\nDate: 2026-08-23\n\nEggs       $4.99\nBread      $2.49\n\nTAX:       $0.60\nTOTAL:     $8.08\nAmex ending in 1002",
            "money_invoice_freelance" to "INVOICE #2026-A\nWeb Development\n\nAmount Due: $1200.00\nDue Date: 2026-09-15",
            "health_prescription_1" to "DR. HOUSE CLINIC\nPatient: Gregory House\n\nRx: Vicodin 5mg\nTake 1 tablet every 6 hours\nRefills: 5",
            "health_lab_lipid" to "QUEST DIAGNOSTICS\nLipid Panel\n\nCholesterol: 195\nTriglycerides: 110\nRef Range: <200",
            "travel_flight_pass" to "DELTA AIRLINES\nJFK to LAX\nFlight: DL 404\nDate: 2026-11-20\nDepart: 08:15\nGate: B22\nSeat: 12F",
            "travel_hotel_booking" to "MARRIOTT DOWNTOWN\nConfirmation: 88XY92\nCheck-in: 2026-10-01\nCheck-out: 2026-10-05",
            "bureaucracy_passport_us" to "PASSPORT\nUSA\nPassport No: 987654321\nDOB: 1990-07-22\nExpiry Date: 2032-07-21",
            "productivity_bizcard_1" to "Sarah Connor\nSecurity Consultant\n\ns.connor@skynet.com\n555-0199",
            "home_warranty" to "SAMSUNG ELECTRONICS\nWarranty Certificate\nModel No: QN90B\nSerial No: S/N 89347209\nValid until: 2027-01-01",
            "car_license_plate" to "NEW YORK\nPLATE: EXCELSIOR\nEMPIRE STATE"
        )

        val extractor = HeuristicExtractor()
        println("================ DATASET RESULTS ================")
        datasets.forEach { (name, text) ->
            val res = extractor.extract(text = text, visionObjects = listOf(name.split("_")[1].replaceFirstChar(Char::uppercaseChar)), neuralClassification = null, neuralConfidence = 0f)
            println("## $name")
            println("- Lens: " + res.lensTags.joinToString())
            println("- Classification: " + res.inferredClassification)
            println("- Metadata: " + res.metadata)
            if (res.alertDate != null) println("- Alert Scheduled: Yes (Expiry: ${res.expiryDate}, Alert: ${res.alertDate})")
            println()
        }
        println("=================================================")
    }
}
