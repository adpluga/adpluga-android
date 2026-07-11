package com.adpluga.logger

public object AdPlugaLogger {

    public interface Sink {
        public fun log(level: Level, message: String, cause: Throwable?)
    }

    public enum class Level { DEBUG, INFO, WARN, ERROR }

    @Volatile
    public var enabled: Boolean = false

    @Volatile
    public var sink: Sink? = null

    internal fun debug(message: String, cause: Throwable? = null): Unit = emit(Level.DEBUG, message, cause)
    internal fun info(message: String, cause: Throwable? = null): Unit = emit(Level.INFO, message, cause)
    internal fun warn(message: String, cause: Throwable? = null): Unit = emit(Level.WARN, message, cause)
    internal fun error(message: String, cause: Throwable? = null): Unit = emit(Level.ERROR, message, cause)

    private fun emit(level: Level, message: String, cause: Throwable?) {
        if (!enabled) return
        val out = sink ?: DefaultSink
        try {
            out.log(level, message, cause)
        } catch (_: Throwable) {
        }
    }

    private object DefaultSink : Sink {
        override fun log(level: Level, message: String, cause: Throwable?) {
            val tag = "AdPluga"
            try {
                when (level) {
                    Level.DEBUG -> android.util.Log.d(tag, message, cause)
                    Level.INFO -> android.util.Log.i(tag, message, cause)
                    Level.WARN -> android.util.Log.w(tag, message, cause)
                    Level.ERROR -> android.util.Log.e(tag, message, cause)
                }
            } catch (_: Throwable) {
                val stream = if (level == Level.ERROR || level == Level.WARN) System.err else System.out
                stream.println("[AdPluga/$level] $message")
                cause?.printStackTrace(java.io.PrintWriter(stream, true))
            }
        }
    }
}
