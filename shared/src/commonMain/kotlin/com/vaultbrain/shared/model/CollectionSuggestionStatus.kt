package com.vaultbrain.shared.model

/**
 * Lifecycle of a machine-proposed collection membership.
 *
 * Suggestions are strictly separate from accepted organization
 * ([PersonalCollection] membership): a [SUGGESTED] row is only a proposal.
 * [REJECTED] rows are kept as suppression evidence so the same pair is not
 * proposed again; [ACCEPTED] rows record which membership came from a suggestion.
 */
enum class CollectionSuggestionStatus {
    SUGGESTED,
    ACCEPTED,
    REJECTED
}
