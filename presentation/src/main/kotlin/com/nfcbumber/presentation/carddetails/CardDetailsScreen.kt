package com.nfcbumber.presentation.carddetails

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.nfcbumber.domain.model.Card
import com.nfcbumber.domain.model.CardCompatibility
import com.nfcbumber.domain.model.getCompatibility
import com.nfcbumber.domain.model.getDescription
import com.nfcbumber.domain.model.getMessage
import com.nfcbumber.domain.util.toHexString

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CardDetailsScreen(
    card: Card,
    onBack: () -> Unit
) {
    val compatibility = card.getCompatibility()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Card Info") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.Default.ArrowBack, "Back")
                    }
                }
            )
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Card Name
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = Color(card.color)
                )
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(20.dp)
                ) {
                    Text(
                        text = card.name,
                        style = MaterialTheme.typography.headlineMedium,
                        color = Color.White,
                        fontWeight = FontWeight.Bold
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        text = card.cardType.name.replace('_', ' '),
                        style = MaterialTheme.typography.bodyLarge,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                }
            }

            // Safety Information
            SafetyInfoCard()

            // Compatibility Status
            CompatibilityCard(compatibility)

            // Technical Details
            TechnicalDetailsCard(card)

            // Intercom Specific Info
            if (card.cardType.name.contains("MIFARE")) {
                IntercomInfoCard()
            }
        }
    }
}

@Composable
fun SafetyInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🛡️",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Security",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            Text(
                text = "Uses official Android HCE API. All data encrypted.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun CompatibilityCard(compatibility: CardCompatibility) {
    val (backgroundColor, iconColor) = when (compatibility) {
        CardCompatibility.FULL_HCE ->
            MaterialTheme.colorScheme.primaryContainer to MaterialTheme.colorScheme.primary
        CardCompatibility.UID_ONLY ->
            MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.tertiary
        CardCompatibility.NOT_SUPPORTED ->
            MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.error
        CardCompatibility.UNKNOWN ->
            MaterialTheme.colorScheme.surfaceVariant to MaterialTheme.colorScheme.onSurfaceVariant
    }

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = backgroundColor
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Icon(
                    imageVector = if (compatibility == CardCompatibility.NOT_SUPPORTED)
                        Icons.Default.Warning else Icons.Default.Info,
                    contentDescription = null,
                    tint = iconColor,
                    modifier = Modifier.size(32.dp)
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Compatibility",
                        style = MaterialTheme.typography.labelLarge,
                        color = iconColor
                    )
                    Text(
                        text = compatibility.getMessage(),
                        style = MaterialTheme.typography.bodyLarge,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}

@Composable
fun TechnicalDetailsCard(card: Card) {
    Card(
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(
                text = "Technical Data",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold
            )

            DetailRow("UID", card.uid.toHexString())

            card.ats?.let { ats ->
                if (ats.isNotEmpty()) {
                    DetailRow("ATS", ats.toHexString())
                }
            }

            card.historicalBytes?.let { historicalBytes ->
                if (historicalBytes.isNotEmpty()) {
                    DetailRow("Historical Bytes", historicalBytes.toHexString())
                }
            }

            DetailRow("Type", card.cardType.name.replace('_', ' '))
            DetailRow("UID Length", "${card.uid.size} bytes")
        }
    }
}

@Composable
fun DetailRow(label: String, value: String) {
    Column {
        Text(
            text = label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Medium
        )
    }
}

@Composable
fun IntercomInfoCard() {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer
        )
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Text(
                    text = "🏠",
                    style = MaterialTheme.typography.headlineMedium
                )
                Text(
                    text = "Intercom Support",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
            }

            Text(
                text = "MIFARE card detected. HCE emulation active.",
                style = MaterialTheme.typography.bodyMedium
            )
        }
    }
}

