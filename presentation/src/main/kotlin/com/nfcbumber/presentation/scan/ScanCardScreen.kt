package com.nfcbumber.presentation.scan

import androidx.compose.animation.core.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountBox
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.nfcbumber.data.nfc.NfcCardData
import com.nfcbumber.domain.util.toHexString

/**
 * Screen for scanning NFC cards.
 * Displays scanning status and allows user to name and save the card.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScanCardScreen(
    uiState: ScanCardUiState,
    onSaveCard: (NfcCardData, String, Int) -> Unit,
    onDismiss: () -> Unit,
    modifier: Modifier = Modifier
) {
    var cardName by remember { mutableStateOf("") }
    var selectedColor by remember { mutableStateOf(generateRandomColor()) }

    // Update suggested name when card is scanned
    LaunchedEffect(uiState) {
        if (uiState is ScanCardUiState.Scanned) {
            cardName = uiState.suggestedName
        }
    }

    // Navigate back when card is saved
    LaunchedEffect(uiState) {
        if (uiState is ScanCardUiState.Saved) {
            onDismiss()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Scan NFC Card") },
                navigationIcon = {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Close")
                    }
                }
            )
        }
    ) { paddingValues ->
        Box(
            modifier = modifier
                .fillMaxSize()
                .padding(paddingValues)
                .padding(16.dp),
            contentAlignment = Alignment.Center
        ) {
            when (uiState) {
                is ScanCardUiState.Idle -> {
                    ScanningPrompt()
                }
                is ScanCardUiState.Scanning -> {
                    ScanningInProgress()
                }
                is ScanCardUiState.Scanned -> {
                    CardScannedContent(
                        cardData = uiState.cardData,
                        cardName = cardName,
                        selectedColor = selectedColor,
                        onCardNameChange = { cardName = it },
                        onColorChange = { selectedColor = it },
                        onSave = {
                            onSaveCard(uiState.cardData, cardName, selectedColor)
                        }
                    )
                }
                is ScanCardUiState.Saving -> {
                    SavingCard()
                }
                is ScanCardUiState.Saved -> {
                    CardSaved()
                }
                is ScanCardUiState.Error -> {
                    ErrorContent(message = uiState.message, onDismiss = onDismiss)
                }
            }
        }
    }
}

@Composable
private fun ScanningPrompt() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AccountBox,
            contentDescription = null,
            modifier = Modifier.size(120.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Scan NFC Card",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
    }
}

@Composable
private fun ScanningInProgress() {
    val infiniteTransition = rememberInfiniteTransition(label = "scan")
    val scale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.3f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "scale"
    )

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.AccountBox,
            contentDescription = null,
            modifier = Modifier
                .size(120.dp)
                .scale(scale),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Reading card...",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center
        )
        CircularProgressIndicator()
    }
}

@Composable
private fun CardScannedContent(
    cardData: NfcCardData,
    cardName: String,
    selectedColor: Int,
    onCardNameChange: (String) -> Unit,
    onColorChange: (Int) -> Unit,
    onSave: () -> Unit
) {
    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // Success icon
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier
                .size(80.dp)
                .align(Alignment.CenterHorizontally),
            tint = MaterialTheme.colorScheme.primary
        )

        Text(
            text = "Card Scanned",
            style = MaterialTheme.typography.headlineSmall,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth()
        )

        // Card details
        CardDetailsSection(cardData)

        // Name input
        OutlinedTextField(
            value = cardName,
            onValueChange = onCardNameChange,
            label = { Text("Card Name") },
            modifier = Modifier.fillMaxWidth(),
            keyboardOptions = KeyboardOptions(
                capitalization = KeyboardCapitalization.Words
            ),
            singleLine = true
        )

        // Color picker
        ColorPickerRow(
            selectedColor = selectedColor,
            onColorSelect = onColorChange
        )

        // Save button
        Button(
            onClick = onSave,
            modifier = Modifier.fillMaxWidth(),
            enabled = cardName.isNotBlank()
        ) {
            Text("Save Card")
        }
    }
}

@Composable
private fun CardDetailsSection(cardData: NfcCardData) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text(
                text = "Card Details",
                style = MaterialTheme.typography.titleMedium
            )
            Divider()
            DetailRow("Type", cardData.cardType.name.replace('_', ' '))
            DetailRow("UID", cardData.uid.toHexString())
            cardData.ats?.let {
                DetailRow("ATS", it.toHexString())
            }
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium
        )
    }
}

@Composable
private fun ColorPickerRow(
    selectedColor: Int,
    onColorSelect: (Int) -> Unit
) {
    val colors = remember {
        listOf(
            0xFFEF5350.toInt(), // Red
            0xFFEC407A.toInt(), // Pink
            0xFFAB47BC.toInt(), // Purple
            0xFF5C6BC0.toInt(), // Indigo
            0xFF42A5F5.toInt(), // Blue
            0xFF26A69A.toInt(), // Teal
            0xFF66BB6A.toInt(), // Green
            0xFFFFEE58.toInt(), // Yellow
            0xFFFF7043.toInt(), // Deep Orange
        )
    }

    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(
            text = "Card Color",
            style = MaterialTheme.typography.bodyMedium
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            colors.forEach { color ->
                ColorOption(
                    color = Color(color),
                    selected = color == selectedColor,
                    onClick = { onColorSelect(color) }
                )
            }
        }
    }
}

@Composable
private fun ColorOption(
    color: Color,
    selected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        onClick = onClick,
        modifier = Modifier.size(40.dp),
        shape = MaterialTheme.shapes.small,
        color = color,
        border = if (selected) 
            androidx.compose.foundation.BorderStroke(3.dp, MaterialTheme.colorScheme.primary)
        else null
    ) {}
}

@Composable
private fun SavingCard() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        CircularProgressIndicator()
        Text(
            text = "Saving card...",
            style = MaterialTheme.typography.bodyLarge
        )
    }
}

@Composable
private fun CardSaved() {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Icon(
            imageVector = Icons.Default.CheckCircle,
            contentDescription = null,
            modifier = Modifier.size(80.dp),
            tint = MaterialTheme.colorScheme.primary
        )
        Text(
            text = "Card Saved!",
            style = MaterialTheme.typography.headlineSmall
        )
    }
}

@Composable
private fun ErrorContent(
    message: String,
    onDismiss: () -> Unit
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text(
            text = "Error",
            style = MaterialTheme.typography.headlineSmall,
            color = MaterialTheme.colorScheme.error
        )
        Text(
            text = message,
            style = MaterialTheme.typography.bodyMedium,
            textAlign = TextAlign.Center
        )
        Button(onClick = onDismiss) {
            Text("Close")
        }
    }
}

private fun generateRandomColor(): Int {
    val colors = listOf(
        0xFFEF5350.toInt(),
        0xFFEC407A.toInt(),
        0xFFAB47BC.toInt(),
        0xFF5C6BC0.toInt(),
        0xFF42A5F5.toInt(),
        0xFF26A69A.toInt(),
        0xFF66BB6A.toInt(),
        0xFFFFEE58.toInt(),
        0xFFFF7043.toInt(),
    )
    return colors.random()
}
