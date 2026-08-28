package com.vaultbrain.core.ai.heuristics

fun main() {
    val text = """
        TARGET STORE #1234
        123 Main St
        Date: 2026-08-23
        
        1x Apples     $2.99
        1x Milk       $3.49
        
        TAX:          $0.51
        TOTAL:        $6.99
        Visa ending in 4242
    """.trimIndent()
    
    val extractor = HeuristicExtractor()
    val res = extractor.extract(text, emptyList(), null, 0f)
    println("TOTAL: " + res.metadata["total"])
    println("EXPIRY: " + res.expiryDate)
}
