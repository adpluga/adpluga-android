package com.adpluga.ui

import android.view.View
import com.adpluga.AdListener
import com.adpluga.AdPluga
import com.adpluga.errors.AdPlugaError
import com.adpluga.model.Ad
import com.adpluga.model.ServeResponse
import com.adpluga.viewability.ViewabilityTracker
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

public class NativeAd internal constructor(
    private val response: ServeResponse,
    private val slotId: String,
) {
    public val ad: Ad get() = response.ad

    public fun attach(container: View, listener: AdListener? = null): Handle {
        val pluga = AdPluga.maybeInstance
        if (pluga == null) {
            listener?.onError(AdPlugaError.NotInitialized)
            return Handle(0)
        }
        var impressionFired = false
        val vh = ViewabilityTracker.register(container) {
            if (impressionFired) return@register
            impressionFired = true
            listener?.onImpression()
            pluga.fireImpression(
                slotId = slotId,
                ad = ad,
                impressionUrl = response.impressionUrl,
                trackToken = response.trackToken,
            )
        }
        container.setOnClickListener {
            listener?.onClick()
            pluga.fireClick(
                slotId = slotId,
                ad = ad,
                clickUrl = response.clickUrl,
                trackToken = response.trackToken,
            )
        }
        return Handle(vh)
    }

    public class Handle internal constructor(private val viewabilityHandle: Int) {
        public fun release() {
            if (viewabilityHandle != 0) ViewabilityTracker.unregister(viewabilityHandle)
        }
    }

    public interface LoadCallback {
        public fun onLoaded(ad: NativeAd)
        public fun onError(error: Throwable)
    }

    public companion object {
        @JvmStatic
        @JvmOverloads
        public fun load(
            slotId: String,
            format: String? = null,
            callback: LoadCallback,
        ) {
            val pluga = AdPluga.maybeInstance
            if (pluga == null) {
                callback.onError(AdPlugaError.NotInitialized)
                return
            }
            pluga.internalScope.launch {
                try {
                    val response = pluga.serve(slotId, format)
                    if (response == null) {
                        withContext(Dispatchers.Main) {
                            callback.onError(AdPlugaError.Network(0, "no fill"))
                        }
                        return@launch
                    }
                    withContext(Dispatchers.Main) {
                        callback.onLoaded(NativeAd(response, slotId))
                    }
                } catch (ce: CancellationException) {
                    throw ce
                } catch (t: Throwable) {
                    withContext(Dispatchers.Main) {
                        callback.onError(t)
                    }
                }
            }
        }
    }
}
