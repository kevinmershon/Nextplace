package com.nextplace.ui.locations

import androidx.compose.foundation.layout.*
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

// "Find my vibe" screen - locations + weather with filters
// Shows locations with current/forecasted weather, filterable by weather type and location type
@Composable
fun LocationsScreen(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier.fillMaxSize(),
        contentAlignment = Alignment.Center
    ) {
        Text(
            text = "Find my vibe",
            style = MaterialTheme.typography.headlineMedium
        )
    }
}
