package com.catalystmonitor.client.core

import com.google.protobuf.duration
import xyz.bliu.codedoctor.*
import xyz.bliu.codedoctor.Library.SendBackendEventsRequest
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse.BodyHandlers
import java.time.Clock
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.concurrent.Executors
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantLock

class CatalystServer(
    val options: Options,
    private val client: HttpClient = HttpClient.newHttpClient(),
    private val clock: Clock = Clock.systemUTC(),
    private val generateUuid: () -> UUID = { UUID.randomUUID() }
) {
    object Context {
        private val threadLocalContext = InheritableThreadLocal.withInitial<ServerRequestContext?> { null }

        fun setLocal(context: ServerRequestContext) {
            threadLocalContext.set(context)
        }

        fun getLocal(): ServerRequestContext? {
            return threadLocalContext.get()
        }

        fun removeLocal() {
            threadLocalContext.remove()
        }
    }

    companion object {
        private var instance: CatalystServer? = null

        fun hasInstance(): Boolean {
            return instance != null
        }

        fun createInstance(opts: Options): CatalystServer {
            val newInst = CatalystServer(opts)
            instance = newInst
            return newInst
        }

        fun getInstance(): CatalystServer {
            return instance ?: throw NullPointerException("Please call CatalystServer.createInstance(...) first!")
        }
    }

    data class Options(
        val privateKey: String,
        val version: String,
        val systemName: String,
        val baseUrl: String = "https://app.catalystmonitor.com",
        val disabled: Boolean = false,
        val recursive: Boolean = false,
    )

    private val sendLock = ReentrantLock()
    private val scheduler =
        Executors.newScheduledThreadPool(1)
    private var scheduledFlush: ScheduledFuture<*>? = null
    private val syncList = Collections.synchronizedList(mutableListOf<SendBackendEventsRequest.Event>())

    fun start() {
        if (scheduledFlush?.isDone == false) {
            return
        }
        scheduledFlush = scheduler.scheduleAtFixedRate({
            try {
                flushEvents()
            } catch (e: Exception) {
            }
        }, 0, 5, TimeUnit.SECONDS)
    }

    fun stop() {
        scheduledFlush?.cancel(false)
    }

    fun flushEvents() {
        if (options.disabled) {
            return
        }
        if (!sendLock.tryLock()) {
            return
        }

        try {
            if (syncList.isEmpty()) {
                return
            }
            val eventsToSend = buildList { addAll(syncList) }
            val opts = this.options
            val resp = client.send(
                HttpRequest.newBuilder()
                    .uri(URI.create("${opts.baseUrl}/api/ingest/be"))
                    .timeout(Duration.ofMinutes(1))
                    .header("Content-Type", "application/protobuf")
                    .header(CommonStrings.PRIVATE_KEY_HEADER, opts.privateKey)
                    .let {
                        if (opts.recursive) {
                            it.header(CommonStrings.RECURSIVE_HEADER, "1")
                        } else it
                    }
                    .PUT(
                        HttpRequest.BodyPublishers.ofByteArray(
                            sendBackendEventsRequest {
                                events.addAll(eventsToSend)
                                info = backEndInfo {
                                    name = opts.systemName
                                    version = opts.version
                                }
                            }.toByteArray()
                        )
                    )
                    .build(),
                BodyHandlers.discarding()
            )
            if (resp.statusCode() in 200..299) {
                syncList.removeAll(eventsToSend)
            }
        } finally {
            sendLock.unlock()
        }
    }

    fun recordFetch(
        method: String,
        pattern: String,
        patternArgs: Map<String, String>,
        statusCode: Int,
        duration: Duration,
        context: ServerRequestContext
    ) {
        syncList.add(
            SendBackendEventsRequestKt.event {
                traceInfo = context.toTraceInfoProto()
                fetch = fetch {
                    this.method = method.lowercase(Locale.getDefault())
                    path = path {
                        this.pattern = pattern
                        params.addAll(
                            patternArgs.entries.map {
                                PathKt.param {
                                    paramName = it.key
                                    argValue = it.value
                                }
                            }
                        )
                    }
                    requestDuration = duration {
                        seconds = duration.seconds
                        nanos = duration.nano
                    }
                    this.statusCode = statusCode
                    endTime = toProtoTimestamp(Instant.now(clock))
                }
            }
        )
    }

    fun recordLog(
        severity: LogSeverity,
        message: String,
        error: Throwable?,
        args: List<LogArgument>,
        logTime: Instant,
        context: ServerRequestContext
    ) {
        syncList.add(
            SendBackendEventsRequestKt.event {
                traceInfo = context.toTraceInfoProto()
                log = log {
                    id = generateUuid().toString()
                    time = toProtoTimestamp(logTime)
                    logSeverity = when (severity) {
                        LogSeverity.INFO -> Library.LogSeverity.INFO_LOG_SEVERITY
                        LogSeverity.WARN -> Library.LogSeverity.WARNING_LOG_SEVERITY
                        LogSeverity.ERROR -> Library.LogSeverity.ERROR_LOG_SEVERITY
                    }
                    this.message = message
                    error?.let {
                        stackTrace = it.stackTraceToString()
                    }
                    logArgs.addAll(args.map {
                        logArg {
                            paramName = it.paramName
                            it.doubleVal?.let { doubleVal = it }
                            it.intVal?.let { intVal = it }
                            it.stringVal?.let { strVal = it }
                        }
                    })
                }
            }
        )
    }
}