package com.catalystmonitor.client.core

import io.opentelemetry.api.GlobalOpenTelemetry
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.baggage.propagation.W3CBaggagePropagator
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.logs.Severity
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.api.trace.propagation.W3CTraceContextPropagator
import io.opentelemetry.context.Context
import io.opentelemetry.context.propagation.ContextPropagators
import io.opentelemetry.context.propagation.TextMapPropagator
import io.opentelemetry.exporter.otlp.logs.OtlpGrpcLogRecordExporter
import io.opentelemetry.exporter.otlp.trace.OtlpGrpcSpanExporter
import io.opentelemetry.sdk.OpenTelemetrySdk
import io.opentelemetry.sdk.logs.SdkLoggerProvider
import io.opentelemetry.sdk.logs.export.BatchLogRecordProcessor
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import io.opentelemetry.sdk.trace.SdkTracerProvider
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.sdk.trace.export.BatchSpanProcessor
import io.opentelemetry.semconv.HttpAttributes
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Clock
import java.time.Instant
import java.time.ZoneId
import kotlin.test.Test

internal class ReporterTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    val fixedClock = Clock.fixed(
        Instant.ofEpochSecond(1725320206),
        ZoneId.systemDefault()
    )

    @Test
    fun `startServerAction creates span`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(),
                headers = mapOf(),
                rawPath = "/test/1",
            )
        )
        span.setOk()
        span.end()
        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasSpanId(span.span.spanContext.spanId)
            .hasTraceId(span.span.spanContext.traceId)
            .hasName("get /test/{num}")
            .hasStatus(StatusData.ok())
            .startsAt(Instant.ofEpochSecond(1725320206))
            .endsAt(Instant.ofEpochSecond(1725320206))
            .hasAttributes(
                Attributes.of(
                    HttpAttributes.HTTP_ROUTE, "/test/{num}",
                    HttpAttributes.HTTP_REQUEST_METHOD, "get",
                    AttributeKey.stringKey("catalyst.route.rawPath"), "/test/1",
                    AttributeKey.stringKey("catalyst.route.params.num"), "1",
                    AttributeKey.stringKey("catalyst.sessionId"), "random-session"
                )
            )
    }

    @Test
    fun `startServerAction sets session ID baggage when no previous session ID`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(),
                headers = mapOf(),
                rawPath = "/test/1",
            )
        )
        span.makeCurrent().use {
            assertThat(Baggage.current().getEntryValue("catalyst.sessionId")).isEqualTo("random-session")
        }
    }

    @Test
    fun `startServerAction propagates W3C trace headers`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(),
                headers = mapOf(
                    "traceparent" to "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01"
                ),
                rawPath = "/test/1",
            )
        )
        span.end()

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasTraceId("0af7651916cd43dd8448eb211c80319c")
            .hasParentSpanId("b9c7c989f97918e1")
    }

    @Test
    fun `startServerAction propagates page view ID headers`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(),
                headers = mapOf(
                    CommonStrings.SESSION_ID_HEADER to "0af7651916cd43dd8448eb211c80319c",
                    CommonStrings.PAGE_VIEW_ID_HEADER to "b9c7c989f97918e1"
                ),
                rawPath = "/test/1",
            )
        )
        span.end()

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasAttribute(AttributeKey.stringKey("catalyst.sessionId"), "0af7651916cd43dd8448eb211c80319c")
            .hasAttribute(AttributeKey.stringKey("catalyst.pageViewId"), "b9c7c989f97918e1")
    }

    @Test
    fun `startServerAction propagates session ID from baggage`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(),
                headers = mapOf(
                    "baggage" to "catalyst.sessionId = 0af7651916cd43dd8448eb211c80319c"
                ),
                rawPath = "/test/1",
            )
        )
        span.end()

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasAttribute(AttributeKey.stringKey("catalyst.sessionId"), "0af7651916cd43dd8448eb211c80319c")
    }

    @Test
    fun `startServerAction falls back to cookies`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(CommonStrings.SESSION_COOKIE_NAME to "0af7651916cd43dd8448eb211c80319c"),
                headers = mapOf(),
                rawPath = "/test/1",
            )
        )
        span.end()

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0].attributes)
            .containsEntry("catalyst.sessionId", "0af7651916cd43dd8448eb211c80319c")
    }

    @Test
    fun `startServerAction prefers headers to cookies`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(CommonStrings.SESSION_COOKIE_NAME to "0bf7651916cd43dd8448eb211c80319c"),
                headers = mapOf(
                    "traceparent" to "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01"
                ),
                rawPath = "/test/1",
            )
        )
        span.end()

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0]).hasTraceId("0af7651916cd43dd8448eb211c80319c")
    }

    @Test
    fun `setLoggedInUserInfo updates the current span`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(),
                headers = mapOf(),
                rawPath = "/test/1",
            )
        )
        span.makeCurrent().use {
            Reporter().setLoggedInUserInfo(SessionUserInfo("test-id-1", "Test Name"))
        }
        span.setOk()
        span.end()
        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasSpanId(span.span.spanContext.spanId)
            .hasTraceId(span.span.spanContext.traceId)
            .hasName("get /test/{num}")
            .hasStatus(StatusData.ok())
            .startsAt(Instant.ofEpochSecond(1725320206))
            .endsAt(Instant.ofEpochSecond(1725320206))
            .hasAttributes(
                Attributes.builder()
                    .put(HttpAttributes.HTTP_ROUTE, "/test/{num}")
                    .put(HttpAttributes.HTTP_REQUEST_METHOD, "get")
                    .put(AttributeKey.stringKey("catalyst.route.rawPath"), "/test/1")
                    .put(AttributeKey.stringKey("catalyst.route.params.num"), "1")
                    .put(AttributeKey.stringKey("catalyst.loggedInId"), "test-id-1")
                    .put(AttributeKey.stringKey("catalyst.loggedInName"), "Test Name")
                    .put(AttributeKey.stringKey("catalyst.sessionId"), "random-session")
                    .build()
            )
    }

    @Test
    fun `recordLog sets span context`() {
        val span = Reporter(fixedClock) { "random-session" }.startServerAction(
            ServerAction(
                pathPattern = "/test/{num}",
                patternArgs = mapOf("num" to "1"),
                method = "get",
                cookies = mapOf(),
                headers = mapOf(
                    "traceparent" to "00-0af7651916cd43dd8448eb211c80319c-b9c7c989f97918e1-01"
                ),
                rawPath = "/test/1",
            )
        )
        span.makeCurrent().use {
            Reporter().recordLog(
                Log(
                    LogSeverity.INFO,
                    "Test message 1",
                    "Test message {num}",
                    args = listOf(LogArgument("num", 1)),
                    logTime = Instant.now()
                )
            )
        }
        span.setOk()
        span.end()

        assertThat(otelTesting.logRecords).hasSize(1)
        assertThat(otelTesting.logRecords[0]).hasSpanContext(
            SpanContext.create(
                "0af7651916cd43dd8448eb211c80319c",
                span.span.spanContext.spanId,
                TraceFlags.getSampled(),
                TraceState.getDefault()
            )
        )
        assertThat(otelTesting.logRecords[0].attributes)
            .containsEntry("catalyst.sessionId", "random-session")
    }

    @Test
    fun `recordLog sets non-error fields`() {
        Reporter().recordLog(
            Log(
                LogSeverity.INFO,
                "Test message 1",
                "Test message {num}",
                args = listOf(LogArgument("num", 1)),
                logTime = Instant.now()
            )
        )

        assertThat(otelTesting.logRecords).hasSize(1)
        assertThat(otelTesting.logRecords[0])
            .hasAttributes(
                Attributes.of(
                    AttributeKey.stringKey("catalyst.log.messagePattern"), "Test message {num}",
                    AttributeKey.longKey("catalyst.log.params.num"), 1
                )
            )
            .hasBody("Test message 1")
            .hasSeverity(Severity.INFO)
            .hasSeverityText("info")
    }

    @Test
    fun `recordLog sets error fields`() {
        val thrown = RuntimeException("Test")

        Reporter().recordLog(
            Log(
                LogSeverity.ERROR,
                "Test message 1",
                "Test message {num}",
                thrown,
                args = listOf(LogArgument("num", 1)),
                logTime = Instant.now()
            )
        )

        assertThat(otelTesting.logRecords).hasSize(1)
        assertThat(otelTesting.logRecords[0])
            .hasAttributes(
                Attributes.of(
                    AttributeKey.stringKey("catalyst.log.messagePattern"), "Test message {num}",
                    AttributeKey.longKey("catalyst.log.params.num"), 1,
                    AttributeKey.stringKey("catalyst.log.stackTrace"), thrown.stackTraceToString()
                )
            )
            .hasBody("Test message 1")
            .hasSeverity(Severity.ERROR)
            .hasSeverityText("error")
    }
}
