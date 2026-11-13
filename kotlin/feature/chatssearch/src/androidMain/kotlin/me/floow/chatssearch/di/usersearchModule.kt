package me.floow.chatssearch.di

import me.floow.chatssearch.uilogic.SearchUsersScreenViewModel
import me.floow.chatssearch.uilogic.SearchUsersStateHolder
import org.koin.core.module.dsl.viewModelOf
import org.koin.dsl.module

val usersearchModule = module {
	viewModelOf(::SearchUsersScreenViewModel)
}
