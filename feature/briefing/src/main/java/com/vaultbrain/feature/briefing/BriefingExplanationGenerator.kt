package com.vaultbrain.feature.briefing

import com.vaultbrain.core.ai.llm.LlmClient
import javax.inject.Inject
import javax.inject.Singleton

/** Optional tiered on-device prose over an already-computed fact. It never creates a trigger. */
@Singleton
class BriefingExplanationGenerator @Inject constructor(
    private val llmClient: LlmClient
) {
    suspend fun explain(insight: TodayInsight, languageCode: String): String? {
        val arabic = languageCode.equals("ar", ignoreCase = true)
        val fact = insight.factForPrompt(arabic)
        val prompt = if (arabic) {
            """
                أنت تكتب ملاحظة قصيرة لتطبيق Nemory على الجهاز.
                أعد صياغة الحقيقة التالية في جملة عربية هادئة واحدة فقط.
                لا تضف أرقامًا أو حقائق أو نصائح أو إجراءات. لا تقل إن شيئًا تم حذفه أو جدولته أو تغييره.
                الحقيقة: $fact
            """.trimIndent()
        } else {
            """
                You write a short on-device note for Nemory.
                Rephrase the following fact as one calm English sentence only.
                Add no numbers, facts, advice, or actions. Never claim anything was deleted, scheduled, or changed.
                Fact: $fact
            """.trimIndent()
        }
        return llmClient.generate(prompt)?.validatedAgainst(fact)
    }

    internal fun TodayInsight.factForPrompt(arabic: Boolean): String = when (this) {
        is TodayInsight.Expiring -> if (arabic) {
            "$itemTitle تنتهي صلاحيته خلال $daysRemaining يومًا."
        } else {
            "$itemTitle expires in $daysRemaining days."
        }
        is TodayInsight.PossibleDuplicates -> if (arabic) {
            "$captureCount من العناصر المحفوظة قد تكون مكررة."
        } else {
            "$captureCount saved captures may be duplicates."
        }
        is TodayInsight.SpendingIncrease -> if (arabic) {
            "ارتفعت دفعة $provider المتكررة من $currency $previousAmount إلى $currency $currentAmount."
        } else {
            "$provider recurring charge increased from $currency $previousAmount to $currency $currentAmount."
        }
        is TodayInsight.UpcomingTravel -> if (arabic) {
            "$itemTitle موعده خلال $daysRemaining يومًا."
        } else {
            "$itemTitle is in $daysRemaining days."
        }
        is TodayInsight.MediaBacklog -> if (arabic) {
            "$itemCount عناصر ما زالت في قائمة المشاهدة أو القراءة، ومنها $sampleTitle."
        } else {
            "$itemCount items remain on the watch or reading list, including $sampleTitle."
        }
    }

    private fun String.validatedAgainst(fact: String): String? {
        val rawCandidate = trim().trim('"', '\'')
        if (rawCandidate.contains('\n') || rawCandidate.contains('\r')) return null
        val candidate = rawCandidate
            .replace(Regex("^[#*\\-•\\s]+"), "")
            .replace(Regex("\\s+"), " ")
        if (candidate.isBlank() || candidate.length > MAX_EXPLANATION_CHARS) return null
        if (FORBIDDEN_ACTION_CLAIMS.any { claim -> candidate.containsIgnoringCase(claim) }) return null
        val allowedNumbers = NUMBER.findAll(fact).map(MatchResult::value).toSet()
        val outputNumbers = NUMBER.findAll(candidate).map(MatchResult::value).toSet()
        if (!allowedNumbers.containsAll(outputNumbers)) return null
        return candidate
    }

    private fun String.containsIgnoringCase(value: String): Boolean = contains(value, ignoreCase = true)

    private companion object {
        const val MAX_EXPLANATION_CHARS = 240
        val NUMBER = Regex("\\d+(?:[.,]\\d+)?")
        val FORBIDDEN_ACTION_CLAIMS = listOf(
            "scheduled", "deleted", "removed", "changed", "updated", "sent", "shared",
            "تمت الجدولة", "تم الحذف", "تم التغيير", "تم الإرسال", "تمت المشاركة"
        )
    }
}
