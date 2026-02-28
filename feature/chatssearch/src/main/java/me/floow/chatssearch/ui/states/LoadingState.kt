package me.floow.chatssearch.ui.states

import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import me.floow.uikit.components.loading.FlowLoadingIndicator

@Composable
fun LoadingState(
	modifier: Modifier = Modifier
) {
	Box(
		modifier,
		contentAlignment = Alignment.Center
	) {
		FlowLoadingIndicator()
	}
}
