package com.vaultbrain.core.ai.llm

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class DocumentUnderstandingV2Test {
    @Test fun `fabricated evidence and inferred values are rejected`() {
        val raw = """{"facts":[{"kind":"money","field":"premium","value":"910","evidence":"Premium 910 EGP"}],"uncertainties":[]}"""
        assertThat(DocumentUnderstandingV2.parse(raw, "Premium 620 EGP")).isNull()
        assertThat(DocumentUnderstandingV2.parse(raw, "Premium 910 EGP")?.facts).hasSize(1)
        assertThat(DocumentUnderstandingV2.parse(raw.replace("\"910\"", "\"999\""), "Premium 910 EGP")).isNull()
    }
    @Test fun `unfinished reasoning is never visible`() {
        assertThat(ModelOutput.visible("<think>private reasoning")).isEmpty()
        assertThat(ModelOutput.visible("<think>private</think>Result")).isEqualTo("Result")
    }
}
