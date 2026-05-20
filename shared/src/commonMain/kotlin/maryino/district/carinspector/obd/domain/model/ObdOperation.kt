package maryino.district.carinspector.obd.domain.model

enum class ObdOperation {
    Scan,
    ResolveBlePeripheral,
    OpenTransport,
    DiscoverBleServices,
    SubscribeBleNotifications,
    TcpConnect,
    ElmReset,
    ElmCommand,
    ElmHandshake,
    Disconnect
}
