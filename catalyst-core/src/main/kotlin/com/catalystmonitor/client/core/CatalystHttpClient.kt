package com.catalystmonitor.client.core

import java.net.Authenticator
import java.net.CookieHandler
import java.net.ProxySelector
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.*
import java.util.concurrent.CompletableFuture
import java.util.concurrent.Executor
import javax.net.ssl.SSLContext
import javax.net.ssl.SSLParameters

class CatalystHttpClient(val httpClient: HttpClient): HttpClient() {
    override fun cookieHandler(): Optional<CookieHandler> = httpClient.cookieHandler()

    override fun connectTimeout(): Optional<Duration> = httpClient.connectTimeout()

    override fun followRedirects(): Redirect = httpClient.followRedirects()

    override fun proxy(): Optional<ProxySelector> = httpClient.proxy()

    override fun sslContext(): SSLContext = httpClient.sslContext()

    override fun sslParameters(): SSLParameters = httpClient.sslParameters()

    override fun authenticator(): Optional<Authenticator> = httpClient.authenticator()

    override fun version(): Version = httpClient.version()

    override fun executor(): Optional<Executor> = httpClient.executor()

    override fun <T> send(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>
    ): HttpResponse<T> {
        return httpClient.send(
            appendRequest(request), responseBodyHandler
        )
    }

    override fun <T> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>
    ): CompletableFuture<HttpResponse<T>> {
        return httpClient.sendAsync(
            appendRequest(request), responseBodyHandler
        )
    }

    override fun <T> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>,
        pushPromiseHandler: HttpResponse.PushPromiseHandler<T>
    ): CompletableFuture<HttpResponse<T>> {
        return httpClient.sendAsync(
            appendRequest(request), responseBodyHandler, pushPromiseHandler
        )
    }

    private fun appendRequest(request: HttpRequest): HttpRequest {
        var builder = HttpRequest.newBuilder().expectContinue(request.expectContinue())
            .let { build ->
                request.timeout().map { build.timeout(it) }.orElse(build)
            }
            .method(request.method(), request.bodyPublisher().orElse(HttpRequest.BodyPublishers.noBody()))
            .uri(request.uri())
            .let { build -> request.version().map { build.version(it) }.orElse(build) }
        for (headerEntry in request.headers().map().entries) {
            val headerName = headerEntry.key
            for (headerVal in headerEntry.value) {
                builder = builder.setHeader(headerName, headerVal)
            }
        }
        for (propagationEntry in Catalyst.getReporter().getPropagationHeaders().entries) {
            builder = builder.setHeader(propagationEntry.key, propagationEntry.value)
        }

        return builder.build()
    }
}