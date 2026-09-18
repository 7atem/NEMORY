package com.vaultbrain.core.database.util

import androidx.room.TypeConverter
import com.vaultbrain.core.common.model.Classification
import com.vaultbrain.core.common.model.EnrichmentState
import com.vaultbrain.core.common.model.ProcessingState
import com.vaultbrain.core.common.model.SourceType
import com.vaultbrain.core.common.model.Tier
import com.vaultbrain.core.common.model.external.ConnectionState
import com.vaultbrain.core.common.model.external.ConnectorCapability
import com.vaultbrain.core.common.model.external.ExternalRecordType
import com.vaultbrain.core.common.model.external.ExternalRetention
import com.vaultbrain.core.common.model.external.ExternalSource
import com.vaultbrain.core.common.model.VaultReminderStatus
import com.vaultbrain.core.common.model.external.SensitivityLevel
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

/**
 * Room type converters for enums and JSON-backed collections.
 */
class RoomTypeConverters {
    @TypeConverter
    fun vaultReminderStatusToString(value: VaultReminderStatus?): String? = value?.name

    @TypeConverter
    fun stringToVaultReminderStatus(value: String?): VaultReminderStatus? =
        value?.let { enumValueOf<VaultReminderStatus>(it) }


    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter
    fun sourceTypeToString(value: SourceType?): String? = value?.name

    @TypeConverter
    fun stringToSourceType(value: String?): SourceType? =
        value?.let { enumValueOf<SourceType>(it) }

    @TypeConverter
    fun classificationToString(value: Classification?): String? = value?.name

    @TypeConverter
    fun stringToClassification(value: String?): Classification? =
        value?.let { enumValueOf<Classification>(it) }

    @TypeConverter
    fun enrichmentStateToString(value: EnrichmentState?): String? = value?.name

    @TypeConverter
    fun stringToEnrichmentState(value: String?): EnrichmentState? =
        value?.let { enumValueOf<EnrichmentState>(it) }

    @TypeConverter
    fun processingStateToString(value: ProcessingState?): String? = value?.name

    @TypeConverter
    fun stringToProcessingState(value: String?): ProcessingState? =
        value?.let { enumValueOf<ProcessingState>(it) }

    @TypeConverter
    fun tierToString(value: Tier?): String? = value?.name

    @TypeConverter
    fun stringToTier(value: String?): Tier? =
        value?.let { enumValueOf<Tier>(it) }

    @TypeConverter
    fun externalSourceToString(value: ExternalSource?): String? = value?.name

    @TypeConverter
    fun stringToExternalSource(value: String?): ExternalSource? =
        value?.let { enumValueOf<ExternalSource>(it) }

    @TypeConverter
    fun externalRecordTypeToString(value: ExternalRecordType?): String? = value?.name

    @TypeConverter
    fun stringToExternalRecordType(value: String?): ExternalRecordType? =
        value?.let { enumValueOf<ExternalRecordType>(it) }

    @TypeConverter
    fun sensitivityLevelToString(value: SensitivityLevel?): String? = value?.name

    @TypeConverter
    fun stringToSensitivityLevel(value: String?): SensitivityLevel? =
        value?.let { enumValueOf<SensitivityLevel>(it) }

    @TypeConverter
    fun externalRetentionToString(value: ExternalRetention?): String? = value?.name

    @TypeConverter
    fun stringToExternalRetention(value: String?): ExternalRetention? =
        value?.let { enumValueOf<ExternalRetention>(it) }

    @TypeConverter
    fun connectorCapabilityToString(value: ConnectorCapability?): String? = value?.name

    @TypeConverter
    fun stringToConnectorCapability(value: String?): ConnectorCapability? =
        value?.let { enumValueOf<ConnectorCapability>(it) }

    @TypeConverter
    fun connectionStateToString(value: ConnectionState?): String? = value?.name

    @TypeConverter
    fun stringToConnectionState(value: String?): ConnectionState? =
        value?.let { enumValueOf<ConnectionState>(it) }

    @TypeConverter
    fun stringMapToJson(value: Map<String, String>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToStringMap(value: String?): Map<String, String> =
        value?.let { json.decodeFromString<Map<String, String>?>(it) } ?: emptyMap()

    @TypeConverter
    fun stringSetToJson(value: Set<String>?): String? =
        value?.let { json.encodeToString(it.toList()) }

    @TypeConverter
    fun jsonToStringSet(value: String?): Set<String> =
        value?.let { json.decodeFromString<List<String>?>(it)?.toSet() } ?: emptySet()

    @TypeConverter
    fun stringListToJson(value: List<String>?): String? =
        value?.let { json.encodeToString(it) }

    @TypeConverter
    fun jsonToStringList(value: String?): List<String> =
        value?.let { json.decodeFromString<List<String>?>(it) } ?: emptyList()
}
