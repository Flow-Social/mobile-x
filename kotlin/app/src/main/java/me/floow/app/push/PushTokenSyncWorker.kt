package me.floow.app.push

import android.content.Context
import android.content.res.Configuration
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.google.android.gms.tasks.Tasks
import com.google.firebase.messaging.FirebaseMessaging
import me.floow.app.BuildConfig
import me.floow.domain.auth.AuthenticationManager
import me.floow.domain.data.UpdateDataResponse
import me.floow.domain.data.repos.PushTokensRepository
import me.floow.domain.utils.Logger
import org.koin.core.context.GlobalContext
import java.util.Locale
import java.util.TimeZone

class PushTokenSyncWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams) {

    override suspend fun doWork(): Result {
        val koin = GlobalContext.getKoinApplicationOrNull()?.koin ?: return Result.retry()
        val logger = koin.get<Logger>()
        val authenticationManager = koin.get<AuthenticationManager>()
        if (!authenticationManager.isSignedIn()) {
            return Result.success()
        }

        val token = runCatching {
            Tasks.await(FirebaseMessaging.getInstance().token)
        }.getOrElse { error ->
            logger.d("PushTokenSyncWorker.doWork", "Failed to resolve FCM token: ${error.message}")
            return Result.retry()
        }
		PushTokenStorage.save(applicationContext, token)

        val locale = resolveLocaleTag(applicationContext.resources.configuration)
        val timezone = runCatching { TimeZone.getDefault().id }.getOrNull()
        val registrar = koin.getOrNull<me.floow.shared.push.PushTokenRegistrar>()
        val success = if (registrar != null) {
            registrar.register(
                token = token,
                platform = "android",
                appVersion = BuildConfig.VERSION_NAME,
                locale = locale,
                timezone = timezone,
            )
        } else {
            val repository = koin.get<PushTokensRepository>()
            repository.registerToken(
                token = token,
                appVersion = BuildConfig.VERSION_NAME,
                locale = locale,
                timezone = timezone,
                platform = "android"
            ) is UpdateDataResponse.Success
        }
        return if (success) {
            Result.success()
        } else {
            logger.d("PushTokenSyncWorker.doWork", "Push token register failed")
            Result.retry()
        }
    }

    private fun resolveLocaleTag(configuration: Configuration): String? {
        return if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.N) {
            configuration.locales.get(0)?.toLanguageTag()
        } else {
            @Suppress("DEPRECATION")
            configuration.locale?.let(Locale::toLanguageTag)
        }
    }
}
