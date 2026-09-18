package com.vaultbrain.feature.capture

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class OcrLayoutReconstructionTest {

    @Test
    fun `reconstructs receipt rows when ML Kit returns separate column blocks`() {
        val blockOrderedLines = listOf(
            PositionedOcrLine("MILK", 50, 100, 110, 120),
            PositionedOcrLine("CHEESE", 50, 130, 130, 150),
            PositionedOcrLine("CHIPS", 50, 160, 115, 180),
            PositionedOcrLine("${'$'}7", 230, 100, 260, 120),
            PositionedOcrLine("${'$'}1.89", 220, 130, 275, 150),
            PositionedOcrLine("${'$'}120.1", 215, 160, 280, 180),
            PositionedOcrLine("GROCERY DEPOT", 140, 10, 300, 30),
            PositionedOcrLine("979 NATUREVILLE,", 130, 40, 315, 60),
            PositionedOcrLine("LAS VEGAS", 160, 70, 270, 90)
        )

        val reconstructed = reconstructSpatialText(blockOrderedLines)
        println("DEBUG RECONSTRUCTED OUTPUT:\n$reconstructed")

        assertThat(reconstructed.lines()).containsExactly(
            "GROCERY DEPOT",
            "979 NATUREVILLE,",
            "LAS VEGAS",
            "MILK   ${'$'}7",
            "CHEESE   ${'$'}1.89",
            "CHIPS   ${'$'}120.1"
        ).inOrder()
    }
}
