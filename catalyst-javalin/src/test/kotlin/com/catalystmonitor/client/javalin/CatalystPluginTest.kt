package com.catalystmonitor.client.javalin

import com.catalystmonitor.client.core.Catalyst
import com.catalystmonitor.client.core.Log
import com.catalystmonitor.client.core.LogSeverity
import io.javalin.Javalin
import io.javalin.apibuilder.ApiBuilder.get
import io.javalin.apibuilder.ApiBuilder.post
import io.javalin.testtools.JavalinTest
import io.javalin.testtools.TestConfig
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import io.opentelemetry.semconv.HttpAttributes
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant

class CatalystPluginTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    @Test
    fun `creates span`() = JavalinTest.test(Javalin.create { config ->
        config.registerPlugin(CatalystPlugin {})
        config.showJavalinBanner = false
        config.router.apiBuilder {
            get("/") { ctx ->
                ctx.result("Good")
            }
        }
    }, TestConfig()) { javalin, httpClient ->
        val resp = httpClient.get("/")

        assertThat(resp.body.use { it?.string() }).isEqualTo("Good")
        assertThat(resp.isSuccessful).isTrue()
        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasTotalAttributeCount(5 + 3)
            .hasAttribute(HttpAttributes.HTTP_ROUTE, "/")
            .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "get")
            .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .hasAttribute(AttributeKey.stringKey("catalyst.route.rawPath"), "/")
        assertThat(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))).isNotBlank()

    }

    @Test
    fun `handles route parameters`() = JavalinTest.test(Javalin.create { config ->
        config.showJavalinBanner = false
        config.registerPlugin(CatalystPlugin {})
        config.router.apiBuilder {
            get("/{param1}") { ctx ->
                ctx.result("Good")
            }
        }
    }, TestConfig()) { javalin, httpClient ->
        val resp = httpClient.get("/test")

        assertThat(resp.isSuccessful).isTrue()
        assertThat(resp.body.use { it?.string() }).isEqualTo("Good")

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasTotalAttributeCount(6 + 3)
            .hasAttribute(HttpAttributes.HTTP_ROUTE, "/{param1}")
            .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "get")
            .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .hasAttribute(AttributeKey.stringKey("catalyst.route.rawPath"), "/test")
            .hasAttribute(AttributeKey.stringKey("catalyst.route.params.param1"), "test")
        assertThat(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))).isNotBlank()
    }

    @Test
    fun `handles method`() = JavalinTest.test(Javalin.create { config ->
        config.showJavalinBanner = false
        config.registerPlugin(CatalystPlugin {})
        config.router.apiBuilder {
            post("/") { ctx ->
                ctx.result("Good")
            }
        }
    }, TestConfig()) { javalin, httpClient ->
        val resp = httpClient.post("/")

        assertThat(resp.isSuccessful).isTrue()
        assertThat(resp.body.use { it?.string() }).isEqualTo("Good")

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasTotalAttributeCount(5 + 3)
            .hasAttribute(HttpAttributes.HTTP_ROUTE, "/")
            .hasAttribute(HttpAttributes.HTTP_REQUEST_METHOD, "post")
            .hasAttribute(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .hasAttribute(AttributeKey.stringKey("catalyst.route.rawPath"), "/")
        assertThat(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))).isNotBlank()
    }

    @Test
    fun `correctly propagates span context`() = JavalinTest.test(Javalin.create { config ->
        val now = Instant.now()

        config.showJavalinBanner = false
        config.registerPlugin(CatalystPlugin {})
        config.router.apiBuilder {
            get("/") { ctx ->
                Catalyst.getReporter().recordLog(
                    Log(
                        message = "Hi",
                        args = listOf(),
                        rawMessage = "Hi",
                        severity = LogSeverity.INFO,
                        logTime = now
                    )
                )
                ctx.result("Good")
            }
        }
    }, TestConfig()) { javalin, httpClient ->
        val resp = httpClient.get("/")

        assertThat(resp.isSuccessful).isTrue()
        assertThat(resp.body.use { it?.string() }).isEqualTo("Good")

        assertThat(otelTesting.spans).hasSize(1)
        val span = otelTesting.spans[0]
        assertThat(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))).isNotBlank()

        assertThat(otelTesting.logRecords).hasSize(1)
        assertThat(otelTesting.logRecords[0])
            .hasSpanContext(
                SpanContext.create(
                    span.traceId,
                    span.spanId,
                    TraceFlags.getSampled(),
                    TraceState.getDefault(),
                )
            )
        assertThat(otelTesting.logRecords[0].attributes)
            .containsEntry(
                AttributeKey.stringKey("catalyst.sessionId"),
                otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))!!
            )
    }
}