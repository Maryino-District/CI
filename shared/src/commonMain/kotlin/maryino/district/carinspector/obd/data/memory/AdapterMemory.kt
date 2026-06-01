package maryino.district.carinspector.obd.data.memory

import com.russhwolf.settings.Settings
import kotlin.time.Instant
import maryino.district.carinspector.obd.domain.model.adapter.AdapterFingerprint
import maryino.district.carinspector.obd.domain.model.transport.ObdTransportType

class AdapterMemory(
    private val settings: Settings
) {
    fun save(fingerprint: AdapterFingerprint) {
        settings.putString(KEY_TRANSPORT_TYPE, fingerprint.transportType.name)
        settings.putString(KEY_STABLE_ID, fingerprint.stableId)
        settings.putNullableString(KEY_DISPLAY_NAME, fingerprint.displayName)
        settings.putNullableString(KEY_BLE_PROFILE_ID, fingerprint.bleProfileId)
        settings.putNullableString(KEY_WIFI_HOST, fingerprint.wifiHost)
        settings.putNullableInt(KEY_WIFI_PORT, fingerprint.wifiPort)
        settings.putString(KEY_LAST_SUCCESSFUL_AT, fingerprint.lastSuccessfulAt.toString())
    }

    fun load(): AdapterFingerprint? {
        val transportType = settings.getStringOrNull(KEY_TRANSPORT_TYPE)
            ?.let(::parseTransportType)
            ?: return null
        val stableId = settings.getStringOrNull(KEY_STABLE_ID) ?: return null
        val lastSuccessfulAt = settings.getStringOrNull(KEY_LAST_SUCCESSFUL_AT)
            ?.let(::parseInstant)
            ?: return null

        return AdapterFingerprint(
            transportType = transportType,
            stableId = stableId,
            displayName = settings.getStringOrNull(KEY_DISPLAY_NAME),
            bleProfileId = settings.getStringOrNull(KEY_BLE_PROFILE_ID),
            wifiHost = settings.getStringOrNull(KEY_WIFI_HOST),
            wifiPort = settings.getIntOrNull(KEY_WIFI_PORT),
            lastSuccessfulAt = lastSuccessfulAt
        )
    }

    fun clear() {
        KEYS.forEach(settings::remove)
    }

    private fun parseTransportType(value: String): ObdTransportType? =
        runCatching { ObdTransportType.valueOf(value) }.getOrNull()

    private fun parseInstant(value: String): Instant? =
        runCatching { Instant.parse(value) }.getOrNull()

    private fun Settings.putNullableString(key: String, value: String?) {
        if (value == null) {
            remove(key)
        } else {
            putString(key, value)
        }
    }

    private fun Settings.putNullableInt(key: String, value: Int?) {
        if (value == null) {
            remove(key)
        } else {
            putInt(key, value)
        }
    }

    private companion object {
        const val KEY_TRANSPORT_TYPE = "adapter_memory.transport_type"
        const val KEY_STABLE_ID = "adapter_memory.stable_id"
        const val KEY_DISPLAY_NAME = "adapter_memory.display_name"
        const val KEY_BLE_PROFILE_ID = "adapter_memory.ble_profile_id"
        const val KEY_WIFI_HOST = "adapter_memory.wifi_host"
        const val KEY_WIFI_PORT = "adapter_memory.wifi_port"
        const val KEY_LAST_SUCCESSFUL_AT = "adapter_memory.last_successful_at"

        val KEYS = listOf(
            KEY_TRANSPORT_TYPE,
            KEY_STABLE_ID,
            KEY_DISPLAY_NAME,
            KEY_BLE_PROFILE_ID,
            KEY_WIFI_HOST,
            KEY_WIFI_PORT,
            KEY_LAST_SUCCESSFUL_AT
        )
    }
}
