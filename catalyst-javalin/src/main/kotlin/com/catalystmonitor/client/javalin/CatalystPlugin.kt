package com.catalystmonitor.client.javalin

import com.catalystmonitor.client.core.Catalyst
import com.catalystmonitor.client.core.CommonStrings
import com.catalystmonitor.client.core.Reporter.FetchSpan
import com.catalystmonitor.client.core.ServerAction
import io.javalin.config.JavalinConfig
import io.javalin.plugin.Plugin
import io.javalin.router.matcher.PathParser
import java.util.*
import java.util.function.Consumer

class CatalystPlugin(userConfig: Consumer<Config>) : Plugin<CatalystPlugin.Config>(userConfig, Config()) {
    companion object {
        private const val CATALYST_SPAN_INSTANCE = "catalyst_span"
        private const val CATALYST_CONTEXT_SCOPE_INSTANCE = "catalyst_context_scope"
        private val defaultEndpoints = listOf("*")
    }

    class Config {
        val endpoints = mutableListOf<String>()
    }

    override fun onInitialize(config: JavalinConfig) {
        val endpointsToMatch = if (pluginConfig.endpoints.isEmpty()) defaultEndpoints else pluginConfig.endpoints

        val pathParserCache = mutableMapOf<String, PathParser>()

        config.router.mount { router ->
            for (endpoint in endpointsToMatch) {
                router.before(endpoint) { ctx ->
                    var sessionId = ctx.cookie(CommonStrings.SESSION_COOKIE_NAME)
                    if (sessionId == null) {
                        sessionId = UUID.randomUUID().toString()
                        ctx.cookie(CommonStrings.SESSION_COOKIE_NAME, sessionId)
                    }
                    val fetchSpan = Catalyst.getReporter().startServerAction(
                        ServerAction(
                            method = "Unknown",
                            rawPath = "Unknown",
                            pathPattern = "Unknown",
                            patternArgs = emptyMap(),
                            headers = ctx.headerMap(),
                            cookies = ctx.cookieMap(),
                        )
                    )
                    ctx.attribute(CATALYST_SPAN_INSTANCE, fetchSpan)
                    ctx.attribute(CATALYST_CONTEXT_SCOPE_INSTANCE, fetchSpan.makeCurrent())
                }
                // We must use after instead of afterMatched, as endpointHandlerPath() does not get populated
                router.after(endpoint) { ctx ->
                    ctx.attribute<FetchSpan.CurrentSpanContext>(CATALYST_CONTEXT_SCOPE_INSTANCE)?.close()
                    val fetchSpan = ctx.attribute<FetchSpan>(CATALYST_SPAN_INSTANCE) ?: return@after

                    val endpointHandlerPath = ctx.endpointHandlerPath()

                    // Since we are in the "after" handler, the context will not have the
                    // params used to match the URL. We simply reparse the params to get them.
                    val parser = pathParserCache.getOrPut(endpointHandlerPath) {
                        PathParser(endpointHandlerPath, config.router)
                    }

                    val rawPath = ctx.path()
                    fetchSpan.updateMethodAndPaths(
                        method = ctx.method().name,
                        pathPattern = endpointHandlerPath,
                        // We must recheck if the parser matches. When the method and
                        // path is unknown, calling extractPathParams causes an exception.
                        params = if (parser.matches(rawPath)) {
                            parser.extractPathParams(rawPath)
                        } else {
                            mapOf()
                        },
                        rawPath = ctx.path(),
                    )

                    fetchSpan.setStatusCode(ctx.status().code)
                    fetchSpan.end()
                }
            }
        }
    }
}