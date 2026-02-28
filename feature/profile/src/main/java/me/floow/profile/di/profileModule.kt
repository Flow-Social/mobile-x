package me.floow.profile.di

import me.floow.profile.uilogic.addpost.AddPostViewModel
import me.floow.profile.uilogic.addpost.EditPostViewModel
import me.floow.profile.uilogic.addpost.AndroidLocalImageFileReader
import me.floow.profile.uilogic.addpost.LocalImageFileReader
import me.floow.profile.uilogic.profile.ProfileScreenViewModel
import me.floow.profile.uilogic.edit.EditProfileViewModel
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModelOf

val profileModule = module {
	single<LocalImageFileReader> { AndroidLocalImageFileReader(androidContext()) }

	viewModelOf(::ProfileScreenViewModel)

	viewModelOf(::EditProfileViewModel)

	viewModelOf(::AddPostViewModel)

	viewModelOf(::EditPostViewModel)
}
