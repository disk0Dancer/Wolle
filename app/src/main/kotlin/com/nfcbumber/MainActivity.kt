package com.nfcbumber

import android.app.PendingIntent
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.nfcbumber.data.nfc.NfcEmulationManager
import com.nfcbumber.presentation.cardlist.CardListScreen
import com.nfcbumber.presentation.cardlist.CardListViewModel
import com.nfcbumber.presentation.scan.ScanCardScreen
import com.nfcbumber.presentation.scan.ScanCardViewModel
import com.nfcbumber.presentation.settings.SettingsScreen
import com.nfcbumber.presentation.settings.SettingsViewModel
import com.nfcbumber.presentation.theme.WolleTheme
import dagger.hilt.android.AndroidEntryPoint
import javax.inject.Inject

/**
 * Main activity for NFC Bumber application.
 * Entry point for the app UI and NFC handling.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    
    private var nfcAdapter: NfcAdapter? = null
    private var pendingIntent: PendingIntent? = null
    private var scanViewModel: ScanCardViewModel? = null

    @Inject
    lateinit var emulationManager: NfcEmulationManager

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        // Initialize NFC adapter
        nfcAdapter = NfcAdapter.getDefaultAdapter(this)
        
        // Check if device supports NFC
        if (nfcAdapter == null) {
            Toast.makeText(this, "NFC is not available on this device", Toast.LENGTH_LONG).show()
        } else if (nfcAdapter?.isEnabled == false) {
            Toast.makeText(this, "NFC is disabled. Please enable it in settings", Toast.LENGTH_LONG).show()
        }
        
        // Create pending intent for NFC foreground dispatch
        pendingIntent = PendingIntent.getActivity(
            this, 0,
            Intent(this, javaClass).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_MUTABLE
        )
        
        setContent {
            MainApp(
                onScanViewModelCreated = { scanViewModel = it }
            )
        }
    }

    override fun onResume() {
        super.onResume()
        // Enable foreground dispatch for NFC
        nfcAdapter?.enableForegroundDispatch(this, pendingIntent, null, null)

        // Activate NFC emulation if a card was previously selected
        // Keep emulation active even when app goes to background
        val selectedCardId = emulationManager.getSelectedCardId()
        if (selectedCardId != -1L) {
            emulationManager.activateEmulation(selectedCardId)
        }
    }

    override fun onPause() {
        super.onPause()
        // Disable foreground dispatch
        nfcAdapter?.disableForegroundDispatch(this)
        // Keep NFC emulation active even when app goes to background
        // This allows the phone to act as an access card/key fob
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        
        // Handle NFC tag detection
        if (NfcAdapter.ACTION_TAG_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_TECH_DISCOVERED == intent.action ||
            NfcAdapter.ACTION_NDEF_DISCOVERED == intent.action) {
            
            val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG)
            tag?.let {
                // Pass tag to scan view model if on scan screen
                scanViewModel?.onTagDetected(it)
            }
        }
    }
}

@Composable
fun MainApp(
    onScanViewModelCreated: (ScanCardViewModel?) -> Unit
) {
    val settingsViewModel: SettingsViewModel = hiltViewModel()
    val themeMode by settingsViewModel.themeMode.collectAsState()
    val dynamicColor by settingsViewModel.dynamicColor.collectAsState()

    WolleTheme(
        themeMode = themeMode,
        dynamicColor = dynamicColor
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.background
        ) {
            MainNavigation(
                settingsViewModel = settingsViewModel,
                onScanViewModelCreated = onScanViewModelCreated
            )
        }
    }
}

@Composable
fun MainNavigation(
    settingsViewModel: SettingsViewModel,
    onScanViewModelCreated: (ScanCardViewModel?) -> Unit
) {
    val navController = rememberNavController()

    NavHost(navController = navController, startDestination = "cardList") {
        composable("cardList") {
            val viewModel: CardListViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()
            val selectedCardId by viewModel.selectedCardId.collectAsState()
            val searchQuery by viewModel.searchQuery.collectAsState()
            val backupState by viewModel.backupState.collectAsState()

            CardListScreen(
                uiState = uiState,
                selectedCardId = selectedCardId,
                searchQuery = searchQuery,
                backupState = backupState,
                onCardSelect = viewModel::selectCard,
                onAddCard = { navController.navigate("scan") },
                onDeleteCard = viewModel::deleteCard,
                onRefresh = viewModel::refresh,
                onSearchQueryChange = viewModel::updateSearchQuery,
                onResetBackupState = viewModel::resetBackupState,
                onNavigateToSettings = { navController.navigate("settings") },
                onCardDetails = { cardId -> navController.navigate("cardDetails/$cardId") }
            )
        }

        composable("scan") {
            val viewModel: ScanCardViewModel = hiltViewModel()
            val uiState by viewModel.uiState.collectAsState()

            // Notify activity about view model
            LaunchedEffect(viewModel) {
                onScanViewModelCreated(viewModel)
            }

            // Clear view model reference when leaving screen
            DisposableEffect(Unit) {
                onDispose {
                    onScanViewModelCreated(null)
                    viewModel.resetState()
                }
            }

            ScanCardScreen(
                uiState = uiState,
                onSaveCard = viewModel::saveCard,
                onDismiss = {
                    viewModel.resetState()
                    navController.popBackStack()
                }
            )
        }

        composable("settings") {
            val themeMode by settingsViewModel.themeMode.collectAsState()
            val dynamicColor by settingsViewModel.dynamicColor.collectAsState()

            SettingsScreen(
                currentTheme = themeMode,
                dynamicColor = dynamicColor,
                onThemeChange = settingsViewModel::setThemeMode,
                onDynamicColorChange = settingsViewModel::setDynamicColor,
                onBack = { navController.popBackStack() }
            )
        }

        composable("cardDetails/{cardId}") { backStackEntry ->
            val cardId = backStackEntry.arguments?.getString("cardId")?.toLongOrNull()

            if (cardId != null) {
                val viewModel: CardListViewModel = hiltViewModel()
                val uiState by viewModel.uiState.collectAsState()

                when (val state = uiState) {
                    is com.nfcbumber.presentation.cardlist.CardListUiState.Success -> {
                        val card = state.cards.find { it.id == cardId }
                        if (card != null) {
                            com.nfcbumber.presentation.carddetails.CardDetailsScreen(
                                card = card,
                                onBack = { navController.popBackStack() }
                            )
                        } else {
                            // Card not found, go back
                            LaunchedEffect(Unit) {
                                navController.popBackStack()
                            }
                        }
                    }
                    else -> {
                        // Loading or error, show loading indicator
                        Box(
                            modifier = Modifier.fillMaxSize(),
                            contentAlignment = Alignment.Center
                        ) {
                            CircularProgressIndicator()
                        }
                    }
                }
            } else {
                // Invalid card ID, go back
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            }
        }
    }
}
