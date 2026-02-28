package me.floow.feed.ui.components.avatar

sealed interface AvatarUiModel {
	data class Remote(
		val url: String,
		val fallbackName: String = "?"
	) : AvatarUiModel

	data class Initial(
		val name: String
	) : AvatarUiModel
}

internal fun List<String>.toAvatarUiModels(limit: Int = 3): List<AvatarUiModel> {
	return this
		.asSequence()
		.map { it.trim() }
		.filter { it.isNotEmpty() }
		.take(limit)
		.map { raw ->
			if (raw.startsWith("http://", ignoreCase = true) || raw.startsWith("https://", ignoreCase = true)) {
				AvatarUiModel.Remote(url = raw)
			} else {
				AvatarUiModel.Initial(name = raw)
			}
		}
		.toList()
}
