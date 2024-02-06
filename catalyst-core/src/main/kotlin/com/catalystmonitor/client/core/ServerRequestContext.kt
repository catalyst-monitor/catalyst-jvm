package com.catalystmonitor.client.core

import xyz.bliu.codedoctor.Library.TraceInfo
import xyz.bliu.codedoctor.traceInfo

class ServerRequestContext(
    val fetchId: String,
    val sessionId: String,
    val pageViewId: String? = null,
    val parentFetchId: String? = null
) {
    fun toTraceInfoProto(): TraceInfo {
        val that = this
        return traceInfo {
            fetchId = that.fetchId
            sessionId = that.sessionId
            that.pageViewId?.let { pageViewId = it }
            that.parentFetchId?.let { parentFetchId = it }
        }
    }
}