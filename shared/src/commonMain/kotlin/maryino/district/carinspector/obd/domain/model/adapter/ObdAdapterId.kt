package maryino.district.carinspector.obd.domain.model.adapter

/**
 * Stable domain identifier for a discovered or remembered OBD adapter.
 *
 * The value is produced by the data layer from transport-specific identifiers,
 * but it must not expose platform handles such as BluetoothDevice or CBPeripheral.
 */
@JvmInline
value class ObdAdapterId(val value: String)

