package com.catalystmonitor.client.core

import com.google.protobuf.Timestamp
import com.google.protobuf.timestamp
import java.time.Instant

internal fun toProtoTimestamp(instant: Instant): Timestamp = timestamp {
    seconds = instant.epochSecond
    nanos = instant.nano
}