package com.vaultbrain.core.ai.vision.di

import android.content.Context
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.vaultbrain.core.ai.vision.DocumentClassifier
import com.vaultbrain.core.ai.vision.DocumentClassifier.TfliteRunner
import com.vaultbrain.core.ai.vision.VisionAnalyzer
import com.vaultbrain.core.ai.vision.SemanticVisualClassifier
import com.vaultbrain.core.ai.vision.UnavailableSemanticVisualClassifier
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    @Provides
    @Singleton
    fun provideBarcodeScanner(): BarcodeScanner {
        val options = BarcodeScannerOptions.Builder()
            .enableAllPotentialBarcodes()
            .build()
        return BarcodeScanning.getClient(options)
    }

    @Provides
    @Singleton
    fun provideImageLabeler(): ImageLabeler {
        val options = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.6f)
            .build()
        return ImageLabeling.getClient(options)
    }

    @Provides
    fun provideSemanticVisualClassifier(): SemanticVisualClassifier =
        UnavailableSemanticVisualClassifier()

    @Provides
    @Singleton
    fun provideClassifierRunnerProvider(
        @ApplicationContext context: Context
    ): @JvmSuppressWildcards () -> TfliteRunner? = { DocumentClassifier.createRunner(context) }

    @Provides
    @Singleton
    fun provideVisionAnalyzer(
        documentClassifier: DocumentClassifier,
        barcodeScanner: BarcodeScanner,
        imageLabeler: ImageLabeler,
        semanticVisualClassifier: SemanticVisualClassifier
    ): VisionAnalyzer = VisionAnalyzer(
        documentClassifier = documentClassifier,
        barcodeScanner = barcodeScanner,
        imageLabeler = imageLabeler,
        semanticVisualClassifier = semanticVisualClassifier
    )
}
