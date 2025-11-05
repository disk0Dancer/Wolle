package com.nfcbumber.presentation.cardlist

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.nfcbumber.data.nfc.NfcEmulationManager
import com.nfcbumber.data.security.SecureStorage
import com.nfcbumber.domain.model.BackupResult
import com.nfcbumber.domain.model.Card
import com.nfcbumber.domain.model.RestoreResult
import com.nfcbumber.domain.usecase.DeleteCardUseCase
import com.nfcbumber.domain.usecase.ExportCardsUseCase
import com.nfcbumber.domain.usecase.GetAllCardsUseCase
import com.nfcbumber.domain.usecase.ImportCardsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * ViewModel for the card list screen.
 * Manages the list of saved cards and user interactions.
 */
@HiltViewModel
class CardListViewModel @Inject constructor(
    private val getAllCardsUseCase: GetAllCardsUseCase,
    private val deleteCardUseCase: DeleteCardUseCase,
    private val exportCardsUseCase: ExportCardsUseCase,
    private val importCardsUseCase: ImportCardsUseCase,
    private val secureStorage: SecureStorage,
    private val emulationManager: NfcEmulationManager
) : ViewModel() {

    companion object {
        private const val KEY_SELECTED_CARD_ID = "selected_card_id"
        // Sentinel value for no card selection
        private const val NO_CARD_SELECTED = -1L
    }

    private val _uiState = MutableStateFlow<CardListUiState>(CardListUiState.Loading)
    val uiState: StateFlow<CardListUiState> = _uiState.asStateFlow()

    private val _selectedCardId = MutableStateFlow<Long?>(null)
    val selectedCardId: StateFlow<Long?> = _selectedCardId.asStateFlow()

    private val _searchQuery = MutableStateFlow("")
    val searchQuery: StateFlow<String> = _searchQuery.asStateFlow()

    private val _backupState = MutableStateFlow<BackupState>(BackupState.Idle)
    val backupState: StateFlow<BackupState> = _backupState.asStateFlow()

    private var allCards: List<Card> = emptyList()

    init {
        // Load previously selected card ID
        val savedCardId = secureStorage.getLong(KEY_SELECTED_CARD_ID, NO_CARD_SELECTED)
        if (savedCardId != NO_CARD_SELECTED) {
            _selectedCardId.value = savedCardId
        }
        loadCards()
    }

    private fun loadCards() {
        viewModelScope.launch {
            getAllCardsUseCase()
                .catch { error ->
                    _uiState.value = CardListUiState.Error(
                        error.message ?: "Failed to load cards"
                    )
                }
                .collect { cards ->
                    allCards = cards
                    applySearchFilter()
                    
                    // Auto-select first card if none selected or selected card doesn't exist
                    val currentSelectedId = _selectedCardId.value
                    val cardExists = cards.any { it.id == currentSelectedId }
                    
                    if (cards.isNotEmpty() && (currentSelectedId == null || !cardExists)) {
                        val firstCardId = cards.first().id
                        _selectedCardId.value = firstCardId
                        secureStorage.putLong(KEY_SELECTED_CARD_ID, firstCardId)
                        // Activate emulation for auto-selected card
                        emulationManager.activateEmulation(firstCardId)
                    } else if (cards.isNotEmpty() && currentSelectedId != null && cardExists) {
                        // Re-activate emulation for the currently selected card
                        emulationManager.activateEmulation(currentSelectedId)
                    }
                }
        }
    }

    private fun applySearchFilter() {
        val query = _searchQuery.value.lowercase()
        val filteredCards = if (query.isEmpty()) {
            allCards
        } else {
            allCards.filter { card ->
                card.name.lowercase().contains(query) ||
                card.cardType.name.lowercase().contains(query)
            }
        }

        _uiState.value = if (filteredCards.isEmpty() && allCards.isNotEmpty()) {
            CardListUiState.Empty
        } else if (filteredCards.isEmpty()) {
            CardListUiState.Empty
        } else {
            CardListUiState.Success(filteredCards)
        }
    }

    fun selectCard(cardId: Long) {
        _selectedCardId.value = cardId
        // Save to secure storage for HCE service
        secureStorage.putLong(KEY_SELECTED_CARD_ID, cardId)
        // Activate NFC emulation for this card
        emulationManager.activateEmulation(cardId)
    }

    fun deleteCard(cardId: Long) {
        viewModelScope.launch {
            deleteCardUseCase(cardId).fold(
                onSuccess = {
                    // If deleted card was selected, clear selection and deactivate emulation
                    if (_selectedCardId.value == cardId) {
                        _selectedCardId.value = null
                        secureStorage.remove(KEY_SELECTED_CARD_ID)
                        emulationManager.deactivateEmulation()
                    }
                },
                onFailure = { error ->
                    // Handle error - could show a snackbar
                    _uiState.value = CardListUiState.Error(
                        error.message ?: "Failed to delete card"
                    )
                }
            )
        }
    }

    fun updateSearchQuery(query: String) {
        _searchQuery.value = query
        applySearchFilter()
    }

    fun exportCards(filePath: String) {
        viewModelScope.launch {
            _backupState.value = BackupState.Exporting
            when (val result = exportCardsUseCase(filePath)) {
                is BackupResult.Success -> {
                    _backupState.value = BackupState.ExportSuccess(result.cardsCount)
                }
                is BackupResult.Error -> {
                    _backupState.value = BackupState.Error(result.message)
                }
            }
        }
    }

    fun importCards(filePath: String) {
        viewModelScope.launch {
            _backupState.value = BackupState.Importing
            when (val result = importCardsUseCase(filePath)) {
                is RestoreResult.Success -> {
                    _backupState.value = BackupState.ImportSuccess(result.cardsImported, result.cardsSkipped)
                    loadCards() // Reload cards after import
                }
                is RestoreResult.Error -> {
                    _backupState.value = BackupState.Error(result.message)
                }
            }
        }
    }

    fun resetBackupState() {
        _backupState.value = BackupState.Idle
    }

    fun refresh() {
        loadCards()
    }
}

/**
 * UI state for the card list screen.
 */
sealed interface CardListUiState {
    /**
     * Loading cards from database.
     */
    object Loading : CardListUiState

    /**
     * Successfully loaded cards.
     */
    data class Success(val cards: List<Card>) : CardListUiState

    /**
     * No cards in database.
     */
    object Empty : CardListUiState

    /**
     * Error loading cards.
     */
    data class Error(val message: String) : CardListUiState
}

/**
 * State for backup/restore operations.
 */
sealed interface BackupState {
    object Idle : BackupState
    object Exporting : BackupState
    object Importing : BackupState
    data class ExportSuccess(val cardsCount: Int) : BackupState
    data class ImportSuccess(val imported: Int, val skipped: Int) : BackupState
    data class Error(val message: String) : BackupState
}
