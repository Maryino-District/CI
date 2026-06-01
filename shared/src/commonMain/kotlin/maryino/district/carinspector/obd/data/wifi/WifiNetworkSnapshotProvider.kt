package maryino.district.carinspector.obd.data.wifi

interface WifiNetworkSnapshotProvider {
    suspend fun snapshot(): WifiNetworkSnapshot?
}
