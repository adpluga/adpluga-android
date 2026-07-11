package com.adpluga

public interface AdListener {
    public fun onLoaded() {}
    public fun onImpression() {}
    public fun onClick() {}
    public fun onDismiss() {}
    public fun onError(error: Throwable) {}
}

public fun interface RewardListener {
    public fun onReward(amount: Int, currency: String)
}
