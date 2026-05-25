package maryino.district.carinspector.obd.data.transport

import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget

/**
 * Opens the platform transport required by an OBD connection target.
 *
 * The returned channel is already connected at the byte-transport level, but
 * callers must still perform ELM327 validation before treating it as connected.
 */
interface ObdTransportFactory {
    suspend fun open(target: ObdConnectionTarget): ObdResult<ObdByteChannel>
}
