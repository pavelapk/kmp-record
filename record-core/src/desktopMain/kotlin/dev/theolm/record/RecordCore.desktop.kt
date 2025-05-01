@file:Suppress("MatchingDeclarationName")

package dev.theolm.record

import dev.theolm.record.config.OutputFormat
import dev.theolm.record.config.RecordConfig
import dev.theolm.record.error.NoOutputFileException
import dev.theolm.record.error.PermissionMissingException
import dev.theolm.record.error.RecordFailException
import java.io.File
import java.util.concurrent.atomic.AtomicReference
import javax.sound.sampled.AudioFileFormat
import javax.sound.sampled.AudioFormat
import javax.sound.sampled.AudioInputStream
import javax.sound.sampled.AudioSystem
import javax.sound.sampled.DataLine
import javax.sound.sampled.LineUnavailableException
import javax.sound.sampled.TargetDataLine
import kotlin.coroutines.cancellation.CancellationException
import kotlinx.coroutines.CoroutineExceptionHandler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking

@Suppress("TooGenericExceptionCaught")
internal actual object RecordCore {
    private const val BITS_PER_SAMPLE = 16
    private const val NUM_CHANNELS = 1
    private const val IS_SIGNED = true
    private const val IS_BIG_ENDIAN = false // WAV files are typically little-endian

    // --- Constants for messages ---
    private const val MSG_ERROR_AUDIO_WRITE = "Error during audio writing: "
    private const val MSG_ALREADY_RECORDING = "Already recording."
    private const val MSG_NOT_RECORDING = "Not recording."
    private const val MSG_CANCELLING_JOB = "Recording job cancelled."
    private const val MSG_STOPPING_RECORDING = "Stopping recording requested"
    private const val ERR_MSG_LINE_UNAVAILABLE = "Audio line unavailable: "
    private const val ERR_MSG_RECORDING_FAILED = "Recording failed: "
    private const val ERR_MSG_START_CANCELLED = "Recording start cancelled."
    private const val ERR_MSG_MKDIRS_FAILED = "Failed to create output directory."
    private const val ERR_MSG_NOT_RECORDING_NO_FILE = "Not recording and no output file available."
    private const val ERR_MSG_OUTPUT_FILE_NOT_SET = "Output file not set."
    private const val ERR_MSG_LINE_IN_USE = "Audio line is already in use."
    private const val ERR_MSG_MP4_NOT_IMPLEMENTED = "MPEG-4/AAC not implemented yet on desktop."

    private val lineRef = AtomicReference<TargetDataLine>(null)
    private val outputFileRef = AtomicReference<File?>(null)
    private var recordingJob: kotlinx.coroutines.Job? = null
    private val exceptionHandler = CoroutineExceptionHandler { _, throwable ->
        // Consider using a proper logger
        System.err.println(MSG_ERROR_AUDIO_WRITE + throwable.message)
        recordingState = RecordingState.IDLE
        closeLine() // Ensure line is closed on error
    }
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO + exceptionHandler)

    @Volatile
    private var recordingState: RecordingState = RecordingState.IDLE

    @Throws(RecordFailException::class)
    actual fun startRecording(config: RecordConfig) {
        if (isRecording()) {
            println(MSG_ALREADY_RECORDING)
            return
        }

        runCatching {
            val newOutputFile = File(config.getOutput())
            val parentDir = newOutputFile.parentFile
            if (parentDir != null && !parentDir.exists()) {
                if (!parentDir.mkdirs()) {
                    throw RecordFailException(ERR_MSG_MKDIRS_FAILED + parentDir.absolutePath)
                }
            }
            outputFileRef.set(newOutputFile)

            when (config.outputFormat) {
                OutputFormat.WAV -> startWavRecording(config)
                OutputFormat.MPEG_4 -> throw RecordFailException(ERR_MSG_MP4_NOT_IMPLEMENTED)
            }
        }.onFailure { throwable ->
            recordingState = RecordingState.IDLE
            outputFileRef.set(null) // Clear output file on failure
            closeLine() // Ensure line is closed if setup failed
            when (throwable) {
                is LineUnavailableException -> {
                    if (throwable.message?.contains("permission", ignoreCase = true) == true) {
                        throw PermissionMissingException()
                    } else {
                        throw RecordFailException(
                            ERR_MSG_LINE_UNAVAILABLE + throwable.message,
                            throwable
                        )
                    }
                }

                is RecordFailException -> throw throwable // Re-throw specific known exceptions
                is CancellationException -> { // Handle potential cancellation during setup
                    println(ERR_MSG_START_CANCELLED)
                    throw throwable
                }

                else -> throw RecordFailException(
                    ERR_MSG_RECORDING_FAILED + throwable.message,
                    throwable
                )
            }
        }
    }

    @Throws(NoOutputFileException::class)
    actual fun stopRecording(config: RecordConfig): String {
        // Simplified check: if not recording, job should be null or finishing.
        if (!isRecording()) {
            println(MSG_NOT_RECORDING)
            // Return current output file path if available, or throw if none exists
            return outputFileRef.get()?.absolutePath
                ?: throw NoOutputFileException(ERR_MSG_NOT_RECORDING_NO_FILE)
        }

        recordingState = RecordingState.IDLE

        // Close the audio line first to allow the writing coroutine to finish
        closeLine()

        // Cancel the recording job and wait for it to complete
        // runBlocking is used here to ensure stopRecording is synchronous
        runBlocking {
            recordingJob?.cancel(MSG_STOPPING_RECORDING)
            recordingJob?.join()
        }
        recordingJob = null // Clear the job reference

        return outputFileRef.get()?.absolutePath ?: throw NoOutputFileException()
    }

    actual fun isRecording(): Boolean = recordingState == RecordingState.RECORDING

    private fun startWavRecording(config: RecordConfig) {
        val format = AudioFormat(
            config.sampleRate.toFloat(),
            BITS_PER_SAMPLE,
            NUM_CHANNELS,
            IS_SIGNED,
            IS_BIG_ENDIAN
        )

        val info = DataLine.Info(TargetDataLine::class.java, format)
        val newLine = AudioSystem.getLine(info) as TargetDataLine

        // Use AtomicReference for safer handling across threads
        if (!lineRef.compareAndSet(null, newLine)) {
            // Should not happen if isRecording() check works, but defensively close
            newLine.close()
            throw IllegalStateException(ERR_MSG_LINE_IN_USE)
        }

        newLine.open(format)
        newLine.start()

        recordingJob = scope.launch {
            val currentLine = lineRef.get() // Get the line for this job
            val currentOutputFile = outputFileRef.get()
                ?: throw NoOutputFileException(ERR_MSG_OUTPUT_FILE_NOT_SET)

            // Ensure AudioInputStream is closed properly
            try {
                AudioInputStream(currentLine).use { audioInputStream ->
                    recordingState = RecordingState.RECORDING
                    // This write blocks until the stream (line) is closed or cancelled
                    AudioSystem.write(
                        audioInputStream,
                        AudioFileFormat.Type.WAVE,
                        currentOutputFile
                    )
                }
            } catch (e: CancellationException) {
                println(MSG_CANCELLING_JOB)
                // Cancellation is expected on stop
            } finally { // Other exceptions are handled by the CoroutineExceptionHandler
                // Line is closed by stopRecording before join is called.
                recordingState = RecordingState.IDLE
            }
        }
    }

    private fun closeLine() {
        lineRef.getAndSet(null)?.use { // Safely get, set to null, and close
            it.stop()
            it.close() // Ensure close is called
        }
    }
}
