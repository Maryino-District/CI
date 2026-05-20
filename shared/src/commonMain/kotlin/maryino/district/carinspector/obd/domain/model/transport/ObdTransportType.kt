package maryino.district.carinspector.obd.domain.model.transport

/**
 * Describes the transport family at the domain level.
 *
 * UI and use cases can reason about supported connection paths without depending
 * on Android Bluetooth, CoreBluetooth, sockets, UUIDs, or platform handles.
 */
enum class ObdTransportType {
    /** Android-only Bluetooth Classic SPP connection through already paired devices. */
    BluetoothClassic,

    /** Cross-platform BLE connection through known or heuristic UART-like GATT profiles. */
    BluetoothLowEnergy,

    /** Cross-platform TCP socket connection to a Wi-Fi OBD adapter network endpoint. */
    WifiTcp
}
