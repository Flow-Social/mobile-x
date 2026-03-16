package me.floow.app.di.configs

import me.floow.api.util.ApiConfig

// NOTE: Prod gateway exposes only 80/443. Port 8080 is not public.
// Use TLS base to keep HTTP + WS transport consistent (https + wss).
val apiConfig = ApiConfig(apiUrl = "https://45.66.228.158.nip.io/api/v1")
