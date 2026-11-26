package com.jarvis.assistant.data

enum class AssistantState(val label: String) {
    OFFLINE("OFFLINE"),
    STANDBY("STANDBY"),
    LISTENING("LISTENING"),
    THINKING("THINKING"),
    SPEAKING("SPEAKING")
}

enum class ListeningMode(val label: String) {
    WAKE_PHRASE("Hey JARVIS Wake Phrase"),
    CONTINUOUS("Continuous Hands-Free")
}

enum class ActionType {
    OPEN_APP,
    LAUNCH_DIALER,
    OPEN_WEBSITE,
    START_NAVIGATION,
    SET_ALARM
}

data class PendingPhoneAction(
    val id: String,
    val type: ActionType,
    val target: String,
    val summary: String,
    val hour: Int? = null,
    val minute: Int? = null,
    val sensitive: Boolean = true
)

data class ChatTurn(
    val role: String,
    val text: String
)

data class JarvisUiSnapshot(
    val state: AssistantState = AssistantState.OFFLINE,
    val isServiceRunning: Boolean = false,
    val isMicrophoneActive: Boolean = false,
    val listeningMode: ListeningMode = ListeningMode.WAKE_PHRASE,
    val partialTranscript: String = "",
    val lastUserTranscript: String = "",
    val lastAssistantReply: String = "",
    val statusNotice: String = "",
    val pendingAction: PendingPhoneAction? = null,
    val audioLevel: Float = 0f
)
