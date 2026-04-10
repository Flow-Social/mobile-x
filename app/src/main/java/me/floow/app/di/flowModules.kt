package me.floow.app.di

import me.floow.app.BuildConfig
import me.floow.chats.di.chatsModule
import me.floow.chatssearch.di.usersearchModule
import me.floow.comments.di.commentsModule
import me.floow.login.di.loginModule
import me.floow.profile.di.profileModule
import me.floow.shared.di.sharedCommonModule
import org.koin.core.module.Module

fun flowModules(): List<Module> {
    return if (BuildConfig.USE_MOCK_DATA) {
        listOf(
            appModule,
            apiModule,
            mockAuthModule,
            databaseModule,
            mockDataModule,
            domainModule,
            mockModule,
            sharedCommonModule,
            loginModule,
            profileModule,
            usersearchModule,
            chatsModule,
            commentsModule,
        )
    } else {
        listOf(
            appModule,
            apiModule,
            authModule,
            databaseModule,
            dataModule,
            domainModule,
            mockModule,
            sharedCommonModule,
            loginModule,
            profileModule,
            usersearchModule,
            chatsModule,
            commentsModule,
        )
    }
}
