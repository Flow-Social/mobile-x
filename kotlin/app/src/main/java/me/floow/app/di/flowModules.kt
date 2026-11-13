package me.floow.app.di

import me.floow.app.BuildConfig
import me.floow.chatssearch.di.usersearchModule
import me.floow.comments.di.commentsModule
import me.floow.shared.di.sharedCommonModule
import me.floow.shared.di.sharedModule
import org.koin.core.module.Module

private fun productionModules(): List<Module> {
    return listOf(
        apiModule,
        authModule,
        databaseModule,
        dataModule,
        domainModule,
        sharedCommonModule,
        sharedModule,
        appModule,
        usersearchModule,
        commentsModule,
    )
}

private fun loadMockModuleProvider(): FlowModuleProvider? {
    return runCatching {
        Class.forName("me.floow.app.di.MockFlowModuleProvider")
            .getDeclaredConstructor()
            .newInstance() as FlowModuleProvider
    }.getOrNull()
}

fun flowModules(): List<Module> {
    return if (BuildConfig.USE_MOCK_DATA) {
        loadMockModuleProvider()?.modules() ?: productionModules()
    } else {
        productionModules()
    }
}
