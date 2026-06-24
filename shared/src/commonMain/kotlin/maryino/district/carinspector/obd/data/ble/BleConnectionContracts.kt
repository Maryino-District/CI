package maryino.district.carinspector.obd.data.ble

import maryino.district.carinspector.obd.data.transport.ObdByteChannel
import maryino.district.carinspector.obd.domain.model.ObdResult
import maryino.district.carinspector.obd.domain.model.adapter.BleObdProfile
import maryino.district.carinspector.obd.domain.model.adapter.BleServiceSummary

interface BlePeripheralConnection {
    val peripheralId: String

    suspend fun discoverServices(): ObdResult<List<BleServiceSummary>>

    suspend fun openSerialChannel(profile: BleObdProfile): ObdResult<ObdByteChannel>

    /**
     * Closes GATT, notification subscriptions, and any pending platform work.
     *
     * Implementations must make this idempotent because resolution, service
     * discovery, serial channel opening, ELM validation, and cancellation can
     * all race at ownership boundaries.
     */
    suspend fun close()
}
