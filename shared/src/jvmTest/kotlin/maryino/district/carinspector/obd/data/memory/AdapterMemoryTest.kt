package maryino.district.carinspector.obd.data.memory

import com.russhwolf.settings.Settings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class AdapterMemoryTest {
    @Test
    fun saveAndLoadReturnsFingerprint() {
        val memory = AdapterMemory(FakeSettings())
        val fingerprint = AdapterFingerprint(
            transportType = ObdTransportType.WifiTcp,
            stableId = "192.168.0.10:35000",
            displayName = "OBDII",
            bleProfileId = null,
            wifiHost = "192.168.0.10",
            wifiPort = 35000,
            lastSuccessfulAt = Instant.parse("2026-05-28T12:00:00Z")
        )

        memory.save(fingerprint)

        assertEquals(fingerprint, memory.load())
    }

    @Test
    fun saveRemovesNullableFieldsFromPreviousFingerprint() {
        val memory = AdapterMemory(FakeSettings())
        memory.save(
            AdapterFingerprint(
                transportType = ObdTransportType.BluetoothLowEnergy,
                stableId = "ble-id",
                displayName = "BLE OBD",
                bleProfileId = "uart",
                wifiHost = "192.168.0.10",
                wifiPort = 35000,
                lastSuccessfulAt = Instant.parse("2026-05-28T12:00:00Z")
            )
        )

        val fingerprint = AdapterFingerprint(
            transportType = ObdTransportType.BluetoothClassic,
            stableId = "00:11:22:33:44:55",
            displayName = null,
            bleProfileId = null,
            wifiHost = null,
            wifiPort = null,
            lastSuccessfulAt = Instant.parse("2026-05-28T13:00:00Z")
        )
        memory.save(fingerprint)

        assertEquals(fingerprint, memory.load())
    }

    @Test
    fun clearRemovesSavedFingerprint() {
        val memory = AdapterMemory(FakeSettings())
        memory.save(
            AdapterFingerprint(
                transportType = ObdTransportType.WifiTcp,
                stableId = "192.168.0.10:35000",
                displayName = "OBDII",
                bleProfileId = null,
                wifiHost = "192.168.0.10",
                wifiPort = 35000,
                lastSuccessfulAt = Instant.parse("2026-05-28T12:00:00Z")
            )
        )

        memory.clear()

        assertNull(memory.load())
    }

    private class FakeSettings : Settings {
        private val values = mutableMapOf<String, Any>()

        override val keys: Set<String>
            get() = values.keys

        override val size: Int
            get() = values.size

        override fun clear() {
            values.clear()
        }

        override fun remove(key: String) {
            values.remove(key)
        }

        override fun hasKey(key: String): Boolean =
            values.containsKey(key)

        override fun putInt(key: String, value: Int) {
            values[key] = value
        }

        override fun getInt(key: String, defaultValue: Int): Int =
            getIntOrNull(key) ?: defaultValue

        override fun getIntOrNull(key: String): Int? =
            values[key] as? Int

        override fun putLong(key: String, value: Long) {
            values[key] = value
        }

        override fun getLong(key: String, defaultValue: Long): Long =
            getLongOrNull(key) ?: defaultValue

        override fun getLongOrNull(key: String): Long? =
            values[key] as? Long

        override fun putString(key: String, value: String) {
            values[key] = value
        }

        override fun getString(key: String, defaultValue: String): String =
            getStringOrNull(key) ?: defaultValue

        override fun getStringOrNull(key: String): String? =
            values[key] as? String

        override fun putFloat(key: String, value: Float) {
            values[key] = value
        }

        override fun getFloat(key: String, defaultValue: Float): Float =
            getFloatOrNull(key) ?: defaultValue

        override fun getFloatOrNull(key: String): Float? =
            values[key] as? Float

        override fun putDouble(key: String, value: Double) {
            values[key] = value
        }

        override fun getDouble(key: String, defaultValue: Double): Double =
            getDoubleOrNull(key) ?: defaultValue

        override fun getDoubleOrNull(key: String): Double? =
            values[key] as? Double

        override fun putBoolean(key: String, value: Boolean) {
            values[key] = value
        }

        override fun getBoolean(key: String, defaultValue: Boolean): Boolean =
            getBooleanOrNull(key) ?: defaultValue

        override fun getBooleanOrNull(key: String): Boolean? =
            values[key] as? Boolean
    }
}
