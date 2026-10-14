package dev.netvalve.payprobe

import java.io.BufferedWriter
import java.io.File
import java.io.FileWriter
import java.time.Instant

/** CSV layout shared by the app and the analysis script (docs/data and analysis/). */
object CsvFormat {
    const val HEADER = "iso_time,elapsed_ms,latency_ms,ok,http_code,label,note"

    fun isoTime(epochMs: Long): String = Instant.ofEpochMilli(epochMs).toString()

    /** Keep file names and the label column simple: letters, digits, underscore and dash. */
    fun safeLabel(label: String?): String {
        val cleaned = (label ?: "").trim().map { c -> if (c.isLetterOrDigit() || c == '_' || c == '-') c else '_' }
            .joinToString("").take(40)
        return if (cleaned.isEmpty()) "run" else cleaned
    }

    fun escape(field: String): String =
        if (field.any { it == ',' || it == '"' || it == '\n' || it == '\r' }) {
            "\"" + field.replace("\"", "\"\"") + "\""
        } else {
            field
        }

    fun row(s: ProbeSample, label: String): String =
        listOf(
            isoTime(s.wallClockMs),
            s.elapsedMs.toString(),
            s.latencyMs.toString(),
            if (s.ok) "1" else "0",
            s.httpCode.toString(),
            escape(label),
            escape(s.note),
        ).joinToString(",")
}

/** Appends rows to a CSV file and flushes after every row, so a crash or a pulled cable loses nothing. */
class CsvSink(val file: File, private val label: String) {
    private val writer: BufferedWriter = BufferedWriter(FileWriter(file, false)).also {
        it.write(CsvFormat.HEADER)
        it.newLine()
        it.flush()
    }

    @Synchronized
    fun write(sample: ProbeSample) {
        writer.write(CsvFormat.row(sample, label))
        writer.newLine()
        writer.flush()
    }

    @Synchronized
    fun close() {
        try {
            writer.close()
        } catch (_: Exception) {
        }
    }
}
