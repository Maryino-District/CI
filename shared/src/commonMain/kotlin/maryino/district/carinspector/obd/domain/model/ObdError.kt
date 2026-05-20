package maryino.district.carinspector.obd.domain.model

sealed interface ObdError {
    data class UnsupportedTransport(val type: ObdTransportType) : ObdError
    data class PermissionDenied(val action: ObdRequiredSetupAction) : ObdError
    data class BluetoothDisabled(val action: ObdRequiredSetupAction) : ObdError
    data class NoBondedClassicDevices(val action: ObdRequiredSetupAction) : ObdError
    data class BlePeripheralUnavailable(val peripheralId: String) : ObdError
    data class BleProfileNotFound(val serviceUuids: List<String>) : ObdError
    data class CandidateIsNotElm327(val targetLabel: String?) : ObdError
    data object WifiNetworkNotConnected : ObdError
    data class TcpEndpointUnavailable(val host: String, val port: Int) : ObdError
    data class ElmHandshakeFailed(
        val transportType: ObdTransportType,
        val targetLabel: String?,
        val lastRawResponse: String?
    ) : ObdError

    data class Timeout(
        val operation: ObdOperation,
        val transportType: ObdTransportType?,
        val targetLabel: String?
    ) : ObdError

    data object AlreadyConnecting : ObdError
    data class TransportClosed(
        val transportType: ObdTransportType?,
        val reason: String?
    ) : ObdError

    data class Unknown(val message: String?) : ObdError
}
