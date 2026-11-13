package me.floow.shared.di

import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.network.NetworkMonitor
import me.floow.domain.utils.Logger
import me.floow.shared.auth.CommonAuthenticationManager
import me.floow.shared.auth.OAuthLauncher
import me.floow.shared.auth.TokenStorage
import me.floow.shared.auth.createOAuthLauncher
import me.floow.shared.auth.createPlatformGoogleOAuth
import me.floow.shared.auth.createTokenStorage
import me.floow.shared.login.auth.CommonAuthRepository
import me.floow.shared.login.uilogic.AuthRepository
import me.floow.shared.network.createNetworkMonitor
import me.floow.shared.media.CommonPostMediaTransferStore
import me.floow.uikit.components.media.transfer.PostMediaTransferStore
import org.koin.dsl.module

class CommonLogger : Logger {
    override fun d(tag: String?, message: String) {
        println("[${tag ?: "Flow"}] $message")
    }
}

val sharedAppModule = module {
    single<Logger> { CommonLogger() }
    single<NetworkMonitor> { createNetworkMonitor() }
    single<PostMediaTransferStore> { CommonPostMediaTransferStore() }
    single<AuthenticationManager> { CommonAuthenticationManager(get(), get(), createPlatformGoogleOAuth(), get()) }
    single<AuthRepository> { CommonAuthRepository(get()) }
}
