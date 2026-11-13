package me.floow.shared.di

import me.floow.database.di.databaseModule
import org.koin.core.KoinApplication
import org.koin.core.context.startKoin
import org.koin.core.module.Module
import org.koin.dsl.KoinAppDeclaration
import org.koin.mp.KoinPlatformTools

fun commonModules(): List<Module> = listOf(
    sharedAppModule,
    sharedApiModule,
    sharedDataModule,
    databaseModule,
    sharedCommonModule,
)

fun initKoin(appDeclaration: KoinAppDeclaration = {}): KoinApplication? {
    if (KoinPlatformTools.defaultContext().getOrNull() != null) {
        return null
    }
    return startKoin {
        appDeclaration()
        modules(commonModules())
    }
}
