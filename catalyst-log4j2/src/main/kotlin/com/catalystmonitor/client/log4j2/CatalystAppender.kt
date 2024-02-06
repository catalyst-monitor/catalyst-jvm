package com.catalystmonitor.client.log4j2

import com.catalystmonitor.client.core.CatalystServer
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
        if (!CatalystServer.hasInstance()) {
            return
        }
        val context = CatalystServer.Context.getLocal() ?: return

        CatalystServer.getInstance().recordLog(
            convertLogLevel(event.level),
            event.message.format,
            event.thrown,
            event.message.parameters.mapIndexed { index, value ->
                when (value) {
                    is Int -> LogArgument(index.toString(), value)
                    is String -> LogArgument(index.toString(), value)
                    is Float -> LogArgument(index.toString(), value.toDouble())
                    is Double -> LogArgument(index.toString(), value)
                    else -> LogArgument(index.toString(), value.toString())
                }
            },
            Instant.ofEpochMilli(event.instant.epochMillisecond),
            context
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