package maryino.district.carinspector.obd.domain.model.elm327

sealed interface Elm327ResponseStatus {
    data object Ok : Elm327ResponseStatus
    data object NoData : Elm327ResponseStatus
    data object UnableToConnect : Elm327ResponseStatus
    data object UnknownCommand : Elm327ResponseStatus
    data object Timeout : Elm327ResponseStatus
    data class BusyProcessing(val rawMarker: String) : Elm327ResponseStatus
}
