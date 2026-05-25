package maryino.district.carinspector.obd.domain.repository

import kotlinx.coroutines.flow.Flow
import maryino.district.carinspector.obd.domain.model.ObdAutoConnectPolicy
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.scan.ObdScanEvent
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.session.ObdConnectionState
import maryino.district.carinspector.obd.domain.model.session.ObdSession
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportAvailability

/**
 * Domain facade for scanning, connecting, disconnecting, and observing OBD state.
 *
 * Presentation and other features use this API without knowing whether the
 * active adapter is Bluetooth Classic, BLE, or Wi-Fi TCP. A successful
 * connection result means the adapter passed ELM327 validation, not merely that
 * a low-level transport was opened.
 */
interface ObdConnectionRepository {
    /** Single source of truth for the current connection feature state. */
    val connectionState: Flow<ObdConnectionState>

    /** Command gateway for diagnostics/PID features after a verified connection. */
    val commandGateway: ObdCommandGateway

    /** Emits current platform support and recoverable setup requirements. */
    fun observeSupportedTransports(): Flow<List<ObdTransportAvailability>>

    /** Starts one discovery pass and emits candidates as they appear. */
    fun scan(request: ObdScanRequest): Flow<ObdScanEvent>

    /** Opens the selected target and returns success only after ELM327 handshake. */
    suspend fun connect(target: ObdConnectionTarget): ObdResult<ObdSession>

    /** Runs scan/ranking and may connect silently only to a remembered adapter. */
    suspend fun autoConnect(policy: ObdAutoConnectPolicy): ObdResult<ObdSession>

    /** Cancels scan/attempt/session work and closes active transport resources. */
    suspend fun disconnect()
}
