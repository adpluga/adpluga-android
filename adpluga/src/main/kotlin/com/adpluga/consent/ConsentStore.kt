package com.adpluga.consent

import com.adpluga.logger.AdPlugaLogger
import java.util.concurrent.CopyOnWriteArraySet

internal class ConsentStore(initial: ConsentState) {

    @Volatile
    private var current: ConsentState = initial
    private val listeners = CopyOnWriteArraySet<(ConsentState) -> Unit>()

    val state: ConsentState get() = current

    fun update(next: ConsentState): Boolean {
        val prev = current
        if (prev == next) return false
        current = next
        for (listener in listeners) {
            try {
                listener(next)
            } catch (t: Throwable) {
                AdPlugaLogger.warn("consent listener failed", t)
            }
        }
        return true
    }

    fun addListener(listener: (ConsentState) -> Unit) {
        listeners.add(listener)
    }

    fun removeListener(listener: (ConsentState) -> Unit) {
        listeners.remove(listener)
    }
}
