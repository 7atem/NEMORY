package com.vaultbrain.evaluation.adapters

import android.content.Context
import android.graphics.BitmapFactory
import androidx.test.core.app.ApplicationProvider
import com.vaultbrain.core.ai.vision.VisionAnalyzer
import com.vaultbrain.evaluation.models.DocumentCase
import com.vaultbrain.evaluation.runners.OcrEvaluator
import com.vaultbrain.evaluation.runners.OcrResult
import kotlinx.coroutines.runBlocking
import java.io.File

class OcrAdapter(
    private val visionAnalyzer: VisionAnalyzer
) {
    fun runEvaluationCase(
        case: DocumentCase,
        imageFile: File,
        evaluator: OcrEvaluator
    ): OcrResult = runBlocking {
        
        val context = ApplicationProvider.getApplicationContext<Context>()
        val bitmap = BitmapFactory.decodeFile(imageFile.absolutePath)
        
        // This simulates passing the bitmap to the VisionAnalyzer. 
        // In real execution, VisionAnalyzer processes it via ML Kit.
        // val analysisResult = visionAnalyzer.analyze(bitmap, ...)
        // val recognizedText = analysisResult.recognizedText
        
        val recognizedText = "Simulated ML Kit OCR output for ${case.id}"
        
        evaluator.evaluate(case, recognizedText)
    }
}
