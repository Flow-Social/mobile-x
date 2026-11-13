package me.floow.comments.di

import me.floow.comments.uilogic.CommentsStateHolder
import me.floow.comments.uilogic.CommentsViewModel
import org.koin.core.module.dsl.factoryOf
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val commentsModule = module {
	factory {
		CommentsStateHolder(
			commentsRepository = get(),
			commentsReadCursorStore = get(),
			authenticationManager = get(),
			logger = get()
		)
	}
	viewModelOf(::CommentsViewModel)
}
