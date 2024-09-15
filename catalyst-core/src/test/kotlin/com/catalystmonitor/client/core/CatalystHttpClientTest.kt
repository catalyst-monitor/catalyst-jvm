package com.catalystmonitor.client.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import io.opentelemetry.api.baggage.Baggage
import io.opentelemetry.api.trace.Span
import io.opentelemetry.context.Context
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import org.assertj.core.api.Assertions.assertThat
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpRequest.BodyPublishers
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import kotlin.test.assertEquals

internal class CatalystHttpClientTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    @Test
    fun `appendRequest does nothing with no OTEL context`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }

        val publisher = BodyPublishers.ofString("Test!")
        val client = CatalystHttpClient(clientMock)
        client.send(
            HttpRequest.newBuilder()
                .POST(publisher)
                .uri(URI.create("https://www.test.com"))
                .expectContinue(true)
                .version(HttpClient.Version.HTTP_2)
                .timeout(
                    Duration.ofSeconds(500)
                )
                .header("x-test-1", "1")
                .header("x-test-2", "2")
                .build(),
            HttpResponse.BodyHandlers.discarding()
        )

        verify {
            clientMock.send(any(), HttpResponse.BodyHandlers.discarding())
        }
        val req = requestSlot.captured
        assertThat(req.headers().map()).containsExactly(
            java.util.Map.entry("x-test-1", listOf("1")),
            java.util.Map.entry("x-test-2", listOf("2")),
        )
    }

    @Test
    fun `appendRequest copies all fields`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }

        val publisher = BodyPublishers.ofString("Test!")
        val client = CatalystHttpClient(clientMock)

        val span = Reporter { "random-session" }.startServerAction(
            ServerAction(
                method = "get",
                patternArgs = mapOf(),
                cookies = mapOf(),
                headers = mapOf(),
                rawPath = "/test",
                pathPattern = "/test"
            )
        )
        span.makeCurrent().use {
            client.send(
                HttpRequest.newBuilder()
                    .POST(publisher)
                    .uri(URI.create("https://www.test.com"))
                    .expectContinue(true)
                    .version(HttpClient.Version.HTTP_2)
                    .timeout(
                        Duration.ofSeconds(500)
                    )
                    .header("x-test-1", "1")
                    .header("x-test-2", "2")
                    .build(),
                HttpResponse.BodyHandlers.discarding()
            )
        }
        span.end()

        verify {
            clientMock.send(any(), HttpResponse.BodyHandlers.discarding())
        }
        val req = requestSlot.captured
        assertEquals(URI.create("https://www.test.com"), req.uri())
        assertEquals(Optional.of(publisher), req.bodyPublisher())
        assertEquals(true, req.expectContinue())
        assertEquals("POST", req.method())
        assertThat(req.version()).hasValue(HttpClient.Version.HTTP_2)
        assertThat(req.timeout()).hasValue(Duration.ofSeconds(500))
        assertEquals(
            mapOf(
                "x-test-1" to listOf("1"),
                "x-test-2" to listOf("2"),
                "traceparent" to listOf("00-${span.span.spanContext.traceId}-${span.span.spanContext.spanId}-01"),
                "baggage" to listOf("catalyst.sessionId=random-session")
            ),
            req.headers().map()
        )
    }

    @Test
    fun `appendRequest handles omitted optional fields`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystHttpClient(clientMock)

        val span = Reporter().startServerAction(
            ServerAction(
                method = "get",
                patternArgs = mapOf(),
                cookies = mapOf(),
                headers = mapOf(),
                rawPath = "/test",
                pathPattern = "/test"
            )
        )
        span.makeCurrent().use {
            client.send(
                HttpRequest.newBuilder(URI.create("https://www.test.com")).build(),
                HttpResponse.BodyHandlers.discarding(),
            )
        }
        span.end()

        verify {
            clientMock.send(any(), HttpResponse.BodyHandlers.discarding())
        }
        val req = requestSlot.captured
        assertEquals(URI.create("https://www.test.com"), req.uri())
        assertEquals(0, req.bodyPublisher().get().contentLength())
        assertEquals("GET", req.method())
        assertEquals(Optional.empty(), req.version())
        assertEquals(Optional.empty(), req.timeout())
        assertEquals(
            mapOf(
                "traceparent" to listOf("00-${span.span.spanContext.traceId}-${span.span.spanContext.spanId}-01")
            ),
            req.headers().map()
        )
    }

    @Test
    fun `send delegates to HttpClient`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        every {
            clientMock.send(any(), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }

        val client = CatalystHttpClient(clientMock)
        client.send(
            HttpRequest.newBuilder(URI.create("https://www.test.com")).build(),
            HttpResponse.BodyHandlers.discarding(),
        )

        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
    }

    @Test
    fun `sendAsync delegates to HttpClient`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        every {
            clientMock.sendAsync(any(), HttpResponse.BodyHandlers.discarding())
        } answers {
            CompletableFuture.completedFuture(httpResponse)
        }

        val client = CatalystHttpClient(clientMock)
        client.sendAsync(
            HttpRequest.newBuilder(URI.create("https://www.test.com")).build(),
            HttpResponse.BodyHandlers.discarding(),
        )

        verify(exactly = 1) { clientMock.sendAsync(any(), HttpResponse.BodyHandlers.discarding()) }
    }

    @Test
    fun `sendAsync with push promise delegates to HttpClient`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val pushPromiseHandler = HttpResponse.PushPromiseHandler<Void> { _, _, _ -> }
        every {
            clientMock.sendAsync(any(), HttpResponse.BodyHandlers.discarding(), pushPromiseHandler)
        } answers {
            CompletableFuture.completedFuture(httpResponse)
        }

        val client = CatalystHttpClient(clientMock)
        client.sendAsync(
            HttpRequest.newBuilder(URI.create("https://www.test.com")).build(),
            HttpResponse.BodyHandlers.discarding(),
            pushPromiseHandler,
        )

        verify(exactly = 1) { clientMock.sendAsync(any(), HttpResponse.BodyHandlers.discarding(), pushPromiseHandler) }
    }
}