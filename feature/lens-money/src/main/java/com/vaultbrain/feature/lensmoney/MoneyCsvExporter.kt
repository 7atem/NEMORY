package com.vaultbrain.feature.lensmoney

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.vaultbrain.shared.model.VaultItem
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Utility for exporting financial [VaultItem]s to standard RFC 4180 CSV files.
 */
object MoneyCsvExporter {

    private val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /**
     * Generates a CSV file in app cache and returns the [File].
     */
    fun generateCsv(context: Context, items: List<VaultItem>): File {
        val exportDir = File(context.cacheDir, "exports").apply { mkdirs() }
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val file = File(exportDir, "vaultbrain_money_export_$timestamp.csv")

        FileWriter(file).use { writer ->
            // CSV Header
            writer.appendLine("Date,Title,Amount,Currency,Lens,Tags,Notes")

            for (item in items) {
                val dateStr = item.expiryDate?.let { dateFormat.format(Date(it)) }
                    ?: dateFormat.format(Date())
                val title = escapeCsv(item.title)
                val amount = item.parsedMetadata["amount"] ?: item.targetPrice?.toString() ?: ""
                val currency = item.parsedMetadata["currency"] ?: ""
                val lensTags = escapeCsv(item.lensTags.joinToString(";"))
                val notes = escapeCsv(item.summary ?: "")

                writer.appendLine("$dateStr,$title,$amount,$currency,Money,$lensTags,$notes")
            }
        }
        return file
    }

    /**
     * Creates an [Intent.ACTION_SEND] to share the generated CSV via Android system share sheet.
     */
    fun createShareIntent(context: Context, csvFile: File): Intent {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            csvFile
        )

        return Intent(Intent.ACTION_SEND).apply {
            type = "text/csv"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Nemory Money Export")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    private fun escapeCsv(value: String): String {
        return if (value.contains(",") || value.contains("\"") || value.contains("\n")) {
            "\"" + value.replace("\"", "\"\"") + "\""
        } else {
            value
        }
    }
}
