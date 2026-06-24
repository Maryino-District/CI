package maryino.district.carinspector.obd.data.ble

import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget

interface BlePeripheralResolver {
    suspend fun resolve(target: ObdConnectionTarget.Ble): ObdResult<BleResolvedPeripheral>
}

interface BleResolvedPeripheral {
    val peripheralId: String
    val name: String?
    val resolvedAt: Instant
}
