package com.adpluga.viewability

import android.graphics.Rect
import android.os.Handler
import android.os.Looper
import android.view.View
import com.adpluga.config.Constants
import java.util.concurrent.atomic.AtomicInteger

internal object ViewabilityTracker {

    private val slots = LinkedHashMap<Int, Slot>()
    private val handleSeq = AtomicInteger(0)
    private val mainHandler = Handler(Looper.getMainLooper())
    private var scheduled = false

    private val tickRunnable = Runnable {
        scheduled = false
        val now = System.currentTimeMillis()
        val fired = ArrayList<Int>(4)
        val expired = ArrayList<Int>(4)
        for ((handle, slot) in slots) {
            when (evaluate(slot, now)) {
                Result.Fire -> fired.add(handle)
                Result.Detached -> expired.add(handle)
                Result.Continue -> Unit
            }
        }
        for (handle in fired) {
            val slot = slots.remove(handle) ?: continue
            try {
                slot.onViewable()
            } catch (_: Throwable) {
            }
        }
        for (handle in expired) {
            slots.remove(handle)
        }
        if (slots.isNotEmpty()) schedule()
    }

    @Synchronized
    fun register(view: View, onViewable: () -> Unit): Int {
        val handle = handleSeq.incrementAndGet()
        slots[handle] = Slot(view, onViewable)
        schedule()
        return handle
    }

    @Synchronized
    fun unregister(handle: Int) {
        slots.remove(handle)
    }

    private fun schedule() {
        if (scheduled) return
        scheduled = true
        mainHandler.postDelayed(tickRunnable, Constants.VIEWABILITY_TICK_MS)
    }

    private fun evaluate(slot: Slot, now: Long): Result {
        val ref = slot.viewRef.get() ?: return Result.Detached
        if (!ref.isShown || !ref.isAttachedToWindow) {
            slot.visibleSince = 0L
            return Result.Continue
        }
        if (ref.width == 0 || ref.height == 0) {
            slot.visibleSince = 0L
            return Result.Continue
        }
        val ratio = computeVisibleRatio(ref)
        if (ratio < Constants.VIEWABILITY_THRESHOLD) {
            slot.visibleSince = 0L
            return Result.Continue
        }
        if (slot.visibleSince == 0L) {
            slot.visibleSince = now
            return Result.Continue
        }
        if (now - slot.visibleSince >= Constants.VIEWABILITY_DURATION_MS) {
            return Result.Fire
        }
        return Result.Continue
    }

    private fun computeVisibleRatio(view: View): Double {
        val rect = Rect()
        val visible = view.getGlobalVisibleRect(rect)
        if (!visible) return 0.0
        val viewArea = view.width.toLong() * view.height.toLong()
        if (viewArea <= 0L) return 0.0
        val visibleArea = rect.width().toLong() * rect.height().toLong()
        return (visibleArea.toDouble() / viewArea.toDouble()).coerceIn(0.0, 1.0)
    }

    private data class Slot(
        val viewRef: java.lang.ref.WeakReference<View>,
        val onViewable: () -> Unit,
        var visibleSince: Long = 0L,
    ) {
        constructor(view: View, onViewable: () -> Unit) :
            this(java.lang.ref.WeakReference(view), onViewable)
    }

    private enum class Result { Continue, Fire, Detached }
}
