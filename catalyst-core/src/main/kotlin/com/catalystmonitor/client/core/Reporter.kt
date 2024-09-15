package com.catalystmonitor.client.core

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.*
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.ContextKey
import io.opentelemetry.context.Scope
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapGetter
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.context.propagation.TextMapSetter
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.resources.Resource
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.HttpAttributes
import io.opentelemetry.semconv.ServiceAttributes
import java.time.Clock
import java.time.Instant
import kotlin.random.Random

class Reporter internal constructor(
    private val clock: Clock = Clock.systemDefaultZone(),
    private val sessionIdGenerator: () -> String = {
        Random.nextBytes(ByteArray(16)).joinToString("") {
            String.format("%02x", it)
        }
    }
) {
    var otelInstance: OpenTelemetrySdk? = null

    // Store the propagators as well as register it globally.
    // Catalyst relies on the propagator behavior, so storing the propagators
    // gives us the same propagation behavior no matter what is set globally.
    // This is specifically useful for testing.
    val propagators = ContextPropagators.create(
        TextMapPropagator.composite(
            W3CTraceContextPropagator.getInstance(),
            W3CBaggagePropagator.getInstance()
        )
    )

    internal fun start(config: CatalystConfig) {
        val resource = Resource.getDefault().toBuilder()
            .put(ServiceAttributes.SERVICE_NAME, config.systemName)
            .put(ServiceAttributes.SERVICE_VERSION, config.version)
            .put("catalyst.systemName", config.systemName)
            .put("catalyst.systemVersion", config.version)
            .build()
        otelInstance = OpenTelemetrySdk.builder()
            .setTracerProvider(
                SdkTracerProvider.builder()
                    .addSpanProcessor(
                        BatchSpanProcessor.builder(
                            OtlpGrpcSpanExporter.builder()
                                .addHeader(CommonStrings.PRIVATE_KEY_HEADER, config.privateKey)
                                .let {
                                    if (config.recursive) {
                                        it.addHeader(CommonStrings.RECURSIVE_HEADER, "1")
                                    } else it
                                }
                                .setEndpoint(config.baseUrl)
                                .build()
                        ).build()
                    )
                    .setResource(resource)
                    .build()
            )
            .setLoggerProvider(
                SdkLoggerProvider.builder()
                    .addLogRecordProcessor(
                        BatchLogRecordProcessor.builder(
                            OtlpGrpcLogRecordExporter.builder()
                                .addHeader("X-Catalyst-Private-Key", config.privateKey)
                                .let {
                                    if (config.recursive) {
                                        it.addHeader(CommonStrings.RECURSIVE_HEADER, "1")
                                    } else it
                                }
                                .setEndpoint(config.baseUrl)
                                .build()
                        ).build()
                    )
                    .setResource(resource)
                    .build()
            )
            .setPropagators(propagators)
            .buildAndRegisterGlobal()
    }

    fun stop() {
        otelInstance?.shutdown()
    }

    fun getPropagationHeaders(): Map<String, String> {
        val map = mutableMapOf<String, String>()
        propagators.textMapPropagator.inject(Context.current(), map, PlainMapSetter())
        return map
    }

    fun setLoggedInUserInfo(sessionUserInfo: SessionUserInfo) {
        val currentSpan = Span.current()
        sessionUserInfo.loggedInId?.let {
            currentSpan.setAttribute(loggedInId, it)
        }
        sessionUserInfo.loggedInName?.let {
            currentSpan.setAttribute(loggedInName, it)
        }
    }

    fun startServerAction(serverAction: ServerAction): FetchSpan {
        val extracted = getExtractedContext(serverAction.headers, serverAction.cookies)

        val span = GlobalOpenTelemetry.getTracer("catalyst-java", "0.0.1")
            .spanBuilder("${serverAction.method} - ${serverAction.pathPattern}")
            .setSpanKind(SpanKind.SERVER)
            .setParent(extracted.context)
            .setAllAttributes(extracted.attributes)
            .setStartTimestamp(Instant.now(clock))
            .startSpan()

        setMethodAndPathsForSpan(
            span,
            serverAction.method,
            serverAction.pathPattern,
            serverAction.patternArgs,
            serverAction.rawPath
        )

        return FetchSpan(span, extracted.context, clock)
    }

    fun recordLog(log: Log) {
        val currentSessionId = Baggage.current().getEntryValue("catalyst.sessionId")

        var builder =
            GlobalOpenTelemetry.get().logsBridge.loggerBuilder("catalyst-java").setInstrumentationVersion("0.0.1")
                .build()
                .logRecordBuilder()
                .setBody(log.rawMessage)
                .setSeverity(
                    when (log.severity) {
                        LogSeverity.INFO -> Severity.INFO
                        LogSeverity.WARN -> Severity.WARN
                        LogSeverity.ERROR -> Severity.ERROR
                    }
                )
                .setSeverityText(
                    when (log.severity) {
                        LogSeverity.INFO -> "info"
                        LogSeverity.WARN -> "warn"
                        LogSeverity.ERROR -> "error"
                    }
                )
                .setTimestamp(log.logTime)
                .setAttribute(messagePatternKey, log.message)

        if (currentSessionId != null) {
            builder = builder.setAttribute(sessionIdKey, currentSessionId)
        }

        if (log.error != null) {
            builder.setAttribute(stackTraceKey, log.error.stackTraceToString())
        }

        log.args.forEach {
            it.doubleVal?.let { v ->
                builder = builder.setAttribute(AttributeKey.doubleKey("catalyst.log.params.${it.paramName}"), v)
            }
            it.intVal?.let { v ->
                builder = builder.setAttribute(AttributeKey.longKey("catalyst.log.params.${it.paramName}"), v.toLong())
            }
            it.stringVal?.let { v ->
                builder = builder.setAttribute(AttributeKey.stringKey("catalyst.log.params.${it.paramName}"), v)
            }
        }
        builder.emit()
    }

    private fun getExtractedContext(
        headers: Map<String, String>,
        cookies: Map<String, String>
    ): ExtractedContextAttributes {
        val sessionId = headers[CommonStrings.SESSION_ID_HEADER]
        val pageViewId = headers[CommonStrings.PAGE_VIEW_ID_HEADER]
        val sessionIdCookie = cookies[CommonStrings.SESSION_COOKIE_NAME]

        val currentContext =
            propagators.textMapPropagator.extract(Context.current(), headers, PlainMapGetter())

        var baggage = Baggage.fromContext(currentContext)

        if (baggage.getEntryValue("catalyst.sessionId") == null) {
            baggage = baggage.toBuilder()
                .put("catalyst.sessionId", sessionId ?: sessionIdCookie ?: sessionIdGenerator())
                .build()
        }

        return ExtractedContextAttributes(
            baggage.storeInContext(currentContext),
            Attributes.builder()
                .put(sessionIdKey, baggage.getEntryValue("catalyst.sessionId")!!)
                .let {
                    if (pageViewId != null) {
                        it.put("catalyst.pageViewId", pageViewId)
                    } else it
                }
                .build()
        )
    }

    class FetchSpan internal constructor(
        internal val span: Span,
        internal val context: Context,
        private val clock: Clock
    ) {
        class CurrentSpanContext(internal val scope: Scope) : AutoCloseable by scope

        fun updateMethodAndPaths(method: String, pathPattern: String, params: Map<String, String>, rawPath: String) =
            setMethodAndPathsForSpan(span, method, pathPattern, params, rawPath)

        fun setStatusCode(code: Int) {
            span.setAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, code)
        }

        fun setError(throwable: Throwable) {
            span.setStatus(StatusCode.ERROR)
            span.recordException(throwable)
        }

        fun setOk() {
            span.setStatus(StatusCode.OK)
        }

        fun makeCurrent(): CurrentSpanContext {
            // Somehow, `span.makeCurrent` doesn't set the context from setParent as the current context.
            return CurrentSpanContext(context.with(span).makeCurrent())
        }

        fun end() {
            span.end(Instant.now(clock))
        }
    }
}

private fun setMethodAndPathsForSpan(
    span: Span,
    method: String,
    pathPattern: String,
    params: Map<String, String>,
    rawPath: String
) {
    span.updateName("$method $pathPattern")
    span.setAttribute(HttpAttributes.HTTP_ROUTE, pathPattern)
    span.setAttribute(HttpAttributes.HTTP_REQUEST_METHOD, method.lowercase())
    span.setAttribute("catalyst.route.rawPath", rawPath)
    params.entries.forEach {
        span.setAttribute("catalyst.route.params.${it.key}", it.value)
    }
}


private val stackTraceKey = AttributeKey.stringKey("catalyst.log.stackTrace")
private val messagePatternKey = AttributeKey.stringKey("catalyst.log.messagePattern")
private val sessionIdKey = AttributeKey.stringKey("catalyst.sessionId")
private val loggedInId = AttributeKey.stringKey("catalyst.loggedInId")
private val loggedInName = AttributeKey.stringKey("catalyst.loggedInName")

private class PlainMapSetter : TextMapSetter<MutableMap<String, String>> {
    override fun set(carrier: MutableMap<String, String>?, key: String, value: String) {
        carrier?.set(key, value)
    }
}

private class PlainMapGetter : TextMapGetter<Map<String, String>> {
    override fun get(carrier: Map<String, String>?, key: String): String? {
        return carrier?.get(key)
    }

    override fun keys(carrier: Map<String, String>): Iterable<String> {
        return carrier.keys
    }
}

private data class ExtractedContextAttributes(
    val context: Context,
    val attributes: Attributes,
)