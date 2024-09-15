package com.catalystmonitor.client.core

import java.time.Instant

data class Log(
    val severity: LogSeverity,
    val rawMessage: String,
    val message: String,
    val error: Throwable? = null,
    val args: List<LogArgument>,
    val logTime: Instant = Instant.now()
) {
    class Builder {
        var severity: LogSeverity? = null
        var rawMessage: String? = null
        var message: String? = null
        var error: Throwable? = null
        var args: List<LogArgument> = listOf()
        var logTime: Instant = Instant.now()

        fun build(): Log {
            return Log(
                severity ?: throw BuilderMissingFieldException("severity", "setSeverity"),
                rawMessage ?: throw BuilderMissingFieldException("rawMessage", "setRawMessage"),
                message ?: throw BuilderMissingFieldException("message", "setMessage"),
                error,
                args,
                logTime
            )
        }
    }
}