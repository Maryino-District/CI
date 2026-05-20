package maryino.district.carinspector.obd.domain.model.session

import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Adapter identity captured after a successful ELM327 handshake.
 *
 * Unlike DiscoveredObdAdapter, this model represents a proven adapter that has
 * become part of an active session.
 */
data class ConnectedObdAdapter(
    val id: ObdAdapterId,
    val displayName: String,
    val transportType: ObdTransportType,
    val target: ObdConnectionTarget
)

