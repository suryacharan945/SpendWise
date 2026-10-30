package com.example.core.common

import java.io.IOException
import java.net.SocketTimeoutException
import kotlinx.coroutines.CancellationException

/**
 * Enterprise FinTech AppError taxonomy.
 * Provides strongly-typed categorization for Network, Authentication,
 * Database, Validation, Conflict, and Unexpected failures.
 */
sealed class AppError(
    override val message: String,
    override val cause: Throwable? = null,
) : Exception(message, cause) {

    sealed class Network : AppError("Network error") {
        data object NoConnection : Network()
        data class Timeout(override val cause: Throwable) : Network() {
            override val message: String get() = "Request timed out"
        }
        data class Server(val code: Int) : Network() {
            override val message: String get() = "Server error $code"
        }
    }

    sealed class Auth : AppError("Authentication error") {
        data class InvalidCredentials(override val cause: Throwable? = null) : Auth() {
            override val message: String get() = "Invalid email or password"
        }
        data object EmailNotVerified : Auth() {
            override val message: String get() = "Email is not verified"
        }
        data object SessionExpired : Auth() {
            override val message: String get() = "Session has expired"
        }
        data class GoogleFailed(override val cause: Throwable) : Auth() {
            override val message: String get() = "Google Sign-In failed"
        }
    }

    sealed class Database : AppError("Database error") {
        data class ReadFailed(override val cause: Throwable) : Database() {
            override val message: String get() = "Could not read local data"
        }
        data class WriteFailed(override val cause: Throwable) : Database() {
            override val message: String get() = "Could not save locally"
        }
        data class DecryptionFailed(override val cause: Throwable) : Database() {
            override val message: String get() = "Database integrity check failed"
        }
    }

    data class Validation(val fieldErrors: Map<String, String>) : AppError("Validation failed") {
        fun firstError(): String = fieldErrors.values.firstOrNull() ?: "Validation failed"
    }

    data class Conflict(override val message: String) : AppError(message)

    data class Unexpected(override val cause: Throwable) : AppError(cause.message ?: "Something went wrong", cause)

    fun userMessage(): String = when (this) {
        is Network.NoConnection -> "No internet connection. Please check your network."
        is Network.Timeout -> "Request timed out. Please try again."
        is Network.Server -> "Server error ($code). Please try again later."
        is Auth.InvalidCredentials -> "Invalid email or password. Please verify your details."
        is Auth.EmailNotVerified -> "Please verify your email address to continue."
        is Auth.SessionExpired -> "Your session has expired. Please sign in again."
        is Auth.GoogleFailed -> "Google sign-in could not be completed."
        is Database.DecryptionFailed -> "Security error: local data decryption failed."
        is Database.ReadFailed, is Database.WriteFailed -> "Local database storage error. Please try again."
        is Validation -> firstError()
        is Conflict -> message
        is Unexpected -> message
    }
}

/**
 * Maps standard JVM/Android exceptions into strongly-typed AppErrors.
 */
fun Throwable.toAppError(): AppError = when (this) {
    is AppError -> this
    is SocketTimeoutException -> AppError.Network.Timeout(this)
    is IOException -> AppError.Network.NoConnection
    else -> AppError.Unexpected(this)
}

/**
 * Executes a suspending block safely, catching exceptions and wrapping results into Resource.
 */
suspend fun <T> safeCall(block: suspend () -> T): Resource<T> = try {
    Resource.Success(block())
} catch (e: CancellationException) {
    throw e
} catch (e: Throwable) {
    Resource.Error(e.toAppError())
}
