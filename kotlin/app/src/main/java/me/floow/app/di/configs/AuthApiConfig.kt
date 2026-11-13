package me.floow.app.di.configs

import me.floow.api.config.AppBuildConfig
import me.floow.api.util.ApiConfig

// NOTE: Prod gateway exposes only 80/443. Port 8080 is not public.
// Use TLS base to keep HTTP + WS transport consistent (https + wss).
val apiConfig = ApiConfig(apiUrl = AppBuildConfig.BASE_URL)
