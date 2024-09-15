package com.catalystmonitor.client.grpc

import com.catalystmonitor.client.core.*
import com.catalystmonitor.client.grpc.test.GreeterGrpc
import com.catalystmonitor.client.grpc.test.GreeterGrpc.GreeterImplBase
import com.catalystmonitor.client.grpc.test.Test.HelloReply
import com.catalystmonitor.client.grpc.test.Test.HelloRequest
import io.grpc.Channel
import io.grpc.ServerInterceptors
import io.grpc.inprocess.InProcessChannelBuilder
import io.grpc.inprocess.InProcessServerBuilder
import io.grpc.stub.StreamObserver
import io.grpc.testing.GrpcCleanupRule
import io.opentelemetry.api.common.AttributeKey
import io.opentelemetry.api.common.Attributes
import io.opentelemetry.api.trace.SpanContext
import io.opentelemetry.api.trace.TraceFlags
import io.opentelemetry.api.trace.TraceState
import io.opentelemetry.sdk.testing.assertj.OpenTelemetryAssertions.assertThat
import io.opentelemetry.sdk.testing.junit5.OpenTelemetryExtension
import io.opentelemetry.sdk.trace.data.StatusData
import io.opentelemetry.semconv.HttpAttributes
import org.junit.Rule
import org.junit.jupiter.api.Test
import org.junit.jupiter.api.extension.RegisterExtension
import java.time.Instant


class CatalystServerInterceptorTest {
    companion object {
        @JvmField
        @RegisterExtension
        val otelTesting: OpenTelemetryExtension = OpenTelemetryExtension.create()
    }

    @JvmField
    @Rule
    val grpcCleanup: GrpcCleanupRule = GrpcCleanupRule()
    private var channel: Channel? = null

    @Test
    fun `gRPC request creates span`() {
        buildService { req, resp ->
            resp.onNext(HelloReply.getDefaultInstance())
            resp.onCompleted()
        }

        val blockingStub = GreeterGrpc.newBlockingStub(channel)
        blockingStub.sayHello(HelloRequest.getDefaultInstance())

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasName("grpc com.catalystmonitor.client.grpc.test.Greeter/SayHello")
            .hasStatus(StatusData.ok())
        assertThat(otelTesting.spans[0].attributes)
            .hasSize(5)
            .containsEntry(HttpAttributes.HTTP_ROUTE, "com.catalystmonitor.client.grpc.test.Greeter/SayHello")
            .containsEntry(HttpAttributes.HTTP_REQUEST_METHOD, "grpc")
            .containsEntry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 200)
            .containsEntry(
                AttributeKey.stringKey("catalyst.route.rawPath"),
                "com.catalystmonitor.client.grpc.test.Greeter/SayHello"
            )
        assertThat(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))).isNotBlank()
    }

    @Test
    fun `gRPC request failure gets reported`() {
        buildService { req, resp ->
            throw RuntimeException("Testing")
        }


        val blockingStub = GreeterGrpc.newBlockingStub(channel)
        try {
            blockingStub.sayHello(HelloRequest.getDefaultInstance())
        } catch (_: RuntimeException) {
            // Expected
        }

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0])
            .hasName("grpc com.catalystmonitor.client.grpc.test.Greeter/SayHello")
            .hasStatus(StatusData.error())
        assertThat(otelTesting.spans[0].attributes)
            .hasSize(5)
            .containsEntry(HttpAttributes.HTTP_ROUTE, "com.catalystmonitor.client.grpc.test.Greeter/SayHello")
            .containsEntry(HttpAttributes.HTTP_REQUEST_METHOD, "grpc")
            .containsEntry(HttpAttributes.HTTP_RESPONSE_STATUS_CODE, 500)
            .containsEntry(
                AttributeKey.stringKey("catalyst.route.rawPath"),
                "com.catalystmonitor.client.grpc.test.Greeter/SayHello",
            )
        assertThat(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))).isNotBlank()
    }

    @Test
    fun `gRPC logs have the correct span context`() {
        buildService { req, resp ->
            Catalyst.getReporter().recordLog(
                Log(
                    LogSeverity.INFO,
                    "Test message 1",
                    "Test message {num}",
                    args = listOf(LogArgument("num", 1)),
                    logTime = Instant.now()
                )
            )
            resp.onNext(HelloReply.getDefaultInstance())
            resp.onCompleted()
        }


        val blockingStub = GreeterGrpc.newBlockingStub(channel)
        try {
            blockingStub.sayHello(HelloRequest.getDefaultInstance())
        } catch (_: RuntimeException) {
            // Expected
        }

        assertThat(otelTesting.spans).hasSize(1)
        assertThat(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId"))).isNotBlank()
        assertThat(otelTesting.logRecords).hasSize(1)
        assertThat(otelTesting.logRecords[0]).hasSpanContext(
            SpanContext.create(
                otelTesting.spans[0].spanContext.traceId,
                otelTesting.spans[0].spanContext.spanId,
                TraceFlags.getSampled(),
                TraceState.getDefault()
            )
        )
        assertThat(otelTesting.logRecords[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId")))
            .isEqualTo(otelTesting.spans[0].attributes.get(AttributeKey.stringKey("catalyst.sessionId")))
    }

    private fun buildService(impl: (request: HelloRequest?, responseObserver: StreamObserver<HelloReply?>) -> Unit) {
        // See example: https://github.com/grpc/grpc-java/blob/master/examples/src/test/java/io/grpc/examples/header/HeaderServerInterceptorTest.java
        val greeterImplBase: GreeterImplBase =
            object : GreeterImplBase() {
                override fun sayHello(request: HelloRequest?, responseObserver: StreamObserver<HelloReply?>) {
                    impl(request, responseObserver)
                }
            }
        val serverName: String = InProcessServerBuilder.generateName()
        grpcCleanup.register(
            InProcessServerBuilder.forName(serverName).directExecutor()
                .addService(ServerInterceptors.intercept(greeterImplBase, CatalystServerInterceptor()))
                .build()
                .start()
        )
        channel =
            grpcCleanup.register(InProcessChannelBuilder.forName(serverName).directExecutor().build())
    }
}