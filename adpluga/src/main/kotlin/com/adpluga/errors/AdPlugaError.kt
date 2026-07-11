package com.adpluga.errors

public sealed class AdPlugaError(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause) {

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

    public object ConsentDenied : AdPlugaError("consent denied") {
        private fun readResolve(): Any = ConsentDenied
    }

    public class UnsupportedFormat internal constructor(public val kind: String) :
        AdPlugaError("unsupported ad kind: $kind")
}
