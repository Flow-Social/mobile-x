package me.floow.feed.di

import me.floow.feed.uilogic.FeedViewModel
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val feedModule = module {
	viewModelOf(::FeedViewModel)
}
