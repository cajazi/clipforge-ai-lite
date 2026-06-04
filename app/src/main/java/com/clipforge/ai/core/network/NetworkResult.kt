package com.clipforge.ai.core.network
sealed class NetworkResult<out T> {
    data class Success<T>(val data: T) : NetworkResult<T>()
    data class Error(val code: Int? = null, val message: String?) : NetworkResult<Nothing>()
    object Loading : NetworkResult<Nothing>()
}
