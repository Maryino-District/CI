package maryino.district.carinspector.obd.domain.model.scan

import maryino.district.carinspector.obd.domain.model.ObdError
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Streams scan progress and results as they happen.
 *
 * Discovery is event-based because BLE and Wi-Fi candidates can appear over
 * time, and the UI should not wait for the whole scan window before updating.
 */
sealed interface ObdScanEvent {
    /** Emitted when a scan pass starts with its effective request parameters. */
    data class Started(val request: ObdScanRequest) : ObdScanEvent

    /** Emitted when a new possible OBD adapter is discovered. */
    data class CandidateFound(val adapter: DiscoveredObdAdapter) : ObdScanEvent

    /** Emitted when a previously discovered adapter receives fresher metadata or probe state. */
    data class CandidateUpdated(val adapter: DiscoveredObdAdapter) : ObdScanEvent

    /** Emitted when scan can offer optional guidance without stopping discovery. */
    data class HintAvailable(val hint: ObdScanHint) : ObdScanEvent

    /** Emitted when one transport fails while the overall scan may still continue. */
    data class Failed(val type: ObdTransportType, val error: ObdError) : ObdScanEvent

    /** Emitted when the scan window ends with the accumulated candidate list. */
    data class Finished(val candidates: List<DiscoveredObdAdapter>) : ObdScanEvent
}
