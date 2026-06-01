package maryino.district.carinspector.obd.data.discovery

import maryino.district.carinspector.obd.domain.model.adapter.BleObdProfile
import maryino.district.carinspector.obd.domain.model.adapter.BleServiceSummary
import maryino.district.carinspector.obd.domain.model.adapter.BleWriteMode

interface BleObdProfileRegistry {
    fun knownProfiles(): List<BleObdProfile>

    /**
     * Returns profiles whose service UUID is present in discovered services.
     * Matches are ordered from device-specific profiles to generic profiles.
     */
    fun match(services: List<BleServiceSummary>): List<BleObdProfile>

    companion object {
        fun default(): BleObdProfileRegistry = DefaultBleObdProfileRegistry
    }
}

data object DefaultBleObdProfileRegistry : BleObdProfileRegistry {
    override fun knownProfiles(): List<BleObdProfile> = KnownProfiles

    override fun match(services: List<BleServiceSummary>): List<BleObdProfile> {
        val discoveredServiceUuids = services
            .map { normalizeUuid(it.serviceUuid) }
            .toSet()

        return KnownProfiles
            .filter { normalizeUuid(it.serviceUuid) in discoveredServiceUuids }
            .sortedWith(compareByDescending<BleObdProfile> { it.specificity }.thenBy { KnownProfiles.indexOf(it) })
    }

    private val KnownProfiles = listOf(
        BleObdProfile(
            id = "obdlink_cx",
            displayName = "OBDLink CX",
            serviceUuid = Fff0ServiceUuid,
            notifyCharacteristicUuid = Fff1CharacteristicUuid,
            writeCharacteristicUuid = Fff2CharacteristicUuid,
            writeMode = BleWriteMode.WithoutResponsePreferred,
            requiresMtuNegotiation = true,
            specificity = 100
        ),
        BleObdProfile(
            id = "generic_fff0",
            displayName = "Generic FFF0 UART",
            serviceUuid = Fff0ServiceUuid,
            notifyCharacteristicUuid = Fff1CharacteristicUuid,
            writeCharacteristicUuid = Fff2CharacteristicUuid,
            writeMode = BleWriteMode.WithoutResponsePreferred,
            requiresMtuNegotiation = false,
            specificity = 10
        ),
        BleObdProfile(
            id = "generic_18f0",
            displayName = "Generic 18F0 UART",
            serviceUuid = "000018F0-0000-1000-8000-00805F9B34FB",
            notifyCharacteristicUuid = null,
            writeCharacteristicUuid = null,
            writeMode = BleWriteMode.ByCharacteristicProperty,
            requiresMtuNegotiation = false,
            specificity = 5
        )
    )

    private const val Fff0ServiceUuid = "0000FFF0-0000-1000-8000-00805F9B34FB"
    private const val Fff1CharacteristicUuid = "0000FFF1-0000-1000-8000-00805F9B34FB"
    private const val Fff2CharacteristicUuid = "0000FFF2-0000-1000-8000-00805F9B34FB"

    private fun normalizeUuid(uuid: String): String {
        val compact = uuid
            .trim()
            .lowercase()
            .replace("-", "")

        val expanded = when (compact.length) {
            4 -> "0000${compact}00001000800000805f9b34fb"
            8 -> "${compact}00001000800000805f9b34fb"
            else -> compact
        }

        return if (expanded.length == 32) {
            "${expanded.substring(0, 8)}-${expanded.substring(8, 12)}-" +
                "${expanded.substring(12, 16)}-${expanded.substring(16, 20)}-" +
                expanded.substring(20, 32)
        } else {
            expanded
        }
    }
}
