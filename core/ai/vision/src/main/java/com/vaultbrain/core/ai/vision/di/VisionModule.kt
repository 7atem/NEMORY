package com.vaultbrain.core.ai.vision.di

import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScannerOptions
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.label.ImageLabeler
import com.google.mlkit.vision.label.ImageLabeling
import com.google.mlkit.vision.label.defaults.ImageLabelerOptions
import com.vaultbrain.core.ai.vision.DocumentClassifier
import com.vaultbrain.core.ai.vision.VisionAnalyzer
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object VisionModule {

    @Provides
    @Singleton
    fun provideImageLabeler(): ImageLabeler =
        ImageLabeling.getClient(ImageLabelerOptions.DEFAULT_OPTIONS)

    @Provides
    @Singleton
    fun provideBarcodeScanner(): BarcodeScanner =
        BarcodeScanning.getClient(BarcodeScannerOptions.Builder().build())

    @Provides
    @Singleton
    fun provideVisionAnalyzer(
        documentClassifier: DocumentClassifier,
        imageLabeler: ImageLabeler,
        barcodeScanner: BarcodeScanner
    ): VisionAnalyzer = VisionAnalyzer(documentClassifier, imageLabeler, barcodeScanner)
}
