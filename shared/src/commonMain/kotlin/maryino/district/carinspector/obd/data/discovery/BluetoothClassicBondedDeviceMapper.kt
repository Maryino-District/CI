package maryino.district.carinspector.obd.data.discovery

import kotlin.time.Clock
import maryino.district.carinspector.obd.domain.model.adapter.DiscoveredObdAdapter
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterCapability
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterId
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

data class BluetoothClassicBondedDevice(
    val address: String,
    val name: String?
)

class BluetoothClassicBondedDeviceMapper(
    private val clock: Clock = Clock.System,
    private val nameMatcher: ObdLikeNameMatcher = ObdLikeNameMatcher.Default
) {
    fun map(device: BluetoothClassicBondedDevice): DiscoveredObdAdapter? {
        val address = device.address.trim().takeIf { it.isNotBlank() }?.uppercase() ?: return null
        val name = device.name?.trim()?.takeIf { it.isNotBlank() }

        return DiscoveredObdAdapter(
            id = ObdAdapterId("classic:$address"),
            displayName = name ?: address,
            transportType = ObdTransportType.BluetoothClassic,
            target = ObdConnectionTarget.BluetoothClassic(
                deviceAddress = address,
                deviceName = name
            ),
            signal = null,
            confidence = if (nameMatcher.matches(name)) {
                ObdAdapterConfidence.Medium
            } else {
                ObdAdapterConfidence.Low
            },
            probeState = ObdCandidateProbeState.AdvertisementOnly,
            capabilities = setOf(ObdAdapterCapability.BluetoothClassicSpp),
            lastSeenAt = clock.now()
        )
    }
}
