package me.floow.shared.profile.ui.segments.summary

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import flow.feature.shared.generated.resources.Res
import flow.feature.shared.generated.resources.no_profile_description
import me.floow.uikit.theme.LocalTypography
import org.jetbrains.compose.resources.stringResource

@Composable
fun AboutMeProfileSummaryPage(description: String?, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier.padding(start = 24.dp, end = 24.dp),
        verticalArrangement = Arrangement.Center,
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(
            text = "обо мне",
            style = LocalTypography.current.titleLarge,
            color = Color.White,
        )

        Spacer(Modifier.height(9.dp))

        Text(
            text = description ?: stringResource(Res.string.no_profile_description),
            textAlign = TextAlign.Center,
            style = LocalTypography.current.bodyMedium,
            color = Color.White,
        )
    }
}
