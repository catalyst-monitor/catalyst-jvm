package com.catalystmonitor.client.core

data class CatalystConfig(
    val privateKey: String,
    val version: String,
    val systemName: String,
    val baseUrl: String = "https://app.catalystmonitor.com:4173",
    val disabled: Boolean = false,
    val recursive: Boolean = false
) {
    class Builder {
        var privateKey = ""
        var version = ""
        var systemName = ""
        var baseUrl = "https://app.catalystmonitor.com:4173"
        var disabled = false
        var recursive = false

        fun build(): CatalystConfig {
            return CatalystConfig(privateKey, version, systemName, baseUrl, disabled, recursive)
        }
    }
}