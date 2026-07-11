package me.domino.fa2.data.model

sealed interface QueryError {
  data class AuthRequired(val requestUrl: String, val message: String) : QueryError

  data object CfChallenge : QueryError

  data class MatureBlocked(val reason: String) : QueryError

  data class Exception(val cause: Throwable) : QueryError
}

fun QueryError.message(): String =
    when (this) {
      is QueryError.AuthRequired -> message
      QueryError.CfChallenge -> "Cloudflare challenge"
      is QueryError.MatureBlocked -> reason
      is QueryError.Exception -> cause.message ?: cause::class.simpleName.orEmpty()
    }

sealed interface QueryState<out T> {
  val data: T?

  data class Loading<T>(override val data: T? = null) : QueryState<T>

  data class Success<T : Any>(override val data: T, val refreshing: Boolean = false) : QueryState<T>

  data class Failure<T>(val error: QueryError, override val data: T? = null) : QueryState<T>
}

fun ResourceResult<*>.errorOrNull(): QueryError? =
    when (this) {
      is ResourceResult.Success -> null
      is ResourceResult.AuthRequired -> QueryError.AuthRequired(requestUrl, message)
      ResourceResult.CfChallenge -> QueryError.CfChallenge
      is ResourceResult.MatureBlocked -> QueryError.MatureBlocked(reason)
      is ResourceResult.Error -> QueryError.Exception(exception)
    }

fun ResourceResult<*>.errorMessageOrNull(): String? = errorOrNull()?.message()

fun <T : Any> ResourceResult<T>.toQueryState(stale: T? = null): QueryState<T> =
    when (this) {
      is ResourceResult.Success -> QueryState.Success(data)
      else -> QueryState.Failure(errorOrNull()!!, stale)
    }

inline fun <T, R : Any> QueryState<T>.mapData(transform: (T) -> R): QueryState<R> =
    when (this) {
      is QueryState.Loading -> QueryState.Loading(data?.let(transform))
      is QueryState.Success -> QueryState.Success(transform(data), refreshing)
      is QueryState.Failure -> QueryState.Failure(error, data?.let(transform))
    }
