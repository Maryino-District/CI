package maryino.district.carinspector.obd.domain.model.session

import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.ObdRequiredSetupAction
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.scan.ObdScanHint
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Single source of truth for the OBD connection feature state.
 *
 * Presentation should render this state stream instead of composing several
 * independent boolean flags.
 */
sealed interface ObdConnectionState {
    /** No scan, connection attempt, or active session is running. */
    data object Idle : ObdConnectionState

    /** The app is scanning across one or more transports and accumulating candidates. */
    data class FindingAdapters(
        val activeTransports: Set<ObdTransportType>,
        val candidates: List<DiscoveredObdAdapter>,
        val hint: ObdScanHint?
    ) : ObdConnectionState

    /** The repository is opening a target and validating it as ELM327-compatible. */
    data class Connecting(val attempt: ObdConnectionAttempt) : ObdConnectionState

    /** Transport is open and the ELM327 initialization handshake is in progress. */
    data class InitializingElm327(val adapter: DiscoveredObdAdapter?) : ObdConnectionState

    /** A verified ELM327 session is active. */
    data class Connected(val session: ObdSession) : ObdConnectionState

    /** Disconnect is closing the active session resources. */
    data class Disconnecting(val sessionId: ObdSessionId) : ObdConnectionState

    /** The last scan or connection flow failed with a typed domain error. */
    data class Failed(
        val error: ObdError,
        val recoverAction: ObdRequiredSetupAction?
    ) : ObdConnectionState
}

