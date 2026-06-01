package maryino.district.carinspector.obd.domain.model.adapter

import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

/**
 * Persistable fingerprint of the last successfully connected OBD adapter.
 *
 * Contains only stable identifiers and metadata that are safe for common code.
 * Platform handles such as BluetoothDevice, CBPeripheral, sockets, or GATT
 * objects must stay below the transport layer and must not be stored here.
 */
data class AdapterFingerprint(
    val transportType: ObdTransportType,
    val stableId: String,
    val displayName: String?,
    val bleProfileId: String?,
    val wifiHost: String?,
    val wifiPort: Int?,
    val lastSuccessfulAt: Instant
)
