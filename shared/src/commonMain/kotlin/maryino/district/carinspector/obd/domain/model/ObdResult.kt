package maryino.district.carinspector.obd.domain.model

sealed interface ObdResult<out T> {
    data class Success<T>(val value: T) : ObdResult<T>
    data class Failure(val error: ObdError) : ObdResult<Nothing>
}
