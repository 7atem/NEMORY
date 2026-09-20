package com.vaultbrain.core.ai.heuristics.experience.parsers

import com.vaultbrain.shared.model.ExperienceId

// Services parsers (subscriptions and household services).

/** Extracts phone plan fields (monthly fee, renewal date, carrier). */
class PhonePlanParser : KeywordDocumentParser(
    ExperienceId.PHONE_PLAN,
    typeName = "phone_plan",
    keywords = listOf("phone plan", "mobile plan", "باقة الموبايل", "باقة الهاتف", "gb", "prepaid", "postpaid"),
    amountKey = "monthly_fee",
    dateKey = "renewal_date",
    providerKey = "carrier"
)

/** Extracts internet plan fields (monthly fee, renewal date, ISP). */
class InternetPlanParser : KeywordDocumentParser(
    ExperienceId.INTERNET_PLAN,
    typeName = "internet_plan",
    keywords = listOf("internet plan", "باقة الإنترنت", "broadband", "fiber", "mbps", "فايبر"),
    amountKey = "monthly_fee",
    dateKey = "renewal_date",
    providerKey = "isp"
)

/** Extracts gym membership fields (fee, expiry, gym). */
class GymMembershipParser : KeywordDocumentParser(
    ExperienceId.GYM_MEMBERSHIP,
    typeName = "gym_membership",
    keywords = listOf("gym membership", "عضوية الجيم", "اشتراك الجيم", "fitness club", "نادي رياضي"),
    amountKey = "monthly_fee",
    dateKey = "expiry_date",
    providerKey = "gym",
    withReference = true
)

/** Extracts streaming service fields (fee, renewal date, service). */
class StreamingServiceParser : KeywordDocumentParser(
    ExperienceId.STREAMING_SERVICE,
    typeName = "streaming_service",
    keywords = listOf("streaming", "netflix", "shahid", "شاهد", "disney+", "prime video"),
    amountKey = "monthly_fee",
    dateKey = "renewal_date",
    providerKey = "service"
)

/** Extracts cloud storage plan fields (fee, renewal date, provider). */
class CloudStorageParser : KeywordDocumentParser(
    ExperienceId.CLOUD_STORAGE,
    typeName = "cloud_storage",
    keywords = listOf("cloud storage", "تخزين سحابي", "icloud", "google drive", "onedrive", "dropbox"),
    amountKey = "monthly_fee",
    dateKey = "renewal_date",
    providerKey = "provider"
)

/** Extracts cleaning service fields (total, date, company). */
class CleaningServiceParser : KeywordDocumentParser(
    ExperienceId.CLEANING_SERVICE,
    typeName = "cleaning_service",
    keywords = listOf("cleaning service", "خدمة تنظيف", "شركة تنظيف", "housekeeping", "deep cleaning"),
    amountKey = "total",
    dateKey = "service_date",
    providerKey = "company"
)
