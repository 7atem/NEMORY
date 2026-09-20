package com.vaultbrain.core.common.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vaultbrain.core.common.R
import com.vaultbrain.shared.model.Classification

/**
 * Classification-aware ordering and human labels for parsed metadata keys,
 * shared by vault cards and the item detail screen.
 */
object MetadataFields {

    fun orderedFields(
        classification: Classification?,
        metadata: Map<String, String>
    ): List<Pair<String, String>> {
        val priority = PRIORITY_BY_CLASSIFICATION[classification].orEmpty() + DEFAULT_PRIORITY
        val rank = priority.distinct().withIndex().associate { (index, key) -> key to index }
        return metadata.entries
            .filter { it.value.isNotBlank() }
            .sortedWith(compareBy<Map.Entry<String, String>> { rank[it.key] ?: Int.MAX_VALUE })
            .map { it.key to it.value }
    }

    /** Top [limit] fields after priority ordering. */
    fun topFields(
        classification: Classification?,
        metadata: Map<String, String>,
        limit: Int = 5
    ): List<Pair<String, String>> = orderedFields(classification, metadata).take(limit)

    /** Human-readable label for [key]; falls back to a prettified key. */
    @Composable
    fun label(key: String): String =
        labelRes(key)?.let { stringResource(it) } ?: prettifyKey(key)

    /** snake_case -> Title Case fallback for unmapped keys. */
    fun prettifyKey(key: String): String = key
        .split('_')
        .joinToString(" ") { word -> word.replaceFirstChar { it.titlecase() } }

    @StringRes
    private fun labelRes(key: String): Int? = when (key) {
        "merchant" -> R.string.meta_merchant
        "store" -> R.string.meta_store
        "total" -> R.string.meta_total
        "amount" -> R.string.meta_amount
        "price" -> R.string.meta_price
        "currency" -> R.string.meta_currency
        "date" -> R.string.meta_date
        "date_text" -> R.string.meta_date_as_printed
        "tax" -> R.string.meta_tax
        "subtotal" -> R.string.meta_subtotal
        "cashier" -> R.string.meta_cashier
        "item_count" -> R.string.meta_item_count
        "amount_tendered" -> R.string.meta_amount_tendered
        "change" -> R.string.meta_change
        "purchase_time" -> R.string.meta_purchase_time
        "transaction_number" -> R.string.meta_transaction_number
        "address" -> R.string.meta_address
        "card_last4" -> R.string.meta_payment_method
        "grocery_list" -> R.string.meta_grocery_list
        "items" -> R.string.meta_items
        "supplier" -> R.string.meta_supplier
        "biller" -> R.string.meta_biller
        "amount_due" -> R.string.meta_amount_due
        "due_date" -> R.string.meta_due_date
        "iban" -> R.string.meta_iban
        "reference_number" -> R.string.meta_reference_number
        "medication" -> R.string.meta_medication
        "dosage" -> R.string.meta_dosage
        "frequency" -> R.string.meta_frequency
        "doctor" -> R.string.meta_doctor
        "pharmacy" -> R.string.meta_pharmacy
        "refills" -> R.string.meta_refills
        "prescription_date" -> R.string.meta_prescription_date
        "lab_metric" -> R.string.meta_lab_metric
        "lab_value" -> R.string.meta_lab_value
        "reference_range" -> R.string.meta_reference_range
        "test_type" -> R.string.meta_test_type
        "test_date" -> R.string.meta_test_date
        "result_date" -> R.string.meta_result_date
        "document_type" -> R.string.meta_document_type
        "document_number" -> R.string.meta_document_number
        "passport_number" -> R.string.meta_passport_number
        "name" -> R.string.meta_name
        "nationality" -> R.string.meta_nationality
        "dob" -> R.string.meta_dob
        "expiry_date" -> R.string.meta_expiry_date
        "authority" -> R.string.meta_authority
        "flight_number" -> R.string.meta_flight_number
        "event" -> R.string.meta_event
        "route" -> R.string.meta_route
        "carrier" -> R.string.meta_carrier
        "departure_date" -> R.string.meta_departure_date
        "time" -> R.string.meta_departure_time
        "gate" -> R.string.meta_gate
        "seat" -> R.string.meta_seat
        "pnr" -> R.string.meta_pnr
        "passenger" -> R.string.meta_passenger
        "confirmation" -> R.string.meta_confirmation
        "hotel_name" -> R.string.meta_hotel_name
        "location" -> R.string.meta_location
        "check_in" -> R.string.meta_check_in
        "check_out" -> R.string.meta_check_out
        "room_type" -> R.string.meta_room_type
        "product" -> R.string.meta_product
        "model_number" -> R.string.meta_model_number
        "serial_number" -> R.string.meta_serial_number
        "brand" -> R.string.meta_brand
        "warranty_expiry" -> R.string.meta_warranty_expiry
        "warranty_duration" -> R.string.meta_warranty_duration
        "purchase_date" -> R.string.meta_purchase_date
        "company" -> R.string.meta_company
        "contact_name" -> R.string.meta_contact
        "email" -> R.string.meta_email
        "phone" -> R.string.meta_phone

        "job_title" -> R.string.meta_job_title
        "status" -> R.string.meta_status
        "author" -> R.string.meta_author
        "provider" -> R.string.meta_provider
        "season" -> R.string.meta_season
        "episode" -> R.string.meta_episode
        "media_status" -> R.string.meta_media_status
        "action_items" -> R.string.meta_action_items
        "recipient" -> R.string.meta_recipient
        "sender" -> R.string.meta_sender
        "tracking_number" -> R.string.meta_tracking_number
        "url" -> R.string.meta_url
        "vin" -> R.string.meta_vin
        "license_plate" -> R.string.meta_license_plate
        "odometer" -> R.string.meta_odometer
        "deadline" -> R.string.meta_deadline
        "loyalty_card" -> R.string.meta_loyalty_card
        "policy_number" -> R.string.meta_policy_number
        "pickup_code" -> R.string.meta_pickup_code
        "return_by" -> R.string.meta_return_by
        "provider_url" -> R.string.meta_provider_url
        "release_year" -> R.string.meta_release_year
        "financial_identifier" -> R.string.meta_financial_identifier
        "national_id" -> R.string.meta_national_id
        "patient_name" -> R.string.meta_patient_name
        // Optional additions for new fields (they will default to prettified key if null)
        else -> null
    }

    private val DEFAULT_PRIORITY = listOf(
        "date", "amount", "total", "due_date", "expiry_date"
    )

    private val PRIORITY_BY_CLASSIFICATION = mapOf(
        Classification.RECEIPT to listOf(
            "merchant", "total", "currency", "purchase_date", "date_text", "purchase_time", "subtotal",
            "tax", "items", "item_count", "cashier", "amount_tendered", "change",
            "transaction_number", "financial_identifier", "address", "card_last4", "reference_number"
        ),
        Classification.INVOICE to listOf(
            "supplier", "merchant", "total", "amount_due", "currency", "due_date", "date",
            "tax", "invoice_number", "financial_identifier", "reference_number", "iban"
        ),
        Classification.PRESCRIPTION to listOf(
            "patient_name", "medication", "dosage", "frequency", "doctor", "pharmacy", "refills", "prescription_date"
        ),
        Classification.TICKET to listOf(
            "passenger", "carrier", "flight_number", "route", "departure_date", "time", "gate", "seat", "pnr"
        ),
        Classification.REAL_ESTATE to listOf(
            "project_name", "property_address", "deed_number", "unit_number", "tenant_name", "landlord_name", "rent_amount", "installment_amount", "due_date", "lease_start", "lease_end"
        ),
        Classification.LEGAL_DOCUMENT to listOf(
            "attorney_name", "grantor_name", "poa_type", "court_name", "case_number", "hearing_date", "plaintiff", "defendant", "tax_id"
        ),
        Classification.BANK_STATEMENT to listOf(
            "account_number", "statement_period", "opening_balance", "closing_balance", "iban", "swift_code"
        ),
        Classification.UTILITY_BILL to listOf(
            "provider", "amount_due", "currency", "due_date", "account_number", "meter_number", "billing_cycle", "consumption"
        ),
        Classification.MEDICAL_RECORD to listOf(
            "patient_name", "patient_weight", "height", "vaccine_name", "dose_number", "next_dose_date", "tooth_number", "procedure_code", "dentist_name", "doctor"
        ),
        Classification.CRYPTO_TRANSACTION to listOf(
            "wallet_address", "tx_hash", "network", "crypto_amount", "currency", "date"
        ),
        Classification.ACADEMIC_RECORD to listOf(
            "institution_name", "student_id", "degree_title", "graduation_date", "gpa", "petition_status"
        ),
        Classification.IDENTITY_DOCUMENT to listOf(
            "national_id", "document_number", "name", "dob", "expiry_date", "authority"
        ),
        Classification.PASSPORT to listOf(
            "passport_number", "document_number", "national_id", "name", "nationality", "dob", "expiry_date", "authority"
        )
    )
}
