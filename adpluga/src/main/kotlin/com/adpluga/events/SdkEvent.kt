package com.adpluga.events

import com.adpluga.consent.ConsentState
import com.adpluga.model.Ad

public sealed class SdkEvent(public val at: Long = System.currentTimeMillis()) {
    public class InitCompleted internal constructor() : SdkEvent()
    public class AdServed internal constructor(public val slotId: String, public val ad: Ad) : SdkEvent()
    public class AdFailed internal constructor(public val slotId: String, public val cause: Throwable) : SdkEvent()
    public class Impression internal constructor(public val slotId: String, public val adId: String) : SdkEvent()
    public class Click internal constructor(public val slotId: String, public val adId: String) : SdkEvent()
    public class ConsentChanged internal constructor(public val consent: ConsentState) : SdkEvent()
    public class FeaturesUpdated internal constructor(public val flags: Map<String, Boolean>) : SdkEvent()
    public class UpgradeRequiredEvent internal constructor(public val minVersion: String) : SdkEvent()
}
