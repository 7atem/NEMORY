package com.vaultbrain.feature.capture

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.LinearGradient
import android.graphics.Matrix
import android.graphics.Shader
import android.util.Log
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.vaultbrain.core.ai.heuristics.HeuristicExtractor
import com.vaultbrain.shared.domain.LensId
import org.junit.AfterClass
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import kotlinx.coroutines.runBlocking
import java.util.Locale

/** End-to-end rendered-image -> bundled OCR -> lens routing accuracy checks. */
@RunWith(AndroidJUnit4::class)
class ImageLensAccuracyTest {

    @Test fun moneyImage() = assertImageCase(LensId.MONEY, "NILE MARKET RECEIPT\nDate 15/08/2026\nTOTAL: 145.50 EGP", mapOf("total" to "145.5", "currency" to "EGP"))
    @Test fun healthImage() = assertImageCase(LensId.HEALTH, "MEDICAL PRESCRIPTION\nAmoxicillin 500mg\nTake twice daily", mapOf("dosage" to "500mg"))
    @Test fun travelImage() = assertImageCase(LensId.TRAVEL, "BOARDING PASS\nFlight MS777\nDeparture CAI Gate F4 Seat 12A", mapOf("flight_number" to "MS777", "gate" to "F4"))
    @Test fun bureaucracyImage() = assertImageCase(LensId.BUREAUCRACY, "PASSPORT\nPassport Number A12345678\nExpiry 2030-01-01", mapOf("document_number" to "A12345678"))
    @Test fun shoppingImage() = assertImageCase(LensId.MONEY, "ORGANIC COFFEE PRODUCT LABEL\nNet Wt 250g\nPrice 12.50 USD", mapOf("total" to "12.5", "currency" to "USD"))
    @Test fun homeImage() = assertImageCase(LensId.BUREAUCRACY, "WASHING MACHINE WARRANTY\nModel Number WM900\nGuarantee until 2028-04-10", mapOf("model_number" to "WM900"))
    @Test fun carImage() = assertImageCase(LensId.TRAVEL, "VEHICLE REGISTRATION\nCar Toyota Camry\nPlate: ABC 123\nOdometer 45000 km", mapOf("plate" to "ABC 123", "odometer" to "45000"))
    @Test fun servicesImage() = assertImageCase(LensId.MONEY, "SPARKLE DRY CLEANING\nPickup code 4821\nReturn by 2027-02-18", mapOf("pickup_code" to "4821", "return_by" to "2027-02-18"))
    @Test fun mediaImage() = assertImageCase(LensId.MEDIA, "MOVIE WATCHLIST\nDune Part Two\nFilm status watched", mapOf("media_type" to "movie", "media_status" to "watched"))
    @Test fun scamGuardImage() = assertImageCase(LensId.BUREAUCRACY, "SECURITY ALERT\nVerify account urgently\nhttps://bit.ly/account-check", mapOf("url" to "https://bit.ly/account-check"))
    @Test fun productivityImage() = assertImageCase(LensId.BUREAUCRACY, "PROJECT MEETING NOTES\nAction item send proposal\nDeadline 2027-03-10", mapOf("deadline" to "2027-03-10"))

    private fun assertImageCase(expectedLens: String, sourceText: String, expectedMetadata: Map<String, String>) {
        Distortion.entries.forEach { distortion ->
            val bitmap = renderDocument(sourceText, distortion)
            val recognized = try {
                runBlocking { recognizer.recognizeBestDocument(bitmap) }
            } finally {
                bitmap.recycle()
            }
            val recall = tokenRecall(sourceText, recognized)
            val result = extractor.extract(recognized)
            Log.i(TAG, "$expectedLens distortion=$distortion recall=$recall tags=${result.lensTags} OCR=$recognized")

            val minimumRecall = when (distortion) {
                Distortion.CLEAN -> 0.78
                Distortion.LOW_CONTRAST -> 0.62
                Distortion.CAMERA_LIKE -> 0.50
                Distortion.SIDEWAYS -> 0.62
            }
            assertTrue(
                "$expectedLens OCR token recall $recall below $minimumRecall. OCR=[$recognized]",
                recall >= minimumRecall
            )
            assertTrue(
                "$expectedLens routed to ${result.lensTags}. OCR=[$recognized]",
                expectedLens in result.lensTags
            )
            expectedMetadata.forEach { (key, expectedValue) ->
                assertTrue(
                    "$expectedLens metadata $key expected [$expectedValue], was [${result.metadata[key]}]. OCR=[$recognized]",
                    result.metadata[key] == expectedValue
                )
            }
        }
    }

    private fun renderDocument(text: String, distortion: Distortion): Bitmap {
        val bitmap = Bitmap.createBitmap(1_400, 1_600, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        canvas.drawColor(if (distortion == Distortion.CLEAN) Color.WHITE else Color.rgb(224, 222, 214))
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = if (distortion == Distortion.CLEAN) Color.rgb(20, 20, 20) else Color.rgb(105, 103, 98)
            textSize = when (distortion) {
                Distortion.CLEAN -> 50f
                Distortion.LOW_CONTRAST -> 43f
                Distortion.CAMERA_LIKE -> 38f
                Distortion.SIDEWAYS -> 50f
            }
            typeface = Typeface.create("sans-serif", Typeface.NORMAL)
        }
        canvas.save()
        val angle = when (distortion) {
            Distortion.CLEAN -> 0f
            Distortion.LOW_CONTRAST -> -1.4f
            Distortion.CAMERA_LIKE -> 3.2f
            Distortion.SIDEWAYS -> 0f
        }
        canvas.rotate(angle, bitmap.width / 2f, bitmap.height / 2f)
        var y = 150f
        text.lineSequence().forEachIndexed { index, line ->
            paint.typeface = Typeface.create("sans-serif", if (index == 0) Typeface.BOLD else Typeface.NORMAL)
            canvas.drawText(line, 95f, y, paint)
            y += 105f
        }
        canvas.restore()
        if (distortion == Distortion.SIDEWAYS) {
            val matrix = Matrix().apply { postRotate(90f) }
            val sideways = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            bitmap.recycle()
            return sideways
        }
        if (distortion != Distortion.CAMERA_LIKE) return bitmap

        val shadow = Paint().apply {
            shader = LinearGradient(
                0f, 0f, bitmap.width.toFloat(), 0f,
                intArrayOf(Color.TRANSPARENT, Color.argb(65, 25, 25, 25)),
                null,
                Shader.TileMode.CLAMP
            )
        }
        canvas.drawRect(0f, 0f, bitmap.width.toFloat(), bitmap.height.toFloat(), shadow)
        val downsampled = Bitmap.createScaledBitmap(bitmap, 700, 800, true)
        val cameraLike = Bitmap.createScaledBitmap(downsampled, 1_400, 1_600, true)
        downsampled.recycle()
        bitmap.recycle()
        return cameraLike
    }

    private fun tokenRecall(expected: String, actual: String): Double {
        val expectedTokens = tokens(expected)
        if (expectedTokens.isEmpty()) return 1.0
        val actualTokens = tokens(actual)
        return expectedTokens.count { it in actualTokens }.toDouble() / expectedTokens.size
    }

    private fun tokens(text: String): Set<String> = Regex("[A-Za-z0-9]{2,}")
        .findAll(text.lowercase(Locale.US))
        .map { it.value }
        .toSet()

    companion object {
        private const val TAG = "VaultBrainImageAccuracy"
        private val extractor = HeuristicExtractor()
        private val recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS)

        @JvmStatic
        @AfterClass
        fun closeRecognizer() {
            recognizer.close()
        }
    }

    private enum class Distortion {
        CLEAN,
        LOW_CONTRAST,
        CAMERA_LIKE,
        SIDEWAYS
    }
}
