package com.catalystmonitor.client.core

import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.util.concurrent.CompletableFuture

class CatalystHttpClient(val httpClient: HttpClient) {
    fun <T> send(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>,
        context: ServerRequestContext
    ): HttpResponse<T> {
        return httpClient.send(
            appendRequest(request, context), responseBodyHandler
        )
    }

    fun <T> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>,
        context: ServerRequestContext
    ): CompletableFuture<HttpResponse<T>> {
        return httpClient.sendAsync(
            appendRequest(request, context), responseBodyHandler
        )
    }

    fun <T> sendAsync(
        request: HttpRequest,
        responseBodyHandler: HttpResponse.BodyHandler<T>,
        pushPromiseHandler: HttpResponse.PushPromiseHandler<T>,
        context: ServerRequestContext
    ): CompletableFuture<HttpResponse<T>> {
        return httpClient.sendAsync(
            appendRequest(request, context), responseBodyHandler, pushPromiseHandler
        )
    }

    private fun appendRequest(request: HttpRequest, context: ServerRequestContext): HttpRequest {
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
        return builder
            .setHeader(CommonStrings.SESSION_ID_HEADER, context.sessionId)
            .setHeader(CommonStrings.PARENT_FETCH_ID_HEADER, context.fetchId)
            .build()
    }
}