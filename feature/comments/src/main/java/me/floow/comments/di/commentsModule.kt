package me.floow.comments.di

import me.floow.comments.uilogic.CommentsViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val commentsModule = module {
	viewModelOf(::CommentsViewModel)
}
