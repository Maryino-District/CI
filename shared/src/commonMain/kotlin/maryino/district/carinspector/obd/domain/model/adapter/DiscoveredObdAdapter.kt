package maryino.district.carinspector.obd.domain.model.adapter

import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Adapter candidate shown by the unified scan flow.
 *
 * This model is safe for UI/domain use: it contains enough information to start
 * a connection attempt, but no raw platform object or open transport handle.
 */
data class DiscoveredObdAdapter(
    /** Stable candidate id for de-duplication and remembered-adapter matching. */
    val id: ObdAdapterId,
    /** User-facing name from Bluetooth advertisement, bonded device, or endpoint label. */
    val displayName: String,
    /** Transport family that produced this candidate. */
    val transportType: ObdTransportType,
    /** Connection target understood by the data/platform transport factory. */
    val target: ObdConnectionTarget,
    /** Optional radio/network signal estimate when the transport can report it. */
    val signal: ObdSignalStrength?,
    /** Confidence score explaining how likely this candidate is to be an OBD adapter. */
    val confidence: ObdAdapterConfidence,
    /** Current validation phase for this candidate. */
    val probeState: ObdCandidateProbeState,
    /** Capabilities inferred during discovery or validation. */
    val capabilities: Set<ObdAdapterCapability>,
    /** Last moment when discovery saw or refreshed this candidate. */
    val lastSeenAt: Instant
)
