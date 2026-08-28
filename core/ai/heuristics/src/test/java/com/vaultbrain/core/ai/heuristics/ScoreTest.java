package com.vaultbrain.core.ai.heuristics;
import org.junit.Test;
import com.vaultbrain.core.ai.heuristics.experience.ExperienceKeywordLibrary;

public class ScoreTest {
    @Test
    public void testScore() {
        String ocr = "SUPERMARKET\n123 Main St\n\nEggs ... 2.99\nMilk ... 3.49\nBread ... 2.00\n\nSubtotal ... 8.48\nTax ... 0.50\nTotal ... 8.98\nCashier: John\nReceipt #123456";
        System.out.println("Scoring: ");
        for (ExperienceKeywordLibrary.ScoredExperience exp : ExperienceKeywordLibrary.INSTANCE.quickScore(ocr, java.util.Collections.emptyMap(), 10)) {
            System.out.println(exp.getExperienceId() + " -> " + exp.getScore() + " (matches: " + exp.getMatchedKeywords() + ")");
        }
    }
}
