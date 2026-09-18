package com.vaultbrain.feature.briefing

import com.vaultbrain.core.ai.llm.LlmClient
import com.vaultbrain.core.common.model.VaultReminder
import com.vaultbrain.core.integrations.model.ExternalRecord
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

    suspend fun generateDailySummary(
        insights: List<TodayInsight>,
        externalRecords: List<ExternalRecord>,
        reminders: List<VaultReminder>,
        languageCode: String
    ): String? {
        val arabic = languageCode.equals("ar", ignoreCase = true)
        
        val contextBuilder = StringBuilder()
        if (insights.isNotEmpty()) {
            contextBuilder.appendLine("Key Insights:")
            insights.forEach { insight ->
                contextBuilder.appendLine("- ${insight.factForPrompt(false)}")
            }
        }
        
        val calendarEvents = externalRecords.filter { it.source == com.vaultbrain.core.common.model.external.ExternalSource.CALENDAR }
        if (calendarEvents.isNotEmpty()) {
            contextBuilder.appendLine("Calendar Events:")
            calendarEvents.forEach { event ->
                contextBuilder.appendLine("- ${event.title}")
            }
        }
        
        val healthRecords = externalRecords.filter { it.source == com.vaultbrain.core.common.model.external.ExternalSource.HEALTH_CONNECT }
        if (healthRecords.isNotEmpty()) {
            contextBuilder.appendLine("Health Data:")
            healthRecords.forEach { health ->
                contextBuilder.appendLine("- ${health.description}")
            }
        }
        
        if (reminders.isNotEmpty()) {
            contextBuilder.appendLine("Reminders:")
            reminders.forEach { reminder ->
                contextBuilder.appendLine("- ${reminder.title}")
            }
        }
        
        val contextText = contextBuilder.toString().trim()
        if (contextText.isEmpty()) return null

        val prompt = if (arabic) {
            """
                أنت المساعد الشخصي الذكي Nemory الذي يعمل محلياً على الجهاز لضمان الخصوصية.
                اكتب ملخصاً صباحياً قصيراً ومترابطاً (جملتين أو 3 جمل فقط) يجمع المعلومات التالية لتقديم نظرة عامة مفيدة ومريحة ليوم المستخدم.
                استخدم نبرة هادئة وموجزة ومفيدة. لا تضف أي هلوسات أو معلومات غير موجودة في البيانات المقدمة.
                
                المعلومات:
                $contextText
            """.trimIndent()
        } else {
            """
                You are Nemory, a smart on-device personal assistant ensuring privacy.
                Write a short, cohesive morning summary (only 2-3 sentences) combining the following information to give the user a helpful, calm overview of their day.
                Use a calm, concise, and helpful tone. Do not add any hallucinations or information not present in the provided data.
                
                Information:
                $contextText
            """.trimIndent()
        }
        
        val candidate = llmClient.generate(prompt)?.trim()?.trim('"', '\'')
        if (candidate.isNullOrBlank()) return null
        
        return candidate.replace(Regex("^[#*\\-•\\s]+"), "").replace(Regex("\\s+"), " ")
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
        is TodayInsight.ActionableExternalInsight -> if (arabic) {
            "$insightTitle: $insightBody"
        } else {
            "$insightTitle: $insightBody"
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
