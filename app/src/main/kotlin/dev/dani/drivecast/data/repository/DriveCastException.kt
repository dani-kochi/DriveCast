package dev.dani.drivecast.data.repository

// Upstream service error responses or processing errors
class DriveCastException(
    message: String,
    cause: Throwable? = null,
) : Exception(message, cause)