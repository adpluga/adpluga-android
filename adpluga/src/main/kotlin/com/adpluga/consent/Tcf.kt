package com.adpluga.consent

import android.content.Context

internal data class Tcf(
    val gdprApplies: Boolean?,
    val tcString: String?,
)

internal fun interface TcfSource {
    fun read(): Tcf?
}

internal object AppContextHolder {

    @Volatile
    var context: Context? = null
        private set

    fun remember(context: Context) {
        if (this.context != null) return
        this.context = try {
            context.applicationContext
        } catch (_: Throwable) {
            null
        } ?: return
    }
}

internal object IabTcfStorage : TcfSource {

    const val KEY_GDPR_APPLIES: String = "IABTCF_gdprApplies"
    const val KEY_TC_STRING: String = "IABTCF_TCString"

    override fun read(): Tcf? {
        val ctx = AppContextHolder.context ?: return null
        val values = try {
            ctx.getSharedPreferences("${ctx.packageName}_preferences", Context.MODE_PRIVATE).all
        } catch (_: Throwable) {
            return null
        }
        return parse(values[KEY_GDPR_APPLIES], values[KEY_TC_STRING])
    }

    fun parse(gdprApplies: Any?, tcString: Any?): Tcf? {
        val applies = when (gdprApplies) {
            is Int -> flag(gdprApplies.toLong())
            is Long -> flag(gdprApplies)
            is String -> gdprApplies.trim().toLongOrNull()?.let(::flag)
            else -> null
        }
        val tc = (tcString as? String)?.takeIf { it.isNotBlank() }
        if (applies == null && tc == null) return null
        return Tcf(applies, tc)
    }

    private fun flag(value: Long): Boolean? = when (value) {
        1L -> true
        0L -> false
        else -> null
    }
}
