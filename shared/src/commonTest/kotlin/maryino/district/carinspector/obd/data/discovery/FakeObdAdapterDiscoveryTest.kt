package maryino.district.carinspector.obd.data.discovery

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertIs
import kotlin.test.assertNull
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import maryino.district.carinspector.obd.domain.model.scan.ObdScanRequest
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType
import maryino.district.carinspector.obd.test.FakeObdAdapterDiscovery
import maryino.district.carinspector.obd.test.bleCandidate
import maryino.district.carinspector.obd.test.classicCandidate
import maryino.district.carinspector.obd.test.wifiCandidate

class FakeObdAdapterDiscoveryTest {
    @Test
    fun noOpDiscoveryEmitsNoEvents() = runTest {
        val discovery = FakeObdAdapterDiscovery.noOp()

        val events = discovery.scan(ObdScanRequest()).toList()

        assertEquals(emptyList(), events)
    }

    @Test
    fun scriptedDiscoveryEmitsCandidatesInOrder() = runTest {
        val classic = classicCandidate(id = "classic")
        val ble = bleCandidate(id = "ble")
        val wifi = wifiCandidate(id = "wifi")
        val discovery = FakeObdAdapterDiscovery.scripted(
            ObdDiscoveryEvent.CandidateFound(classic),
            ObdDiscoveryEvent.CandidateFound(ble),
            ObdDiscoveryEvent.CandidateFound(wifi)
        )

        val events = discovery.scan(ObdScanRequest()).toList()

        assertEquals(listOf("classic", "ble", "wifi"), events.candidateIds())
        assertEquals(ObdTransportType.BluetoothClassic, events.candidateAt(0).transportType)
        assertEquals(ObdTransportType.BluetoothLowEnergy, events.candidateAt(1).transportType)
        assertEquals(ObdTransportType.WifiTcp, events.candidateAt(2).transportType)
    }

    @Test
    fun scanStoresLastRequestWhenFlowIsCollected() = runTest {
        val discovery = FakeObdAdapterDiscovery.noOp()
        val request = ObdScanRequest(
            transportTypes = setOf(ObdTransportType.BluetoothLowEnergy),
            includeRememberedAdapters = false
        )

        assertNull(discovery.lastRequest)

        discovery.scan(request).toList()

        assertEquals(request, discovery.lastRequest)
    }

    private fun List<ObdDiscoveryEvent>.candidateIds(): List<String> =
        map { event -> event.candidate().id.value }

    private fun List<ObdDiscoveryEvent>.candidateAt(index: Int) =
        this[index].candidate()

    private fun ObdDiscoveryEvent.candidate() =
        assertIs<ObdDiscoveryEvent.CandidateFound>(this).adapter
}
