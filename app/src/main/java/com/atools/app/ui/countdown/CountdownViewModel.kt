package com.atools.app.ui.countdown

import android.app.Application
import android.content.Context
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

enum class CountdownPhase {
    IDLE,
    WORK,
    REST,
    FINISHED,
}

data class CountdownUiState(
    val repeatCount: String = "3",
    val workSeconds: String = "60",
    val restSeconds: String = "10",
    val phase: CountdownPhase = CountdownPhase.IDLE,
    val currentRound: Int = 0,
    val remainingSeconds: Int = 0,
    val isRunning: Boolean = false,
    val isPaused: Boolean = false,
    val ttsReady: Boolean = false,
    val messageRes: Int? = null,
)

/**
 * Wraps Android TextToSpeech with Chinese locale.
 */
class SpeechHelper(
    context: Context,
    onReady: (Boolean) -> Unit,
) {
    private var tts: TextToSpeech? = null
    private var ready = false

    init {
        tts = TextToSpeech(context.applicationContext) { status ->
            if (status == TextToSpeech.SUCCESS) {
                val result = tts?.setLanguage(Locale.CHINESE)
                ready = result != TextToSpeech.LANG_MISSING_DATA &&
                    result != TextToSpeech.LANG_NOT_SUPPORTED
            } else {
                ready = false
            }
            onReady(ready)
        }
    }

    fun speak(text: String) {
        if (ready) {
            tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "atools-countdown")
        }
    }

    fun shutdown() {
        tts?.shutdown()
        tts = null
    }
}

class CountdownViewModel(
    application: Application,
) : AndroidViewModel(application) {
    private val _uiState = MutableStateFlow(CountdownUiState())
    val uiState: StateFlow<CountdownUiState> = _uiState.asStateFlow()

    private val speech = SpeechHelper(application) { ready ->
        _uiState.update {
            it.copy(
                ttsReady = ready,
                messageRes = if (ready) R.string.tts_ready else R.string.tts_unavailable,
            )
        }
    }

    private var job: Job? = null

    fun onRepeatCountChange(value: String) {
        _uiState.update { it.copy(repeatCount = value) }
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

    fun start() {
        val state = _uiState.value
        val count = state.repeatCount.toIntOrNull()
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
