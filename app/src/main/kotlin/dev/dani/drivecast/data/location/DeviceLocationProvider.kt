package dev.dani.drivecast.data.location

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.os.CancellationSignal
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import com.google.android.gms.common.ConnectionResult
import com.google.android.gms.common.GoogleApiAvailability
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.tasks.CancellationTokenSource
import dagger.hilt.android.qualifiers.ApplicationContext
import dev.dani.drivecast.di.IoDispatcher
import dev.dani.drivecast.domain.location.LocationData
import dev.dani.drivecast.domain.location.LocationProvider
import dev.dani.drivecast.domain.model.LatLng
import kotlinx.coroutines.CancellableContinuation
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject
import javax.inject.Singleton
import kotlin.coroutines.resume
import kotlin.time.Duration.Companion.milliseconds

@Singleton
class DeviceLocationProvider @Inject constructor(
    @param:ApplicationContext private val context: Context,
    @param:IoDispatcher private val ioDispatcher: CoroutineDispatcher
) : LocationProvider {

    private val locationManager: LocationManager? =
        ContextCompat.getSystemService(context, LocationManager::class.java)

    override fun hasPermission(): Boolean =
        granted(Manifest.permission.ACCESS_COARSE_LOCATION) ||
                granted(Manifest.permission.ACCESS_FINE_LOCATION)

    override suspend fun currentLocation(): LocationData {
        if (hasPermission().not()) return LocationData.PermissionMissing
        val manager = locationManager ?: return LocationData.Unavailable
        if (LocationManagerCompat.isLocationEnabled(manager).not()) return LocationData.LocationDisabled

        val location = withTimeoutOrNull(LOCATION_TIMEOUT.milliseconds) {
            getCurrentFusedLocation() ?: getCurrentOrLastKnownGpsLocation(manager)
        }
        return location
            ?.let { LocationData.Available(LatLng(it.latitude, it.longitude)) }
            ?: LocationData.Unavailable
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentFusedLocation(): Location? {
        if (playServicesAvailable().not()) return null

        return runCatching {
            val client = LocationServices.getFusedLocationProviderClient(context)
            val tokenSource = CancellationTokenSource()
            val current = suspendCancellableCoroutine { c ->
                c.invokeOnCancellation { tokenSource.cancel() }
                client.getCurrentLocation(Priority.PRIORITY_BALANCED_POWER_ACCURACY, tokenSource.token)
                    .addOnSuccessListener { c.resumeIfActive(it) }
                    .addOnFailureListener { c.resumeIfActive(null) }
                    .addOnCanceledListener { c.resumeIfActive(null) }
            }
            current ?: suspendCancellableCoroutine<Location?> { c ->
                client.lastLocation
                    .addOnSuccessListener { c.resumeIfActive(it) }
                    .addOnFailureListener { c.resumeIfActive(null) }
                    .addOnCanceledListener { c.resumeIfActive(null) }
            }
        }.getOrNull()
    }

    @SuppressLint("MissingPermission")
    private suspend fun getCurrentOrLastKnownGpsLocation(manager: LocationManager): Location? = runCatching {
        val provider = if (manager.isProviderEnabled(LocationManager.GPS_PROVIDER)) {
            LocationManager.GPS_PROVIDER
        } else {
            return null
        }
        suspendCancellableCoroutine { c ->
            val signal = CancellationSignal()
            c.invokeOnCancellation { signal.cancel() }
            LocationManagerCompat.getCurrentLocation(
                manager,
                provider,
                signal,
                ContextCompat.getMainExecutor(context)
            ) { location ->
                c.resumeIfActive(location)
            }
        } ?: withContext(ioDispatcher) {
            runCatching {
                manager.getLastKnownLocation(LocationManager.GPS_PROVIDER)
            }.getOrNull()
        }
    }.getOrNull()

    private fun CancellableContinuation<Location?>.resumeIfActive(location: Location?) {
        if (isActive) resume(location)
    }

    private fun granted(permission: String) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private fun playServicesAvailable(): Boolean = runCatching {
        GoogleApiAvailability.getInstance().isGooglePlayServicesAvailable(context) == ConnectionResult.SUCCESS
    }.getOrDefault(false)

    private companion object {
        const val LOCATION_TIMEOUT = 8_000L
    }
}
