package maryino.district.carinspector.obd.domain.model.adapter

import kotlin.time.Instant

/**
 * Transport-neutral description of how to open a connection attempt.
 *
 * The target contains stable identifiers and discovery metadata only. Platform
 * code is responsible for resolving these values into real Bluetooth/GATT/socket handles.
 */
sealed interface ObdConnectionTarget {
    /** Android-only Bluetooth Classic target for an already paired SPP device. */
    data class BluetoothClassic(
        val deviceAddress: String,
        val deviceName: String?
    ) : ObdConnectionTarget

    /** BLE target resolved through a fresh scanner/resolver cache on each platform. */
    data class Ble(
        val peripheralId: String,
        val deviceName: String?,
        val knownProfileId: String?,
        val discoveredServiceUuids: List<String>,
        val discoveredAt: Instant
    ) : ObdConnectionTarget

    /** TCP endpoint candidate for a Wi-Fi OBD adapter network. */
    data class WifiTcp(
        val host: String,
        val port: Int,
        val source: WifiCandidateSource
    ) : ObdConnectionTarget
}
