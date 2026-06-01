package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Known or inferred BLE UART-like profile for an OBD adapter.
 *
 * Characteristic UUIDs are nullable for profiles where the registry can only
 * identify the service and the transport must select characteristics by GATT
 * properties after service discovery.
 */
data class BleObdProfile(
    val id: String,
    val displayName: String,
    val serviceUuid: String,
    val notifyCharacteristicUuid: String?,
    val writeCharacteristicUuid: String?,
    val writeMode: BleWriteMode,
    val requiresMtuNegotiation: Boolean,
    val specificity: Int
)
