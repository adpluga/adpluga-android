package com.adpluga.errors

public sealed class AdPlugaError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

    /**
     * Raised when [AdPluga.initialize] is called with a different publisher key
     * than the live instance holds. Rotating a key revokes the previous one
     * immediately, so returning the old instance would leave the app serving
     * with a revoked key. Call `destroy()` first to re-initialize deliberately.
     */
    public class AlreadyInitialized(
        public val activeKey: String,
        public val requestedKey: String,
    ) : AdPlugaError(
        "AdPluga is already initialized with a different publisher key; " +
            "call destroy() before initializing again",
    )

    public object NotInitialized : AdPlugaError("AdPluga.initialize must be called before use") {
        private fun readResolve(): Any = NotInitialized
    }

    public class InvalidKey internal constructor(public val key: String) :
        AdPlugaError("invalid publisher key")

    public class Network internal constructor(
        public val statusCode: Int,
        detail: String,
        cause: Throwable? = null,
    ) : AdPlugaError("network error status=$statusCode $detail", cause)

    public class UpgradeRequired internal constructor(public val minVersion: String) :
        AdPlugaError("SDK upgrade required, minimum version=$minVersion")

    /**
     * Nothing paid to show. Raised instead of drawing the house fallback when
     * the slot runs inside another SDK's waterfall, so the next network can
     * still fill it.
     */
    public object NoFill : AdPlugaError("no fill") {
        private fun readResolve(): Any = NoFill
    }

    public object ConsentDenied : AdPlugaError("consent denied") {
        private fun readResolve(): Any = ConsentDenied
    }

    public class UnsupportedFormat internal constructor(public val kind: String) :
        AdPlugaError("unsupported ad kind: $kind")
}
