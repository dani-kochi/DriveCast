package dev.dani.drivecast.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import androidx.core.net.toUri
import dev.dani.drivecast.domain.model.LatLng
import timber.log.Timber

private const val GOOGLE_MAPS_PACKAGE = "com.google.android.apps.maps"

fun Context.openRouteInGoogleMaps(stops: List<LatLng>) {
    val mapsAppIntent = Intent(
        Intent.ACTION_VIEW,
        GoogleMapsLink.directionsUrl(stops).toUri()
    ).setPackage(GOOGLE_MAPS_PACKAGE)

    try {
        startActivity(mapsAppIntent)
        return
    } catch (_: ActivityNotFoundException) {
        Timber.tag("GoogleMapsLauncher").e("No Google Maps app installed.")
    }

    val browserIntent = Intent(
        Intent.ACTION_VIEW,
        GoogleMapsLink.directionsUrl(
            stops = stops,
            maxWaypoints = GoogleMapsLink.MAX_WAYPOINTS_MOBILE_BROWSER
        ).toUri()
    )
    try {
        startActivity(browserIntent)
    } catch (_: ActivityNotFoundException) {
        Timber.tag("GoogleMapsLauncher").e("No app can open a URL: ${browserIntent.data}")
        // Do nothing
    }
}
