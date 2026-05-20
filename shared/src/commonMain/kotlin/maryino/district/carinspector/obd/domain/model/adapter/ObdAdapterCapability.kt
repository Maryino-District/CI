package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Capabilities inferred for a candidate during discovery or after handshake.
 *
 * These flags are descriptive only; connection behavior should still be driven
 * by the selected target and verified ELM327 session.
 */
enum class ObdAdapterCapability {
    /** Adapter can be reached through Bluetooth Classic SPP. */
    BluetoothClassicSpp,

    /** Adapter exposes a known BLE UART-like profile. */
    KnownBleUartProfile,

    /** Adapter exposes a heuristic BLE UART-like profile. */
    HeuristicBleUartProfile,

    /** Adapter can be reached through a Wi-Fi TCP endpoint. */
    WifiTcpEndpoint,

    /** Adapter has passed an ELM327 probe. */
    Elm327Compatible
}

