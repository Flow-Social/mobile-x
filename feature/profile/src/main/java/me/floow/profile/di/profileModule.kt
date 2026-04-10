package me.floow.profile.di

import me.floow.profile.uilogic.addpost.AddPostViewModel
import me.floow.profile.uilogic.bump.ProfileBumpViewModel
import me.floow.profile.uilogic.bump.SendBumpHelloUseCase
import me.floow.shared.profile.image.AndroidLocalImageFileReader
import me.floow.shared.profile.image.LocalImageFileReader
import me.floow.shared.profile.uilogic.AndroidProfileRepository
import me.floow.shared.profile.uilogic.compose.AndroidPostComposerRepository
import me.floow.shared.profile.uilogic.compose.PostComposerRepository
import me.floow.shared.profile.uilogic.edit.AndroidProfileEditorRepository
import me.floow.shared.profile.uilogic.edit.ProfileEditorRepository
import org.koin.android.ext.koin.androidContext
import org.koin.dsl.module
import org.koin.core.module.dsl.viewModelOf

val profileModule = module {
	single<LocalImageFileReader> { AndroidLocalImageFileReader(androidContext()) }
	single<PostComposerRepository> { AndroidPostComposerRepository(get(), get(), get()) }
	single<ProfileEditorRepository> { AndroidProfileEditorRepository(get(), get()) }
	single<me.floow.shared.profile.uilogic.ProfileRepository> { AndroidProfileRepository(get(), get(), get(), get(), get(), get()) }
	single { SendBumpHelloUseCase(get(), get()) }

	viewModelOf(::ProfileBumpViewModel)

	viewModelOf(::AddPostViewModel)
}
