package com.jarvis.assistant.service

import android.Manifest
import android.app.Notification
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.media.AudioManager
import android.os.Bundle
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.PowerManager
import android.speech.RecognitionListener
import android.speech.RecognizerIntent
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.jarvis.assistant.JarvisApplication
import com.jarvis.assistant.R
import com.jarvis.assistant.data.ApiOutcome
import com.jarvis.assistant.data.AssistantState
import com.jarvis.assistant.data.ChatTurn
import com.jarvis.assistant.data.JarvisApiClient
import com.jarvis.assistant.data.JarvisUiSnapshot
import com.jarvis.assistant.data.ListeningMode
import com.jarvis.assistant.data.PendingPhoneAction
import com.jarvis.assistant.data.SecureSettingsManager
import com.jarvis.assistant.ui.MainActivity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID

class JarvisVoiceService : Service(), TextToSpeech.OnInitListener {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val mainHandler = Handler(Looper.getMainLooper())

    private lateinit var settings: SecureSettingsManager
    private lateinit var apiClient: JarvisApiClient
    private lateinit var actionExecutor: PhoneActionExecutor
    private lateinit var audioManager: AudioManager

    private var wakeLock: PowerManager.WakeLock? = null
    private var speechRecognizer: SpeechRecognizer? = null
    private var tts: TextToSpeech? = null
    private var isTtsReady = false

    private var activeRequestJob: Job? = null
    private var isShuttingDown = false
    private var isRecognizerListening = false
    private var consecutiveErrorCount = 0
    private var activeConversationWindowUntilMs = 0L

    private val conversationHistory = mutableListOf<ChatTurn>()

    override fun onCreate() {
        super.onCreate()
        settings = SecureSettingsManager(this)
        apiClient = JarvisApiClient(settings)
        actionExecutor = PhoneActionExecutor(this)
        audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager

        acquirePartialWakeLock()
        tts = TextToSpeech(this, this)
        initSpeechRecognizer()

        _uiState.update {
            it.copy(
                isServiceRunning = true,
                listeningMode = settings.listeningMode,
                state = AssistantState.STANDBY,
                statusNotice = ""
            )
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_STOP -> {
                shutdownService()
                return START_NOT_STICKY
            }
            ACTION_CONFIRM_PENDING -> {
                confirmPendingAction()
                return START_STICKY
            }
            ACTION_CANCEL_PENDING -> {
                cancelPendingAction()
                return START_STICKY
            }
            ACTION_REFRESH_SETTINGS -> {
                _uiState.update { it.copy(listeningMode = settings.listeningMode) }
                return START_STICKY
            }
        }

        if (!hasMicrophonePermission()) {
            _uiState.update {
                it.copy(
                    state = AssistantState.OFFLINE,
                    isServiceRunning = false,
                    isMicrophoneActive = false,
                    statusNotice = "Microphone permission is required before JARVIS can listen."
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }

        startForegroundWithMicType()

        if (!SpeechRecognizer.isRecognitionAvailable(this)) {
            _uiState.update {
                it.copy(
                    state = AssistantState.OFFLINE,
                    isServiceRunning = false,
                    isMicrophoneActive = false,
                    statusNotice = "Speech recognition service is unavailable on this device. Install or enable Google Speech Services."
                )
            }
            stopSelf()
            return START_NOT_STICKY
        }

        if (_uiState.value.state == AssistantState.STANDBY && !isRecognizerListening) {
            activeConversationWindowUntilMs = System.currentTimeMillis() + CONVERSATION_WINDOW_MS
            scheduleListeningCycle(delayMs = 250L)
        }

        return START_STICKY
    }

    private fun startForegroundWithMicType() {
        val notification = buildNotification()
        ServiceCompat.startForeground(
            this,
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_MICROPHONE
        )
    }

    private fun buildNotification(): Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_SINGLE_TOP or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val openPendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val stopIntent = Intent(this, JarvisVoiceService::class.java).apply {
            action = ACTION_STOP
        }
        val stopPendingIntent = PendingIntent.getService(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, JarvisApplication.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_jarvis_mic)
            .setContentTitle(getString(R.string.notification_title))
            .setContentText("Hands-free mode: ${settings.listeningMode.label}")
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setContentIntent(openPendingIntent)
            .addAction(0, getString(R.string.action_turn_off), stopPendingIntent)
            .build()
    }

    private fun acquirePartialWakeLock() {
        val pm = getSystemService(Context.POWER_SERVICE) as PowerManager
        wakeLock = pm.newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "JARVIS::VoiceServiceWakeLock").apply {
            setReferenceCounted(false)
            acquire(15 * 60 * 1000L)
        }
    }

    private fun refreshWakeLock() {
        wakeLock?.let {
            if (!it.isHeld) {
                it.acquire(15 * 60 * 1000L)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val langResult = tts?.setLanguage(Locale.US)
            isTtsReady = langResult != TextToSpeech.LANG_MISSING_DATA &&
                langResult != TextToSpeech.LANG_NOT_SUPPORTED

            if (isTtsReady) {
                selectPreferredMaleUsVoice()
                tts?.setPitch(0.95f)
                tts?.setSpeechRate(settings.speechRate)
                tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                    override fun onStart(utteranceId: String?) {
                        mainHandler.post {
                            _uiState.update {
                                it.copy(
                                    state = AssistantState.SPEAKING,
                                    isMicrophoneActive = false
                                )
                            }
                        }
                    }

                    override fun onDone(utteranceId: String?) {
                        mainHandler.post {
                            if (!isShuttingDown) {
                                activeConversationWindowUntilMs =
                                    System.currentTimeMillis() + CONVERSATION_WINDOW_MS
                                // Wait briefly for speaker echo to decay before reopening microphone
                                scheduleListeningCycle(delayMs = 380L)
                            }
                        }
                    }

                    @Deprecated("Deprecated in Java")
                    override fun onError(utteranceId: String?) {
                        mainHandler.post {
                            if (!isShuttingDown) {
                                scheduleListeningCycle(delayMs = 500L)
                            }
                        }
                    }
                })
            }
        }
    }

    private fun selectPreferredMaleUsVoice() {
        val availableVoices = tts?.voices ?: return
        val usVoices = availableVoices.filter { it.locale == Locale.US }
        val maleVoice = usVoices.firstOrNull { voice ->
            val name = voice.name.lowercase(Locale.US)
            name.contains("male") ||
                name.contains("-tpd-") ||
                name.contains("-iom-") ||
                name.contains("-iol-") ||
                name.contains("-sfg-") ||
                name.contains("en-us-x-tpf")
        } ?: usVoices.firstOrNull { !it.isNetworkConnectionRequired }
            ?: usVoices.firstOrNull()

        if (maleVoice != null) {
            tts?.voice = maleVoice
        }
    }

    private fun initSpeechRecognizer() {
        mainHandler.post {
            speechRecognizer?.destroy()
            speechRecognizer = SpeechRecognizer.createSpeechRecognizer(this).apply {
                setRecognitionListener(recognitionListener)
            }
        }
    }

    private fun scheduleListeningCycle(delayMs: Long) {
        mainHandler.removeCallbacks(startListeningRunnable)
        if (!isShuttingDown) {
            mainHandler.postDelayed(startListeningRunnable, delayMs)
        }
    }

    private val startListeningRunnable = Runnable {
        startListeningNow()
    }

    private fun startListeningNow() {
        if (isShuttingDown || !hasMicrophonePermission()) return

        // Do not compete with active phone calls or ringtones
        if (audioManager.mode == AudioManager.MODE_IN_CALL ||
            audioManager.mode == AudioManager.MODE_RINGTONE ||
            audioManager.mode == AudioManager.MODE_IN_COMMUNICATION
        ) {
            _uiState.update {
                it.copy(
                    state = AssistantState.STANDBY,
                    isMicrophoneActive = false,
                    statusNotice = "Paused while phone audio or call is active."
                )
            }
            scheduleListeningCycle(delayMs = 2000L)
            return
        }

        // Never open microphone while JARVIS is speaking
        if (tts?.isSpeaking == true) {
            scheduleListeningCycle(delayMs = 450L)
            return
        }

        refreshWakeLock()

        val recognizerIntent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
            putExtra(RecognizerIntent.EXTRA_LANGUAGE, "en-US")
            putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE, "en-US")
            putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS, true)
            putExtra(RecognizerIntent.EXTRA_MAX_RESULTS, 1)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS, 1600L)
            putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS, 1200L)
        }

        try {
            if (speechRecognizer == null) {
                initSpeechRecognizer()
            }
            isRecognizerListening = true
            val isWakeWaiting = settings.listeningMode == ListeningMode.WAKE_PHRASE &&
                System.currentTimeMillis() > activeConversationWindowUntilMs

            _uiState.update {
                it.copy(
                    state = if (isWakeWaiting) AssistantState.STANDBY else AssistantState.LISTENING,
                    isMicrophoneActive = true,
                    partialTranscript = "",
                    statusNotice = ""
                )
            }
            speechRecognizer?.startListening(recognizerIntent)
        } catch (_: Exception) {
            isRecognizerListening = false
            _uiState.update {
                it.copy(
                    state = AssistantState.STANDBY,
                    isMicrophoneActive = false
                )
            }
            scheduleListeningCycle(delayMs = 1200L)
        }
    }

    private fun stopRecognizerNow() {
        mainHandler.removeCallbacks(startListeningRunnable)
        isRecognizerListening = false
        mainHandler.post {
            runCatching { speechRecognizer?.cancel() }
        }
        _uiState.update { it.copy(isMicrophoneActive = false, audioLevel = 0f) }
    }

    private val recognitionListener = object : RecognitionListener {
        override fun onReadyForSpeech(params: Bundle?) {
            consecutiveErrorCount = 0
        }

        override fun onBeginningOfSpeech() {
            val isWakeWaiting = settings.listeningMode == ListeningMode.WAKE_PHRASE &&
                System.currentTimeMillis() > activeConversationWindowUntilMs
            if (!isWakeWaiting) {
                _uiState.update { it.copy(state = AssistantState.LISTENING) }
            }
        }

        override fun onRmsChanged(rmsdB: Float) {
            val normalized = ((rmsdB + 2f) / 12f).coerceIn(0f, 1f)
            _uiState.update { it.copy(audioLevel = normalized) }
        }

        override fun onBufferReceived(buffer: ByteArray?) = Unit

        override fun onEndOfSpeech() {
            isRecognizerListening = false
            _uiState.update { it.copy(isMicrophoneActive = false, audioLevel = 0f) }
        }

        override fun onError(error: Int) {
            isRecognizerListening = false
            _uiState.update { it.copy(isMicrophoneActive = false, audioLevel = 0f) }

            if (isShuttingDown) return

            when (error) {
                SpeechRecognizer.ERROR_NO_MATCH,
                SpeechRecognizer.ERROR_SPEECH_TIMEOUT -> {
                    consecutiveErrorCount = 0
                    val isWakeWaiting = settings.listeningMode == ListeningMode.WAKE_PHRASE &&
                        System.currentTimeMillis() > activeConversationWindowUntilMs
                    _uiState.update {
                        it.copy(
                            state = if (isWakeWaiting) AssistantState.STANDBY else AssistantState.LISTENING
                        )
                    }
                    scheduleListeningCycle(delayMs = 350L)
                }
                SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS -> {
                    _uiState.update {
                        it.copy(
                            state = AssistantState.OFFLINE,
                            statusNotice = "Microphone permission was revoked."
                        )
                    }
                    shutdownService()
                }
                SpeechRecognizer.ERROR_RECOGNIZER_BUSY -> {
                    consecutiveErrorCount++
                    initSpeechRecognizer()
                    scheduleListeningCycle(delayMs = (600L * consecutiveErrorCount).coerceAtMost(3000L))
                }
                else -> {
                    consecutiveErrorCount++
                    val backoffMs = (700L * consecutiveErrorCount).coerceAtMost(4500L)
                    _uiState.update {
                        it.copy(
                            state = AssistantState.STANDBY,
                            statusNotice = if (error == SpeechRecognizer.ERROR_NETWORK || error == SpeechRecognizer.ERROR_NETWORK_TIMEOUT) {
                                "Speech recognition network unavailable; retrying..."
                            } else {
                                ""
                            }
                        )
                    }
                    scheduleListeningCycle(delayMs = backoffMs)
                }
            }
        }

        override fun onResults(results: Bundle?) {
            isRecognizerListening = false
            _uiState.update { it.copy(isMicrophoneActive = false, audioLevel = 0f) }

            val spokenText = results
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()

            if (spokenText.isEmpty()) {
                scheduleListeningCycle(delayMs = 300L)
                return
            }

            handleRecognizedUtterance(spokenText)
        }

        override fun onPartialResults(partialResults: Bundle?) {
            val partial = partialResults
                ?.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION)
                ?.firstOrNull()
                ?.trim()
                .orEmpty()
            if (partial.isNotEmpty()) {
                _uiState.update { it.copy(partialTranscript = partial) }
            }
        }

        override fun onEvent(eventType: Int, params: Bundle?) = Unit
    }

    private fun handleRecognizedUtterance(rawText: String) {
        val normalized = rawText.lowercase(Locale.US).trim()

        // Immediate voice shutdown command
        if (normalized == "jarvis turn off" ||
            normalized == "jarvis stop listening" ||
            normalized == "turn off jarvis"
        ) {
            speakAndThenStop("Going offline now.")
            return
        }

        // Handle pending sensitive action confirmation by voice
        val pending = _uiState.value.pendingAction
        if (pending != null) {
            when {
                normalized.contains("yes") || normalized.contains("confirm") ||
                    normalized.contains("go ahead") || normalized.contains("do it") -> {
                    confirmPendingAction()
                    return
                }
                normalized.contains("no") || normalized.contains("cancel") ||
                    normalized.contains("never mind") || normalized.contains("stop") -> {
                    cancelPendingAction()
                    return
                }
            }
        }

        // Check wake phrase if in WAKE_PHRASE mode and conversation window expired
        val processedText = if (settings.listeningMode == ListeningMode.WAKE_PHRASE &&
            System.currentTimeMillis() > activeConversationWindowUntilMs
        ) {
            val wakeMatch = WAKE_PHRASE_REGEX.find(rawText)
            if (wakeMatch == null) {
                // Ignore background chatter in STANDBY
                _uiState.update { it.copy(state = AssistantState.STANDBY, partialTranscript = "") }
                scheduleListeningCycle(delayMs = 250L)
                return
            }

            val stripped = rawText.replaceFirst(WAKE_PHRASE_REGEX, "").trim(' ', ',', '.', '?', '!')
            if (stripped.isEmpty()) {
                activeConversationWindowUntilMs = System.currentTimeMillis() + CONVERSATION_WINDOW_MS
                speakResponse("Yes, I'm listening.")
                return
            }
            stripped
        } else {
            rawText
        }

        activeConversationWindowUntilMs = System.currentTimeMillis() + CONVERSATION_WINDOW_MS
        sendToGeminiBackend(processedText)
    }

    private fun sendToGeminiBackend(userText: String) {
        stopRecognizerNow()
        _uiState.update {
            it.copy(
                state = AssistantState.THINKING,
                lastUserTranscript = userText,
                partialTranscript = "",
                statusNotice = ""
            )
        }

        activeRequestJob?.cancel()
        activeRequestJob = serviceScope.launch {
            val outcome = apiClient.sendTurn(
                userText = userText,
                history = conversationHistory.toList()
            )

            if (isShuttingDown) return@launch

            when (outcome) {
                is ApiOutcome.Success -> {
                    val reply = outcome.data.replyText
                    conversationHistory.add(ChatTurn("user", userText))
                    conversationHistory.add(ChatTurn("model", reply))
                    while (conversationHistory.size > 16) {
                        conversationHistory.removeAt(0)
                    }

                    val action = outcome.data.action
                    if (action != null) {
                        if (settings.requireActionConfirmation && action.sensitive) {
                            _uiState.update {
                                it.copy(
                                    pendingAction = action,
                                    lastAssistantReply = "$reply Please confirm: ${action.summary}."
                                )
                            }
                            speakResponse("$reply Should I proceed with ${action.summary}? Say yes to confirm or no to cancel.")
                        } else {
                            val result = actionExecutor.execute(action)
                            val fullSpeech = "$reply ${result.spokenFeedback}".trim()
                            _uiState.update { it.copy(lastAssistantReply = fullSpeech) }
                            speakResponse(fullSpeech)
                        }
                    } else {
                        _uiState.update { it.copy(lastAssistantReply = reply) }
                        speakResponse(reply)
                    }
                }
                is ApiOutcome.Failure -> {
                    _uiState.update {
                        it.copy(
                            lastAssistantReply = outcome.userMessage,
                            statusNotice = outcome.userMessage
                        )
                    }
                    speakResponse(outcome.userMessage)
                }
            }
        }
    }

    fun confirmPendingAction() {
        val action = _uiState.value.pendingAction ?: return
        _uiState.update { it.copy(pendingAction = null) }
        val result = actionExecutor.execute(action)
        _uiState.update { it.copy(lastAssistantReply = result.spokenFeedback) }
        speakResponse(result.spokenFeedback)
    }

    fun cancelPendingAction() {
        if (_uiState.value.pendingAction == null) return
        _uiState.update {
            it.copy(
                pendingAction = null,
                lastAssistantReply = "Action cancelled."
            )
        }
        speakResponse("Cancelled.")
    }

    private fun speakResponse(text: String) {
        stopRecognizerNow()
        if (!isTtsReady) {
            _uiState.update { it.copy(state = AssistantState.STANDBY) }
            scheduleListeningCycle(delayMs = 500L)
            return
        }

        _uiState.update { it.copy(state = AssistantState.SPEAKING, isMicrophoneActive = false) }
        tts?.setSpeechRate(settings.speechRate)
        val utteranceId = UUID.randomUUID().toString()
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, utteranceId)
    }

    private fun speakAndThenStop(text: String) {
        stopRecognizerNow()
        isShuttingDown = true
        if (isTtsReady) {
            tts?.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
                override fun onStart(utteranceId: String?) = Unit
                override fun onDone(utteranceId: String?) {
                    mainHandler.post { shutdownService() }
                }
                @Deprecated("Deprecated in Java")
                override fun onError(utteranceId: String?) {
                    mainHandler.post { shutdownService() }
                }
            })
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "jarvis_goodbye")
        } else {
            shutdownService()
        }
    }

    private fun shutdownService() {
        isShuttingDown = true
        activeRequestJob?.cancel()
        stopRecognizerNow()
        runCatching { tts?.stop() }
        _uiState.update {
            JarvisUiSnapshot(
                state = AssistantState.OFFLINE,
                isServiceRunning = false,
                isMicrophoneActive = false,
                listeningMode = settings.listeningMode
            )
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    private fun hasMicrophonePermission(): Boolean {
        return ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
    }

    override fun onDestroy() {
        isShuttingDown = true
        activeRequestJob?.cancel()
        mainHandler.removeCallbacksAndMessages(null)
        runCatching { speechRecognizer?.destroy() }
        speechRecognizer = null
        runCatching {
            tts?.stop()
            tts?.shutdown()
        }
        tts = null
        wakeLock?.let {
            if (it.isHeld) {
                runCatching { it.release() }
            }
        }
        wakeLock = null
        serviceScope.cancel()
        _uiState.update {
            it.copy(
                state = AssistantState.OFFLINE,
                isServiceRunning = false,
                isMicrophoneActive = false,
                audioLevel = 0f
            )
        }
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    companion object {
        const val NOTIFICATION_ID = 1001
        const val ACTION_START = "com.jarvis.assistant.action.START"
        const val ACTION_STOP = "com.jarvis.assistant.action.STOP"
        const val ACTION_CONFIRM_PENDING = "com.jarvis.assistant.action.CONFIRM_PENDING"
        const val ACTION_CANCEL_PENDING = "com.jarvis.assistant.action.CANCEL_PENDING"
        const val ACTION_REFRESH_SETTINGS = "com.jarvis.assistant.action.REFRESH_SETTINGS"

        private const val CONVERSATION_WINDOW_MS = 20_000L
        private val WAKE_PHRASE_REGEX = Regex("(?i)\\b(hey\\s+)?jarvis\\b[:,]?\\s*")

        private val _uiState = MutableStateFlow(JarvisUiSnapshot())
        val uiState: StateFlow<JarvisUiSnapshot> = _uiState.asStateFlow()
    }
}
