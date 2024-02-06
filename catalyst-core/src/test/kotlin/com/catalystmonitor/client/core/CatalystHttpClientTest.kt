package com.catalystmonitor.client.core

import io.mockk.every
import io.mockk.mockk
import io.mockk.slot
import io.mockk.verify
import org.junit.jupiter.api.Test
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

    @Test
    fun `appendRequest copies filled optional fields`() {
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
            HttpResponse.BodyHandlers.discarding(),
            ServerRequestContext(fetchId = "1", sessionId = "2")
        )

        verify {
            clientMock.send(any(), HttpResponse.BodyHandlers.discarding())
        }
        val req = requestSlot.captured
        assertEquals(URI.create("https://www.test.com"), req.uri())
        assertEquals(Optional.of(publisher), req.bodyPublisher())
        assertEquals(true, req.expectContinue())
        assertEquals("POST", req.method())
        assertEquals(Optional.of(HttpClient.Version.HTTP_2), req.version())
        assertEquals(Optional.of(Duration.ofSeconds(500)), req.timeout())
        assertEquals(
            mapOf(
                "x-test-1" to listOf("1"),
                "x-test-2" to listOf("2"),
                CommonStrings.SESSION_ID_HEADER to listOf("2"),
                CommonStrings.PARENT_FETCH_ID_HEADER to listOf("1"),
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
        client.send(
            HttpRequest.newBuilder(URI.create("https://www.test.com")).build(),
            HttpResponse.BodyHandlers.discarding(),
            ServerRequestContext(fetchId = "1", sessionId = "2")
        )

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
                CommonStrings.SESSION_ID_HEADER to listOf("2"),
                CommonStrings.PARENT_FETCH_ID_HEADER to listOf("1"),
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
            ServerRequestContext(fetchId = "1", sessionId = "2")
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
            ServerRequestContext(fetchId = "1", sessionId = "2")
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
            ServerRequestContext(fetchId = "1", sessionId = "2")
        )

        verify(exactly = 1) { clientMock.sendAsync(any(), HttpResponse.BodyHandlers.discarding(), pushPromiseHandler) }
    }
}