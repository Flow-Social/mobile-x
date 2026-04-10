package me.floow.login.di

import me.floow.shared.login.auth.AndroidAuthRepository
import me.floow.shared.login.createprofile.AndroidCreateProfileRepository
import me.floow.shared.login.uilogic.AuthRepository
import me.floow.shared.login.uilogic.createprofile.CreateProfileRepository
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.bind
import org.koin.dsl.module

val loginModule = module {
    factoryOf(::AndroidAuthRepository) bind AuthRepository::class
    factoryOf(::AndroidCreateProfileRepository) bind CreateProfileRepository::class
}
