package maryino.district.carinspector.obd.domain.model.session

/**
 * Fine-grained progress step for a connection attempt.
 *
 * These steps are useful for logging and UI progress, but they do not expose
 * concrete socket, GATT, or Bluetooth APIs.
 */
enum class ObdConnectionStep {
    OpeningTransport,
    DiscoveringBleServices,
    SelectingBleProfile,
    OpeningTcpSocket,
    WaitingForElmPrompt,
    SendingElmHandshake,
    ValidatingElmResponse
}

