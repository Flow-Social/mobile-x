package me.floow.app.di

import org.koin.core.module.Module

interface FlowModuleProvider {
    fun modules(): List<Module>
}
