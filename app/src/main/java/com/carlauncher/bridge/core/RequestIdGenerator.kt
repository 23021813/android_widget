package com.carlauncher.bridge.core

import java.util.concurrent.atomic.AtomicInteger

object RequestIdGenerator {
    private val counter = AtomicInteger(1000)
    fun next(): String = counter.incrementAndGet().toString()
}
