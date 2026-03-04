package me.floow.profile.ui.profile.bump

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.Build
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withTimeoutOrNull
import me.floow.profile.R
import me.floow.profile.uilogic.bump.ProfileBumpViewModel
import java.util.UUID
import kotlin.coroutines.resume

private data class LastKnownLocation(
    val lat: Double,
    val lon: Double,
    val accuracyMeters: Float,
)

private const val FRESH_LOCATION_TIMEOUT_MS = 4200L
private const val START_PRELOAD_MAX_AGE_MS = 30 * 60_000L
private const val IMPACT_MAX_AGE_MS = 10 * 60_000L
private const val MAX_ACCEPTABLE_ACCURACY_METERS = 400f
private val UnknownLocation = LastKnownLocation(
    lat = 0.0,
    lon = 0.0,
    accuracyMeters = 9999f,
)

class ProfileBumpCoordinator(
    private val context: Context,
    private val bumpViewModel: ProfileBumpViewModel,
) {
    private var cachedLocation: LastKnownLocation? = null

    fun hasLocationPermission(): Boolean = hasLocationPermission(context)

    fun onLocationPermissionDenied() {
        bumpViewModel.showBlockingError(context.getString(R.string.bump_status_location_permission))
    }

    suspend fun startSession(ttlSeconds: Int = 10) {
        // Start session immediately. Location preloading should not block UX.
        bumpViewModel.startSession(ttlSeconds = ttlSeconds)

        val preloadLocation = readBumpLocation(
            context = context,
            maxAgeMs = START_PRELOAD_MAX_AGE_MS,
            requestFreshFix = true,
        )
        if (preloadLocation?.isUsableForBump() == true) {
            cachedLocation = preloadLocation
        }
    }

    suspend fun submitImpact(peak: Float) {
        // On impact, send fast. Use cached/recent coordinates and avoid waiting for fresh GPS fix.
        val resolvedLocation = cachedLocation?.takeIf { it.isUsableForBump() }
            ?: readBumpLocation(
                context = context,
                maxAgeMs = IMPACT_MAX_AGE_MS,
                requestFreshFix = false,
            )?.takeIf { it.isUsableForBump() }
            ?: UnknownLocation

        if (resolvedLocation.isUsableForBump()) {
            cachedLocation = resolvedLocation
        }
        bumpViewModel.submitImpact(
            lat = resolvedLocation.lat,
            lon = resolvedLocation.lon,
            accuracyMeters = resolvedLocation.accuracyMeters,
            accelPeak = peak,
            deviceNonce = UUID.randomUUID().toString(),
        )
    }
}

private suspend fun readBumpLocation(
    context: Context,
    maxAgeMs: Long,
    requestFreshFix: Boolean,
): LastKnownLocation? {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    if (!hasFine && !hasCoarse) return null

    val manager = context.getSystemService(Context.LOCATION_SERVICE) as? LocationManager ?: return null

    if (requestFreshFix) {
        val fresh = readFreshLocation(context, manager, hasFine, hasCoarse, maxAgeMs)
        if (fresh != null) {
            return fresh.toBumpLocation()
        }
    }

    val recentLastKnown = readRecentLastKnownLocation(manager, hasFine, hasCoarse, maxAgeMs)
    if (recentLastKnown != null) {
        return recentLastKnown.toBumpLocation()
    }

    val fallbackLastKnown = readLastKnownLocation(manager, maxAgeMs)
    return fallbackLastKnown?.toBumpLocation()
}

private suspend fun readFreshLocation(
    context: Context,
    manager: LocationManager,
    hasFine: Boolean,
    hasCoarse: Boolean,
    maxAgeMs: Long,
): Location? {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        return readRecentLastKnownLocation(manager, hasFine, hasCoarse, maxAgeMs)
    }

    val preferredProviders = buildList {
        if (hasFine) add(LocationManager.GPS_PROVIDER)
        if (hasFine || hasCoarse) add(LocationManager.NETWORK_PROVIDER)
        if (hasFine || hasCoarse) add(LocationManager.PASSIVE_PROVIDER)
    }
    if (preferredProviders.isEmpty()) return null

    val enabledProviders = preferredProviders
        .distinct()
        .filter { provider ->
            runCatching { manager.isProviderEnabled(provider) }.getOrDefault(false)
        }

    for (provider in enabledProviders) {
        val location = withTimeoutOrNull(FRESH_LOCATION_TIMEOUT_MS) {
            awaitCurrentLocation(context, manager, provider)
        }
        if (location != null && isLocationFreshEnough(location, maxAgeMs)) {
            return location
        }
    }

    return null
}

private fun readRecentLastKnownLocation(
    manager: LocationManager,
    hasFine: Boolean,
    hasCoarse: Boolean,
    maxAgeMs: Long,
): Location? {
    val candidateProviders = buildList {
        if (hasFine) add(LocationManager.GPS_PROVIDER)
        if (hasFine || hasCoarse) add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
        add("fused")
        addAll(runCatching { manager.getProviders(false) }.getOrElse { emptyList() })
    }

    val now = System.currentTimeMillis()
    val candidates = candidateProviders
        .distinct()
        .mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .filter { isValidCoordinates(it.latitude, it.longitude) }
        .filter { location ->
            val ageMs = now - location.time
            ageMs in 0..maxAgeMs
        }

    return candidates.minWithOrNull(
        compareBy<Location>({ now - it.time }, { it.accuracy.coerceAtLeast(1f) }),
    )
}

private suspend fun awaitCurrentLocation(
    context: Context,
    manager: LocationManager,
    provider: String,
): Location? = suspendCancellableCoroutine { continuation ->
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
        continuation.resume(null)
        return@suspendCancellableCoroutine
    }

    val cancellationSignal = CancellationSignal()
    continuation.invokeOnCancellation { cancellationSignal.cancel() }

    runCatching {
        manager.getCurrentLocation(
            provider,
            cancellationSignal,
            ContextCompat.getMainExecutor(context),
        ) { location ->
            if (continuation.isActive) continuation.resume(location)
        }
    }.onFailure {
        if (continuation.isActive) continuation.resume(null)
    }
}

private fun readLastKnownLocation(
    manager: LocationManager,
    maxAgeMs: Long,
): Location? {
    val providers = buildList {
        addAll(runCatching { manager.getProviders(false) }.getOrElse { emptyList() })
        add(LocationManager.GPS_PROVIDER)
        add(LocationManager.NETWORK_PROVIDER)
        add(LocationManager.PASSIVE_PROVIDER)
        add("fused")
    }

    val now = System.currentTimeMillis()
    val candidates = providers
        .distinct()
        .mapNotNull { provider ->
            runCatching { manager.getLastKnownLocation(provider) }.getOrNull()
        }
        .filter { isValidCoordinates(it.latitude, it.longitude) }
        .filter { location ->
            val ageMs = now - location.time
            ageMs in 0..maxAgeMs
        }

    return candidates.maxByOrNull { it.time }
}

private fun Location.toBumpLocation(): LastKnownLocation {
    return LastKnownLocation(
        lat = latitude,
        lon = longitude,
        accuracyMeters = accuracy.coerceAtLeast(1f),
    )
}

private fun hasLocationPermission(context: Context): Boolean {
    val hasFine = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
    val hasCoarse = ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
    return hasFine || hasCoarse
}

private fun isLocationServiceEnabled(manager: LocationManager): Boolean {
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
        return runCatching { manager.isLocationEnabled }.getOrDefault(false)
    }
    val gpsEnabled = runCatching { manager.isProviderEnabled(LocationManager.GPS_PROVIDER) }.getOrDefault(false)
    val networkEnabled = runCatching { manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) }.getOrDefault(false)
    return gpsEnabled || networkEnabled
}

private fun LastKnownLocation.isUsableForBump(): Boolean {
    if (!isValidCoordinates(lat, lon)) return false
    if (lat == 0.0 && lon == 0.0 && accuracyMeters >= 999f) return false
    return accuracyMeters in 0f..MAX_ACCEPTABLE_ACCURACY_METERS
}

private fun isLocationFreshEnough(location: Location, maxAgeMs: Long): Boolean {
    if (!isValidCoordinates(location.latitude, location.longitude)) return false
    val ageMs = System.currentTimeMillis() - location.time
    return ageMs in 0..maxAgeMs
}

private fun isValidCoordinates(lat: Double, lon: Double): Boolean {
    return lat in -90.0..90.0 && lon in -180.0..180.0
}
