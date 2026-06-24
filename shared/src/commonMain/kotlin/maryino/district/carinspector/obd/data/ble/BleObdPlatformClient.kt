package maryino.district.carinspector.obd.data.ble

import kotlinx.coroutines.flow.Flow
import maryino.district.carinspector.obd.domain.model.ObdResult

/**
 * Lifecycle owner for platform BLE state.
 *
 * Android and iOS implementations may keep native scanner, central manager,
 * GATT, or peripheral handles internally, but those handles must not be exposed
 * through this common contract.
 */
interface BleObdPlatformClient {
    /**
     * Resolver backed by the same platform BLE owner as this client.
     *
     * Resolved peripherals are only valid for the client/resolver pair that
     * produced them; callers should not pass them between unrelated instances.
     */
    val resolver: BlePeripheralResolver

    fun scan(request: BleScanRequest): Flow<BleScanEvent>

    suspend fun connect(peripheral: BleResolvedPeripheral): ObdResult<BlePeripheralConnection>
}
