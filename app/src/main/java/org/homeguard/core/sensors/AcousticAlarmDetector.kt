package org.homeguard.core.sensors

import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioFormat
import android.media.AudioRecord
import android.media.MediaRecorder
import android.util.Log
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlin.math.abs

class AcousticAlarmDetector(
    private val context: Context,
    private val onAlarmSoundDetected: () -> Unit
) {

    private val scope = CoroutineScope(Dispatchers.IO)
    private var listeningJob: Job? = null

    private val _isListening = MutableStateFlow(false)
    val isListening: StateFlow<Boolean> = _isListening.asStateFlow()

    private val sampleRate = 16000
    private val bufferSize = AudioRecord.getMinBufferSize(
        sampleRate,
        AudioFormat.CHANNEL_IN_MONO,
        AudioFormat.ENCODING_PCM_16BIT
    )

    fun startListening() {
        if (_isListening.value) return
        val hasPermission = ContextCompat.checkSelfPermission(
            context,
            android.Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED

        if (!hasPermission || bufferSize <= 0) {
            Log.i("AcousticDetector", "Audio permission not granted or mic unavailable. Ear standing by.")
            return
        }

        listeningJob = scope.launch {
            var audioRecord: AudioRecord? = null
            try {
                audioRecord = AudioRecord(
                    MediaRecorder.AudioSource.MIC,
                    sampleRate,
                    AudioFormat.CHANNEL_IN_MONO,
                    AudioFormat.ENCODING_PCM_16BIT,
                    bufferSize
                )

                if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                    return@launch
                }

                audioRecord.startRecording()
                _isListening.value = true

                val buffer = ShortArray(bufferSize)
                var consecutiveHighPitches = 0

                while (isActive) {
                    val read = audioRecord.read(buffer, 0, buffer.size)
                    if (read > 0) {
                        // Check for high-frequency smoke detector acoustic signature (zero-crossing rate & energy)
                        var zeroCrossings = 0
                        var totalEnergy = 0L

                        for (i in 0 until read - 1) {
                            totalEnergy += abs(buffer[i].toInt())
                            if ((buffer[i] > 0 && buffer[i + 1] <= 0) || (buffer[i] < 0 && buffer[i + 1] >= 0)) {
                                zeroCrossings++
                            }
                        }

                        val averageAmplitude = totalEnergy / read
                        // Smoke detectors emit ~3.1kHz - 3.4kHz pure tones at loud volume (>60dB)
                        // At 16kHz sample rate, 3200Hz produces ~6400 zero-crossings per sec (~40% of samples)
                        val zeroCrossingRate = zeroCrossings.toFloat() / read.toFloat()

                        if (averageAmplitude > 4500 && zeroCrossingRate in 0.32f..0.48f) {
                            consecutiveHighPitches++
                            if (consecutiveHighPitches >= 3) {
                                Log.w("AcousticDetector", "SMOKE ALARM FREQUENCY PATTERN DETECTED!")
                                onAlarmSoundDetected()
                                consecutiveHighPitches = 0
                            }
                        } else {
                            if (consecutiveHighPitches > 0) consecutiveHighPitches--
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e("AcousticDetector", "Error in acoustic listener: ${e.message}")
            } finally {
                try {
                    audioRecord?.stop()
                    audioRecord?.release()
                } catch (_: Exception) {}
                _isListening.value = false
            }
        }
    }

    fun stopListening() {
        listeningJob?.cancel()
        _isListening.value = false
    }
}
