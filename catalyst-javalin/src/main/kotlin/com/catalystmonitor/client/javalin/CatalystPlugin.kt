package com.catalystmonitor.client.javalin

import com.catalystmonitor.client.core.CatalystServer
import com.catalystmonitor.client.core.CommonStrings
import com.catalystmonitor.client.core.ServerRequestContext
import io.javalin.config.JavalinConfig
import io.javalin.plugin.Plugin
import java.time.Duration
import java.time.Instant
import java.util.*
import java.util.function.Consumer

class CatalystPlugin(userConfig: Consumer<Config>) : Plugin<CatalystPlugin.Config>(userConfig, Config()) {
    companion object {
        private const val REQUEST_TIME_ATTR = "catalyst_request_timer"
        private val defaultEndpoints = listOf("*")
    }

    class Config {
        val endpoints = mutableListOf<String>()
    }

    override fun onInitialize(config: JavalinConfig) {
        val endpointsToMatch = if (pluginConfig.endpoints.isEmpty()) defaultEndpoints else pluginConfig.endpoints

        config.router.mount { router ->
            for (endpoint in endpointsToMatch) {
                router.before(endpoint) { ctx ->
                    var sessionId = ctx.cookie(CommonStrings.SESSION_COOKIE_NAME)
                    if (sessionId == null) {
                        sessionId = UUID.randomUUID().toString()
                        ctx.cookie(CommonStrings.SESSION_COOKIE_NAME, sessionId)
                    }
                    CatalystServer.Context.setLocal(
                        ServerRequestContext(
                            fetchId = UUID.randomUUID().toString(),
                            sessionId = sessionId,
                            pageViewId = ctx.header(CommonStrings.PAGE_VIEW_ID_HEADER),
                            parentFetchId = ctx.header(CommonStrings.PARENT_FETCH_ID_HEADER),
                        )
                    )
                    ctx.attribute(REQUEST_TIME_ATTR, Instant.now())
                }
                // For some reason, `afterMatched` does not set endpointHandlerPath correctly.
                router.after(endpoint) { ctx ->
                    val context = CatalystServer.Context.getLocal() ?: return@after
                    if (!CatalystServer.hasInstance()) {
                        return@after
                    }

                    val isRecursive = ctx.header(CommonStrings.RECURSIVE_HEADER)
                    val requestTime = ctx.attribute<Instant>(REQUEST_TIME_ATTR) ?: Instant.now()

                    if (isRecursive != "1") {
                        CatalystServer.getInstance().recordFetch(
                            ctx.method().name,
                            ctx.endpointHandlerPath(),
                            ctx.pathParamMap(),
                            ctx.status().code,
                            Duration.between(requestTime, Instant.now()),
                            context
                        )
                    }

                    CatalystServer.Context.removeLocal()
                }
            }
        }
    }
}