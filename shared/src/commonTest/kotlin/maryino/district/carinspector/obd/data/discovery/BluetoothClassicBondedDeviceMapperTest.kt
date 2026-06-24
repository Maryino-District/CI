package maryino.district.carinspector.obd.data.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlin.time.Clock
import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterCapability
import maryino.district.carinspector.obd.domain.model.adapter.ObdAdapterConfidence
import maryino.district.carinspector.obd.domain.model.adapter.ObdCandidateProbeState
import maryino.district.carinspector.obd.domain.model.adapter.ObdConnectionTarget
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class BluetoothClassicBondedDeviceMapperTest {
    private val mapper = BluetoothClassicBondedDeviceMapper(clock = FixedClock)

    @Test
    fun mapsObdLikeBondedDeviceToClassicCandidate() {
        val candidate = mapper.map(
            BluetoothClassicBondedDevice(
                address = "aa:bb:cc:dd:ee:ff",
                name = "ELM327"
            )
        )

        requireNotNull(candidate)
        val target = assertIs<ObdConnectionTarget.BluetoothClassic>(candidate.target)

        assertEquals("classic:AA:BB:CC:DD:EE:FF", candidate.id.value)
        assertEquals("ELM327", candidate.displayName)
        assertEquals(ObdTransportType.BluetoothClassic, candidate.transportType)
        assertEquals("AA:BB:CC:DD:EE:FF", target.deviceAddress)
        assertEquals("ELM327", target.deviceName)
        assertEquals(null, candidate.signal)
        assertEquals(ObdAdapterConfidence.Medium, candidate.confidence)
        assertEquals(ObdCandidateProbeState.AdvertisementOnly, candidate.probeState)
        assertEquals(setOf(ObdAdapterCapability.BluetoothClassicSpp), candidate.capabilities)
        assertEquals(NOW, candidate.lastSeenAt)
    }

    @Test
    fun mapsUnknownNameWithLowConfidence() {
        val candidate = mapper.map(
            BluetoothClassicBondedDevice(
                address = "00:11:22:33:44:55",
                name = "Headset"
            )
        )

        requireNotNull(candidate)

        assertEquals("Headset", candidate.displayName)
        assertEquals(ObdAdapterConfidence.Low, candidate.confidence)
    }

    @Test
    fun usesAddressAsDisplayNameWhenNameIsBlank() {
        val candidate = mapper.map(
            BluetoothClassicBondedDevice(
                address = "00:11:22:33:44:55",
                name = "   "
            )
        )

        requireNotNull(candidate)
        val target = assertIs<ObdConnectionTarget.BluetoothClassic>(candidate.target)

        assertEquals("00:11:22:33:44:55", candidate.displayName)
        assertEquals(null, target.deviceName)
        assertEquals(ObdAdapterConfidence.Low, candidate.confidence)
    }

    @Test
    fun ignoresDeviceWithBlankAddress() {
        val candidate = mapper.map(
            BluetoothClassicBondedDevice(
                address = "   ",
                name = "ELM327"
            )
        )

        assertNull(candidate)
    }

    private object FixedClock : Clock {
        override fun now(): Instant = NOW
    }

    private companion object {
        val NOW = Instant.parse("2026-05-31T00:00:00Z")
    }
}
