package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Platform-neutral summary of discovered BLE GATT services.
 */
data class BleServiceSummary(
    val serviceUuid: String,
    val characteristics: List<BleCharacteristicSummary>
)

data class BleCharacteristicSummary(
    val uuid: String,
    val properties: Set<BleCharacteristicProperty>
)

enum class BleCharacteristicProperty {
    Read,
    Write,
    WriteWithoutResponse,
    Notify,
    Indicate
}

enum class BleWriteMode {
    WithResponse,
    WithoutResponse,
    WithoutResponsePreferred,
    ByCharacteristicProperty
}
