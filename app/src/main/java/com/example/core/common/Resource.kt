package com.example.core.common

/**
 * Standard Result state wrapper for production FinTech data flow.
 * Handles Success, Loading, and typed AppError states across
 * Data, Domain, and Presentation layers.
 */
sealed class Resource<out T> {
    data class Success<out T>(
        val data: T,
        val isStale: Boolean = false
    ) : Resource<T>()

    data class Error<out T>(
        val message: String,
        val cause: Throwable? = null,
        val error: AppError = (cause as? AppError) ?: (if (cause != null) AppError.Unexpected(cause) else AppError.Conflict(message)),
        val data: T? = null
    ) : Resource<T>() {
        constructor(appError: AppError, data: T? = null) : this(
            message = appError.message,
            cause = appError.cause,
            error = appError,
            data = data
        )
    }

    data class Loading<out T>(val data: T? = null) : Resource<T>()

    val isSuccess: Boolean get() = this is Success
    val isError: Boolean get() = this is Error
    val isLoading: Boolean get() = this is Loading

    fun getOrNull(): T? = when (this) {
        is Success -> data
        is Error -> data
        is Loading -> data
    }

    inline fun <R> map(transform: (T) -> R): Resource<R> = when (this) {
        is Success -> Success(transform(data), isStale)
        is Error -> Error(message = message, cause = cause, error = error, data = data?.let(transform))
        is Loading -> Loading(data?.let(transform))
    }

    inline fun onSuccess(action: (T) -> Unit): Resource<T> {
        if (this is Success) action(data)
        return this
    }

    inline fun onError(action: (String, Throwable?) -> Unit): Resource<T> {
        if (this is Error) action(message, cause)
        return this
    }

    inline fun onLoading(action: (T?) -> Unit): Resource<T> {
        if (this is Loading) action(data)
        return this
    }

    companion object {
        /** Offline-first helper: emit cached data immediately, refresh after. */
        fun <T> cachedThenRefresh(cached: T): List<Resource<T>> =
            listOf(Success(cached, isStale = true), Loading())
    }
}

val <T> Resource<T>.dataOrNull: T? get() = (this as? Resource.Success)?.data
