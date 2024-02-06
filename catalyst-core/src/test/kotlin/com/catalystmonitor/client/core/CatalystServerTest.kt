package com.catalystmonitor.client.core

import com.google.protobuf.duration
import io.mockk.*
import xyz.bliu.codedoctor.*
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.net.http.HttpResponse.BodySubscriber
import java.net.http.HttpResponse.BodySubscribers
import java.nio.ByteBuffer
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.time.ZoneOffset
import java.util.*
import java.util.concurrent.Flow
import kotlin.jvm.optionals.getOrNull
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

val fixedUuid: UUID = UUID.fromString("28a892d8-811c-4472-b775-9a3a1ca72fb2")
val fixedInstant: Instant = Instant.ofEpochSecond(1706816531)

internal class CatalystServerTest {
    @Test
    fun `flushEvents sends events in batches`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordLog(
            LogSeverity.INFO, "Hi1", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.recordLog(
            LogSeverity.INFO, "Hi2", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi3",
                sessionId = "hi4",
                pageViewId = "hi5",
                parentFetchId = "hi6",
            )
        )
        client.flushEvents()

        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.addAll(
                    listOf(
                        SendBackendEventsRequestKt.event {
                            traceInfo = traceInfo {
                                fetchId = "hi1"
                                sessionId = "hi2"
                            }
                            log = log {
                                id = fixedUuid.toString()
                                time = toProtoTimestamp(fixedInstant)
                                logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                                message = "Hi1"
                            }
                        },
                        SendBackendEventsRequestKt.event {
                            traceInfo = traceInfo {
                                fetchId = "hi3"
                                sessionId = "hi4"
                                pageViewId = "hi5"
                                parentFetchId = "hi6"
                            }
                            log = log {
                                id = fixedUuid.toString()
                                time = toProtoTimestamp(fixedInstant)
                                logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                                message = "Hi2"
                            }
                        })
                )
            },
            getRequestFromSlot(requestSlot)
        )
    }

    @Test
    fun `flushEvents sends subsequent events`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordLog(
            LogSeverity.INFO, "Hi1", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.flushEvents()
        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }

        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.add(
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                            message = "Hi1"
                        }
                    }
                )
            },
            getRequestFromSlot(requestSlot)
        )

        client.recordLog(
            LogSeverity.INFO, "Hi2", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi3",
                sessionId = "hi4",
                pageViewId = "hi5",
                parentFetchId = "hi6",
            )
        )
        client.flushEvents()

        verify(exactly = 2) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.add(
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi3"
                            sessionId = "hi4"
                            pageViewId = "hi5"
                            parentFetchId = "hi6"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                            message = "Hi2"
                        }
                    }
                )
            },
            getRequestFromSlot(requestSlot)
        )
    }

    @Test
    fun `flushEvents sends nothing if no events`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.flushEvents()

        verify { clientMock wasNot Called }
    }

    @Test
    fun `flushEvents retries events not sent`() {
        val clientMock = mockk<HttpClient>()
        val failureResponse = mockk<HttpResponse<Void>>()
        every { failureResponse.statusCode() } answers { 500 }
        val successResponse = mockk<HttpResponse<Void>>()
        every { successResponse.statusCode() } answers { 200 }
        val requestSlot = slot<HttpRequest>()
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordLog(
            LogSeverity.INFO, "Hi1", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            failureResponse
        }
        client.flushEvents()

        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.add(
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                            message = "Hi1"
                        }
                    }
                )
            },
            getRequestFromSlot(requestSlot)
        )


        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            successResponse
        }
        client.flushEvents()

        verify(exactly = 2) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.add(
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                            message = "Hi1"
                        }
                    }
                )
            },
            getRequestFromSlot(requestSlot)
        )
    }

    @Test
    fun `flushEvents doesn't send if disabled`() {
        val clientMock = mockk<HttpClient>()
        val requestSlot = slot<HttpRequest>()
        val httpResponse = mockk<HttpResponse<Void>>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
                disabled = true,
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordLog(
            LogSeverity.INFO, "Hi1", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )

        client.flushEvents()
        verify { clientMock wasNot Called }
    }

    @Test
    fun `flushEvents sets private key header`() {
        val clientMock = mockk<HttpClient>()
        val requestSlot = slot<HttpRequest>()
        val httpResponse = mockk<HttpResponse<Void>>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordLog(
            LogSeverity.INFO, "Hi1", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.flushEvents()

        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
        assertEquals(
            mapOf(
                "Content-Type" to listOf("application/protobuf"),
                CommonStrings.PRIVATE_KEY_HEADER to listOf("key")
            ),
            requestSlot.captured.headers().map()
        )
    }

    @Test
    fun `flushEvents sets recursive header`() {
        val clientMock = mockk<HttpClient>()
        val requestSlot = slot<HttpRequest>()
        val httpResponse = mockk<HttpResponse<Void>>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
                recursive = true,
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordLog(
            LogSeverity.INFO, "Hi1", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.flushEvents()

        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
        assertEquals(
            mapOf(
                "Content-Type" to listOf("application/protobuf"),
                CommonStrings.PRIVATE_KEY_HEADER to listOf("key"),
                CommonStrings.RECURSIVE_HEADER to listOf("1")
            ),
            requestSlot.captured.headers().map()
        )
    }

    @Test
    fun `recordFetch sends fetch event`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordFetch(
            "get",
            "test",
            mapOf("test1" to "test2", "test3" to "test4"),
            200,
            Duration.ofSeconds(500),
            ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.recordFetch(
            "put", "test2", mapOf(), 400, Duration.ofSeconds(1200), ServerRequestContext(
                fetchId = "hi2",
                sessionId = "hi3"
            )
        )
        client.flushEvents()
        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }

        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.addAll(listOf(
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        fetch = fetch {
                            method = "get"
                            path = path {
                                pattern = "test"
                                params.addAll(listOf(
                                    PathKt.param {
                                        paramName = "test1"
                                        argValue = "test2"
                                    },
                                    PathKt.param {
                                        paramName = "test3"
                                        argValue = "test4"
                                    }
                                ))
                            }
                            requestDuration = duration {
                                seconds = 500
                            }
                            statusCode = 200
                            endTime = toProtoTimestamp(fixedInstant)
                        }
                    },
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi2"
                            sessionId = "hi3"
                        }
                        fetch = fetch {
                            method = "put"
                            path = path {
                                pattern = "test2"
                            }
                            requestDuration = duration {
                                seconds = 1200
                            }
                            statusCode = 400
                            endTime = toProtoTimestamp(fixedInstant)
                        }
                    }
                ))
            },
            getRequestFromSlot(requestSlot)
        )
    }

    @Test
    fun `recordLog sends string message log event`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        client.recordLog(
            LogSeverity.INFO, "test1", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.recordLog(
            LogSeverity.WARN, "test2", null, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.recordLog(
            LogSeverity.ERROR, "test3", null, listOf(
                LogArgument("test1", "hi"),
                LogArgument("test2", 1),
                LogArgument("test3", 2.5),
            ), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.flushEvents()
        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }

        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.addAll(listOf(
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                            message = "test1"
                        }
                    },
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.WARNING_LOG_SEVERITY
                            message = "test2"
                        }
                    },
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.ERROR_LOG_SEVERITY
                            logArgs.addAll(
                                listOf(
                                    logArg {
                                        paramName = "test1"
                                        strVal = "hi"
                                    },
                                    logArg {
                                        paramName = "test2"
                                        intVal = 1
                                    },
                                    logArg {
                                        paramName = "test3"
                                        doubleVal = 2.5
                                    },
                                )
                            )
                            message = "test3"
                        }
                    }
                ))
            },
            getRequestFromSlot(requestSlot)
        )
    }

    @Test
    fun `recordLog sends exception log event`() {
        val clientMock = mockk<HttpClient>()
        val httpResponse = mockk<HttpResponse<Void>>()
        val requestSlot = slot<HttpRequest>()
        every { httpResponse.statusCode() } answers { 200 }
        every {
            clientMock.send(capture(requestSlot), HttpResponse.BodyHandlers.discarding())
        } answers {
            httpResponse
        }
        val client = CatalystServer(
            CatalystServer.Options(
                privateKey = "key",
                version = "1",
                systemName = "sys",
            ),
            client = clientMock,
            clock = Clock.fixed(fixedInstant, ZoneOffset.UTC),
            generateUuid = { fixedUuid }
        )
        val exception = NullPointerException("hi")
        client.recordLog(
            LogSeverity.INFO, "hi", exception, listOf(), fixedInstant, ServerRequestContext(
                fetchId = "hi1",
                sessionId = "hi2"
            )
        )
        client.flushEvents()

        verify(exactly = 1) { clientMock.send(any(), HttpResponse.BodyHandlers.discarding()) }
        assertEquals(
            sendBackendEventsRequest {
                info = backEndInfo {
                    version = "1"
                    name = "sys"
                }
                events.add(
                    SendBackendEventsRequestKt.event {
                        traceInfo = traceInfo {
                            fetchId = "hi1"
                            sessionId = "hi2"
                        }
                        log = log {
                            id = fixedUuid.toString()
                            time = toProtoTimestamp(fixedInstant)
                            logSeverity = Library.LogSeverity.INFO_LOG_SEVERITY
                            message = "hi"
                            stackTrace = exception.stackTraceToString()
                        }
                    })
            },
            getRequestFromSlot(requestSlot)
        )
    }
}

fun getRequestFromSlot(slot: CapturingSlot<HttpRequest>): Library.SendBackendEventsRequest {
    assertTrue(slot.isCaptured)
    val publisher = slot.captured.bodyPublisher().getOrNull()
    assertNotNull(publisher)

    val subscriber = TestRequestSubscriber()
    publisher.subscribe(subscriber)
    val value = subscriber.getValue()
    return Library.SendBackendEventsRequest.parseFrom(value)
}

class TestRequestSubscriber : Flow.Subscriber<ByteBuffer> {
    private val subscriber: BodySubscriber<ByteArray> = BodySubscribers.ofByteArray()

    override fun onSubscribe(subscription: Flow.Subscription) {
        subscriber.onSubscribe(subscription)
    }

    override fun onNext(item: ByteBuffer) {
        subscriber.onNext(listOf(item))
    }

    override fun onError(throwable: Throwable) {
        subscriber.onError(throwable)
    }

    override fun onComplete() {
        subscriber.onComplete()
    }

    fun getValue(): ByteArray {
        return subscriber.body.toCompletableFuture().join()
    }
}