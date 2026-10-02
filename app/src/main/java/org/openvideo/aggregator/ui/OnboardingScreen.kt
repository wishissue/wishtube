package org.openvideo.aggregator.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp

val availableInterests = listOf(
    "Gaming", "Technology", "Programming", "Music", "Movies & TV",
    "Science", "History", "Documentary", "Animation", "Art & Design",
    "Comedy", "News", "Sports", "Tutorials", "Linux & Open Source",
    "Horror", "Travel", "Nature", "Education", "DIY & Making"
)

@Composable
fun OnboardingScreen(app: AppViewModel, onComplete: () -> Unit) {
    val up = app.prefs
    var selected by remember { mutableStateOf(setOf("Technology", "Programming", "Science")) }

    fun finish() {
        up.setInterests(selected)
        up.setOnboardingCompleted(true)
        onComplete()
    }

    Box(
        modifier = Modifier.fillMaxSize().background(MaterialTheme.colorScheme.background).padding(24.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize().padding(bottom = 80.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(Modifier.height(32.dp))
            Text(
                "Welcome to WishTube",
                style = MaterialTheme.typography.headlineMedium,
                color = MaterialTheme.colorScheme.primary,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(8.dp))
            Text(
                "What are you interested in?",
                style = MaterialTheme.typography.titleLarge,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(6.dp))
            Text(
                "Choose a few topics so WishTube can personalize your recommendations locally on your device.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center
            )
            Spacer(Modifier.height(24.dp))

            LazyVerticalGrid(
                columns = GridCells.Adaptive(140.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.fillMaxWidth().weight(1f)
            ) {
                items(availableInterests) { category ->
                    val isSelected = category in selected
                    FilterChip(
                        selected = isSelected,
                        onClick = {
                            selected = if (isSelected) selected - category else selected + category
                        },
                        label = { Text(category) },
                        leadingIcon = if (isSelected) {
                            { Icon(Icons.Rounded.Check, null, modifier = Modifier.size(16.dp)) }
                        } else null,
                        shape = RoundedCornerShape(16.dp)
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().align(Alignment.BottomCenter).padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            TextButton(onClick = { finish() }) {
                Text("Skip", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { finish() },
                shape = RoundedCornerShape(20.dp)
            ) {
                Text("Continue (${selected.size})", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}
