package maryino.district.carinspector.obd.data.discovery

import maryino.district.carinspector.obd.domain.model.adapter.BleServiceSummary
import maryino.district.carinspector.obd.domain.model.adapter.BleWriteMode
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class BleObdProfileRegistryTest {
    private val registry = BleObdProfileRegistry.default()

    @Test
    fun knownProfilesContainsStarterProfiles() {
        val profiles = registry.knownProfiles()

        assertEquals(listOf("obdlink_cx", "generic_fff0", "generic_18f0"), profiles.map { it.id })
        assertEquals(BleWriteMode.WithoutResponsePreferred, profiles.first { it.id == "generic_fff0" }.writeMode)
        assertTrue(profiles.first { it.id == "obdlink_cx" }.requiresMtuNegotiation)
        assertEquals(BleWriteMode.ByCharacteristicProperty, profiles.first { it.id == "generic_18f0" }.writeMode)
    }

    @Test
    fun matchReturnsProfilesForDiscoveredServiceUuid() {
        val matches = registry.match(listOf(service("000018F0-0000-1000-8000-00805F9B34FB")))

        assertEquals(listOf("generic_18f0"), matches.map { it.id })
    }

    @Test
    fun matchNormalizesShortAndLowercaseUuidForms() {
        val matches = registry.match(listOf(service("fff0")))

        assertEquals(listOf("obdlink_cx", "generic_fff0"), matches.map { it.id })
    }

    @Test
    fun matchOrdersDeviceSpecificProfileBeforeGenericProfile() {
        val matches = registry.match(listOf(service("0000FFF0-0000-1000-8000-00805F9B34FB")))

        assertEquals(listOf("obdlink_cx", "generic_fff0"), matches.map { it.id })
    }

    @Test
    fun matchReturnsAllKnownServicesBySpecificity() {
        val matches = registry.match(listOf(service("18F0"), service("FFF0")))

        assertEquals(listOf("obdlink_cx", "generic_fff0", "generic_18f0"), matches.map { it.id })
    }

    @Test
    fun matchReturnsEmptyListForUnknownServiceUuid() {
        val matches = registry.match(listOf(service("0000180A-0000-1000-8000-00805F9B34FB")))

        assertTrue(matches.isEmpty())
    }

    private fun service(uuid: String): BleServiceSummary =
        BleServiceSummary(serviceUuid = uuid, characteristics = emptyList())
}
