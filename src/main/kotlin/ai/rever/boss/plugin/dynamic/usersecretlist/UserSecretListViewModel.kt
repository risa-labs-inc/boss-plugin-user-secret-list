package ai.rever.boss.plugin.dynamic.usersecretlist

import ai.rever.boss.plugin.api.SecretDataProvider
import ai.rever.boss.plugin.api.SecretEntryWithSharingData
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/**
 * ViewModel for User Secret List (Read-Only)
 *
 * Uses SecretDataProvider interface for data operations.
 *
 * Features:
 * - Load secrets with sharing information (owned + shared)
 * - Client-side search/filter by website or username
 * - Pagination support
 * - No CRUD operations (read-only view)
 */
class UserSecretListViewModel(
    private val secretDataProvider: SecretDataProvider?,
    private val scope: CoroutineScope
) {
    private val _state = MutableStateFlow(UserSecretListState())
    val state: StateFlow<UserSecretListState> = _state.asStateFlow()

    // Job tracking to prevent race conditions
    private var loadJob: Job? = null
    private var loadMoreJob: Job? = null

    init {
        loadSecrets()
    }

    /**
     * Load all accessible secrets (owned + shared)
     */
    fun loadSecrets() {
        val provider = secretDataProvider ?: run {
            _state.update { it.copy(
                isLoading = false,
                errorMessage = "Secret data provider not available"
            ) }
            return
        }

        // Cancel any in-flight load or pagination requests
        loadJob?.cancel()
        loadMoreJob?.cancel()

        _state.update { it.copy(
            isLoading = true,
            errorMessage = null,
            searchQuery = "",
            currentOffset = 0,
            hasMore = true
        ) }

        loadJob = scope.launch {
            val result = provider.getUserSecretsWithSharingInfo(
                limit = _state.value.pageSize,
                offset = 0
            )

            result.onSuccess { paginatedResult ->
                val secrets = paginatedResult.data
                _state.update { it.copy(
                    allSecrets = secrets,
                    secrets = secrets,
                    isLoading = false,
                    currentOffset = secrets.size,
                    hasMore = paginatedResult.hasMore
                ) }
            }.onFailure { exception ->
                if (exception is CancellationException) return@onFailure

                val error = exception.message ?: "Unknown error"
                _state.update { it.copy(
                    isLoading = false,
                    errorMessage = error
                ) }
            }
        }
    }

    /**
     * Load more secrets (pagination)
     */
    fun loadMoreSecrets() {
        val provider = secretDataProvider ?: return
        val currentState = _state.value

        if (currentState.isLoadingMore || !currentState.hasMore || currentState.isLoading || currentState.searchQuery.isNotBlank()) {
            return
        }

        loadMoreJob?.cancel()
        _state.update { it.copy(isLoadingMore = true) }

        loadMoreJob = scope.launch {
            val result = provider.getUserSecretsWithSharingInfo(
                limit = currentState.pageSize,
                offset = currentState.currentOffset
            )

            result.onSuccess { paginatedResult ->
                val newSecrets = paginatedResult.data
                val allSecrets = _state.value.allSecrets + newSecrets
                _state.update { it.copy(
                    allSecrets = allSecrets,
                    secrets = if (it.searchQuery.isBlank()) allSecrets else it.secrets,
                    isLoadingMore = false,
                    currentOffset = it.currentOffset + newSecrets.size,
                    hasMore = paginatedResult.hasMore
                ) }
            }.onFailure { exception ->
                if (exception is CancellationException) return@onFailure

                _state.update { it.copy(
                    isLoadingMore = false,
                    errorMessage = exception.message ?: "Unknown error"
                ) }
            }
        }
    }

    /**
     * Search/filter secrets by website or username (client-side)
     */
    fun searchSecrets(query: String) {
        _state.update { it.copy(searchQuery = query) }

        if (query.isBlank()) {
            _state.update { it.copy(secrets = it.allSecrets) }
            return
        }

        // Client-side filter
        val filtered = _state.value.allSecrets.filter { secret ->
            secret.website.contains(query, ignoreCase = true) ||
                secret.username.contains(query, ignoreCase = true)
        }

        _state.update { it.copy(secrets = filtered) }
    }

    /**
     * Toggle metadata expansion for a secret
     */
    fun toggleMetadataExpanded(secretId: String) {
        _state.update { state ->
            val currentExpanded = state.expandedSecretIds
            state.copy(
                expandedSecretIds = if (currentExpanded.contains(secretId)) {
                    currentExpanded - secretId
                } else {
                    currentExpanded + secretId
                }
            )
        }
    }

    /**
     * Clear error message
     */
    fun clearError() {
        _state.update { it.copy(errorMessage = null) }
    }
}

/**
 * State for User Secret List
 */
data class UserSecretListState(
    val allSecrets: List<SecretEntryWithSharingData> = emptyList(),
    val secrets: List<SecretEntryWithSharingData> = emptyList(),
    val isLoading: Boolean = false,
    val isLoadingMore: Boolean = false,
    val errorMessage: String? = null,
    val searchQuery: String = "",
    val expandedSecretIds: Set<String> = emptySet(),
    val pageSize: Int = 50,
    val currentOffset: Int = 0,
    val hasMore: Boolean = true
)
