package com.atools.app.ui.countdown

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Switch
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Snackbar
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.atools.app.R
import kotlinx.coroutines.delay

@Composable
fun CountdownScreen(
    modifier: Modifier = Modifier,
    viewModel: CountdownViewModel = viewModel(),
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current

    uiState.messageRes?.let { resId ->
        LaunchedEffect(resId) {
            delay(3000L)
            viewModel.onMessageShown()
        }
        Snackbar(modifier = Modifier.padding(8.dp)) {
            Text(text = context.getString(resId))
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = context.getString(R.string.countdown_title),
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            val statusText = when (uiState.ttsStatus) {
                TtsStatus.INITIALIZING -> context.getString(R.string.tts_initializing)
                TtsStatus.READY -> context.getString(R.string.tts_ready)
                TtsStatus.UNAVAILABLE -> context.getString(R.string.tts_unavailable_hint)
            }
            Text(
                text = statusText,
                style = MaterialTheme.typography.bodySmall,
                color = if (uiState.ttsStatus == TtsStatus.UNAVAILABLE) {
                    MaterialTheme.colorScheme.error
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                modifier = Modifier.weight(1f),
            )
            OutlinedButton(onClick = viewModel::testVoice) {
                Text(context.getString(R.string.tts_test))
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(),
        ) {
            Column(
                modifier = Modifier.padding(16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = context.getString(R.string.label_infinite),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(
                        checked = uiState.isInfinite,
                        onCheckedChange = viewModel::onInfiniteChange,
                        enabled = !uiState.isRunning,
                    )
                }
                OutlinedTextField(
                    value = uiState.repeatCount,
                    onValueChange = viewModel::onRepeatCountChange,
                    label = { Text(context.getString(R.string.label_repeat_count)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !uiState.isRunning && !uiState.isInfinite,
                    modifier = Modifier.fillMaxWidth(),
                )
                OutlinedTextField(
                    value = uiState.workSeconds,
                    onValueChange = viewModel::onWorkSecondsChange,
                    label = { Text(context.getString(R.string.label_work_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !uiState.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    listOf("30", "60", "90", "120").forEach { seconds ->
                        FilterChip(
                            selected = uiState.workSeconds == seconds,
                            onClick = { viewModel.onWorkSecondsChange(seconds) },
                            label = { Text("$seconds s") },
                            enabled = !uiState.isRunning,
                            modifier = Modifier.weight(1f),
                        )
                    }
                }
                OutlinedTextField(
                    value = uiState.restSeconds,
                    onValueChange = viewModel::onRestSecondsChange,
                    label = { Text(context.getString(R.string.label_rest_seconds)) },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    enabled = !uiState.isRunning,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }

        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.primaryContainer,
            ),
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                val phaseLabel = when (uiState.phase) {
                    CountdownPhase.IDLE -> context.getString(R.string.phase_idle)
                    CountdownPhase.WORK -> context.getString(R.string.phase_work)
                    CountdownPhase.REST -> context.getString(R.string.phase_rest)
                    CountdownPhase.FINISHED -> context.getString(R.string.phase_finished)
                }
                Text(
                    text = phaseLabel,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                Text(
                    text = formatTime(uiState.remainingSeconds),
                    style = MaterialTheme.typography.displayLarge,
                    fontWeight = FontWeight.Bold,
                    fontSize = 64.sp,
                    textAlign = TextAlign.Center,
                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                )
                if (uiState.currentRound > 0) {
                    val roundText = if (uiState.isInfinite) {
                        context.getString(R.string.round_info_infinite, uiState.currentRound)
                    } else {
                        context.getString(
                            R.string.round_info,
                            uiState.currentRound,
                            uiState.repeatCount.toIntOrNull() ?: uiState.currentRound,
                        )
                    }
                    Text(
                        text = roundText,
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onPrimaryContainer,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Button(
                onClick = viewModel::start,
                enabled = !uiState.isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(context.getString(R.string.start))
            }
            OutlinedButton(
                onClick = viewModel::togglePause,
                enabled = uiState.isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(
                    context.getString(
                        if (uiState.isPaused) R.string.resume else R.string.pause,
                    ),
                )
            }
            OutlinedButton(
                onClick = viewModel::stopAndReset,
                enabled = uiState.isRunning,
                modifier = Modifier.weight(1f),
            ) {
                Text(context.getString(R.string.stop_reset))
            }
        }

        Spacer(modifier = Modifier.height(8.dp))
    }
}

private fun formatTime(totalSeconds: Int): String {
    val minutes = totalSeconds / 60
    val seconds = totalSeconds % 60
    return "%02d:%02d".format(minutes, seconds)
}
