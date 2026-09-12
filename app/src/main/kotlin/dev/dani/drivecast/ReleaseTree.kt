package dev.dani.drivecast

import android.util.Log
import timber.log.Timber

class ReleaseTree : Timber.Tree() {
    override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
        if (priority == Log.ERROR || priority == Log.WARN) {
            Log.println(priority, tag ?: "DriveCast", message)
            t?.let { Log.println(Log.ERROR, tag ?: "DriveCast", Log.getStackTraceString(it)) }
        }
    }
}
