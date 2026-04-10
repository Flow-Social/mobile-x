package me.floow.chatssearch.di

import org.koin.core.module.dsl.viewModelOf
import me.floow.chatssearch.uilogic.SearchUsersScreenViewModel
import org.koin.dsl.module

val usersearchModule = module {
	viewModelOf(::SearchUsersScreenViewModel)
}
