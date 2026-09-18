package com.vaultbrain.core.integrations.model

/**
 * User-initiated action that can be routed to the connector responsible for a record.
 */
sealed class ExternalAction {

    /** Open the record in its originating app. */
    data class OpenOriginal(val deepLinkUri: String) : ExternalAction()

    /** Promote an external record into a vault item. */
    data class ImportAsVaultItem(val record: ExternalRecord) : ExternalAction()

    /** Mark the record as handled so it stops surfacing. */
    data class MarkResolved(
        val connectorId: String,
        val accountId: String,
        val externalId: String
    ) : ExternalAction()
}
