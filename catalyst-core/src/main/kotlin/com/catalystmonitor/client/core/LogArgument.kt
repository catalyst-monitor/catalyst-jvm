package com.catalystmonitor.client.core

data class LogArgument(val paramName: String, val stringVal: String?, val doubleVal: Double?, val intVal: Int?) {
    constructor(paramName: String, value: String) : this(paramName, value, null, null)
    constructor(paramName: String, value: Double) : this(paramName, null, value, null)
    constructor(paramName: String, value: Int) : this(paramName, null, null, value)
}