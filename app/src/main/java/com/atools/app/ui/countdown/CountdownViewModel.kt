package com.atools.app.ui.countdown

import android.app.Application
import android.content.Context
import android.os.Bundle
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.atools.app.R
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

enum class CountdownPhase {
    IDLE,
    WORK,
    REST,
    FINISHED,
}

enum class TtsStatus {
    INITIALIZING,
    READY,
    UNAVAILABLE,
}

data class CountdownUiState(
    val repeatCount: String = "3",
    val isInfinite: Boolean = true,
    val workSeconds: String = "30",
    val restSeconds: String = "10",
    val phase: CountdownPhase = CountdownPhase.IDLE,
    val currentRound: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val ttsStatus: TtsStatus = TtsStatus.INITIALIZING,
    val messageRes: Int? = null,
)

/**
 * Wraps Android TextToSpeech with Chinese locale.
 * On Android 11+ the app must declare a queries entry for TTS_SERVICE, otherwise
 * the engine fails to bind and every speak() is silently dropped.
 */
class SpeechHelper(
    context: Context,
    onStatus: (TtsStatus) -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var ready = false
    private var pendingText: String? = null
    private val utteranceId = AtomicInteger(0)

    init {
        val appContext = context.applicationContext
        try {
            tts = TextToSpeech(appContext) { status ->
                if (status != TextToSpeech.SUCCESS) {
                    ready = false
                    onStatus(TtsStatus.UNAVAILABLE)
                    return@TextToSpeech
                }
                val engine = tts
                if (engine == null) {
                    ready = false
                    onStatus(TtsStatus.UNAVAILABLE)
                    return@TextToSpeech
                }
                var result = engine.setLanguage(Locale.SIMPLIFIED_CHINESE)
                if (isUnsupported(result)) {
                    result = engine.setLanguage(Locale.CHINESE)
                }
                ready = !isUnsupported(result)
                if (ready) {
                    engine.setSpeechRate(1.0f)
                    engine.setPitch(1.0f)
                    onStatus(TtsStatus.READY)
                    pendingText?.let { text ->
                        speakInternal(engine, text)
                        pendingText = null
                    }
                } else {
                    onStatus(TtsStatus.UNAVAILABLE)
                }
            }
        } catch (_: Exception) {
            ready = false
            onStatus(TtsStatus.UNAVAILABLE)
        }
    }

    private fun isUnsupported(result: Int): Boolean =
        result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED

    private fun speakInternal(engine: TextToSpeech, text: String): Int {
        val params = Bundle().apply {
            putFloat(TextToSpeech.Engine.KEY_PARAM_VOLUME, 1.0f)
        }
        return engine.speak(
            text,
            TextToSpeech.QUEUE_FLUSH,
            params,
            "atools-tts-${utteranceId.incrementAndGet()}",
        )
    }

    fun speak(text: String) {
        val engine = tts
        if (!ready || engine == null) {
            // Countdown may start before TTS init finishes; replay once ready.
            pendingText = text
            return
        }
        if (speakInternal(engine, text) != TextToSpeech.SUCCESS) {
            pendingText = text
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
        ready = false
    }
}

class CountdownViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CountdownUiState())
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    private val speech = SpeechHelper(application) { status ->
        _uiState.update { it.copy(ttsStatus = status) }
    }

    private var job: Job? = null

    fun onRepeatCountChange(value: String) {
        _uiState.update { it.copy(repeatCount = value) }
    }

    fun onInfiniteChange(value: Boolean) {
        _uiState.update { it.copy(isInfinite = value) }
    }

    fun onWorkSecondsChange(value: String) {
        _uiState.update { it.copy(workSeconds = value) }
    }

    fun onRestSecondsChange(value: String) {
        _uiState.update { it.copy(restSeconds = value) }
    }

    fun onMessageShown() {
        _uiState.update { it.copy(messageRes = null) }
    }

    fun testVoice() {
        speech.speak(getApplication<Application>().getString(R.string.tts_test_text))
    }

    fun start() {
        val state = _uiState.value
        val count = if (state.isInfinite) Int.MAX_VALUE else state.repeatCount.toIntOrNull()
        val work = state.workSeconds.toIntOrNull()
        val rest = state.restSeconds.toIntOrNull()
        if (count == null || count <= 0 || work == null || work <= 0 || rest == null || rest <= 0) {
            _uiState.update { it.copy(messageRes = R.string.invalid_input) }
            return
        }

        job?.cancel()
        _uiState.update {
            it.copy(
                phase = CountdownPhase.WORK,
                currentRound = 1,
                remainingSeconds = work,
                isRunning = true,
                isPaused = false,
                messageRes = null,
            )
        }

        job = viewModelScope.launch {
            for (round in 1..count) {
                runPhase(seconds = work, isWork = true, round = round)
                if (round == count) break
                runPhase(seconds = rest, isWork = false, round = round)
                _uiState.update { it.copy(currentRound = round + 1) }
            }
            _uiState.update {
                it.copy(
                    phase = CountdownPhase.FINISHED,
                    isRunning = false,
                    isPaused = false,
                    remainingSeconds = 0,
                )
            }
        }
    }

    /**
     * Runs one countdown phase. Pausing is handled by waiting until isPaused is false.
     */
    private suspend fun runPhase(seconds: Int, isWork: Boolean, round: Int) {
        _uiState.update {
            it.copy(
                phase = if (isWork) CountdownPhase.WORK else CountdownPhase.REST,
                remainingSeconds = seconds,
            )
        }
        for (remaining in seconds downTo 1) {
            while (_uiState.value.isPaused) {
                delay(PAUSE_POLL_MS)
            }
            _uiState.update { it.copy(remainingSeconds = remaining) }
            if (remaining <= COUNTDOWN_VOICE_THRESHOLD) {
                speech.speak(remaining.toString())
            }
            delay(TICK_MS)
        }
        if (isWork) {
            val message = getApplication<Application>().getString(
                R.string.announce_round_complete,
                round,
                seconds,
            )
            speech.speak(message)
        }
    }

    fun togglePause() {
        _uiState.update { it.copy(isPaused = !it.isPaused) }
    }

    fun stopAndReset() {
        job?.cancel()
        job = null
        _uiState.update {
            it.copy(
                phase = CountdownPhase.IDLE,
                currentRound = 0,
                remainingSeconds = 0,
                isRunning = false,
                isPaused = false,
            )
        }
    }

    override fun onCleared() {
        super.onCleared()
        job?.cancel()
        speech.shutdown()
    }

    private companion object {
        const val TICK_MS = 1000L
        const val PAUSE_POLL_MS = 100L
        const val COUNTDOWN_VOICE_THRESHOLD = 10
    }
}
