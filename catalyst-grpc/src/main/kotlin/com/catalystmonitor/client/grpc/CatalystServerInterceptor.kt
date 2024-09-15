package com.catalystmonitor.client.grpc

import com.catalystmonitor.client.core.Catalyst
import com.catalystmonitor.client.core.CommonStrings
import com.catalystmonitor.client.core.Reporter.FetchSpan
import com.catalystmonitor.client.core.ServerAction
import io.grpc.ForwardingServerCallListener.SimpleForwardingServerCallListener
import io.grpc.Metadata
import io.grpc.ServerCall
import io.grpc.ServerCall.Listener
import io.grpc.ServerCallHandler
import io.grpc.ServerInterceptor


class CatalystServerInterceptor : ServerInterceptor {
    override fun <ReqT : Any?, RespT : Any?> interceptCall(
        call: ServerCall<ReqT, RespT>?,
        requestHeaders: Metadata?,
        next: ServerCallHandler<ReqT, RespT>?
    ): Listener<ReqT> {
        val headers = requestHeaders!!
        val recursive =
            requestHeaders.get(Metadata.Key.of(CommonStrings.RECURSIVE_HEADER, Metadata.ASCII_STRING_MARSHALLER))
        if (recursive == "1") {
            return next!!.startCall(call, headers)
        }

        val mappedHeaders = headers.keys().mapNotNull {
            if (it.endsWith(Metadata.BINARY_HEADER_SUFFIX)) {
                return@mapNotNull null
            } else {
                val value = headers.get(Metadata.Key.of(it, Metadata.ASCII_STRING_MARSHALLER)) ?: return@mapNotNull null
                it to value
            }
        }.toMap()

        val path = call?.methodDescriptor?.fullMethodName ?: "Unknown gRPC"

        val span = Catalyst.getReporter().startServerAction(
            ServerAction(
                method = "grpc",
                pathPattern = path,
                patternArgs = mapOf(),
                rawPath = path,
                headers = mappedHeaders,
                cookies = mapOf(),
            )
        )

        return CatalystForwardingServerCallListener(
            next!!.startCall(call!!, requestHeaders), span
        )
    }

    private class CatalystForwardingServerCallListener<ReqT>(
        delegate: Listener<ReqT>,
        private val span: FetchSpan
    ) : SimpleForwardingServerCallListener<ReqT>(delegate) {
        override fun onCancel() {
            super.onCancel()
            span.setStatusCode(499)
            span.end()
        }

        override fun onComplete() {
            super.onComplete()
            span.end()
        }

        override fun onHalfClose() {
            span.makeCurrent().use {
                try {
                    super.onHalfClose()
                    span.setStatusCode(200)
                    span.setOk()
                } catch (e: Exception) {
                    span.setError(e)
                    span.setStatusCode(500)
                    throw e
                }
            }
        }
    }
}
