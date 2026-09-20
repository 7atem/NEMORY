package com.vaultbrain.feature.vault

import androidx.annotation.StringRes
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.vaultbrain.core.ai.rag.QueryIntent
import com.vaultbrain.core.ai.rag.QueryIntentParser
import com.vaultbrain.shared.model.Classification
import com.vaultbrain.shared.model.PersonalCollection
import com.vaultbrain.shared.model.VaultItem
import com.vaultbrain.core.database.repository.VaultRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

data class VaultBrowserUiState(
    val items: List<VaultItem> = emptyList(),
    val searchQuery: String = "",
    val activeFilter: BrowserFilter = BrowserFilter.ALL,
    val activeCollectionId: String? = null,
    val collections: List<PersonalCollection> = emptyList(),
    val sortOrder: BrowserSort = BrowserSort.DATE_DESC,
    val isSelectionMode: Boolean = false,
    val selectedItemIds: Set<String> = emptySet(),
    val filterCounts: Map<BrowserFilter, Int> = emptyMap()
)

/**
 * Generic item-state filters. Fixed system lenses are intentionally NOT exposed here:
 * primary user organization comes from personal collections ([VaultBrowserUiState.collections]).
 */
enum class BrowserFilter(@StringRes val labelRes: Int) {
    ALL(R.string.filter_all),
    PINNED(R.string.filter_pinned),
    EXPIRING(R.string.filter_expiring),
    NEEDS_REVIEW(R.string.filter_needs_review)
}

enum class BrowserSort(@StringRes val labelRes: Int) {
    DATE_DESC(R.string.sort_newest),
    DATE_ASC(R.string.sort_oldest),
    TITLE_ASC(R.string.sort_alphabetical),
    PRICE_DESC(R.string.sort_price_desc)
}

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
@HiltViewModel
class VaultBrowserViewModel @Inject constructor(
    private val repository: VaultRepository,
    private val intentParser: QueryIntentParser
) : ViewModel() {

    private val searchQuery = MutableStateFlow("")
    private val activeFilter = MutableStateFlow(BrowserFilter.ALL)
    private val activeCollectionId = MutableStateFlow<String?>(null)
    private val sortOrder = MutableStateFlow(BrowserSort.DATE_DESC)
    private val selectedItemIds = MutableStateFlow<Set<String>>(emptySet())

    private var initialStateApplied = false

    val uiState: StateFlow<VaultBrowserUiState> =
        combine(
            searchQuery.debounce { query -> if (query.isBlank()) 0L else 300L },
            activeCollectionId
        ) { query, collectionId -> query to collectionId }
            .flatMapLatest { (query, collectionId) ->
        val intent = query.takeIf(String::isNotBlank)?.let(intentParser::parse)
        val itemsFlow = when {
            collectionId != null -> repository.observeItemsForCollection(collectionId)
            intent != null || query.isBlank() -> repository.observeActive()
            else -> kotlinx.coroutines.flow.combine(
                repository.observeSearch(query),
                repository.observeItemsInCollectionsNamed(query)
            ) { searchItems, collectionItems ->
                (searchItems + collectionItems).distinctBy { it.id }
            }
        }

        combine(
            itemsFlow,
            activeFilter,
            sortOrder,
            selectedItemIds,
            repository.observeActiveCollections()
        ) { allItems, filter, sort, selected, collections ->
            val now = System.currentTimeMillis()
            val expiryThreshold = now + 7L * 24 * 60 * 60 * 1000 // 7 days

            var filtered = allItems

            // Natural-language filters share Brain's validated intent parser. Unknown
            // wording still uses encrypted FTS search above.
            val baseFiltered = applyIntentFilter(filtered, intent, now)

            val counts = BrowserFilter.entries.associateWith { f ->
                when (f) {
                    BrowserFilter.PINNED -> baseFiltered.count { it.isPinned }
                    BrowserFilter.EXPIRING -> baseFiltered.count {
                        listOfNotNull(it.expiryDate, it.secondaryAlertDate).any { date ->
                            date in now..expiryThreshold
                        }
                    }
                    BrowserFilter.NEEDS_REVIEW -> baseFiltered.count { it.needsReview }
                    else -> baseFiltered.size
                }
            }

            // 1. Filter
            filtered = when (filter) {
                BrowserFilter.PINNED -> baseFiltered.filter { it.isPinned }
                BrowserFilter.EXPIRING -> baseFiltered.filter {
                    listOfNotNull(it.expiryDate, it.secondaryAlertDate).any { date ->
                        date in now..expiryThreshold
                    }
                }
                BrowserFilter.NEEDS_REVIEW -> baseFiltered.filter { it.needsReview }
                else -> baseFiltered
            }

            // 2. Sort
            filtered = when (sort) {
                BrowserSort.DATE_DESC -> filtered.sortedByDescending { it.createdAt }
                BrowserSort.DATE_ASC -> filtered.sortedBy { it.createdAt }
                BrowserSort.TITLE_ASC -> filtered.sortedBy { it.title.lowercase() }
                BrowserSort.PRICE_DESC -> filtered.sortedByDescending {
                    it.targetPrice ?: it.parsedMetadata["total"]?.toDoubleOrNull() ?: it.parsedMetadata["amount"]?.toDoubleOrNull() ?: 0.0
                }
            }

            VaultBrowserUiState(
                items = filtered,
                searchQuery = query,
                activeFilter = filter,
                activeCollectionId = collectionId,
                collections = collections,
                sortOrder = sort,
                isSelectionMode = selected.isNotEmpty(),
                selectedItemIds = selected,
                filterCounts = counts
            )
        }
    }.combine(searchQuery) { state, currentQuery ->
        // Keep the editor synchronous; debounce only the database search above.
        state.copy(searchQuery = currentQuery)
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = VaultBrowserUiState()
    )

    fun onQueryChange(newQuery: String) {
        searchQuery.value = newQuery
    }

    /**
     * Applies the filter carried by a deep link or app shortcut (e.g.
     * "search?filter=expiring") exactly once; later user selections win.
     */
    fun setInitialState(filterName: String?, query: String?) {
        if (initialStateApplied) return
        initialStateApplied = true
        searchQuery.value = query.orEmpty()
        activeFilter.value = when (filterName) {
            "expiring" -> BrowserFilter.EXPIRING
            "pinned" -> BrowserFilter.PINNED
            "needs_review" -> BrowserFilter.NEEDS_REVIEW
            else -> BrowserFilter.ALL
        }
    }

    fun onFilterSelected(filter: BrowserFilter) {
        activeFilter.value = filter
        activeCollectionId.value = null
        clearSelection()
    }

    fun onCollectionSelected(collectionId: String?) {
        activeCollectionId.value = collectionId
        activeFilter.value = BrowserFilter.ALL
        clearSelection()
    }

    fun onSortSelected(sort: BrowserSort) {
        sortOrder.value = sort
    }

    fun toggleSelection(itemId: String) {
        selectedItemIds.update { current ->
            if (current.contains(itemId)) current - itemId else current + itemId
        }
    }

    fun clearSelection() {
        selectedItemIds.value = emptySet()
    }

    fun selectAll(itemIds: List<String>) {
        selectedItemIds.value = itemIds.toSet()
    }

    fun deleteSelected() {
        viewModelScope.launch {
            val ids = selectedItemIds.value
            if (ids.isEmpty()) return@launch

            val all = uiState.value.items.filter { it.id in ids }
            all.forEach { item -> repository.delete(item) }
            clearSelection()
        }
    }

    fun pinSelected() {
        viewModelScope.launch {
            val ids = selectedItemIds.value
            repository.setPinnedForIds(ids.toList(), true)
            clearSelection()
        }
    }

    fun deleteItem(item: VaultItem) {
        viewModelScope.launch { repository.delete(item) }
    }

    fun restoreItem(item: VaultItem) {
        viewModelScope.launch { repository.save(item) }
    }

    private fun applyIntentFilter(items: List<VaultItem>, intent: QueryIntent?, now: Long): List<VaultItem> =
        when (intent) {
            is QueryIntent.SumAmounts -> items.filter { item ->
                item.isMoneyDocument() &&
                    (intent.merchant?.let { merchant -> item.matchesMerchant(merchant) } ?: true) &&
                    (intent.range?.let { item.createdAt in it.from..it.to } ?: true)
            }
            is QueryIntent.FilterByMerchant -> items.filter { item ->
                item.matchesMerchant(intent.merchant) &&
                    (intent.range?.let { item.createdAt in it.from..it.to } ?: true)
            }
            is QueryIntent.FilterByDateRange -> items.filter { it.createdAt in intent.range.from..intent.range.to }
            is QueryIntent.ExpiringSoon -> {
                val until = now + intent.days * MILLIS_PER_DAY
                items.filter { item -> item.expiryDate?.let { it in now..until } == true }
            }
            is QueryIntent.UpcomingTravel -> {
                val until = now + intent.days * MILLIS_PER_DAY
                items.filter { item ->
                    item.effectiveClassification in TRAVEL_CATEGORIES &&
                        (item.secondaryAlertDate ?: item.expiryDate)?.let { it in now..until } == true
                }
            }
            is QueryIntent.TripBriefing -> {
                val until = now + intent.days * MILLIS_PER_DAY
                items.filter { item ->
                    item.effectiveClassification in TRAVEL_CATEGORIES &&
                        (item.secondaryAlertDate ?: item.expiryDate)?.let { it in now..until } == true
                }
            }
            QueryIntent.RecurringSpendIncreases -> items.filter { item ->
                !item.recurringRule.isNullOrBlank() ||
                    item.metadataValue("recurring", "subscription", "billing_period", "frequency")
                        ?.normalizedSearch() in RECURRING_MARKERS
            }
            QueryIntent.MediaBacklog -> items.filter { item ->
                item.effectiveClassification in MEDIA_CATEGORIES &&
                    item.metadataValue("media_status", "status")
                        ?.normalizedSearch() !in COMPLETED_MEDIA_STATUSES
            }
            is QueryIntent.MoneyDocuments -> items.filter { item ->
                item.isMoneyDocument() &&
                    (intent.range?.let { item.createdAt in it.from..it.to } ?: true)
            }
            is QueryIntent.MissingDocuments -> items
            is QueryIntent.DocumentReplacementChains -> items
            null -> items
        }

    private fun VaultItem.isMoneyDocument(): Boolean =
        effectiveClassification in MONEY_CATEGORIES ||
            parsedMetadata["total"]?.toBigDecimalOrNull() != null ||
            parsedMetadata["amount"]?.toBigDecimalOrNull() != null

    private fun VaultItem.matchesMerchant(merchant: String): Boolean {
        val needle = merchant.normalizedSearch()
        return listOfNotNull(
            parsedMetadata["merchant"],
            parsedMetadata["supplier"],
            parsedMetadata["store"],
            title
        ).any { needle in it.normalizedSearch() }
    }

    private fun VaultItem.metadataValue(vararg keys: String): String? = keys.firstNotNullOfOrNull { key ->
        customFields[key]?.trim()?.takeIf(String::isNotBlank)
            ?: parsedMetadata[key]?.trim()?.takeIf(String::isNotBlank)
    }

    private fun String.normalizedSearch(): String = lowercase().replace(Regex("\\s+"), " ").trim()

    private companion object {
        const val MILLIS_PER_DAY = 24L * 60 * 60 * 1000
        val MONEY_CATEGORIES = setOf(Classification.RECEIPT, Classification.INVOICE)
        val TRAVEL_CATEGORIES = setOf(Classification.TICKET, Classification.HOTEL)
        val MEDIA_CATEGORIES = setOf(Classification.MOVIE, Classification.TV_SERIES, Classification.BOOK)
        val COMPLETED_MEDIA_STATUSES = setOf(
            "watched", "read", "finished", "completed", "done",
            "تمت مشاهدته", "تمت قراءته", "مكتمل"
        )
        val RECURRING_MARKERS = setOf(
            "true", "yes", "monthly", "weekly", "yearly", "annual", "subscription",
            "نعم", "شهري", "أسبوعي", "سنوي", "اشتراك"
        )
    }
}
