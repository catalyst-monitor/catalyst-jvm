package com.catalystmonitor.client.core

import java.lang.NullPointerException

data class ServerAction(
    val method: String,
    val pathPattern: String,
    val patternArgs: Map<String, String>,
    val rawPath: String,
    val headers: Map<String, String>,
    val cookies: Map<String, String>
) {
    class Builder {
        var method: String? = null
        var pathPattern: String? = null
        var patternArgs: Map<String, String> = emptyMap()
        var rawPath: String? = null
        var headers: Map<String, String> = emptyMap()
        var cookies: Map<String, String> = emptyMap()

        fun build(): ServerAction {
            return ServerAction(
                method ?: throw BuilderMissingFieldException("method", "setMethod"),
                pathPattern ?: throw BuilderMissingFieldException("pathPattern", "setPathPattern"),
                patternArgs,
                rawPath ?: throw BuilderMissingFieldException("rawPath", "setRawPath"),
                headers,
                cookies
            )
        }
    }
}