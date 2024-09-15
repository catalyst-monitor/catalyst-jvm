package com.catalystmonitor.client.core

object Catalyst {
    private var config: CatalystConfig? = null
    private val reporter = Reporter()

    fun start(config: CatalystConfig) {
        this.config = config
        if (!config.disabled) {
            reporter.start(config)
        }
    }

    fun stop() {
        reporter.stop()
    }

    fun getConfig() = config

    fun getReporter() = reporter
}