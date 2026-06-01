package maryino.district.carinspector.obd.data.wifi

/**
 * Platform-provided Wi-Fi network metadata used to build OBD TCP endpoint candidates.
 *
 * Fields are optional because Android/iOS may hide SSID, BSSID, gateway, or local
 * address depending on permissions, capabilities, and current network state.
 */
data class WifiNetworkSnapshot(
    val ssid: String?,
    val bssid: String?,
    val gatewayHost: String?,
    val localHost: String?,
    val subnetPrefix: String?
)
