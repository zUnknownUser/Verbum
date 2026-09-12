package com.nexussoft.verbum.designsystem

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.tooling.preview.Preview
import com.nexussoft.verbum.designsystem.tokens.Elevation
import com.nexussoft.verbum.designsystem.tokens.Radius
import com.nexussoft.verbum.designsystem.tokens.Spacing
import com.nexussoft.verbum.designsystem.tokens.VerbumTypography

// Visual reference for the token set. Open the preview to review typography,
// surfaces and accent in light/dark and at any font scale. Mirrors iOS TokenGallery.

@Preview(showBackground = true, name = "Tokens")
@Composable
private fun TokenGalleryPreview() {
    VerbumTheme {
        Column(
            Modifier.padding(Spacing.screenMargin),
            verticalArrangement = Arrangement.spacedBy(Spacing.xl),
        ) {
            Column(verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                Text("David", style = VerbumTypography.editorialTitle)
                Text(
                    "King of Israel",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Column(verticalArrangement = Arrangement.spacedBy(Spacing.md)) {
                Text("1 Samuel 17", style = VerbumTypography.scriptureHeading)
                Row(
                    horizontalArrangement = Arrangement.spacedBy(Spacing.sm),
                    verticalAlignment = Alignment.Top,
                ) {
                    Text(
                        "45",
                        style = VerbumTypography.verseNumber,
                        color = MaterialTheme.colorScheme.outline,
                    )
                    Text(
                        "Then David said to the Philistine, “You come to me with a sword and with a spear and with a javelin, but I come to you in the name of the LORD of hosts.”",
                        style = VerbumTypography.scripture,
                    )
                }
            }

            Card(
                Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(Radius.lg),
                elevation = CardDefaults.cardElevation(defaultElevation = Elevation.Card.dp),
            ) {
                Column(Modifier.padding(Spacing.lg), verticalArrangement = Arrangement.spacedBy(Spacing.sm)) {
                    Text("Surface", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Surface container, card elevation.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Button(onClick = {}) { Text("Explore connections") }
        }
    }
}
