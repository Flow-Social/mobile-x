package me.floow.shared.feed.di

import me.floow.shared.feed.uilogic.SharedFeedStateHolder
import org.koin.dsl.module

val feedModule = module {
    factory {
        SharedFeedStateHolder(
            logger = get(),
            feedRepository = get(),
            categoryCatalogRepository = get(),
            postsRepository = get(),
            userProfileRepository = get(),
            profileLocalStore = get(),
            postsLocalStore = get(),
            feedStackSnapshotLocalStore = get(),
            authenticationManager = get(),
            feedSyncLocalStore = get(),
            networkMonitor = getOrNull(),
        )
    }
}
