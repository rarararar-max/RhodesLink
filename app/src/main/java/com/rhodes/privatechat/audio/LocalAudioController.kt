package com.rhodes.privatechat.audio

import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.media.AudioAttributes
import android.media.AudioFocusRequest
import android.media.AudioFormat
import android.media.AudioManager
import android.media.AudioRecord
import android.media.MediaPlayer
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import kotlin.concurrent.thread

class LocalAudioController(private val context: Context) {
    companion object {
        const val MAX_RECORDING_MS = 120_000L
        private const val MAX_RECORDING_BYTES = 4_000_000
    }
    private var recorder: AudioRecord? = null
    private var player: MediaPlayer? = null
    private var recordingFile: File? = null
    private var startedAt: Long = 0L
    @Volatile private var isRecording = false
    private var recordingThread: Thread? = null
    private var pcmFile: File? = null
    @Volatile private var currentRecordingLevel = 0f
    @Volatile private var currentRecordingBytes = 0
    @Volatile private var lastSpeechAt = 0L
    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var audioFocusRequest: AudioFocusRequest? = null
    private var playbackCompletion: ((Boolean) -> Unit)? = null

    @SuppressLint("MissingPermission")
    fun startRecording(): Boolean {
        if (context.checkSelfPermission(android.Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) return false
        if (isRecording) stopRecordingInternal()
        stopPlayback()
        val file = File(File(context.filesDir, "voice").apply { mkdirs() }, "voice_${System.currentTimeMillis()}.pcm")
        val minBufferSize = AudioRecord.getMinBufferSize(RECORD_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT)
        if (minBufferSize <= 0) return false
        return runCatching {
            val bufferSize = minBufferSize.coerceAtLeast(RECORD_SAMPLE_RATE / 2)
            val audioRecord = AudioRecord(android.media.MediaRecorder.AudioSource.MIC, RECORD_SAMPLE_RATE, AudioFormat.CHANNEL_IN_MONO, AudioFormat.ENCODING_PCM_16BIT, bufferSize)
            if (audioRecord.state != AudioRecord.STATE_INITIALIZED) {
                audioRecord.release()
                error("AudioRecord init failed")
            }
            file.delete()
            currentRecordingLevel = 0f
            currentRecordingBytes = 0
            recorder = audioRecord
            recordingFile = file
            pcmFile = file
            startedAt = System.currentTimeMillis()
            lastSpeechAt = startedAt
            isRecording = true
            audioRecord.startRecording()
            recordingThread = thread(name = "rhodes-audio-record") {
                FileOutputStream(file).use { output ->
                    val buffer = ByteArray(bufferSize)
                    while (isRecording) {
                        val read = audioRecord.read(buffer, 0, buffer.size)
                        if (read > 0) {
                            val average = averageAbsPcm16(buffer, read)
                            currentRecordingLevel = (average / 4000f).coerceIn(0f, 1f)
                            currentRecordingBytes += read
                            if (average > RECORD_SPEECH_THRESHOLD) lastSpeechAt = System.currentTimeMillis()
                            if (currentRecordingBytes <= MAX_RECORDING_BYTES && System.currentTimeMillis() - startedAt <= MAX_RECORDING_MS) {
                                output.write(buffer, 0, read)
                            } else {
                                isRecording = false
                            }
                        }
                    }
                }
            }
            true
        }.getOrElse {
            stopRecordingInternal()
            recordingFile = null
            pcmFile?.delete()
            pcmFile = null
            false
        }
    }

    fun stopRecording(): RecordedAudio? {
        val pcm = recordingFile ?: return null
        val durationMs = (System.currentTimeMillis() - startedAt).coerceAtLeast(300L)
        stopRecordingInternal()
        recordingFile = null
        pcmFile = null
        if (!pcm.exists() || pcm.length() == 0L) {
            pcm.delete()
            return null
        }
        return runCatching {
            val wav = File(pcm.parentFile, pcm.nameWithoutExtension + ".wav")
            FileOutputStream(wav).use { output ->
                output.write(buildWavHeader(pcm.length().toInt(), RECORD_SAMPLE_RATE, 1, 16))
                FileInputStream(pcm).use { input -> input.copyTo(output) }
            }
            pcm.delete()
            RecordedAudio(wav.absolutePath, durationMs)
        }.getOrElse {
            pcm.delete()
            null
        }
    }

    fun readPcmFromWav(path: String): ByteArray {
        val bytes = File(path.removePrefix("file://")).readBytes()
        return if (bytes.size <= WAV_HEADER_SIZE) ByteArray(0) else bytes.copyOfRange(WAV_HEADER_SIZE, bytes.size)
    }

    fun saveTtsAudio(bytes: ByteArray, extension: String = "mp3"): RecordedAudio? {
        if (bytes.isEmpty()) return null
        val file = File(File(context.filesDir, "voice").apply { mkdirs() }, "tts_${System.currentTimeMillis()}.$extension")
        file.writeBytes(bytes)
        return RecordedAudio(file.absolutePath, (bytes.size / 16L).coerceIn(800L, 120_000L))
    }

    fun deleteAudio(path: String) {
        runCatching { File(path.removePrefix("file://")).delete() }
    }

    fun play(path: String, onComplete: ((Boolean) -> Unit)? = null) {
        stopPlayback()
        val file = File(path.removePrefix("file://"))
        runCatching {
            if (!requestPlaybackFocus()) error("无法获取音频播放焦点")
            playbackCompletion = onComplete
            player = MediaPlayer().apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
                } else {
                    @Suppress("DEPRECATION") setAudioStreamType(AudioManager.STREAM_MUSIC)
                }
                setDataSource(file.absolutePath)
                prepareAsync()
                setOnPreparedListener { it.start() }
                setOnCompletionListener { finishPlayback(true) }
                setOnErrorListener { _, _, _ -> finishPlayback(false); true }
            }
        }.getOrElse { finishPlayback(false) }
    }

    fun stopPlayback() {
        val callback = playbackCompletion
        playbackCompletion = null
        runCatching { player?.stop() }
        player?.release()
        player = null
        abandonPlaybackFocus()
        callback?.invoke(false)
    }

    private fun finishPlayback(success: Boolean) {
        val callback = playbackCompletion
        playbackCompletion = null
        stopPlayback()
        callback?.invoke(success)
    }

    /** Releases recording, playback, audio focus, and transient in-memory buffers on screen exit. */
    fun release() {
        stopRecordingInternal()
        recordingFile?.delete()
        pcmFile?.delete()
        recordingFile = null
        currentRecordingLevel = 0f
        currentRecordingBytes = 0
        stopPlayback()
        setSpeakerEnabled(false)
    }

    fun setSpeakerEnabled(enabled: Boolean) {
        @Suppress("DEPRECATION") audioManager.isSpeakerphoneOn = enabled
        audioManager.mode = if (enabled) AudioManager.MODE_IN_COMMUNICATION else AudioManager.MODE_NORMAL
    }

    fun hasRecordingBeenSilent(silenceMs: Long = 1000L): Boolean {
        if (!isRecording) return true
        return System.currentTimeMillis() - lastSpeechAt >= silenceMs
    }

    fun hasReachedRecordingLimit(): Boolean =
        currentRecordingBytes >= MAX_RECORDING_BYTES || (startedAt > 0L && System.currentTimeMillis() - startedAt >= MAX_RECORDING_MS)

    fun getCurrentRecordingLevel(): Float = currentRecordingLevel

    fun getRecordingDebugInfo(): String = "recording=$isRecording level=${"%.2f".format(currentRecordingLevel)} bytes=$currentRecordingBytes lastSpeechAgoMs=${System.currentTimeMillis() - lastSpeechAt}"

    private fun stopRecordingInternal() {
        isRecording = false
        runCatching { recorder?.stop() }
        recordingThread?.join(500L)
        recordingThread = null
        recorder?.release()
        recorder = null
    }

    private fun requestPlaybackFocus(): Boolean = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
        val request = AudioFocusRequest.Builder(AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK)
            .setAudioAttributes(AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_MEDIA).setContentType(AudioAttributes.CONTENT_TYPE_SPEECH).build())
            .setOnAudioFocusChangeListener { change ->
                if (change < AudioManager.AUDIOFOCUS_GAIN) finishPlayback(false)
            }
            .build()
        audioFocusRequest = request
        audioManager.requestAudioFocus(request) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    } else {
        @Suppress("DEPRECATION") audioManager.requestAudioFocus(null, AudioManager.STREAM_MUSIC, AudioManager.AUDIOFOCUS_GAIN_TRANSIENT_MAY_DUCK) == AudioManager.AUDIOFOCUS_REQUEST_GRANTED
    }

    private fun abandonPlaybackFocus() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            audioFocusRequest?.let { audioManager.abandonAudioFocusRequest(it) }
            audioFocusRequest = null
        } else {
            @Suppress("DEPRECATION") audioManager.abandonAudioFocus(null)
        }
    }

    private fun buildWavHeader(pcmSize: Int, sampleRate: Int, channels: Int, bitsPerSample: Int): ByteArray {
        val byteRate = sampleRate * channels * bitsPerSample / 8
        val blockAlign = channels * bitsPerSample / 8
        val output = ByteArray(WAV_HEADER_SIZE)
        fun writeAscii(offset: Int, value: String) { value.forEachIndexed { index, char -> output[offset + index] = char.code.toByte() } }
        fun writeIntLe(offset: Int, value: Int) { output[offset] = (value and 0xff).toByte(); output[offset + 1] = ((value shr 8) and 0xff).toByte(); output[offset + 2] = ((value shr 16) and 0xff).toByte(); output[offset + 3] = ((value shr 24) and 0xff).toByte() }
        fun writeShortLe(offset: Int, value: Int) { output[offset] = (value and 0xff).toByte(); output[offset + 1] = ((value shr 8) and 0xff).toByte() }
        writeAscii(0, "RIFF"); writeIntLe(4, 36 + pcmSize); writeAscii(8, "WAVE"); writeAscii(12, "fmt "); writeIntLe(16, 16)
        writeShortLe(20, 1); writeShortLe(22, channels); writeIntLe(24, sampleRate); writeIntLe(28, byteRate); writeShortLe(32, blockAlign); writeShortLe(34, bitsPerSample)
        writeAscii(36, "data"); writeIntLe(40, pcmSize)
        return output
    }

    private fun averageAbsPcm16(bytes: ByteArray, length: Int = bytes.size): Int {
        if (length < 2) return 0
        var sum = 0L
        var count = 0
        var index = 0
        while (index + 1 < length) {
            val sample = ((bytes[index + 1].toInt() shl 8) or (bytes[index].toInt() and 0xff)).toShort().toInt()
            sum += kotlin.math.abs(sample)
            count++
            index += 2
        }
        return if (count == 0) 0 else (sum / count).toInt()
    }
}

private const val RECORD_SAMPLE_RATE = 16_000
private const val WAV_HEADER_SIZE = 44
private const val RECORD_SPEECH_THRESHOLD = 550

data class RecordedAudio(val path: String, val durationMs: Long)
