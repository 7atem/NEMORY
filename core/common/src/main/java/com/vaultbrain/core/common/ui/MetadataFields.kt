package com.vaultbrain.core.common.ui

import androidx.annotation.StringRes
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.vaultbrain.core.common.R
import com.vaultbrain.core.common.model.Classification

/**
 * Classification-aware ordering and human labels for parsed metadata keys,
 * shared by vault cards and the item detail screen.
 */
object MetadataFields {

    fun orderedFields(
        classification: Classification?,
        metadata: Map<String, String>
    ): List<Pair<String, String>> {
        return metadata.entries
            .filter { it.value.isNotBlank() }
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
        "tax" -> R.string.meta_tax
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
        // Optional additions for new fields (they will default to prettified key if null)
        else -> null
    }

    private val DEFAULT_PRIORITY = listOf(
        "date", "amount", "total", "due_date", "expiry_date"
    )
}
