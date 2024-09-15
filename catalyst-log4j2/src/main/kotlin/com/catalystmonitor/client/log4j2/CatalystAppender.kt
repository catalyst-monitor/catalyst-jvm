package com.catalystmonitor.client.log4j2

import com.catalystmonitor.client.core.Catalyst
import com.catalystmonitor.client.core.Log
import com.catalystmonitor.client.core.LogArgument
import com.catalystmonitor.client.core.LogSeverity
import org.apache.logging.log4j.Level
import org.apache.logging.log4j.core.Appender
import org.apache.logging.log4j.core.Core
import org.apache.logging.log4j.core.Filter
import org.apache.logging.log4j.core.LogEvent
import org.apache.logging.log4j.core.appender.AbstractAppender
import org.apache.logging.log4j.core.config.plugins.Plugin
import org.apache.logging.log4j.core.config.plugins.PluginAttribute
import org.apache.logging.log4j.core.config.plugins.PluginElement
import org.apache.logging.log4j.core.config.plugins.PluginFactory
import java.time.Instant

@Plugin(
    name = "CatalystAppender",
    category = Core.CATEGORY_NAME,
    elementType = Appender.ELEMENT_TYPE
)
class CatalystAppender(name: String, filter: Filter?) : AbstractAppender(name, filter, null, true, null) {
    companion object {
        @JvmStatic
        @PluginFactory
        fun createAppender(
            @PluginAttribute("name") name: String,
            @PluginElement("Filter") filter: Filter?
        ): CatalystAppender {
            return CatalystAppender(name, filter)
        }
    }

    override fun append(event: LogEvent) {
        if (event.marker?.isInstanceOf(CATALYST_IGNORED_MARKER) == true) {
            return
        }
        Catalyst.getReporter().recordLog(
            Log(
                severity = convertLogLevel(event.level),
                rawMessage = event.message.formattedMessage,
                message = event.message.format,
                error = event.thrown,
                // Kotlin doesn't catch this, but parameters can be null.
                args = event.message.parameters?.mapIndexed { index, value ->
                    when (value) {
                        is Int -> LogArgument(index.toString(), value)
                        is String -> LogArgument(index.toString(), value)
                        is Float -> LogArgument(index.toString(), value.toDouble())
                        is Double -> LogArgument(index.toString(), value)
                        else -> LogArgument(index.toString(), value.toString())
                    }
                } ?: listOf(),
                logTime = Instant.ofEpochMilli(event.instant.epochMillisecond),
            )
        )
    }

    private fun convertLogLevel(level: Level) = if (level.isMoreSpecificThan(Level.ERROR)) {
        LogSeverity.ERROR
    } else if (level.isMoreSpecificThan(Level.WARN)) {
        LogSeverity.WARN
    } else {
        LogSeverity.INFO
    }
}